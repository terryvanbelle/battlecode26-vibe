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

    public static void main(String[] args) throws Exception {
        for (int i = 1; i < args.length - 1; i++) {
            if (args[i].equals("--from")) fromRound = Integer.parseInt(args[++i]);
            else if (args[i].equals("--to")) toRound = Integer.parseInt(args[++i]);
            else if (args[i].equals("--robot")) trackRobot = Integer.parseInt(args[++i]);
            else if (args[i].equals("--terrain")) {
                String[] xy = args[++i].split(",");
                terrainX = Integer.parseInt(xy[0]);
                terrainY = Integer.parseInt(xy[1]);
            }
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
                if (round < fromRound || round > toRound) continue;
                // GameWorld.java packs this field as `numRatKings + 10*cheese`
                // ("combine total cheese into the rat kings stat") -- unpack both.
                StringBuilder status = new StringBuilder();
                for (int k = 0; k < r.teamAliveRatKingsLength(); k++) {
                    int combined = r.teamAliveRatKings(k);
                    status.append(r.teamIds(k)).append(":kings=").append(combined % 10)
                            .append(",cheese=").append(combined / 10).append(" ");
                }
                if (round % 25 == 0 || round < 5) {
                    System.out.println("round " + round + " " + status + "aliveBabies="
                            + vec(r::teamAliveBabyRats, r.teamAliveBabyRatsLength())
                            + " catDamage=" + vec(r::teamCatDamage, r.teamCatDamageLength()));
                }
                for (int k = 0; k < r.diedIdsLength(); k++) {
                    System.out.println("round " + round + " DIED id=" + label(r.diedIds(k)));
                }
                for (int ti = 0; ti < r.turnsLength(); ti++) {
                    Turn turn = r.turns(ti);
                    dumpActions(turn, round);
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
    static void dumpActions(Turn turn, int round) {
        int o = turn.__offset(26); // field 11 (0-based) of 13 -> vtable slot (11+2)*2
        if (o == 0) return;
        int len = turn.__vector_len(o);
        int vecStart = turn.__vector(o);
        ByteBuffer bb = turn.getByteBuffer();
        String who = label(turn.robotId());
        for (int j = 0; j < len; j++) {
            byte at = turn.actionsType(j);
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
                    System.out.println("round " + round + " " + who + " SpawnAction -> " + lbl + " at (" + sp.x() + "," + sp.y() + ")");
                    break;
                }
                case Action.IndicatorStringAction: {
                    IndicatorStringAction d = new IndicatorStringAction();
                    d.__init(pos, bb);
                    String v = d.value();
                    if (v != null && v.contains("OVERRAN")) {
                        System.out.println("round " + round + " " + who + " indicator: " + v);
                    }
                    break;
                }
                case Action.RatNap: {
                    System.out.println("round " + round + " " + who + " RatNap");
                    break;
                }
                case Action.ThrowRat: {
                    System.out.println("round " + round + " " + who + " ThrowRat");
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
