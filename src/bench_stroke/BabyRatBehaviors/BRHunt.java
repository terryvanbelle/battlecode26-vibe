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
import static bench_stroke.BabyRat.*;

import java.util.Random;

import bench_stroke.Utilities;
import bench_stroke.SymmetryManager;
import bench_stroke.Communication.Communicator;

public class BRHunt implements Behavior {
    
    //Instance Variables (persist over state changes)
    MapLocation targetEnemyLoc;

    //Singleton Stuff
    private static BRHunt instance;
    private BRHunt() {}
    public static BRHunt getInstance() {
        if(instance == null) {
            instance = new BRHunt();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        targetEnemyLoc = SymmetryManager.getTarget();
        Pathfinding.attemptMove(targetEnemyLoc, true);
        if (rc.isActionReady()) Utilities.attemptRatnap();
        if (rc.isActionReady()) Utilities.attemptThrow(turnsCarrying, health);
        if (rc.isActionReady()) Utilities.attemptAttack();
    }
}