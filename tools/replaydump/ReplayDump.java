package com.google.flatbuffers; // needed for protected access to Table's __offset/__vector/__indirect,
                                  // required to read Struct-typed (not just Table-typed) union members
                                  // out of Turn.actions() -- see notes in TRAINING_LOG.md.

import battlecode.schema.*;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.zip.GZIPInputStream;

/**
 * Turns a .bc26 replay into a human-readable text transcript: per-round team
 * aggregates (cheese, cat damage, population) plus a filtered log of the
 * events that actually matter for root-causing a loss (deaths, cat
 * pounces/scratches, damage, spawns). See tools/README.md.
 *
 * Usage: ReplayDump <file.bc26> [--from R] [--to R]
 */
public class ReplayDump {
    static Map<Integer, String> robotLabel = new HashMap<>();
    static int mapWidth = -1;
    static int fromRound = 0;
    static int toRound = Integer.MAX_VALUE;
    static int trackRobot = -1;
    static int terrainX = Integer.MIN_VALUE, terrainY = Integer.MIN_VALUE, terrainR = 8;
    // Iteration-agnostic: cooperation state decides which score weights apply
    // (cooperating = catDamage 0.5 / kings 0.3; after a backstab = kings 0.5 /
    // catDamage 0.3), so knowing WHEN it flips is needed to price any catDamage
    // number at all. Printed on transition only.
    static Boolean lastCoop = null;

    // `--turns <team>`: emit one line per robot turn carrying position and
    // FACING. The Turn table has had x(), y() and dir() all along and this dump
    // read none of them, which left two questions unanswerable from a replay:
    // how often a rat holds still, and which way it faced when something
    // happened to it. Both are central to the facing trap (TRAINING_LOG.md) --
    // canGrab succeeds when the target faces away, and only turn() changes
    // facing. 0 = both teams, 1 or 2 = that team only, -1 = off.
    static int turnsTeam = -1;

    public static void main(String[] args) throws Exception {
        // Every flag takes exactly one value. An unknown or misspelled flag is a
        // hard error rather than a silent no-op: the old loop ignored anything it
        // did not recognise, so `--form 100` dumped the entire game and looked
        // like a legitimate result.
        for (int i = 1; i < args.length; i++) {
            String flag = args[i];
            String val = argValue(args, ++i, flag);
            switch (flag) {
                case "--from": fromRound = Integer.parseInt(val); break;
                case "--to": toRound = Integer.parseInt(val); break;
                case "--robot": trackRobot = Integer.parseInt(val); break;
                case "--terrain": {
                    String[] xy = val.split(",");
                    if (xy.length != 2) throw new IllegalArgumentException("--terrain wants x,y, got: " + val);
                    terrainX = Integer.parseInt(xy[0]);
                    terrainY = Integer.parseInt(xy[1]);
                    break;
                }
                case "--turns": turnsTeam = Integer.parseInt(val); break;
                default: throw new IllegalArgumentException("unknown flag: " + flag);
            }
        }
        if (fromRound > toRound) {
            throw new IllegalArgumentException("--from " + fromRound + " is after --to " + toRound);
        }
        byte[] raw = readAll(args[0]);
        byte[] bytes;
        if (raw.length > 2 && (raw[0] & 0xff) == 0x1f && (raw[1] & 0xff) == 0x8b) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (GZIPInputStream gz = new GZIPInputStream(new ByteArrayInputStream(raw))) {
                gz.transferTo(bos);
            }
            bytes = bos.toByteArray();
        } else {
            bytes = raw;
        }
        ByteBuffer buf = ByteBuffer.wrap(bytes);
        GameWrapper gw = GameWrapper.getRootAsGameWrapper(buf);

        int round = -1;
        for (int i = 0; i < gw.eventsLength(); i++) {
            EventWrapper ew = gw.events(i);
            byte t = ew.eType();
            if (t == Event.GameHeader) {
                GameHeader gh = (GameHeader) ew.e(new GameHeader());
                System.out.println("=== GameHeader spec=" + gh.specVersion() + " teams=" + gh.teamsLength());
                for (int k = 0; k < gh.teamsLength(); k++) {
                    TeamData td = gh.teams(k);
                    System.out.println("  team " + td.teamId() + " packageName=" + td.packageName());
                }
            } else if (t == Event.MatchHeader) {
                MatchHeader mh = (MatchHeader) ew.e(new MatchHeader());
                GameMap map = mh.map();
                mapWidth = map.size().x();
                System.out.println("=== MatchHeader map=" + map.name() + " size=" + map.size().x() + "x" + map.size().y()
                        + " symmetry=" + map.symmetry() + " maxRounds=" + mh.maxRounds());
                if (terrainX != Integer.MIN_VALUE) printTerrain(map, terrainX, terrainY, terrainR);
                VecTable mines = map.cheeseMines();
                if (mines != null) {
                    StringBuilder sb = new StringBuilder("  cheese mines (" + mines.xsLength() + "): ");
                    for (int k = 0; k < mines.xsLength(); k++) {
                        sb.append("(").append(mines.xs(k)).append(",").append(mines.ys(k)).append(") ");
                    }
                    System.out.println(sb);
                }
                InitialBodyTable ibt = map.initialBodies();
                if (ibt != null) {
                    for (int k = 0; k < ibt.spawnActionsLength(); k++) {
                        SpawnAction sp = ibt.spawnActions(k);
                        String lbl = "id" + sp.id() + "(team" + sp.team() + "," + RobotType.name(sp.robotType()) + ")";
                        robotLabel.put(sp.id(), lbl);
                        System.out.println("  initial body " + lbl + " at (" + sp.x() + "," + sp.y() + ")");
                        if (sp.robotType() == RobotType.RAT_KING) {
                            printTerrain(map, sp.x(), sp.y(), 4);
                        }
                    }
                }
            } else if (t == Event.Round) {
                Round r = (Round) ew.e(new Round());
                round = r.roundId();
                // Robot labels must accumulate over EVERY round, not just the
                // printed window. The label table is built from SpawnActions, so
                // skipping rounds before `--from` used to leave every rat spawned
                // in them as a bare "id10519" with no (team,type) -- and an
                // unlabelled id is exactly how a trace gets attributed to the
                // wrong team. Walk the turns for spawns first, print second.
                boolean inWindow = round >= fromRound && round <= toRound;
                for (int ti = 0; ti < r.turnsLength(); ti++) {
                    dumpActions(r.turns(ti), round, inWindow);
                }
                if (!inWindow) continue;
                // GameWorld.java packs this field as `numRatKings + 10*cheese`
                // ("combine total cheese into the rat kings stat") -- unpack both.
                StringBuilder status = new StringBuilder();
                for (int k = 0; k < r.teamAliveRatKingsLength(); k++) {
                    int combined = r.teamAliveRatKings(k);
                    status.append(r.teamIds(k)).append(":kings=").append(combined % 10)
                            .append(",cheese=").append(combined / 10).append(" ");
                }
                // Team aggregates are sampled every 25 rounds to keep the
                // transcript readable. `round == fromRound` is included so a
                // narrow window always yields at least one stats line -- without
                // it, `--from 101 --to 124` printed no team totals at all and
                // silently looked like a game with no economy.
                if (round % 25 == 0 || round < 5 || round == fromRound) {
                    System.out.println("round " + round + " " + status + "aliveBabies="
                            + vec(r::teamAliveBabyRats, r.teamAliveBabyRatsLength())
                            + " catDamage=" + vec(r::teamCatDamage, r.teamCatDamageLength())
                            + " cheeseTransferred=" + vec(r::teamCheeseTransferred, r.teamCheeseTransferredLength())
                            + " dirt=" + vec(r::teamDirtAmounts, r.teamDirtAmountsLength())
                            + " ratTraps=" + vec(r::teamRatTrapCount, r.teamRatTrapCountLength())
                            + " catTraps=" + vec(r::teamCatTrapCount, r.teamCatTrapCountLength()));
                }
                for (int k = 0; k < r.diedIdsLength(); k++) {
                    System.out.println("round " + round + " DIED id=" + label(r.diedIds(k)));
                }
                for (int ti = 0; ti < r.turnsLength(); ti++) {
                    Turn turn = r.turns(ti);
                    boolean coop = turn.isCooperation();
                    if (lastCoop == null || lastCoop != coop) {
                        System.out.println("round " + round + " COOPERATION -> " + coop
                                + " (weights: " + (coop ? "catDamage 0.5 / kings 0.3"
                                                       : "kings 0.5 / catDamage 0.3") + ")");
                        lastCoop = coop;
                    }
                    if (trackRobot >= 0 && turn.robotId() == trackRobot) {
                        System.out.println("round " + round + " TRACK id" + trackRobot + " at (" + turn.x() + "," + turn.y()
                                + ") dir=" + turn.dir() + " hp=" + turn.health() + " cheese=" + turn.cheese()
                                + " moveCD=" + turn.moveCooldown() + " turnCD=" + turn.turningCooldown());
                    }
                }
            } else if (t == Event.MatchFooter) {
                MatchFooter mf = (MatchFooter) ew.e(new MatchFooter());
                System.out.println("=== MatchFooter winner=" + mf.winner() + " winType=" + WinType.name(mf.winType())
                        + " totalRounds=" + mf.totalRounds());
            }
        }
    }

    // Manual union-vector walk: Turn.actions()'s generated accessor only
    // type-checks for Table-derived union members, but several Action union
    // members (DieAction, CatPounce, CatScratch, DamageAction, SpawnAction,
    // ...) are Struct-derived. Replicate what Table.__union()/UnionVector.get()
    // do internally (__offset -> __vector -> __indirect per element), using
    // protected same-package access (this class lives in package
    // com.google.flatbuffers specifically to get it), then __init() the
    // right struct/table type directly -- __init is public on every
    // generated type.
    // `print` false means "this round is outside the --from/--to window": still
    // walk the actions so SpawnActions register their labels, but emit nothing.
    static void dumpActions(Turn turn, int round, boolean print) {
        // `actions` is vtable slot 26. A flatbuffers union expands into TWO
        // fields -- actions_type then actions -- so the schema's 12 declared
        // Turn fields become 13 vtable slots and `actions` lands at 0-based
        // index 11, i.e. (11+2)*2 = 26. Verified against the generated
        // Turn.java accessor; tools/test_replaydump.py pins it.
        // Emitted BEFORE the no-actions early return below. A rat that stands
        // still and does nothing has no actions at all, and those turns are
        // precisely the ones a rotate-to-scan proposal depends on -- returning
        // first would have hidden exactly the population being measured.
        if (turnsTeam >= 0 && print) {
            String lbl = label(turn.robotId());
            if (turnsTeam == 0 || lbl.contains("team" + turnsTeam)) {
                System.out.println("round " + round + " TURN " + lbl
                        + " at (" + turn.x() + "," + turn.y() + ")"
                        + " dir=" + turn.dir()
                        + " hp=" + turn.health()
                        + " moveCd=" + turn.moveCooldown()
                        + " turnCd=" + turn.turningCooldown());
            }
        }

        int o = turn.__offset(26);
        if (o == 0) return;
        int len = turn.__vector_len(o);
        int vecStart = turn.__vector(o);
        ByteBuffer bb = turn.getByteBuffer();
        String who = label(turn.robotId());
        for (int j = 0; j < len; j++) {
            byte at = turn.actionsType(j);
            // Outside the print window only spawns matter, and only for their
            // side effect of registering a label.
            if (!print && at != Action.SpawnAction) continue;
            int elemOffset = vecStart + j * 4;
            int pos = Table.__indirect(elemOffset, bb);
            switch (at) {
                case Action.DieAction: {
                    DieAction d = new DieAction();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " DieAction target=" + label(d.id())
                            + " type=" + DieType.name(d.dieType()));
                    break;
                }
                case Action.CatPounce: {
                    CatPounce d = new CatPounce();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " CatPounce start=" + loc(d.startLoc())
                            + " end=" + loc(d.endLoc()));
                    break;
                }
                case Action.CatScratch: {
                    CatScratch d = new CatScratch();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " CatScratch loc=" + loc(d.loc()));
                    break;
                }
                case Action.CatFeed: {
                    System.out.println("round " + round + " " + who + " CatFeed");
                    break;
                }
                case Action.DamageAction: {
                    DamageAction d = new DamageAction();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " DamageAction target=" + label(d.id())
                            + " dmg=" + d.damage());
                    break;
                }
                case Action.RatAttack: {
                    System.out.println("round " + round + " " + who + " RatAttack");
                    break;
                }
                case Action.StunAction: {
                    System.out.println("round " + round + " " + who + " StunAction");
                    break;
                }
                case Action.SpawnAction: {
                    SpawnAction sp = new SpawnAction();
                    sp.__init(pos, bb);
                    String lbl = "id" + sp.id() + "(team" + sp.team() + "," + RobotType.name(sp.robotType()) + ")";
                    robotLabel.put(sp.id(), lbl);
                    if (print) {
                        System.out.println("round " + round + " " + who + " SpawnAction -> " + lbl
                                + " at (" + sp.x() + "," + sp.y() + ")");
                    }
                    break;
                }
                case Action.IndicatorStringAction: {
                    IndicatorStringAction d = new IndicatorStringAction();
                    d.__init(pos, bb);
                    String v = d.value();
                    if (v != null && (v.contains("OVERRAN") || v.contains("near-limit"))) {
                        System.out.println("round " + round + " " + who + " indicator: " + v);
                    }
                    break;
                }
                case Action.PlaceTrap: {
                    PlaceTrap d = new PlaceTrap();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " PlaceTrap "
                            + (d.isRatTrapType() ? "RAT" : "CAT") + " loc=" + loc(d.loc()));
                    break;
                }
                case Action.RemoveTrap: {
                    // Iteration 201 needs to prove the King retracts its ring,
                    // and the absence of these lines previously proved nothing
                    // because the action was simply not decoded here.
                    RemoveTrap d = new RemoveTrap();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " RemoveTrap"
                            + " loc=" + loc(d.loc()) + " team=" + d.team());
                    break;
                }
                case Action.TriggerTrap: {
                    TriggerTrap d = new TriggerTrap();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " TriggerTrap");
                    break;
                }
                case Action.RatNap: {
                    // grabRobot() emits this with the CAPTIVE's id in the
                    // payload, while `who` is the GRABBER (the robot whose turn
                    // it is). Printing only `who` left every grab ambiguous --
                    // "team2 RatNap" reads equally well as a team2 rat grabbing
                    // or a team2 rat being grabbed, and the two give opposite
                    // conclusions. That ambiguity is what produced the
                    // Iteration 108 retraction, so print both ends.
                    RatNap d = new RatNap();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " RatNap grabbed=" + label(d.id()));
                    break;
                }
                case Action.RatSqueak: {
                    System.out.println("round " + round + " " + who + " RatSqueak");
                    break;
                }
                // The economy. Omitted from this dump until 2026-09-04, which
                // left the far-map income collapse (our cheeseTransferred flat
                // at 200 while the opponent reached 1480) impossible to
                // diagnose -- the round-header totals show THAT it happens,
                // these show which robot did what.
                case Action.CheesePickup: {
                    CheesePickup d = new CheesePickup();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " CheesePickup loc=" + loc(d.loc()));
                    break;
                }
                case Action.CheeseTransfer: {
                    CheeseTransfer d = new CheeseTransfer();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " CheeseTransfer to=" + label(d.id())
                            + " amount=" + d.amount());
                    break;
                }
                case Action.ThrowRat: {
                    // Same actor/victim split as RatNap: addThrowAction() is
                    // called with robotBeingCarried.getID(), so the payload is
                    // the THROWN rat and `who` is the thrower. Note this is the
                    // opposite convention from RatAttack, whose payload is the
                    // biter (the actor) -- the schema is not consistent, so
                    // never assume which end an id refers to.
                    ThrowRat d = new ThrowRat();
                    d.__init(pos, bb);
                    System.out.println("round " + round + " " + who + " ThrowRat thrown=" + label(d.id())
                            + " to=" + loc(d.loc()));
                    break;
                }
                case Action.RatCollision: {
                    System.out.println("round " + round + " " + who + " RatCollision");
                    break;
                }
                default:
                    // cheese/dirt/trap/comm/indicator noise -- not needed for this focused dump
            }
        }
    }

    interface IntAt { int get(int i); }
    static String vec(IntAt f, int len) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < len; i++) sb.append(f.get(i)).append(i < len - 1 ? "," : "");
        return sb.append("]").toString();
    }

    static void printTerrain(GameMap map, int cx, int cy, int radius) {
        int w = map.size().x(), h = map.size().y();
        System.out.println("  terrain around (" + cx + "," + cy + ") [# wall, . dirt, blank open]:");
        for (int y = Math.min(h - 1, cy + radius); y >= Math.max(0, cy - radius); y--) {
            StringBuilder row = new StringBuilder("    ");
            for (int x = Math.max(0, cx - radius); x <= Math.min(w - 1, cx + radius); x++) {
                int idx = x + w * y;
                char c = (x == cx && y == cy) ? 'K' : map.walls(idx) ? '#' : map.dirt(idx) ? '.' : ' ';
                row.append(c);
            }
            System.out.println(row);
        }
    }

    static String argValue(String[] args, int i, String flag) {
        if (i >= args.length) throw new IllegalArgumentException(flag + " needs a value");
        return args[i];
    }

    static String label(int id) {
        return robotLabel.getOrDefault(id, "id" + id);
    }

    static String loc(int packed) {
        if (mapWidth <= 0) return "(raw=" + packed + ")";
        return "(" + (packed % mapWidth) + "," + (packed / mapWidth) + " raw=" + packed + ")";
    }

    static byte[] readAll(String path) throws IOException {
        try (InputStream in = new FileInputStream(path); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            in.transferTo(bos);
            return bos.toByteArray();
        }
    }
}
