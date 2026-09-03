package bench_stroke.BabyRatBehaviors;
import bench_stroke.Communication.Communicator;
import bench_stroke.Behavior;
import bench_stroke.Pathfinding.Pathfinding;
import bench_stroke.SymmetryManager;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static bench_stroke.RobotPlayer.rc;

public class BRRush implements Behavior {

    //Singleton Stuff
    private static BRRush instance;
    private BRRush() {}
    public static BRRush getInstance() {
        if(instance == null) {
            instance = new BRRush();
        }

        return instance;
    }

    MapLocation enemyRatKingGuess = null;

    @Override
    public void execute() throws GameActionException {
        if(enemyRatKingGuess == null) {
            //System.out.println(SymmetryManager.getSymmetric(Communicator.getRatKings().get(0).loc()));
           // enemyRatKingGuess = SymmetryManager.getSymmetric(Communicator.getRatKings().get(0).loc());
        }
       // Pathfinding.attemptMove(enemyRatKingGuess, true);
    }
}
