package bench_stroke;

import bench_stroke.Communication.*;
import bench_stroke.DataStructures.FastIterableLocMap;
import bench_stroke.RatKingStateUpdaters.SimpleRKStateUpdater;
import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

import java.util.Arrays;

import static bench_stroke.BabyRat.RAT_KING_FORMATION_THRESHOLD;
import static bench_stroke.Communication.Communicator.getInDistressRatKing;
import static bench_stroke.RobotPlayer.MAP_HEIGHT;
import static bench_stroke.RobotPlayer.MAP_WIDTH;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.SymmetryManager.SYMMETRY_INDEX;


public class RatKing {
    /**
     * List of adjacent tile offsets for a 3x3 Rat King.
     */
    public static final MapLocation[] adjacentTiles = {
            new MapLocation(0, 2),
            new MapLocation(1, 2),
            new MapLocation(2, 2),
            new MapLocation(2, 1),
            new MapLocation(2, 0),
            new MapLocation(2, -1),
            new MapLocation(2, -2),
            new MapLocation(1, -2),
            new MapLocation(0, -2),
            new MapLocation(-1, -2),
            new MapLocation(-2, -2),
            new MapLocation(-2, -1),
            new MapLocation(-2, 0),
            new MapLocation(-2, 1),
            new MapLocation(-2, 2),
            new MapLocation(-1, 2)
    };

    /**
     * The stateUpdater decides which behavior needs to be run at the beginning of each turn.
     */
    static StateUpdater stateUpdater;

    //per turn sensed info
    public static MapLocation currentLocation;
    static int health;
    static int turn;
    public static RobotInfo[] nearbyRobots;
    public static RobotInfo nearestCat = null;
    public static int turnSeenCat = -1;
    public static RobotInfo nearestEnemy;
    public static boolean inDistress;
    public static MapLocation closestCheeseMine = null;
    public static boolean travelingToCheeseMine = false;
    public static FastIterableLocSet unsafeMines;
    public static boolean pickingUpCheese = false;
    public static boolean chiefRat;
    //maximum number of rat kings we will ever have
    public static int maxRatKings = (MAP_HEIGHT * MAP_WIDTH <= 2500) ? 2 : 3;

    public static boolean sharedSymmetry = false;

    public static int ratsBuilt = 0;
    public static int trapsBuilt = 0;

    public static int numAllies;
    public static int numEnemies;

    public static int cheeseIncome;
    public static int lastTurnCheese;

    public static boolean movingTowardsCenter;
    public static boolean centerDangerous = false;

    //calibrated to be 7 at smallest map and 12 at largest map
    //y = 0.002x + 6.2
    public static final int RAT_BUILD_THRESHOLD = (int) (0.002 * (rc.getMapHeight() * rc.getMapWidth()) + 6.2);
    //dont spawn rats for higher than this cost unless in distress
    //cost for 12, 13, 14, 15
    public static int RAT_COST_THRESHOLD = (RobotPlayer.MAP_HEIGHT * RobotPlayer.MAP_WIDTH <= (23 * 23)) ? 36 : 40;

    // public static final int RK_FORMATION_THRESHOLD = 80;
    // public static final int INCREASE_THRESHOLD_INCOME = 500;

    //if we have more cheese than this, why not build more baby rats?
    public static final int CONTINUE_BUILD_CHEESE_THRESHOLD = 2000;

    //how long to keep running after last seeing cat
    public static final int RUN_CAT_ROUNDS = 15;

    //dont build anything below this global cheese amount, because it might kill us
    public static final int BUILD_ANYTHING_THRESHOLD = 80;
    //calibrated to be 30 on smallest map and 80 on biggest map
    public static final int LEAVE_CORNER_ROUND = (int) (0.016 * (MAP_HEIGHT * MAP_WIDTH) + 23.6);

    public static final int RESET_UNSAFE_MINES = 75;

    public static final int SPAM_BOTS_FRENZY_ROUND = 1900;

    public static boolean secondRat = false;


    /**
     * Initializes the Rat King in its first turn.
     * @throws GameActionException 
     */
    public static void initializeRatKing() throws GameActionException {
        stateUpdater = new SimpleRKStateUpdater();
        //mines = new FastIterableLocSet();
        unsafeMines = new FastIterableLocSet();
    }

    /**
     * Runs one turn of a Rat King.
     */
    public static void runRatKing() throws GameActionException {
        updateInfo();

        Behavior state = stateUpdater.decideBehavior();
        state.execute();

        // if (Clock.getBytecodesLeft() > 500 && rc.getGlobalCheese() > 30) {
        //     Utilities.placeTrapAsRatKing();
        // }
        if (Clock.getBytecodesLeft() > 500 && rc.isActionReady()) {
            Utilities.attemptAttackAsRatKing();
        }
        if (Clock.getBytecodesLeft() > 500 && !sharedSymmetry) {
            attemptToReadAndShareSymmetry();
        }
        if (Clock.getBytecodesLeft() > 200) {
            Utilities.attemptToPickUpRatKingCheese();
        }
        // if (Clock.getBytecodesLeft() > 200 && rc.isActionReady() && !inDistress && rc.getGlobalCheese() > 1000) {
        //     Utilities.attemptMineDirtAsRatKing();
        // }

        lastTurnCheese = rc.getAllCheese();

    }


    /**
     * Updates all information from last turn, aids the stateUpdater in making a decision.
     */
    public static void updateInfo() throws GameActionException {
//         SymmetryManager.seenMines.updateIterable();
//         StringBuilder sb = new StringBuilder();
//         for(int i = 0; i < SymmetryManager.seenMines.size; i++) {
//             sb.append(SymmetryManager.seenMines.getKey(i) + " " + SymmetryManager.seenMines.getValue(i) + ", ");
//         }
//        System.out.println(sb);
        // for(CheeseMineInfo mineInfo : Communicator.getCheeseMines()) {
        //     rc.setIndicatorDot(mineInfo.location(),0, 255, 0);
        // }
        currentLocation = rc.getLocation();

        health = rc.getHealth();
        turn = rc.getRoundNum();
        nearbyRobots = rc.senseNearbyRobots(-1);

        numAllies = 0;
        numEnemies = 0;

        if (rc.getRoundNum() % RESET_UNSAFE_MINES == 0) {
            unsafeMines.clear();
            centerDangerous = false;
        }


        // if (cheeseIncome > INCREASE_THRESHOLD_INCOME && rc.getGlobalCheese() > 500 && RAT_COST_THRESHOLD < RAT_KING_FORMATION_THRESHOLD) {
        //     RAT_COST_THRESHOLD += 5;
        //     cheeseIncome = 0;
        // }


        inDistress = false;
        //nearestCat = null;
        nearestEnemy = null;
        int nearestCatDist = Integer.MAX_VALUE;
        int nearestEnemyDist = Integer.MAX_VALUE;
        int averageEnemyX = 0;
        int averageEnemyY = 0;
        for (RobotInfo robot : nearbyRobots) {
            int dist = currentLocation.distanceSquaredTo(robot.location);
            UnitType type = robot.getType();
            if (type == UnitType.CAT) {
                if (dist < nearestCatDist) {
                    nearestCatDist = dist;
                    nearestCat = robot;
                    turnSeenCat = rc.getRoundNum();
                }
                if (Utilities.withinCatVision(currentLocation, robot)) {
                    inDistress = true;
                    pickingUpCheese = false;
                }
            }
            else if (type == UnitType.BABY_RAT) {
                if (robot.getTeam() == rc.getTeam().opponent()) {
                    if (dist < nearestEnemyDist) {
                        nearestEnemyDist = dist;
                        nearestEnemy = robot;
                    }
                    averageEnemyX += robot.location.x;
                    averageEnemyY += robot.location.y;
                    numEnemies++;
                }
                else {
                    numAllies++;
                }
            }
        }

        if(!inDistress && nearestEnemy != null) {
            inDistress = true;
            pickingUpCheese = false;
        }

        if (numEnemies > 0) {
            MapLocation averageEnemy = new MapLocation(averageEnemyX / numEnemies, averageEnemyY / numEnemies);
            Communicator.storeAverageEnemyInSharedArray(averageEnemy);
        }
        else if (Communicator.getAverageEnemyFromSharedArray() != null && getInDistressRatKing(currentLocation) == null) {
            Communicator.clearAverageEnemyInSharedArray();
        }
        Communicator.storeRatKingInSharedArray(currentLocation, health, turn, inDistress);

        //try to approximate how much cheese we are pulling in
        if (rc.getRoundNum() > 1) {
            cheeseIncome = (rc.getAllCheese() - lastTurnCheese > 0) ? cheeseIncome + (rc.getAllCheese() - lastTurnCheese) : cheeseIncome;
        }
        Squeak[] mineSqueaks = Communicator.getSqueaksOfType(CheeseMineSqueakInfo.class);
        //System.out.println(Arrays.toString(Communicator.getCheeseMines()));
        for(Squeak mineSqueak : mineSqueaks) {
            CheeseMineSqueakInfo squeakInfo = (CheeseMineSqueakInfo) mineSqueak.squeakInfo;
            MapLocation cheeseLoc = squeakInfo.location();
            int valToPut = Math.max(squeakInfo.threatLevel(),
            SymmetryManager.seenMines.get(squeakInfo.location(), 0));
            SymmetryManager.seenMines.put(squeakInfo.location(), valToPut);
//                System.out.println(squeakInfo.location());
            Communicator.storeCheeseMineToArray(cheeseLoc, squeakInfo.threatLevel());

            MapLocation oppositeMine = SymmetryManager.getSymmetric(squeakInfo.location());
            if (oppositeMine != null && ! SymmetryManager.seenMines.contains(oppositeMine)) {
                SymmetryManager.seenMines.put(oppositeMine, 127);
            }
        }

        
        if (inDistress && closestCheeseMine != null && currentLocation.distanceSquaredTo(closestCheeseMine) <= 25) {
            unsafeMines.add(closestCheeseMine);
        }
        else if (closestCheeseMine != null && rc.canSenseLocation(closestCheeseMine) && rc.senseRobotAtLocation(closestCheeseMine) != null) {
            if (rc.senseRobotAtLocation(closestCheeseMine).getType() == UnitType.RAT_KING && rc.senseRobotAtLocation(closestCheeseMine).getID() != rc.getID()) {
                unsafeMines.add(closestCheeseMine);
            }
        }

        closestCheeseMine = Utilities.closestCheeseMine(currentLocation, unsafeMines);

        if (chiefRat || Communicator.getNumRatKings() == 1) {
            chiefRat = true;
            secondRat = false;
        }
        else if (!secondRat && !chiefRat && Communicator.getNumRatKings() == 2) {
            secondRat = true;
        }

        if (inDistress && movingTowardsCenter) {
            centerDangerous = true;
        }
        movingTowardsCenter = false;

        // if(rc.getRoundNum() < 400){
        //     CheeseMineInfo[] curKnownMines = Communicator.getCheeseMines();
        //     int allySideArea = (MAP_HEIGHT * MAP_WIDTH)/2;
        //     int numMines = curKnownMines == null ? 0 : curKnownMines.length;
        //     double tilesPerMine = (double) allySideArea / Math.max(1, numMines);
        //     if(tilesPerMine <= 60.0) maxRatKings = 4;
        //     else if(tilesPerMine <= 130.0) maxRatKings = 3;
        //     else maxRatKings = 2;
        // }
    }

    public static void attemptToReadAndShareSymmetry() throws GameActionException {
        Squeak squeak = Communicator.getMostRecentSqueakOfType(SymmetrySqueakInfo.class);
        if (squeak != null) {
            SymmetrySqueakInfo squeakInfo = (SymmetrySqueakInfo) squeak.squeakInfo;
            int symmetry = squeakInfo.symmetry();
            if (rc.readSharedArray(SymmetryManager.SYMMETRY_INDEX) == 0) {
                SymmetryManager.setSym(symmetry);
                rc.writeSharedArray(SymmetryManager.SYMMETRY_INDEX, SymmetryManager.getSym());
                sharedSymmetry = true;
            }
        }
    }
}


