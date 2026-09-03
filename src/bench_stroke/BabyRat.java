package bench_stroke;

import bench_stroke.BabyRatStateUpdaters.DefectBRStateUpdater;
import bench_stroke.DataStructures.FastIterableLocMap;
import bench_stroke.DataStructures.FastIntSet;
import bench_stroke.DataStructures.FastLocSet;
import bench_stroke.DataStructures.FastMath;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.*;
import bench_stroke.Communication.*;
import static bench_stroke.RobotPlayer.rng;


import java.util.Arrays;
import java.util.Map;

 
import static bench_stroke.RatKing.nearestCat;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.SymmetryManager.target;


public class BabyRat {
    /**
     * The stateUpdater decides which behavior needs to be run at the beginning of each turn.
     */
    static StateUpdater stateUpdater;

    //per turn sensed info
    public static MapLocation currentLocation;
    public static MapInfo[] locsInRadius;
    public static MapLocation nearestCheese;
    public static MapLocation nearestEmptyCheeseMine;
    public static MapLocation nearestAnyCheeseMine;
    public static MapLocation targetCheeseMine; //used to return to a cheese mine after bringing cheese to rat king
    //public static int lowestHealthRatKing;
    public static RobotInfo[] nearbyAllies;
    public static int numberAllies;
    public static MapLocation averageAlly; //set by getting number of allies
    public static RobotInfo[] nearbyEnemies;
    public static MapLocation nearestEnemy;
    public static RobotInfo nearestSeenEnemy;
    public static boolean seeEnemyRatKing;
    public static RobotInfo nearestCat;
    public static RobotInfo mostRecentCat;
    public static int turnsSinceSeenCat = 0;
    public static MapLocation enemyRatKingSeenLoc;
    public static RatKingInfo inDistressRatKing;
    public static RobotInfo[] nearbyCats;
    public static MapLocation nearestCatLocation;
    public static boolean currentlyLuring = false;
    public static int health;
    public static int turnsCarrying = 0;
    public static int seenCheese;
    public static RatKingInfo closestAllyRatKing;
    public static boolean reachabilityUpdated = false;
    public static Squeak catSqueak;
    public static boolean sawDirt;
    public static int allyHealth;
    public static int roundLastCatSqueaked = 0;
    public static FastIterableLocMap allRobots;

    public static FastLocSet allyTraps = new FastLocSet();

    public static MapLocation startingLoc;
    
    public static MapLocation closestPickupRequest = null;

    public static int turnsSinceEnemy = 0;

    public static boolean ferrying = false;

    public static MapLocation targetMineForFormation = null;

    //public static FastLocSet catVisionSquares = null;

    //public static FastIterableLocMap extraEnemies = new FastIterableLocMap(10);
    public static FastLocSet allEnemyLocations = new FastLocSet();

    public static FastIterableLocMap map = null;

    public static boolean processedSharedArrayMines = false; //only do this once, when we spawn
    public static boolean processedSymmetry = false; //when we learn of symmetry, iterate through seenMines and add opposites

    private static FastIntSet allyIDs = new FastIntSet();
//#    public static CheeseMineInfo[] knownMines;



    //constants to be adjusted
    public static int RETURN_CHEESE_THRESHOLD = 20;
    public static final int ABSOLUTE_RETURN_CHEESE_THRESHOLD = 100;
    public static final int RAT_KING_FORMATION_THRESHOLD = 150;
    //keep small for now, dont want to be rushing across map to help rat king
    //primarily just want to go into rescue mode if we are spawned by rat king to help them
    public static final int ANSWER_DISTRESS_DIST_THRESHOLD = 25;
    //threshold to start being more liberal with aggro trap placement
    public static final int AGGRO_TRAP_THRESHOLD = 900;
    //threshold for which, over this distance from rat king, you avoid cats on cheese return
    //trying to get this to match distress threshol
    public static final int AVOID_CATS_CHEESE_RETURN_THRESHOLD = 25;
    //after this much global cheese we are fine with digging with global cheese
    public static final int GLOBAL_CHEESE_DIG_THRESHOLD = 500;
    //after this much health, lets be bold
    public static final int BOLD_HEALTH_THRESHOLD = 80;
    //retreat below this health
    public static final int RETREAT_THRESHOLD = 20;
    //the round we start attacking cats for tiebreaker reasons
    public static final int ATTACK_CAT_ROUND = 1700;
    //radius to report dangers around mines
    public static final int MINE_THREAT_RADIUS_SQR = 41;


    public static RobotInfo carrier;
    public static Direction thrownDirection;
//#    // after this round, each spawned baby rat gets an assigned cheese mine to go to
//#    public static final int MINE_ASSIGNMENT_ROUND = 200;

    /**
     * Initializes the Baby Rat in its first turn.
     * @throws GameActionException 
     */
    public static void initializeBabyRat() throws GameActionException {
        stateUpdater = DefectBRStateUpdater.getInstance();
        int sym = rc.readSharedArray(SymmetryManager.SYMMETRY_INDEX);
        if (sym != 0) {
            SymmetryManager.setSym(sym);
        }
        FastMath.initRand(rc);
        allRobots = new FastIterableLocMap(45);
        //stateUpdater = new RushBRStateUpdater();
    }

    /**
     * Runs one turn of a Rat King.
     */
    public static void runBabyRat() throws GameActionException {
        updateInfo();

        if (nearestCatLocation != null && nearestCatLocation.distanceSquaredTo(currentLocation) <= 8 && nearbyEnemies.length == 0 && rc.isActionReady()) {
            Utilities.attemptAttackCat();
        }

        if (nearestCatLocation != null && closestAllyRatKing.loc().distanceSquaredTo(currentLocation) <= GameConstants.SQUEAK_RADIUS_SQUARED && rc.getRoundNum() - roundLastCatSqueaked > 1) {
            Communicator.sendSqueak(new NearbyCatSqueakInfo(nearestCatLocation, true, nearestCat.direction));
            roundLastCatSqueaked = rc.getRoundNum();
        }
        else if (nearestEnemy != null && closestAllyRatKing.loc().distanceSquaredTo(currentLocation) <= GameConstants.SQUEAK_RADIUS_SQUARED && rc.getRoundNum() - roundLastCatSqueaked > 1) {
            Communicator.sendSqueak(new NearbyCatSqueakInfo(nearestEnemy, false, (nearestSeenEnemy != null) ? nearestSeenEnemy.direction : Direction.NORTH));
            roundLastCatSqueaked = rc.getRoundNum();
        }

        if (rc.isActionReady() && rc.getCarrying() != null && currentLocation.distanceSquaredTo(closestAllyRatKing.loc()) <= 25) {
            Direction dirToRatKing = currentLocation.directionTo(closestAllyRatKing.loc());
            Direction opposite = dirToRatKing.opposite();
            Direction direction = rc.getDirection();
            if (rc.canTurn() && direction != opposite && direction != opposite.rotateLeft() && direction != opposite.rotateRight()){
                rc.turn(opposite);
                //try to throw desperately in opposite direction of rat king so we dont sabotage ourselves
                Utilities.attemptThrow(9, rc.getHealth());
            }
        }

        if (rc.isActionReady() && rc.getCarrying() != null && (turnsCarrying > 5 || nearbyCats.length > 0) && !ferrying) {
            boolean earlyThrow = Utilities.attemptThrow(turnsCarrying, health);
        }

        Behavior state = stateUpdater.decideBehavior();

        if (rc.isActionReady() && allEnemyLocations.size() == 0 && nearestCat != null) {
            boolean attacked = Utilities.attemptAttackCat();
            // if (attacked) System.out.println("attacked cat");
        }

       //if (rc.getRawCheese() >= 100) System.out.println(rc.getRawCheese());

        state.execute();

        // if (Clock.getBytecodesLeft() > 1500) {
        //     int bytecodes = Clock.getBytecodesLeft();
        //     Reachability.updateReachability(rc.getLocation());

        //     System.out.println(bytecodes - Clock.getBytecodesLeft());
        //     reachabilityUpdatedLastTurn = true;
        // }
        // else {
        //     reachabilityUpdatedLastTurn = false;
        // }

        if (Clock.getBytecodesLeft() > 1500){
            if ((nearbyEnemies.length > 0 || nearbyCats.length > 0)) {
                updateMineThreatLevels();
            }
        }

        boolean squeaked = false;

        if (rc.isActionReady() && Clock.getBytecodesLeft() > 1500) {
            Utilities.attemptAttack();
        }
        if (rc.getRawCheese() > 0 && Clock.getBytecodesLeft() > 500) {
            boolean transfered = Utilities.attemptCheeseTransfer();
            if (transfered) {
                CheeseMineSqueakInfo info = Communicator.findMineToStore(map);
                if(info != null) {
                    Communicator.sendSqueak(info);
                    squeaked = true;
                }
            }
        }
        if (!squeaked && Clock.getBytecodesLeft() > 250 && rc.getRoundNum() - roundLastCatSqueaked > 1) {
            if (nearestCat != null && (rc.getLocation().distanceSquaredTo(nearestCat.location) > GameConstants.SQUEAK_RADIUS_SQUARED || nearestCat.location.isWithinDistanceSquared(rc.getLocation(), 17, nearestCat.direction, 180, true))) {
                    //System.out.println("cat squeaking!");
                    roundLastCatSqueaked = rc.getRoundNum();
                    Communicator.sendSqueak(new NearbyCatSqueakInfo(nearestCat.location, true, nearestCat.direction));
                    squeaked = true;
            }
        }

        if (rc.isCooperation() && nearestCatLocation == null && Clock.getBytecodesLeft() > 100) {
            boolean removed = Utilities.attemptRemoveCatTraps();
            // if (removed) System.out.println("removed!");
        }
        // //cheese expensive...
        // if (rc.canBecomeRatKing() && Clock.getBytecodesLeft() > 500 && Communicator.getRatKings().size() == 1 && Communicator.getLowestHealthRatKing().health() < RAT_KING_FORMATION_THRESHOLD) {
        //     rc.becomeRatKing();
        //     RatKing.initializeRatKing();
        // }
        if (Clock.getBytecodesLeft() > 100 && SymmetryManager.getSym() == 0) {
            int sym = rc.readSharedArray(SymmetryManager.SYMMETRY_INDEX);
            if (sym != 0) {
                SymmetryManager.setSym(sym);
            }
        }
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 1000 && nearbyEnemies.length > 0) {
            Utilities.attemptRatnap();
        }
        // if (turnsCarrying > 0 && rc.isActionReady() && Clock.getBytecodesLeft() > 400) {
        //     Utilities.attemptThrow(turnsCarrying, health);
        // }
        if (Clock.getBytecodesLeft() > 100 && nearestCheese != null && rc.canPickUpCheese(nearestCheese) && rc.getRawCheese() <= 100) {
            rc.pickUpCheese(nearestCheese);
        }
        else if (rc.canPickUpCheese(rc.getLocation())) {
            rc.pickUpCheese(rc.getLocation());
        }
        if (Clock.getBytecodesLeft() > 200 && rc.isActionReady() && rc.getDirt() > 0 && nearbyCats.length > 0 && rc.getRawCheese() >= 50) {
            MapLocation[] catLocs = Utilities.getCatActionableLocations(nearbyCats[0].location);
            if (catLocs != null) Utilities.tryBuild(catLocs);
        }
        if (rc.isActionReady() && Clock.getBytecodesLeft() > 100 && (rc.getAllCheese() > 500|| rc.getRawCheese() > 20) && nearestEmptyCheeseMine != null) {
            Utilities.attemptTrapMine(nearestEmptyCheeseMine, rc.getAllCheese() > 1000 && rc.getRoundNum() > 150);
        }

        if (Clock.getBytecodesLeft() > 150 && nearestCatLocation != null && nearestCatLocation.distanceSquaredTo(currentLocation) <= 8 && nearbyEnemies.length == 0 && rc.isActionReady()) {
            Utilities.attemptAttackCat();
        }

        if (!squeaked && Clock.getBytecodesLeft() > 500 && rc.getLocation().distanceSquaredTo(closestAllyRatKing.loc()) <= GameConstants.SQUEAK_RADIUS_SQUARED) {
            if (nearbyEnemies.length == 0) {
                nearestEnemy = null;
                nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
                if (nearbyEnemies.length > 0) {
                    int nearestEnemyDist = Integer.MAX_VALUE;
                    for (RobotInfo enemy : nearbyEnemies) {
                        int dist = currentLocation.distanceSquaredTo(enemy.location);
                        if (dist < nearestEnemyDist) {
                            nearestEnemyDist = dist;
                            nearestSeenEnemy = enemy;
                            nearestEnemy = enemy.location;
                        }
                    }
                }
                if (nearestEnemy != null && rc.getRoundNum() - roundLastCatSqueaked > 1) {
                    Communicator.sendSqueak(new NearbyCatSqueakInfo(nearestEnemy, false, nearestSeenEnemy.direction));
                    roundLastCatSqueaked = rc.getRoundNum();
                }
            }
        }
        else if (Clock.getBytecodesLeft() > 1000 && nearestSeenEnemy == null && !rc.isMovementReady()) {
            nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            if (nearbyEnemies.length > 0) {
                int nearestEnemyDist = Integer.MAX_VALUE;
                for (RobotInfo enemy : nearbyEnemies) {
                    int dist = currentLocation.distanceSquaredTo(enemy.location);
                    if (dist < nearestEnemyDist) {
                        nearestEnemyDist = dist;
                        nearestSeenEnemy = enemy;
                    }
                }
            }
            if (nearestSeenEnemy != null) {
                Communicator.sendSqueak(new PresenceSqueakInfo(rc.getHealth(), nearestSeenEnemy.location, nearestSeenEnemy.direction, nearestSeenEnemy.health));
               // System.out.println("Squeaking!");
            }
            // else if (nearestEnemy != null && rc.canTurn() && rc.getDirection() != rc.getLocation().directionTo(nearestEnemy) && rc.getLocation().distanceSquaredTo(nearestEnemy) <= 4) {
            //     rc.turn(rc.getLocation().directionTo(nearestEnemy));
            //     System.out.println("extra turn fr");
            // }
        }

        // if (rc.getRawCheese() >= 100 && rc.isActionReady() && Clock.getBytecodesLeft() > 500) {
        //     if (Utilities.attemptTrap(nearbyEnemies, 1)) {
        //         System.out.println("hello!");
        //     }
        // }

        if (Clock.getBytecodesLeft() > 500 && rc.isActionReady() && rc.isCooperation() && nearbyCats.length > 0 && rc.getRawCheese() >= 30) {
            Utilities.attemptCatTrap();
        }

//        if (rc.isActionReady() && (rc.getRawCheese() >= GameConstants.DIG_DIRT_CHEESE_COST || rc.getAllCheese() > GLOBAL_CHEESE_DIG_THRESHOLD) && Utilities.facingTwoDirt()) {
//            Utilities.attemptDig();
//        }
//        else if (rc.isActionReady() && rc.getRawCheese() >= 100 && (inDistressRatKing == null || currentLocation.distanceSquaredTo(inDistressRatKing.loc()) >= ANSWER_DISTRESS_DIST_THRESHOLD)) {
//            Utilities.attemptDig();
//        }


        // if (Clock.getBytecodesLeft() > 500 & rc.isActionReady() && rc.getAllCheese() >= RANDOM_TRAP_CHEESE_THRESHOLD) {
        //     Utilities.attemptTrap(nearbyEnemies, 0);
        // }
    }

    /**
     * Updates all information from last turn, aids the stateUpdater in making a decision.
     * @throws GameActionException
     */
    public static void updateInfo() throws GameActionException {
        turnsSinceEnemy++;
        turnsSinceSeenCat++;

        if (allRobots.size > 0) allRobots.clear();

        locsInRadius = rc.senseNearbyMapInfos();
        health = rc.getHealth();
        currentLocation = rc.getLocation();
        startingLoc = currentLocation;
        nearestCheese = null;
        nearestEmptyCheeseMine = null;
        nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
        nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        nearbyCats = rc.senseNearbyRobots(-1, Team.NEUTRAL);
        closestAllyRatKing = Communicator.getClosestRatKing(currentLocation);
        allyTraps.clear();
        if (ferrying && rc.getRawCheese() == 0) {
            boolean dropped = Utilities.tryDrop();
            if (dropped) ferrying = false;
        }
        if (ferrying && rc.getCarrying() == null) {
            ferrying = false;
        }

        seenCheese = 0;
        nearestAnyCheeseMine = null;

        if (rc.isBeingCarried()) {
            carrier = rc.senseRobotAtLocation(rc.getLocation());
            if (rc.canTurn()) {
                Direction dirToTurn = (carrier.team == rc.getTeam()) ? carrier.direction : carrier.direction.opposite();
                rc.canTurn(dirToTurn);
            }
        }
        else if (rc.isBeingThrown() && carrier != null) {
            if (thrownDirection == null) {
                thrownDirection = rc.getLocation().directionTo(carrier.location).opposite();
                carrier = null;
            }
        }
        else if (!rc.isBeingThrown()) {
            carrier = null;
            thrownDirection = null;
        }

        if (!processedSymmetry && SymmetryManager.getSym() != 0 && RobotPlayer.turnCount > 2) {
            processedSymmetry = true;
            SymmetryManager.seenMines.updateIterable();
            int curSize = SymmetryManager.seenMines.size;
            //capture size before hand so we dont have to worry about looping into new indices
            for (int i = 0; i < curSize; i++) {
                MapLocation mine = SymmetryManager.seenMines.getKey(i);
                MapLocation opposite = SymmetryManager.getSymmetric(mine);
                if (!SymmetryManager.seenMines.contains(opposite)) {
                    SymmetryManager.seenMines.put(opposite, 127);
                }
            }
        }

        
        // if (nearbyCats.length == 0){
        //     //catSqueak = Communicator.getMostRecentSqueakOfType(NearbyCatSqueakInfo.class);
        //     catSqueak = Communicator.getMostRecentValidCatSqueak();
        // } 
        // int[] presSqueaks = getNumberPresenceSqueaks();
        // numberAllies = presSqueaks[0];
        // allyHealth = presSqueaks[1];

        //numberAllies, allyHealth, and catSqueak will all be set by this method


        turnsCarrying = (rc.getCarrying() != null) ? turnsCarrying + 1 : 0;

        //lowestHealthRatKing = Communicator.getLowestHealthRatKing().health();
        inDistressRatKing = Communicator.getInDistressRatKing(currentLocation);

        int numWalls = updateMapInfo();

        // Reachability.updateReachability(currentLocation);
        // Reachability.drawIndicatorDots();

        if (numWalls > 0 && nearbyEnemies.length <= 4) {
            Reachability.updateReachability(currentLocation);
            reachabilityUpdated = true;
        }
        else {
            reachabilityUpdated = false;
        }

        seeEnemyRatKing = false;
        enemyRatKingSeenLoc = null;
        int nearestEnemyDist = Integer.MAX_VALUE;
        int nearestSeenEnemyDist = Integer.MAX_VALUE;
        nearestEnemy = null;
        nearestSeenEnemy = null;
        allEnemyLocations.clear();
        if (reachabilityUpdated) {
            for (RobotInfo enemy : nearbyEnemies) {
                if (Reachability.query(enemy.location) || enemy.getType() == UnitType.RAT_KING) {
                    if (enemy.getType() == UnitType.RAT_KING) {
                        seeEnemyRatKing = true;
                        enemyRatKingSeenLoc = enemy.getLocation();
                    }
                    int dist = currentLocation.distanceSquaredTo(enemy.location);
                    if (dist < nearestEnemyDist) {
                        nearestEnemyDist = dist;
                        nearestEnemy = enemy.location;
                    }
                    if (dist < nearestSeenEnemyDist) {
                        nearestSeenEnemyDist = dist;
                        nearestSeenEnemy = enemy;
                    }
                    allEnemyLocations.add(enemy.location);

                    int info = encodeDirectionAndHealth(enemy.direction, enemy.health, enemy.getType() == UnitType.RAT_KING);
                    allRobots.put(enemy.location, info);
                }
            }
        }
        else {
            for (RobotInfo enemy : nearbyEnemies) {
                if (enemy.getType() == UnitType.RAT_KING) {
                    seeEnemyRatKing = true;
                    enemyRatKingSeenLoc = enemy.getLocation();
                }
                int dist = currentLocation.distanceSquaredTo(enemy.location);
                if (dist < nearestEnemyDist) {
                    nearestEnemyDist = dist;
                    nearestEnemy = enemy.location;
                }
                if (dist < nearestSeenEnemyDist) {
                    nearestSeenEnemyDist = dist;
                    nearestSeenEnemy = enemy;
                }
                allEnemyLocations.add(enemy.location);

                int info = encodeDirectionAndHealth(enemy.direction, enemy.health, enemy.getType() == UnitType.RAT_KING);
                allRobots.put(enemy.location, info);
            }
        }

        allyIDs.clear();


        for (RobotInfo ally : nearbyAllies) {
            allRobots.put(ally.location, encodeDirectionAndHealth(Direction.CENTER, ally.health, false));
            allyHealth += ally.health;
            allyIDs.add(ally.getID());
        }

        processSqueaks();

        if (allEnemyLocations.size > 0) {
            turnsSinceEnemy = 0;
            if (ferrying) {
                boolean dropped = Utilities.tryDrop();
                if (dropped) {
                    System.out.println("combat time, had to let you go homeslice");
                    ferrying = false;
                }
            }
        }

        int closestDist = Integer.MAX_VALUE;
        nearestCatLocation = null;
        nearestCat = null;
        for(RobotInfo cat : nearbyCats) {
            turnsSinceSeenCat = 0;
            int dist = cat.getLocation().distanceSquaredTo(currentLocation);
            if(dist < closestDist) {
                nearestCatLocation = cat.getLocation();
                closestDist = dist;
                nearestCat = cat;
                mostRecentCat = cat;
            }
        }

        if (turnsSinceSeenCat > 2) {
            mostRecentCat = null;
        }

        // if (nearestCatLocation != null) {
        //     if (catVisionSquares == null) catVisionSquares = new FastLocSet();
        //     catVisionSquares = Utilities.catVisionSquares(nearestCat, catVisionSquares);
        // }
        if (nearbyEnemies.length == 0 && nearbyCats.length == 0 && (inDistressRatKing == null || inDistressRatKing.loc().distanceSquaredTo(currentLocation) > ANSWER_DISTRESS_DIST_THRESHOLD) && rc.getRoundNum() % 4 == 0) {
            updateMineInfo();
        }


      //  SymmetryManager.seenMines.updateIterable();
//        for(int i = 0; i < SymmetryManager.seenMines.size; i++) {
//            System.out.println("Mine: " + SymmetryManager.seenMines.getKey(i) + " Threat Level: " + SymmetryManager.seenMines.getValue(i));
//        }

        if (rc.getGlobalCheese() > 1500 && !(Communicator.getNumRatKings() >= RatKing.maxRatKings && rc.getRoundNum() >= 1200)) {
            int x = rc.readSharedArray(20);
            if (x != 0 && Communicator.getNumRatKings() < RatKing.maxRatKings) {
                int y = rc.readSharedArray(21);
                targetMineForFormation = new MapLocation(x, y);
            }
            else {
                targetMineForFormation = null;
            }
        }
        else if (targetMineForFormation != null && Communicator.getNumRatKings() >= RatKing.maxRatKings && rc.getRoundNum() >= 1200) {
            targetMineForFormation = null;
        }
    }



    //sets: numberAllies, allyHealth, catSqueak, and averageAlly
    public static void processSqueaks() {
        int round = rc.getRoundNum();
        Squeak[] squeaks = Communicator.getAllSqueaks(1);
        catSqueak = null;
        int x = 0;
        int y = 0;
        allyHealth = 0;
        int minDistToPickup = Integer.MAX_VALUE;
        int mostRecentCatSqueak = Integer.MIN_VALUE;
        closestPickupRequest = null;
        for (Squeak squeak : squeaks) {
            if (squeak == null) break;
            if (squeak.squeakInfo instanceof PresenceSqueakInfo && round - squeak.round <= 1) {
                PresenceSqueakInfo squeakInfo = (PresenceSqueakInfo) squeak.squeakInfo;
                //MapLocation toCheck = new MapLocation(squeak.senderID, 0);
                if (squeakInfo.health() > 0 && !allyIDs.contains(squeak.senderID)) {
                    allyIDs.gauranteeAdd(squeak.senderID);
                    x += squeak.source.x;
                    y += squeak.source.y;
                    allyHealth += squeakInfo.health();
                    int info = encodeDirectionAndHealth(Direction.CENTER, squeakInfo.health());
                    allRobots.put(squeak.source, info);
                }
                MapLocation enemyLoc = squeakInfo.nearestEnemy();
                if (!allEnemyLocations.contains(enemyLoc) && !rc.canSenseLocation(enemyLoc)) {
                    allEnemyLocations.gauranteeAdd(enemyLoc);
                    int info = encodeDirectionAndHealth(squeakInfo.enemyDir(), squeakInfo.enemyHealth());
                    allRobots.put(enemyLoc, info);
                    if (nearestEnemy == null || rc.getLocation().distanceSquaredTo(nearestEnemy) > rc.getLocation().distanceSquaredTo(enemyLoc)) {
                        nearestEnemy = enemyLoc;
                    }
                }
            }
            else if (squeak.squeakInfo instanceof NearbyCatSqueakInfo) {
                NearbyCatSqueakInfo squeakInfo = (NearbyCatSqueakInfo) squeak.squeakInfo;
                if (squeakInfo.cat() && squeak.round > mostRecentCatSqueak) {
                    catSqueak = squeak;
                }
            }
        }
        if (allyIDs.size > 0) {
            averageAlly = new MapLocation(x / allyIDs.size, y / allyIDs.size);
        }
        else {
            averageAlly = null;
        }
        numberAllies = allyIDs.size;
        allyHealth += rc.getHealth();
    }

    public static void updateMineInfo() throws GameActionException {
        if(map == null) map = new FastIterableLocMap(20);
        map = Communicator.getCheeseMinesMap(map);
        //SymmetryManager.seenMines.updateIterable();
    }

    public static void updateMineThreatLevels() {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = 0;
        int maxY = 0;

        for(RobotInfo enemy : nearbyEnemies) {
            MapLocation location = enemy.location;
            minX = Math.min(minX, location.x);
            minY = Math.min(minY, location.y);
            maxX = Math.max(maxX, location.x);
            maxY = Math.max(maxY, location.y);
        }

        for(RobotInfo cat : nearbyCats) {
            MapLocation location = cat.location;
            minX = Math.min(minX, location.x);
            minY = Math.min(minY, location.y);
            maxX = Math.max(maxX, location.x);
            maxY = Math.max(maxY, location.y);
        }

        SymmetryManager.seenMines.updateIterable();
        for(int i = 0; i < SymmetryManager.seenMines.size; i++) {
            MapLocation mine = SymmetryManager.seenMines.getKey(i);
            if(Utilities.dist2ToRect(mine, minX, maxX, minY, maxY) > MINE_THREAT_RADIUS_SQR) {
                continue;
            }

            int count = 0;
            for(RobotInfo enemy : nearbyEnemies) {
                if(enemy.location.isWithinDistanceSquared(mine, MINE_THREAT_RADIUS_SQR)) {
                    count++;
                }
            }

            for(RobotInfo cat : nearbyCats) {
                if(cat.location.isWithinDistanceSquared(mine, MINE_THREAT_RADIUS_SQR)) {
                    count++;
                }
            }
            int val = SymmetryManager.seenMines.get(mine, 0) + count;
            SymmetryManager.seenMines.put(mine, val);
            //System.out.println(SymmetryManager.seenMines.get(mine, 0));
        }
    }

    public static int encodeDirectionAndHealth(Direction dir, int health) {
        int packedVal = 0;
        packedVal |= (dir.ordinal() & 0b1111);
        packedVal <<= 7;
        packedVal |= (health & 0b111_1111);
        packedVal <<= 1;
        return packedVal;
    } 

    public static int encodeDirectionAndHealth(Direction dir, int health, boolean isRatKing) {
        int packedVal = 0;
        packedVal |= (dir.ordinal() & 0b1111);
        packedVal <<= 7;
        packedVal |= (health & 0b111_1111);
        packedVal <<= 1;
        packedVal |= (isRatKing) ? 1 : 0;
        return packedVal;
    } 

    public static DirectionHealthInfo decodeDirectionAndHealth(int info) {
        boolean isRatKing = ((0b1 & info) == 1);
        info >>= 1;
        int health = 0b111_1111 & info;
        info >>= 7;
        Direction dir = Direction.allDirections()[0b1111 & info];
        DirectionHealthInfo retVal = new DirectionHealthInfo(dir, health, isRatKing);
        return retVal;
    }

    public static int updateMapInfo() throws GameActionException {
        MapInfoAccumulator acc = new MapInfoAccumulator();
        int len = locsInRadius.length;

        switch (len) {
            case 22: {
                MapInfo loc = locsInRadius[21];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 21: {
                MapInfo loc = locsInRadius[20];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 20: {
                MapInfo loc = locsInRadius[19];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 19: {
                MapInfo loc = locsInRadius[18];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 18: {
                MapInfo loc = locsInRadius[17];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 17: {
                MapInfo loc = locsInRadius[16];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 16: {
                MapInfo loc = locsInRadius[15];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 15: {
                MapInfo loc = locsInRadius[14];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 14: {
                MapInfo loc = locsInRadius[13];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 13: {
                MapInfo loc = locsInRadius[12];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 12: {
                MapInfo loc = locsInRadius[11];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 11: {
                MapInfo loc = locsInRadius[10];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
            }
            case 10: {
                MapInfo loc;

                loc = locsInRadius[9];
                MapLocation temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                int tempDist = currentLocation.distanceSquaredTo(temp);
                int tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[8];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[7];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[6];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[5];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[4];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[3];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[2];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[1];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }

                loc = locsInRadius[0];
                temp = loc.getMapLocation();
                if (loc.getTrap() == TrapType.RAT_TRAP) {
                    allyTraps.add(temp);
                }
                Pathfinding.passable[temp.x][temp.y] = 0;
                Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                sawDirt |= loc.isDirt();
                if (loc.isWall()) {
                    acc.numWalls++;
                    MapLocation opposite = SymmetryManager.getSymmetric(temp);
                    if (opposite != null) {
                        Pathfinding.passable[temp.x][temp.y] |= 1;
                    }
                } else {
                    Reachability.map.set(temp.x, temp.y, true);
                }
                tempDist = currentLocation.distanceSquaredTo(temp);
                tempCheese = loc.getCheeseAmount();
                seenCheese += tempCheese;
                if (tempCheese > acc.mostCheese) {
                    nearestCheese = temp;
                    acc.mostCheese = tempCheese;
                    acc.closestDistCheese = tempDist;
                }
                else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                    if (tempDist < acc.closestDistCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                }
                if (loc.hasCheeseMine()) {
                    if (!SymmetryManager.seenMines.contains(temp)) {
                        SymmetryManager.seenMines.put(temp, 0);
                        MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                        if (oppositeMine != null) {
                            SymmetryManager.seenMines.put(oppositeMine, 127);
                        }
                    }
                    if (tempDist < acc.closestDistAnyCheeseMine) {
                        nearestAnyCheeseMine = temp;
                        acc.closestDistAnyCheeseMine = tempDist;
                    }
                }
                break;
            }
            default:
                for (int i = 0; i < len; i++) {
                    MapInfo loc = locsInRadius[i];
                    MapLocation temp = loc.getMapLocation();
                    if (loc.getTrap() == TrapType.RAT_TRAP) {
                        allyTraps.add(temp);
                    }
                    Pathfinding.passable[temp.x][temp.y] = 0;
                    Pathfinding.passable[temp.x][temp.y] |= loc.isPassable() ? 0 : 1;
                    Pathfinding.passable[temp.x][temp.y] |= loc.isDirt() ? 2 : 0;
                    sawDirt |= loc.isDirt();
                    if (loc.isWall()) {
                        acc.numWalls++;
                        MapLocation opposite = SymmetryManager.getSymmetric(temp);
                        if (opposite != null) {
                            Pathfinding.passable[temp.x][temp.y] |= 1;
                        }
                    } else {
                        Reachability.map.set(temp.x, temp.y, true);
                    }
                    int tempDist = currentLocation.distanceSquaredTo(temp);
                    int tempCheese = loc.getCheeseAmount();
                    seenCheese += tempCheese;
                    if (tempCheese > acc.mostCheese) {
                        nearestCheese = temp;
                        acc.mostCheese = tempCheese;
                        acc.closestDistCheese = tempDist;
                    }
                    else if (tempCheese == acc.mostCheese && tempCheese > 0) {
                        if (tempDist < acc.closestDistCheese) {
                            nearestCheese = temp;
                            acc.mostCheese = tempCheese;
                            acc.closestDistCheese = tempDist;
                        }
                    }
                    if (loc.hasCheeseMine()) {
                        if (!SymmetryManager.seenMines.contains(temp)) {
                            SymmetryManager.seenMines.put(temp, 0);
                            MapLocation oppositeMine = SymmetryManager.getSymmetric(temp);
                            if (oppositeMine != null) {
                                SymmetryManager.seenMines.put(oppositeMine, 127);
                            }
                        }
                        if (tempDist < acc.closestDistAnyCheeseMine) {
                            nearestAnyCheeseMine = temp;
                            acc.closestDistAnyCheeseMine = tempDist;
                        }
                    }
                }
        }
        return acc.numWalls;
    }

    private static class MapInfoAccumulator {
        int closestDistCheese = Integer.MAX_VALUE;
        int closestDistAnyCheeseMine = Integer.MAX_VALUE;
        int mostCheese = 0;
        int numWalls = 0;
    }

}
