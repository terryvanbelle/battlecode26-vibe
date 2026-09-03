package bench_stroke;

import bench_stroke.BabyRat;
import bench_stroke.Communication.Communicator;
import bench_stroke.Debug;
import bench_stroke.RatKing;
import bench_stroke.SymmetryManager;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.RobotController;

import java.util.Random;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    public static RobotController rc;
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    public static int turnCount = 0;


    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    public static Random rng;
    public static int MAP_WIDTH;
    public static int MAP_HEIGHT;


    /**
     * Array containing all the possible movement directions.
     */
    static final Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST,
    };

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc The RobotController object. You use it to perform actions from this robot, and to get
     *           information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        Debug.enableDebug();
        RobotPlayer.rc = rc;

        MAP_HEIGHT = rc.getMapHeight();
        MAP_WIDTH = rc.getMapWidth();

        Pathfinding.passable = new int[MAP_WIDTH][MAP_HEIGHT];

        SymmetryManager.setBase(rc.getLocation());

        Reachability.initializeReachability(MAP_WIDTH, MAP_HEIGHT);

        rng = new Random(rc.getID());

        switch (rc.getType()) {
            case BABY_RAT -> BabyRat.initializeBabyRat();
            case RAT_KING -> RatKing.initializeRatKing();
        }

        while (true) {

            turnCount += 1;

            Communicator.initializeCommunication();

            try {
                switch (rc.getType()) {
                    case BABY_RAT -> BabyRat.runBabyRat();
                    case RAT_KING -> RatKing.runRatKing();
                }
                if (Clock.getBytecodesLeft() > 500) {
                    SymmetryManager.checkSym();
                }
//                if (Symmetry.knownSymmetry != Symmetry.SymmetryType.Unknown) {
//                    System.out.println(Symmetry.knownSymmetry.toString());
//                }

            } catch (GameActionException e) {
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                Clock.yield();
            }
        }
    }
}
