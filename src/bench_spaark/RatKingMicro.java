package bench_spaark;

import battlecode.common.*;

public class RatKingMicro {
    public static double scorePerSelfHp = 10;
    public static double scorePerOpponentHp = 10;
    public static double scorePerCatHp = 5;
    public static double scorePerCheese = 30;

    public static Micro micro = (Direction d, MapLocation dest) -> {
        int a = Clock.getBytecodeNum();

        boolean[] canMove = new boolean[9];
        canMove[8] = true;
        for (int i = 8; --i >= 0;) {
            canMove[i] = G.rc.canMove(G.ALL_DIRECTIONS[i]);
        }
        double[] moveScores = new double[9];

        // turn scores assuming we move in current direction
        double[] turnScores = new double[9];
        moveScores[G.dirOrd(d)] += 200;
        if (d != Direction.CENTER) {
            moveScores[G.dirOrd(d.rotateLeft())] += 160;
            moveScores[G.dirOrd(d.rotateRight())] += 160;
        }
        for (int i = 8; --i >= 0;) {
            if (!canMove[i]) {
                boolean works = true;
                MapLocation loc1 = G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]);
                Direction d1 = G.ALL_DIRECTIONS[i].rotateLeft().rotateLeft();
                for (int j = 5; --j >= 0;) {
                    MapLocation loc = loc1.add(d1);
                    d1 = d1.rotateRight();
                    if (G.me.distanceSquaredTo(loc) <= 2) {
                        continue;
                    }
                    if (!G.rc.onTheMap(loc)) {
                        works = false;
                        break;
                    }
                    if (G.rc.canSenseRobotAtLocation(loc)) {
                        works = false;
                        break;
                    }
                    if (G.rc.senseMapInfo(loc).isWall()) {
                        works = false;
                        break;
                    }
                    if (G.rc.canRemoveDirt(loc)) {
                        moveScores[i] -= 10 * scorePerCheese;
                    }
                }
                if (!works) {
                    moveScores[i] -= 1000000;
                }
            }
        }
        // try to move away from the center
        // if (RatKing.originalLocation.x <= G.mapWidth / 2) {
        //     moveScores[G.dirOrd(Direction.WEST)] += 140;
        //     moveScores[G.dirOrd(Direction.NORTHWEST)] += 90;
        //     moveScores[G.dirOrd(Direction.SOUTHWEST)] += 90;
        // } else {
        //     moveScores[G.dirOrd(Direction.EAST)] += 140;
        //     moveScores[G.dirOrd(Direction.NORTHEAST)] += 90;
        //     moveScores[G.dirOrd(Direction.SOUTHEAST)] += 90;
        // }
        // if (RatKing.originalLocation.y <= G.mapHeight / 2) {
        //     moveScores[G.dirOrd(Direction.SOUTH)] += 140;
        //     moveScores[G.dirOrd(Direction.SOUTHEAST)] += 90;
        //     moveScores[G.dirOrd(Direction.SOUTHWEST)] += 90;
        // } else {
        //     moveScores[G.dirOrd(Direction.NORTH)] += 140;
        //     moveScores[G.dirOrd(Direction.NORTHEAST)] += 90;
        //     moveScores[G.dirOrd(Direction.NORTHWEST)] += 90;
        // }

        for (int i = 9; --i >= 0;) {
            // for (int j = 8; --j >= 0;) {
            //     for (int k = 0; k < G.ROBOT_HISTORY_TURNS; k++) {
            //         if (G.opponentRobotStrings[k].indexOf(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).add(G.ALL_DIRECTIONS[j]).toString()) != -1) {
            //             moveScores[i] -= 10 * scorePerSelfHp;
            //             break;
            //         }
            //     }
            // }
            for (int j = G.allyRobots.length; --j >= 0;) {
                int orig = G.me.distanceSquaredTo(G.allyRobots[j].getLocation());
                int nxt = G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).distanceSquaredTo(G.allyRobots[j].getLocation());
                if (orig > 18 && nxt <= 18) {
                    moveScores[i] += 10;
                } else if (orig > 9 && nxt <= 9) {
                    moveScores[i] += 10;
                } else if (orig <= 18 && nxt > 18) {
                    moveScores[i] -= 10;
                } else if (orig <= 9 && nxt > 9) {
                    moveScores[i] -= 10;
                }
                // int orig = Motion.getChebyshevDistance(G.me, G.allyRobots[j].getLocation());
                // int nxt = Motion.getChebyshevDistance(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]), G.allyRobots[j].getLocation());
                // if (orig > nxt) {
                //     moveScores[i] += 10;
                // } 
                // else if (orig < nxt) {
                //     moveScores[i] -= 10;
                // }
            }
            for (int j = G.opponentRobots.length; --j >= 0;) {
                int distance = G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).distanceSquaredTo(G.opponentRobots.infos[j].getLocation());
                moveScores[i] += ((double) distance) * 100;
                if (distance <= 8) {
                    moveScores[i] -= 10 * scorePerSelfHp;
                }
                moveScores[i] += distance;
                if (BabyRatMicro.isInRobotVision(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]), G.opponentRobots.infos[j])) {
                    moveScores[i] -= 100;
                }
            }
            if (G.lastSeenCatLocation != null) {
                int catDistance = (int) G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]).bottomLeftDistanceSquaredTo(G.lastSeenCatLocation);
                moveScores[i] += ((double) catDistance) * 100;
                if (catDistance <= G.CAT_SIGHT_RANGE_SQUARED) {
                    moveScores[i] -= 30 * scorePerSelfHp;
                }
                if (catDistance <= 8) {
                    moveScores[i] -= 50 * scorePerSelfHp;
                }
            }
        }
        MicroActionScore[] actionScores = new MicroActionScore[9];
        for (int i = 9; --i >= 0;) {
            actionScores[i] = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
        }
        // G.indicatorString.append("MICRO=" + (Clock.getBytecodeNum() - a) + " ");

        return new MicroScores(moveScores, turnScores, actionScores);
    };
}
