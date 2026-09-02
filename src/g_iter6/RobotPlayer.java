package g_iter6;

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

        attackNearestHostile(rc);

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
        // King outright. RESERVE is a first-cut heuristic (75 rounds of
        // upkeep), not derived from evidence yet -- a candidate for later
        // tuning once there's a losing game that specifically motivates a
        // different value.
        final int RESERVE = 150;

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
        final int MAX_POPULATION = 15;
        MapLocation buildLoc = findBuildLocation(rc);
        if (buildLoc != null && rc.canBuildRat(buildLoc)
                && rc.getGlobalCheese() - rc.getCurrentRatCost() >= RESERVE
                && builtCount < MAX_POPULATION) {
            rc.buildRat(buildLoc);
            builtCount++;
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
                rc.attack(nearestCat.getLocation());
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

        if (!rc.isCooperation()) {
            RobotInfo enemy = nearestEnemyRat(rc, nearby);
            if (enemy != null && rc.canAttack(enemy.getLocation())) {
                rc.attack(enemy.getLocation());
                return;
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
        return moveToward(rc, kingLoc);
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
        return moveToward(rc, loc);
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

    static void attackNearestHostile(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int rangeSq = rc.getType() == UnitType.RAT_KING
                ? GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED
                : GameConstants.ATTACK_DISTANCE_SQUARED;
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : rc.senseNearbyRobots(rangeSq)) {
            boolean hostile = info.getType() == UnitType.CAT
                    || (!rc.isCooperation() && info.getTeam() != rc.getTeam());
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
        if (rc.getLocation().equals(target)) return true;
        return tryMove(rc, rc.getLocation().directionTo(target));
    }

    /**
     * Turn-and/or-move toward `want`, using only directions derived from
     * `want` itself (never a fixed compass fallback -- see the class-level
     * symmetry note). If blocked, sidesteps via `want`'s own rotateLeft/
     * rotateRight, tie-broken by `rc.getID() % 2` (per-robot, not
     * team-correlated) rather than a fixed left-before-right order.
     */
    static boolean tryMove(RobotController rc, Direction want) throws GameActionException {
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
     * deflected -- population fans out in `Direction.allDirections().length`
     * different directions from turn 1 rather than however many directions
     * emerge by chance from wall collisions.
     */
    static void explore(RobotController rc) throws GameActionException {
        if (preferredExploreDir == null) {
            Direction[] dirs = Direction.allDirections();
            preferredExploreDir = dirs[Math.floorMod(rc.getID(), dirs.length)];
        }
        if (rc.getDirection() == preferredExploreDir && rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        if (tryMove(rc, preferredExploreDir)) return;
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        Direction[] dirs = Direction.allDirections();
        if (tryMove(rc, dirs[rng.nextInt(dirs.length)])) return;

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
