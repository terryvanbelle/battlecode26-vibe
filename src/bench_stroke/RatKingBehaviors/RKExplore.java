package bench_stroke.RatKingBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import bench_stroke.Utilities;
import bench_stroke.DataStructures.FastMath;

import static bench_stroke.RobotPlayer.*;

import bench_stroke.FastIterableLocSet;

public class RKExplore implements Behavior {
    //Constants
    static final int RANDOM_LOC_TURN_RESET = 30;
    //tracks whether we have explored
    //clockwise starting in top left
    boolean[] quadrants = {false, false, false, false};
   // FastIterableLocSet exploredLocations = new FastIterableLocSet();

    //Instance Variables (persist over state changes)
    MapLocation randomLoc;

    //Singleton Stuff
    private static RKExplore instance;
    private RKExplore() {}
    public static RKExplore getInstance() {
        if(instance == null) {
            instance = new RKExplore();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        int curQuad = Utilities.calculateQuadrant(rc.getLocation());
        quadrants[curQuad] = true;
        if (quadrants[0] && quadrants[1] && quadrants[2] && quadrants[3]) {
            quadrants[0] = false;
            quadrants[1] = false;
            quadrants[2] = false;
            quadrants[3] = false;
        }
        if (randomLoc == null || turnCount % RANDOM_LOC_TURN_RESET == 0 || rc.canSenseLocation(randomLoc) ) {
            while (randomLoc == null || rc.canSenseLocation(randomLoc) || quadrants[Utilities.calculateQuadrant(randomLoc)]) {
                // int x = rng.nextInt(0, MAP_WIDTH);
                // int y = rng.nextInt(0, MAP_HEIGHT);
                int x = FastMath.randBound(MAP_WIDTH);
                int y = FastMath.randBound(MAP_HEIGHT);
                randomLoc = new MapLocation(x, y);
            }
        }
        Pathfinding.attemptMove(randomLoc, false);
    }
}
