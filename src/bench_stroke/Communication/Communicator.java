package bench_stroke.Communication;

import bench_stroke.DataStructures.FastIterableLocMap;
import bench_stroke.DataStructures.FastMath;
import bench_stroke.FastIterableLocSet;
import bench_stroke.RatKing;
import bench_stroke.SymmetryManager;
import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static bench_stroke.RobotPlayer.MAP_HEIGHT;
import static bench_stroke.RobotPlayer.MAP_WIDTH;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.RobotPlayer.rng;

public class Communicator {
    public enum SqueakTypes {
        LURE,
        ENEMY_RAT_KING,
        NEARBY_CAT,
        SYMMETRY,
        PICKUP,
        FORM_RAT_KING,
        PRESENCE,
        CHEESE_MINE
    }

    static int RAT_KING_ARR_OFFSET = 49;
    static int X_AVERAGE_ENEMY_ATTACKING_RK_IDX = 46;
    static int Y_AVERAGE_ENEMY_ATTACKING_RK_IDX = 47;

    //singletons kinda
    static RatKingInfo[] ratKings;
    static CheeseMineInfo[] cheeseMines;

    static double THREAT_LEVEL_MULTIPLIER = 1;
    private static final Direction[] DIRS = Direction.allDirections();
    private static final Direction[] DIRS_NO_CENTER = {
            Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
            Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    public static void initializeCommunication() {
        ratKings = null;
        cheeseMines = null;
    }

    //so we don't reuse stuff in a turn

    /**
     * Stores a Rat King in the shared array if there is space, if not
     * the lowest health Rat King will be evicted.
     *
     * @return whether the operation succeeded
     */
    public static boolean storeRatKingInSharedArray(MapLocation location, int health, int turn, boolean inDistress) throws GameActionException {

        int index = findAvailableRatKingIndex(location, turn);
        if (index == -1) {
            return false;
        }
        int packedVal = 0;

        packedVal |= 0b11_1111 & location.x;
        packedVal <<= 6;

        packedVal |= 0b11_1111 & location.y;
        packedVal <<= 7;

        packedVal |= 0b111_1111 & turn;
        packedVal <<= 1;

        packedVal |= inDistress ? 1 : 0;
        packedVal <<= 9;

        packedVal |= 0b1_1111_1111 & health;
        packedVal <<= 1;

        packedVal |= 1;


        rc.writeSharedArray(index + 2, packedVal & GameConstants.COMM_ARRAY_MAX_VALUE);
        packedVal >>= 10;
        rc.writeSharedArray(index + 1, packedVal & GameConstants.COMM_ARRAY_MAX_VALUE);
        packedVal >>= 10;
        rc.writeSharedArray(index, packedVal & GameConstants.COMM_ARRAY_MAX_VALUE);

        return true;
    }

    /**
     * Finds an available shared array index to write a Rat King.
     *
     * @return the index to write to, returns -1 if no valid indices are open
     */

    private static int findAvailableRatKingIndex(MapLocation currentLocation, int turn) throws GameActionException {
        //Let's store all the shared array values, so we don't have to keep reading and wasting bytecodes
        int[] arr = new int[3 * GameConstants.MAX_NUMBER_OF_RAT_KINGS];
        for(int i = 0; i < 3 * GameConstants.MAX_NUMBER_OF_RAT_KINGS; i++) {
            arr[i] = rc.readSharedArray(i + RAT_KING_ARR_OFFSET);
        }

        //Clean out dead rat kings
        for(int i = 0; i < arr.length; i += 3) {
            if((arr[i + 2] & 1) == 0) {
                continue;
            }

            int ratKingTurn = 0b111_1111 & (arr[i + 1] >> 1);
            if((turn % 128 - ratKingTurn + 128) % 128 >= 2) { //Rat king has not been updated in more than two turns, clear it out
                arr[i + 2] = 0;
                rc.writeSharedArray(i + 2 + RAT_KING_ARR_OFFSET, 0);
            }
        }

        //Check if we already exist in the shared array, return that index if we do
        for(int i = 0; i < arr.length; i += 3) {
            if((arr[i + 2] & 1) == 0) {
                continue;
            }

            int packedVal = arr[i + 1] | (arr[i] << 10);
            packedVal >>= 8;
            int y = 0b11_1111 & packedVal;
            packedVal >>= 6;
            int x = 0b11_1111 & packedVal;
            if (currentLocation.isWithinDistanceSquared(
                    new MapLocation(x, y), 2)) {
                return i + RAT_KING_ARR_OFFSET;
            }
        }

        //iterate through all indices, check the valid bit, if all are full vacate the lowest health Rat King and place yourself
        for (int i = 0; i < arr.length; i += 3) {
            int index3 = arr[i + 2];
            int used = index3 & 1;
            if (used == 0) { //open index
                return i + RAT_KING_ARR_OFFSET;
            }
        }
        return -1;
    }

    //the following three methods are used for a rat king to communicate where its attackers are to better coordinate rescue efforts

    public static void clearAverageEnemyInSharedArray() throws GameActionException {
        rc.writeSharedArray(X_AVERAGE_ENEMY_ATTACKING_RK_IDX, 0);
        rc.writeSharedArray(Y_AVERAGE_ENEMY_ATTACKING_RK_IDX, 0);
    }

    public static boolean storeAverageEnemyInSharedArray(MapLocation averageEnemy) throws GameActionException {
        int x = averageEnemy.x;
        int y = averageEnemy.y;
        rc.writeSharedArray(X_AVERAGE_ENEMY_ATTACKING_RK_IDX, x);
        rc.writeSharedArray(Y_AVERAGE_ENEMY_ATTACKING_RK_IDX, y);
        return true;
    }

    public static MapLocation getAverageEnemyFromSharedArray() throws GameActionException{
        int x = rc.readSharedArray(X_AVERAGE_ENEMY_ATTACKING_RK_IDX);
        int y = rc.readSharedArray(Y_AVERAGE_ENEMY_ATTACKING_RK_IDX);
        if (x + y == 0) return null;
        return new MapLocation(x, y);
    }
    /**
     * Retrieves a list of the three highest health Rat Kings.
     *
     * @return a List of RatKingInfo objects
     */

    public static RatKingInfo[] getRatKings() throws GameActionException {
        if (ratKings != null) return ratKings;

        RatKingInfo[] temp = new RatKingInfo[GameConstants.MAX_NUMBER_OF_RAT_KINGS];
        int cur = 0;
        int idx = RAT_KING_ARR_OFFSET;

        for (int k = GameConstants.MAX_NUMBER_OF_RAT_KINGS; k-- > 0; idx += 3) {
            int third = rc.readSharedArray(idx + 2);
            if ((third & 1) == 0) continue; // unused slot

            int packed = rc.readSharedArray(idx + 1) | (rc.readSharedArray(idx) << 10);
            int x = (packed >>> 14) & 0x3F;
            int y = (packed >>> 8) & 0x3F;
            int turnMod = (packed >>> 1) & 0x7F;
            boolean inDistress = (packed & 1) != 0;
            int health = third >>> 1;

            temp[cur++] = new RatKingInfo(new MapLocation(x, y), health, turnMod, inDistress);
        }

        ratKings = (cur == temp.length) ? temp : Arrays.copyOf(temp, cur);
        return ratKings;
    }
    

    public static int getNumRatKings() throws GameActionException {
        RatKingInfo[] ratKings = getRatKings();
        return ratKings.length;
    }

    //returns the closest rat king to the given origin location
    public static RatKingInfo getClosestRatKing(MapLocation origin) throws GameActionException {
        RatKingInfo[] ratKings = getRatKings();
        RatKingInfo closestRatKing = null;
        int smallestDist = Integer.MAX_VALUE;
        for(RatKingInfo ratKing : ratKings) {
            int dist = ratKing.loc().distanceSquaredTo(origin);
            if (dist < smallestDist) {
                closestRatKing = ratKing;
                smallestDist = dist;
            }
        }
        return closestRatKing;
    }

    //returns the lowest health rat king
    public static RatKingInfo getLowestHealthRatKing() throws GameActionException {
        RatKingInfo[] ratKings = getRatKings();
        RatKingInfo lowestHealthRatKing = null;
        int lowestHealth = Integer.MAX_VALUE;
        for(RatKingInfo ratKing : ratKings) {
            if (ratKing.health() < lowestHealth) {
                lowestHealthRatKing = ratKing;
                lowestHealth = ratKing.health();
            }
        }
        return lowestHealthRatKing;
    }
    //returns the closest inDistressRatKing to origin
    public static RatKingInfo getInDistressRatKing(MapLocation origin) throws GameActionException {
        RatKingInfo[] ratKings = getRatKings();
        RatKingInfo closestInDistressRatKing = null;
        int smallestDist = Integer.MAX_VALUE;
        for(RatKingInfo ratKing : ratKings) {
            if (ratKing.inDistress()) {
                int dist = origin.distanceSquaredTo(ratKing.loc());
                if (dist < smallestDist) {
                    smallestDist = dist;
                    closestInDistressRatKing = ratKing;
                }
            }
        }
        return closestInDistressRatKing;
    }

    public static void storeCheeseMineToArray(MapLocation location, int threatLevel) throws GameActionException {
        int[] arrayCache = new int[20];
        for(int i = 0; i < 20; i++) {
            arrayCache[i] = rc.readSharedArray(i);
        }

        //check if mine already exists and update it
        for(int i = 1; i < arrayCache.length; i += 2) {
            if((arrayCache[i] & 1) == 1) {
                int packedVal = (arrayCache[i - 1] << 10) | arrayCache[i];
                packedVal >>= 1;
                int tempThreatLevel = 0b111_1111 & packedVal;
                packedVal >>= 7;
                int y = 0b11_1111 & packedVal;
                packedVal >>= 6;
                int x = 0b11_1111 & packedVal;

                if(x == location.x && y == location.y) {
                   threatLevel = Math.max(threatLevel, tempThreatLevel);
                   int toWrite = arrayCache[i];
                   toWrite &= ~0xFF;
                   toWrite |= (threatLevel << 1) | 1;
                   rc.writeSharedArray(i, toWrite);
                   return;
                }
            }
        }

        //find empty index
        for(int i = 1; i < 20; i += 2) {
            int index2 = arrayCache[i];
            if((index2 & 1) == 0) {
                int packedVal = location.x;
                packedVal <<= 6;
                packedVal |= location.y;
                packedVal <<= 7;
                packedVal |= 0b111_1111 & threatLevel;
                packedVal <<= 1;
                packedVal |= 1;

                rc.writeSharedArray(i, packedVal & GameConstants.COMM_ARRAY_MAX_VALUE);
                packedVal >>= 10;
                rc.writeSharedArray(i - 1, packedVal & GameConstants.COMM_ARRAY_MAX_VALUE);
                return;
            }
        }
    }

    public static MapLocation getBestFormationLocation() throws GameActionException {
        CheeseMineInfo[] info = Communicator.getCheeseMines();
        if(info.length == 0) {
            return null;
        }

        double bestScore = Double.MIN_VALUE;
        int bestIndex = 0;
        boolean tooClose;
        int averageDist;

        RatKingInfo[] ratKings = Communicator.getRatKings();

        int numRatKings = ratKings.length;

        double effectiveMultiplier = THREAT_LEVEL_MULTIPLIER / numRatKings;

        MapLocation center = new MapLocation(MAP_WIDTH / 2, MAP_HEIGHT / 2);
        //int maxDistSq = center.distanceSquaredTo(new MapLocation(MAP_WIDTH - 1, MAP_HEIGHT - 1));

        for(int i = 0; i < info.length; i++) {
            if(info[i].threatLevel() > 60) {
                continue;
            }
            averageDist = 0;
            tooClose = false;
            for (RatKingInfo ratKing : ratKings) {
                int dist = ratKing.loc().distanceSquaredTo(info[i].location());
                if (dist <= 8) {
                    tooClose = true;
                    break;
                }
                averageDist += dist;
            }
            if (tooClose) continue;
            averageDist /= ratKings.length;
            double score = averageDist
                    + effectiveMultiplier * (128.0 / (info[i].threatLevel() + 1));
                  //  + effectiveMultiplier * Math.min(1, numRatKings - 1) * (1 - (info[i].location().distanceSquaredTo(center) / maxDistSq));
            //System.out.println("Num: " + numRatKings + ", score: " + score + ", dist contrib:" + averageDist + "\n, threat contrib: " + effectiveMultiplier * (128.0 / (info[i].threatLevel() + 1)));
            if(score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        return info[bestIndex].location();
    }

    public static CheeseMineInfo[] getCheeseMines() throws GameActionException {
        // Original getCheeseMines (kept for reference)
//        if(cheeseMines != null) {
//            return cheeseMines;
//        }
//
//        int size = 0;
//        for(int i = 1; i < 20; i += 2) {
//            int index2 = rc.readSharedArray(i);
//            if((index2 & 1) == 1) {
//                size++;
//            }
//        }
//
//        CheeseMineInfo[] mines = new CheeseMineInfo[size];
//        int cur = 0;
//        for(int i = 1; i < 20; i += 2) {
//            int index2 = rc.readSharedArray(i);
//            if((index2 & 1) == 1) {
//                int index1 = rc.readSharedArray(i - 1);
//                int packedVal = (index1 << 10) | index2;
//
//                packedVal >>= 1;
//                int threatLevel = 0b111_1111 & packedVal;
//                packedVal >>= 7;
//                int y = 0b11_1111 & packedVal;
//                packedVal >>= 6;
//                int x = 0b11_1111 & packedVal;
//
//                mines[cur] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
//                cur++;
//            }
//        }
//        cheeseMines = mines;
//        return mines;

        if(cheeseMines != null) {
            return cheeseMines;
        }

        int a0 = rc.readSharedArray(0);
        int a1 = rc.readSharedArray(1);
        int a2 = rc.readSharedArray(2);
        int a3 = rc.readSharedArray(3);
        int a4 = rc.readSharedArray(4);
        int a5 = rc.readSharedArray(5);
        int a6 = rc.readSharedArray(6);
        int a7 = rc.readSharedArray(7);
        int a8 = rc.readSharedArray(8);
        int a9 = rc.readSharedArray(9);
        int a10 = rc.readSharedArray(10);
        int a11 = rc.readSharedArray(11);
        int a12 = rc.readSharedArray(12);
        int a13 = rc.readSharedArray(13);
        int a14 = rc.readSharedArray(14);
        int a15 = rc.readSharedArray(15);
        int a16 = rc.readSharedArray(16);
        int a17 = rc.readSharedArray(17);
        int a18 = rc.readSharedArray(18);
        int a19 = rc.readSharedArray(19);

        int size = (a1 & 1) + (a3 & 1) + (a5 & 1) + (a7 & 1) + (a9 & 1)
                + (a11 & 1) + (a13 & 1) + (a15 & 1) + (a17 & 1) + (a19 & 1);

        if (size == 0) {
            cheeseMines = new CheeseMineInfo[0];
            return cheeseMines;
        }

        CheeseMineInfo[] mines = new CheeseMineInfo[size];
        int cur = 0;

        if((a1 & 1) == 1) {
            int packedVal = (a0 << 10) | a1;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a3 & 1) == 1) {
            int packedVal = (a2 << 10) | a3;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a5 & 1) == 1) {
            int packedVal = (a4 << 10) | a5;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a7 & 1) == 1) {
            int packedVal = (a6 << 10) | a7;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a9 & 1) == 1) {
            int packedVal = (a8 << 10) | a9;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a11 & 1) == 1) {
            int packedVal = (a10 << 10) | a11;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a13 & 1) == 1) {
            int packedVal = (a12 << 10) | a13;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a15 & 1) == 1) {
            int packedVal = (a14 << 10) | a15;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a17 & 1) == 1) {
            int packedVal = (a16 << 10) | a17;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }
        if((a19 & 1) == 1) {
            int packedVal = (a18 << 10) | a19;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            mines[cur++] = new CheeseMineInfo(new MapLocation(x, y), threatLevel);
        }

        cheeseMines = mines;
        return mines;
    }

    public static FastIterableLocMap getCheeseMinesMap(FastIterableLocMap map) throws GameActionException {
        // Original getCheeseMinesMap (kept for reference)
//        map.clear();
//        int[] cache = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};
//        for(int i = 1; i < 20; i += 2) {
//            int index2 = 0;
//            if (cache[i] != -1) index2 = cache[i];
//            else {
//                index2 = rc.readSharedArray(i);
//                cache[i] = index2;
//            }
//            if((index2 & 1) == 1) {
//                int index1;
//                if (cache[i-1] != -1) {
//                    index1 = cache[i-1];
//                }
//                else {
//                    index1 = rc.readSharedArray(i - 1);
//                    cache[i - 1] = index1;
//                }
//                int packedVal = (index1 << 10) | index2;
//
//                packedVal >>= 1;
//                int threatLevel = 0b111_1111 & packedVal;
//                packedVal >>= 7;
//                int y = 0b11_1111 & packedVal;
//                packedVal >>= 6;
//                int x = 0b11_1111 & packedVal;
//
//                MapLocation mine = new MapLocation(x, y);
//
//                map.put(mine, threatLevel);
//                
//                SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
//
//            }
//        }
//
//        //map.updateIterable();
//        return map;

        map.clear();

        int a0 = rc.readSharedArray(0);
        int a1 = rc.readSharedArray(1);
        int a2 = rc.readSharedArray(2);
        int a3 = rc.readSharedArray(3);
        int a4 = rc.readSharedArray(4);
        int a5 = rc.readSharedArray(5);
        int a6 = rc.readSharedArray(6);
        int a7 = rc.readSharedArray(7);
        int a8 = rc.readSharedArray(8);
        int a9 = rc.readSharedArray(9);
        int a10 = rc.readSharedArray(10);
        int a11 = rc.readSharedArray(11);
        int a12 = rc.readSharedArray(12);
        int a13 = rc.readSharedArray(13);
        int a14 = rc.readSharedArray(14);
        int a15 = rc.readSharedArray(15);
        int a16 = rc.readSharedArray(16);
        int a17 = rc.readSharedArray(17);
        int a18 = rc.readSharedArray(18);
        int a19 = rc.readSharedArray(19);

        if((a1 & 1) == 1) {
            int packedVal = (a0 << 10) | a1;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a3 & 1) == 1) {
            int packedVal = (a2 << 10) | a3;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a5 & 1) == 1) {
            int packedVal = (a4 << 10) | a5;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a7 & 1) == 1) {
            int packedVal = (a6 << 10) | a7;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a9 & 1) == 1) {
            int packedVal = (a8 << 10) | a9;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a11 & 1) == 1) {
            int packedVal = (a10 << 10) | a11;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a13 & 1) == 1) {
            int packedVal = (a12 << 10) | a13;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a15 & 1) == 1) {
            int packedVal = (a14 << 10) | a15;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a17 & 1) == 1) {
            int packedVal = (a16 << 10) | a17;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }
        if((a19 & 1) == 1) {
            int packedVal = (a18 << 10) | a19;
            int threatLevel = (packedVal >> 1) & 0x7F;
            int y = (packedVal >> 8) & 0x3F;
            int x = (packedVal >> 14) & 0x3F;
            MapLocation mine = new MapLocation(x, y);
            map.put(mine, threatLevel);
            SymmetryManager.seenMines.put(mine, Math.max(threatLevel, SymmetryManager.seenMines.get(mine, 0)));
        }

        return map;
    }

    public static CheeseMineSqueakInfo findMineToStore(FastIterableLocMap cheeseMinesInArray) throws GameActionException {

        if (cheeseMinesInArray == null) cheeseMinesInArray = new FastIterableLocMap(45);
        //iterate through all mines and see if we have any not in the shared array
        //iterate through all mines and see if we have any updates
        //FastIterableLocMap cheeseMinesInArray = getCheeseMinesMap(map);
        SymmetryManager.seenMines.updateIterable();
        for(int i = 0; i < SymmetryManager.seenMines.size; i++) {
            MapLocation cur = SymmetryManager.seenMines.getKey(i);
            if(!cheeseMinesInArray.contains(cur)) {
                return new CheeseMineSqueakInfo(cur, SymmetryManager.seenMines.get(cur, 0));
            }
        }

       // List<CheeseMineSqueakInfo> possibleReturn = new ArrayList<>();
        for(int i = 0; i < SymmetryManager.seenMines.size; i++) {
            MapLocation cur = SymmetryManager.seenMines.getKey(i);
            int curVal = SymmetryManager.seenMines.get(cur, 0);
            if(curVal > cheeseMinesInArray.get(cur, 0)) {
                //possibleReturn.add(new CheeseMineSqueakInfo(cur, Math.min(127, SymmetryManager.seenMines.get(cur, 0))));
                return new CheeseMineSqueakInfo(cur, Math.min(127, curVal));
            }
        }
        return null;
        // if(!possibleReturn.isEmpty()) {
        //     return possibleReturn.get(FastMath.randBound(possibleReturn.size()));
        //    // return possibleReturn.get(rng.nextInt(possibleReturn.size()));
        // } else {
        //     return null;
        // }
    }

    public static MapLocation getSafeMineAverageLocation() throws GameActionException {
        CheeseMineInfo[] mines = getCheeseMines();
        RatKingInfo[] ratKings = getRatKings();

        int totalX = 0;
        int totalY = 0;
        int count = 0;
        for(int i = 0; i < mines.length; i++) {
            boolean skip = false;
            for(RatKingInfo ratKing : ratKings) {
                if(!ratKing.loc().equals(RatKing.currentLocation) &&
                        ratKing.loc().isWithinDistanceSquared(mines[i].location(), 25)) {
                    skip = true;
                    break;
                }
            }
            if(skip) continue;

            if(mines[i].threatLevel() > 20) {
                continue;
            }

            totalX += mines[i].location().x;
            totalY += mines[i].location().y;
            count++;
        }

        if(count != 0) {
            return new MapLocation(totalX / count, totalY / count);
        } else {
            return null;
        }
    }

    public static void sendSqueak(SqueakInfo info) {
        int message = switch (info) {
            case EnemyRatKingSqueakInfo erk -> encodeEnemyRatKing(erk);
            case NearbyCatSqueakInfo cat -> encodeNearbyCat(cat);
            case SymmetrySqueakInfo sym -> encodeSymmetry(sym);
            case PickupSqueakInfo pickup -> encodePickup(pickup);
            case FormRatKingSqueakInfo form -> encodeFormRatKing(form);
            case PresenceSqueakInfo presence -> encodePresence(presence);
            case CheeseMineSqueakInfo mine -> encodeMine(mine);
            default -> 0;
        };

        rc.squeak(message);
    }

    // Original getAllSqueaks(int roundBuffer) implementation:
    // /*
    //     public static Squeak[] getAllSqueaks(int roundBuffer) {
    //         int curRound = rc.getRoundNum();
    //         Message[] messages = rc.readSqueaks(-1);
    //         //System.out.println(messages.length);
    //         Squeak[] squeaks = new Squeak[messages.length];
    //         for(int i = 0; i < messages.length; i++) {
    //             if (curRound - messages[i].getRound() <= 1)
    //                 squeaks[i] = decodeSqueak(messages[i]);
    //         }
    //         return squeaks;
    //     }
    // */
    public static Squeak[] getAllSqueaks(int roundBuffer) {
        int curRound = rc.getRoundNum();
        Message[] messages = rc.readSqueaks(-1);
        switch (messages.length) {
            case 21 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 22 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 23 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 24 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 25 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 26 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 27 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 28 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 29 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 30 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 31 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 32 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 33 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 34 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 35 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 36 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 37 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 38 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 39 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 40 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 41 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 42 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 43 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 44 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 45 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 46 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 47 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[46].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[46]); else return squeaks;
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[46] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 48 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[47].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[47]); else return squeaks;
                if (curRound - messages[46].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[46]); else return squeaks;
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[46] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[47] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 49 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[48].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[48]); else return squeaks;
                if (curRound - messages[47].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[47]); else return squeaks;
                if (curRound - messages[46].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[46]); else return squeaks;
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[46] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[47] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[48] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 50 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[49].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[49]); else return squeaks;
                if (curRound - messages[48].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[48]); else return squeaks;
                if (curRound - messages[47].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[47]); else return squeaks;
                if (curRound - messages[46].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[46]); else return squeaks;
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[46] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[47] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[48] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[49] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 51 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[50].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[50]); else return squeaks;
                if (curRound - messages[49].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[49]); else return squeaks;
                if (curRound - messages[48].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[48]); else return squeaks;
                if (curRound - messages[47].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[47]); else return squeaks;
                if (curRound - messages[46].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[46]); else return squeaks;
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[46] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[47] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[48] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[49] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[50] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            case 52 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                if (curRound - messages[51].getRound() <= roundBuffer) squeaks[0] = decodeSqueak(messages[51]); else return squeaks;
                if (curRound - messages[50].getRound() <= roundBuffer) squeaks[1] = decodeSqueak(messages[50]); else return squeaks;
                if (curRound - messages[49].getRound() <= roundBuffer) squeaks[2] = decodeSqueak(messages[49]); else return squeaks;
                if (curRound - messages[48].getRound() <= roundBuffer) squeaks[3] = decodeSqueak(messages[48]); else return squeaks;
                if (curRound - messages[47].getRound() <= roundBuffer) squeaks[4] = decodeSqueak(messages[47]); else return squeaks;
                if (curRound - messages[46].getRound() <= roundBuffer) squeaks[5] = decodeSqueak(messages[46]); else return squeaks;
                if (curRound - messages[45].getRound() <= roundBuffer) squeaks[6] = decodeSqueak(messages[45]); else return squeaks;
                if (curRound - messages[44].getRound() <= roundBuffer) squeaks[7] = decodeSqueak(messages[44]); else return squeaks;
                if (curRound - messages[43].getRound() <= roundBuffer) squeaks[8] = decodeSqueak(messages[43]); else return squeaks;
                if (curRound - messages[42].getRound() <= roundBuffer) squeaks[9] = decodeSqueak(messages[42]); else return squeaks;
                if (curRound - messages[41].getRound() <= roundBuffer) squeaks[10] = decodeSqueak(messages[41]); else return squeaks;
                if (curRound - messages[40].getRound() <= roundBuffer) squeaks[11] = decodeSqueak(messages[40]); else return squeaks;
                if (curRound - messages[39].getRound() <= roundBuffer) squeaks[12] = decodeSqueak(messages[39]); else return squeaks;
                if (curRound - messages[38].getRound() <= roundBuffer) squeaks[13] = decodeSqueak(messages[38]); else return squeaks;
                if (curRound - messages[37].getRound() <= roundBuffer) squeaks[14] = decodeSqueak(messages[37]); else return squeaks;
                if (curRound - messages[36].getRound() <= roundBuffer) squeaks[15] = decodeSqueak(messages[36]); else return squeaks;
                if (curRound - messages[35].getRound() <= roundBuffer) squeaks[16] = decodeSqueak(messages[35]); else return squeaks;
                if (curRound - messages[34].getRound() <= roundBuffer) squeaks[17] = decodeSqueak(messages[34]); else return squeaks;
                if (curRound - messages[33].getRound() <= roundBuffer) squeaks[18] = decodeSqueak(messages[33]); else return squeaks;
                if (curRound - messages[32].getRound() <= roundBuffer) squeaks[19] = decodeSqueak(messages[32]); else return squeaks;
                if (curRound - messages[31].getRound() <= roundBuffer) squeaks[20] = decodeSqueak(messages[31]); else return squeaks;
                if (curRound - messages[30].getRound() <= roundBuffer) squeaks[21] = decodeSqueak(messages[30]); else return squeaks;
                if (curRound - messages[29].getRound() <= roundBuffer) squeaks[22] = decodeSqueak(messages[29]); else return squeaks;
                if (curRound - messages[28].getRound() <= roundBuffer) squeaks[23] = decodeSqueak(messages[28]); else return squeaks;
                if (curRound - messages[27].getRound() <= roundBuffer) squeaks[24] = decodeSqueak(messages[27]); else return squeaks;
                if (curRound - messages[26].getRound() <= roundBuffer) squeaks[25] = decodeSqueak(messages[26]); else return squeaks;
                if (curRound - messages[25].getRound() <= roundBuffer) squeaks[26] = decodeSqueak(messages[25]); else return squeaks;
                if (curRound - messages[24].getRound() <= roundBuffer) squeaks[27] = decodeSqueak(messages[24]); else return squeaks;
                if (curRound - messages[23].getRound() <= roundBuffer) squeaks[28] = decodeSqueak(messages[23]); else return squeaks;
                if (curRound - messages[22].getRound() <= roundBuffer) squeaks[29] = decodeSqueak(messages[22]); else return squeaks;
                if (curRound - messages[21].getRound() <= roundBuffer) squeaks[30] = decodeSqueak(messages[21]); else return squeaks;
                if (curRound - messages[20].getRound() <= roundBuffer) squeaks[31] = decodeSqueak(messages[20]); else return squeaks;
                if (curRound - messages[19].getRound() <= roundBuffer) squeaks[32] = decodeSqueak(messages[19]); else return squeaks;
                if (curRound - messages[18].getRound() <= roundBuffer) squeaks[33] = decodeSqueak(messages[18]); else return squeaks;
                if (curRound - messages[17].getRound() <= roundBuffer) squeaks[34] = decodeSqueak(messages[17]); else return squeaks;
                if (curRound - messages[16].getRound() <= roundBuffer) squeaks[35] = decodeSqueak(messages[16]); else return squeaks;
                if (curRound - messages[15].getRound() <= roundBuffer) squeaks[36] = decodeSqueak(messages[15]); else return squeaks;
                if (curRound - messages[14].getRound() <= roundBuffer) squeaks[37] = decodeSqueak(messages[14]); else return squeaks;
                if (curRound - messages[13].getRound() <= roundBuffer) squeaks[38] = decodeSqueak(messages[13]); else return squeaks;
                if (curRound - messages[12].getRound() <= roundBuffer) squeaks[39] = decodeSqueak(messages[12]); else return squeaks;
                if (curRound - messages[11].getRound() <= roundBuffer) squeaks[40] = decodeSqueak(messages[11]); else return squeaks;
                if (curRound - messages[10].getRound() <= roundBuffer) squeaks[41] = decodeSqueak(messages[10]); else return squeaks;
                if (curRound - messages[9].getRound() <= roundBuffer) squeaks[42] = decodeSqueak(messages[9]); else return squeaks;
                if (curRound - messages[8].getRound() <= roundBuffer) squeaks[43] = decodeSqueak(messages[8]); else return squeaks;
                if (curRound - messages[7].getRound() <= roundBuffer) squeaks[44] = decodeSqueak(messages[7]); else return squeaks;
                if (curRound - messages[6].getRound() <= roundBuffer) squeaks[45] = decodeSqueak(messages[6]); else return squeaks;
                if (curRound - messages[5].getRound() <= roundBuffer) squeaks[46] = decodeSqueak(messages[5]); else return squeaks;
                if (curRound - messages[4].getRound() <= roundBuffer) squeaks[47] = decodeSqueak(messages[4]); else return squeaks;
                if (curRound - messages[3].getRound() <= roundBuffer) squeaks[48] = decodeSqueak(messages[3]); else return squeaks;
                if (curRound - messages[2].getRound() <= roundBuffer) squeaks[49] = decodeSqueak(messages[2]); else return squeaks;
                if (curRound - messages[1].getRound() <= roundBuffer) squeaks[50] = decodeSqueak(messages[1]); else return squeaks;
                if (curRound - messages[0].getRound() <= roundBuffer) squeaks[51] = decodeSqueak(messages[0]); else return squeaks;
                return squeaks;
            }
            default -> {
                Squeak[] squeaks = new Squeak[messages.length];
                for (int mi = messages.length - 1, si = 0; mi >= 0; mi--, si++) {
                    if (curRound - messages[mi].getRound() <= roundBuffer) squeaks[si] = decodeSqueak(messages[mi]); else return squeaks;
                }
                return squeaks;
            }
        }
    }
    // Original getAllSqueaks() implementation:
    // /*
    //     public static Squeak[] getAllSqueaks() {
    //         Message[] messages = rc.readSqueaks(-1);
    //         Squeak[] squeaks = new Squeak[messages.length];
    //         for(int i = 0; i < messages.length; i++) {
    //             squeaks[i] = decodeSqueak(messages[i]);
    //         }
    //         return squeaks;
    //     }
    // */
    public static Squeak[] getAllSqueaks() {
        Message[] messages = rc.readSqueaks(-1);
        switch (messages.length) {
            case 1 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                return squeaks;
            }
            case 2 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                return squeaks;
            }
            case 3 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                return squeaks;
            }
            case 4 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                return squeaks;
            }
            case 5 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                return squeaks;
            }
            case 6 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                return squeaks;
            }
            case 7 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                return squeaks;
            }
            case 8 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                return squeaks;
            }
            case 9 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                return squeaks;
            }
            case 10 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                return squeaks;
            }
            case 11 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                return squeaks;
            }
            case 12 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                return squeaks;
            }
            case 13 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                return squeaks;
            }
            case 14 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                return squeaks;
            }
            case 15 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                return squeaks;
            }
            case 16 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                return squeaks;
            }
            case 17 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                return squeaks;
            }
            case 18 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                return squeaks;
            }
            case 19 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                return squeaks;
            }
            case 20 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                return squeaks;
            }
            case 21 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                return squeaks;
            }
            case 22 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                return squeaks;
            }
            case 23 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                return squeaks;
            }
            case 24 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                return squeaks;
            }
            case 25 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                return squeaks;
            }
            case 26 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                return squeaks;
            }
            case 27 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                return squeaks;
            }
            case 28 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                return squeaks;
            }
            case 29 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                return squeaks;
            }
            case 30 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                return squeaks;
            }
            case 31 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                return squeaks;
            }
            case 32 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                return squeaks;
            }
            case 33 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                return squeaks;
            }
            case 34 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                return squeaks;
            }
            case 35 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                return squeaks;
            }
            case 36 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                return squeaks;
            }
            case 37 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                return squeaks;
            }
            case 38 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                return squeaks;
            }
            case 39 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                return squeaks;
            }
            case 40 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                return squeaks;
            }
            case 41 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                return squeaks;
            }
            case 42 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                return squeaks;
            }
            case 43 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                return squeaks;
            }
            case 44 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                return squeaks;
            }
            case 45 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                squeaks[44] = decodeSqueak(messages[44]);
                return squeaks;
            }
            case 46 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                squeaks[44] = decodeSqueak(messages[44]);
                squeaks[45] = decodeSqueak(messages[45]);
                return squeaks;
            }
            case 47 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                squeaks[44] = decodeSqueak(messages[44]);
                squeaks[45] = decodeSqueak(messages[45]);
                squeaks[46] = decodeSqueak(messages[46]);
                return squeaks;
            }
            case 48 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                squeaks[44] = decodeSqueak(messages[44]);
                squeaks[45] = decodeSqueak(messages[45]);
                squeaks[46] = decodeSqueak(messages[46]);
                squeaks[47] = decodeSqueak(messages[47]);
                return squeaks;
            }
            case 49 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                squeaks[44] = decodeSqueak(messages[44]);
                squeaks[45] = decodeSqueak(messages[45]);
                squeaks[46] = decodeSqueak(messages[46]);
                squeaks[47] = decodeSqueak(messages[47]);
                squeaks[48] = decodeSqueak(messages[48]);
                return squeaks;
            }
            case 50 -> {
                Squeak[] squeaks = new Squeak[messages.length];
                squeaks[0] = decodeSqueak(messages[0]);
                squeaks[1] = decodeSqueak(messages[1]);
                squeaks[2] = decodeSqueak(messages[2]);
                squeaks[3] = decodeSqueak(messages[3]);
                squeaks[4] = decodeSqueak(messages[4]);
                squeaks[5] = decodeSqueak(messages[5]);
                squeaks[6] = decodeSqueak(messages[6]);
                squeaks[7] = decodeSqueak(messages[7]);
                squeaks[8] = decodeSqueak(messages[8]);
                squeaks[9] = decodeSqueak(messages[9]);
                squeaks[10] = decodeSqueak(messages[10]);
                squeaks[11] = decodeSqueak(messages[11]);
                squeaks[12] = decodeSqueak(messages[12]);
                squeaks[13] = decodeSqueak(messages[13]);
                squeaks[14] = decodeSqueak(messages[14]);
                squeaks[15] = decodeSqueak(messages[15]);
                squeaks[16] = decodeSqueak(messages[16]);
                squeaks[17] = decodeSqueak(messages[17]);
                squeaks[18] = decodeSqueak(messages[18]);
                squeaks[19] = decodeSqueak(messages[19]);
                squeaks[20] = decodeSqueak(messages[20]);
                squeaks[21] = decodeSqueak(messages[21]);
                squeaks[22] = decodeSqueak(messages[22]);
                squeaks[23] = decodeSqueak(messages[23]);
                squeaks[24] = decodeSqueak(messages[24]);
                squeaks[25] = decodeSqueak(messages[25]);
                squeaks[26] = decodeSqueak(messages[26]);
                squeaks[27] = decodeSqueak(messages[27]);
                squeaks[28] = decodeSqueak(messages[28]);
                squeaks[29] = decodeSqueak(messages[29]);
                squeaks[30] = decodeSqueak(messages[30]);
                squeaks[31] = decodeSqueak(messages[31]);
                squeaks[32] = decodeSqueak(messages[32]);
                squeaks[33] = decodeSqueak(messages[33]);
                squeaks[34] = decodeSqueak(messages[34]);
                squeaks[35] = decodeSqueak(messages[35]);
                squeaks[36] = decodeSqueak(messages[36]);
                squeaks[37] = decodeSqueak(messages[37]);
                squeaks[38] = decodeSqueak(messages[38]);
                squeaks[39] = decodeSqueak(messages[39]);
                squeaks[40] = decodeSqueak(messages[40]);
                squeaks[41] = decodeSqueak(messages[41]);
                squeaks[42] = decodeSqueak(messages[42]);
                squeaks[43] = decodeSqueak(messages[43]);
                squeaks[44] = decodeSqueak(messages[44]);
                squeaks[45] = decodeSqueak(messages[45]);
                squeaks[46] = decodeSqueak(messages[46]);
                squeaks[47] = decodeSqueak(messages[47]);
                squeaks[48] = decodeSqueak(messages[48]);
                squeaks[49] = decodeSqueak(messages[49]);
                return squeaks;
            }
            default -> {
                Squeak[] squeaks = new Squeak[messages.length];
                for(int i = 0; i < messages.length; i++) {
                    squeaks[i] = decodeSqueak(messages[i]);
                }
                return squeaks;
            }
        }
    }

    public static Squeak getMostRecentSqueakOfType(Class<? extends SqueakInfo> targetType) {
        Squeak mostRecent = null;
        Message[] messages = rc.readSqueaks(-1);
        for(Message message : messages) {
            Squeak squeak = decodeSqueak(message);
            SqueakInfo info = squeak.squeakInfo;
            if (info == null || info.getClass() != targetType) {
                continue;
            }
            if (mostRecent == null || squeak.round > mostRecent.round) {
                mostRecent = squeak;
            }
        }
        return mostRecent;

    }

    public static Squeak getMostRecentValidCatSqueak() {
        Class<? extends SqueakInfo> targetType  = NearbyCatSqueakInfo.class;
        Squeak mostRecent = null;
        Message[] messages = rc.readSqueaks(-1);
        for(Message message : messages) {
            Squeak squeak = decodeSqueak(message);
            SqueakInfo info = squeak.squeakInfo;
            if (info == null || info.getClass() != targetType) {
                continue;
            }
            NearbyCatSqueakInfo catInfo = (NearbyCatSqueakInfo) squeak.squeakInfo;
            if ((mostRecent == null || squeak.round > mostRecent.round) && catInfo.cat()) {
                mostRecent = squeak;
            }
        }
        return mostRecent;

    }

    // public static Squeak getMostRecentSqueakOfType(Class<? extends SqueakInfo> targetType) {
    //     Squeak mostRecent = null;
    //     for(Squeak squeak : getAllSqueaks()) {
    //         //System.out.println(squeak);
    //         SqueakInfo info = squeak.squeakInfo;
    //         if (info == null || info.getClass() != targetType) {
    //             continue;
    //         }

    //         if (mostRecent == null || squeak.round > mostRecent.round) {
    //             mostRecent = squeak;
    //         }
    //     }

    //     return mostRecent;
    // }

    public static Squeak[] getSqueaksOfType(Class<? extends SqueakInfo> targetType) {
        int size = 0;
        Squeak[] squeaks = getAllSqueaks();
        for(Squeak squeak : squeaks) {
            if(squeak.squeakInfo.getClass() == targetType) {
                size++;
            }
        }
        int i = 0;
        Squeak[] toReturn = new Squeak[size];
        for(Squeak squeak : squeaks) {
            if(squeak.squeakInfo.getClass() == targetType) {
                toReturn[i] = squeak;
                i++;
            }
        }
        return toReturn;
    }

    public static Squeak decodeSqueak(Message message) {
        int bytes = message.getBytes();
        SqueakInfo info = null;
        switch (0b111 & bytes) {
            case 1 -> info = decodeEnemyRatKing(bytes);
            case 2 -> info = decodeNearbyCat(bytes);
            case 3 -> info = decodeSymmetry(bytes);
            case 4 -> info = decodePickup(bytes);
            case 5 -> info = decodeFormRatKing(bytes);
            case 6 -> info = decodePresence(bytes);
            case 7 -> info = decodeMine(bytes);
        }

        return new Squeak(message.getSource(), message.getSenderID(), message.getRound(), info);
    }

    private static int encodeEnemyRatKing(EnemyRatKingSqueakInfo info) {
        return ((info.location().x & 0b11_1111) << 17) |
                ((info.location().y & 0b11_1111) << 11) |
                ((info.health() & 0b1_1111_1111) << 2) |
                SqueakTypes.ENEMY_RAT_KING.ordinal();
    }

    private static EnemyRatKingSqueakInfo decodeEnemyRatKing(int info) {
        int health = (info >>> 2) & 0b1_1111_1111;
        int y = (info >>> 11) & 0b11_1111;
        int x = (info >>> 17) & 0b11_1111;
        return new EnemyRatKingSqueakInfo(new MapLocation(x, y), health);
    }

    private static int dirToIdxNoCenter(Direction dir) {
        if (dir == null) return 0;
        for (int i = 0; i < DIRS_NO_CENTER.length; i++) {
            if (DIRS_NO_CENTER[i] == dir) {
                return i;
            }
        }
        return 0;
    }

    private static int encodeNearbyCat(NearbyCatSqueakInfo info) {
        int dirBits = dirToIdxNoCenter(info.direction());
        return ((info.location().x & 0b11_1111) << 13) |
                ((info.location().y & 0b11_1111) << 7) |
                ((dirBits & 0b111) << 4) |
                ((info.cat() ? 1 : 0) << 3) |
                SqueakTypes.NEARBY_CAT.ordinal();
    }

    private static NearbyCatSqueakInfo decodeNearbyCat(int info) {
        boolean cat = ((info >>> 3) & 0b1) == 1;
        int dirIdx = (info >>> 4) & 0b111;
        int y = (info >>> 7) & 0b11_1111;
        int x = (info >>> 13) & 0b11_1111;
        Direction direction = DIRS_NO_CENTER[dirIdx % DIRS_NO_CENTER.length];
        return new NearbyCatSqueakInfo(new MapLocation(x, y), cat, direction);
    }

    private static int encodeMine(CheeseMineSqueakInfo info) {
        return ((info.location().x & 0b11_1111) << 16) |
                ((info.location().y & 0b11_1111) << 10) |
                ((info.threatLevel() & 0b111_1111) << 3) |
                SqueakTypes.CHEESE_MINE.ordinal();
    }

    private static CheeseMineSqueakInfo decodeMine(int info) {
        int threatLevel = (info >>> 3) & 0b111_1111;
        int y = (info >>> 10) & 0b11_1111;
        int x = (info >>> 16) & 0b11_1111;
        return new CheeseMineSqueakInfo(new MapLocation(x, y), threatLevel);
    }

    private static int encodeSymmetry(SymmetrySqueakInfo info) {
        return ((info.symmetry() & 0b11_1111) << 9) |
                SqueakTypes.SYMMETRY.ordinal();
    }

    private static SymmetrySqueakInfo decodeSymmetry(int info) {
        int symmetry = (info >>> 9) & 0b11_1111;
        return new SymmetrySqueakInfo(symmetry);
    }

    private static int encodePickup(PickupSqueakInfo info) {
        return SqueakTypes.PICKUP.ordinal();
    }

    private static PickupSqueakInfo decodePickup(int info) {
        return new PickupSqueakInfo();
    }

    private static int encodePresence(PresenceSqueakInfo info) {
        return ((info.health() & 0b111_1111) << 25) |
                ((info.nearestEnemy().x & 0b11_1111) << 19) |
                ((info.nearestEnemy().y & 0b11_1111) << 13) |
                ((info.enemyDir().ordinal() & 0b111) << 10) |
                ((info.enemyHealth() & 0b111_1111) << 3) |
                SqueakTypes.PRESENCE.ordinal();
    }

    private static PresenceSqueakInfo decodePresence(int info) {
        int enemyHealth = (info >>> 3) & 0b111_1111;
        Direction enemyDir = DIRS[(info >>> 10) & 0b111];
        int y = (info >>> 13) & 0b11_1111;
        int x = (info >>> 19) & 0b11_1111;
        int health = (info >>> 25) & 0b111_1111;
        return new PresenceSqueakInfo(health, new MapLocation(x, y), enemyDir, enemyHealth);
    }

    private static int encodeFormRatKing(FormRatKingSqueakInfo info) {
        return ((info.location().x & 0b11_1111) << 15) |
                ((info.location().y & 0b11_1111) << 9) |
                ((info.numPresentUnits() & 0b11_1111) << 3) |
                SqueakTypes.FORM_RAT_KING.ordinal();
    }

    private static FormRatKingSqueakInfo decodeFormRatKing(int info) {
        int numUnits = (info >>> 3) & 0b11_1111;
        int y = (info >>> 9) & 0b11_1111;
        int x = (info >>> 15) & 0b11_1111;
        return new FormRatKingSqueakInfo(new MapLocation(x, y), numUnits);
    }

}
