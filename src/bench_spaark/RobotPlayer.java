package bench_spaark;

import battlecode.common.*;
import bench_spaark.fast.RobotInfoMap;

public class RobotPlayer {
    public static void updateInfo() throws Exception {
        int a = Clock.getBytecodeNum();
        // every time we move, turn, or on round change
        G.lastMe = G.me;
        G.me = G.rc.getLocation();
        G.dir = G.rc.getDirection();
        G.robots = G.rc.senseNearbyRobots(-1);
        G.allyRobots = G.rc.senseNearbyRobots(-1, G.team);
        for (RobotInfo i : G.rc.senseNearbyRobots(-1, G.opponentTeam)) {
            G.opponentRobots.set(i);
        }
        G.cats = G.rc.senseNearbyRobots(-1, Team.NEUTRAL);
        // G.allyRobotStrings[0] = new StringBuilder();
        // for (int i = G.allyRobots.length; --i >= 0;) {
        //     if (G.allyRobots[i].type.isBabyRatType()) {
        //         G.allyRobotStrings[0].append(G.allyRobots[i].location.toString());
        //     }
        // }
        // G.opponentRobotStrings[0] = new StringBuilder();
        // for (int i = G.opponentRobots.length; --i >= 0;) {
        //     if (G.opponentRobots[i].type.isBabyRatType()) {
        //         G.opponentRobotStrings[0].append(G.opponentRobots[i].location.toString());
        //     }
        // }
        for (RobotInfo i : G.allyRobots) {
            if (i.type.isBabyRatType()) {
                G.allyRobotString.append(i.location.toString());
            }
        }

        G.nearbyMapInfos = G.rc.senseNearbyMapInfos();
        
        G.detectedNonAllyRobots = false;
        if (G.lastSeenCatRound < G.round - 10) {
            G.lastSeenCatLocation = null;
            G.lastSeenCat = null;
        }
        if (G.lastSeenOpponentRound < G.round - 10) {
            G.lastSeenOpponentLocation = null;
            G.lastSeenOpponent = null;
        }
        if (G.lastSeenCatLocation != null && G.rc.canSenseLocation(G.lastSeenCatLocation) &&
            (G.rc.senseRobotAtLocation(G.lastSeenCatLocation) == null || G.rc.senseRobotAtLocation(G.lastSeenCatLocation).team != Team.NEUTRAL) && G.lastSeenCatRound < G.round - 4) {
            G.lastSeenCatLocation = null;
            G.lastSeenCat = null;
        }
        if (G.lastSeenOpponentLocation != null && G.rc.canSenseLocation(G.lastSeenOpponentLocation) &&
            (G.rc.senseRobotAtLocation(G.lastSeenOpponentLocation) == null || G.rc.senseRobotAtLocation(G.lastSeenOpponentLocation).team != G.opponentTeam)) {
            G.lastSeenOpponentLocation = null;
            G.lastSeenOpponent = null;
        }
        for (RobotInfo i : G.robots) {
            if (i.type == UnitType.CAT) {
                if (G.lastSeenCatLocation == null || G.lastSeenCatLocation.bottomLeftDistanceSquaredTo(G.me) > i.location.bottomLeftDistanceSquaredTo(G.me)) {
                    G.lastSeenCatLocation = i.location;
                }
                G.lastSeenCat = i;
                G.lastSeenCatRound = G.round;
            }
            // if (i.type == UnitType.CAT && (G.lastSeenCatLocation == null ||
            //     G.me.distanceSquaredTo(G.lastSeenCatLocation) > G.me.distanceSquaredTo(i.location)
            // )) {
            // }
            if (i.team == G.opponentTeam && (G.lastSeenOpponentLocation == null ||
                G.me.distanceSquaredTo(G.lastSeenOpponentLocation) > G.me.distanceSquaredTo(i.location)
            )) {
                G.lastSeenOpponentLocation = i.location;
                G.lastSeenOpponent = i;
                G.lastSeenOpponentRound = G.round;
            }
            if (i.team != G.team) {
                G.detectedNonAllyRobots = true;
            }
        }
        Comms.updateInfo();

        // G.indicatorString.append("UPD-INFO=" + (Clock.getBytecodeNum() - a) + " ");
    }

    public static void updateMove() throws Exception {
        // every time we move
        updateInfo();
        // Motion.lastVisitedLocations.append(G.me.toString());
        // switch (Motion.lastVisitedLocations.length() % 8) {
        //     case 6:
        //         Motion.lastVisitedLocations.append("  ");
        //         break;
        //     case 7:
        //         Motion.lastVisitedLocations.append(" ");
        //         break;
        // }
    }

    public static void updateRound() throws Exception {
        // every round
        // Motion.movementCooldown -= GameConstants.COOLDOWNS_PER_TURN * (G.rc.getRoundNum() - G.round);
        // Motion.movementCooldown = Math.max(Motion.movementCooldown, 0);
        G.round = G.rc.getRoundNum();
        G.allyRobotString = new StringBuffer();
        G.opponentRobots.clear();
        // for (int i = 0; i < G.ROBOT_HISTORY_TURNS - 1; i++) {
        //     G.allyRobotStrings[i + 1] = G.allyRobotStrings[i];
        //     G.opponentRobotStrings[i + 1] = G.opponentRobotStrings[i];
        // }
        // G.allyRobotStrings[0] = G.allyRobotString;
        // G.opponentRobotStrings[0] = G.opponentRobotString;
        // G.allAllyRobotStrings = new StringBuffer();
        // G.allOpponentRobotStrings = new StringBuffer();
        // for (int i = 0; i < G.ROBOT_HISTORY_TURNS - 1; i++) {
        //     if (G.allyRobotStrings[i] == null) {
        //         break;
        //     }
        //     G.allAllyRobotStrings.append(G.allyRobotStrings[i]);
        //     G.allOpponentRobotStrings.append(G.opponentRobotStrings[i]);
        // }
        if (G.backstabRound == 2000 && !G.rc.isCooperation()) {
            G.backstabRound = G.round;
        }
        updateInfo();
        Comms.updateRound();
    }

    public static void run(RobotController rc) throws Exception {
        try {
            G.rc = rc;
            G.type = G.rc.getType();
            Random.state = G.rc.getID() * 0x2bda6bc + 0x9734e9;
            G.mapWidth = G.rc.getMapWidth();
            G.mapHeight = G.rc.getMapHeight();
            G.mapCenter = new MapLocation(G.mapWidth / 2, G.mapHeight / 2);
            G.mapArea = G.mapWidth * G.mapHeight;
            G.team = G.rc.getTeam();
            G.opponentTeam = G.team.opponent();
            G.round = G.rc.getRoundNum();
            G.roundSpawned = G.rc.getRoundNum();
            G.indicatorString = new StringBuilder();

            G.allyRobotString = new StringBuffer();
            G.opponentRobots = new RobotInfoMap();
            // for (int i = 0; i < G.ROBOT_HISTORY_TURNS - 1; i++) {
            //     G.allyRobotStrings[i + 1] = G.allyRobotStrings[i];
            //     G.opponentRobotStrings[i + 1] = G.opponentRobotStrings[i];
            // }
            // G.allyRobotStrings[0] = G.allyRobotString;
            // G.opponentRobotStrings[0] = G.opponentRobotString;
            // G.allAllyRobotStrings = new StringBuffer();
            // G.allOpponentRobotStrings = new StringBuffer();
            // for (int i = 0; i < G.ROBOT_HISTORY_TURNS - 1; i++) {
            //     if (G.allyRobotStrings[i] == null) {
            //         break;
            //     }
            //     G.allAllyRobotStrings.append(G.allyRobotStrings[i]);
            //     G.allOpponentRobotStrings.append(G.opponentRobotStrings[i]);
            // }
            
            updateInfo();
            switch (G.type) {
                case BABY_RAT -> BabyRat.init();
                case RAT_KING -> RatKing.init();
                case CAT -> throw new Exception("cat alert!!!");
            }
            // init bytecode count
            // G.indicatorString.append("INIT " + Clock.getBytecodeNum() + " ");
            while (true) {
                int r = G.rc.getRoundNum();
                try {
                    updateRound();
                    switch (G.type) {
                        case BABY_RAT -> BabyRat.run();
                        case RAT_KING -> RatKing.run();
                        case CAT -> throw new Exception("cat alert!!!");
                    }
                    // G.indicatorString.append("SYM="
                    //         + (Comms.symmetry[0] ? "1" : "0") + (Comms.symmetry[1] ? "1" : "0") + (Comms.symmetry[2] ? "1 " : "0 "));
                    if (G.type.isBabyRatType()) {
                        Comms.sendSqueakMessages();
                    }
                    // Comms.drawIndicators();
                    // G.rc.setIndicatorString(G.indicatorString.toString());
                    G.indicatorString = new StringBuilder();
                } catch (GameActionException e) {
                    // System.out.println("Unexpected GameActionException");
                    // G.indicatorString.append(" GAErr!");
                    // G.rc.setIndicatorString(G.indicatorString.toString());
                    G.indicatorString = new StringBuilder();
                    // e.printStackTrace();
                } catch (Exception e) {
                    // System.out.println("Unexpected Exception");
                    // G.indicatorString.append(" Err!");
                    // G.rc.setIndicatorString(G.indicatorString.toString());
                    G.indicatorString = new StringBuilder();
                    // e.printStackTrace();
                }
                if (G.rc.getRoundNum() != r) {
                    System.err.println(
                            "Bytecode overflow! (Round " + r + ", " + G.type + ", " + G.rc.getLocation() + ")");
                    G.indicatorString.append("BYTE=" + r + " ");
                }
                // for (int i = 0; i <= 50; i++) {
                // int
                // a=Random.rand()%G.mapHeight,b=Random.rand()%G.mapWidth,c=Random.rand()%G.mapHeight,d=Random.rand()%G.mapWidth;
                // G.rc.setIndicatorLine(new MapLocation(b, a), new MapLocation(d, c),
                // Random.rand()%256, Random.rand()%256, Random.rand()%256);
                // }
                Clock.yield();
            }
        } catch (GameActionException e) {
            System.out.println("Unexpected GameActionException");
            e.printStackTrace();
        } catch (Exception e) {
            System.out.println("Unexpected Exception");
            e.printStackTrace();
        }
    }
}