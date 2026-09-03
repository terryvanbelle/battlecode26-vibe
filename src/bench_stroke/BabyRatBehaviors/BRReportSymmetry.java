package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.*;
import bench_stroke.Communication.RatKingInfo;
import static bench_stroke.BabyRat.*;
import bench_stroke.Communication.SymmetrySqueakInfo;

import static bench_stroke.BabyRat.currentLocation;
import static bench_stroke.BabyRat.nearbyCats;
import static bench_stroke.RobotPlayer.*;
import bench_stroke.SymmetryManager;

import bench_stroke.Communication.Communicator;

public class BRReportSymmetry implements Behavior {
    //Singleton Stuff
    private static BRReportSymmetry instance;
    private BRReportSymmetry() {}
    public static BRReportSymmetry getInstance() {
        if(instance == null) {
            instance = new BRReportSymmetry();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        if (currentLocation.distanceSquaredTo(closestAllyRatKing.loc()) <= GameConstants.SQUEAK_RADIUS_SQUARED) {
            SymmetrySqueakInfo squeak = new SymmetrySqueakInfo(SymmetryManager.getSym());
            Communicator.sendSqueak(squeak);
        }
        else {
            Pathfinding.attemptMove(closestAllyRatKing.loc(), true);
        }
    }
}