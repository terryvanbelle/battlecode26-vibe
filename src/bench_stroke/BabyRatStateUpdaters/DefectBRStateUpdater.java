package bench_stroke.BabyRatStateUpdaters;

import bench_stroke.BabyRatBehaviors.*;
import bench_stroke.Behavior;
import bench_stroke.StateUpdater;
import bench_stroke.Communication.*;

import battlecode.common.GameActionException;
import bench_stroke.SymmetryManager;

import static bench_stroke.BabyRat.*;
import static bench_stroke.RobotPlayer.rc;
import bench_stroke.Utilities;

public class DefectBRStateUpdater implements StateUpdater {
    private static DefectBRStateUpdater instance;
    private DefectBRStateUpdater() {}
    public static DefectBRStateUpdater getInstance() {
        if(instance == null) {
            instance = new DefectBRStateUpdater();
        }

        return instance;
    }

    static final int RAT_KING_HEALTH_THRESHOLD = 300;
    @Override
    public Behavior decideBehavior() throws GameActionException {
        // if (currentlyLuring) {
        //     if (rc.getRoundNum() % 25 == 0) {
        //         currentlyLuring = false;
        //     }
        //     return BRLure.getInstance();
        // }

//        if(Communicator.getLowestHealthRatKing().health() < RAT_KING_HEALTH_THRESHOLD
//                && Communicator.getNumRatKings() == 1 && !(rc.getID() % 2 == 0) || rc.getGlobalCheese() > 4000) {
//            return BRFormRatKing.getInstance();
//        }

        //System.out.println(catSqueak);

        if (rc.isBeingCarried() || rc.isBeingThrown()) {
            return BRThrownCarried.getInstance();
        }
        else if (inDistressRatKing != null && currentLocation.distanceSquaredTo(inDistressRatKing.loc()) <= ANSWER_DISTRESS_DIST_THRESHOLD && (Communicator.getAverageEnemyFromSharedArray() != null || ((catSqueak != null || nearbyCats.length > 0) || nearestEnemy != null || allEnemyLocations.size > 0))) {
            if (nearestEnemy != null || allEnemyLocations.size > 0) {
                rc.setIndicatorString("micro!");
                return BRMicro.getInstance();
            }
            else if (nearbyCats.length > 0 || catSqueak != null) {
                rc.setIndicatorString("cat micro!");
                return BRCatMicro.getInstance();
            }
        }
        else if ((nearestSeenEnemy != null || (nearestEnemy != null && currentLocation.distanceSquaredTo(nearestEnemy) <= 9)) && !Utilities.facingThreeWalls()) {
            //Debug.log("here");
            rc.setIndicatorString("micro!");
            return BRMicro.getInstance();
        }
        else if (nearbyCats.length > 0 && (rc.isCooperation() || rc.getRoundNum() > ATTACK_CAT_ROUND)) {
            rc.setIndicatorString("cat micro!");
            return BRCatMicro.getInstance();
        }
        // else if (rc.getCarrying() != null && rc.getCarrying().getTeam() != rc.getTeam()) {
        //     rc.setIndicatorString("micro");
        //     return BRMicro.getInstance();
        // }
        else if (SymmetryManager.getSym() != 0 && rc.readSharedArray(SymmetryManager.SYMMETRY_INDEX) == 0) {
            rc.setIndicatorString("reportsymmetry!");
            return BRReportSymmetry.getInstance();
        }
        else if (targetMineForFormation != null && !(Communicator.getNumRatKings() == 2 && rc.getRoundNum() > 1200) && turnsSinceEnemy > 2) {
            rc.setIndicatorString("rat king formation!");
            return BRFormRatKing.getInstance();
        }
        else if (nearestCheese != null || rc.getRawCheese() >= RETURN_CHEESE_THRESHOLD || targetCheeseMine != null) {
            rc.setIndicatorString("scavenger!");
            return BRScavengeRatPickup.getInstance();
        }

        rc.setIndicatorString("explore!");
        return BRExplore.getInstance();
    }
}
