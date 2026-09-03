package bench_spaark;

import battlecode.common.*;

public class BabyRatMicro {
    public static double scorePerSelfHp = 10;
    public static double scorePerOpponentHp = 10;
    public static double scorePerCatHp = 5;
    public static double scorePerCheese = 20;

    public static int TARGET_CAT_ROUND = 1900;

    public static boolean ran = false;

    public static boolean canAct = true;
    
    public static int lastDroppedRatID = -1;
    public static int lastDroppedRatRound = -1;
    
    public static int carryRound = -1;

    public static double lastMicroScore;
    public static MapLocation lastMicroTarget;
    public static Direction lastMicroDir;
    public static int lastMicroType;

    public static Micro micro = (Direction d, MapLocation dest) -> {
        int a = Clock.getBytecodeNum();
        ran = true;
        // MICRO BYTECODE OPTIMIZATIONS:
        // - don't check for ratnapping if no enemy bots near

        if (G.round >= TARGET_CAT_ROUND) {
            scorePerCatHp = 100;
        }

        // scorePerCheese = 20000 / (G.rc.getGlobalCheese() + 100) - 200 / (G.rc.getRawCheese() + 20) - 10;
        scorePerCheese = 70000 / (G.rc.getGlobalCheese() + 500);
        if (G.rc.getRawCheese() > 0) {
            scorePerCheese = 10;
        }
        if (Comms.numberOfMines > 3) {
            scorePerCheese *= Math.pow(0.8, Comms.numberOfMines - 3);
        }
        boolean[] canMove = new boolean[9];
        canMove[8] = true;
        for (int i = 8; --i >= 0;) {
            canMove[i] = G.rc.canMove(G.ALL_DIRECTIONS[i]);
        }
        double[] moveScores = new double[9];

        // turn scores assuming we move in current direction
        double[] turnScores = new double[8];
        moveScores[G.dirOrd(d)] += 300;
        if (d != Direction.CENTER) {
            moveScores[G.dirOrd(d.rotateLeft())] += 260;
            moveScores[G.dirOrd(d.rotateRight())] += 260;
        }
        for (int i = 8; --i >= 0;) {
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]))) {
                moveScores[i] -= 10 * scorePerCheese;
            }
            else if (!canMove[i]) {
                moveScores[i] -= 1000000;
            }
        }
        MicroActionScore[] actionScores = new MicroActionScore[9];
        if (G.round == G.roundSpawned) {
            MicroActionScore stationaryAttackScore = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
            stationaryAttackScore.compare(tileScore(G.rc.adjacentLocation(G.dir.rotateLeft()), G.me));
            stationaryAttackScore.compare(tileScore(G.rc.adjacentLocation(G.dir), G.me));
            stationaryAttackScore.compare(tileScore(G.rc.adjacentLocation(G.dir.rotateRight()), G.me));
            
            for (int i = 9; --i >= 0;) {
                actionScores[i] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            }
            // hardcode to prevent bytecode running out on turn 1
            return new MicroScores(moveScores, turnScores, actionScores);
        }

        for (int i = 9; --i >= 0;) {
            // for (int j = 8; --j >= 0;) {
            //     for (int k = 0; k < G.ROBOT_HISTORY_TURNS; k++) {
            //         if (G.opponentRobotStrings[k].indexOf(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).add(G.ALL_DIRECTIONS[j]).toString()) != -1) {
            //             moveScores[i] -= 10 * scorePerSelfHp;
            //             break;
            //         }
            //     }
            // }
            // for (int j = G.allyRobots.length; --j >= 0;) {
            //     int distance = G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).distanceSquaredTo(G.allyRobots[j].getLocation());
            //     if (distance <= 2) {
            //         moveScores[i] -= 20;
            //     }
            //     // if (distance <= 8) {
            //     //     moveScores[i] += 30;
            //     // }
            // }
            boolean one = false;
            for (int j = G.opponentRobots.length; --j >= 0;) {
                if (!G.opponentRobots.infos[j].type.isBabyRatType()) {
                    continue;
                }
                int distance = G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).distanceSquaredTo(G.opponentRobots.infos[j].getLocation());
                double weight = 0.75;
                if (G.opponentRobots.length >= Comms.numberOfAlliesNearby) {
                    weight = 1;
                }
                if (distance == 1) {
                    moveScores[i] -= 50.0 * scorePerSelfHp * weight;
                }
                if (distance == 2) {
                    if (one || G.opponentRobots.infos[j].health > G.rc.getHealth()) {
                        moveScores[i] -= 25.0 * scorePerSelfHp * weight;
                    }
                    one = true;
                }
                if (isInRobotVision(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]), G.opponentRobots.infos[j])) {
                    moveScores[i] -= 1;
                    if (distance <= 2) {
                        moveScores[i] -= 20.0 * scorePerSelfHp * weight;
                    }
                    if (distance <= 8) {
                        moveScores[i] -= 30.0 * scorePerSelfHp * weight;
                    }
                }
                // if (G.opponentRobots.length > Comms.numberOfAlliesNearby + 1) {
                //     if (distance <= 2) {
                //         moveScores[i] -= 40 * scorePerSelfHp;
                //     }
                //     if (distance <= 8) {
                //         moveScores[i] -= 30 * scorePerSelfHp;
                //     }
                // }
                // else {
                    // if (distance <= 2) {
                    //     moveScores[i] -= 20 * scorePerSelfHp;
                    // }
                    // if (distance <= 8) {
                    //     moveScores[i] -= 10 * scorePerSelfHp;
                    // }
                // }
                // if (distance <= G.me.distanceSquaredTo(G.opponentRobots[j].getLocation())) {
                //     moveScores[i] += 20;
                    // moveScores[i] -= ((double) distance) / 1000;
                // }
                // if (G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).distanceSquaredTo(G.opponentRobots[j].getLocation()) <= 2) {
                //     moveScores[i] -= 10 * scorePerSelfHp;
                // }
                // if (isInRobotVision(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]), G.opponentRobots[j])) {
                //     moveScores[i] -= 1;
                // }
            }
            if (G.lastSeenCatLocation != null && Comms.numberOfAlliesNearby + 1 >= G.opponentRobots.length) {
                int catDistance = (int) G.lastSeenCatLocation.bottomLeftDistanceSquaredTo(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]));
                // if (BabyRat.mode != BabyRat.DEFEND_RAT_KING) {
                if (G.round < TARGET_CAT_ROUND) {
                    if (G.me.isWithinDistanceSquared(G.lastSeenCatLocation, G.CAT_SIGHT_RANGE_SQUARED, G.lastSeenCat.direction, 180, true)) {
                        if (catDistance <= G.CAT_SIGHT_RANGE_SQUARED) {
                            moveScores[i] -= 30 * scorePerSelfHp - ((double) catDistance) / 10;
                        }
                    }
                    if (G.me.isWithinDistanceSquared(G.lastSeenCatLocation, 2, G.lastSeenCat.direction, 180, true)) {
                        moveScores[i] -= 100 * scorePerSelfHp;
                    }
                    if (catDistance <= 8) {
                        moveScores[i] -= 20 * scorePerSelfHp;
                    }
                    if (catDistance <= 2) {
                        moveScores[i] -= 20 * scorePerSelfHp;
                    }
                }
                // }
            }
        }
        if (G.rc.isActionReady() && canAct && (G.detectedNonAllyRobots || G.rc.getCarrying() != null)) {
            MapLocation loc6 = G.me;
            MapLocation loc3 = G.rc.adjacentLocation(G.dir.rotateLeft());
            MapLocation loc7 = G.rc.adjacentLocation(G.dir.rotateRight());
            MapLocation loc0 = loc3.add(G.dir.rotateLeft());
            MapLocation loc1 = loc3.add(G.dir);
            MapLocation loc4 = G.rc.adjacentLocation(G.dir);
            MapLocation loc2 = loc4.add(G.dir);
            MapLocation loc5 = loc6.add(G.dir);
            MapLocation loc8 = loc6.add(G.dir.rotateRight());

            MicroActionScore stationaryAttackScore = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
            stationaryAttackScore.compare(tileScore(loc3, loc6));
            stationaryAttackScore.compare(tileScore(loc4, loc6));
            stationaryAttackScore.compare(tileScore(loc7, loc6));

            // MicroActionScore ratnapScore0 = ratnapScore(loc0, loc3);
            // MicroActionScore ratnapScore1 = ratnapScore(loc2, loc4);
            // MicroActionScore ratnapScore2 = ratnapScore(loc8, loc7);

            // MicroActionScore ratnapScore3;
            // if (ratnapScore0.score > 0 && G.rc.canMove(G.dir.rotateLeft())) {
            //     ratnapScore3 = ratnapScore0;
            //     ratnapScore3.score -= 5 * scorePerOpponentHp;
            // }
            // else {
            //     ratnapScore3 = ratnapScore(loc3, loc6);
            // }
            // MicroActionScore ratnapScore4;
            // if (ratnapScore1.score > 0 && G.rc.canMove(G.dir)) {
            //     ratnapScore4 = ratnapScore1;
            //     ratnapScore4.score -= 5 * scorePerOpponentHp;
            // }
            // else {
            //     ratnapScore4 = ratnapScore(loc4, loc6);
            // }
            // MicroActionScore ratnapScore5;
            // if (ratnapScore2.score > 0 && G.rc.canMove(G.dir.rotateRight())) {
            //     ratnapScore5 = ratnapScore2;
            //     ratnapScore5.score -= 5 * scorePerOpponentHp;
            // }
            // else {
            //     ratnapScore5 = ratnapScore(loc7, loc6);
            // }

            // stationaryAttackScore.compare(ratnapScore3);
            // stationaryAttackScore.compare(ratnapScore4);
            // stationaryAttackScore.compare(ratnapScore5);

            actionScores[8] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[7] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[6] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[5] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[4] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[3] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[2] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[1] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);
            actionScores[0] = new MicroActionScore(stationaryAttackScore.score, stationaryAttackScore.target, stationaryAttackScore.dir, stationaryAttackScore.type);

            int dirOrd = G.dirOrd(G.dir);
            MapLocation meLoc = loc4;
            
            MicroActionScore attackScore0 = tileScore(loc0);
            double attackScore0Score = attackScore0.score;
            MapLocation attackScore0Target = attackScore0.target;
            Direction attackScore0Dir = attackScore0.dir;
            int attackScore0Type = attackScore0.type;
            MicroActionScore attackScore1 = tileScore(loc1);
            double attackScore1Score = attackScore1.score;
            MapLocation attackScore1Target = attackScore1.target;
            Direction attackScore1Dir = attackScore1.dir;
            int attackScore1Type = attackScore1.type;
            MicroActionScore attackScore2 = tileScore(loc2);
            double attackScore2Score = attackScore2.score;
            MapLocation attackScore2Target = attackScore2.target;
            Direction attackScore2Dir = attackScore2.dir;
            int attackScore2Type = attackScore2.type;
            MicroActionScore attackScore3 = tileScore(loc5);
            double attackScore3Score = attackScore3.score;
            MapLocation attackScore3Target = attackScore3.target;
            Direction attackScore3Dir = attackScore3.dir;
            int attackScore3Type = attackScore3.type;
            MicroActionScore attackScore4 = tileScore(loc8);
            double attackScore4Score = attackScore4.score;
            MapLocation attackScore4Target = attackScore4.target;
            Direction attackScore4Dir = attackScore4.dir;
            int attackScore4Type = attackScore4.type;

            // compute tiles, compute throw scores

            if (G.rc.getCarrying() == null) {
                if (G.dirOrd(G.dir) % 2 == 0) {
                    // facing diagonally
                    
                    // straight ahead
                    if (canMove[dirOrd]) {
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        
                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }
                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }

                        ratnapScoreStrafe(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 left
                    dirOrd = (dirOrd + 1) % 8;

                    if (canMove[dirOrd]) {
                        meLoc = loc3;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }

                        ratnapScore(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }

                        ratnapScoreStrafe(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 135 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 135 right
                    dirOrd = (dirOrd + 2) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }

                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = loc7;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }
                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }

                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                }
                else {
                    // facing orthogonally
                    
                    // straight ahead
                    if (canMove[dirOrd]) {
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }

                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = loc3;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }

                        ratnapScore(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 right
                    dirOrd = (dirOrd + 4) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = loc7;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }
                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }
                        
                        ratnapScoreStrafe(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                }
            }
            else {
                if (G.dirOrd(G.dir) % 2 == 0) {
                    // facing diagonally
                    
                    // straight ahead
                    if (canMove[dirOrd]) {
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }
                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }

                        ratnapScoreStrafe(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 left
                    dirOrd = (dirOrd + 1) % 8;

                    if (canMove[dirOrd]) {
                        meLoc = loc3;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }

                        ratnapScore(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }

                        ratnapScoreStrafe(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 135 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // opposite
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 135 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }

                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = loc7;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }
                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }

                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                }
                else {
                    // facing orthogonally
                    
                    // straight ahead
                    if (canMove[dirOrd]) {
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }

                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = loc3;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        if (attackScore0Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore0Score;
                        	curActionScoreDir = attackScore0Dir;
                        	curActionScoreType = attackScore0Type;
                        	curActionScoreTarget = attackScore0Target;
                        }
                        if (attackScore1Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore1Score;
                        	curActionScoreDir = attackScore1Dir;
                        	curActionScoreType = attackScore1Type;
                        	curActionScoreTarget = attackScore1Target;
                        }
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }

                        ratnapScore(loc0, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc1, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc3, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 135 left
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // opposite
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 135 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 90 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = G.rc.adjacentLocation(G.ALL_DIRECTIONS[dirOrd]);
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;

                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc7, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                    
                    // 45 right
                    dirOrd = (dirOrd + 1) % 8;
                    if (canMove[dirOrd]) {
                        meLoc = loc7;
                        double curActionScoreScore = stationaryAttackScore.score;
                        MapLocation curActionScoreTarget = stationaryAttackScore.target;
                        Direction curActionScoreDir = stationaryAttackScore.dir;
                        int curActionScoreType = stationaryAttackScore.type;
                        
                        if (attackScore2Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore2Score;
                        	curActionScoreDir = attackScore2Dir;
                        	curActionScoreType = attackScore2Type;
                        	curActionScoreTarget = attackScore2Target;
                        }
                        if (attackScore3Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore3Score;
                        	curActionScoreDir = attackScore3Dir;
                        	curActionScoreType = attackScore3Type;
                        	curActionScoreTarget = attackScore3Target;
                        }
                        if (attackScore4Score > curActionScoreScore) {
                        	curActionScoreScore = attackScore4Score;
                        	curActionScoreDir = attackScore4Dir;
                        	curActionScoreType = attackScore4Type;
                        	curActionScoreTarget = attackScore4Target;
                        }
                        
                        ratnapScoreStrafe(loc2, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc5, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScore(loc8, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc4, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        ratnapScoreStrafe(loc6, meLoc);
                        if (lastMicroScore > curActionScoreScore) {
                        	curActionScoreScore = lastMicroScore;
                        	curActionScoreDir = lastMicroDir;
                        	curActionScoreType = lastMicroType;
                        	curActionScoreTarget = lastMicroTarget;
                        }
                        actionScores[dirOrd] = new MicroActionScore(curActionScoreScore, curActionScoreTarget, curActionScoreDir, curActionScoreType);
                    }
                }
            }

            
            moveScores[8] += actionScores[8].score;
            moveScores[7] += actionScores[7].score;
            moveScores[6] += actionScores[6].score;
            moveScores[5] += actionScores[5].score;
            moveScores[4] += actionScores[4].score;
            moveScores[3] += actionScores[3].score;
            moveScores[2] += actionScores[2].score;
            moveScores[1] += actionScores[1].score;
            moveScores[0] += actionScores[0].score;
            // turnScores[G.dirOrd(G.dir.rotateLeft().rotateLeft().rotateLeft())] += ratnapScores[3].score;
            // turnScores[G.dirOrd(G.dir.rotateLeft().rotateLeft())] += Math.max(ratnapScores[3].score, ratnapScores[4].score);
            // turnScores[G.dirOrd(G.dir.rotateLeft())] += Math.max(ratnapScores[3].score, Math.max(ratnapScores[4].score, ratnapScores[5].score));
            // turnScores[G.dirOrd(G.dir)] += Math.max(ratnapScores[4].score, Math.max(ratnapScores[5].score, ratnapScores[6].score));
            // turnScores[G.dirOrd(G.dir.rotateRight())] += Math.max(ratnapScores[5].score, Math.max(ratnapScores[6].score, ratnapScores[7].score));
            // turnScores[G.dirOrd(G.dir.rotateRight().rotateRight())] += Math.max(ratnapScores[6].score, ratnapScores[7].score);
            // turnScores[G.dirOrd(G.dir.rotateRight().rotateRight().rotateRight())] += ratnapScores[7].score;
        }
        else {
            for (int i = 9; --i >= 0;) {
                actionScores[i] = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
            }
        }

        // if (BabyRat.mode != BabyRat.ATTACK && BabyRat.mode != BabyRat.CHEESE_MESSAGE) {
        if (BabyRat.mode != BabyRat.ATTACK) {
            if (G.round % 4 <= 1) {
                turnScores[G.dirOrd(G.dir.rotateRight())] += 10;
            }
            else {
                turnScores[G.dirOrd(G.dir.rotateLeft())] += 10;
            }
        }
        if (G.lastSeenOpponentLocation != null && !G.lastSeenOpponentLocation.equals(G.me)) {
            turnScores[G.dirOrd(G.me.directionTo(G.lastSeenOpponentLocation))] += 100;
        }
        // for (int k = 0; k < 8; k++) {
        //     for (int j = G.opponentRobots.length; --j >= 0;) {
        //         if (isInRobotVision(G.opponentRobots[j].location, G.rc.adjacentLocation(G.ALL_DIRECTIONS[k]), G.ALL_DIRECTIONS[k], 20)) {
        //             turnScores[k] += 50;
        //             moveScores[k] += 10;
        //         }
        //     }
        // }

        // G.indicatorString.append("MICRO=" + (Clock.getBytecodeNum() - a) + " ");
        // if (Clock.getBytecodeNum() - a > 2000) {
        //     System.out.println("MICRO: "+(Clock.getBytecodeNum() - a));
        // }
        return new MicroScores(moveScores, turnScores, actionScores);
    };
    public static MicroActionScore tileScore(MapLocation loc) throws Exception {
        MicroActionScore score = attackScore(loc);
        score.compare(trapScore(loc));
        return score;
    }
    public static MicroActionScore tileScore(MapLocation loc, MapLocation meLoc) throws Exception {
        MicroActionScore score = attackScore(loc);
        score.compare(trapScore(loc));
        if (loc.equals(G.rc.adjacentLocation(G.dir)) && meLoc.equals(G.me)) {
            ratnapScore(loc, meLoc);
            if (lastMicroScore > score.score) {
                score.score = lastMicroScore;
                score.dir = lastMicroDir;
                score.type = lastMicroType;
                score.target = lastMicroTarget;
            }
        }
        else {
            ratnapScoreStrafe(loc, meLoc);
            if (lastMicroScore > score.score) {
                score.score = lastMicroScore;
                score.dir = lastMicroDir;
                score.type = lastMicroType;
                score.target = lastMicroTarget;
            }
        }
        return score;
    }
    public static MicroActionScore tileScoreNoStrafe(MapLocation loc, MapLocation meLoc) throws Exception {
        MicroActionScore score = attackScore(loc);
        score.compare(trapScore(loc));
        ratnapScore(loc, meLoc);
        if (lastMicroScore > score.score) {
            score.score = lastMicroScore;
            score.dir = lastMicroDir;
            score.type = lastMicroType;
            score.target = lastMicroTarget;
        }
        return score;
    }
    public static MicroActionScore attackScore(MapLocation loc) throws Exception {
        double score = 0;
        int attackType = MicroActionScore.ATTACK;
        if (G.rc.canSenseRobotAtLocation(loc)) {
            RobotInfo robot = G.rc.senseRobotAtLocation(loc);
            if (robot.team == G.opponentTeam) {
                score = 10 * scorePerOpponentHp - robot.health / 10000;
                if (G.rc.getRawCheese() > 0 && robot.health > 20) {
                    switch (G.rc.getRawCheese()) {
                        case 1:
                            attackType = MicroActionScore.ATTACK_1;
                            break;
                        case 2:
                        case 3:
                        case 4:
                            attackType = MicroActionScore.ATTACK_2;
                            break;
                        default:
                            attackType = MicroActionScore.ATTACK_3;
                            break;
                    }
                } 
                else if (G.rc.getAllCheese() >= 2 && robot.type == UnitType.BABY_RAT && robot.health > 20) {
                    int hpDiff = robot.health - G.rc.getHealth();
                    if (hpDiff == 0) {
                        attackType = MicroActionScore.ATTACK_1;
                    } else if (hpDiff == 1 || hpDiff == 2) {
                        attackType = MicroActionScore.ATTACK_2;
                    }
                }
            }
            else if (robot.team == Team.NEUTRAL) {
                score = 10 * scorePerCatHp - robot.health / 10000;
            }
        }
        return new MicroActionScore(score, loc, Direction.CENTER, attackType);
    }
    public static MicroActionScore trapScore(MapLocation loc) throws Exception {
        if (!G.rc.onTheMap(loc) || !G.rc.sensePassability(loc) || G.rc.canSenseRobotAtLocation(loc)) {
            return new MicroActionScore(-1, loc, Direction.CENTER, MicroActionScore.TRAP);
        }
        if (G.rc.canSenseLocation(loc) && G.rc.senseMapInfo(loc).getTrap() != TrapType.NONE) {
            return new MicroActionScore(-1, loc, Direction.CENTER, MicroActionScore.TRAP);
        }
        double score = -20 * scorePerCheese;
        double catTrapScore = -10 * scorePerCheese;
        if (G.rc.getNumberRatTraps() < 25 && G.rc.getNumberCatTraps() < 10 && G.round - 100 < G.backstabRound) {
            for (int i = 8; --i >= 0;) {
                MapLocation aLoc = loc.add(G.ALL_DIRECTIONS[i]);
                if (G.rc.canSenseLocation(aLoc)) {
                    if (G.rc.senseMapInfo(aLoc).getTrap() == TrapType.RAT_TRAP) {
                        score -= 200;
                    }
                    // if (G.opponentRobotStrings[0].indexOf(aLoc.toString()) != -1) {
                    if (G.rc.canSenseRobotAtLocation(aLoc)) {
                        RobotInfo robot = G.rc.senseRobotAtLocation(aLoc);
                        if (robot.team == G.opponentTeam) {
                            score += 50 * scorePerOpponentHp - robot.health / 10000;
                        }
                        else if (robot.team == Team.NEUTRAL) {
                            catTrapScore += 50 * scorePerCatHp;
                        }
                    }
                    else if (G.opponentRobots.exists(aLoc)) {
                        score += 50 * scorePerOpponentHp - 5;
                    }
                }
            }
        }
        else if (G.rc.getNumberRatTraps() < 25) {
            for (int i = 8; --i >= 0;) {
                MapLocation aLoc = loc.add(G.ALL_DIRECTIONS[i]);
                if (G.rc.canSenseLocation(aLoc)) {
                    if (G.rc.senseMapInfo(aLoc).getTrap() == TrapType.RAT_TRAP) {
                        score -= 200;
                    }
                    // if (G.opponentRobotStrings[0].indexOf(aLoc.toString()) != -1) {
                    if (G.rc.canSenseRobotAtLocation(aLoc)) {
                        RobotInfo robot = G.rc.senseRobotAtLocation(aLoc);
                        if (robot.team == G.opponentTeam) {
                            score += 50 * scorePerOpponentHp - robot.health / 10000;
                        }
                    }
                    else if (G.opponentRobots.exists(aLoc)) {
                        score += 50 * scorePerOpponentHp - 5;
                    }
                }
            }
        }
        else if (G.rc.getNumberCatTraps() < 10 && G.round - 100 < G.backstabRound) {
            for (int i = 8; --i >= 0;) {
                MapLocation aLoc = loc.add(G.ALL_DIRECTIONS[i]);
                if (G.rc.canSenseLocation(aLoc)) {
                    if (G.rc.canSenseRobotAtLocation(aLoc)) {
                        RobotInfo robot = G.rc.senseRobotAtLocation(aLoc);
                        if (robot.team == Team.NEUTRAL) {
                            catTrapScore += 50 * scorePerCatHp;
                        }
                    }
                }
            }
        }
        else {
            return new MicroActionScore(-1, loc, Direction.CENTER, MicroActionScore.TRAP);
        }
        if (catTrapScore > score) {
            return new MicroActionScore(catTrapScore, loc, Direction.CENTER, MicroActionScore.CAT_TRAP);
        }
        return new MicroActionScore(score, loc, Direction.CENTER, MicroActionScore.TRAP);
    }
    public static void ratnapScore(MapLocation loc, MapLocation meLoc) throws Exception {
        ratnapScoreStrafe(loc, meLoc);
        if (lastMicroScore > 0) {
            lastMicroScore += 100;
        }
    }
    public static void ratnapScoreStrafe(MapLocation loc, MapLocation meLoc) throws Exception {
        // if (!G.me.equals(meLoc) && !G.rc.canMove(G.me.directionTo(meLoc))) {
        //     return new MicroActionScore(-1000000, loc, Direction.CENTER, MicroActionScore.RATNAP);
        // }
        double score = -1;
        test: if (G.rc.getCarrying() == null) {
            // if (G.opponentRobotStrings[0].indexOf(loc.toString()) != -1) {
            if (G.rc.canSenseRobotAtLocation(loc)) {
                RobotInfo robot = G.rc.senseRobotAtLocation(loc);
                if (robot.type.isRatKingType()) {
                    break test;
                }
                // if (robot.ID == lastDroppedRatID && G.round <= lastDroppedRatRound + 1) {
                //     break test;
                // }
                int dirDiff = (G.dirOrd(robot.direction.opposite()) - G.dirOrd(meLoc.directionTo(loc)) + 8) % 8;
                if (robot.team == G.opponentTeam && robot.type == UnitType.BABY_RAT && (robot.health < G.rc.getHealth() || (dirDiff > 1 && dirDiff < 7))) {
                    score += 70 * scorePerOpponentHp - robot.health / 10000;
                    score += 1000;
                    if (meLoc.distanceSquaredTo(loc) == 2) {
                        score += 20 * scorePerSelfHp;
                    }
                    else {
                        score += 40 * scorePerSelfHp;
                    }
                    if (robot.getCarryingRobot() != null && robot.getCarryingRobot().team == G.team) {
                        // score += robot.getCarryingRobot().health * scorePerSelfHp;
                        score += 30 * scorePerSelfHp + robot.getCarryingRobot().health / 10;
                    }
                }
            }
        }
        else if (G.rc.getCarrying().team != G.team) {
            boolean valid = G.rc.onTheMap(loc) && G.rc.sensePassability(loc) && (G.me.equals(loc) || G.rc.senseRobotAtLocation(loc) == null);
            if (G.me.distanceSquaredTo(loc) <= 2 && G.rc.canMove(G.me.directionTo(loc))) {
                valid = true;
            }
            if (valid) {
                MapLocation newLoc = loc;
                for (int i = 4; --i >= 0;) {
                    newLoc = newLoc.add(meLoc.directionTo(loc));
                    if (!G.rc.onTheMap(newLoc)) {
                        score += 10 * scorePerOpponentHp;
                        break;
                    }
                    if (((Comms.wall[newLoc.y] >> newLoc.x) & 1) == 1) {
                        score += i * 5 * scorePerOpponentHp + 1000;
                        break;
                    }
                    if (G.opponentRobots.exists(newLoc)) {
                        score += i * 10 * scorePerOpponentHp + 1000;
                        break;
                    }
                    if (G.rc.canSenseLocation(newLoc)) {
                        if (!G.rc.sensePassability(newLoc)) {
                            score += i * 5 * scorePerOpponentHp + 1000;
                            break;
                        }
                        if (G.allyRobotString.indexOf(newLoc.toString()) != -1) {
                            score += -100;
                            break;
                        }
                    }
                    else if (G.rc.isMovementReady() && G.me.distanceSquaredTo(newLoc) <= 2 && !G.rc.canMove(G.me.directionTo(loc))) {
                        score += i * 5 * scorePerOpponentHp + 1000;
                        break;
                    }
                }
                if (carryRound != -1) {
                    switch (G.round - carryRound) {
                        case 9:
                            score += 100;
                            break;
                        case 8:
                            score += 20;
                            break;
                        case 7:
                            score += 10;
                            break;
                    }
                }
            }
        }
        else if (BabyRat.mode == BabyRat.DEFEND_RAT_KING) {
            boolean valid = G.rc.onTheMap(loc) && G.rc.sensePassability(loc) && (G.me.equals(loc) || G.rc.senseRobotAtLocation(loc) == null);
            if (G.me.distanceSquaredTo(loc) <= 2 && G.rc.canMove(G.me.directionTo(loc))) {
                valid = true;
            }
            if (valid) {
                MapLocation newLoc = loc;
                for (int i = 4; --i >= 0;) {
                    newLoc = newLoc.add(meLoc.directionTo(loc));
                    if (!G.rc.onTheMap(newLoc)) {
                        score += -100;
                        break;
                    }
                    if (((Comms.wall[newLoc.y] >> newLoc.x) & 1) == 1) {
                        score += -100;
                        break;
                    }
                    if (G.opponentRobots.exists(newLoc)) {
                        score += 0;
                        break;
                    }
                    if (G.rc.canSenseLocation(newLoc)) {
                        if (!G.rc.sensePassability(newLoc)) {
                            score += -100;
                            break;
                        }
                        if (G.rc.canSenseRobotAtLocation(newLoc) && G.rc.senseRobotAtLocation(newLoc).team == Team.NEUTRAL) {
                            score += 1000;
                            break;
                        }
                        if (G.allyRobotString.indexOf(newLoc.toString()) != -1) {
                            score += -100;
                            break;
                        }
                    }
                    if (G.rc.isMovementReady() && G.me.distanceSquaredTo(newLoc) <= 2 && !G.rc.canMove(G.me.directionTo(loc))) {
                        score += -100;
                        break;
                    }
                }
            }
        }
        lastMicroScore = score;
        lastMicroTarget = loc;
        lastMicroDir = meLoc.directionTo(loc);
        lastMicroType = MicroActionScore.RATNAP;
        // return new MicroActionScore(score, loc, meLoc.directionTo(loc), MicroActionScore.RATNAP);
    }
    public static boolean isInRobotVision(MapLocation loc, RobotInfo robot) throws Exception {
        return isInRobotVision(loc, robot.location, robot.direction, 20);
    }
    public static boolean isInRobotVision(MapLocation loc, MapLocation robotLoc, Direction robotDir, int distance) throws Exception {
        if (loc.distanceSquaredTo(robotLoc) > distance) {
            return false;
        }
        switch (robotDir) {
            case Direction.SOUTHWEST:
                return loc.x <= robotLoc.x && loc.y <= robotLoc.y;
            case Direction.SOUTHEAST:
                return loc.x >= robotLoc.x && loc.y <= robotLoc.y;
            case Direction.NORTHWEST:
                return loc.x <= robotLoc.x && loc.y >= robotLoc.y;
            case Direction.NORTHEAST:
                return loc.x >= robotLoc.x && loc.y >= robotLoc.y;
            case Direction.WEST:
                return loc.y - loc.x >= robotLoc.y - robotLoc.x && loc.y + loc.x <= robotLoc.y + robotLoc.x;
            case Direction.EAST:
                return loc.y - loc.x <= robotLoc.y - robotLoc.x && loc.y + loc.x >= robotLoc.y + robotLoc.x;
            case Direction.SOUTH:
                return loc.y - loc.x <= robotLoc.y - robotLoc.x && loc.y + loc.x <= robotLoc.y + robotLoc.x;
            case Direction.NORTH:
                return loc.y - loc.x >= robotLoc.y - robotLoc.x && loc.y + loc.x >= robotLoc.y + robotLoc.x;
            default:
                throw new Exception("invalid dir robot vision buh");
        }
    }
}
