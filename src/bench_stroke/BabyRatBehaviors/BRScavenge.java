package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.SymmetryManager;
import bench_stroke.Utilities;
import bench_stroke.Communication.CheeseMineSqueakInfo;
import bench_stroke.Communication.RatKingInfo;
import bench_stroke.DataStructures.FastMath;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.*;

import static bench_stroke.Utilities.getActionableLocations;

import bench_stroke.Communication.Communicator;

import static bench_stroke.BabyRat.*;
import static bench_stroke.Communication.Communicator.getClosestRatKing;
import static bench_stroke.RatKing.closestCheeseMine;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.RobotPlayer.rng;


public class BRScavenge implements Behavior {

    // --- Singleton Stuff ---
    private static BRScavenge instance;
    private BRScavenge() {}
    public static BRScavenge getInstance() {
        if(instance == null) {
            instance = new BRScavenge();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
//#        if(rc.getRoundNum() > MINE_ASSIGNMENT_ROUND && targetCheeseMine == null) {
//#           int randomAssignment = rng.nextInt(knownMines.length);
//#           targetCheeseMine = knownMines[randomAssignment].location();
//#        }

        int curCheese = rc.getRawCheese();
        boolean bringingCheeseHome = curCheese >= ABSOLUTE_RETURN_CHEESE_THRESHOLD ||
                (curCheese >= RETURN_CHEESE_THRESHOLD && (nearestCheese == null || (rc.canSenseLocation(nearestCheese) && rc.senseMapInfo(nearestCheese).getCheeseAmount() == 0)));

        // if (rc.getCarrying() != null && rc.isActionReady()) {
        //     if (bringingCheeseHome) {
        //         Utilities.attemptThrowAwayFromRatKing(closestAllyRatKing.loc());
        //     }
        //     else {
        //         Utilities.attemptThrow(turnsCarrying, health);
        //     }
        // }

        if (nearestCheese != null && currentLocation.distanceSquaredTo(nearestCheese) == 2) Utilities.attemptCheesePickup();
        if (nearestEmptyCheeseMine != null && rc.isActionReady() && rc.getLocation().isAdjacentTo(nearestEmptyCheeseMine)) {
            Utilities.attemptTrapMine(nearestEmptyCheeseMine, false);
        }
        //try to bring cheese home
        if (bringingCheeseHome) {
            MapLocation closestRatKingLoc = closestAllyRatKing.loc();
            int dist = currentLocation.distanceSquaredTo(closestRatKingLoc);
            if (dist <= 18) {
                if (Utilities.attemptCheeseTransfer()) {
                    CheeseMineSqueakInfo info = Communicator.findMineToStore(map);
                    if(info != null) {
                        Communicator.sendSqueak(info);
                    }
                    if (targetCheeseMine != null) {
                        if (rc.canTurn()) {
                           // System.out.println("extra turn");
                            rc.turn(rc.getLocation().directionTo(targetCheeseMine));
                        }
                    }
                }
                else if (rc.canTurn() && rc.getDirection() != rc.getLocation().directionTo(closestRatKingLoc)) {
                    rc.turn(rc.getLocation().directionTo(closestRatKingLoc));
                    if (Utilities.attemptCheeseTransfer()) {
                        CheeseMineSqueakInfo info = Communicator.findMineToStore(map);
                        if(info != null) {
                            Communicator.sendSqueak(info);
                        }
                        if (targetCheeseMine != null) {
                            if (rc.canTurn()) {
                             //   System.out.println("extra turn");
                                rc.turn(rc.getLocation().directionTo(targetCheeseMine));
                            }
                        }
                    }
                }
            }
            else if (rc.isMovementReady()) {
                boolean avoidCats = dist > ANSWER_DISTRESS_DIST_THRESHOLD;
                Pathfinding.attemptMove(closestRatKingLoc, avoidCats);
                dist = currentLocation.distanceSquaredTo(closestRatKingLoc);
                if (dist <= 18) {
                    if (Utilities.attemptCheeseTransfer()) {
                        CheeseMineSqueakInfo info = Communicator.findMineToStore(map);
                        if(info != null) {
                            Communicator.sendSqueak(info);
                        }
                        if (targetCheeseMine != null && rc.canTurn()) {
                          // System.out.println("extra turn");
                            rc.turn(rc.getLocation().directionTo(targetCheeseMine));
                        }
                    }
                }
            }
        }
        //try to pick up some cheese
        else if (nearestCheese != null) {
            Pathfinding.attemptMove(nearestCheese, true);
            if (rc.getLocation().distanceSquaredTo(nearestCheese) <= 2) Utilities.attemptCheesePickup();
            // if (nearestAnyCheeseMine != null && rc.isActionReady() && turnsSinceEnemy >= 5 && nearestEnemy == null) {
            //     boolean dug = Utilities.placeDirtMine(nearestAnyCheeseMine);
            //     if (dug) System.out.println("placing dirt!");
            // }
            if (targetCheeseMine == null) targetCheeseMine = nearestAnyCheeseMine;
        }
        // else if (nearestEmptyCheeseMine != null) {
        //     if (rc.getLocation().equals(nearestEmptyCheeseMine)) {
        //         if (rc.canPickUpCheese(rc.getLocation())) rc.pickUpCheese(rc.getLocation());
        //         if (rc.canTurn()) rc.turn(rc.getDirection().rotateLeft().rotateLeft().rotateLeft());
        //         MapLocation closestCheese = null;
        //         int smallestDist = Integer.MAX_VALUE;
        //         for (MapInfo info : rc.senseNearbyMapInfos(-1)) {
        //             if (info.getCheeseAmount() > 0) {
        //                 int dist = rc.getLocation().distanceSquaredTo(info.getMapLocation());
        //                 if (dist < smallestDist) {
        //                     smallestDist = dist;
        //                     closestCheese = info.getMapLocation();
        //                 }
        //             }
        //         }
        //         if (closestCheese != null) {
        //             Pathfinding.attemptMove(closestCheese, true);
        //             Utilities.attemptCheesePickup();
        //         }
        //     }
        //     else {
        //         Pathfinding.attemptMove(nearestEmptyCheeseMine, true);
        //     }
        // }
        else if (targetCheeseMine != null) {
            Pathfinding.attemptMove(targetCheeseMine, true);
            Utilities.attemptCheesePickup();
            rc.setIndicatorLine(currentLocation, targetCheeseMine, 255, 0, 0);
            if (currentLocation.distanceSquaredTo(targetCheeseMine) <= 4) {
                targetCheeseMine = null;
            }
        }

        // if (nearestAnyCheeseMine != null && rc.isActionReady() && rc.getLocation().distanceSquaredTo(nearestAnyCheeseMine) <= 5 && rc.getRawCheese() > 40) {
        //     int threatLevel = SymmetryManager.seenMines.get(nearestAnyCheeseMine, 0);
        //     if (threatLevel > 20){
        //         if (Utilities.attemptTrapMine(nearestAnyCheeseMine, true)) System.out.println("trapping");
        //     } 
        // }

    }


}
