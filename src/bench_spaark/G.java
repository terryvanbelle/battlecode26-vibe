package bench_spaark;

import battlecode.common.*;
import bench_spaark.fast.RobotInfoMap;

/**
 * Stores global stuff that all bots use (more or less) and don't want to pass
 * around, and doesn't fit anywhere else
 */
public class G {
    // stuff that doesn't change
    public static RobotController rc;
    public static UnitType type;
    public static MapLocation mapCenter;
    public static int mapWidth;
    public static int mapHeight;
    public static int mapArea;
    public static Team team;
    public static Team opponentTeam;
    public static int roundSpawned;
    // not in game constants for some reason
    public static final int CAT_SIGHT_RANGE_SQUARED = 17;

    public static final Direction[] DIRECTIONS = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.EAST,
            Direction.NORTHEAST,
            Direction.NORTH,
            Direction.NORTHWEST,
            Direction.WEST,
    };

    public static final Direction[] ALL_DIRECTIONS = {
            Direction.SOUTHWEST,
            Direction.SOUTH,
            Direction.SOUTHEAST,
            Direction.EAST,
            Direction.NORTHEAST,
            Direction.NORTH,
            Direction.NORTHWEST,
            Direction.WEST,
            Direction.CENTER
    };

    public static int dirOrd(Direction d) throws Exception {
        switch (d) {
            case Direction.SOUTHWEST:
                return 0;
            case Direction.SOUTH:
                return 1;
            case Direction.SOUTHEAST:
                return 2;
            case Direction.EAST:
                return 3;
            case Direction.NORTHEAST:
                return 4;
            case Direction.NORTH:
                return 5;
            case Direction.NORTHWEST:
                return 6;
            case Direction.WEST:
                return 7;
            case Direction.CENTER:
                return 8;
            default:
                throw new Exception("how did you get an invalid dir");
        }
    }

    public static final MapLocation invalidLoc = new MapLocation(-1, -1);

    // stuff that changes
    public static StringBuilder indicatorString;
    public static MapLocation me;
    public static MapLocation lastMe = invalidLoc;
    public static Direction dir;
    public static RobotInfo[] robots;
    public static RobotInfo[] allyRobots;
    public static RobotInfoMap opponentRobots;
    public static RobotInfo[] cats;
    public static boolean detectedNonAllyRobots;
    public static StringBuffer allyRobotString;
    public static int backstabRound = 2000;

    public static final int ROBOT_HISTORY_TURNS = 4;
    
    // CURRENTLY UNUSED
    public static StringBuffer[] allyRobotStrings = new StringBuffer[ROBOT_HISTORY_TURNS];
    public static StringBuffer[] opponentRobotStrings = new StringBuffer[ROBOT_HISTORY_TURNS];
    public static StringBuffer allAllyRobotStrings;
    public static StringBuffer allOpponentRobotStrings;

    public static MapInfo[] nearbyMapInfos;
    public static int round;

    public static MapLocation lastSeenOpponentLocation;
    public static RobotInfo lastSeenOpponent;
    public static int lastSeenOpponentRound;
    public static MapLocation lastSeenCatLocation;
    public static RobotInfo lastSeenCat;
    public static int lastSeenCatRound;
    // divide all coordinates by 5, now 12x12
    // 1/25th the size of 60x60, don't need the resolution
    // this is to match comms resolution
    public static int[][] lastVisited = new int[12][12];

    public static void setLastVisited(int x, int y, int n) {
        lastVisited[y / 5][x / 5] = n + 2000;
    }

    public static void setLastVisited(MapLocation loc, int n) {
        lastVisited[loc.y / 5][loc.x / 5] = n + 2000;
    }

    public static int getLastVisited(int x, int y) {
        return lastVisited[y / 5][x / 5] - 2000;
    }

    public static int getLastVisited(MapLocation loc) {
        return lastVisited[loc.y / 5][loc.x / 5] - 2000;
    }
}