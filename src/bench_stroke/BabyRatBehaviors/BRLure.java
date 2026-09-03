package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Communication.RatKingInfo;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.*;

import static bench_stroke.BabyRat.currentLocation;
import static bench_stroke.BabyRat.enemyRatKingSeenLoc;
import static bench_stroke.BabyRat.nearbyCats;
import static bench_stroke.BabyRat.seeEnemyRatKing;
import static bench_stroke.RobotPlayer.*;

import java.util.Random;

import bench_stroke.SymmetryManager;
import bench_stroke.Communication.Communicator;

public class BRLure implements Behavior {
    //Constants
    static final int RANDOM_LOC_TURN_RESET = 30;

    //Instance Variables (persist over state changes)
    MapLocation targetEnemyLoc;

    //Singleton Stuff
    private static BRLure instance;
    private BRLure() {}
    public static BRLure getInstance() {
        if(instance == null) {
            instance = new BRLure();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        return;
    }
}