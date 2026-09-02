package pure_cooperator;

import battlecode.common.*;

import java.util.Random;

/**
 * Synthetic Gauntlet reference archetype (see TRAINING_ALGORITHM.md's
 * "Backstab-policy coverage"): a **pure cooperator**. Never attacks an
 * enemy rat under any circumstance, even after the other team has already
 * backstabbed us -- unlike `src/bot/RobotPlayer.java`, which retaliates
 * once `!rc.isCooperation()`. Otherwise identical strategy: grow the
 * population, collect and deliver cheese, and fight cats (alone if
 * necessary, since a pure cooperator's whole value is maximizing cat
 * damage + cheese share while never initiating or continuing a fight with
 * the other team).
 *
 * Purpose: tests whether our own bot recognizes a genuinely safe opponent
 * and doesn't waste effort or risk an unforced backstab against it.
 */
public class RobotPlayer {

    static Random rng;

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

    static void runRatKing(RobotController rc) throws GameActionException {
        rc.writeSharedArray(0, rc.getLocation().x + 1);
        rc.writeSharedArray(1, rc.getLocation().y + 1);

        attackNearestCat(rc);
        pickUpBestNearbyCheese(rc);

        MapLocation buildLoc = findBuildLocation(rc);
        if (buildLoc != null && rc.canBuildRat(buildLoc)) {
            rc.buildRat(buildLoc);
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

    static void runBabyRat(RobotController rc) throws GameActionException {
        RobotInfo[] nearby = rc.senseNearbyRobots();

        MapLocation kingLoc = readHomeKingFromSharedArray(rc);
        for (RobotInfo info : nearby) {
            if (info.getType() == UnitType.RAT_KING && info.getTeam() == rc.getTeam()) {
                kingLoc = info.getLocation();
            }
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

        // No retaliation clause -- a pure cooperator never fights the enemy
        // team, cooperation-mode or not.

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
        moveToward(rc, kingLoc);
        return true;
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
        } else {
            moveToward(rc, loc);
        }
        return true;
    }

    static boolean engage(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.canAttack(target)) {
            rc.attack(target);
            return true;
        }
        moveToward(rc, target);
        return true;
    }

    static boolean flee(RobotController rc, MapLocation threat) throws GameActionException {
        Direction away = rc.getLocation().directionTo(threat).opposite();
        return tryMove(rc, away);
    }

    static void attackNearestCat(RobotController rc) throws GameActionException {
        MapLocation me = rc.getLocation();
        int rangeSq = rc.getType() == UnitType.RAT_KING
                ? GameConstants.RAT_KING_ATTACK_DISTANCE_SQUARED
                : GameConstants.ATTACK_DISTANCE_SQUARED;
        RobotInfo best = null;
        int bestDist = Integer.MAX_VALUE;
        for (RobotInfo info : rc.senseNearbyRobots(rangeSq)) {
            if (info.getType() != UnitType.CAT) continue;
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

    static int countAlliesNear(RobotController rc, RobotInfo[] nearby, MapLocation point, int radiusSquared) {
        int count = 1;
        for (RobotInfo info : nearby) {
            if (info.getTeam() != rc.getTeam()) continue;
            if (info.getType() != UnitType.BABY_RAT) continue;
            if (info.getLocation().distanceSquaredTo(point) <= radiusSquared) count++;
        }
        return count;
    }

    static void moveToward(RobotController rc, MapLocation target) throws GameActionException {
        if (rc.getLocation().equals(target)) return;
        tryMove(rc, rc.getLocation().directionTo(target));
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

    static void explore(RobotController rc) throws GameActionException {
        if (rc.canMoveForward()) {
            rc.moveForward();
            return;
        }
        Direction[] dirs = Direction.allDirections();
        tryMove(rc, dirs[rng.nextInt(dirs.length)]);
    }

    static void reportBytecodeBudget(RobotController rc, int roundAtTurnStart) {
        int used = Clock.getBytecodeNum();
        int limit = rc.getType().getBytecodeLimit();
        boolean overran = rc.getRoundNum() != roundAtTurnStart;
        boolean nearMiss = used > (int) (limit * 0.9);
        String status = overran ? "OVERRAN" : (nearMiss ? "near-limit" : "ok");
        rc.setIndicatorString("bytecode " + used + "/" + limit + " (" + status + ")");
    }
}
