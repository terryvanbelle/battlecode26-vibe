package bench_spaark;

import battlecode.common.*;

public class BabyRat {
    public static final boolean ENABLE_INDICATORS = true;

    public static final int EXPLORE = 0;
    public static final int ACTUALLY_EXPLORE = 3;
    public static final int COLLECT_CHEESE = 1;
    public static final int ATTACK = 2;
    public static final int FLEE = 4;
    public static final int RETREAT = 5;
    public static final int FORM_RAT_KING = 6;
    public static final int DEFEND_RAT_KING = 7;
    public static final int CHEESE_MESSAGE = 8;
    // public static final int SQUEAK_RUSH = 5;
    public static int mode = EXPLORE;
    public static int lastMode = EXPLORE;

    // controls rounds between visiting cheese mines
    public static final double CHEESE_MINE_VISIT_INTERVAL = 20;
    public static final double CHEESE_MINE_VISIT_INTERVAL_MAP_SCALE = 5;
    public static final double CHEESE_MINE_VISIT_INTERVAL_MINE_SCALE = 10;

    public static int lastExploreRound = -1;
    public static int timesExplored = 0;
    // public static boolean startedExploring = false;

    public static int exploreStage = 0;

    public static Micro defaultMicro = BabyRatMicro.micro;


    public static MapLocation collectCheeseTarget = null;
    public static MapLocation collectCheeseCheeseMine = null;
    public static int timeCollecting = 0;
    
    public static MapLocation attackLocation = null;
    public static boolean attackLocationRatKing = false;
    
    public static MapLocation retreatCarryTarget = null;

    public static MapLocation formRatKingLocation = null;

    public static MapLocation defendRatKingLocation = null;
    public static boolean defendRatKingFromCat = false;

    public static MapLocation closestRatKing = null;
    
    public static void init() throws Exception {
        // if (G.round < 30 || G.rc.getCurrentRatCost() >= 60) {
        //     mode = ATTACK;
        // }
        // if (G.rc.getCurrentRatCost() >= 60) {
        //     mode = ATTACK;
        // }
        // if (G.round == 2) {
        //     mode = SQUEAK_RUSH;
        // }
    }
    public static void run() throws Exception {
        // [!] CHEESE MESSAGING DISABLED [!]
        // if (mode != CHEESE_MESSAGE && G.rc.getRawCheese() >= Comms.cheeseMessageCheese && Comms.enterCheeseMessageCheckMode()) {
        //     mode = CHEESE_MESSAGE;
        // }
        if (mode != EXPLORE && mode != ACTUALLY_EXPLORE && mode != COLLECT_CHEESE) {
            collectCheeseTarget = null;
        }
        if (mode == EXPLORE || mode == ACTUALLY_EXPLORE || mode == COLLECT_CHEESE || mode == ATTACK) {
            MapLocation closest = null;
            int index = 0;
            for (int i = 5; --i >= 0;) {
                if (Comms.defendRatKingLocations[i] != null && (closest == null || G.me.distanceSquaredTo(closest) > G.me.distanceSquaredTo(Comms.defendRatKingLocations[i]))) {
                    closest = Comms.defendRatKingLocations[i];
                    index = i;
                }
            }
            if (closest != null && G.me.distanceSquaredTo(closest) <= Math.pow(10 + Comms.defendNumberOfOpponentRobots[index], 2)) {
                lastMode = mode;
                mode = DEFEND_RAT_KING;
                defendRatKingLocation = closest;
                defendRatKingFromCat = Comms.defendRatKingFromCat[index];
            }
        }
        if (mode == EXPLORE || mode == ACTUALLY_EXPLORE || mode == COLLECT_CHEESE) {
            MapLocation closest = null;
            for (int i = 5; --i >= 0;) {
                if (Comms.formRatKingLocations[i] != null && (closest == null || G.me.distanceSquaredTo(closest) > G.me.distanceSquaredTo(Comms.formRatKingLocations[i]))) {
                    closest = Comms.formRatKingLocations[i];
                }
            }
            if (closest != null && G.me.distanceSquaredTo(closest) <= 400 && Comms.minNumberOfBabyRats >= 16) {
                lastMode = mode;
                mode = FORM_RAT_KING;
                formRatKingLocation = closest;
            }
        }
        BabyRatMicro.ran = false;
        closestRatKing = null;
        for (int j = 5; --j >= 0;) {
            if (Comms.existsRatKing[j] && (closestRatKing == null || G.me.distanceSquaredTo(Comms.ratKingLocations[j]) < G.me.distanceSquaredTo(closestRatKing))) {
                closestRatKing = Comms.ratKingLocations[j];
            }
        }
        switch (mode) {
            case EXPLORE -> exploreCheckMode();
            case ACTUALLY_EXPLORE -> actuallyExploreCheckMode();
            case COLLECT_CHEESE -> collectCheeseCheckMode();
            case ATTACK -> attackCheckMode();
            case FLEE -> fleeCheckMode();
            case RETREAT -> retreatCheckMode();
            case FORM_RAT_KING -> formRatKingCheckMode();
            case DEFEND_RAT_KING -> defendRatKingCheckMode();
            case CHEESE_MESSAGE -> cheeseMessageCheckMode();
        }
        if (mode != CHEESE_MESSAGE) {
            attemptCollectCheese(150);
        }
        // if (mode != EXPLORE) {
        //     if (startedExploring) {
        //         startedExploring = false;
        //         if (Random.rand() % 2 == 0) {
        //             lastExploreRound = G.round;
        //         }
        //     }
        // }
        // BabyRatMicro.canAct = mode != CHEESE_MESSAGE;
        BabyRatMicro.canAct = true;
        switch (mode) {
            case EXPLORE -> explore();
            case ACTUALLY_EXPLORE -> actuallyExplore();
            case COLLECT_CHEESE -> collectCheese();
            case ATTACK -> attack();
            case FLEE -> flee();
            case RETREAT -> retreat();
            case FORM_RAT_KING -> formRatKing();
            case DEFEND_RAT_KING -> defendRatKing();
            case CHEESE_MESSAGE -> cheeseMessage();
            // case SQUEAK_RUSH -> squeakRush();
        }
        if (mode != CHEESE_MESSAGE && mode != RETREAT) {
            attemptCollectCheese(150);
        }
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].getType() == UnitType.RAT_KING) {
                if (G.rc.canTransferCheese(G.allyRobots[i].getLocation(), G.rc.getRawCheese())) {
                    G.rc.transferCheese(G.allyRobots[i].getLocation(), G.rc.getRawCheese());
                    break;
                }
            }
        }
        if (!BabyRatMicro.ran && G.rc.isActionReady() && BabyRatMicro.canAct) {
            // G.indicatorString.append("BACKUP MICRO ");
            // Motion.microMove(defaultMicro.micro(G.dir, G.me));
            if (G.rc.isTurningReady()) {
                MicroActionScore stationaryAttackScore = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
                stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir.rotateLeft()), G.me));
                stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir), G.me));
                stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir.rotateRight()), G.me));
                if (stationaryAttackScore.score > 0) {
                    boolean didAction = Motion.doMicroAction(stationaryAttackScore);
                    if (stationaryAttackScore.type == MicroActionScore.RATNAP && G.rc.getCarrying() != null && !didAction) {
                        if (G.rc.adjacentLocation(stationaryAttackScore.dir).equals(stationaryAttackScore.target)) {
                            Motion.turn(stationaryAttackScore.dir);
                            Motion.doMicroAction(stationaryAttackScore);
                        }
                    }
                }
                if (G.rc.isTurningReady()) {
                    if (G.lastSeenOpponentLocation != null && G.me.distanceSquaredTo(G.lastSeenOpponentLocation) <= 8) {
                        Motion.turn(G.me.directionTo(G.lastSeenOpponentLocation));
                    }
                }
                if (G.rc.isTurningReady()) {
                    Direction d;
                    if (G.round % 4 <= 1) {
                        d = G.dir.rotateRight();
                    }
                    else {
                        d = G.dir.rotateLeft();
                    }
                    // if (G.round % 8 <= 1) {
                    //     d = G.dir.rotateLeft();
                    // }
                    // else if (G.round % 8 <= 3) {
                    //     d = G.dir.rotateRight().rotateRight();
                    // }
                    // else if (G.round % 8 <= 5) {
                    //     d = G.dir.rotateLeft().rotateLeft();
                    // }
                    // else {
                    //     d = G.dir.rotateRight();
                    // }
                    Motion.turn(d);
                }
            }
            MicroActionScore stationaryAttackScore = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
            stationaryAttackScore.compare(BabyRatMicro.tileScore(G.rc.adjacentLocation(G.dir.rotateLeft())));
            stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir), G.me));
            stationaryAttackScore.compare(BabyRatMicro.tileScore(G.rc.adjacentLocation(G.dir.rotateRight())));
            if (stationaryAttackScore.score > 0) {
                Motion.doMicroAction(stationaryAttackScore);
            }
        }
    }
    
    public static void exploreCheckMode() throws Exception {
        // G.indicatorString.append("CHK_E ");
        if (G.opponentRobots.length > 0) {
            // if (Comms.numberOfAlliesNearby + 1 >= G.opponentRobots.length) {
            // if (Comms.numberOfAlliesNearby > 0) {
                lastMode = mode;
                mode = ATTACK;
                return;
            // }
            // mode = FLEE;
            // return;
        }
        double totalWeight = 0;
        int roundVisited = 0;
        int visitInterval = (int) (CHEESE_MINE_VISIT_INTERVAL + Math.sqrt((double) G.mapArea) * CHEESE_MINE_VISIT_INTERVAL_MAP_SCALE + Comms.numberOfMines * CHEESE_MINE_VISIT_INTERVAL_MINE_SCALE);
        // visitInterval = 0;
        // if (G.rc.getCurrentRatCost() <= 20) {
        //     visitInterval = -1000;
        // }
        StringBuilder visibleCheeseMines = new StringBuilder();
        for (MapInfo i : G.nearbyMapInfos) {
            if (i.hasCheeseMine()) {
                MapLocation loc = i.getMapLocation();
                visibleCheeseMines.append(loc.x / 5 + "-" + loc.y / 5 + ":");
                // RobotInfo robot = G.rc.senseRobotAtLocation(loc);
                // if (robot != null && robot.team == G.team && robot.type == UnitType.BABY_RAT) {
                //     continue;
                // }
                int lastVisited = G.getLastVisited(loc);
                if (lastVisited + visitInterval < G.round) {
                    // if (roundVisited == -10 || lastVisited < roundVisited) {
                    //     roundVisited = lastVisited;
                    //     mode = COLLECT_CHEESE;
                    //     collectCheeseCheeseMine = loc;
                    //     distance = G.me.distanceSquaredTo(loc);
                    // }
                    // else if (lastVisited == roundVisited && distance > G.me.distanceSquaredTo(loc)) {
                    //     roundVisited = lastVisited;
                    //     mode = COLLECT_CHEESE;
                    //     collectCheeseCheeseMine = loc;
                    //     distance = G.me.distanceSquaredTo(loc);
                    // }
                    totalWeight += 1 / (Math.sqrt(G.me.distanceSquaredTo(loc)) + 1);
                }
            }
        }
        for (int i = Comms.numberOfMines; --i >= 0;) {
            MapLocation loc = Comms.mineLocs[i];
            if (visibleCheeseMines.indexOf(loc.x / 5 + "-" + loc.y / 5 + ":") != -1) {
                continue;
            }
            int lastVisited = G.getLastVisited(loc);
            if (lastVisited + visitInterval < G.round) {
                totalWeight += 1 / (Math.sqrt(G.me.distanceSquaredTo(loc)) + 1);
            }
        }
        double r = ((double) Random.rand()) / 2147483647.0 * totalWeight;
        for (MapInfo i : G.nearbyMapInfos) {
            if (i.hasCheeseMine()) {
                MapLocation loc = i.getMapLocation();
                int lastVisited = G.getLastVisited(loc);
                if (lastVisited + visitInterval < G.round) {
                    r -= 1 / (Math.sqrt(G.me.distanceSquaredTo(loc)) + 1);
                    if (r < 0) {
                        roundVisited = lastVisited;
                        mode = COLLECT_CHEESE;
                        collectCheeseCheeseMine = loc;
                    }
                }
            }
        }
        for (int i = Comms.numberOfMines; --i >= 0;) {
            MapLocation loc = Comms.mineLocs[i];
            if (visibleCheeseMines.indexOf(loc.x / 5 + "-" + loc.y / 5 + ":") != -1) {
                continue;
            }
            int lastVisited = G.getLastVisited(loc);
            if (lastVisited + visitInterval < G.round) {
                r -= 1 / (Math.sqrt(G.me.distanceSquaredTo(loc)) + 1);
                if (r < 0) {
                    roundVisited = lastVisited;
                    mode = COLLECT_CHEESE;
                    collectCheeseCheeseMine = loc;
                    break;
                }
            }
        }
        if (mode == COLLECT_CHEESE) {
            // if (Random.rand() % (timesExplored + 1) >= 1) {
            if (Random.rand() % 2 == 0) {
                if (lastExploreRound >= roundVisited) {
                // if (exploreStage >= 2) {
                    timeCollecting = Motion.getChebyshevDistance(G.me, collectCheeseCheeseMine) + 20;
                    // exploreStage = (exploreStage + 1) % 4;
                    return;
                }
            }
            // exploreStage = (exploreStage + 1) % 4;
            // mode = EXPLORE;
            // if (Random.rand() % 2 == 0) {
                // lastExploreRound = G.round;
            // }
        }
        if (G.rc.getRawCheese() > 30) {
            mode = RETREAT;
            return;
        }
        mode = ACTUALLY_EXPLORE;
        // timesExplored += 1;
            // if (Random.rand() % 2 == 0) {
                lastExploreRound = G.round;
            // }
    }
    public static void actuallyExploreCheckMode() throws Exception {
        // G.indicatorString.append("CHK_AE ");
        if (G.opponentRobots.length > 0) {
            // if (Comms.numberOfAlliesNearby + 1 >= G.opponentRobots.length) {
            // if (Comms.numberOfAlliesNearby > 0) {
                lastMode = mode;
                mode = ATTACK;
                return;
            // }
            // mode = FLEE;
            // return;
        }
        if (G.rc.getRawCheese() > 30) {
            mode = RETREAT;
            return;
        }
    }
    public static void collectCheeseCheckMode() throws Exception {
        // G.indicatorString.append("CHK_C ");
        if (G.opponentRobots.length > 0) {
            // if (Comms.numberOfAlliesNearby + 1 >= G.opponentRobots.length) {
            // if (Comms.numberOfAlliesNearby > 0) {
                lastMode = mode;
                mode = ATTACK;
                return;
            // }
            // mode = FLEE;
            // return;
        }
        if (--timeCollecting < 0) {
            G.setLastVisited(collectCheeseCheeseMine, G.round);
            mode = EXPLORE;
            return;
        }
        // if (G.rc.canSenseLocation(collectCheeseCheeseMine)) {
        //     RobotInfo robot = G.rc.senseRobotAtLocation(collectCheeseCheeseMine);
        //     if (robot != null && robot.team == G.team && robot.type == UnitType.BABY_RAT) {
        //         mode = EXPLORE;
        //         // G.setLastVisited(collectCheeseCheeseMine, G.round);
        //         return;
        //     }
        // }
    }
    public static void attackCheckMode() throws Exception {
        // G.indicatorString.append("CHK_A ");
        // no changing states, we are just attacking lol
        if (G.opponentRobots.length > 0) {
            if (Comms.numberOfAlliesNearby + 1 < G.opponentRobots.length) {
                // mode = FLEE;
                // return;
            }
        }
    }
    public static void fleeCheckMode() throws Exception {
        // G.indicatorString.append("CHK_F ");
        if (G.lastSeenOpponentLocation == null || G.me.distanceSquaredTo(G.lastSeenOpponentLocation) >= 30 || Comms.numberOfAlliesNearby > 0) {
            mode = EXPLORE;
            return;
        }
    }
    public static void retreatCheckMode() throws Exception {
        // G.indicatorString.append("CHK_R ");
        if (G.rc.getRawCheese() == 0) {
            mode = EXPLORE;
            return;
        }
    }
    public static void formRatKingCheckMode() throws Exception {
        // G.indicatorString.append("CHK_FRK ");
        MapLocation closest = null;
        for (int i = 5; --i >= 0;) {
            if (Comms.formRatKingLocations[i] != null && (closest == null || G.me.distanceSquaredTo(closest) > G.me.distanceSquaredTo(Comms.formRatKingLocations[i]))) {
                closest = Comms.formRatKingLocations[i];
            }
        }
        if (Comms.minNumberOfBabyRats >= 12 && Comms.numberOfRatKings == 1 && closest != null) {
            formRatKingLocation = closest;
        }
        else {
            mode = lastMode;
            if (mode == FORM_RAT_KING) {
                System.out.println("invalid lastmode formratking");
                mode = EXPLORE;
            }
            // exploreCheckMode();
            return;
        }
    }
    public static void defendRatKingCheckMode() throws Exception {
        // G.indicatorString.append("CHK_DRK ");
        MapLocation closest = null;
        boolean cat = false;
        for (int i = 5; --i >= 0;) {
            if (Comms.defendRatKingLocations[i] != null && (closest == null || G.me.distanceSquaredTo(closest) > G.me.distanceSquaredTo(Comms.defendRatKingLocations[i]))) {
                closest = Comms.defendRatKingLocations[i];
                cat = Comms.defendRatKingFromCat[i];
            }
        }
        if (closest != null) {
            defendRatKingLocation = closest;
            defendRatKingFromCat = cat;
        }
        else {
            mode = lastMode;
            if (mode == DEFEND_RAT_KING) {
                System.out.println("invalid lastmode defendratking");
                mode = EXPLORE;
            }
            // exploreCheckMode();
            return;
        }
    }
    public static void cheeseMessageCheckMode() throws Exception {
        // G.indicatorString.append("CHK_CM ");
        if (!Comms.cheeseMessageCheckMode()) {
            if (G.rc.getRawCheese() >= Comms.cheeseMessageCheese && Comms.enterCheeseMessageCheckMode()) {
                return;
            }
            mode = EXPLORE;
            MapLocation ratKingLocation = null;
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (G.allyRobots[i].getType() == UnitType.RAT_KING) {
                    if (ratKingLocation == null || G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
                        ratKingLocation = G.allyRobots[i].getLocation();
                    }
                }
            }
            if (G.rc.canTransferCheese(ratKingLocation, G.rc.getRawCheese())) {
                G.rc.transferCheese(ratKingLocation, G.rc.getRawCheese());
            }
            exploreCheckMode();
            return;
        }
    }

    public static void explore() throws Exception {
        // G.indicatorString.append("EXPLORE ");
        updateCheeseTarget();
        if (collectCheeseTarget == null) {
            Motion.exploreRandomly();
        }
        else {
            moveToCheese(collectCheeseTarget);
        }
    }
    public static void actuallyExplore() throws Exception {
        // G.indicatorString.append("AEXPLORE ");
        updateCheeseTarget();
        if (collectCheeseTarget == null) {
            MapLocation startLoc = Motion.exploreLoc;
            MapLocation newLoc = Motion.exploreRandomlyLoc();
            if (startLoc == null || startLoc.equals(newLoc)) {
                Motion.exploreLoc = newLoc;
                Motion.bugnavTowards(Motion.exploreLoc, defaultMicro);
            }
            else {
                mode = EXPLORE;
            }
        }
        else {
            moveToCheese(collectCheeseTarget);
        }
    }
    public static void collectCheese() throws Exception {
        // G.indicatorString.append("COLLECT ");
        for (MapInfo i : G.nearbyMapInfos) {
            if (i.hasCheeseMine()) {
                MapLocation loc = i.getMapLocation();
                if (loc.x / 5 == collectCheeseCheeseMine.x / 5 && loc.y / 5 == collectCheeseCheeseMine.y / 5) {
                    collectCheeseCheeseMine = loc;
                }
            }
        }
        if (G.me.distanceSquaredTo(collectCheeseCheeseMine) <= 8) {
            G.setLastVisited(collectCheeseCheeseMine, G.round);
        }
        updateCheeseTarget();
        if (G.rc.getRawCheese() > 30 && (collectCheeseTarget == null || G.rc.getRawCheese() > 149)) {
            mode = RETREAT;
            retreat();
            // G.setLastVisited(collectCheeseCheeseMine, G.round);
            return;
        }
        if (collectCheeseTarget == null) {
            if (G.me.distanceSquaredTo(collectCheeseCheeseMine) <= 2) {
                mode = EXPLORE;
                Motion.exploreRandomly();
                return;
            }
            if (G.me.distanceSquaredTo(collectCheeseCheeseMine) == 0) {
                Motion.turn(G.dir.rotateLeft().rotateLeft().rotateLeft());
            }
            else {
                Motion.bugnavTowards(collectCheeseCheeseMine, defaultMicro);
            }
            // if (ENABLE_INDICATORS && collectCheeseCheeseMine != null) {
            //     G.rc.setIndicatorLine(G.me, collectCheeseCheeseMine, 0, 125, 255);
            // }
        }
        else {
            moveToCheese(collectCheeseTarget);
        }
    }
    public static void attack() throws Exception {
        // G.indicatorString.append("ATTACK ");
        if (attackLocation != null && G.rc.canSenseLocation(attackLocation) && (G.rc.senseRobotAtLocation(attackLocation) == null || G.rc.senseRobotAtLocation(attackLocation).getTeam() == G.team)) {
            attackLocation = null;
        }
        MapLocation target = attackLocation;
        int targetWeight = 0;
        boolean targetRatKing = false;
        for (int i = G.opponentRobots.length; --i >= 0;) {
            MapLocation loc = G.opponentRobots.infos[i].getLocation();
            int weight = 0;
            weight -= loc.distanceSquaredTo(G.me);
            //TODO try not prioritizing rat king?
            if (G.opponentRobots.infos[i].getType() == UnitType.RAT_KING) {
                weight += 100;
            }
            if (target == null || weight > targetWeight) {
                target = loc;
                targetWeight = weight;
                targetRatKing = G.opponentRobots.infos[i].getType() == UnitType.RAT_KING;
            }
        }
        // if (mode == ATTACK_LEADER) {
        // }
        // else {
        //     if (G.opponentRobots.length == 0) {
        //         mode = EXPLORE;
        //         Motion.exploreRandomly();
        //         return;
        //     }
        // }
        if (target == null) {
            // target = Comms.getOppositeMapLocation(Comms.ratKingInitLocations[0], Comms.mostLikelySymmetry);
            // targetRatKing = true;
            // attackLocation = target;
            // if (G.rc.canSenseLocation(target) && (G.rc.senseRobotAtLocation(target) == null || G.rc.senseRobotAtLocation(target).team == G.team)) {
            //     // technically should be in attackCheckMode
            //     mode = EXPLORE;
            //     Motion.exploreRandomly();
            //     return;
            // }
            mode = lastMode;
            if (mode == ATTACK) {
                mode = EXPLORE;
            }
            Motion.exploreRandomly();
            return;
        }
        else {
            attackLocation = target;
            attackLocationRatKing = targetRatKing;
        }
        if (attackLocationRatKing) {
            if (!Motion.attemptTurnToRatKing(attackLocation)) {
                Motion.bugnavTowards(attackLocation, defaultMicro);
            }
        }
        else {
            Motion.attemptTurnToRat(attackLocation);
            Motion.bugnavTowards(attackLocation, defaultMicro);
        }
        // if (ENABLE_INDICATORS) {
        //     G.rc.setIndicatorLine(G.me, attackLocation, 255, 0, 0);
        // }
    }
    public static void flee() throws Exception {
        // G.indicatorString.append("FLEE ");
        if (G.lastSeenOpponentLocation == null) {
            mode = EXPLORE;
            Motion.exploreRandomly();
            return;
        }
        Motion.bugnavAway(G.lastSeenOpponentLocation, defaultMicro);
        if (G.lastSeenOpponentLocation != null) {
            // if (ENABLE_INDICATORS) {
            //     G.rc.setIndicatorLine(G.me, G.lastSeenOpponentLocation, 255, 125, 0);
            // }
        }
    }
    public static void retreat() throws Exception {
        // G.indicatorString.append("RETREAT ");
        MapLocation ratKingLocation = null;
        if (retreatCarryTarget != null && G.me.equals(retreatCarryTarget)) {
            retreatCarryTarget = null;
        }
        if (retreatCarryTarget != null && G.rc.canSenseLocation(retreatCarryTarget)) {
            RobotInfo r = G.rc.senseRobotAtLocation(retreatCarryTarget);
            if (r == null || r.team != G.team || r.getType() != UnitType.BABY_RAT) {
                retreatCarryTarget = null;
            }
        }
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].getType() == UnitType.RAT_KING) {
                if (ratKingLocation == null || G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
                    ratKingLocation = G.allyRobots[i].getLocation();
                }
            }
        }
        if (ratKingLocation == null) {
            for (int i = 5; --i >= 0;) {
                if (Comms.ratKingLocations[i] != null && (ratKingLocation == null || G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(Comms.ratKingLocations[i]))) {
                    ratKingLocation = Comms.ratKingLocations[i];
                }
            }
        }
        for (int i = G.allyRobots.length; --i >= 0;) {
            // else if (G.allyRobots[i].getRawCheeseAmount() > G.rc.getRawCheese()) {
            //     if (retreatCarryTarget == null || G.me.distanceSquaredTo(retreatCarryTarget) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
            //         retreatCarryTarget = G.allyRobots[i].getLocation();
            //     }
            // }
            // if (Comms.allyState.indexOf(G.allyRobots[i].getID() + "-" + RETREAT + " ") != -1 && G.allyRobots[i].getLocation().distanceSquaredTo(ratKingLocation) >= G.me.distanceSquaredTo(ratKingLocation)) {
            //     if (retreatCarryTarget == null || G.me.distanceSquaredTo(retreatCarryTarget) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
            //         retreatCarryTarget = G.allyRobots[i].getLocation();
            //     }
            // }
            // if (G.allyRobots[i].getRawCheeseAmount() > 50 && G.allyRobots[i].getLocation().distanceSquaredTo(ratKingLocation) >= G.me.distanceSquaredTo(ratKingLocation)) {
            //     if (retreatCarryTarget == null || G.me.distanceSquaredTo(retreatCarryTarget) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
            //         retreatCarryTarget = G.allyRobots[i].getLocation();
            //     }
            // }
            // if (G.allyRobots[i].getRawCheeseAmount() > 50 && G.allyRobots[i].getLocation().distanceSquaredTo(ratKingLocation) >= G.me.distanceSquaredTo(ratKingLocation)) {
            //     if (carryTarget == null || G.me.distanceSquaredTo(carryTarget) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
            //         carryTarget = G.allyRobots[i].getLocation();
            //     }
            // }
        }
        if (!Motion.attemptTurnToRatKingForCheese(ratKingLocation)) {
            // if (G.rc.getCarrying() != null && G.rc.getCarrying().team == G.team) {
            //     BabyRatMicro.canAct = false;
            //     Motion.bugnavTowards(ratKingLocation, defaultMicro);
            //     BabyRatMicro.canAct = true;
            //     // Direction dir = Motion.bug2Helper(G.me, ratKingLocation, Motion.TOWARDS, 0, 0);
            //     Direction dir = Motion.lastDir;
            //     if (G.rc.canDropRat(dir)) {
            //         G.rc.dropRat(dir);
            //     }
            //     if (G.rc.canDropRat(dir.rotateLeft())) {
            //         G.rc.dropRat(dir.rotateLeft());
            //     }
            //     if (G.rc.canDropRat(dir.rotateRight())) {
            //         G.rc.dropRat(dir.rotateRight());
            //     }
            // }
            // else {
            //     if (retreatCarryTarget != null && G.me.distanceSquaredTo(ratKingLocation) > 18) {
            //         if (G.me.distanceSquaredTo(retreatCarryTarget) <= 8) {
            //             Motion.turn(G.me.directionTo(retreatCarryTarget));
            //         }
            //         if (G.rc.canCarryRat(retreatCarryTarget)) {
            //             G.rc.carryRat(retreatCarryTarget);
            //         }
            //         if (G.rc.getCarrying() != null) {
            //             BabyRatMicro.canAct = false;
            //             Motion.bugnavTowards(ratKingLocation, defaultMicro);
            //             BabyRatMicro.canAct = true;
            //             Direction dir = Motion.lastDir;
            //             if (G.rc.canDropRat(dir)) {
            //                 G.rc.dropRat(dir);
            //             }
            //             if (G.rc.canDropRat(dir.rotateLeft())) {
            //                 G.rc.dropRat(dir.rotateLeft());
            //             }
            //             if (G.rc.canDropRat(dir.rotateRight())) {
            //                 G.rc.dropRat(dir.rotateRight());
            //             }
            //         }
            //         else {
            //             BabyRatMicro.canAct = false;
            //             Motion.bugnavTowards(retreatCarryTarget, defaultMicro);
            //             BabyRatMicro.canAct = true;
            //             if (G.rc.canCarryRat(retreatCarryTarget)) {
            //                 G.rc.carryRat(retreatCarryTarget);
            //             }
            //             Direction dir = Motion.lastDir;
            //             if (G.rc.canDropRat(dir)) {
            //                 G.rc.dropRat(dir);
            //             }
            //             if (G.rc.canDropRat(dir.rotateLeft())) {
            //                 G.rc.dropRat(dir.rotateLeft());
            //             }
            //             if (G.rc.canDropRat(dir.rotateRight())) {
            //                 G.rc.dropRat(dir.rotateRight());
            //             }
            //         }
            //         G.rc.setIndicatorLine(G.me, retreatCarryTarget, 0, 0, 255);
            //     }
            //     else {
                    Motion.bugnavTowards(ratKingLocation, defaultMicro);
            //     }
            // }
        }
        attemptCollectCheese(150);
        if (G.rc.canTransferCheese(ratKingLocation, G.rc.getRawCheese())) {
            G.rc.transferCheese(ratKingLocation, G.rc.getRawCheese());
        }
        G.rc.setIndicatorLine(G.me, ratKingLocation, 255, 0, 255);
    }
    public static void formRatKing() throws Exception {
        // G.indicatorString.append("FORM_RK ");
        if (G.me.distanceSquaredTo(formRatKingLocation) <= 8) {
            if (G.rc.canBecomeRatKing()) {
                G.rc.becomeRatKing();
                G.type = UnitType.RAT_KING;
                RatKing.init();
                RatKing.run();
                return;
            }
        }
        for (MapInfo i : G.nearbyMapInfos) {
            if (i.hasCheeseMine()) {
                MapLocation loc = i.getMapLocation();
                if (loc.x / 5 == formRatKingLocation.x / 5 && loc.y / 5 == formRatKingLocation.y / 5) {
                    formRatKingLocation = loc;
                }
            }
        }
        Motion.bugnavTowards(formRatKingLocation);
        if (G.me.distanceSquaredTo(formRatKingLocation) <= 8) {
            if (G.rc.canBecomeRatKing()) {
                G.rc.becomeRatKing();
                G.type = UnitType.RAT_KING;
                RatKing.init();
                RatKing.run();
                return;
            }
        }
        // G.rc.setIndicatorLine(G.me, formRatKingLocation, 0, 0, 255);
    }
    public static void defendRatKing() throws Exception {
        // G.indicatorString.append("DEFEND_RK ");
        // MapLocation defendCarryTarget = null;
        MapLocation ratKingLocation = null;
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].getType() == UnitType.RAT_KING) {
                if (ratKingLocation == null || G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(G.allyRobots[i].getLocation())) {
                    ratKingLocation = G.allyRobots[i].getLocation();
                }
            }
        }
        if (ratKingLocation == null) {
            for (int i = 5; --i >= 0;) {
                if (Comms.ratKingLocations[i] != null && (ratKingLocation == null || G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(Comms.ratKingLocations[i]))) {
                    ratKingLocation = Comms.ratKingLocations[i];
                }
            }
        }
        // if (ratKingLocation != null && defendRatKingLocation.distanceSquaredTo(ratKingLocation) <= 8 && G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(defendRatKingLocation)) {
        if (defendRatKingFromCat && ratKingLocation != null && defendRatKingLocation.distanceSquaredTo(ratKingLocation) <= 8 && G.me.distanceSquaredTo(defendRatKingLocation) <= 40 && G.me.distanceSquaredTo(ratKingLocation) > G.me.distanceSquaredTo(defendRatKingLocation)) {
            G.rc.squeak((1023 << 10) + (3 << 30));
        }
        // if (defendRatKingFromCat && ratKingLocation != null && defendRatKingLocation.distanceSquaredTo(ratKingLocation) <= 8 && G.me.distanceSquaredTo(defendRatKingLocation) <= 30) {
        //     G.rc.squeak((1023 << 10) + (3 << 30));
        // }
        if (G.rc.getCarrying() != null && G.rc.getCarrying().team == G.team) {
            Motion.bugnavTowards(defendRatKingLocation, defaultMicro);
        }
        else {
            
            Motion.bugnavTowards(defendRatKingLocation, defaultMicro);
        }
        Motion.bugnavTowards(defendRatKingLocation);
        // G.rc.setIndicatorLine(G.me, defendRatKingLocation, 125, 0, 255);
    }
    public static void cheeseMessage() throws Exception {
        // G.indicatorString.append("CMESSAGE ");
        Comms.cheeseMessage();
    }
    public static void squeakRush() throws Exception {
        // G.indicatorString.append("SQUEAK ");
        MapLocation target = null;
        int targetWeight = 0;
        boolean targetRatKing = false;
        for (int i = G.opponentRobots.length; --i >= 0;) {
            MapLocation loc = G.opponentRobots.infos[i].getLocation();
            int weight = 0;
            weight -= loc.distanceSquaredTo(G.me);
            if (G.opponentRobots.infos[i].getType() == UnitType.RAT_KING) {
                weight += 100;
            }
            if (target == null || weight > targetWeight) {
                target = loc;
                targetWeight = weight;
                targetRatKing = G.opponentRobots.infos[i].getType() == UnitType.RAT_KING;
            }
        }
        if (target != null) {
            G.rc.squeak(1);
        }
        if (attackLocation != null && G.rc.canSenseLocation(attackLocation) && (G.rc.senseRobotAtLocation(attackLocation) == null || G.rc.senseRobotAtLocation(attackLocation).getTeam() == G.team)) {
            attackLocation = null;
        }
        if (target == null) {
            if (attackLocation == null) {
                target = Comms.getOppositeMapLocation(Comms.ratKingInitLocations[0], Comms.mostLikelySymmetry);
                if (G.rc.canSenseLocation(target) && (G.rc.senseRobotAtLocation(target) == null || G.rc.senseRobotAtLocation(target).team == G.team)) {
                    Motion.exploreRandomly();
                    return;
                }
            }
            else {
                target = attackLocation;
            }
        }
        else {
            attackLocation = target;
        }
        if (targetRatKing) {
            if (!Motion.attemptTurnToRatKing(target)) {
                Motion.bugnavTowards(target, defaultMicro);
            }
        }
        else {
            Motion.bugnavTowards(target, defaultMicro);
        }
    }

    public static void updateCheeseTarget() throws Exception {
        if (collectCheeseTarget != null && G.rc.canSenseLocation(collectCheeseTarget) && G.rc.senseMapInfo(collectCheeseTarget).getCheeseAmount() == 0) {
            collectCheeseTarget = null;
        }
        for (MapInfo i : G.nearbyMapInfos) {
            if (i.getCheeseAmount() > 0 && (G.lastSeenCatLocation == null || i.getMapLocation().distanceSquaredTo(G.lastSeenCatLocation) > G.CAT_SIGHT_RANGE_SQUARED)) {
                if (collectCheeseTarget == null || collectCheeseTarget.distanceSquaredTo(G.me) > i.getMapLocation().distanceSquaredTo(G.me)) {
                    collectCheeseTarget = i.getMapLocation();
                }
            }
        }
    }
    public static void moveToCheese(MapLocation cheeseTarget) throws Exception {
        if (G.me.distanceSquaredTo(cheeseTarget) == 0) {

        }
        else if (G.me.distanceSquaredTo(cheeseTarget) <= 2) {
            Motion.turn(G.me.directionTo(cheeseTarget));
        }
        else {
            Motion.bugnavTowards(cheeseTarget, defaultMicro);
        }
        // if (ENABLE_INDICATORS) {
        //     G.rc.setIndicatorLine(G.me, cheeseTarget, 0, 255, 255);
        // }
    }
    public static void attemptCollectCheese() throws Exception {
        MapLocation loc = G.me;
        if (G.rc.canPickUpCheese(loc)) {
            G.rc.pickUpCheese(loc);
        }
        loc = G.rc.adjacentLocation(G.dir);
        if (G.rc.canPickUpCheese(loc)) {
            G.rc.pickUpCheese(loc);
        }
        loc = G.rc.adjacentLocation(G.dir.rotateLeft());
        if (G.rc.canPickUpCheese(loc)) {
            G.rc.pickUpCheese(loc);
        }
        loc = G.rc.adjacentLocation(G.dir.rotateRight());
        if (G.rc.canPickUpCheese(loc)) {
            G.rc.pickUpCheese(loc);
        }
    }
    public static void attemptCollectCheese(int maxCheese) throws Exception {
        if (G.rc.isBeingThrown()) return;
        int maxCheeseToCollect = maxCheese - G.rc.getRawCheese();
        if (maxCheeseToCollect <= 0) return;
        MapLocation loc = G.me;
        if (G.rc.canSenseLocation(loc)) {
            int amount = G.rc.senseMapInfo(loc).getCheeseAmount();
            if (amount > 0) {
                G.rc.pickUpCheese(loc, Math.min(amount, maxCheeseToCollect));
            }
        }
        loc = G.rc.adjacentLocation(G.dir);
        if (G.rc.canSenseLocation(loc)) {
            int amount = G.rc.senseMapInfo(loc).getCheeseAmount();
            if (amount > 0) {
                G.rc.pickUpCheese(loc, Math.min(amount, maxCheeseToCollect));
            }
        }
        maxCheeseToCollect = maxCheese - G.rc.getRawCheese();
        if (maxCheeseToCollect <= 0) return;
        loc = G.rc.adjacentLocation(G.dir.rotateLeft());
        if (G.rc.canSenseLocation(loc)) {
            int amount = G.rc.senseMapInfo(loc).getCheeseAmount();
            if (amount > 0) {
                G.rc.pickUpCheese(loc, Math.min(amount, maxCheeseToCollect));
            }
        }
        maxCheeseToCollect = maxCheese - G.rc.getRawCheese();
        if (maxCheeseToCollect <= 0) return;
        loc = G.rc.adjacentLocation(G.dir.rotateRight());
        if (G.rc.canSenseLocation(loc)) {
            int amount = G.rc.senseMapInfo(loc).getCheeseAmount();
            if (amount > 0) {
                G.rc.pickUpCheese(loc, Math.min(amount, maxCheeseToCollect));
            }
        }
    }
}
