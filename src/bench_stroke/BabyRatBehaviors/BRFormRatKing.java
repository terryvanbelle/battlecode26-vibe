package bench_stroke.BabyRatBehaviors;

import java.util.*;

import bench_stroke.Behavior;
import bench_stroke.Communication.FormRatKingSqueakInfo;
import bench_stroke.Communication.Squeak;
import bench_stroke.RatKing;
import bench_stroke.RobotPlayer;
import bench_stroke.Utilities;
import bench_stroke.Communication.Communicator;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;

import static bench_stroke.BabyRat.*;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.Pathfinding.Pathfinding.passable;
public class BRFormRatKing implements Behavior {

    private static BRFormRatKing instance;
    private BRFormRatKing() {}
    public static BRFormRatKing getInstance() {
        if(instance == null) {
            instance = new BRFormRatKing();
        }

        return instance;
    }


    @Override
    public void execute() throws GameActionException {
        if (rc.canBecomeRatKing() && currentLocation.distanceSquaredTo(closestAllyRatKing.loc()) > 25) {
            rc.becomeRatKing();
            RatKing.initializeRatKing();
            rc.writeSharedArray(20, 0);
            rc.writeSharedArray(21, 0);
        }
        else if (rc.getLocation().distanceSquaredTo(targetMineForFormation) > 2){
            Pathfinding.attemptMove(targetMineForFormation, true);
        }
        else if (rc.getLocation().distanceSquaredTo(targetMineForFormation) <= 2 && rc.canSenseLocation(targetMineForFormation) && rc.senseRobotAtLocation(targetMineForFormation) == null) {
            if (rc.canTurn()) rc.turn(rc.getLocation().directionTo(targetMineForFormation));
            if (rc.canMove(rc.getLocation().directionTo(targetMineForFormation))) rc.move(rc.getLocation().directionTo(targetMineForFormation));
        }
        currentLocation = rc.getLocation();
        if (rc.canBecomeRatKing() && currentLocation.distanceSquaredTo(closestAllyRatKing.loc()) > 25) {
            rc.becomeRatKing();
            RatKing.initializeRatKing();
            rc.writeSharedArray(20, 0);
            rc.writeSharedArray(21, 0);
        }
        else if (currentLocation.distanceSquaredTo(targetMineForFormation) <= 8) {
            if (rc.canTurn()) rc.turn(rc.getDirection().rotateLeft());
            Utilities.attemptDig();
        }
    }

}
