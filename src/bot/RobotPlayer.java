package bot;

import battlecode.common.*;

import java.util.Random;

/**
 * Iteration 1 (see TRAINING_ALGORITHM.md): a real cooperation-mode strategy.
 * Rat King grows the population and defends itself; Baby Rats collect and
 * deliver cheese, swarm cats when locally outnumbered-in-our-favor, flee a
 * lone cat otherwise, and never initiate a backstab (they only fight enemy
 * rats once `!rc.isCooperation()`, i.e. the other team already broke coop --
 * see TRAINING_ALGORITHM.md's "Cooperation and backstab strategy").
 *
 * Deliberately still simple: no traps, no ratnap/throw, no dirt digging, no
 * multi-King economy, no cheese-spend-on-bite bonus damage. These are the
 * natural next hypotheses (see TRAINING_LOG.md).
 *
 * Symmetry notes (TRAINING_ALGORITHM.md's "Play symmetry"): no hardcoded
 * compass direction is ever used as a default/fallback -- movement targets
 * are always resolved via `MapLocation.directionTo()` (target-relative) or
 * `Direction.rotateLeft()/rotateRight()` (relative to a target-relative
 * direction), and the per-robot RNG is seeded from `rc.getID()` (unique,
 * not team-correlated) rather than a fixed shared seed -- fixing exactly
 * the anti-pattern BC22's LEARNINGS.md documents ("a shared, fixed-seed
 * Random instance produces identical output for corresponding robots on
 * both teams").
 */
public class RobotPlayer {

    static Random rng;
    static int builtCount = 0;
    static int buildWindowStart = 0;      // Iteration 38, see runRatKing
    static boolean replacementMode = false; // Iteration 39, see runRatKing
    static boolean lastBuildWasTrap = false; // Iteration 48, see runRatKing
    static int cheeseCheckpoint = -1;
    static int cheeseCheckpointRound = 0;
    static boolean economyStruggling = false;
    static MapLocation locOneRoundAgo;
    static MapLocation locTwoRoundsAgo;
    static int stuckCycles = 0;
    static MapLocation exploreLocOneCallAgo;
    static MapLocation exploreLocTwoCallsAgo;
    static int exploreStuckCycles = 0;
    // Bug-navigation state (Iteration 35, see moveToward) -- per-robot, since
    // static fields are per-robot instances in Battlecode, not team-shared.
    static MapLocation bugTarget;
    static int bugClosestDistSq = Integer.MAX_VALUE;
    static boolean bugRotateLeft = false;
    static int bugRoundsFollowing = 0;

    public static void run(RobotController rc) throws GameActionException {
        rng = new Random(rc.getID());
        while (true) {
            int roundAtTurnStart = rc.getRoundNum();
            try {
                if (rc.getType() == UnitType.RAT_KING) {
                    runRatKing(rc);
                } else {
                    runBabyRat(rc);
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Exception: " + e);
                e.printStackTrace();
            } finally {
                reportBytecodeBudget(rc, roundAtTurnStart);
                Clock.yield();
            }
        }
    }

    // ---------------------------------------------------------------- King

    static void runRatKing(RobotController rc) throws GameActionException {
        rc.writeSharedArray(0, rc.getLocation().x + 1);
        rc.writeSharedArray(1, rc.getLocation().y + 1);

        // Iteration 11/12 (TRAINING_LOG.md): `whereisthecheese` (only 2
        // cheese mines) showed the King's own unconditional 2/round upkeep
        // (RULES.md) can outrun income no spending policy prevents. Detect
        // a losing economic trend (every 200 rounds, cheese down more than
        // 150 from the last checkpoint) and latch it permanently -- the
        // same `richHome`-style one-time-branch pattern BC22's
        // LEARNINGS.md found effective for economic doctrine.
        if (rc.getRoundNum() - cheeseCheckpointRound >= 200) {
            if (cheeseCheckpoint >= 0 && cheeseCheckpoint - rc.getGlobalCheese() > 150) {
                economyStruggling = true;
            }
            cheeseCheckpoint = rc.getGlobalCheese();
            cheeseCheckpointRound = rc.getRoundNum();
        }
        // Once the economy is both on a losing trend *and* actually
        // critical (cheese already below RESERVE), broadcast desperation.
        // Iteration 11 tried making desperate Baby Rats merely *willing*
        // to fight a sighted enemy pre-backstab -- rejected as inert,
        // because on this map Kings spawn at opposite corners and nothing
        // ever proactively closes that distance. Iteration 12 adds the
        // missing piece: also broadcast a guessed enemy-King location so
        // desperate rats have somewhere to actually go, instead of only
        // reacting to whatever they happen to stumble across. Shared
        // array slot 2: 1 = desperate. Slots 3/4: guessed enemy King
        // x/y+1 (0 = not yet computed).
        final int RESERVE = 150;
        boolean desperate = economyStruggling && rc.getGlobalCheese() < RESERVE;
        if (desperate) {
            rc.writeSharedArray(2, 1);
            if (rc.readSharedArray(3) == 0) {
                // Best guess at the enemy King's location without ever
                // having seen it: maps are guaranteed symmetric
                // (RULES.md), but the specific symmetry type (rotation vs.
                // horizontal/vertical reflection) isn't exposed by
                // RobotController -- only width/height are. 180-degree
                // rotation is guessed as the single most common case
                // (BC22's LEARNINGS.md: even there, where this *was*
                // queryable, several maps turned out non-rotational, so
                // this is a real, accepted source of error, not a
                // guaranteed-correct computation) -- a wrong guess still
                // sends rats generally away from home and across the map
                // rather than continuing to wander locally, which is
                // strictly more likely to encounter the enemy than not
                // trying at all.
                int guessX = rc.getMapWidth() - 1 - rc.getLocation().x;
                int guessY = rc.getMapHeight() - 1 - rc.getLocation().y;
                rc.writeSharedArray(3, guessX + 1);
                rc.writeSharedArray(4, guessY + 1);
            }
        }

        attackNearestHostile(rc, desperate);

        // The King never moved at all in the first cut of this iteration.
        // Cats patrol fixed, map-specific waypoints (RULES.md), so a King
        // that never relocates is a sitting target once one's cycle brings
        // it nearby -- react the same way a Baby Rat does: flee a close cat.
        // (Movement is expensive -- movementCooldown 40 -- so this is a
        // last-resort reflex, not a relocation strategy; still strictly
        // better than never moving at all. Not yet confirmed as a cause of
        // any specific loss -- see the build-throttle note below for the
        // fix that *is* replay-confirmed.)
        RobotInfo[] nearby = rc.senseNearbyRobots();
        RobotInfo nearestCat = nearestOfType(rc, nearby, UnitType.CAT);
        if (nearestCat != null && nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= 20) {
            flee(rc, nearestCat.getLocation());
        }

        pickUpBestNearbyCheese(rc);

        // Confirmed root cause of the first cut's round-145 loss on `tiny`
        // (tools/replay-dump.sh; see TRAINING_LOG.md): the King built Baby
        // Rats every round it could afford to, with no regard for whether
        // income (cheese delivered by those very rats) was keeping pace.
        // Team cheese hit 0 by round ~100 (40 rats built from a starting
        // 2500) while the opponent -- who never builds anything -- still
        // had 2300; the King then starved (RULES.md: 2 cheese/round upkeep,
        // or 10 HP damage if it can't pay) and died at round 145. Keep a
        // fixed reserve so a temporary income gap (economy still ramping
        // up, or a cat blocking access to nearby cheese) can't bankrupt the
        // King outright.

        // Second, independent throttle -- replay evidence on `knifefight`
        // (TRAINING_LOG.md): across 40 builds, findBuildLocation() only ever
        // returned 2 distinct tiles, both right next to the King. That reads
        // as spatial gridlock, not bad luck: cramming dozens of Baby Rats
        // into a tight spawn area (this map's name suggests a chokepoint)
        // means most of them can never leave -- every adjacent tile is
        // already occupied by another rat -- so nobody ever reaches cheese
        // regardless of how good the wander/search logic is. Cap population
        // growth independently of the cheese reserve above.
        //
        // builtCount is *cumulative ever built*, not a live census -- there
        // is no RobotController API for a team-wide live unit count, and a
        // King's own vision (radius^2 25) can't see rats that have wandered
        // off. This errs in the safe direction for a cap specifically
        // (stopping growth a bit early if some built rats already died is a
        // minor inefficiency); it would be the dangerous direction for a
        // "do we have enough" *floor* check, per BC22's LEARNINGS.md
        // cumulative-vs-live pitfall -- not the mistake being made here.
        //
        // Iteration 9 (TRAINING_LOG.md): 15 was picked purely to solve
        // knifefight's spawn gridlock and never revisited since. Two
        // rejected retreat-logic attempts both point at `closeup`'s
        // population bleed against a dedicated aggressor being a
        // cumulative-attrition problem instead -- a healthy, competitive
        // economy (cheeseTransferred roughly even) that the population cap
        // stops from ever converting into a standing army large enough to
        // outlast a war of attrition. Raising it, not yet Gauntlet-verified
        // -- see TRAINING_LOG.md for whether this reintroduces gridlock on
        // knifefight (the reason 15 was chosen) or actually helps.
        // Iteration 38 (TRAINING_LOG.md): a **sliding build budget**, derived
        // from measurement rather than guessed. Six previous attempts to fix
        // this (Iterations 28-31, 34, 37) all tried to *slow* the King down
        // -- scaling reserves, cooldowns, hysteresis, trend detection,
        // congestion limits -- and every single one made things worse.
        // Measuring an actual winning game finally explained why: the King
        // builds all 25 rats in rounds **1 through 25, one per round,
        // back-to-back**, and then never builds again. The maximal early
        // burst isn't the bug, it's the proven-good behavior; every throttle
        // attempt was damaging the thing that works.
        //
        // The only real defect is the second half of that sentence: because
        // `builtCount` is cumulative-ever-built, once it reaches the cap the
        // King is locked out of *replacing* losses for the rest of the game,
        // no matter how many rats have died (confirmed on `closeup`, `tiny`,
        // and `whereisthecheese`). So: keep the cap's per-window value
        // exactly as-is -- preserving the round-1-to-25 burst byte-for-byte
        // -- but let the budget refresh periodically, permitting replacement
        // building later without ever allowing a faster-than-proven ramp.
        // Iteration 39: make replacement building respect its *real* cost.
        // Iteration 38 tied the baseline but broke `minimaze`/`pipes`; the
        // replay showed why, and it was not the congestion story the map
        // names suggested -- the round-400 replacement burst built ~21 rats
        // in 20 rounds and took the treasury from 1660 to 150, never
        // recovering until the King starved.
        //
        // The cause is `BUILD_ROBOT_COST_INCREASE = 10*floor(pop/4)`
        // (RULES.md): build cost scales with *current* population. The
        // opening burst is cheap because population starts at zero and the
        // price ramps up as it grows; a replacement burst beginning at
        // population ~19 starts already-expensive and compounds. "25
        // builds" in round 400 therefore costs far more than the identical
        // "25 builds" in round 1, and refreshing the budget to the same
        // number was never cost-equivalent.
        //
        // So the opening burst keeps spending down to `RESERVE` exactly as
        // it always has (unchanged, proven), while replacement windows
        // require a much deeper buffer -- they may only draw on genuine
        // surplus, never on the King's survival margin. This is the
        // escalating-threshold pattern BC22's `LEARNINGS.md` records for
        // discretionary spending: a committed investment (the opening army)
        // and a discretionary one (topping it back up) should not be gated
        // at the same bar.
        final int MAX_POPULATION = 25;
        final int BUILD_WINDOW_ROUNDS = 400;
        final int REPLACEMENT_RESERVE = 1000;
        if (rc.getRoundNum() - buildWindowStart >= BUILD_WINDOW_ROUNDS) {
            buildWindowStart = rc.getRoundNum();
            builtCount = 0;
            replacementMode = true;
        }
        // Iteration 40: emergency override. On `tiny`, tracing the one
        // remaining loss showed the King sitting on 800-950 cheese with
        // **zero** living Baby Rats from round 575 onward, slowly starving
        // while holding it -- because that's below `REPLACEMENT_RESERVE`, so
        // rebuilding was blocked at exactly the moment it mattered most.
        // Hoarding a reserve with no army is strictly worse than spending
        // it: the reserve exists to keep the King alive, and an undefended
        // King with no economy dies anyway. When no allied Baby Rat is
        // visible at all, fall back to the ordinary `RESERVE` bar. (Rats
        // alive but outside the King's vision aren't defending it or
        // feeding it either, and the per-window cap still bounds the
        // response, so the downside of a false positive is small.)
        boolean noVisibleArmy = true;
        for (RobotInfo info : nearby) {
            if (info.getType() == UnitType.BABY_RAT && info.getTeam() == rc.getTeam()) {
                noVisibleArmy = false;
                break;
            }
        }
        int buildReserve = (replacementMode && !noVisibleArmy) ? REPLACEMENT_RESERVE : RESERVE;
        // Iteration 48 (TRAINING_LOG.md): **ring the King with rat traps.**
        // The external benchmark showed tournament bots killing us in
        // 21-46 rounds by swarming the King with 7-10 rats, and proved
        // that rearranging our own units cannot stop it (a standing guard
        // of a third of the army changed the result by zero rounds).
        // Traps are the counter we already had and never used:
        // `TrapType.RAT_TRAP` is **50 damage and a 30-round stun for 20
        // cheese, with maxCount 25** -- half an attacker's 100 HP and it
        // is removed from the fight for 30 rounds, at a total cost of 500
        // cheese for a full set against a treasury averaging 4220.
        //
        // Iteration 15 tried traps and rejected them as "never triggered",
        // but that was against peers that never rushed the King; traps
        // laid around a King that nobody attacks are inert by
        // construction. Against opponents whose whole opening is a King
        // rush, the same traps sit exactly on the attack path. The
        // mechanic didn't change -- the opposition did.
        //
        // Interleaved with building rather than deferred until after it:
        // the opening burst runs rounds 1-25 and `bench_spaark` finishes
        // us at round 21, so traps laid only after the burst would arrive
        // after we are already dead. Alternating from round 5 yields
        // roughly ten traps down by round 25 while still building most of
        // the army.
        boolean placedTrap = false;
        if (builtCount >= 5 && !lastBuildWasTrap && rc.getGlobalCheese() > RESERVE + 100) {
            MapLocation trapSpot = findTrapLocation(rc);
            if (trapSpot != null && rc.canPlaceRatTrap(trapSpot)) {
                rc.placeRatTrap(trapSpot);
                placedTrap = true;
                lastBuildWasTrap = true;
            }
        }
        if (placedTrap) {
            rc.setIndicatorString("king trap laid; traps=" + rc.getNumberRatTraps());
            return;
        }
        MapLocation buildLoc = findBuildLocation(rc);
        if (buildLoc != null && rc.canBuildRat(buildLoc)
                && rc.getGlobalCheese() - rc.getCurrentRatCost() >= buildReserve
                && builtCount < MAX_POPULATION) {
            rc.buildRat(buildLoc);
            builtCount++;
            lastBuildWasTrap = false;
        } else if (buildLoc == null) {
            // Replay evidence on `closeup` (TRAINING_LOG.md, tools/replay-dump.sh's
            // new terrain dump): both Kings spawned boxed in entirely by DIRT
            // (impassable until dug, unlike a permanent wall), with zero open
            // tile anywhere in the build radius -- 0 Baby Rats built the whole
            // game, for either team, on this map specifically. Dig out.
            digTowardOpenSpace(rc);
        }

        rc.setIndicatorString("king cheese=" + rc.getGlobalCheese()
                + (nearestCat != null ? " cat@" + nearestCat.getLocation() : ""));
    }

    static void digTowardOpenSpace(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (!info.isDirt()) continue;
            if (!rc.canRemoveDirt(info.getMapLocation())) continue;
            int d = info.getMapLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info.getMapLocation();
            }
        }
        if (best != null) {
            rc.removeDirt(best);
        }
    }

    /**
     * Iteration 48: a tile to trap, preferring the ring just outside the
     * King rather than right against it -- attackers must cross that ring
     * to reach the King, and a trap on the King's own doorstep is one an
     * attacker only touches after it is already in bite range.
     */
    static MapLocation findTrapLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestScore = Integer.MIN_VALUE;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(me,
                GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED)) {
            if (!rc.canPlaceRatTrap(loc)) continue;
            int d = loc.distanceSquaredTo(me);
            int score = -Math.abs(d - 5); // prefer the ring at distance^2 ~5
            if (score > bestScore) {
                bestScore = score;
                best = loc;
            }
        }
        return best;
    }

    static MapLocation findBuildLocation(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapLocation best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(me, GameConstants.RAT_KING_BUILD_DISTANCE_SQUARED)) {
            if (!rc.canBuildRat(loc)) continue;
            int d = loc.distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = loc;
            }
        }
        return best;
    }

    // ------------------------------------------------------------ Baby Rat

    static void runBabyRat(RobotController rc) throws GameActionException {
        // Iteration 24 (TRAINING_LOG.md): tracked once per round, here,
        // rather than inside tryMove() itself -- deliverCheese()/
        // collectCheese()/engage()/flee() can each fall through to a
        // further movement attempt in the same turn if an earlier one
        // fails, so tryMove() can run 0-2 times per round and isn't a
        // reliable place to count rounds. Detects a stable 2-tile
        // oscillation (see tryMove() for what it does about it).
        MapLocation here = rc.getLocation();
        if (here.equals(locTwoRoundsAgo)) {
            stuckCycles++;
        } else {
            stuckCycles = 0;
        }
        locTwoRoundsAgo = locOneRoundAgo;
        locOneRoundAgo = here;

        RobotInfo[] nearby = rc.senseNearbyRobots();

        MapLocation kingLoc = readHomeKingFromSharedArray(rc);
        for (RobotInfo info : nearby) {
            if (info.getType() == UnitType.RAT_KING && info.getTeam() == rc.getTeam()) {
                kingLoc = info.getLocation(); // freshest -- overrides the shared-array value
            }
        }

        if (rc.getRawCheese() > 0 && kingLoc != null) {
            if (deliverCheese(rc, kingLoc)) return;
        }

        RobotInfo nearestCat = nearestOfType(rc, nearby, UnitType.CAT);
        if (nearestCat != null) {
            int allies = countAlliesNear(rc, nearby, nearestCat.getLocation(), 8);
            // Replay evidence (TRAINING_LOG.md, `pure_cooperator` mirror-match
            // trace): catDamage stayed [0,0] all session despite cats visibly
            // scratching/killing our own rats dozens of times per game -- a
            // lone rat within range 8 always fled without ever attacking,
            // even when already in bite range (ATTACK_DISTANCE_SQUARED=2) of
            // the cat that's about to hit it anyway. Take the free hit before
            // (or instead of) fleeing -- it costs nothing extra this turn,
            // the cat isn't going to *not* attack because we didn't, and it's
            // the only thing that's ever put a nonzero number in catDamage.
            if (rc.canAttack(nearestCat.getLocation())) {
                // Iteration 45 (TRAINING_LOG.md): pay a small amount of cheese
                // per bite to raise cat damage. The engine's formula is
                // `damage += ceil(sqrt(cheeseConsumed))` (InternalRobot.bite),
                // so returns diminish sharply and *small* boosts are by far
                // the most efficient: 4 cheese buys +2 damage on a base of
                // RAT_BITE_DAMAGE=10 (a 20% increase at 0.5 damage/cheese),
                // whereas 100 cheese would buy only +10 (0.1 damage/cheese).
                //
                // Measured justification: all seven points-losses are lost on
                // `catDamage` (0.5 weight) and won on `cheeseTransferred`
                // (0.2 weight), and our treasury averages 4220 across a game
                // (max 8300) -- we are rich in the cheap currency and poor in
                // the expensive one. Spending a little of the former per bite
                // converts directly into the latter at a favourable exchange
                // rate, with no change to positioning or contact time, which
                // is what the three failed "go hunt cats" attempts
                // (Iterations 22, 27, 44) all tried to change and could not.
                //
                // Distinct from Iteration 16's rejected cheese-bite: that ran
                // when cheese was genuinely scarce and its spending pulled
                // the desperation latch early, causing a second-order
                // collapse. Cheese is now abundant and the latch behaves
                // differently since Iterations 38-40.
                final int BITE_BOOST_CHEESE = 4;
                if (rc.getGlobalCheese() > 1000
                        && rc.canAttack(nearestCat.getLocation(), BITE_BOOST_CHEESE)) {
                    rc.attack(nearestCat.getLocation(), BITE_BOOST_CHEESE);
                } else {
                    rc.attack(nearestCat.getLocation());
                }
                return;
            }
            // High-risk structural change (TRAINING_ALGORITHM.md): the
            // >=3-ally swarm gate essentially never fired -- rats
            // deliberately spread out for cheese search (Iteration 1-4),
            // so 3 of them converging on the same cat at once was rare
            // luck, not a real policy. Meanwhile a cat's scratch reaches
            // its whole vision cone (radius^2 17, ~4.1 tiles) but a Baby
            // Rat's bite only reaches range 2 (~1.4 tiles), so the old
            // "flee anything within 8" threshold kept a fleeing rat
            // inside the cat's engagement range the entire time it was
            // trying to escape, without ever closing to bite range either
            // -- worst of both outcomes.
            //
            // DPS math favors fighting once adjacent regardless of allies:
            // CAT_SCRATCH_DAMAGE=20 every ~3 rounds (actionCooldown 30)
            // = ~6.67 dmg/round average, vs. RAT_BITE_DAMAGE=10 every
            // round (actionCooldown 10) = 10 dmg/round -- a lone Baby Rat
            // that reaches melee range out-trades a cat once there, even
            // though it can't out-tank one (100 HP vs. 4000). A rat that
            // commits instead of fleeing trades a cheap unit (~10-30
            // cheese) for real, otherwise-nonexistent cat damage. Not yet
            // Gauntlet-verified -- see TRAINING_LOG.md for the result.
            if (allies > 1 || rc.getHealth() > 30) {
                if (engage(rc, nearestCat.getLocation())) return;
            } else if (nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= 8) {
                // Critically low HP and no help nearby: not worth dying on
                // the approach for a hit that likely never lands. Flee.
                if (flee(rc, nearestCat.getLocation())) return;
            }
        }

        // Iteration 11 (TRAINING_LOG.md): shared array slot 2 -- written by
        // our own King -- signals our own economy is critically struggling.
        // A Baby Rat is willing to deliberately trigger a backstab against
        // a sighted enemy even while still nominally cooperating: certain
        // starvation is worse than betting on a combat edge we've already
        // demonstrated (see the King-side comment for the full reasoning).
        boolean desperate = rc.readSharedArray(2) == 1;
        if (!rc.isCooperation() || desperate) {
            // Replay evidence (TRAINING_LOG.md, `closeup` vs. `immediate_defector`):
            // this only ever attacked an enemy rat already in bite range,
            // never closed distance on one it could see but not yet reach --
            // a purely passive defense against an opponent that actively
            // hunts us. Our own population went to *zero* by round 800 (all
            // Baby Rats killed, none replaced fast enough) while our King
            // starved alone afterward with no economy left. Chase like
            // cat-engagement already does, instead of waiting to get lucky.
            RobotInfo enemy = nearestEnemyRat(rc, nearby);
            if (enemy != null) {
                if (rc.canAttack(enemy.getLocation())) {
                    rc.attack(enemy.getLocation());
                    return;
                }
                if (enemy.getLocation().distanceSquaredTo(rc.getLocation()) <= 8) {
                    if (engage(rc, enemy.getLocation())) return;
                }
            }
            // Iteration 12 (TRAINING_LOG.md): Iteration 11's desperation
            // signal alone was rejected as inert -- rats only ever *react*
            // to an enemy already sighted, and on maps with distant spawns
            // that never happens. When desperate with no enemy currently
            // visible, actively path toward the King's guessed
            // enemy-King location (shared array slots 3/4 -- a 180-degree-
            // rotation guess, not guaranteed correct, see the King-side
            // comment) instead of continuing normal cheese/explore
            // behavior. This deliberately forces a crossing instead of
            // waiting for one.
            if (enemy == null && desperate) {
                int gx = rc.readSharedArray(3);
                int gy = rc.readSharedArray(4);
                if (gx != 0 && gy != 0) {
                    if (moveToward(rc, new MapLocation(gx - 1, gy - 1), true)) return;
                }
            }
        }

        // Iteration 74 (TRAINING_LOG.md): become a second Rat King when the
        // crowd for it is already standing here.
        //
        // This re-opens a documented dead end. "Multi-King costs 7 rats each"
        // was rejected on the premise that rats are a scarce asset worth
        // preserving. Three of this session's measurements say they are not:
        //
        //  - The death rate is ~**0.057 per round in every variant tested**
        //    (control 0.0570, throw-always 0.0583, throw-opening 0.0589), so
        //    seven rats not spent here are seven rats that die anyway inside
        //    ~100 rounds.
        //  - Iteration 60 doubled window-0 spawns 25 -> 50 and left live rats
        //    at exactly **4**: the standing army cannot be grown by building.
        //    Rats are a flow we cannot bank, which is precisely the asset to
        //    convert into something permanent.
        //  - `livingKings` is a PROPORTIONAL term, weight 0.3 while
        //    cooperating and **0.5** afterwards -- and cooperation ends around
        //    round 39 in real games, so 0.5 is the operative weight. Both
        //    sides hold one King today, so we split that term 25/25. Two
        //    Kings against their one is 33/17: an 8-point swing on the
        //    largest term on the board.
        //
        // It also answers the failure shape that sank trap avoidance and
        // throwing: those taxed rat-turns every round, and with 4-8 living
        // rats we cannot afford an ongoing tax. An upgrade is a ONE-TIME
        // conversion that costs nothing thereafter -- and it buys a unit with
        // 600 HP against a Baby Rat's 100, defending against the King-death
        // losses that are 91% of how we lose.
        //
        // Gated on the 7 allies ALREADY being adjacent rather than gathering
        // them: `becomeRatKing` kills every ally in the 3x3, so paying for a
        // rendezvous would be the ongoing rat-turn tax all over again. The
        // condition is naturally met near our King during the opening build
        // burst, which is also when cheese is plentiful.
        if (rc.canBecomeRatKing()
                && rc.getGlobalCheese() > GameConstants.RAT_KING_UPGRADE_CHEESE_COST + 400) {
            rc.becomeRatKing();
            rc.setIndicatorString("upgraded to Rat King");
            return;
        }

        // Iteration 75 (TRAINING_LOG.md): RALLY to make the upgrade possible.
        //
        // Iteration 74 waited for `canBecomeRatKing()` to become true on its
        // own and recorded **zero** upgrades in a full game: the call needs 7
        // allies packed into a 3x3, and our 4-8 dispersed collectors never
        // assemble that by accident. `bench_finalist` does it five times
        // (kings at rounds 125, 325, 375, 825) because it fields 56 rats and
        // the crowd happens for free.
        //
        // What that costs us is the largest deficit measured all session.
        // `livingKings` is proportional at weight 0.5 once cooperation ends,
        // so their 5 Kings against our 1 is **42 points against 8** -- ~34
        // points of a 50-point term conceded before the game starts, worse
        // than the catDamage gap. Five Kings is also five deaths required
        // before their instant-loss condition triggers, against our one.
        //
        // Why a rally is affordable when trap-avoidance and throwing were
        // not: those charged rat-turns EVERY ROUND forever, which at 4-8 rats
        // halved our economy (see the per-capita finding). This charges once,
        // inside a bounded window, and returns a permanent 600 HP unit that
        // holds a share of the biggest term on the board.
        //
        // Rally point is offset from the King rather than on it: `RAT_KING`
        // has `size` 3, so the King already occupies a 3x3 footprint and rats
        // cannot pack around its centre.
        final int RALLY_FROM = 25, RALLY_UNTIL = 90;
        if (kingLoc != null
                && rc.getRoundNum() >= RALLY_FROM && rc.getRoundNum() <= RALLY_UNTIL
                && rc.getGlobalCheese() > GameConstants.RAT_KING_UPGRADE_CHEESE_COST + 400
                && rc.getRawCheese() == 0) {
            MapLocation rally = kingLoc.translate(3, 3);
            if (rc.getLocation().distanceSquaredTo(rally) > 2) {
                if (moveToward(rc, rally, false)) return;
            }
        }

        if (collectCheese(rc)) return;

        explore(rc);
    }

    static MapLocation readHomeKingFromSharedArray(RobotController rc) throws GameActionException {
        int kx = rc.readSharedArray(0);
        int ky = rc.readSharedArray(1);
        if (kx == 0 || ky == 0) return null; // never written yet
        return new MapLocation(kx - 1, ky - 1);
    }

    static boolean deliverCheese(RobotController rc, MapLocation kingLoc) throws GameActionException {
        if (rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            rc.transferCheese(kingLoc, rc.getRawCheese());
            return true;
        }
        // Same fix as collectCheese() -- only claim the turn if movement
        // actually succeeded, so a blocked path falls through to
        // cat-engagement/explore instead of camping in place forever. Still
        // carrying cheese, so this is retried first again next turn.
        //
        // The one caller that gets bug-navigation (Iteration 35): the King is
        // a genuinely fixed target, and this is exactly where the confirmed
        // 340-round undelivered-cheese maze trap was traced.
        return moveToward(rc, kingLoc, true, true);
    }

    static boolean collectCheese(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos()) {
            if (info.getCheeseAmount() <= 0) continue;
            int d = info.getMapLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        if (best == null) return false;
        MapLocation loc = best.getMapLocation();
        if (rc.canPickUpCheese(loc)) {
            rc.pickUpCheese(loc);
            return true;
        }
        // Replay evidence (TRAINING_LOG.md, `knifefight`, tools/replay-dump.sh's
        // --robot tracker): this used to unconditionally return true once *any*
        // cheese tile was sighted, even one that turned out unreachable (behind
        // an obstacle `moveToward`'s single-step routing can't get around). That
        // permanently starved `explore()` of ever running again for that robot
        // -- three independently-tracked rats each got stuck at a fixed (x,y)
        // for hundreds of rounds, moveCD/turnCD stuck at 0 (fully able to act)
        // the entire time, because every single turn re-chose the same stuck
        // target instead of ever giving up on it. Now: only claim this turn if
        // movement toward the target actually succeeded, so a genuinely
        // unreachable target falls through to explore() instead of camping
        // forever.
        return moveToward(rc, loc, true);
    }

    static boolean engage(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.canAttack(target)) {
            rc.attack(target);
            return true;
        }
        // Same fix as collectCheese()/deliverCheese() -- claim the turn only
        // if movement actually made progress.
        return moveToward(rc, target);
    }

    static boolean flee(RobotController rc, MapLocation threat) throws GameActionException {
        Direction away = rc.getLocation().directionTo(threat).opposite();
        return tryMove(rc, away);
    }

    // ------------------------------------------------------------- Shared

    static void attackNearestHostile(RobotController rc, boolean desperate) throws GameActionException {
        MapLocation me = rc.getLocation();
        int rangeSq = rc.getType() == UnitType.RAT_KING
                ? GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED
                : GameConstants.ATTACK_DISTANCE_SQUARED;
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : rc.senseNearbyRobots(rangeSq)) {
            boolean hostile = info.getType() == UnitType.CAT
                    || ((!rc.isCooperation() || desperate) && info.getTeam() != rc.getTeam());
            if (!hostile) continue;
            if (!rc.canAttack(info.getLocation())) continue;
            int d = info.getLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        if (best != null) {
            rc.attack(best.getLocation());
        }
    }

    static void pickUpBestNearbyCheese(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        MapInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (MapInfo info : rc.senseNearbyMapInfos(GameConstants.CHEESE_PICK_UP_RADIUS_SQUARED)) {
            if (info.getCheeseAmount() <= 0) continue;
            if (!rc.canPickUpCheese(info.getMapLocation())) continue;
            int d = info.getMapLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        if (best != null) {
            rc.pickUpCheese(best.getMapLocation());
        }
    }

    static RobotInfo nearestOfType(RobotController rc, RobotInfo[] nearby, UnitType type) {
        MapLocation me = rc.getLocation();
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : nearby) {
            if (info.getType() != type) continue;
            int d = info.getLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        return best;
    }

    static RobotInfo nearestEnemyRat(RobotController rc, RobotInfo[] nearby) {
        MapLocation me = rc.getLocation();
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : nearby) {
            if (info.getTeam() == rc.getTeam()) continue;
            if (info.getType() == UnitType.CAT) continue;
            int d = info.getLocation().distanceSquaredTo(me);
            if (d < bestDist) {
                bestDist = d;
                best = info;
            }
        }
        return best;
    }

    static int countAlliesNear(RobotController rc, RobotInfo[] nearby, MapLocation point, int radiusSquared) {
        int count = 1; // self
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam()) continue;
            if (info.getType() != UnitType.BABY_RAT) continue;
            if (info.getLocation().distanceSquaredTo(point) <= radiusSquared) count++;
        }
        return count;
    }

    static boolean moveToward(RobotController rc, MapLocation target) throws GameActionException {
        return moveToward(rc, target, false);
    }

    static boolean moveToward(RobotController rc, MapLocation target, boolean allowStuckEscape) throws GameActionException {
        return moveToward(rc, target, allowStuckEscape, false);
    }

    /**
     * Iteration 35 (TRAINING_LOG.md): Bug2-style navigation, replacing the
     * old "point at the target, sidestep 45 degrees if blocked" greedy
     * step. Iteration 33 established (four failed stuck-detection patches,
     * plus a direct `--terrain` read showing genuine maze corridors between
     * a stuck rat and its King) that greedy target-directed movement has no
     * notion of a *path*, only a heading, and no amount of detect-and-escape
     * patching fixes that -- it needs real obstacle navigation.
     *
     * Implements the pattern BC22's `RESEARCH.md` section 2 documents as the
     * cross-year convergent solution (every strong team starts with textbook
     * A-star or BFS, blows the bytecode budget, and ends up here):
     *   1. Move directly toward the target when that works.
     *   2. When blocked, follow the obstacle boundary, rotating consistently
     *      in one direction, until the direct path clears.
     *   3. Escape concave ("C"-shaped) obstacles -- plain bug-nav loops
     *      forever in these -- by only resuming direct movement once
     *      strictly closer to the target than at any point since wall-
     *      following began (the classic Bug2 improvement; `RESEARCH.md`
     *      cites Gone Fishin's directional-stack equivalent).
     *   4. Randomized tie-break as a last-resort safety valve (retained via
     *      `tryMove`'s existing stuck-escape shuffle).
     * Deliberately no dynamic collections or allocation per `RESEARCH.md`
     * section 10 (bytecode-aware structures over standard-library defaults)
     * -- just a few per-robot ints/enums and a bounded 8-step rotation scan.
     *
     * **Scoped to fixed targets only** (`useBugNav`). A first cut applied
     * this to every `moveToward()` caller and regressed the full Gauntlet
     * (65.0%, down from 75.0%), concentrated hard on `minimaze` (newly lost
     * all four pairings). Diagnosis: bug-navigation's whole premise is
     * making monotonic progress toward a *stationary* goal -- the
     * closest-distance-so-far memory is what escapes concave obstacles. A
     * moving goal (chasing an enemy rat in `engage()`, fleeing a cat, or
     * re-picking the nearest cheese tile as tiles deplete) invalidates that
     * memory every round, so the state thrashes: it resets on every target
     * change, never accumulates the history the escape logic depends on,
     * and meanwhile the wall-following scan replaces the responsive
     * 45-degree sidestep that combat actually wants. Only `deliverCheese()`
     * has a genuinely fixed target (the King) -- which is also exactly
     * where the confirmed 340-round stuck-cheese bug was traced.
     */
    static boolean moveToward(RobotController rc, MapLocation target, boolean allowStuckEscape,
                              boolean useBugNav) throws GameActionException {
        MapLocation here = rc.getLocation();
        if (here.equals(target)) return true;
        if (!useBugNav) {
            return tryMove(rc, here.directionTo(target), allowStuckEscape);
        }

        // Reset bug state whenever the goal changes -- distance progress is
        // only meaningful relative to a single fixed target.
        if (bugTarget == null || !bugTarget.equals(target)) {
            bugTarget = target;
            bugClosestDistSq = Integer.MAX_VALUE;
            bugRoundsFollowing = 0;
            // Per-robot, non-team-correlated rotation preference (see the
            // class-level symmetry note -- a fixed left-before-right order
            // is exactly the absolute-bias anti-pattern BC22's LEARNINGS.md
            // documents as its largest recurring bug class).
            bugRotateLeft = (rc.getID() % 2 == 0);
        }

        int distSq = here.distanceSquaredTo(target);
        Direction toTarget = here.directionTo(target);

        // Correct Bug2 structure: *always* attempt the direct move when not
        // currently committed to tracing a boundary, and use the
        // closest-distance memory only to decide when it's safe to *leave*
        // wall-following. A first cut had this backwards -- it gated the
        // direct-move attempt itself on "strictly closer than ever before,"
        // which meant a rat pushed backwards (delivery congestion near the
        // King, or the King itself relocating to flee a cat) could never
        // satisfy the condition again and would wall-follow forever. That
        // reintroduced the exact permanently-stuck failure class this whole
        // iteration exists to remove, and cost 15 points of win rate
        // (60.0%) before being caught.
        if (bugRoundsFollowing == 0) {
            if (tryMoveDirect(rc, toTarget)) {
                if (distSq < bugClosestDistSq) bugClosestDistSq = distSq;
                return true;
            }
            // Only commit to tracing a boundary if the thing blocking us is
            // actually *terrain*. Bug-navigation assumes static obstacles;
            // most blocks here are transient (another rat standing in the
            // way for a round, especially in delivery traffic near the
            // King), and committing to a multi-round boundary trace over a
            // blocker that would have cleared on its own is strictly worse
            // than the old one-step sidestep -- measured at 12.5 points of
            // win rate across two Gauntlets before this check was added.
            // `RESEARCH.md` section 2 names this exact distinction:
            // "treat friendly units as soft, not hard, obstacles."
            MapLocation ahead = here.add(toTarget);
            boolean terrainBlocked = false;
            if (rc.canSenseLocation(ahead)) {
                terrainBlocked = !rc.senseMapInfo(ahead).isPassable();
            }
            if (!terrainBlocked) {
                return tryMove(rc, toTarget, allowStuckEscape);
            }
            // Direct path is blocked by real terrain -- commit to tracing
            // this obstacle, remembering how close we were when we hit it.
            bugClosestDistSq = distSq;
        } else if (distSq < bugClosestDistSq && tryMoveDirect(rc, toTarget)) {
            // Made real progress past the obstacle: resume direct approach.
            bugClosestDistSq = distSq;
            bugRoundsFollowing = 0;
            return true;
        }

        // Follow the obstacle boundary. Scan a bounded 8 steps rotating
        // consistently from the target-relative heading, so the traced
        // boundary is continuous rather than jumping sides each round.
        bugRoundsFollowing++;
        Direction d = toTarget;
        for (int i = 0; i < 8; i++) {
            d = bugRotateLeft ? d.rotateLeft() : d.rotateRight();
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }

        // Fully enclosed, or wall-following has run long enough to suspect a
        // trap the boundary trace can't resolve (e.g. a pocket whose only
        // exit is back the way we came). Fall through to the existing
        // randomized escape, which `tryMove` already gates on confirmed
        // stuck-ness -- `RESEARCH.md`'s "randomized tie-break as a
        // last-resort safety valve against infinite loops."
        if (bugRoundsFollowing > 16) {
            bugClosestDistSq = Integer.MAX_VALUE; // let direct approach retry
            bugRoundsFollowing = 0;
            bugRotateLeft = !bugRotateLeft;       // try the other way around
        }
        return tryMove(rc, toTarget, allowStuckEscape);
    }

    /** Turn-and-move strictly along `want`, no sidestep. */
    static boolean tryMoveDirect(RobotController rc, Direction want) throws GameActionException {
        if (want == Direction.CENTER) return false;
        if (rc.getDirection() != want && rc.canTurn(want)) {
            rc.turn(want);
        }
        if (rc.getDirection() == want && rc.canMoveForward()) {
            rc.moveForward();
            return true;
        }
        if (rc.canMove(want)) {
            rc.move(want);
            return true;
        }
        return false;
    }

    /**
     * Turn-and/or-move toward `want`, using only directions derived from
     * `want` itself (never a fixed compass fallback -- see the class-level
     * symmetry note). If blocked, sidesteps via `want`'s own rotateLeft/
     * rotateRight, tie-broken by `rc.getID() % 2` (per-robot, not
     * team-correlated) rather than a fixed left-before-right order.
     */
    static boolean tryMove(RobotController rc, Direction want) throws GameActionException {
        return tryMove(rc, want, false);
    }

    /**
     * Iteration 24 attempt (TRAINING_LOG.md): `allowStuckEscape` scopes the
     * random-direction stuck-breaker (below) to "economic" travel only
     * (deliverCheese/collectCheese/explore/backstab-hunt-chase) -- the
     * first full-Gauntlet run applied it everywhere and traced a new,
     * concentrated regression against `immediate_defector` (`knifefight`
     * population collapsed 13->2 in 75 rounds, much faster than baseline):
     * legitimate back-and-forth movement during active combat can trip the
     * same 2-tile-repeat detector as a genuine terrain trap, and injecting
     * a random direction mid-fight is actively harmful there. `engage()`/
     * `flee()` keep the old deterministic tiebreak unconditionally.
     */
    static boolean tryMove(RobotController rc, Direction want, boolean allowStuckEscape) throws GameActionException {
        if (want == Direction.CENTER) return false;
        if (rc.getDirection() != want && rc.canTurn(want)) {
            rc.turn(want);
        }
        if (rc.getDirection() == want && rc.canMoveForward()) {
            rc.moveForward();
            return true;
        }
        if (rc.canMove(want)) {
            rc.move(want);
            return true;
        }
        if (allowStuckEscape && stuckCycles >= 2) {
            // Same class of problem the engine's own cat AI hits and fixes
            // the same way (InternalRobot.java: EXPLORE mode randomizes
            // facing after catTurnsStuck >= 4) -- break the tie randomly
            // across all 8 directions instead of retrying the same blocked
            // pair. Manual Fisher-Yates on a plain array (not
            // java.util.Collections.shuffle) to stay clear of any
            // AllowedPackages risk.
            Direction[] shuffled = ALL_DIRECTIONS.clone();
            for (int i = shuffled.length - 1; i > 0; i--) {
                int j = rng.nextInt(i + 1);
                Direction tmp = shuffled[i];
                shuffled[i] = shuffled[j];
                shuffled[j] = tmp;
            }
            for (Direction d : shuffled) {
                if (rc.canMove(d)) {
                    rc.move(d);
                    return true;
                }
            }
            return false;
        }
        Direction left = want.rotateLeft();
        Direction right = want.rotateRight();
        Direction first = (rc.getID() % 2 == 0) ? left : right;
        Direction second = (first == left) ? right : left;
        for (Direction d : new Direction[]{first, second}) {
            if (rc.canMove(d)) {
                rc.move(d);
                return true;
            }
        }
        return false;
    }

    static final Direction[] ALL_DIRECTIONS = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    static Direction preferredExploreDir;

    /**
     * Replay evidence (TRAINING_LOG.md, `knifefight`): 40 Baby Rats went a
     * full 200+ rounds without finding a single unit of cheese or ever
     * meeting a cat. The old version of this method always continued
     * straight ahead and only picked a *fresh random* direction on being
     * blocked -- with every rat starting from the same spawn facing the
     * same default direction, the whole population walks the map as one
     * front instead of fanning out, so entire regions can go permanently
     * unexplored. Fix: each robot commits once, from `rc.getID()` (not
     * team-correlated), to a personal preferred heading and returns to it
     * whenever unblocked, instead of re-randomizing every time it's
     * deflected -- population fans out in `ALL_DIRECTIONS.length` different
     * directions from turn 1 rather than however many directions emerge by
     * chance from wall collisions.
     *
     * Iteration 32 (TRAINING_LOG.md): "commits once... and returns to it
     * whenever unblocked" turns out to be a serious problem the original
     * fix never accounted for -- map boundaries. Traced a rat
     * (`tools/replay-dump.sh --robot`, `rift`) that spawned near a map
     * corner with a preferred heading pointing straight at it: it reached
     * the corner in ~15 rounds, then spent the remaining ~1985 rounds of a
     * 2000-round game oscillating within a handful of tiles, never once
     * picking up cheese. The stuck-cycle escape (see `tryMove()`) can move
     * it one tile away for a turn, but `explore()` unconditionally retries
     * the *same* fixed heading next round -- which immediately re-hits the
     * same boundary, recreating the trap. A one-off escape move can't fix
     * a heading that's permanently wrong for this robot's spawn position;
     * only replacing the heading does. Also fixed a related latent bug
     * while here: `Direction.allDirections()` returns 9 values including
     * `CENTER` (`tryMove` treats `CENTER` as an immediate no-op), so 1 in 9
     * robots by pure `rc.getID()` arithmetic got a preferred heading that
     * never did anything at all. Switched both the initial assignment and
     * the reassignment below to `ALL_DIRECTIONS` (8 real headings only).
     *
     * First attempt at the reassignment trigger reused the shared, global
     * `stuckCycles` counter (tracked once per round in `runBabyRat()`,
     * also driving `tryMove()`'s escape for `deliverCheese()`/
     * `collectCheese()`) and it broadly regressed the full Gauntlet
     * (23/40, down from 70.0%) despite fixing the motivating game cleanly.
     * Root cause: that counter fires on *any* 2-tile repeat regardless of
     * cause -- a rat briefly jammed delivering cheese in a crowded spot
     * near the King (a real, common, and totally benign occurrence) would
     * get its perfectly-fine exploration heading needlessly reassigned
     * the next time it happened to call `explore()`, undermining exactly
     * the population fan-out Iteration 4 relied on. Fixed by tracking a
     * *dedicated* explore-call-to-explore-call position history here,
     * completely separate from the shared per-round one -- only repeated,
     * consecutive *exploration* stalls trigger a reassignment now.
     */
    static void explore(RobotController rc) throws GameActionException {
        if (preferredExploreDir == null) {
            preferredExploreDir = ALL_DIRECTIONS[Math.floorMod(rc.getID(), ALL_DIRECTIONS.length)];
        }
        MapLocation here = rc.getLocation();
        if (here.equals(exploreLocTwoCallsAgo)) {
            exploreStuckCycles++;
        } else {
            exploreStuckCycles = 0;
        }
        exploreLocTwoCallsAgo = exploreLocOneCallAgo;
        exploreLocOneCallAgo = here;
        if (exploreStuckCycles >= 2) {
            Direction newDir;
            do {
                newDir = ALL_DIRECTIONS[rng.nextInt(ALL_DIRECTIONS.length)];
            } while (newDir == preferredExploreDir);
            preferredExploreDir = newDir;
            exploreStuckCycles = 0;
        }
        if (rc.getDirection() == preferredExploreDir && rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        if (tryMove(rc, preferredExploreDir, true)) return;
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        if (tryMove(rc, ALL_DIRECTIONS[rng.nextInt(ALL_DIRECTIONS.length)], true)) return;

        // Iteration 4 (TRAINING_LOG.md): a Baby Rat with every direction
        // blocked used to just do nothing this turn -- confirmed via the
        // replay tool's --terrain flag that at least one real dead end
        // (`knifefight`) is dirt, not a permanent wall, and only the King
        // could dig (Iteration 2). Generalize King's digTowardOpenSpace()
        // to any unit type that's genuinely stuck.
        digTowardOpenSpace(rc);
    }

    /**
     * Compares the round number before/after this robot's own turn logic to
     * detect a confirmed bytecode overrun (the engine pauses mid-instruction
     * and resumes next round with no exception -- see TRAINING_ALGORITHM.md).
     * Also compares live bytecode usage against this unit type's limit to
     * catch near-misses before they become overruns. Surfaced via the
     * robot's own indicator string so it's visible in every replay.
     */
    static void reportBytecodeBudget(RobotController rc, int roundAtTurnStart) {
        int used = Clock.getBytecodeNum();
        int limit = rc.getType().getBytecodeLimit();
        boolean overran = rc.getRoundNum() != roundAtTurnStart;
        boolean nearMiss = used > (int) (limit * 0.9);
        String status = overran ? "OVERRAN" : (nearMiss ? "near-limit" : "ok");
        if (rc.getType() != UnitType.RAT_KING) { // King's indicator already set above
            rc.setIndicatorString("bytecode " + used + "/" + limit + " (" + status + ")");
        }
    }
}
