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


public class BRScavengeRatPickup implements Behavior {

    // --- Singleton Stuff ---
    private static BRScavengeRatPickup instance;
    private BRScavengeRatPickup() {}
    public static BRScavengeRatPickup getInstance() {
        if(instance == null) {
            instance = new BRScavengeRatPickup();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {

        int curCheese = rc.getRawCheese();
        boolean bringingCheeseHome = curCheese >= ABSOLUTE_RETURN_CHEESE_THRESHOLD ||
                (curCheese >= RETURN_CHEESE_THRESHOLD && (nearestCheese == null || (rc.canSenseLocation(nearestCheese) && rc.senseMapInfo(nearestCheese).getCheeseAmount() == 0)));

        if (nearestCheese != null && currentLocation.distanceSquaredTo(nearestCheese) == 2) Utilities.attemptCheesePickup();
        if (nearestEmptyCheeseMine != null && rc.isActionReady() && rc.getLocation().isAdjacentTo(nearestEmptyCheeseMine)) {
            Utilities.attemptTrapMine(nearestEmptyCheeseMine, false);
        }
        //try to bring cheese home
        if (bringingCheeseHome) {
            MapLocation closestRatKingLoc = closestAllyRatKing.loc();
            int dist = currentLocation.distanceSquaredTo(closestRatKingLoc);
            //see if there is someone we can help out by picking up
            if (dist > 35 && !ferrying && rc.getRawCheese() <= 40 && rc.isActionReady()) {
                MapLocation[] actionable = Utilities.getActionableLocations();
                for (MapLocation option : actionable) {
                    if (rc.onTheMap(option) && rc.canSenseRobotAtLocation(option)) {
                        RobotInfo ally = rc.senseRobotAtLocation(option);
                        if (ally.getTeam() == rc.getTeam() && ally.cheeseAmount >= 100) {
                            if (rc.canCarryRat(option)) {
                                rc.carryRat(option);
                               // System.out.println("gotchu homie");
                                ferrying = true;
                                break;
                            }
                        }
                    }
                }
            }
            else if (dist <= 25 && ferrying) {
                boolean dropped = Utilities.tryDrop();
                if (dropped) {
                  //  System.out.println("Your welcome for the ride homeslice");
                    ferrying = false;
                }
            }
            if (dist <= 18) {
                if (Utilities.attemptCheeseTransfer()) {
                    CheeseMineSqueakInfo info = Communicator.findMineToStore(map);
                    if(info != null) {
                        Communicator.sendSqueak(info);
                    }
                    if (targetCheeseMine != null) {
                        if (rc.canTurn()) {
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
            if (targetCheeseMine == null) targetCheeseMine = nearestAnyCheeseMine;
        }
        else if (targetCheeseMine != null) {
            Pathfinding.attemptMove(targetCheeseMine, true);
            Utilities.attemptCheesePickup();
            rc.setIndicatorLine(currentLocation, targetCheeseMine, 255, 0, 0);
            if (currentLocation.distanceSquaredTo(targetCheeseMine) <= 4) {
                targetCheeseMine = null;
            }
        }

    }


}
