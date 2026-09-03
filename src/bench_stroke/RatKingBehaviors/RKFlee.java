package bench_stroke.RatKingBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Utilities;
import bench_stroke.Communication.Communicator;
import bench_stroke.Communication.NearbyCatSqueakInfo;
import bench_stroke.Communication.PresenceSqueakInfo;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static bench_stroke.RatKing.*;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.Utilities.attemptTrapRevamped;


public class RKFlee implements Behavior {
    //Constants
    static final int RANDOM_LOC_TURN_RESET = 30;

    //Instance Variables (persist over state changes)
    MapLocation fleeLoc = null;

    //Singleton Stuff
    private static RKFlee instance;
    private RKFlee() {}
    public static RKFlee getInstance() {
        if(instance == null) {
            instance = new RKFlee();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        Direction distressDirection = null;
        if (nearestCat != null && turnSeenCat == rc.getRoundNum()) {
            attemptMoveAway(nearestCat.location);
            distressDirection = rc.getLocation().directionTo(nearestCat.location);
            if (Utilities.withinCatVision(rc.getLocation(), nearestCat)) {
                NearbyCatSqueakInfo squeakInfo = new NearbyCatSqueakInfo(nearestCat.location, true, nearestCat.direction);
                Communicator.sendSqueak(squeakInfo);
            }
            if (rc.isActionReady() && nearestEnemy == null) {
                if (Utilities.tryBuild(distressDirection)) {
                    //System.out.println("blocked");
                }
            }
        }
        else if (nearestEnemy != null) {
            if (rc.isActionReady()) {
                if (currentLocation.distanceSquaredTo(nearestEnemy.location) <= 8) {
                    Utilities.attemptAttackAsRatKing();
                }
                else {
                    Utilities.attemptTrapInFrontOfEnemy(nearestEnemy);
                }
            }
           // System.out.println("help me!");
            Communicator.sendSqueak(new PresenceSqueakInfo(Math.min(100, rc.getHealth()), nearestEnemy.location, nearestEnemy.direction, nearestEnemy.health));
            attemptMoveAway(nearestEnemy.location);

            distressDirection = rc.getLocation().directionTo(nearestEnemy.location);

           // System.out.println(destination);
        }
        if (numAllies - numEnemies < 4 || (nearestCat != null && numAllies < 2) || (rc.getCurrentRatCost() <= RAT_COST_THRESHOLD && rc.getAllCheese() >= BUILD_ANYTHING_THRESHOLD)) {
            MapLocation bestBuild = null;
            int bestScore = Integer.MAX_VALUE;
            for (MapLocation offset : adjacentTiles) {
                MapLocation tileToBuildAt = Utilities.add(rc.getLocation(), offset);
                Direction dirToBuild = rc.getLocation().directionTo(tileToBuildAt);
                if (rc.canBuildRat(tileToBuildAt)) {
                    int score = Utilities.directionalDistance(dirToBuild, distressDirection);
                    if (score < bestScore) {
                        bestBuild = tileToBuildAt;
                        bestScore = score;
                    }
                }
            }
           // System.out.println(bestBuild);
            if (bestBuild != null) {
                rc.buildRat(bestBuild);
                ratsBuilt++;
            }
        }
    }
    public static boolean attemptMoveAway(MapLocation avoid) throws GameActionException {
        Direction directionToAvoid = rc.getLocation().directionTo(avoid);
        Direction opposite = directionToAvoid.opposite();
        //lower cheese threshold when fleeing
        if (rc.isActionReady() && rc.getAllCheese() > 100) Utilities.attemptMineDirtAsRatKing(opposite);
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
}
