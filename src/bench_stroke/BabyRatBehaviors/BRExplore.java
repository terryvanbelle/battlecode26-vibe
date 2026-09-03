package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Utilities;
import bench_stroke.DataStructures.FastMath;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.*;
import bench_stroke.Communication.*;

import static bench_stroke.RobotPlayer.*;
import static bench_stroke.BabyRat.*;

import bench_stroke.SymmetryManager;

public class BRExplore implements Behavior {
    //Constants
    static final int RANDOM_LOC_TURN_RESET = 75;

    static final int INF = 1000000000;

    //Instance Variables (persist over state changes)
    MapLocation randomLocInGivenDirection;
    Direction exploreDirection;
    //boolean[] quadrants = {false, false, false, false};


    //Singleton Stuff
    private static BRExplore instance;
    private BRExplore() {}
    public static BRExplore getInstance() {
        if(instance == null) {
            instance = new BRExplore();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {

        // if (rc.getCarrying() != null && rc.isActionReady()) {
        //     Utilities.attemptThrow(turnsCarrying, health);
        // }
        

        if (exploreDirection == null) {
            //RatKingInfo father = new RatKingInfo(new MapLocation(0, 0), 0, 0, false);
            RatKingInfo father = Communicator.getClosestRatKing(currentLocation);
            Direction dirToFather = currentLocation.directionTo(father.loc());
            exploreDirection = dirToFather.opposite();
        }

        randomLocInGivenDirection = getExplore3Target();

        Pathfinding.attemptMoveExplore(randomLocInGivenDirection, true);

        rc.setIndicatorLine(currentLocation, randomLocInGivenDirection, 255, 255, 255);

        if ((rc.getRoundNum() >= 75 && SymmetryManager.getSym() != 0)) { //&& (rc.getLocation().distanceSquaredTo(closestAllyRatKing.loc()) <= 16) || rc.getRoundNum() > 350) {
            if (FastMath.fakefloat() > 0.7) {
                targetCheeseMine = Utilities.closestCheeseMineInDirection(rc.getLocation(), exploreDirection);
            }
            else {
                targetCheeseMine = Utilities.randomCheeseMine();
            }
            //System.out.println(targetCheeseMine);
        }

        // int curQuad = Utilities.calculateQuadrant(currentLocation);
        // quadrants[curQuad] = true;
        // if (quadrants[0] && quadrants[1] && quadrants[2] && quadrants[3]) {
        //     quadrants[0] = false;
        //     quadrants[1] = false;
        //     quadrants[2] = false;
        //     quadrants[3] = false;
        // }
        // if (randomLoc == null || turnCount % RANDOM_LOC_TURN_RESET == 0 || rc.canSenseLocation(randomLoc) ) {
        //     while (randomLoc == null || rc.canSenseLocation(randomLoc) || quadrants[Utilities.calculateQuadrant(randomLoc)]) {
        //         int x = rng.nextInt(0, MAP_WIDTH);
        //         int y = rng.nextInt(0, MAP_HEIGHT);
        //         randomLoc = new MapLocation(x, y);
        //     }
        // }

        // MapLocation averageSqueak = Utilities.getAveragePresenceSqueakLocation();
        // if (averageSqueak != null) {
        //     Pathfinding.attemptMove(averageSqueak, true);
        // }
        // Pathfinding.attemptMoveExplore(randomLocInGivenDirection, true);
        //Communicator.sendSqueak(new PresenceSqueakInfo());
    }

    // Higher scores point away from the nearest map edge so we bias exploration outward.
    private Direction pickWeightedDirection(Direction avoid1, Direction avoid2) {
        int[] scores = scoreDirections();
        int total = 0;
        Direction[] dirs = Direction.allDirections();
        for (Direction d : dirs) {
            if (d == Direction.CENTER || d == avoid1 || d == avoid2) continue;
            int s = scores[d.ordinal()];
            if (s <= 0) continue;
            // keep a small baseline so we always have some option even when hugging an edge
            total += s + 1;
        }
        if (total == 0) {
            Direction dir = dirs[FastMath.rand9()];
            while (dir == Direction.CENTER || dir == avoid1 || dir == avoid2) dir = dirs[FastMath.rand9()];
            return dir;
        }
        // int pick = rng.nextInt(total);
        int pick = FastMath.randBound(total);
        for (Direction d : dirs) {
            if (d == Direction.CENTER || d == avoid1 || d == avoid2) continue;
            int s = scores[d.ordinal()];
            if (s <= 0) continue;
            pick -= (s + 1);
            if (pick < 0) return d;
        }
        //should never happen, but keep a fallback
        Direction dir = dirs[FastMath.rand9()];
        while (dir == Direction.CENTER || dir == avoid1 || dir == avoid2) dir = dirs[FastMath.rand9()];
        return dir;
    }

    private int[] scoreDirections() {
        int[] scores = new int[9];
        int x = currentLocation.x;
        int y = currentLocation.y;
        int left = Math.max(0, x);
        int right = Math.max(0, MAP_WIDTH - 1 - x);
        int down = Math.max(0, y);
        int up = Math.max(0, MAP_HEIGHT - 1 - y);

        scores[Direction.NORTH.ordinal()] = up;
        scores[Direction.NORTHEAST.ordinal()] = Math.min(up, right);
        scores[Direction.EAST.ordinal()] = right;
        scores[Direction.SOUTHEAST.ordinal()] = Math.min(down, right);
        scores[Direction.SOUTH.ordinal()] = down;
        scores[Direction.SOUTHWEST.ordinal()] = Math.min(down, left);
        scores[Direction.WEST.ordinal()] = left;
        scores[Direction.NORTHWEST.ordinal()] = Math.min(up, left);

        return scores;
    }

    boolean movingOutOfMap(Direction dir){
        try {
            MapLocation loc = rc.getLocation().add(dir);
            if (!rc.onTheMap(loc)) {
                return true;
            }
            loc = loc.add(dir);
            if (!rc.onTheMap(loc)) {
                return true;
            }
            loc = loc.add(dir);
            if (!rc.onTheMap(loc)) {
                return true;
            }
            loc = loc.add(dir);
            if (rc.canSenseLocation(loc) && !rc.onTheMap(loc)) {
                return true;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return false;
    }

    void checkDirection(){
        if (!movingOutOfMap(exploreDirection)) return;

        Direction[] possibleDirs = new Direction[8];
        int cont = 0;
        Direction d = exploreDirection.rotateLeft().rotateLeft();
        if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
        d = d.rotateLeft();
        if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
        d = d.rotateLeft();
        if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
        d = d.rotateLeft();
        if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
        d = d.rotateLeft();
        if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
        if (cont == 0) {
            d = d.rotateLeft();
            if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
            d = d.rotateLeft();
            d = d.rotateLeft();
            if (!movingOutOfMap(d)) possibleDirs[cont++] = d;
        }

        int randomDir = (int)(Math.random()*cont);

        assignExplore3Dir(possibleDirs[randomDir]);
    }

    void assignExplore3Dir(Direction dir){
        exploreDirection = dir;
        int diffX = INF, diffY = INF;
        if (dir.dx > 0) diffX = MAP_WIDTH - rc.getLocation().x - 1;
        else if (dir.dx < 0) diffX = rc.getLocation().x;
        if (dir.dy > 0) diffY = MAP_HEIGHT - rc.getLocation().y - 1;
        else if (dir.dy < 0) diffY = rc.getLocation().y;
        int diff = diffX;
        if (diffY < diffX) diff = diffY;
        randomLocInGivenDirection = rc.getLocation().translate(diff*dir.dx, diff*dir.dy);
    }

    MapLocation getExplore3Target(){
        checkDirection();
        if (randomLocInGivenDirection == null) {
            assignExplore3Dir(exploreDirection);
        }
        return randomLocInGivenDirection;
    }
}
