package immediate_defector;

import battlecode.common.*;

import java.util.Random;

/**
 * Synthetic Gauntlet reference archetype (see TRAINING_ALGORITHM.md's
 * "Backstab-policy coverage"): an **immediate defector**. Treats enemy
 * rats as hostile from turn 1 -- no waiting to be attacked first, unlike
 * `src/bot/RobotPlayer.java` (retaliates only post-backstab) or
 * `src/pure_cooperator/RobotPlayer.java` (never fights back at all). Baby
 * Rats stay on a short leash around their home Rat King ("turtle") rather
 * than roaming far for cheese, prioritizing defense of the King over
 * economy once any enemy is sighted. Otherwise identical to `src/bot/` as
 * of Iteration 4 (economy/search fixes included -- see TRAINING_LOG.md's
 * "kept archetypes in sync" note; this file is meant to isolate the
 * backstab-policy dimension specifically, not also be a weaker economy).
 *
 * Purpose: tests our own bot's resilience to a worst-case early betrayal
 * (whichever team meets first likely triggers a backstab almost
 * immediately) and whether it recovers economically afterward.
 */
public class RobotPlayer {

    static Random rng;
    static int builtCount = 0;
    static final int LEASH_RADIUS_SQUARED = 100; // ~10 tiles from home King

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

        RobotInfo[] nearby = rc.senseNearbyRobots();
        RobotInfo nearestCat = nearestOfType(rc, nearby, UnitType.CAT);
        if (nearestCat != null && nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= 20) {
            flee(rc, nearestCat.getLocation());
        }

        pickUpBestNearbyCheese(rc);

        final int RESERVE = 150;
        final int MAX_POPULATION = 15;
        MapLocation buildLoc = findBuildLocation(rc);
        if (buildLoc != null && rc.canBuildRat(buildLoc)
                && rc.getGlobalCheese() - rc.getCurrentRatCost() >= RESERVE
                && builtCount < MAX_POPULATION) {
            rc.buildRat(buildLoc);
            builtCount++;
        } else if (buildLoc == null) {
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
                kingLoc = info.getLocation();
            }
        }

        // Always-hostile: fight an enemy rat on sight, no waiting for them
        // to hit us first. This is what makes the archetype an "immediate"
        // defector rather than a retaliatory one.
        RobotInfo enemy = nearestEnemyRat(rc, nearby);
        if (enemy != null && rc.canAttack(enemy.getLocation())) {
            rc.attack(enemy.getLocation());
            return;
        }

        if (rc.getRawCheese() > 0 && kingLoc != null) {
            if (deliverCheese(rc, kingLoc)) return;
        }

        RobotInfo nearestCat = nearestOfType(rc, nearby, UnitType.CAT);
        if (nearestCat != null) {
            int allies = countAlliesNear(rc, nearby, nearestCat.getLocation(), 8);
            if (allies >= 3) {
                if (engage(rc, nearestCat.getLocation())) return;
            } else if (nearestCat.getLocation().distanceSquaredTo(rc.getLocation()) <= 8) {
                if (flee(rc, nearestCat.getLocation())) return;
            }
        }

        if (enemy != null) {
            // An enemy is visible but out of attack range: close in rather
            // than wander off, since defection is already committed.
            if (moveToward(rc, enemy.getLocation())) return;
        }

        // No enemy in sight (or unreachable): stay on the leash. Collect
        // cheese only within leash range of home; otherwise head back
        // toward the King.
        if (kingLoc != null && rc.getLocation().distanceSquaredTo(kingLoc) > LEASH_RADIUS_SQUARED) {
            if (moveToward(rc, kingLoc)) return;
        }

        if (collectCheese(rc)) return;

        explore(rc);
    }

    static MapLocation readHomeKingFromSharedArray(RobotController rc) throws GameActionException {
        int kx = rc.readSharedArray(0);
        int ky = rc.readSharedArray(1);
        if (kx == 0 || ky == 0) return null;
        return new MapLocation(kx - 1, ky - 1);
    }

    static boolean deliverCheese(RobotController rc, MapLocation kingLoc) throws GameActionException {
        if (rc.canTransferCheese(kingLoc, rc.getRawCheese())) {
            rc.transferCheese(kingLoc, rc.getRawCheese());
            return true;
        }
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
        return moveToward(rc, loc);
    }

    static boolean engage(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.canAttack(target)) {
            rc.attack(target);
            return true;
        }
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
            boolean hostile = info.getType() == UnitType.CAT || info.getTeam() != rc.getTeam();
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
        int count = 1;
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
        digTowardOpenSpace(rc);
    }

    static void reportBytecodeBudget(RobotController rc, int roundAtTurnStart) {
        int used = Clock.getBytecodeNum();
        int limit = rc.getType().getBytecodeLimit();
        boolean overran = rc.getRoundNum() != roundAtTurnStart;
        boolean nearMiss = used > (int) (limit * 0.9);
        String status = overran ? "OVERRAN" : (nearMiss ? "near-limit" : "ok");
        if (rc.getType() != UnitType.RAT_KING) {
            rc.setIndicatorString("bytecode " + used + "/" + limit + " (" + status + ")");
        }
    }
}
