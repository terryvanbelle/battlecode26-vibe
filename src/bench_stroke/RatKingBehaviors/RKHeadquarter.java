package bench_stroke.RatKingBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Utilities;
import battlecode.common.*;

import static bench_stroke.RobotPlayer.MAP_WIDTH;
import static bench_stroke.BabyRat.inDistressRatKing;
import static bench_stroke.RatKing.*;
import static bench_stroke.RobotPlayer.MAP_HEIGHT;
import static bench_stroke.RobotPlayer.MAP_WIDTH;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.RobotPlayer.rng;
import static bench_stroke.SymmetryManager.seenMines;

import java.util.Arrays;
import java.util.Map;

import bench_stroke.RobotPlayer;
import bench_stroke.Communication.*;
import bench_stroke.DataStructures.FastMath;
import bench_stroke.Pathfinding.Pathfinding;
import bench_stroke.SymmetryManager;

public class RKHeadquarter implements Behavior {
    //Singleton Stuff
    private static RKHeadquarter instance;
    private RKHeadquarter() {}
    public static RKHeadquarter getInstance() {
        if(instance == null) {
            instance = new RKHeadquarter();
        }

        return instance;
    }

    Direction exploreDirection = null;

    int[] spawnCount = new int[8];
    double[] dists = new double[8];
    private static final double CENTER_BIAS_MULT = 10.0;

    @Override
    public void execute() throws GameActionException {
       if(!rc.isActionReady() && !rc.isMovementReady()) return;
        if (rc.getAllCheese() >= BUILD_ANYTHING_THRESHOLD && 
            (rc.getCurrentRatCost() <= RAT_COST_THRESHOLD || 
            inDistress ||
            (rc.getAllCheese() >= CONTINUE_BUILD_CHEESE_THRESHOLD) && rc.getRoundNum() > 100
            || rc.getAllCheese() >= CONTINUE_BUILD_CHEESE_THRESHOLD / 3 && rc.getRoundNum() > SPAM_BOTS_FRENZY_ROUND) &&
            rc.isActionReady()) 
        {
             if (rc.getRoundNum() % 2 == 0 || RobotPlayer.turnCount <= 5) doDirs();
            //if (RobotPlayer.turnCount <= 5) doDirs();
            double[] mineBias = rc.getRoundNum() > 150 ? getMineBias() : null;
            Direction dir = getBestDir(spawnCount, mineBias);
            if (dir != null) {
                if (rc.canBuildRat(rc.getLocation().add(dir).add(dir))) {
                    rc.buildRat(rc.getLocation().add(dir).add(dir));
                    spawnCount[dir.ordinal()]++;
                }
            }
        }
       // }
        if (rc.isMovementReady()) {
            //check if we hear of a cat even if we cant see it
            if (rc.getRoundNum() - turnSeenCat >= RUN_CAT_ROUNDS || turnSeenCat == -1) {
                Squeak[] squeaks  = Communicator.getSqueaksOfType(NearbyCatSqueakInfo.class);
                NearbyCatSqueakInfo bestSqueakInfo = null;
                int bestRound = Integer.MAX_VALUE;
                for (Squeak squeak : squeaks) {
                    NearbyCatSqueakInfo catSqueakInfo = (NearbyCatSqueakInfo) squeak.squeakInfo;
                    if (bestSqueakInfo == null) {
                        bestSqueakInfo = catSqueakInfo;
                        bestRound = squeak.round;
                    }
                    else if (catSqueakInfo.cat() && !bestSqueakInfo.cat()) {
                        bestSqueakInfo = catSqueakInfo;
                        bestRound = squeak.round;
                    }
                    else if (squeak.round > bestRound && catSqueakInfo.cat() == bestSqueakInfo.cat()) {
                        bestSqueakInfo = catSqueakInfo;
                        bestRound = squeak.round;
                    }
                }

               // MapLocation targetAverage = Communicator.getSafeMineAverageLocation();

                if (bestSqueakInfo != null) {
                    if (bestSqueakInfo.cat()) rc.setIndicatorLine(currentLocation, bestSqueakInfo.location(), 255, 255, 255);
                    else rc.setIndicatorLine(currentLocation, bestSqueakInfo.location(), 0, 0,0);
                    attemptMoveAway(bestSqueakInfo.location());
                }
                // else if (secondRat && targetAverage != null) {
                //     System.out.println(targetAverage);
                //     if (!rc.getLocation().equals(targetAverage)) {
                //         attemptMoveTowards(targetAverage);
                //         rc.setIndicatorDot(targetAverage, 0, 255, 0);
                //     }
                // }
                else if (closestCheeseMine != null && !currentLocation.equals(closestCheeseMine)) {
                    attemptMoveTowards(closestCheeseMine);
                }
                else if (!currentLocation.equals(closestCheeseMine) && !centerDangerous) {
                    MapLocation center = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
                    attemptMoveTowards(center);
                    movingTowardsCenter = true;
                }
                // else if ((currentLocation.equals(closestCheeseMine) || pickingUpCheese) && Clock.getBytecodesLeft() > 1000) {
                //     MapLocation nearestCheese = null;
                //     int smallestDist = Integer.MAX_VALUE;
                //     for (MapInfo info : rc.senseNearbyMapInfos(-1)) {
                //         if (info.getCheeseAmount() > 0) {
                //          //   System.out.println(info.getMapLocation());
                //             int dist = currentLocation.distanceSquaredTo(info.getMapLocation());
                //             int distFromMine = closestCheeseMine.distanceSquaredTo(info.getMapLocation());
                //             if (dist < smallestDist && distFromMine <= 8) {
                //                 smallestDist = dist;
                //                 nearestCheese = info.getMapLocation();
                //                 if (dist == 8) break;
                //             }
                //         }
                //     }
                //     if (nearestCheese != null) {
                //         pickingUpCheese = true;
                //       //  System.out.println(nearestCheese);
                //         attemptMoveTowards(nearestCheese);
                //         Utilities.attemptToPickUpRatKingCheese();
                //     }
                //     else {
                //         pickingUpCheese = false;
                //     }
                // }

                
            }
            else if (rc.getRoundNum() - turnSeenCat < RUN_CAT_ROUNDS && turnSeenCat != -1) {
                attemptMoveAway(nearestCat.location);
            }
        }

        //if we are the chief rat, we get to call the shots about forming new rat kings
        int numRatKings = Communicator.getNumRatKings();
        if (chiefRat && numRatKings < GameConstants.MAX_NUMBER_OF_RAT_KINGS && !(numRatKings >= 2 && rc.getRoundNum() >= 1200) && numRatKings < maxRatKings) {
            //1. are we already trying to form a new rat king?
            //2. are we over threshold
            double threshold_multiplier = 1.8 + ((numRatKings - 1) / 3);
            if (rc.readSharedArray(20) == 0 && rc.getCurrentRatCost() > RAT_COST_THRESHOLD * threshold_multiplier) {
                MapLocation bestLocation = Communicator.getBestFormationLocation();
                if (rc.getLocation().distanceSquaredTo(bestLocation) <= 25) return;
                if(bestLocation != null) {
                    rc.writeSharedArray(20, bestLocation.x);
                    rc.writeSharedArray(21, bestLocation.y);
                }
            }
        }
        else if (chiefRat && Communicator.getNumRatKings() >= maxRatKings && rc.readSharedArray(20) != 0) {
            rc.writeSharedArray(20, 0);
            rc.writeSharedArray(21, 0);
        }

        

    }


    public static boolean attemptMoveAway(MapLocation avoid) throws GameActionException {
        Direction directionToAvoid = rc.getLocation().directionTo(avoid);
        Direction opposite = directionToAvoid.opposite();
        if (rc.isActionReady() && rc.getAllCheese() > 1000) Utilities.attemptMineDirtAsRatKing(opposite);
        if (rc.canMove(opposite)) {
            rc.move(opposite);
        }
        else if (rc.canMove(opposite.rotateLeft())) {
            rc.move(opposite.rotateLeft());
        }
        else if (rc.canMove(opposite.rotateRight())) {
            rc.move(opposite.rotateRight());
        }
        else if (rc.canMove(opposite.rotateLeft().rotateLeft())) {
            rc.move(opposite.rotateLeft().rotateLeft());
        }
        else if (rc.canMove(opposite.rotateRight().rotateRight())) {
            rc.move(opposite.rotateRight().rotateRight());
        }
        if (rc.isMovementReady()) return false;
        return true;
    }
    public static boolean attemptMoveTowards(MapLocation target) throws GameActionException {
        Direction direction = rc.getLocation().directionTo(target);
        if (rc.isActionReady() && rc.getAllCheese() > 1000) Utilities.attemptMineDirtAsRatKing(direction);
        if (rc.canMove(direction)) {
            rc.move(direction);
        }
        else if (rc.canMove(direction.rotateLeft())) {
            rc.move(direction.rotateLeft());
        }
        else if (rc.canMove(direction.rotateRight())) {
            rc.move(direction.rotateRight());
        }
        else if (rc.canMove(direction.rotateLeft().rotateLeft())) {
            rc.move(direction.rotateLeft().rotateLeft());
        }
        else if (rc.canMove(direction.rotateRight().rotateRight())) {
            rc.move(direction.rotateRight().rotateRight());
        }
        if (rc.isMovementReady()) return false;
        return true;
    } 

    void doDirs(){
        int dx, dy;
        MapLocation myLoc = rc.getLocation();
        dists[Direction.EAST.ordinal()] = MAP_WIDTH - myLoc.x - 1;;
        dx = MAP_WIDTH - myLoc.x - 1;
        dy = MAP_HEIGHT - myLoc.y - 1;
        dists[Direction.NORTHEAST.ordinal()] = Math.sqrt(2)*Math.min(dx,dy);
        dists[Direction.NORTH.ordinal()] = MAP_HEIGHT - myLoc.y - 1;
        dx = MAP_WIDTH - myLoc.x - 1;
        dy = myLoc.y;
        dists[Direction.SOUTHEAST.ordinal()] = Math.sqrt(2)*Math.min(dx,dy);
        dists[Direction.WEST.ordinal()] = myLoc.x;;
        dx = myLoc.x;
        dy = MAP_HEIGHT - myLoc.y - 1;
        dists[Direction.NORTHWEST.ordinal()] = Math.sqrt(2)*Math.min(dx,dy);
        dx = myLoc.x;
        dy = myLoc.y;
        dists[Direction.SOUTHWEST.ordinal()] = Math.sqrt(2)*Math.min(dx,dy);
        dists[Direction.SOUTH.ordinal()] = myLoc.y;
        addCenterBias(myLoc);
    }

    private void addCenterBias(MapLocation myLoc) {
        MapLocation center = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
        Direction centerDir = myLoc.directionTo(center);
        if (centerDir == Direction.CENTER) return;

        int dxToCenter = Math.abs(center.x - myLoc.x);
        int dyToCenter = Math.abs(center.y - myLoc.y);
        double rayLength;
        if (centerDir == Direction.NORTH || centerDir == Direction.SOUTH) {
            rayLength = dyToCenter;
        } else if (centerDir == Direction.EAST || centerDir == Direction.WEST) {
            rayLength = dxToCenter;
        } else {
            rayLength = Math.sqrt(2) * Math.min(dxToCenter, dyToCenter);
        }
        double scaledBias = (rayLength);
        dists[centerDir.ordinal()] += scaledBias;
    }

    Direction getBestDir(int[] dirCount, double[] mineBias){
        Direction ans = null;
        double dAns = -1;
        for (Direction dir : Direction.allDirections()){
            if (!rc.canBuildRat(rc.getLocation().add(dir).add(dir))) continue;
            double bias = mineBias == null ? 0 : mineBias[dir.ordinal()];
            double ndAns = (dists[dir.ordinal()] + bias)/((dirCount[dir.ordinal()])+1);
            if (ndAns > dAns){
                dAns = ndAns;
                ans = dir;
            }
        }
        return ans;
    }

    double[] getMineBias() throws GameActionException {
        double[] bias = new double[8];
        seenMines.updateIterable();
        MapLocation myLoc = rc.getLocation();
        for (int i = 0; i < seenMines.size; i++) {
            Direction dir = myLoc.directionTo(seenMines.getKey(i));
            if (dir == Direction.CENTER) continue;
            addBias(bias, dir, 10);
            addBias(bias, dir.rotateLeft(), 5);
            addBias(bias, dir.rotateRight(), 5);
        }
        return bias;
    }

    void addBias(double[] bias, Direction dir, double amount) {
        if (dir == Direction.CENTER) return;
        int idx = dir.ordinal();
        if (idx >= 0 && idx < bias.length) {
            bias[idx] += amount;
        }
    }
}
