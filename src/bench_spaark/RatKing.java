package bench_spaark;

import battlecode.common.*;

public class RatKing {
    public static final boolean ENABLE_INDICATORS = true;

    public static final int EXPLORE = 0;
    public static final int COLLECT_CHEESE = 1;
    public static final int FLEE = 2;
    public static int mode = EXPLORE;
    public static int lastMode = EXPLORE;

    public static final int FLEE_OPPONENT_DISTANCE = 49;
    public static final int FLEE_CAT_DISTANCE = 25;

    public static MapLocation collectCheeseTarget = null;
    public static MapLocation collectCheeseCheeseMine = null;
    public static boolean atCheeseMine = false;

    public static Micro defaultMicro = RatKingMicro.micro;

    static int placedCatTraps = 0;
    static MapLocation originalLocation;
    static double cheeseThreshold = 0;

    static int[][] possibleSpawnLocations = new int[][]{
        {-2, -2},
        {-1, -2},
        {0, -2},
        {1, -2},
        {2, -2},
        {-2, -1},
        {2, -1},
        {-2, 0},
        {2, 0},
        {-2, 1},
        {2, 1},
        {-2, 2},
        {-1, 2},
        {0, 2},
        {1, 2},
        {2, 2},
    };

    public static void init() throws Exception {
        Sustain.init();
        originalLocation = G.me;
        if (G.round != 1) {
            while (G.rc.readSharedArray(Comms.RAT_KING_CURR_ROUND + Comms.ratKingID) >= ((G.round - 3) / 2)) {
                Comms.ratKingID += 1;
            }
        }
        if (G.round == 1) {
            G.rc.writeSharedArray(Comms.SYMMETRY, Comms.intifySymmetry(Comms.symmetry));
            // set mines to 1023
            for (int i = Comms.CHEESE_MINE_LOC; i < Comms.CHEESE_MINE_LOC + 5; i++) {
                G.rc.writeSharedArray(i, 1023);
            }
            for (int i = Comms.CHEESE_MINE_CLOSEST_LOC; i < Comms.CHEESE_MINE_CLOSEST_LOC + 5; i++) {
                G.rc.writeSharedArray(i, 1023);
            }
            for (int i = Comms.FORM_OR_DEFEND_RAT_KING_LOC; i < Comms.FORM_OR_DEFEND_RAT_KING_LOC + 10; i += 2) {
                G.rc.writeSharedArray(i, 1023);
            }
            for (int i = Comms.CAT_LOC; i < Comms.CAT_LOC + 15; i += 3) {
                G.rc.writeSharedArray(i + 2, 1023);
            }
            for (int i = Comms.CAT_CLOSEST_LOC; i < Comms.CAT_CLOSEST_LOC + 15; i += 3) {
                G.rc.writeSharedArray(i + 2, 1023);
            }
        }
    }
    public static void run() throws Exception {
        // if (G.rc.getGlobalCheese() > 1500.0 * ((double) (2000 - G.round)) / 2000.0) {
        //     spawnCost = 2000;
        // }
        // if (G.rc.getGlobalCheese() > 1000) {
        //     spawnCost = 2000;
        // }
        Sustain.update(G.rc.getCurrentRatCost());
        int spawnCost = (int) Sustain.average;
        cheeseThreshold = 200 * Comms.numberOfRatKings + (10 + G.rc.getCurrentRatCost()) * (G.rc.getCurrentRatCost() / 10) * 2 
        - Math.max(0, Sustain.average - G.rc.getCurrentRatCost()) * G.rc.getCurrentRatCost() / 4
        + Math.max(0, 1500 - G.round);
        if (G.rc.getGlobalCheese() > cheeseThreshold) {
            // spawnCost = Math.min(spawnCost, G.rc.getGlobalCheese() / 50 + 10);
            if (G.round > 100 || spawnCost < 30)
                spawnCost += 10;
        } else {
            // spawnCost -= 10;
            // if (G.rc.getGlobalCheese() < 200) {
            //     spawnCost = 10;
            // }
            // 80: 54.7
            // 100: 57
            // 100 + 15: 57.6
            // 100 + 25: 58.7
            // 125 + 25: 49.4
            // 80 + 25: 57.6
            // against /100 + 25:
            // 80 + 15: 1 - 44.8
            // 120 + 15: 1 - 47.7
            // spawnCost = G.rc.getGlobalCheese() / 120 + 15;
            spawnCost = G.rc.getGlobalCheese() / 100 + 25;
        }
        if (G.round < 200) {
            spawnCost = Math.min(spawnCost, 30); // 57%
        }
        MapLocation spawnTarget = G.mapCenter;
        if (G.lastSeenOpponentLocation != null && G.lastSeenOpponentLocation.distanceSquaredTo(G.me) <= 30) {
            spawnTarget = G.lastSeenOpponentLocation;
            spawnCost += 10;
        }
        else if (G.lastSeenCatLocation != null && G.lastSeenCatLocation.distanceSquaredTo(G.me) <= 30) {
            spawnTarget = G.lastSeenCatLocation;
            spawnCost += 10;
        }
        MapLocation best = null;
        int bestWeight = 0;
        for (int i = 0; i < possibleSpawnLocations.length; i++) {
            int weight = 0;
            MapLocation loc = G.me.translate(possibleSpawnLocations[i][0], possibleSpawnLocations[i][1]);
            weight -= loc.distanceSquaredTo(spawnTarget);
            if (G.rc.canBuildRat(loc)) {
                weight += 1000;
            }
            else if (G.rc.canRemoveDirt(loc)) {
            }
            else {
                weight -= 1000000;
            }
            if (best == null || bestWeight < weight) {
                best = loc;
                bestWeight = weight;
            }
        }
        if (G.rc.getCurrentRatCost() <= spawnCost && G.rc.canBuildRat(best)) {
            Motion.turn(best.directionTo(spawnTarget));
            G.rc.buildRat(best);
        }
        else if (G.rc.canRemoveDirt(best)) {
            G.rc.removeDirt(best);
        }

        switch (mode) {
            case EXPLORE -> exploreCheckMode();
            case COLLECT_CHEESE -> collectCheeseCheckMode();
            case FLEE -> fleeCheckMode();
        }
        
        attemptAttack();
        attemptCollectCheese();

        switch (mode) {
            case EXPLORE -> explore();
            case COLLECT_CHEESE -> collectCheese();
            case FLEE -> flee();
        }

        attemptCollectCheese();
    }
    public static void exploreCheckMode() throws Exception {
        G.indicatorString.append("CHK_E ");
        StringBuilder visibleCheeseMines = new StringBuilder();
        collectCheeseCheeseMine = null;
        search: for (MapInfo i : G.nearbyMapInfos) {
            if (i.hasCheeseMine()) {
                MapLocation loc = i.getMapLocation();
                visibleCheeseMines.append(loc.x / 5 + "-" + loc.y / 5 + ":");
                for (int j = 0; j < 5; j++) {
                    if (j == Comms.ratKingID) continue;
                    if (Comms.ratKingLocations[j] != null && Comms.ratKingLocations[j].distanceSquaredTo(loc) <= 15) {
                        continue search;
                    }
                }
                if (collectCheeseCheeseMine == null || G.me.distanceSquaredTo(collectCheeseCheeseMine) > G.me.distanceSquaredTo(loc)) {
                    mode = COLLECT_CHEESE;
                    collectCheeseCheeseMine = loc;
                    // return;
                }
            }
        }
        search: for (int i = Comms.numberOfMines; --i >= 0;) {
            if (visibleCheeseMines.indexOf(Comms.mineLocs[i].x / 5 + "-" + Comms.mineLocs[i].y / 5 + ":") != -1) {
                continue;
            }
            for (int j = 0; j < 5; j++) {
                if (j == Comms.ratKingID) continue;
                if (Comms.ratKingLocations[j] != null && Comms.ratKingLocations[j].distanceSquaredTo(Comms.mineLocs[i]) <= 15) {
                    continue search;
                }
            }
            if (collectCheeseCheeseMine == null || G.me.distanceSquaredTo(collectCheeseCheeseMine) > G.me.distanceSquaredTo(Comms.mineLocs[i])) {
                mode = COLLECT_CHEESE;
                collectCheeseCheeseMine = Comms.mineLocs[i];
                // return;
            }
        }
        if (mode == COLLECT_CHEESE) {
            return;
        }
        if (G.opponentRobots.length > 0) {
            mode = FLEE;
            return;
        }
        if (G.lastSeenCatLocation != null && G.lastSeenCatLocation.distanceSquaredTo(G.me) <= FLEE_CAT_DISTANCE && G.lastSeenCatRound >= G.round - 2) {
            mode = FLEE;
            return;
        }
    }
    public static void collectCheeseCheckMode() throws Exception {
        G.indicatorString.append("CHK_C ");
        if (G.opponentRobots.length > 0) {
            mode = FLEE;
            return;
        }
        if (G.lastSeenCatLocation != null && G.lastSeenCatLocation.distanceSquaredTo(G.me) <= FLEE_CAT_DISTANCE && G.lastSeenCatRound >= G.round - 2) {
            mode = FLEE;
            return;
        }
        MapLocation offsetCheeseMine = collectCheeseCheeseMine.translate(2, 2);
        for (int j = 0; j < 5; j++) {
            if (Comms.ratKingLocations[j] != null && Comms.ratKingLocations[j].distanceSquaredTo(offsetCheeseMine) <= 8) {
                mode = EXPLORE;
                return;
            }
        }
    }
    public static void fleeCheckMode() throws Exception {
        G.indicatorString.append("CHK_F ");
        if ((G.lastSeenOpponentLocation == null || G.lastSeenOpponentLocation.distanceSquaredTo(G.me) > FLEE_OPPONENT_DISTANCE) && (G.lastSeenCatLocation == null || G.lastSeenCatLocation.distanceSquaredTo(G.me) > FLEE_CAT_DISTANCE)) {
            mode = EXPLORE;
            return;
        }
    }
    
    public static void explore() throws Exception {
        G.indicatorString.append("EXPLORE ");
        updateCheeseTarget();
        if (collectCheeseTarget == null) {
            Motion.exploreRandomly(defaultMicro);
        }
        else {
            moveToCheese(collectCheeseTarget);
        }
    }
    public static void collectCheese() throws Exception {
        G.indicatorString.append("COLLECT ");
        updateCheeseTarget();
        if (collectCheeseTarget == null) {
            if (G.me.distanceSquaredTo(collectCheeseCheeseMine) > 2) {
                Motion.bugnavTowards(collectCheeseCheeseMine, defaultMicro);
            }
            // if (ENABLE_INDICATORS) {
            //     G.rc.setIndicatorLine(G.me, collectCheeseCheeseMine, 0, 125, 255);
            // }
        }
        else {
            moveToCheese(collectCheeseTarget);
        }
    }
    public static void flee() throws Exception {
        G.indicatorString.append("FLEE ");
        if (G.lastSeenOpponentLocation != null) {
            if (G.lastSeenOpponentLocation.distanceSquaredTo(G.me) <= FLEE_OPPONENT_DISTANCE && G.opponentRobots.length > 1) {
                MapLocation loc = G.rc.adjacentLocation(G.me.directionTo(G.lastSeenOpponentLocation)).add(G.rc.adjacentLocation(G.me.directionTo(G.lastSeenOpponentLocation)).directionTo(G.lastSeenOpponentLocation));
                if (G.rc.canPlaceRatTrap(loc)) {
                    G.rc.placeRatTrap(loc);
                }
                if (G.rc.canPlaceDirt(loc)) {
                    G.rc.placeDirt(loc);
                }
            }
            Motion.bugnavAway(G.lastSeenOpponentLocation, defaultMicro);
            if (G.lastSeenOpponentLocation.distanceSquaredTo(G.me) <= FLEE_OPPONENT_DISTANCE && G.opponentRobots.length > 1) {
                MapLocation loc = G.rc.adjacentLocation(G.me.directionTo(G.lastSeenOpponentLocation)).add(G.rc.adjacentLocation(G.me.directionTo(G.lastSeenOpponentLocation)).directionTo(G.lastSeenOpponentLocation));
                if (G.rc.canPlaceRatTrap(loc)) {
                    G.rc.placeRatTrap(loc);
                }
                if (G.rc.canPlaceDirt(loc)) {
                    G.rc.placeDirt(loc);
                }
            }
        }
        else if (G.lastSeenCatLocation != null) {
            if (G.lastSeenCatLocation.distanceSquaredTo(G.me) <= FLEE_CAT_DISTANCE) {
                MapLocation loc = G.rc.adjacentLocation(G.me.directionTo(G.lastSeenCatLocation)).add(G.rc.adjacentLocation(G.me.directionTo(G.lastSeenCatLocation)).directionTo(G.lastSeenCatLocation));
                if (G.rc.canPlaceCatTrap(loc)) {
                    G.rc.placeCatTrap(loc);
                    placedCatTraps += 1;
                }
                if (G.rc.canPlaceDirt(loc)) {
                    G.rc.placeDirt(loc);
                }
            }
            Motion.bugnavAway(G.lastSeenCatLocation, defaultMicro);
            if (G.lastSeenCatLocation.distanceSquaredTo(G.me) <= FLEE_CAT_DISTANCE) {
                MapLocation loc = G.rc.adjacentLocation(G.me.directionTo(G.lastSeenCatLocation)).add(G.rc.adjacentLocation(G.me.directionTo(G.lastSeenCatLocation)).directionTo(G.lastSeenCatLocation));
                if (G.rc.canPlaceCatTrap(loc)) {
                    G.rc.placeCatTrap(loc);
                    placedCatTraps += 1;
                }
                if (G.rc.canPlaceDirt(loc)) {
                    G.rc.placeDirt(loc);
                }
            }
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
        Motion.bugnavTowards(cheeseTarget, defaultMicro);
        // if (ENABLE_INDICATORS) {
        //     G.rc.setIndicatorLine(G.me, cheeseTarget, 0, 255, 255);
        // }
    }
    public static void attemptCollectCheese() throws Exception {
        atCheeseMine = false;
        for (MapInfo i : G.nearbyMapInfos) {
            if (i.getCheeseAmount() > 0) {
                if (G.rc.canPickUpCheese(i.getMapLocation())) {
                    G.rc.pickUpCheese(i.getMapLocation());
                }
            }
            if (i.hasCheeseMine()) {
                atCheeseMine = true;
            }
        }
    }
    public static void attemptAttack() throws Exception {
        if (!G.rc.isActionReady()) {
            return;
        }
        RobotInfo best = null;
        for (int i = G.opponentRobots.length; --i >= 0;) {
            if (G.rc.canAttack(G.opponentRobots.infos[i].getLocation())) {
                if (best == null || G.opponentRobots.infos[i].health < best.health) {
                    best = G.opponentRobots.infos[i];
                }
            }
        }
        if (best != null) {
            G.rc.attack(best.getLocation());
        }
    }
}
