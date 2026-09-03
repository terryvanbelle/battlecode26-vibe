package bench_spaark;

import java.util.*;

import bench_spaark.fast.RobotInfoMap;
import battlecode.common.*;

public class Comms {
    public static final boolean ENABLE_INDICATORS = true;

    // **************** GLOBAL ARRAY ****************
    // Index 0: Symmetry
    // Index 1-5: Rat king current round
    // Index 1-5: Rat king locations
    //            - 2x2 resolution
    // Index 6-10: Rat king starting locations
    //            - 2x2 resolution
    // Index 11-15: Cheese mine locations
    //            - 5x5 resolution
    //            - [!] Value 1023 means no cheese mine
    //            - Each rat king will write a different cheese mine every turn
    // Index 16-20: Cheese mine locations that are closest to the rat king
    // Index 21-25: Target location to form rat king
    // Index 26-40: Cat locations

    // **************** SQUEAKS ****************
    // Bit 0-6: Enemy location
    // Bit 7-14: Enemy ID mod 256
    // Bit 15-20: Enemy HP (cats: HP / 31, rat kings: HP / 5, rats: HP / 2, see encodeNumber and decodeNumber)
    // Bit 21-23: Enemy facing direction
    // Bit 24-25: Enemy type (0 for baby rat, 1 for rat king, 2 for cat, 3 for meant for rat king)
    // Bit 26-28: Self state
    // Bit 29-31: Travelling direction

    public static final int SYMMETRY = 0;
    public static final int RAT_KING_CURR_ROUND = 1;
    public static final int RAT_KING_LOC = 6;
    // public static final int RAT_KING_INIT_LOC = 11;
    public static final int CHEESE_MINE_LOC = 11;
    public static final int CHEESE_MINE_CLOSEST_LOC = 16;
    public static final int FORM_OR_DEFEND_RAT_KING_LOC = 21;
    public static final int CAT_LOC = 31;
    public static final int CAT_CLOSEST_LOC = 46;

    public static final int ENEMY_LOCATION = 0;
    public static final int ENEMY_ID = 7;
    public static final int ENEMY_HP = 15;
    public static final int ENEMY_DIR = 21;
    public static final int SELF_STATE = 24;
    public static final int TRAVELLING_DIR = 27;
    public static final int ENEMY_TYPE = 30;
    
    public static final boolean ENABLE_SQUEAKING = true;

    public static int ratKingID = 0;

    public static int numberOfRatKings = 1;
    public static boolean[] existsRatKing = new boolean[5];
    public static MapLocation[] ratKingLocations = new MapLocation[5];
    public static MapLocation[] ratKingInitLocations = new MapLocation[5];
    public static MapLocation[] formRatKingLocations = new MapLocation[5];
    public static MapLocation[] defendRatKingLocations = new MapLocation[5];
    public static boolean[] defendRatKingFromCat = new boolean[5];
    public static int[] defendNumberOfOpponentRobots = new int[5];

    public static int minNumberOfBabyRats; //lower bound on number of baby rats
    public static int maxNumberOfBabyRats; //upper bound on number of baby rats

    public static int numberOfCats = 0;
    public static int[] catIDs = new int[144];
    public static MapLocation[] catLocations = new MapLocation[144];
    public static int[] catRounds = new int[144];
    public static boolean[] catCritical = new boolean[144];
    public static long[] catSeenLocs = new long[64]; //all locations a cat has been sighted (use to explore away from cats)

    public static int numberOfCriticalInformation = 0;

    // 144 mines
    public static int numberOfMines = 0;
    public static MapLocation[] mineLocs = new MapLocation[144];
    public static boolean[] mineCritical = new boolean[144];
    public static int[][] mineGrid = new int[][] {
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
            {
                    -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            },
    };

    // symmetry detection
    // set bit if its a wall, mine, or we explored it, and use bit operators to
    // check symmetry
    public static long[] wall = new long[60]; // wall[xy.y] |= 1L << xy.x;
    public static long[] mine = new long[60];
    public static long[] explored = new long[60];
    public static boolean[] symmetry = new boolean[] { true, true, true };
    public static boolean[] criticalSymmetry = new boolean[] { false, false, false };
    public static int mostLikelySymmetry = 2;
    public static int numValidSymmetries = 3;
    // 0: horz (the line of symmetry is parallel to the x axis)
    // 1: vert
    // 2: rot

    // stores all mine and mine data

    // basically critical array means this robot found this information, not
    // received through message
    // robot prioritizes critical information to be sent first

    public static void addMine(int source, MapLocation loc) {
        if (mineGrid[loc.y / 5][loc.x / 5] == -1) {
            mineGrid[loc.y / 5][loc.x / 5] = numberOfMines;
            mineLocs[numberOfMines] = loc;
            if (source == -1 && !mineCritical[numberOfMines]) {
                mineCritical[numberOfMines] = true;
                numberOfCriticalInformation++;
            }
            numberOfMines++;
        }
        else if (source != -1) {
            int index = mineGrid[loc.y / 5][loc.x / 5];
            if (mineCritical[index]) {
                mineCritical[index] = false;
                numberOfCriticalInformation--;
            }
        }
    };

    public static void addCat(int source, int id, int round, MapLocation loc) throws Exception {
		switch (loc.y) {
			case 0:
				catSeenLocs[0] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[1] |= 0b111111111 << loc.x - 4;
				catSeenLocs[2] |= 0b111111111 << loc.x - 4;
				catSeenLocs[3] |= 0b111111111 << loc.x - 4;
				catSeenLocs[4] |= 0b1111111 << loc.x - 3;
				catSeenLocs[5] |= 0b1 << loc.x;
				break;
			case 1:
				catSeenLocs[0] |= 0b111111111 << loc.x - 4;
				catSeenLocs[1] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[2] |= 0b111111111 << loc.x - 4;
				catSeenLocs[3] |= 0b111111111 << loc.x - 4;
				catSeenLocs[4] |= 0b111111111 << loc.x - 4;
				catSeenLocs[5] |= 0b1111111 << loc.x - 3;
				catSeenLocs[6] |= 0b1 << loc.x;
				break;
			case 2:
				catSeenLocs[0] |= 0b111111111 << loc.x - 4;
				catSeenLocs[1] |= 0b111111111 << loc.x - 4;
				catSeenLocs[2] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[3] |= 0b111111111 << loc.x - 4;
				catSeenLocs[4] |= 0b111111111 << loc.x - 4;
				catSeenLocs[5] |= 0b111111111 << loc.x - 4;
				catSeenLocs[6] |= 0b1111111 << loc.x - 3;
				catSeenLocs[7] |= 0b1 << loc.x;
				break;
			case 3:
				catSeenLocs[0] |= 0b111111111 << loc.x - 4;
				catSeenLocs[1] |= 0b111111111 << loc.x - 4;
				catSeenLocs[2] |= 0b111111111 << loc.x - 4;
				catSeenLocs[3] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[4] |= 0b111111111 << loc.x - 4;
				catSeenLocs[5] |= 0b111111111 << loc.x - 4;
				catSeenLocs[6] |= 0b111111111 << loc.x - 4;
				catSeenLocs[7] |= 0b1111111 << loc.x - 3;
				catSeenLocs[8] |= 0b1 << loc.x;
				break;
			case 4:
				catSeenLocs[0] |= 0b1111111 << loc.x - 3;
				catSeenLocs[1] |= 0b111111111 << loc.x - 4;
				catSeenLocs[2] |= 0b111111111 << loc.x - 4;
				catSeenLocs[3] |= 0b111111111 << loc.x - 4;
				catSeenLocs[4] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[5] |= 0b111111111 << loc.x - 4;
				catSeenLocs[6] |= 0b111111111 << loc.x - 4;
				catSeenLocs[7] |= 0b111111111 << loc.x - 4;
				catSeenLocs[8] |= 0b1111111 << loc.x - 3;
				catSeenLocs[9] |= 0b1 << loc.x;
				break;
			case 5:
				catSeenLocs[0] |= 0b1 << loc.x;
				catSeenLocs[1] |= 0b1111111 << loc.x - 3;
				catSeenLocs[2] |= 0b111111111 << loc.x - 4;
				catSeenLocs[3] |= 0b111111111 << loc.x - 4;
				catSeenLocs[4] |= 0b111111111 << loc.x - 4;
				catSeenLocs[5] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[6] |= 0b111111111 << loc.x - 4;
				catSeenLocs[7] |= 0b111111111 << loc.x - 4;
				catSeenLocs[8] |= 0b111111111 << loc.x - 4;
				catSeenLocs[9] |= 0b1111111 << loc.x - 3;
				catSeenLocs[10] |= 0b1 << loc.x;
				break;
			case 6:
				catSeenLocs[1] |= 0b1 << loc.x;
				catSeenLocs[2] |= 0b1111111 << loc.x - 3;
				catSeenLocs[3] |= 0b111111111 << loc.x - 4;
				catSeenLocs[4] |= 0b111111111 << loc.x - 4;
				catSeenLocs[5] |= 0b111111111 << loc.x - 4;
				catSeenLocs[6] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[7] |= 0b111111111 << loc.x - 4;
				catSeenLocs[8] |= 0b111111111 << loc.x - 4;
				catSeenLocs[9] |= 0b111111111 << loc.x - 4;
				catSeenLocs[10] |= 0b1111111 << loc.x - 3;
				catSeenLocs[11] |= 0b1 << loc.x;
				break;
			case 7:
				catSeenLocs[2] |= 0b1 << loc.x;
				catSeenLocs[3] |= 0b1111111 << loc.x - 3;
				catSeenLocs[4] |= 0b111111111 << loc.x - 4;
				catSeenLocs[5] |= 0b111111111 << loc.x - 4;
				catSeenLocs[6] |= 0b111111111 << loc.x - 4;
				catSeenLocs[7] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[8] |= 0b111111111 << loc.x - 4;
				catSeenLocs[9] |= 0b111111111 << loc.x - 4;
				catSeenLocs[10] |= 0b111111111 << loc.x - 4;
				catSeenLocs[11] |= 0b1111111 << loc.x - 3;
				catSeenLocs[12] |= 0b1 << loc.x;
				break;
			case 8:
				catSeenLocs[3] |= 0b1 << loc.x;
				catSeenLocs[4] |= 0b1111111 << loc.x - 3;
				catSeenLocs[5] |= 0b111111111 << loc.x - 4;
				catSeenLocs[6] |= 0b111111111 << loc.x - 4;
				catSeenLocs[7] |= 0b111111111 << loc.x - 4;
				catSeenLocs[8] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[9] |= 0b111111111 << loc.x - 4;
				catSeenLocs[10] |= 0b111111111 << loc.x - 4;
				catSeenLocs[11] |= 0b111111111 << loc.x - 4;
				catSeenLocs[12] |= 0b1111111 << loc.x - 3;
				catSeenLocs[13] |= 0b1 << loc.x;
				break;
			case 9:
				catSeenLocs[4] |= 0b1 << loc.x;
				catSeenLocs[5] |= 0b1111111 << loc.x - 3;
				catSeenLocs[6] |= 0b111111111 << loc.x - 4;
				catSeenLocs[7] |= 0b111111111 << loc.x - 4;
				catSeenLocs[8] |= 0b111111111 << loc.x - 4;
				catSeenLocs[9] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[10] |= 0b111111111 << loc.x - 4;
				catSeenLocs[11] |= 0b111111111 << loc.x - 4;
				catSeenLocs[12] |= 0b111111111 << loc.x - 4;
				catSeenLocs[13] |= 0b1111111 << loc.x - 3;
				catSeenLocs[14] |= 0b1 << loc.x;
				break;
			case 10:
				catSeenLocs[5] |= 0b1 << loc.x;
				catSeenLocs[6] |= 0b1111111 << loc.x - 3;
				catSeenLocs[7] |= 0b111111111 << loc.x - 4;
				catSeenLocs[8] |= 0b111111111 << loc.x - 4;
				catSeenLocs[9] |= 0b111111111 << loc.x - 4;
				catSeenLocs[10] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[11] |= 0b111111111 << loc.x - 4;
				catSeenLocs[12] |= 0b111111111 << loc.x - 4;
				catSeenLocs[13] |= 0b111111111 << loc.x - 4;
				catSeenLocs[14] |= 0b1111111 << loc.x - 3;
				catSeenLocs[15] |= 0b1 << loc.x;
				break;
			case 11:
				catSeenLocs[6] |= 0b1 << loc.x;
				catSeenLocs[7] |= 0b1111111 << loc.x - 3;
				catSeenLocs[8] |= 0b111111111 << loc.x - 4;
				catSeenLocs[9] |= 0b111111111 << loc.x - 4;
				catSeenLocs[10] |= 0b111111111 << loc.x - 4;
				catSeenLocs[11] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[12] |= 0b111111111 << loc.x - 4;
				catSeenLocs[13] |= 0b111111111 << loc.x - 4;
				catSeenLocs[14] |= 0b111111111 << loc.x - 4;
				catSeenLocs[15] |= 0b1111111 << loc.x - 3;
				catSeenLocs[16] |= 0b1 << loc.x;
				break;
			case 12:
				catSeenLocs[7] |= 0b1 << loc.x;
				catSeenLocs[8] |= 0b1111111 << loc.x - 3;
				catSeenLocs[9] |= 0b111111111 << loc.x - 4;
				catSeenLocs[10] |= 0b111111111 << loc.x - 4;
				catSeenLocs[11] |= 0b111111111 << loc.x - 4;
				catSeenLocs[12] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[13] |= 0b111111111 << loc.x - 4;
				catSeenLocs[14] |= 0b111111111 << loc.x - 4;
				catSeenLocs[15] |= 0b111111111 << loc.x - 4;
				catSeenLocs[16] |= 0b1111111 << loc.x - 3;
				catSeenLocs[17] |= 0b1 << loc.x;
				break;
			case 13:
				catSeenLocs[8] |= 0b1 << loc.x;
				catSeenLocs[9] |= 0b1111111 << loc.x - 3;
				catSeenLocs[10] |= 0b111111111 << loc.x - 4;
				catSeenLocs[11] |= 0b111111111 << loc.x - 4;
				catSeenLocs[12] |= 0b111111111 << loc.x - 4;
				catSeenLocs[13] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[14] |= 0b111111111 << loc.x - 4;
				catSeenLocs[15] |= 0b111111111 << loc.x - 4;
				catSeenLocs[16] |= 0b111111111 << loc.x - 4;
				catSeenLocs[17] |= 0b1111111 << loc.x - 3;
				catSeenLocs[18] |= 0b1 << loc.x;
				break;
			case 14:
				catSeenLocs[9] |= 0b1 << loc.x;
				catSeenLocs[10] |= 0b1111111 << loc.x - 3;
				catSeenLocs[11] |= 0b111111111 << loc.x - 4;
				catSeenLocs[12] |= 0b111111111 << loc.x - 4;
				catSeenLocs[13] |= 0b111111111 << loc.x - 4;
				catSeenLocs[14] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[15] |= 0b111111111 << loc.x - 4;
				catSeenLocs[16] |= 0b111111111 << loc.x - 4;
				catSeenLocs[17] |= 0b111111111 << loc.x - 4;
				catSeenLocs[18] |= 0b1111111 << loc.x - 3;
				catSeenLocs[19] |= 0b1 << loc.x;
				break;
			case 15:
				catSeenLocs[10] |= 0b1 << loc.x;
				catSeenLocs[11] |= 0b1111111 << loc.x - 3;
				catSeenLocs[12] |= 0b111111111 << loc.x - 4;
				catSeenLocs[13] |= 0b111111111 << loc.x - 4;
				catSeenLocs[14] |= 0b111111111 << loc.x - 4;
				catSeenLocs[15] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[16] |= 0b111111111 << loc.x - 4;
				catSeenLocs[17] |= 0b111111111 << loc.x - 4;
				catSeenLocs[18] |= 0b111111111 << loc.x - 4;
				catSeenLocs[19] |= 0b1111111 << loc.x - 3;
				catSeenLocs[20] |= 0b1 << loc.x;
				break;
			case 16:
				catSeenLocs[11] |= 0b1 << loc.x;
				catSeenLocs[12] |= 0b1111111 << loc.x - 3;
				catSeenLocs[13] |= 0b111111111 << loc.x - 4;
				catSeenLocs[14] |= 0b111111111 << loc.x - 4;
				catSeenLocs[15] |= 0b111111111 << loc.x - 4;
				catSeenLocs[16] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[17] |= 0b111111111 << loc.x - 4;
				catSeenLocs[18] |= 0b111111111 << loc.x - 4;
				catSeenLocs[19] |= 0b111111111 << loc.x - 4;
				catSeenLocs[20] |= 0b1111111 << loc.x - 3;
				catSeenLocs[21] |= 0b1 << loc.x;
				break;
			case 17:
				catSeenLocs[12] |= 0b1 << loc.x;
				catSeenLocs[13] |= 0b1111111 << loc.x - 3;
				catSeenLocs[14] |= 0b111111111 << loc.x - 4;
				catSeenLocs[15] |= 0b111111111 << loc.x - 4;
				catSeenLocs[16] |= 0b111111111 << loc.x - 4;
				catSeenLocs[17] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[18] |= 0b111111111 << loc.x - 4;
				catSeenLocs[19] |= 0b111111111 << loc.x - 4;
				catSeenLocs[20] |= 0b111111111 << loc.x - 4;
				catSeenLocs[21] |= 0b1111111 << loc.x - 3;
				catSeenLocs[22] |= 0b1 << loc.x;
				break;
			case 18:
				catSeenLocs[13] |= 0b1 << loc.x;
				catSeenLocs[14] |= 0b1111111 << loc.x - 3;
				catSeenLocs[15] |= 0b111111111 << loc.x - 4;
				catSeenLocs[16] |= 0b111111111 << loc.x - 4;
				catSeenLocs[17] |= 0b111111111 << loc.x - 4;
				catSeenLocs[18] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[19] |= 0b111111111 << loc.x - 4;
				catSeenLocs[20] |= 0b111111111 << loc.x - 4;
				catSeenLocs[21] |= 0b111111111 << loc.x - 4;
				catSeenLocs[22] |= 0b1111111 << loc.x - 3;
				catSeenLocs[23] |= 0b1 << loc.x;
				break;
			case 19:
				catSeenLocs[14] |= 0b1 << loc.x;
				catSeenLocs[15] |= 0b1111111 << loc.x - 3;
				catSeenLocs[16] |= 0b111111111 << loc.x - 4;
				catSeenLocs[17] |= 0b111111111 << loc.x - 4;
				catSeenLocs[18] |= 0b111111111 << loc.x - 4;
				catSeenLocs[19] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[20] |= 0b111111111 << loc.x - 4;
				catSeenLocs[21] |= 0b111111111 << loc.x - 4;
				catSeenLocs[22] |= 0b111111111 << loc.x - 4;
				catSeenLocs[23] |= 0b1111111 << loc.x - 3;
				catSeenLocs[24] |= 0b1 << loc.x;
				break;
			case 20:
				catSeenLocs[15] |= 0b1 << loc.x;
				catSeenLocs[16] |= 0b1111111 << loc.x - 3;
				catSeenLocs[17] |= 0b111111111 << loc.x - 4;
				catSeenLocs[18] |= 0b111111111 << loc.x - 4;
				catSeenLocs[19] |= 0b111111111 << loc.x - 4;
				catSeenLocs[20] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[21] |= 0b111111111 << loc.x - 4;
				catSeenLocs[22] |= 0b111111111 << loc.x - 4;
				catSeenLocs[23] |= 0b111111111 << loc.x - 4;
				catSeenLocs[24] |= 0b1111111 << loc.x - 3;
				catSeenLocs[25] |= 0b1 << loc.x;
				break;
			case 21:
				catSeenLocs[16] |= 0b1 << loc.x;
				catSeenLocs[17] |= 0b1111111 << loc.x - 3;
				catSeenLocs[18] |= 0b111111111 << loc.x - 4;
				catSeenLocs[19] |= 0b111111111 << loc.x - 4;
				catSeenLocs[20] |= 0b111111111 << loc.x - 4;
				catSeenLocs[21] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[22] |= 0b111111111 << loc.x - 4;
				catSeenLocs[23] |= 0b111111111 << loc.x - 4;
				catSeenLocs[24] |= 0b111111111 << loc.x - 4;
				catSeenLocs[25] |= 0b1111111 << loc.x - 3;
				catSeenLocs[26] |= 0b1 << loc.x;
				break;
			case 22:
				catSeenLocs[17] |= 0b1 << loc.x;
				catSeenLocs[18] |= 0b1111111 << loc.x - 3;
				catSeenLocs[19] |= 0b111111111 << loc.x - 4;
				catSeenLocs[20] |= 0b111111111 << loc.x - 4;
				catSeenLocs[21] |= 0b111111111 << loc.x - 4;
				catSeenLocs[22] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[23] |= 0b111111111 << loc.x - 4;
				catSeenLocs[24] |= 0b111111111 << loc.x - 4;
				catSeenLocs[25] |= 0b111111111 << loc.x - 4;
				catSeenLocs[26] |= 0b1111111 << loc.x - 3;
				catSeenLocs[27] |= 0b1 << loc.x;
				break;
			case 23:
				catSeenLocs[18] |= 0b1 << loc.x;
				catSeenLocs[19] |= 0b1111111 << loc.x - 3;
				catSeenLocs[20] |= 0b111111111 << loc.x - 4;
				catSeenLocs[21] |= 0b111111111 << loc.x - 4;
				catSeenLocs[22] |= 0b111111111 << loc.x - 4;
				catSeenLocs[23] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[24] |= 0b111111111 << loc.x - 4;
				catSeenLocs[25] |= 0b111111111 << loc.x - 4;
				catSeenLocs[26] |= 0b111111111 << loc.x - 4;
				catSeenLocs[27] |= 0b1111111 << loc.x - 3;
				catSeenLocs[28] |= 0b1 << loc.x;
				break;
			case 24:
				catSeenLocs[19] |= 0b1 << loc.x;
				catSeenLocs[20] |= 0b1111111 << loc.x - 3;
				catSeenLocs[21] |= 0b111111111 << loc.x - 4;
				catSeenLocs[22] |= 0b111111111 << loc.x - 4;
				catSeenLocs[23] |= 0b111111111 << loc.x - 4;
				catSeenLocs[24] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[25] |= 0b111111111 << loc.x - 4;
				catSeenLocs[26] |= 0b111111111 << loc.x - 4;
				catSeenLocs[27] |= 0b111111111 << loc.x - 4;
				catSeenLocs[28] |= 0b1111111 << loc.x - 3;
				catSeenLocs[29] |= 0b1 << loc.x;
				break;
			case 25:
				catSeenLocs[20] |= 0b1 << loc.x;
				catSeenLocs[21] |= 0b1111111 << loc.x - 3;
				catSeenLocs[22] |= 0b111111111 << loc.x - 4;
				catSeenLocs[23] |= 0b111111111 << loc.x - 4;
				catSeenLocs[24] |= 0b111111111 << loc.x - 4;
				catSeenLocs[25] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[26] |= 0b111111111 << loc.x - 4;
				catSeenLocs[27] |= 0b111111111 << loc.x - 4;
				catSeenLocs[28] |= 0b111111111 << loc.x - 4;
				catSeenLocs[29] |= 0b1111111 << loc.x - 3;
				catSeenLocs[30] |= 0b1 << loc.x;
				break;
			case 26:
				catSeenLocs[21] |= 0b1 << loc.x;
				catSeenLocs[22] |= 0b1111111 << loc.x - 3;
				catSeenLocs[23] |= 0b111111111 << loc.x - 4;
				catSeenLocs[24] |= 0b111111111 << loc.x - 4;
				catSeenLocs[25] |= 0b111111111 << loc.x - 4;
				catSeenLocs[26] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[27] |= 0b111111111 << loc.x - 4;
				catSeenLocs[28] |= 0b111111111 << loc.x - 4;
				catSeenLocs[29] |= 0b111111111 << loc.x - 4;
				catSeenLocs[30] |= 0b1111111 << loc.x - 3;
				catSeenLocs[31] |= 0b1 << loc.x;
				break;
			case 27:
				catSeenLocs[22] |= 0b1 << loc.x;
				catSeenLocs[23] |= 0b1111111 << loc.x - 3;
				catSeenLocs[24] |= 0b111111111 << loc.x - 4;
				catSeenLocs[25] |= 0b111111111 << loc.x - 4;
				catSeenLocs[26] |= 0b111111111 << loc.x - 4;
				catSeenLocs[27] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[28] |= 0b111111111 << loc.x - 4;
				catSeenLocs[29] |= 0b111111111 << loc.x - 4;
				catSeenLocs[30] |= 0b111111111 << loc.x - 4;
				catSeenLocs[31] |= 0b1111111 << loc.x - 3;
				catSeenLocs[32] |= 0b1 << loc.x;
				break;
			case 28:
				catSeenLocs[23] |= 0b1 << loc.x;
				catSeenLocs[24] |= 0b1111111 << loc.x - 3;
				catSeenLocs[25] |= 0b111111111 << loc.x - 4;
				catSeenLocs[26] |= 0b111111111 << loc.x - 4;
				catSeenLocs[27] |= 0b111111111 << loc.x - 4;
				catSeenLocs[28] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[29] |= 0b111111111 << loc.x - 4;
				catSeenLocs[30] |= 0b111111111 << loc.x - 4;
				catSeenLocs[31] |= 0b111111111 << loc.x - 4;
				catSeenLocs[32] |= 0b1111111 << loc.x - 3;
				catSeenLocs[33] |= 0b1 << loc.x;
				break;
			case 29:
				catSeenLocs[24] |= 0b1 << loc.x;
				catSeenLocs[25] |= 0b1111111 << loc.x - 3;
				catSeenLocs[26] |= 0b111111111 << loc.x - 4;
				catSeenLocs[27] |= 0b111111111 << loc.x - 4;
				catSeenLocs[28] |= 0b111111111 << loc.x - 4;
				catSeenLocs[29] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[30] |= 0b111111111 << loc.x - 4;
				catSeenLocs[31] |= 0b111111111 << loc.x - 4;
				catSeenLocs[32] |= 0b111111111 << loc.x - 4;
				catSeenLocs[33] |= 0b1111111 << loc.x - 3;
				catSeenLocs[34] |= 0b1 << loc.x;
				break;
			case 30:
				catSeenLocs[25] |= 0b1 << loc.x;
				catSeenLocs[26] |= 0b1111111 << loc.x - 3;
				catSeenLocs[27] |= 0b111111111 << loc.x - 4;
				catSeenLocs[28] |= 0b111111111 << loc.x - 4;
				catSeenLocs[29] |= 0b111111111 << loc.x - 4;
				catSeenLocs[30] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[31] |= 0b111111111 << loc.x - 4;
				catSeenLocs[32] |= 0b111111111 << loc.x - 4;
				catSeenLocs[33] |= 0b111111111 << loc.x - 4;
				catSeenLocs[34] |= 0b1111111 << loc.x - 3;
				catSeenLocs[35] |= 0b1 << loc.x;
				break;
			case 31:
				catSeenLocs[26] |= 0b1 << loc.x;
				catSeenLocs[27] |= 0b1111111 << loc.x - 3;
				catSeenLocs[28] |= 0b111111111 << loc.x - 4;
				catSeenLocs[29] |= 0b111111111 << loc.x - 4;
				catSeenLocs[30] |= 0b111111111 << loc.x - 4;
				catSeenLocs[31] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[32] |= 0b111111111 << loc.x - 4;
				catSeenLocs[33] |= 0b111111111 << loc.x - 4;
				catSeenLocs[34] |= 0b111111111 << loc.x - 4;
				catSeenLocs[35] |= 0b1111111 << loc.x - 3;
				catSeenLocs[36] |= 0b1 << loc.x;
				break;
			case 32:
				catSeenLocs[27] |= 0b1 << loc.x;
				catSeenLocs[28] |= 0b1111111 << loc.x - 3;
				catSeenLocs[29] |= 0b111111111 << loc.x - 4;
				catSeenLocs[30] |= 0b111111111 << loc.x - 4;
				catSeenLocs[31] |= 0b111111111 << loc.x - 4;
				catSeenLocs[32] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[33] |= 0b111111111 << loc.x - 4;
				catSeenLocs[34] |= 0b111111111 << loc.x - 4;
				catSeenLocs[35] |= 0b111111111 << loc.x - 4;
				catSeenLocs[36] |= 0b1111111 << loc.x - 3;
				catSeenLocs[37] |= 0b1 << loc.x;
				break;
			case 33:
				catSeenLocs[28] |= 0b1 << loc.x;
				catSeenLocs[29] |= 0b1111111 << loc.x - 3;
				catSeenLocs[30] |= 0b111111111 << loc.x - 4;
				catSeenLocs[31] |= 0b111111111 << loc.x - 4;
				catSeenLocs[32] |= 0b111111111 << loc.x - 4;
				catSeenLocs[33] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[34] |= 0b111111111 << loc.x - 4;
				catSeenLocs[35] |= 0b111111111 << loc.x - 4;
				catSeenLocs[36] |= 0b111111111 << loc.x - 4;
				catSeenLocs[37] |= 0b1111111 << loc.x - 3;
				catSeenLocs[38] |= 0b1 << loc.x;
				break;
			case 34:
				catSeenLocs[29] |= 0b1 << loc.x;
				catSeenLocs[30] |= 0b1111111 << loc.x - 3;
				catSeenLocs[31] |= 0b111111111 << loc.x - 4;
				catSeenLocs[32] |= 0b111111111 << loc.x - 4;
				catSeenLocs[33] |= 0b111111111 << loc.x - 4;
				catSeenLocs[34] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[35] |= 0b111111111 << loc.x - 4;
				catSeenLocs[36] |= 0b111111111 << loc.x - 4;
				catSeenLocs[37] |= 0b111111111 << loc.x - 4;
				catSeenLocs[38] |= 0b1111111 << loc.x - 3;
				catSeenLocs[39] |= 0b1 << loc.x;
				break;
			case 35:
				catSeenLocs[30] |= 0b1 << loc.x;
				catSeenLocs[31] |= 0b1111111 << loc.x - 3;
				catSeenLocs[32] |= 0b111111111 << loc.x - 4;
				catSeenLocs[33] |= 0b111111111 << loc.x - 4;
				catSeenLocs[34] |= 0b111111111 << loc.x - 4;
				catSeenLocs[35] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[36] |= 0b111111111 << loc.x - 4;
				catSeenLocs[37] |= 0b111111111 << loc.x - 4;
				catSeenLocs[38] |= 0b111111111 << loc.x - 4;
				catSeenLocs[39] |= 0b1111111 << loc.x - 3;
				catSeenLocs[40] |= 0b1 << loc.x;
				break;
			case 36:
				catSeenLocs[31] |= 0b1 << loc.x;
				catSeenLocs[32] |= 0b1111111 << loc.x - 3;
				catSeenLocs[33] |= 0b111111111 << loc.x - 4;
				catSeenLocs[34] |= 0b111111111 << loc.x - 4;
				catSeenLocs[35] |= 0b111111111 << loc.x - 4;
				catSeenLocs[36] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[37] |= 0b111111111 << loc.x - 4;
				catSeenLocs[38] |= 0b111111111 << loc.x - 4;
				catSeenLocs[39] |= 0b111111111 << loc.x - 4;
				catSeenLocs[40] |= 0b1111111 << loc.x - 3;
				catSeenLocs[41] |= 0b1 << loc.x;
				break;
			case 37:
				catSeenLocs[32] |= 0b1 << loc.x;
				catSeenLocs[33] |= 0b1111111 << loc.x - 3;
				catSeenLocs[34] |= 0b111111111 << loc.x - 4;
				catSeenLocs[35] |= 0b111111111 << loc.x - 4;
				catSeenLocs[36] |= 0b111111111 << loc.x - 4;
				catSeenLocs[37] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[38] |= 0b111111111 << loc.x - 4;
				catSeenLocs[39] |= 0b111111111 << loc.x - 4;
				catSeenLocs[40] |= 0b111111111 << loc.x - 4;
				catSeenLocs[41] |= 0b1111111 << loc.x - 3;
				catSeenLocs[42] |= 0b1 << loc.x;
				break;
			case 38:
				catSeenLocs[33] |= 0b1 << loc.x;
				catSeenLocs[34] |= 0b1111111 << loc.x - 3;
				catSeenLocs[35] |= 0b111111111 << loc.x - 4;
				catSeenLocs[36] |= 0b111111111 << loc.x - 4;
				catSeenLocs[37] |= 0b111111111 << loc.x - 4;
				catSeenLocs[38] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[39] |= 0b111111111 << loc.x - 4;
				catSeenLocs[40] |= 0b111111111 << loc.x - 4;
				catSeenLocs[41] |= 0b111111111 << loc.x - 4;
				catSeenLocs[42] |= 0b1111111 << loc.x - 3;
				catSeenLocs[43] |= 0b1 << loc.x;
				break;
			case 39:
				catSeenLocs[34] |= 0b1 << loc.x;
				catSeenLocs[35] |= 0b1111111 << loc.x - 3;
				catSeenLocs[36] |= 0b111111111 << loc.x - 4;
				catSeenLocs[37] |= 0b111111111 << loc.x - 4;
				catSeenLocs[38] |= 0b111111111 << loc.x - 4;
				catSeenLocs[39] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[40] |= 0b111111111 << loc.x - 4;
				catSeenLocs[41] |= 0b111111111 << loc.x - 4;
				catSeenLocs[42] |= 0b111111111 << loc.x - 4;
				catSeenLocs[43] |= 0b1111111 << loc.x - 3;
				catSeenLocs[44] |= 0b1 << loc.x;
				break;
			case 40:
				catSeenLocs[35] |= 0b1 << loc.x;
				catSeenLocs[36] |= 0b1111111 << loc.x - 3;
				catSeenLocs[37] |= 0b111111111 << loc.x - 4;
				catSeenLocs[38] |= 0b111111111 << loc.x - 4;
				catSeenLocs[39] |= 0b111111111 << loc.x - 4;
				catSeenLocs[40] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[41] |= 0b111111111 << loc.x - 4;
				catSeenLocs[42] |= 0b111111111 << loc.x - 4;
				catSeenLocs[43] |= 0b111111111 << loc.x - 4;
				catSeenLocs[44] |= 0b1111111 << loc.x - 3;
				catSeenLocs[45] |= 0b1 << loc.x;
				break;
			case 41:
				catSeenLocs[36] |= 0b1 << loc.x;
				catSeenLocs[37] |= 0b1111111 << loc.x - 3;
				catSeenLocs[38] |= 0b111111111 << loc.x - 4;
				catSeenLocs[39] |= 0b111111111 << loc.x - 4;
				catSeenLocs[40] |= 0b111111111 << loc.x - 4;
				catSeenLocs[41] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[42] |= 0b111111111 << loc.x - 4;
				catSeenLocs[43] |= 0b111111111 << loc.x - 4;
				catSeenLocs[44] |= 0b111111111 << loc.x - 4;
				catSeenLocs[45] |= 0b1111111 << loc.x - 3;
				catSeenLocs[46] |= 0b1 << loc.x;
				break;
			case 42:
				catSeenLocs[37] |= 0b1 << loc.x;
				catSeenLocs[38] |= 0b1111111 << loc.x - 3;
				catSeenLocs[39] |= 0b111111111 << loc.x - 4;
				catSeenLocs[40] |= 0b111111111 << loc.x - 4;
				catSeenLocs[41] |= 0b111111111 << loc.x - 4;
				catSeenLocs[42] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[43] |= 0b111111111 << loc.x - 4;
				catSeenLocs[44] |= 0b111111111 << loc.x - 4;
				catSeenLocs[45] |= 0b111111111 << loc.x - 4;
				catSeenLocs[46] |= 0b1111111 << loc.x - 3;
				catSeenLocs[47] |= 0b1 << loc.x;
				break;
			case 43:
				catSeenLocs[38] |= 0b1 << loc.x;
				catSeenLocs[39] |= 0b1111111 << loc.x - 3;
				catSeenLocs[40] |= 0b111111111 << loc.x - 4;
				catSeenLocs[41] |= 0b111111111 << loc.x - 4;
				catSeenLocs[42] |= 0b111111111 << loc.x - 4;
				catSeenLocs[43] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[44] |= 0b111111111 << loc.x - 4;
				catSeenLocs[45] |= 0b111111111 << loc.x - 4;
				catSeenLocs[46] |= 0b111111111 << loc.x - 4;
				catSeenLocs[47] |= 0b1111111 << loc.x - 3;
				catSeenLocs[48] |= 0b1 << loc.x;
				break;
			case 44:
				catSeenLocs[39] |= 0b1 << loc.x;
				catSeenLocs[40] |= 0b1111111 << loc.x - 3;
				catSeenLocs[41] |= 0b111111111 << loc.x - 4;
				catSeenLocs[42] |= 0b111111111 << loc.x - 4;
				catSeenLocs[43] |= 0b111111111 << loc.x - 4;
				catSeenLocs[44] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[45] |= 0b111111111 << loc.x - 4;
				catSeenLocs[46] |= 0b111111111 << loc.x - 4;
				catSeenLocs[47] |= 0b111111111 << loc.x - 4;
				catSeenLocs[48] |= 0b1111111 << loc.x - 3;
				catSeenLocs[49] |= 0b1 << loc.x;
				break;
			case 45:
				catSeenLocs[40] |= 0b1 << loc.x;
				catSeenLocs[41] |= 0b1111111 << loc.x - 3;
				catSeenLocs[42] |= 0b111111111 << loc.x - 4;
				catSeenLocs[43] |= 0b111111111 << loc.x - 4;
				catSeenLocs[44] |= 0b111111111 << loc.x - 4;
				catSeenLocs[45] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[46] |= 0b111111111 << loc.x - 4;
				catSeenLocs[47] |= 0b111111111 << loc.x - 4;
				catSeenLocs[48] |= 0b111111111 << loc.x - 4;
				catSeenLocs[49] |= 0b1111111 << loc.x - 3;
				catSeenLocs[50] |= 0b1 << loc.x;
				break;
			case 46:
				catSeenLocs[41] |= 0b1 << loc.x;
				catSeenLocs[42] |= 0b1111111 << loc.x - 3;
				catSeenLocs[43] |= 0b111111111 << loc.x - 4;
				catSeenLocs[44] |= 0b111111111 << loc.x - 4;
				catSeenLocs[45] |= 0b111111111 << loc.x - 4;
				catSeenLocs[46] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[47] |= 0b111111111 << loc.x - 4;
				catSeenLocs[48] |= 0b111111111 << loc.x - 4;
				catSeenLocs[49] |= 0b111111111 << loc.x - 4;
				catSeenLocs[50] |= 0b1111111 << loc.x - 3;
				catSeenLocs[51] |= 0b1 << loc.x;
				break;
			case 47:
				catSeenLocs[42] |= 0b1 << loc.x;
				catSeenLocs[43] |= 0b1111111 << loc.x - 3;
				catSeenLocs[44] |= 0b111111111 << loc.x - 4;
				catSeenLocs[45] |= 0b111111111 << loc.x - 4;
				catSeenLocs[46] |= 0b111111111 << loc.x - 4;
				catSeenLocs[47] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[48] |= 0b111111111 << loc.x - 4;
				catSeenLocs[49] |= 0b111111111 << loc.x - 4;
				catSeenLocs[50] |= 0b111111111 << loc.x - 4;
				catSeenLocs[51] |= 0b1111111 << loc.x - 3;
				catSeenLocs[52] |= 0b1 << loc.x;
				break;
			case 48:
				catSeenLocs[43] |= 0b1 << loc.x;
				catSeenLocs[44] |= 0b1111111 << loc.x - 3;
				catSeenLocs[45] |= 0b111111111 << loc.x - 4;
				catSeenLocs[46] |= 0b111111111 << loc.x - 4;
				catSeenLocs[47] |= 0b111111111 << loc.x - 4;
				catSeenLocs[48] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[49] |= 0b111111111 << loc.x - 4;
				catSeenLocs[50] |= 0b111111111 << loc.x - 4;
				catSeenLocs[51] |= 0b111111111 << loc.x - 4;
				catSeenLocs[52] |= 0b1111111 << loc.x - 3;
				catSeenLocs[53] |= 0b1 << loc.x;
				break;
			case 49:
				catSeenLocs[44] |= 0b1 << loc.x;
				catSeenLocs[45] |= 0b1111111 << loc.x - 3;
				catSeenLocs[46] |= 0b111111111 << loc.x - 4;
				catSeenLocs[47] |= 0b111111111 << loc.x - 4;
				catSeenLocs[48] |= 0b111111111 << loc.x - 4;
				catSeenLocs[49] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[50] |= 0b111111111 << loc.x - 4;
				catSeenLocs[51] |= 0b111111111 << loc.x - 4;
				catSeenLocs[52] |= 0b111111111 << loc.x - 4;
				catSeenLocs[53] |= 0b1111111 << loc.x - 3;
				catSeenLocs[54] |= 0b1 << loc.x;
				break;
			case 50:
				catSeenLocs[45] |= 0b1 << loc.x;
				catSeenLocs[46] |= 0b1111111 << loc.x - 3;
				catSeenLocs[47] |= 0b111111111 << loc.x - 4;
				catSeenLocs[48] |= 0b111111111 << loc.x - 4;
				catSeenLocs[49] |= 0b111111111 << loc.x - 4;
				catSeenLocs[50] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[51] |= 0b111111111 << loc.x - 4;
				catSeenLocs[52] |= 0b111111111 << loc.x - 4;
				catSeenLocs[53] |= 0b111111111 << loc.x - 4;
				catSeenLocs[54] |= 0b1111111 << loc.x - 3;
				catSeenLocs[55] |= 0b1 << loc.x;
				break;
			case 51:
				catSeenLocs[46] |= 0b1 << loc.x;
				catSeenLocs[47] |= 0b1111111 << loc.x - 3;
				catSeenLocs[48] |= 0b111111111 << loc.x - 4;
				catSeenLocs[49] |= 0b111111111 << loc.x - 4;
				catSeenLocs[50] |= 0b111111111 << loc.x - 4;
				catSeenLocs[51] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[52] |= 0b111111111 << loc.x - 4;
				catSeenLocs[53] |= 0b111111111 << loc.x - 4;
				catSeenLocs[54] |= 0b111111111 << loc.x - 4;
				catSeenLocs[55] |= 0b1111111 << loc.x - 3;
				catSeenLocs[56] |= 0b1 << loc.x;
				break;
			case 52:
				catSeenLocs[47] |= 0b1 << loc.x;
				catSeenLocs[48] |= 0b1111111 << loc.x - 3;
				catSeenLocs[49] |= 0b111111111 << loc.x - 4;
				catSeenLocs[50] |= 0b111111111 << loc.x - 4;
				catSeenLocs[51] |= 0b111111111 << loc.x - 4;
				catSeenLocs[52] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[53] |= 0b111111111 << loc.x - 4;
				catSeenLocs[54] |= 0b111111111 << loc.x - 4;
				catSeenLocs[55] |= 0b111111111 << loc.x - 4;
				catSeenLocs[56] |= 0b1111111 << loc.x - 3;
				catSeenLocs[57] |= 0b1 << loc.x;
				break;
			case 53:
				catSeenLocs[48] |= 0b1 << loc.x;
				catSeenLocs[49] |= 0b1111111 << loc.x - 3;
				catSeenLocs[50] |= 0b111111111 << loc.x - 4;
				catSeenLocs[51] |= 0b111111111 << loc.x - 4;
				catSeenLocs[52] |= 0b111111111 << loc.x - 4;
				catSeenLocs[53] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[54] |= 0b111111111 << loc.x - 4;
				catSeenLocs[55] |= 0b111111111 << loc.x - 4;
				catSeenLocs[56] |= 0b111111111 << loc.x - 4;
				catSeenLocs[57] |= 0b1111111 << loc.x - 3;
				catSeenLocs[58] |= 0b1 << loc.x;
				break;
			case 54:
				catSeenLocs[49] |= 0b1 << loc.x;
				catSeenLocs[50] |= 0b1111111 << loc.x - 3;
				catSeenLocs[51] |= 0b111111111 << loc.x - 4;
				catSeenLocs[52] |= 0b111111111 << loc.x - 4;
				catSeenLocs[53] |= 0b111111111 << loc.x - 4;
				catSeenLocs[54] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[55] |= 0b111111111 << loc.x - 4;
				catSeenLocs[56] |= 0b111111111 << loc.x - 4;
				catSeenLocs[57] |= 0b111111111 << loc.x - 4;
				catSeenLocs[58] |= 0b1111111 << loc.x - 3;
				catSeenLocs[59] |= 0b1 << loc.x;
				break;
			case 55:
				catSeenLocs[50] |= 0b1 << loc.x;
				catSeenLocs[51] |= 0b1111111 << loc.x - 3;
				catSeenLocs[52] |= 0b111111111 << loc.x - 4;
				catSeenLocs[53] |= 0b111111111 << loc.x - 4;
				catSeenLocs[54] |= 0b111111111 << loc.x - 4;
				catSeenLocs[55] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[56] |= 0b111111111 << loc.x - 4;
				catSeenLocs[57] |= 0b111111111 << loc.x - 4;
				catSeenLocs[58] |= 0b111111111 << loc.x - 4;
				catSeenLocs[59] |= 0b1111111 << loc.x - 3;
				break;
			case 56:
				catSeenLocs[51] |= 0b1 << loc.x;
				catSeenLocs[52] |= 0b1111111 << loc.x - 3;
				catSeenLocs[53] |= 0b111111111 << loc.x - 4;
				catSeenLocs[54] |= 0b111111111 << loc.x - 4;
				catSeenLocs[55] |= 0b111111111 << loc.x - 4;
				catSeenLocs[56] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[57] |= 0b111111111 << loc.x - 4;
				catSeenLocs[58] |= 0b111111111 << loc.x - 4;
				catSeenLocs[59] |= 0b111111111 << loc.x - 4;
				break;
			case 57:
				catSeenLocs[52] |= 0b1 << loc.x;
				catSeenLocs[53] |= 0b1111111 << loc.x - 3;
				catSeenLocs[54] |= 0b111111111 << loc.x - 4;
				catSeenLocs[55] |= 0b111111111 << loc.x - 4;
				catSeenLocs[56] |= 0b111111111 << loc.x - 4;
				catSeenLocs[57] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[58] |= 0b111111111 << loc.x - 4;
				catSeenLocs[59] |= 0b111111111 << loc.x - 4;
				break;
			case 58:
				catSeenLocs[53] |= 0b1 << loc.x;
				catSeenLocs[54] |= 0b1111111 << loc.x - 3;
				catSeenLocs[55] |= 0b111111111 << loc.x - 4;
				catSeenLocs[56] |= 0b111111111 << loc.x - 4;
				catSeenLocs[57] |= 0b111111111 << loc.x - 4;
				catSeenLocs[58] |= 0b11111111111 << loc.x - 5;
				catSeenLocs[59] |= 0b111111111 << loc.x - 4;
				break;
			case 59:
				catSeenLocs[54] |= 0b1 << loc.x;
				catSeenLocs[55] |= 0b1111111 << loc.x - 3;
				catSeenLocs[56] |= 0b111111111 << loc.x - 4;
				catSeenLocs[57] |= 0b111111111 << loc.x - 4;
				catSeenLocs[58] |= 0b111111111 << loc.x - 4;
				catSeenLocs[59] |= 0b11111111111 << loc.x - 5;
				break;
		}
        for (int i = 0; i < numberOfCats; i++) {
            if (catIDs[i] == id) {
                if (catRounds[i] < round) {
                    catIDs[i] = id;
                    catRounds[i] = round;
                    catLocations[i] = loc;
                    if (source == -1 && !catCritical[i]) {
                        catCritical[i] = true;
                        numberOfCriticalInformation++;
                    }
                    else if (catCritical[i]) {
                        catCritical[i] = false;
                        numberOfCriticalInformation--;
                    }
                    return;
                }
                else {
                    if (source != -1 && catCritical[i]) {
                        catCritical[i] = false;
                        numberOfCriticalInformation--;
                    }
                    return;
                }
            }
        }
        catIDs[numberOfCats] = id;
        catRounds[numberOfCats] = round;
        catLocations[numberOfCats] = loc;
        if (source == -1 && !catCritical[numberOfCats]) {
            catCritical[numberOfCats] = true;
            numberOfCriticalInformation++;
        }
        numberOfCats++;
    };

    public static void removeValidSymmetry(int source, int index) throws Exception {
        if (symmetry[index]) {
            symmetry[index] = false;
            numValidSymmetries--;
            if (source == -1) {
                if (!criticalSymmetry[index]) {
                    criticalSymmetry[index] = true;
                    numberOfCriticalInformation++;
                }
            }
            else if (criticalSymmetry[index]) {
                criticalSymmetry[index] = false;
                numberOfCriticalInformation--;
            }
            if (symmetry[2]) {
                mostLikelySymmetry = 2;
            }
            if (symmetry[1]) {
                mostLikelySymmetry = 1;
            }
            if (symmetry[0]) {
                mostLikelySymmetry = 0;
            }
        }
        else if (source != -1 && criticalSymmetry[index]) {
            criticalSymmetry[index] = false;
            numberOfCriticalInformation--;
        }
		// if (numValidSymmetries == 1) {
		// 	for (int i = numberOfMines; --i >= 0;) {
		// 		addMine(1, getOppositeMapLocation(mineLocs[i], mostLikelySymmetry));
		// 	}
		// }
    };

    public static void updateRound() throws Exception {
        int a = Clock.getBytecodeNum();

        minNumberOfBabyRats = (G.rc.getCurrentRatCost() - GameConstants.BUILD_ROBOT_BASE_COST) / GameConstants.BUILD_ROBOT_COST_INCREASE * GameConstants.NUM_ROBOTS_FOR_COST_INCREASE;
        maxNumberOfBabyRats = minNumberOfBabyRats + GameConstants.NUM_ROBOTS_FOR_COST_INCREASE - 1;
        
        switch (G.type) {
            case RAT_KING:
                updateSymmetry();
                writeSelfLocation();
                readRatKingLocations();
                writeCheeseMineLocations();
                writeDefendRatKingLocations();
                writeFormRatKingLocations();
                writeCatLocations();
                readCheeseMessages();
                break;
            case BABY_RAT:
                updateSymmetry();
                readRatKingLocations();
                readCheeseMineLocations();
                readFormRatKingLocations();
                readDefendRatKingLocations();
                readCatLocations();
                break;
            default:
                throw new Exception("lol cat?");
        }
        if (ENABLE_SQUEAKING) {
            readSqueakMessages();
        }

        // if (ENABLE_INDICATORS)
            // G.indicatorString.append("COMMS-BT=" + (Clock.getBytecodeNum() - a) + " ");
        a = Clock.getBytecodeNum();

        // drawIndicators(); // uses 5000 bytecode somehow

        // update symmetry array
        // if (G.lastMe != G.me) {
        if (G.round == 1) {
            return;
        }
        if (G.type.isBabyRatType()) {
            updateExploredBabyRat();
        } else {
            updateExploredRatKing();
        }
        for (MapInfo i : G.nearbyMapInfos) {
            MapLocation xy = i.getMapLocation();
            if (i.hasCheeseMine()) {
                addMine(-1, xy);
                mine[xy.y] |= 1L << xy.x;
            }
            if (i.isWall()) {
                wall[xy.y] |= 1L << xy.x;
            }
        }
        // if (ENABLE_INDICATORS)
            // G.indicatorString.append("INFO-BT=" + (Clock.getBytecodeNum() - a) + " ");

        a = Clock.getBytecodeNum();
        if (numValidSymmetries > 1) {
            if (symmetry[0] && !symmetryValid(0)) {
                removeValidSymmetry(-1, 0);
            }
            if (symmetry[1] && !symmetryValid(1)) {
                removeValidSymmetry(-1, 1);
            }
            if (symmetry[2] && !symmetryValid(2)) {
                removeValidSymmetry(-1, 2);
            }
        }
        // if (ENABLE_INDICATORS)
        //     G.indicatorString.append("SYM=" + (Clock.getBytecodeNum() - a) + " ");
        // }
    };
    public static void updateInfo() throws Exception {
        for (int i = G.cats.length; --i >= 0;) {
			addCat(-1, G.cats[i].ID, (G.round / 2) * 2, G.cats[i].location);
        }
    }
	public static void updateExploredBabyRat() {
		switch (G.dir) {
			case Direction.SOUTHWEST:
				updateExploredBabyRat0();
				break;
			case Direction.SOUTH:
				updateExploredBabyRat1();
				break;
			case Direction.SOUTHEAST:
				updateExploredBabyRat2();
				break;
			case Direction.EAST:
				updateExploredBabyRat3();
				break;
			case Direction.NORTHEAST:
				updateExploredBabyRat4();
				break;
			case Direction.NORTH:
				updateExploredBabyRat5();
				break;
			case Direction.NORTHWEST:
				updateExploredBabyRat6();
				break;
			case Direction.WEST:
				updateExploredBabyRat7();
				break;
		}
	}
	public static void updateExploredBabyRat0() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111 << G.me.x - 4;
				break;
			case 1:
				explored[0] |= 0b11111 << G.me.x - 4;
				explored[1] |= 0b11111 << G.me.x - 4;
				break;
			case 2:
				explored[0] |= 0b11111 << G.me.x - 4;
				explored[1] |= 0b11111 << G.me.x - 4;
				explored[2] |= 0b11111 << G.me.x - 4;
				break;
			case 3:
				explored[0] |= 0b1111 << G.me.x - 3;
				explored[1] |= 0b11111 << G.me.x - 4;
				explored[2] |= 0b11111 << G.me.x - 4;
				explored[3] |= 0b11111 << G.me.x - 4;
				break;
			case 4:
				explored[0] |= 0b111 << G.me.x - 2;
				explored[1] |= 0b1111 << G.me.x - 3;
				explored[2] |= 0b11111 << G.me.x - 4;
				explored[3] |= 0b11111 << G.me.x - 4;
				explored[4] |= 0b11111 << G.me.x - 4;
				break;
			case 5:
				explored[1] |= 0b111 << G.me.x - 2;
				explored[2] |= 0b1111 << G.me.x - 3;
				explored[3] |= 0b11111 << G.me.x - 4;
				explored[4] |= 0b11111 << G.me.x - 4;
				explored[5] |= 0b11111 << G.me.x - 4;
				break;
			case 6:
				explored[2] |= 0b111 << G.me.x - 2;
				explored[3] |= 0b1111 << G.me.x - 3;
				explored[4] |= 0b11111 << G.me.x - 4;
				explored[5] |= 0b11111 << G.me.x - 4;
				explored[6] |= 0b11111 << G.me.x - 4;
				break;
			case 7:
				explored[3] |= 0b111 << G.me.x - 2;
				explored[4] |= 0b1111 << G.me.x - 3;
				explored[5] |= 0b11111 << G.me.x - 4;
				explored[6] |= 0b11111 << G.me.x - 4;
				explored[7] |= 0b11111 << G.me.x - 4;
				break;
			case 8:
				explored[4] |= 0b111 << G.me.x - 2;
				explored[5] |= 0b1111 << G.me.x - 3;
				explored[6] |= 0b11111 << G.me.x - 4;
				explored[7] |= 0b11111 << G.me.x - 4;
				explored[8] |= 0b11111 << G.me.x - 4;
				break;
			case 9:
				explored[5] |= 0b111 << G.me.x - 2;
				explored[6] |= 0b1111 << G.me.x - 3;
				explored[7] |= 0b11111 << G.me.x - 4;
				explored[8] |= 0b11111 << G.me.x - 4;
				explored[9] |= 0b11111 << G.me.x - 4;
				break;
			case 10:
				explored[6] |= 0b111 << G.me.x - 2;
				explored[7] |= 0b1111 << G.me.x - 3;
				explored[8] |= 0b11111 << G.me.x - 4;
				explored[9] |= 0b11111 << G.me.x - 4;
				explored[10] |= 0b11111 << G.me.x - 4;
				break;
			case 11:
				explored[7] |= 0b111 << G.me.x - 2;
				explored[8] |= 0b1111 << G.me.x - 3;
				explored[9] |= 0b11111 << G.me.x - 4;
				explored[10] |= 0b11111 << G.me.x - 4;
				explored[11] |= 0b11111 << G.me.x - 4;
				break;
			case 12:
				explored[8] |= 0b111 << G.me.x - 2;
				explored[9] |= 0b1111 << G.me.x - 3;
				explored[10] |= 0b11111 << G.me.x - 4;
				explored[11] |= 0b11111 << G.me.x - 4;
				explored[12] |= 0b11111 << G.me.x - 4;
				break;
			case 13:
				explored[9] |= 0b111 << G.me.x - 2;
				explored[10] |= 0b1111 << G.me.x - 3;
				explored[11] |= 0b11111 << G.me.x - 4;
				explored[12] |= 0b11111 << G.me.x - 4;
				explored[13] |= 0b11111 << G.me.x - 4;
				break;
			case 14:
				explored[10] |= 0b111 << G.me.x - 2;
				explored[11] |= 0b1111 << G.me.x - 3;
				explored[12] |= 0b11111 << G.me.x - 4;
				explored[13] |= 0b11111 << G.me.x - 4;
				explored[14] |= 0b11111 << G.me.x - 4;
				break;
			case 15:
				explored[11] |= 0b111 << G.me.x - 2;
				explored[12] |= 0b1111 << G.me.x - 3;
				explored[13] |= 0b11111 << G.me.x - 4;
				explored[14] |= 0b11111 << G.me.x - 4;
				explored[15] |= 0b11111 << G.me.x - 4;
				break;
			case 16:
				explored[12] |= 0b111 << G.me.x - 2;
				explored[13] |= 0b1111 << G.me.x - 3;
				explored[14] |= 0b11111 << G.me.x - 4;
				explored[15] |= 0b11111 << G.me.x - 4;
				explored[16] |= 0b11111 << G.me.x - 4;
				break;
			case 17:
				explored[13] |= 0b111 << G.me.x - 2;
				explored[14] |= 0b1111 << G.me.x - 3;
				explored[15] |= 0b11111 << G.me.x - 4;
				explored[16] |= 0b11111 << G.me.x - 4;
				explored[17] |= 0b11111 << G.me.x - 4;
				break;
			case 18:
				explored[14] |= 0b111 << G.me.x - 2;
				explored[15] |= 0b1111 << G.me.x - 3;
				explored[16] |= 0b11111 << G.me.x - 4;
				explored[17] |= 0b11111 << G.me.x - 4;
				explored[18] |= 0b11111 << G.me.x - 4;
				break;
			case 19:
				explored[15] |= 0b111 << G.me.x - 2;
				explored[16] |= 0b1111 << G.me.x - 3;
				explored[17] |= 0b11111 << G.me.x - 4;
				explored[18] |= 0b11111 << G.me.x - 4;
				explored[19] |= 0b11111 << G.me.x - 4;
				break;
			case 20:
				explored[16] |= 0b111 << G.me.x - 2;
				explored[17] |= 0b1111 << G.me.x - 3;
				explored[18] |= 0b11111 << G.me.x - 4;
				explored[19] |= 0b11111 << G.me.x - 4;
				explored[20] |= 0b11111 << G.me.x - 4;
				break;
			case 21:
				explored[17] |= 0b111 << G.me.x - 2;
				explored[18] |= 0b1111 << G.me.x - 3;
				explored[19] |= 0b11111 << G.me.x - 4;
				explored[20] |= 0b11111 << G.me.x - 4;
				explored[21] |= 0b11111 << G.me.x - 4;
				break;
			case 22:
				explored[18] |= 0b111 << G.me.x - 2;
				explored[19] |= 0b1111 << G.me.x - 3;
				explored[20] |= 0b11111 << G.me.x - 4;
				explored[21] |= 0b11111 << G.me.x - 4;
				explored[22] |= 0b11111 << G.me.x - 4;
				break;
			case 23:
				explored[19] |= 0b111 << G.me.x - 2;
				explored[20] |= 0b1111 << G.me.x - 3;
				explored[21] |= 0b11111 << G.me.x - 4;
				explored[22] |= 0b11111 << G.me.x - 4;
				explored[23] |= 0b11111 << G.me.x - 4;
				break;
			case 24:
				explored[20] |= 0b111 << G.me.x - 2;
				explored[21] |= 0b1111 << G.me.x - 3;
				explored[22] |= 0b11111 << G.me.x - 4;
				explored[23] |= 0b11111 << G.me.x - 4;
				explored[24] |= 0b11111 << G.me.x - 4;
				break;
			case 25:
				explored[21] |= 0b111 << G.me.x - 2;
				explored[22] |= 0b1111 << G.me.x - 3;
				explored[23] |= 0b11111 << G.me.x - 4;
				explored[24] |= 0b11111 << G.me.x - 4;
				explored[25] |= 0b11111 << G.me.x - 4;
				break;
			case 26:
				explored[22] |= 0b111 << G.me.x - 2;
				explored[23] |= 0b1111 << G.me.x - 3;
				explored[24] |= 0b11111 << G.me.x - 4;
				explored[25] |= 0b11111 << G.me.x - 4;
				explored[26] |= 0b11111 << G.me.x - 4;
				break;
			case 27:
				explored[23] |= 0b111 << G.me.x - 2;
				explored[24] |= 0b1111 << G.me.x - 3;
				explored[25] |= 0b11111 << G.me.x - 4;
				explored[26] |= 0b11111 << G.me.x - 4;
				explored[27] |= 0b11111 << G.me.x - 4;
				break;
			case 28:
				explored[24] |= 0b111 << G.me.x - 2;
				explored[25] |= 0b1111 << G.me.x - 3;
				explored[26] |= 0b11111 << G.me.x - 4;
				explored[27] |= 0b11111 << G.me.x - 4;
				explored[28] |= 0b11111 << G.me.x - 4;
				break;
			case 29:
				explored[25] |= 0b111 << G.me.x - 2;
				explored[26] |= 0b1111 << G.me.x - 3;
				explored[27] |= 0b11111 << G.me.x - 4;
				explored[28] |= 0b11111 << G.me.x - 4;
				explored[29] |= 0b11111 << G.me.x - 4;
				break;
			case 30:
				explored[26] |= 0b111 << G.me.x - 2;
				explored[27] |= 0b1111 << G.me.x - 3;
				explored[28] |= 0b11111 << G.me.x - 4;
				explored[29] |= 0b11111 << G.me.x - 4;
				explored[30] |= 0b11111 << G.me.x - 4;
				break;
			case 31:
				explored[27] |= 0b111 << G.me.x - 2;
				explored[28] |= 0b1111 << G.me.x - 3;
				explored[29] |= 0b11111 << G.me.x - 4;
				explored[30] |= 0b11111 << G.me.x - 4;
				explored[31] |= 0b11111 << G.me.x - 4;
				break;
			case 32:
				explored[28] |= 0b111 << G.me.x - 2;
				explored[29] |= 0b1111 << G.me.x - 3;
				explored[30] |= 0b11111 << G.me.x - 4;
				explored[31] |= 0b11111 << G.me.x - 4;
				explored[32] |= 0b11111 << G.me.x - 4;
				break;
			case 33:
				explored[29] |= 0b111 << G.me.x - 2;
				explored[30] |= 0b1111 << G.me.x - 3;
				explored[31] |= 0b11111 << G.me.x - 4;
				explored[32] |= 0b11111 << G.me.x - 4;
				explored[33] |= 0b11111 << G.me.x - 4;
				break;
			case 34:
				explored[30] |= 0b111 << G.me.x - 2;
				explored[31] |= 0b1111 << G.me.x - 3;
				explored[32] |= 0b11111 << G.me.x - 4;
				explored[33] |= 0b11111 << G.me.x - 4;
				explored[34] |= 0b11111 << G.me.x - 4;
				break;
			case 35:
				explored[31] |= 0b111 << G.me.x - 2;
				explored[32] |= 0b1111 << G.me.x - 3;
				explored[33] |= 0b11111 << G.me.x - 4;
				explored[34] |= 0b11111 << G.me.x - 4;
				explored[35] |= 0b11111 << G.me.x - 4;
				break;
			case 36:
				explored[32] |= 0b111 << G.me.x - 2;
				explored[33] |= 0b1111 << G.me.x - 3;
				explored[34] |= 0b11111 << G.me.x - 4;
				explored[35] |= 0b11111 << G.me.x - 4;
				explored[36] |= 0b11111 << G.me.x - 4;
				break;
			case 37:
				explored[33] |= 0b111 << G.me.x - 2;
				explored[34] |= 0b1111 << G.me.x - 3;
				explored[35] |= 0b11111 << G.me.x - 4;
				explored[36] |= 0b11111 << G.me.x - 4;
				explored[37] |= 0b11111 << G.me.x - 4;
				break;
			case 38:
				explored[34] |= 0b111 << G.me.x - 2;
				explored[35] |= 0b1111 << G.me.x - 3;
				explored[36] |= 0b11111 << G.me.x - 4;
				explored[37] |= 0b11111 << G.me.x - 4;
				explored[38] |= 0b11111 << G.me.x - 4;
				break;
			case 39:
				explored[35] |= 0b111 << G.me.x - 2;
				explored[36] |= 0b1111 << G.me.x - 3;
				explored[37] |= 0b11111 << G.me.x - 4;
				explored[38] |= 0b11111 << G.me.x - 4;
				explored[39] |= 0b11111 << G.me.x - 4;
				break;
			case 40:
				explored[36] |= 0b111 << G.me.x - 2;
				explored[37] |= 0b1111 << G.me.x - 3;
				explored[38] |= 0b11111 << G.me.x - 4;
				explored[39] |= 0b11111 << G.me.x - 4;
				explored[40] |= 0b11111 << G.me.x - 4;
				break;
			case 41:
				explored[37] |= 0b111 << G.me.x - 2;
				explored[38] |= 0b1111 << G.me.x - 3;
				explored[39] |= 0b11111 << G.me.x - 4;
				explored[40] |= 0b11111 << G.me.x - 4;
				explored[41] |= 0b11111 << G.me.x - 4;
				break;
			case 42:
				explored[38] |= 0b111 << G.me.x - 2;
				explored[39] |= 0b1111 << G.me.x - 3;
				explored[40] |= 0b11111 << G.me.x - 4;
				explored[41] |= 0b11111 << G.me.x - 4;
				explored[42] |= 0b11111 << G.me.x - 4;
				break;
			case 43:
				explored[39] |= 0b111 << G.me.x - 2;
				explored[40] |= 0b1111 << G.me.x - 3;
				explored[41] |= 0b11111 << G.me.x - 4;
				explored[42] |= 0b11111 << G.me.x - 4;
				explored[43] |= 0b11111 << G.me.x - 4;
				break;
			case 44:
				explored[40] |= 0b111 << G.me.x - 2;
				explored[41] |= 0b1111 << G.me.x - 3;
				explored[42] |= 0b11111 << G.me.x - 4;
				explored[43] |= 0b11111 << G.me.x - 4;
				explored[44] |= 0b11111 << G.me.x - 4;
				break;
			case 45:
				explored[41] |= 0b111 << G.me.x - 2;
				explored[42] |= 0b1111 << G.me.x - 3;
				explored[43] |= 0b11111 << G.me.x - 4;
				explored[44] |= 0b11111 << G.me.x - 4;
				explored[45] |= 0b11111 << G.me.x - 4;
				break;
			case 46:
				explored[42] |= 0b111 << G.me.x - 2;
				explored[43] |= 0b1111 << G.me.x - 3;
				explored[44] |= 0b11111 << G.me.x - 4;
				explored[45] |= 0b11111 << G.me.x - 4;
				explored[46] |= 0b11111 << G.me.x - 4;
				break;
			case 47:
				explored[43] |= 0b111 << G.me.x - 2;
				explored[44] |= 0b1111 << G.me.x - 3;
				explored[45] |= 0b11111 << G.me.x - 4;
				explored[46] |= 0b11111 << G.me.x - 4;
				explored[47] |= 0b11111 << G.me.x - 4;
				break;
			case 48:
				explored[44] |= 0b111 << G.me.x - 2;
				explored[45] |= 0b1111 << G.me.x - 3;
				explored[46] |= 0b11111 << G.me.x - 4;
				explored[47] |= 0b11111 << G.me.x - 4;
				explored[48] |= 0b11111 << G.me.x - 4;
				break;
			case 49:
				explored[45] |= 0b111 << G.me.x - 2;
				explored[46] |= 0b1111 << G.me.x - 3;
				explored[47] |= 0b11111 << G.me.x - 4;
				explored[48] |= 0b11111 << G.me.x - 4;
				explored[49] |= 0b11111 << G.me.x - 4;
				break;
			case 50:
				explored[46] |= 0b111 << G.me.x - 2;
				explored[47] |= 0b1111 << G.me.x - 3;
				explored[48] |= 0b11111 << G.me.x - 4;
				explored[49] |= 0b11111 << G.me.x - 4;
				explored[50] |= 0b11111 << G.me.x - 4;
				break;
			case 51:
				explored[47] |= 0b111 << G.me.x - 2;
				explored[48] |= 0b1111 << G.me.x - 3;
				explored[49] |= 0b11111 << G.me.x - 4;
				explored[50] |= 0b11111 << G.me.x - 4;
				explored[51] |= 0b11111 << G.me.x - 4;
				break;
			case 52:
				explored[48] |= 0b111 << G.me.x - 2;
				explored[49] |= 0b1111 << G.me.x - 3;
				explored[50] |= 0b11111 << G.me.x - 4;
				explored[51] |= 0b11111 << G.me.x - 4;
				explored[52] |= 0b11111 << G.me.x - 4;
				break;
			case 53:
				explored[49] |= 0b111 << G.me.x - 2;
				explored[50] |= 0b1111 << G.me.x - 3;
				explored[51] |= 0b11111 << G.me.x - 4;
				explored[52] |= 0b11111 << G.me.x - 4;
				explored[53] |= 0b11111 << G.me.x - 4;
				break;
			case 54:
				explored[50] |= 0b111 << G.me.x - 2;
				explored[51] |= 0b1111 << G.me.x - 3;
				explored[52] |= 0b11111 << G.me.x - 4;
				explored[53] |= 0b11111 << G.me.x - 4;
				explored[54] |= 0b11111 << G.me.x - 4;
				break;
			case 55:
				explored[51] |= 0b111 << G.me.x - 2;
				explored[52] |= 0b1111 << G.me.x - 3;
				explored[53] |= 0b11111 << G.me.x - 4;
				explored[54] |= 0b11111 << G.me.x - 4;
				explored[55] |= 0b11111 << G.me.x - 4;
				break;
			case 56:
				explored[52] |= 0b111 << G.me.x - 2;
				explored[53] |= 0b1111 << G.me.x - 3;
				explored[54] |= 0b11111 << G.me.x - 4;
				explored[55] |= 0b11111 << G.me.x - 4;
				explored[56] |= 0b11111 << G.me.x - 4;
				break;
			case 57:
				explored[53] |= 0b111 << G.me.x - 2;
				explored[54] |= 0b1111 << G.me.x - 3;
				explored[55] |= 0b11111 << G.me.x - 4;
				explored[56] |= 0b11111 << G.me.x - 4;
				explored[57] |= 0b11111 << G.me.x - 4;
				break;
			case 58:
				explored[54] |= 0b111 << G.me.x - 2;
				explored[55] |= 0b1111 << G.me.x - 3;
				explored[56] |= 0b11111 << G.me.x - 4;
				explored[57] |= 0b11111 << G.me.x - 4;
				explored[58] |= 0b11111 << G.me.x - 4;
				break;
			case 59:
				explored[55] |= 0b111 << G.me.x - 2;
				explored[56] |= 0b1111 << G.me.x - 3;
				explored[57] |= 0b11111 << G.me.x - 4;
				explored[58] |= 0b11111 << G.me.x - 4;
				explored[59] |= 0b11111 << G.me.x - 4;
				break;
		}
	}
	public static void updateExploredBabyRat1() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b1 << G.me.x;
				break;
			case 1:
				explored[0] |= 0b111 << G.me.x - 1;
				explored[1] |= 0b1 << G.me.x;
				break;
			case 2:
				explored[0] |= 0b11111 << G.me.x - 2;
				explored[1] |= 0b111 << G.me.x - 1;
				explored[2] |= 0b1 << G.me.x;
				break;
			case 3:
				explored[0] |= 0b1111111 << G.me.x - 3;
				explored[1] |= 0b11111 << G.me.x - 2;
				explored[2] |= 0b111 << G.me.x - 1;
				explored[3] |= 0b1 << G.me.x;
				break;
			case 4:
				explored[0] |= 0b11111 << G.me.x - 2;
				explored[1] |= 0b1111111 << G.me.x - 3;
				explored[2] |= 0b11111 << G.me.x - 2;
				explored[3] |= 0b111 << G.me.x - 1;
				explored[4] |= 0b1 << G.me.x;
				break;
			case 5:
				explored[1] |= 0b11111 << G.me.x - 2;
				explored[2] |= 0b1111111 << G.me.x - 3;
				explored[3] |= 0b11111 << G.me.x - 2;
				explored[4] |= 0b111 << G.me.x - 1;
				explored[5] |= 0b1 << G.me.x;
				break;
			case 6:
				explored[2] |= 0b11111 << G.me.x - 2;
				explored[3] |= 0b1111111 << G.me.x - 3;
				explored[4] |= 0b11111 << G.me.x - 2;
				explored[5] |= 0b111 << G.me.x - 1;
				explored[6] |= 0b1 << G.me.x;
				break;
			case 7:
				explored[3] |= 0b11111 << G.me.x - 2;
				explored[4] |= 0b1111111 << G.me.x - 3;
				explored[5] |= 0b11111 << G.me.x - 2;
				explored[6] |= 0b111 << G.me.x - 1;
				explored[7] |= 0b1 << G.me.x;
				break;
			case 8:
				explored[4] |= 0b11111 << G.me.x - 2;
				explored[5] |= 0b1111111 << G.me.x - 3;
				explored[6] |= 0b11111 << G.me.x - 2;
				explored[7] |= 0b111 << G.me.x - 1;
				explored[8] |= 0b1 << G.me.x;
				break;
			case 9:
				explored[5] |= 0b11111 << G.me.x - 2;
				explored[6] |= 0b1111111 << G.me.x - 3;
				explored[7] |= 0b11111 << G.me.x - 2;
				explored[8] |= 0b111 << G.me.x - 1;
				explored[9] |= 0b1 << G.me.x;
				break;
			case 10:
				explored[6] |= 0b11111 << G.me.x - 2;
				explored[7] |= 0b1111111 << G.me.x - 3;
				explored[8] |= 0b11111 << G.me.x - 2;
				explored[9] |= 0b111 << G.me.x - 1;
				explored[10] |= 0b1 << G.me.x;
				break;
			case 11:
				explored[7] |= 0b11111 << G.me.x - 2;
				explored[8] |= 0b1111111 << G.me.x - 3;
				explored[9] |= 0b11111 << G.me.x - 2;
				explored[10] |= 0b111 << G.me.x - 1;
				explored[11] |= 0b1 << G.me.x;
				break;
			case 12:
				explored[8] |= 0b11111 << G.me.x - 2;
				explored[9] |= 0b1111111 << G.me.x - 3;
				explored[10] |= 0b11111 << G.me.x - 2;
				explored[11] |= 0b111 << G.me.x - 1;
				explored[12] |= 0b1 << G.me.x;
				break;
			case 13:
				explored[9] |= 0b11111 << G.me.x - 2;
				explored[10] |= 0b1111111 << G.me.x - 3;
				explored[11] |= 0b11111 << G.me.x - 2;
				explored[12] |= 0b111 << G.me.x - 1;
				explored[13] |= 0b1 << G.me.x;
				break;
			case 14:
				explored[10] |= 0b11111 << G.me.x - 2;
				explored[11] |= 0b1111111 << G.me.x - 3;
				explored[12] |= 0b11111 << G.me.x - 2;
				explored[13] |= 0b111 << G.me.x - 1;
				explored[14] |= 0b1 << G.me.x;
				break;
			case 15:
				explored[11] |= 0b11111 << G.me.x - 2;
				explored[12] |= 0b1111111 << G.me.x - 3;
				explored[13] |= 0b11111 << G.me.x - 2;
				explored[14] |= 0b111 << G.me.x - 1;
				explored[15] |= 0b1 << G.me.x;
				break;
			case 16:
				explored[12] |= 0b11111 << G.me.x - 2;
				explored[13] |= 0b1111111 << G.me.x - 3;
				explored[14] |= 0b11111 << G.me.x - 2;
				explored[15] |= 0b111 << G.me.x - 1;
				explored[16] |= 0b1 << G.me.x;
				break;
			case 17:
				explored[13] |= 0b11111 << G.me.x - 2;
				explored[14] |= 0b1111111 << G.me.x - 3;
				explored[15] |= 0b11111 << G.me.x - 2;
				explored[16] |= 0b111 << G.me.x - 1;
				explored[17] |= 0b1 << G.me.x;
				break;
			case 18:
				explored[14] |= 0b11111 << G.me.x - 2;
				explored[15] |= 0b1111111 << G.me.x - 3;
				explored[16] |= 0b11111 << G.me.x - 2;
				explored[17] |= 0b111 << G.me.x - 1;
				explored[18] |= 0b1 << G.me.x;
				break;
			case 19:
				explored[15] |= 0b11111 << G.me.x - 2;
				explored[16] |= 0b1111111 << G.me.x - 3;
				explored[17] |= 0b11111 << G.me.x - 2;
				explored[18] |= 0b111 << G.me.x - 1;
				explored[19] |= 0b1 << G.me.x;
				break;
			case 20:
				explored[16] |= 0b11111 << G.me.x - 2;
				explored[17] |= 0b1111111 << G.me.x - 3;
				explored[18] |= 0b11111 << G.me.x - 2;
				explored[19] |= 0b111 << G.me.x - 1;
				explored[20] |= 0b1 << G.me.x;
				break;
			case 21:
				explored[17] |= 0b11111 << G.me.x - 2;
				explored[18] |= 0b1111111 << G.me.x - 3;
				explored[19] |= 0b11111 << G.me.x - 2;
				explored[20] |= 0b111 << G.me.x - 1;
				explored[21] |= 0b1 << G.me.x;
				break;
			case 22:
				explored[18] |= 0b11111 << G.me.x - 2;
				explored[19] |= 0b1111111 << G.me.x - 3;
				explored[20] |= 0b11111 << G.me.x - 2;
				explored[21] |= 0b111 << G.me.x - 1;
				explored[22] |= 0b1 << G.me.x;
				break;
			case 23:
				explored[19] |= 0b11111 << G.me.x - 2;
				explored[20] |= 0b1111111 << G.me.x - 3;
				explored[21] |= 0b11111 << G.me.x - 2;
				explored[22] |= 0b111 << G.me.x - 1;
				explored[23] |= 0b1 << G.me.x;
				break;
			case 24:
				explored[20] |= 0b11111 << G.me.x - 2;
				explored[21] |= 0b1111111 << G.me.x - 3;
				explored[22] |= 0b11111 << G.me.x - 2;
				explored[23] |= 0b111 << G.me.x - 1;
				explored[24] |= 0b1 << G.me.x;
				break;
			case 25:
				explored[21] |= 0b11111 << G.me.x - 2;
				explored[22] |= 0b1111111 << G.me.x - 3;
				explored[23] |= 0b11111 << G.me.x - 2;
				explored[24] |= 0b111 << G.me.x - 1;
				explored[25] |= 0b1 << G.me.x;
				break;
			case 26:
				explored[22] |= 0b11111 << G.me.x - 2;
				explored[23] |= 0b1111111 << G.me.x - 3;
				explored[24] |= 0b11111 << G.me.x - 2;
				explored[25] |= 0b111 << G.me.x - 1;
				explored[26] |= 0b1 << G.me.x;
				break;
			case 27:
				explored[23] |= 0b11111 << G.me.x - 2;
				explored[24] |= 0b1111111 << G.me.x - 3;
				explored[25] |= 0b11111 << G.me.x - 2;
				explored[26] |= 0b111 << G.me.x - 1;
				explored[27] |= 0b1 << G.me.x;
				break;
			case 28:
				explored[24] |= 0b11111 << G.me.x - 2;
				explored[25] |= 0b1111111 << G.me.x - 3;
				explored[26] |= 0b11111 << G.me.x - 2;
				explored[27] |= 0b111 << G.me.x - 1;
				explored[28] |= 0b1 << G.me.x;
				break;
			case 29:
				explored[25] |= 0b11111 << G.me.x - 2;
				explored[26] |= 0b1111111 << G.me.x - 3;
				explored[27] |= 0b11111 << G.me.x - 2;
				explored[28] |= 0b111 << G.me.x - 1;
				explored[29] |= 0b1 << G.me.x;
				break;
			case 30:
				explored[26] |= 0b11111 << G.me.x - 2;
				explored[27] |= 0b1111111 << G.me.x - 3;
				explored[28] |= 0b11111 << G.me.x - 2;
				explored[29] |= 0b111 << G.me.x - 1;
				explored[30] |= 0b1 << G.me.x;
				break;
			case 31:
				explored[27] |= 0b11111 << G.me.x - 2;
				explored[28] |= 0b1111111 << G.me.x - 3;
				explored[29] |= 0b11111 << G.me.x - 2;
				explored[30] |= 0b111 << G.me.x - 1;
				explored[31] |= 0b1 << G.me.x;
				break;
			case 32:
				explored[28] |= 0b11111 << G.me.x - 2;
				explored[29] |= 0b1111111 << G.me.x - 3;
				explored[30] |= 0b11111 << G.me.x - 2;
				explored[31] |= 0b111 << G.me.x - 1;
				explored[32] |= 0b1 << G.me.x;
				break;
			case 33:
				explored[29] |= 0b11111 << G.me.x - 2;
				explored[30] |= 0b1111111 << G.me.x - 3;
				explored[31] |= 0b11111 << G.me.x - 2;
				explored[32] |= 0b111 << G.me.x - 1;
				explored[33] |= 0b1 << G.me.x;
				break;
			case 34:
				explored[30] |= 0b11111 << G.me.x - 2;
				explored[31] |= 0b1111111 << G.me.x - 3;
				explored[32] |= 0b11111 << G.me.x - 2;
				explored[33] |= 0b111 << G.me.x - 1;
				explored[34] |= 0b1 << G.me.x;
				break;
			case 35:
				explored[31] |= 0b11111 << G.me.x - 2;
				explored[32] |= 0b1111111 << G.me.x - 3;
				explored[33] |= 0b11111 << G.me.x - 2;
				explored[34] |= 0b111 << G.me.x - 1;
				explored[35] |= 0b1 << G.me.x;
				break;
			case 36:
				explored[32] |= 0b11111 << G.me.x - 2;
				explored[33] |= 0b1111111 << G.me.x - 3;
				explored[34] |= 0b11111 << G.me.x - 2;
				explored[35] |= 0b111 << G.me.x - 1;
				explored[36] |= 0b1 << G.me.x;
				break;
			case 37:
				explored[33] |= 0b11111 << G.me.x - 2;
				explored[34] |= 0b1111111 << G.me.x - 3;
				explored[35] |= 0b11111 << G.me.x - 2;
				explored[36] |= 0b111 << G.me.x - 1;
				explored[37] |= 0b1 << G.me.x;
				break;
			case 38:
				explored[34] |= 0b11111 << G.me.x - 2;
				explored[35] |= 0b1111111 << G.me.x - 3;
				explored[36] |= 0b11111 << G.me.x - 2;
				explored[37] |= 0b111 << G.me.x - 1;
				explored[38] |= 0b1 << G.me.x;
				break;
			case 39:
				explored[35] |= 0b11111 << G.me.x - 2;
				explored[36] |= 0b1111111 << G.me.x - 3;
				explored[37] |= 0b11111 << G.me.x - 2;
				explored[38] |= 0b111 << G.me.x - 1;
				explored[39] |= 0b1 << G.me.x;
				break;
			case 40:
				explored[36] |= 0b11111 << G.me.x - 2;
				explored[37] |= 0b1111111 << G.me.x - 3;
				explored[38] |= 0b11111 << G.me.x - 2;
				explored[39] |= 0b111 << G.me.x - 1;
				explored[40] |= 0b1 << G.me.x;
				break;
			case 41:
				explored[37] |= 0b11111 << G.me.x - 2;
				explored[38] |= 0b1111111 << G.me.x - 3;
				explored[39] |= 0b11111 << G.me.x - 2;
				explored[40] |= 0b111 << G.me.x - 1;
				explored[41] |= 0b1 << G.me.x;
				break;
			case 42:
				explored[38] |= 0b11111 << G.me.x - 2;
				explored[39] |= 0b1111111 << G.me.x - 3;
				explored[40] |= 0b11111 << G.me.x - 2;
				explored[41] |= 0b111 << G.me.x - 1;
				explored[42] |= 0b1 << G.me.x;
				break;
			case 43:
				explored[39] |= 0b11111 << G.me.x - 2;
				explored[40] |= 0b1111111 << G.me.x - 3;
				explored[41] |= 0b11111 << G.me.x - 2;
				explored[42] |= 0b111 << G.me.x - 1;
				explored[43] |= 0b1 << G.me.x;
				break;
			case 44:
				explored[40] |= 0b11111 << G.me.x - 2;
				explored[41] |= 0b1111111 << G.me.x - 3;
				explored[42] |= 0b11111 << G.me.x - 2;
				explored[43] |= 0b111 << G.me.x - 1;
				explored[44] |= 0b1 << G.me.x;
				break;
			case 45:
				explored[41] |= 0b11111 << G.me.x - 2;
				explored[42] |= 0b1111111 << G.me.x - 3;
				explored[43] |= 0b11111 << G.me.x - 2;
				explored[44] |= 0b111 << G.me.x - 1;
				explored[45] |= 0b1 << G.me.x;
				break;
			case 46:
				explored[42] |= 0b11111 << G.me.x - 2;
				explored[43] |= 0b1111111 << G.me.x - 3;
				explored[44] |= 0b11111 << G.me.x - 2;
				explored[45] |= 0b111 << G.me.x - 1;
				explored[46] |= 0b1 << G.me.x;
				break;
			case 47:
				explored[43] |= 0b11111 << G.me.x - 2;
				explored[44] |= 0b1111111 << G.me.x - 3;
				explored[45] |= 0b11111 << G.me.x - 2;
				explored[46] |= 0b111 << G.me.x - 1;
				explored[47] |= 0b1 << G.me.x;
				break;
			case 48:
				explored[44] |= 0b11111 << G.me.x - 2;
				explored[45] |= 0b1111111 << G.me.x - 3;
				explored[46] |= 0b11111 << G.me.x - 2;
				explored[47] |= 0b111 << G.me.x - 1;
				explored[48] |= 0b1 << G.me.x;
				break;
			case 49:
				explored[45] |= 0b11111 << G.me.x - 2;
				explored[46] |= 0b1111111 << G.me.x - 3;
				explored[47] |= 0b11111 << G.me.x - 2;
				explored[48] |= 0b111 << G.me.x - 1;
				explored[49] |= 0b1 << G.me.x;
				break;
			case 50:
				explored[46] |= 0b11111 << G.me.x - 2;
				explored[47] |= 0b1111111 << G.me.x - 3;
				explored[48] |= 0b11111 << G.me.x - 2;
				explored[49] |= 0b111 << G.me.x - 1;
				explored[50] |= 0b1 << G.me.x;
				break;
			case 51:
				explored[47] |= 0b11111 << G.me.x - 2;
				explored[48] |= 0b1111111 << G.me.x - 3;
				explored[49] |= 0b11111 << G.me.x - 2;
				explored[50] |= 0b111 << G.me.x - 1;
				explored[51] |= 0b1 << G.me.x;
				break;
			case 52:
				explored[48] |= 0b11111 << G.me.x - 2;
				explored[49] |= 0b1111111 << G.me.x - 3;
				explored[50] |= 0b11111 << G.me.x - 2;
				explored[51] |= 0b111 << G.me.x - 1;
				explored[52] |= 0b1 << G.me.x;
				break;
			case 53:
				explored[49] |= 0b11111 << G.me.x - 2;
				explored[50] |= 0b1111111 << G.me.x - 3;
				explored[51] |= 0b11111 << G.me.x - 2;
				explored[52] |= 0b111 << G.me.x - 1;
				explored[53] |= 0b1 << G.me.x;
				break;
			case 54:
				explored[50] |= 0b11111 << G.me.x - 2;
				explored[51] |= 0b1111111 << G.me.x - 3;
				explored[52] |= 0b11111 << G.me.x - 2;
				explored[53] |= 0b111 << G.me.x - 1;
				explored[54] |= 0b1 << G.me.x;
				break;
			case 55:
				explored[51] |= 0b11111 << G.me.x - 2;
				explored[52] |= 0b1111111 << G.me.x - 3;
				explored[53] |= 0b11111 << G.me.x - 2;
				explored[54] |= 0b111 << G.me.x - 1;
				explored[55] |= 0b1 << G.me.x;
				break;
			case 56:
				explored[52] |= 0b11111 << G.me.x - 2;
				explored[53] |= 0b1111111 << G.me.x - 3;
				explored[54] |= 0b11111 << G.me.x - 2;
				explored[55] |= 0b111 << G.me.x - 1;
				explored[56] |= 0b1 << G.me.x;
				break;
			case 57:
				explored[53] |= 0b11111 << G.me.x - 2;
				explored[54] |= 0b1111111 << G.me.x - 3;
				explored[55] |= 0b11111 << G.me.x - 2;
				explored[56] |= 0b111 << G.me.x - 1;
				explored[57] |= 0b1 << G.me.x;
				break;
			case 58:
				explored[54] |= 0b11111 << G.me.x - 2;
				explored[55] |= 0b1111111 << G.me.x - 3;
				explored[56] |= 0b11111 << G.me.x - 2;
				explored[57] |= 0b111 << G.me.x - 1;
				explored[58] |= 0b1 << G.me.x;
				break;
			case 59:
				explored[55] |= 0b11111 << G.me.x - 2;
				explored[56] |= 0b1111111 << G.me.x - 3;
				explored[57] |= 0b11111 << G.me.x - 2;
				explored[58] |= 0b111 << G.me.x - 1;
				explored[59] |= 0b1 << G.me.x;
				break;
		}
	}
	public static void updateExploredBabyRat2() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111 << G.me.x;
				break;
			case 1:
				explored[0] |= 0b11111 << G.me.x;
				explored[1] |= 0b11111 << G.me.x;
				break;
			case 2:
				explored[0] |= 0b11111 << G.me.x;
				explored[1] |= 0b11111 << G.me.x;
				explored[2] |= 0b11111 << G.me.x;
				break;
			case 3:
				explored[0] |= 0b1111 << G.me.x;
				explored[1] |= 0b11111 << G.me.x;
				explored[2] |= 0b11111 << G.me.x;
				explored[3] |= 0b11111 << G.me.x;
				break;
			case 4:
				explored[0] |= 0b111 << G.me.x;
				explored[1] |= 0b1111 << G.me.x;
				explored[2] |= 0b11111 << G.me.x;
				explored[3] |= 0b11111 << G.me.x;
				explored[4] |= 0b11111 << G.me.x;
				break;
			case 5:
				explored[1] |= 0b111 << G.me.x;
				explored[2] |= 0b1111 << G.me.x;
				explored[3] |= 0b11111 << G.me.x;
				explored[4] |= 0b11111 << G.me.x;
				explored[5] |= 0b11111 << G.me.x;
				break;
			case 6:
				explored[2] |= 0b111 << G.me.x;
				explored[3] |= 0b1111 << G.me.x;
				explored[4] |= 0b11111 << G.me.x;
				explored[5] |= 0b11111 << G.me.x;
				explored[6] |= 0b11111 << G.me.x;
				break;
			case 7:
				explored[3] |= 0b111 << G.me.x;
				explored[4] |= 0b1111 << G.me.x;
				explored[5] |= 0b11111 << G.me.x;
				explored[6] |= 0b11111 << G.me.x;
				explored[7] |= 0b11111 << G.me.x;
				break;
			case 8:
				explored[4] |= 0b111 << G.me.x;
				explored[5] |= 0b1111 << G.me.x;
				explored[6] |= 0b11111 << G.me.x;
				explored[7] |= 0b11111 << G.me.x;
				explored[8] |= 0b11111 << G.me.x;
				break;
			case 9:
				explored[5] |= 0b111 << G.me.x;
				explored[6] |= 0b1111 << G.me.x;
				explored[7] |= 0b11111 << G.me.x;
				explored[8] |= 0b11111 << G.me.x;
				explored[9] |= 0b11111 << G.me.x;
				break;
			case 10:
				explored[6] |= 0b111 << G.me.x;
				explored[7] |= 0b1111 << G.me.x;
				explored[8] |= 0b11111 << G.me.x;
				explored[9] |= 0b11111 << G.me.x;
				explored[10] |= 0b11111 << G.me.x;
				break;
			case 11:
				explored[7] |= 0b111 << G.me.x;
				explored[8] |= 0b1111 << G.me.x;
				explored[9] |= 0b11111 << G.me.x;
				explored[10] |= 0b11111 << G.me.x;
				explored[11] |= 0b11111 << G.me.x;
				break;
			case 12:
				explored[8] |= 0b111 << G.me.x;
				explored[9] |= 0b1111 << G.me.x;
				explored[10] |= 0b11111 << G.me.x;
				explored[11] |= 0b11111 << G.me.x;
				explored[12] |= 0b11111 << G.me.x;
				break;
			case 13:
				explored[9] |= 0b111 << G.me.x;
				explored[10] |= 0b1111 << G.me.x;
				explored[11] |= 0b11111 << G.me.x;
				explored[12] |= 0b11111 << G.me.x;
				explored[13] |= 0b11111 << G.me.x;
				break;
			case 14:
				explored[10] |= 0b111 << G.me.x;
				explored[11] |= 0b1111 << G.me.x;
				explored[12] |= 0b11111 << G.me.x;
				explored[13] |= 0b11111 << G.me.x;
				explored[14] |= 0b11111 << G.me.x;
				break;
			case 15:
				explored[11] |= 0b111 << G.me.x;
				explored[12] |= 0b1111 << G.me.x;
				explored[13] |= 0b11111 << G.me.x;
				explored[14] |= 0b11111 << G.me.x;
				explored[15] |= 0b11111 << G.me.x;
				break;
			case 16:
				explored[12] |= 0b111 << G.me.x;
				explored[13] |= 0b1111 << G.me.x;
				explored[14] |= 0b11111 << G.me.x;
				explored[15] |= 0b11111 << G.me.x;
				explored[16] |= 0b11111 << G.me.x;
				break;
			case 17:
				explored[13] |= 0b111 << G.me.x;
				explored[14] |= 0b1111 << G.me.x;
				explored[15] |= 0b11111 << G.me.x;
				explored[16] |= 0b11111 << G.me.x;
				explored[17] |= 0b11111 << G.me.x;
				break;
			case 18:
				explored[14] |= 0b111 << G.me.x;
				explored[15] |= 0b1111 << G.me.x;
				explored[16] |= 0b11111 << G.me.x;
				explored[17] |= 0b11111 << G.me.x;
				explored[18] |= 0b11111 << G.me.x;
				break;
			case 19:
				explored[15] |= 0b111 << G.me.x;
				explored[16] |= 0b1111 << G.me.x;
				explored[17] |= 0b11111 << G.me.x;
				explored[18] |= 0b11111 << G.me.x;
				explored[19] |= 0b11111 << G.me.x;
				break;
			case 20:
				explored[16] |= 0b111 << G.me.x;
				explored[17] |= 0b1111 << G.me.x;
				explored[18] |= 0b11111 << G.me.x;
				explored[19] |= 0b11111 << G.me.x;
				explored[20] |= 0b11111 << G.me.x;
				break;
			case 21:
				explored[17] |= 0b111 << G.me.x;
				explored[18] |= 0b1111 << G.me.x;
				explored[19] |= 0b11111 << G.me.x;
				explored[20] |= 0b11111 << G.me.x;
				explored[21] |= 0b11111 << G.me.x;
				break;
			case 22:
				explored[18] |= 0b111 << G.me.x;
				explored[19] |= 0b1111 << G.me.x;
				explored[20] |= 0b11111 << G.me.x;
				explored[21] |= 0b11111 << G.me.x;
				explored[22] |= 0b11111 << G.me.x;
				break;
			case 23:
				explored[19] |= 0b111 << G.me.x;
				explored[20] |= 0b1111 << G.me.x;
				explored[21] |= 0b11111 << G.me.x;
				explored[22] |= 0b11111 << G.me.x;
				explored[23] |= 0b11111 << G.me.x;
				break;
			case 24:
				explored[20] |= 0b111 << G.me.x;
				explored[21] |= 0b1111 << G.me.x;
				explored[22] |= 0b11111 << G.me.x;
				explored[23] |= 0b11111 << G.me.x;
				explored[24] |= 0b11111 << G.me.x;
				break;
			case 25:
				explored[21] |= 0b111 << G.me.x;
				explored[22] |= 0b1111 << G.me.x;
				explored[23] |= 0b11111 << G.me.x;
				explored[24] |= 0b11111 << G.me.x;
				explored[25] |= 0b11111 << G.me.x;
				break;
			case 26:
				explored[22] |= 0b111 << G.me.x;
				explored[23] |= 0b1111 << G.me.x;
				explored[24] |= 0b11111 << G.me.x;
				explored[25] |= 0b11111 << G.me.x;
				explored[26] |= 0b11111 << G.me.x;
				break;
			case 27:
				explored[23] |= 0b111 << G.me.x;
				explored[24] |= 0b1111 << G.me.x;
				explored[25] |= 0b11111 << G.me.x;
				explored[26] |= 0b11111 << G.me.x;
				explored[27] |= 0b11111 << G.me.x;
				break;
			case 28:
				explored[24] |= 0b111 << G.me.x;
				explored[25] |= 0b1111 << G.me.x;
				explored[26] |= 0b11111 << G.me.x;
				explored[27] |= 0b11111 << G.me.x;
				explored[28] |= 0b11111 << G.me.x;
				break;
			case 29:
				explored[25] |= 0b111 << G.me.x;
				explored[26] |= 0b1111 << G.me.x;
				explored[27] |= 0b11111 << G.me.x;
				explored[28] |= 0b11111 << G.me.x;
				explored[29] |= 0b11111 << G.me.x;
				break;
			case 30:
				explored[26] |= 0b111 << G.me.x;
				explored[27] |= 0b1111 << G.me.x;
				explored[28] |= 0b11111 << G.me.x;
				explored[29] |= 0b11111 << G.me.x;
				explored[30] |= 0b11111 << G.me.x;
				break;
			case 31:
				explored[27] |= 0b111 << G.me.x;
				explored[28] |= 0b1111 << G.me.x;
				explored[29] |= 0b11111 << G.me.x;
				explored[30] |= 0b11111 << G.me.x;
				explored[31] |= 0b11111 << G.me.x;
				break;
			case 32:
				explored[28] |= 0b111 << G.me.x;
				explored[29] |= 0b1111 << G.me.x;
				explored[30] |= 0b11111 << G.me.x;
				explored[31] |= 0b11111 << G.me.x;
				explored[32] |= 0b11111 << G.me.x;
				break;
			case 33:
				explored[29] |= 0b111 << G.me.x;
				explored[30] |= 0b1111 << G.me.x;
				explored[31] |= 0b11111 << G.me.x;
				explored[32] |= 0b11111 << G.me.x;
				explored[33] |= 0b11111 << G.me.x;
				break;
			case 34:
				explored[30] |= 0b111 << G.me.x;
				explored[31] |= 0b1111 << G.me.x;
				explored[32] |= 0b11111 << G.me.x;
				explored[33] |= 0b11111 << G.me.x;
				explored[34] |= 0b11111 << G.me.x;
				break;
			case 35:
				explored[31] |= 0b111 << G.me.x;
				explored[32] |= 0b1111 << G.me.x;
				explored[33] |= 0b11111 << G.me.x;
				explored[34] |= 0b11111 << G.me.x;
				explored[35] |= 0b11111 << G.me.x;
				break;
			case 36:
				explored[32] |= 0b111 << G.me.x;
				explored[33] |= 0b1111 << G.me.x;
				explored[34] |= 0b11111 << G.me.x;
				explored[35] |= 0b11111 << G.me.x;
				explored[36] |= 0b11111 << G.me.x;
				break;
			case 37:
				explored[33] |= 0b111 << G.me.x;
				explored[34] |= 0b1111 << G.me.x;
				explored[35] |= 0b11111 << G.me.x;
				explored[36] |= 0b11111 << G.me.x;
				explored[37] |= 0b11111 << G.me.x;
				break;
			case 38:
				explored[34] |= 0b111 << G.me.x;
				explored[35] |= 0b1111 << G.me.x;
				explored[36] |= 0b11111 << G.me.x;
				explored[37] |= 0b11111 << G.me.x;
				explored[38] |= 0b11111 << G.me.x;
				break;
			case 39:
				explored[35] |= 0b111 << G.me.x;
				explored[36] |= 0b1111 << G.me.x;
				explored[37] |= 0b11111 << G.me.x;
				explored[38] |= 0b11111 << G.me.x;
				explored[39] |= 0b11111 << G.me.x;
				break;
			case 40:
				explored[36] |= 0b111 << G.me.x;
				explored[37] |= 0b1111 << G.me.x;
				explored[38] |= 0b11111 << G.me.x;
				explored[39] |= 0b11111 << G.me.x;
				explored[40] |= 0b11111 << G.me.x;
				break;
			case 41:
				explored[37] |= 0b111 << G.me.x;
				explored[38] |= 0b1111 << G.me.x;
				explored[39] |= 0b11111 << G.me.x;
				explored[40] |= 0b11111 << G.me.x;
				explored[41] |= 0b11111 << G.me.x;
				break;
			case 42:
				explored[38] |= 0b111 << G.me.x;
				explored[39] |= 0b1111 << G.me.x;
				explored[40] |= 0b11111 << G.me.x;
				explored[41] |= 0b11111 << G.me.x;
				explored[42] |= 0b11111 << G.me.x;
				break;
			case 43:
				explored[39] |= 0b111 << G.me.x;
				explored[40] |= 0b1111 << G.me.x;
				explored[41] |= 0b11111 << G.me.x;
				explored[42] |= 0b11111 << G.me.x;
				explored[43] |= 0b11111 << G.me.x;
				break;
			case 44:
				explored[40] |= 0b111 << G.me.x;
				explored[41] |= 0b1111 << G.me.x;
				explored[42] |= 0b11111 << G.me.x;
				explored[43] |= 0b11111 << G.me.x;
				explored[44] |= 0b11111 << G.me.x;
				break;
			case 45:
				explored[41] |= 0b111 << G.me.x;
				explored[42] |= 0b1111 << G.me.x;
				explored[43] |= 0b11111 << G.me.x;
				explored[44] |= 0b11111 << G.me.x;
				explored[45] |= 0b11111 << G.me.x;
				break;
			case 46:
				explored[42] |= 0b111 << G.me.x;
				explored[43] |= 0b1111 << G.me.x;
				explored[44] |= 0b11111 << G.me.x;
				explored[45] |= 0b11111 << G.me.x;
				explored[46] |= 0b11111 << G.me.x;
				break;
			case 47:
				explored[43] |= 0b111 << G.me.x;
				explored[44] |= 0b1111 << G.me.x;
				explored[45] |= 0b11111 << G.me.x;
				explored[46] |= 0b11111 << G.me.x;
				explored[47] |= 0b11111 << G.me.x;
				break;
			case 48:
				explored[44] |= 0b111 << G.me.x;
				explored[45] |= 0b1111 << G.me.x;
				explored[46] |= 0b11111 << G.me.x;
				explored[47] |= 0b11111 << G.me.x;
				explored[48] |= 0b11111 << G.me.x;
				break;
			case 49:
				explored[45] |= 0b111 << G.me.x;
				explored[46] |= 0b1111 << G.me.x;
				explored[47] |= 0b11111 << G.me.x;
				explored[48] |= 0b11111 << G.me.x;
				explored[49] |= 0b11111 << G.me.x;
				break;
			case 50:
				explored[46] |= 0b111 << G.me.x;
				explored[47] |= 0b1111 << G.me.x;
				explored[48] |= 0b11111 << G.me.x;
				explored[49] |= 0b11111 << G.me.x;
				explored[50] |= 0b11111 << G.me.x;
				break;
			case 51:
				explored[47] |= 0b111 << G.me.x;
				explored[48] |= 0b1111 << G.me.x;
				explored[49] |= 0b11111 << G.me.x;
				explored[50] |= 0b11111 << G.me.x;
				explored[51] |= 0b11111 << G.me.x;
				break;
			case 52:
				explored[48] |= 0b111 << G.me.x;
				explored[49] |= 0b1111 << G.me.x;
				explored[50] |= 0b11111 << G.me.x;
				explored[51] |= 0b11111 << G.me.x;
				explored[52] |= 0b11111 << G.me.x;
				break;
			case 53:
				explored[49] |= 0b111 << G.me.x;
				explored[50] |= 0b1111 << G.me.x;
				explored[51] |= 0b11111 << G.me.x;
				explored[52] |= 0b11111 << G.me.x;
				explored[53] |= 0b11111 << G.me.x;
				break;
			case 54:
				explored[50] |= 0b111 << G.me.x;
				explored[51] |= 0b1111 << G.me.x;
				explored[52] |= 0b11111 << G.me.x;
				explored[53] |= 0b11111 << G.me.x;
				explored[54] |= 0b11111 << G.me.x;
				break;
			case 55:
				explored[51] |= 0b111 << G.me.x;
				explored[52] |= 0b1111 << G.me.x;
				explored[53] |= 0b11111 << G.me.x;
				explored[54] |= 0b11111 << G.me.x;
				explored[55] |= 0b11111 << G.me.x;
				break;
			case 56:
				explored[52] |= 0b111 << G.me.x;
				explored[53] |= 0b1111 << G.me.x;
				explored[54] |= 0b11111 << G.me.x;
				explored[55] |= 0b11111 << G.me.x;
				explored[56] |= 0b11111 << G.me.x;
				break;
			case 57:
				explored[53] |= 0b111 << G.me.x;
				explored[54] |= 0b1111 << G.me.x;
				explored[55] |= 0b11111 << G.me.x;
				explored[56] |= 0b11111 << G.me.x;
				explored[57] |= 0b11111 << G.me.x;
				break;
			case 58:
				explored[54] |= 0b111 << G.me.x;
				explored[55] |= 0b1111 << G.me.x;
				explored[56] |= 0b11111 << G.me.x;
				explored[57] |= 0b11111 << G.me.x;
				explored[58] |= 0b11111 << G.me.x;
				break;
			case 59:
				explored[55] |= 0b111 << G.me.x;
				explored[56] |= 0b1111 << G.me.x;
				explored[57] |= 0b11111 << G.me.x;
				explored[58] |= 0b11111 << G.me.x;
				explored[59] |= 0b11111 << G.me.x;
				break;
		}
	}
	public static void updateExploredBabyRat3() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111 << G.me.x;
				explored[1] |= 0b1111 << G.me.x - -1;
				explored[2] |= 0b111 << G.me.x - -2;
				explored[3] |= 0b1 << G.me.x - -3;
				break;
			case 1:
				explored[0] |= 0b1111 << G.me.x - -1;
				explored[1] |= 0b11111 << G.me.x;
				explored[2] |= 0b1111 << G.me.x - -1;
				explored[3] |= 0b111 << G.me.x - -2;
				explored[4] |= 0b1 << G.me.x - -3;
				break;
			case 2:
				explored[0] |= 0b111 << G.me.x - -2;
				explored[1] |= 0b1111 << G.me.x - -1;
				explored[2] |= 0b11111 << G.me.x;
				explored[3] |= 0b1111 << G.me.x - -1;
				explored[4] |= 0b111 << G.me.x - -2;
				explored[5] |= 0b1 << G.me.x - -3;
				break;
			case 3:
				explored[0] |= 0b1 << G.me.x - -3;
				explored[1] |= 0b111 << G.me.x - -2;
				explored[2] |= 0b1111 << G.me.x - -1;
				explored[3] |= 0b11111 << G.me.x;
				explored[4] |= 0b1111 << G.me.x - -1;
				explored[5] |= 0b111 << G.me.x - -2;
				explored[6] |= 0b1 << G.me.x - -3;
				break;
			case 4:
				explored[1] |= 0b1 << G.me.x - -3;
				explored[2] |= 0b111 << G.me.x - -2;
				explored[3] |= 0b1111 << G.me.x - -1;
				explored[4] |= 0b11111 << G.me.x;
				explored[5] |= 0b1111 << G.me.x - -1;
				explored[6] |= 0b111 << G.me.x - -2;
				explored[7] |= 0b1 << G.me.x - -3;
				break;
			case 5:
				explored[2] |= 0b1 << G.me.x - -3;
				explored[3] |= 0b111 << G.me.x - -2;
				explored[4] |= 0b1111 << G.me.x - -1;
				explored[5] |= 0b11111 << G.me.x;
				explored[6] |= 0b1111 << G.me.x - -1;
				explored[7] |= 0b111 << G.me.x - -2;
				explored[8] |= 0b1 << G.me.x - -3;
				break;
			case 6:
				explored[3] |= 0b1 << G.me.x - -3;
				explored[4] |= 0b111 << G.me.x - -2;
				explored[5] |= 0b1111 << G.me.x - -1;
				explored[6] |= 0b11111 << G.me.x;
				explored[7] |= 0b1111 << G.me.x - -1;
				explored[8] |= 0b111 << G.me.x - -2;
				explored[9] |= 0b1 << G.me.x - -3;
				break;
			case 7:
				explored[4] |= 0b1 << G.me.x - -3;
				explored[5] |= 0b111 << G.me.x - -2;
				explored[6] |= 0b1111 << G.me.x - -1;
				explored[7] |= 0b11111 << G.me.x;
				explored[8] |= 0b1111 << G.me.x - -1;
				explored[9] |= 0b111 << G.me.x - -2;
				explored[10] |= 0b1 << G.me.x - -3;
				break;
			case 8:
				explored[5] |= 0b1 << G.me.x - -3;
				explored[6] |= 0b111 << G.me.x - -2;
				explored[7] |= 0b1111 << G.me.x - -1;
				explored[8] |= 0b11111 << G.me.x;
				explored[9] |= 0b1111 << G.me.x - -1;
				explored[10] |= 0b111 << G.me.x - -2;
				explored[11] |= 0b1 << G.me.x - -3;
				break;
			case 9:
				explored[6] |= 0b1 << G.me.x - -3;
				explored[7] |= 0b111 << G.me.x - -2;
				explored[8] |= 0b1111 << G.me.x - -1;
				explored[9] |= 0b11111 << G.me.x;
				explored[10] |= 0b1111 << G.me.x - -1;
				explored[11] |= 0b111 << G.me.x - -2;
				explored[12] |= 0b1 << G.me.x - -3;
				break;
			case 10:
				explored[7] |= 0b1 << G.me.x - -3;
				explored[8] |= 0b111 << G.me.x - -2;
				explored[9] |= 0b1111 << G.me.x - -1;
				explored[10] |= 0b11111 << G.me.x;
				explored[11] |= 0b1111 << G.me.x - -1;
				explored[12] |= 0b111 << G.me.x - -2;
				explored[13] |= 0b1 << G.me.x - -3;
				break;
			case 11:
				explored[8] |= 0b1 << G.me.x - -3;
				explored[9] |= 0b111 << G.me.x - -2;
				explored[10] |= 0b1111 << G.me.x - -1;
				explored[11] |= 0b11111 << G.me.x;
				explored[12] |= 0b1111 << G.me.x - -1;
				explored[13] |= 0b111 << G.me.x - -2;
				explored[14] |= 0b1 << G.me.x - -3;
				break;
			case 12:
				explored[9] |= 0b1 << G.me.x - -3;
				explored[10] |= 0b111 << G.me.x - -2;
				explored[11] |= 0b1111 << G.me.x - -1;
				explored[12] |= 0b11111 << G.me.x;
				explored[13] |= 0b1111 << G.me.x - -1;
				explored[14] |= 0b111 << G.me.x - -2;
				explored[15] |= 0b1 << G.me.x - -3;
				break;
			case 13:
				explored[10] |= 0b1 << G.me.x - -3;
				explored[11] |= 0b111 << G.me.x - -2;
				explored[12] |= 0b1111 << G.me.x - -1;
				explored[13] |= 0b11111 << G.me.x;
				explored[14] |= 0b1111 << G.me.x - -1;
				explored[15] |= 0b111 << G.me.x - -2;
				explored[16] |= 0b1 << G.me.x - -3;
				break;
			case 14:
				explored[11] |= 0b1 << G.me.x - -3;
				explored[12] |= 0b111 << G.me.x - -2;
				explored[13] |= 0b1111 << G.me.x - -1;
				explored[14] |= 0b11111 << G.me.x;
				explored[15] |= 0b1111 << G.me.x - -1;
				explored[16] |= 0b111 << G.me.x - -2;
				explored[17] |= 0b1 << G.me.x - -3;
				break;
			case 15:
				explored[12] |= 0b1 << G.me.x - -3;
				explored[13] |= 0b111 << G.me.x - -2;
				explored[14] |= 0b1111 << G.me.x - -1;
				explored[15] |= 0b11111 << G.me.x;
				explored[16] |= 0b1111 << G.me.x - -1;
				explored[17] |= 0b111 << G.me.x - -2;
				explored[18] |= 0b1 << G.me.x - -3;
				break;
			case 16:
				explored[13] |= 0b1 << G.me.x - -3;
				explored[14] |= 0b111 << G.me.x - -2;
				explored[15] |= 0b1111 << G.me.x - -1;
				explored[16] |= 0b11111 << G.me.x;
				explored[17] |= 0b1111 << G.me.x - -1;
				explored[18] |= 0b111 << G.me.x - -2;
				explored[19] |= 0b1 << G.me.x - -3;
				break;
			case 17:
				explored[14] |= 0b1 << G.me.x - -3;
				explored[15] |= 0b111 << G.me.x - -2;
				explored[16] |= 0b1111 << G.me.x - -1;
				explored[17] |= 0b11111 << G.me.x;
				explored[18] |= 0b1111 << G.me.x - -1;
				explored[19] |= 0b111 << G.me.x - -2;
				explored[20] |= 0b1 << G.me.x - -3;
				break;
			case 18:
				explored[15] |= 0b1 << G.me.x - -3;
				explored[16] |= 0b111 << G.me.x - -2;
				explored[17] |= 0b1111 << G.me.x - -1;
				explored[18] |= 0b11111 << G.me.x;
				explored[19] |= 0b1111 << G.me.x - -1;
				explored[20] |= 0b111 << G.me.x - -2;
				explored[21] |= 0b1 << G.me.x - -3;
				break;
			case 19:
				explored[16] |= 0b1 << G.me.x - -3;
				explored[17] |= 0b111 << G.me.x - -2;
				explored[18] |= 0b1111 << G.me.x - -1;
				explored[19] |= 0b11111 << G.me.x;
				explored[20] |= 0b1111 << G.me.x - -1;
				explored[21] |= 0b111 << G.me.x - -2;
				explored[22] |= 0b1 << G.me.x - -3;
				break;
			case 20:
				explored[17] |= 0b1 << G.me.x - -3;
				explored[18] |= 0b111 << G.me.x - -2;
				explored[19] |= 0b1111 << G.me.x - -1;
				explored[20] |= 0b11111 << G.me.x;
				explored[21] |= 0b1111 << G.me.x - -1;
				explored[22] |= 0b111 << G.me.x - -2;
				explored[23] |= 0b1 << G.me.x - -3;
				break;
			case 21:
				explored[18] |= 0b1 << G.me.x - -3;
				explored[19] |= 0b111 << G.me.x - -2;
				explored[20] |= 0b1111 << G.me.x - -1;
				explored[21] |= 0b11111 << G.me.x;
				explored[22] |= 0b1111 << G.me.x - -1;
				explored[23] |= 0b111 << G.me.x - -2;
				explored[24] |= 0b1 << G.me.x - -3;
				break;
			case 22:
				explored[19] |= 0b1 << G.me.x - -3;
				explored[20] |= 0b111 << G.me.x - -2;
				explored[21] |= 0b1111 << G.me.x - -1;
				explored[22] |= 0b11111 << G.me.x;
				explored[23] |= 0b1111 << G.me.x - -1;
				explored[24] |= 0b111 << G.me.x - -2;
				explored[25] |= 0b1 << G.me.x - -3;
				break;
			case 23:
				explored[20] |= 0b1 << G.me.x - -3;
				explored[21] |= 0b111 << G.me.x - -2;
				explored[22] |= 0b1111 << G.me.x - -1;
				explored[23] |= 0b11111 << G.me.x;
				explored[24] |= 0b1111 << G.me.x - -1;
				explored[25] |= 0b111 << G.me.x - -2;
				explored[26] |= 0b1 << G.me.x - -3;
				break;
			case 24:
				explored[21] |= 0b1 << G.me.x - -3;
				explored[22] |= 0b111 << G.me.x - -2;
				explored[23] |= 0b1111 << G.me.x - -1;
				explored[24] |= 0b11111 << G.me.x;
				explored[25] |= 0b1111 << G.me.x - -1;
				explored[26] |= 0b111 << G.me.x - -2;
				explored[27] |= 0b1 << G.me.x - -3;
				break;
			case 25:
				explored[22] |= 0b1 << G.me.x - -3;
				explored[23] |= 0b111 << G.me.x - -2;
				explored[24] |= 0b1111 << G.me.x - -1;
				explored[25] |= 0b11111 << G.me.x;
				explored[26] |= 0b1111 << G.me.x - -1;
				explored[27] |= 0b111 << G.me.x - -2;
				explored[28] |= 0b1 << G.me.x - -3;
				break;
			case 26:
				explored[23] |= 0b1 << G.me.x - -3;
				explored[24] |= 0b111 << G.me.x - -2;
				explored[25] |= 0b1111 << G.me.x - -1;
				explored[26] |= 0b11111 << G.me.x;
				explored[27] |= 0b1111 << G.me.x - -1;
				explored[28] |= 0b111 << G.me.x - -2;
				explored[29] |= 0b1 << G.me.x - -3;
				break;
			case 27:
				explored[24] |= 0b1 << G.me.x - -3;
				explored[25] |= 0b111 << G.me.x - -2;
				explored[26] |= 0b1111 << G.me.x - -1;
				explored[27] |= 0b11111 << G.me.x;
				explored[28] |= 0b1111 << G.me.x - -1;
				explored[29] |= 0b111 << G.me.x - -2;
				explored[30] |= 0b1 << G.me.x - -3;
				break;
			case 28:
				explored[25] |= 0b1 << G.me.x - -3;
				explored[26] |= 0b111 << G.me.x - -2;
				explored[27] |= 0b1111 << G.me.x - -1;
				explored[28] |= 0b11111 << G.me.x;
				explored[29] |= 0b1111 << G.me.x - -1;
				explored[30] |= 0b111 << G.me.x - -2;
				explored[31] |= 0b1 << G.me.x - -3;
				break;
			case 29:
				explored[26] |= 0b1 << G.me.x - -3;
				explored[27] |= 0b111 << G.me.x - -2;
				explored[28] |= 0b1111 << G.me.x - -1;
				explored[29] |= 0b11111 << G.me.x;
				explored[30] |= 0b1111 << G.me.x - -1;
				explored[31] |= 0b111 << G.me.x - -2;
				explored[32] |= 0b1 << G.me.x - -3;
				break;
			case 30:
				explored[27] |= 0b1 << G.me.x - -3;
				explored[28] |= 0b111 << G.me.x - -2;
				explored[29] |= 0b1111 << G.me.x - -1;
				explored[30] |= 0b11111 << G.me.x;
				explored[31] |= 0b1111 << G.me.x - -1;
				explored[32] |= 0b111 << G.me.x - -2;
				explored[33] |= 0b1 << G.me.x - -3;
				break;
			case 31:
				explored[28] |= 0b1 << G.me.x - -3;
				explored[29] |= 0b111 << G.me.x - -2;
				explored[30] |= 0b1111 << G.me.x - -1;
				explored[31] |= 0b11111 << G.me.x;
				explored[32] |= 0b1111 << G.me.x - -1;
				explored[33] |= 0b111 << G.me.x - -2;
				explored[34] |= 0b1 << G.me.x - -3;
				break;
			case 32:
				explored[29] |= 0b1 << G.me.x - -3;
				explored[30] |= 0b111 << G.me.x - -2;
				explored[31] |= 0b1111 << G.me.x - -1;
				explored[32] |= 0b11111 << G.me.x;
				explored[33] |= 0b1111 << G.me.x - -1;
				explored[34] |= 0b111 << G.me.x - -2;
				explored[35] |= 0b1 << G.me.x - -3;
				break;
			case 33:
				explored[30] |= 0b1 << G.me.x - -3;
				explored[31] |= 0b111 << G.me.x - -2;
				explored[32] |= 0b1111 << G.me.x - -1;
				explored[33] |= 0b11111 << G.me.x;
				explored[34] |= 0b1111 << G.me.x - -1;
				explored[35] |= 0b111 << G.me.x - -2;
				explored[36] |= 0b1 << G.me.x - -3;
				break;
			case 34:
				explored[31] |= 0b1 << G.me.x - -3;
				explored[32] |= 0b111 << G.me.x - -2;
				explored[33] |= 0b1111 << G.me.x - -1;
				explored[34] |= 0b11111 << G.me.x;
				explored[35] |= 0b1111 << G.me.x - -1;
				explored[36] |= 0b111 << G.me.x - -2;
				explored[37] |= 0b1 << G.me.x - -3;
				break;
			case 35:
				explored[32] |= 0b1 << G.me.x - -3;
				explored[33] |= 0b111 << G.me.x - -2;
				explored[34] |= 0b1111 << G.me.x - -1;
				explored[35] |= 0b11111 << G.me.x;
				explored[36] |= 0b1111 << G.me.x - -1;
				explored[37] |= 0b111 << G.me.x - -2;
				explored[38] |= 0b1 << G.me.x - -3;
				break;
			case 36:
				explored[33] |= 0b1 << G.me.x - -3;
				explored[34] |= 0b111 << G.me.x - -2;
				explored[35] |= 0b1111 << G.me.x - -1;
				explored[36] |= 0b11111 << G.me.x;
				explored[37] |= 0b1111 << G.me.x - -1;
				explored[38] |= 0b111 << G.me.x - -2;
				explored[39] |= 0b1 << G.me.x - -3;
				break;
			case 37:
				explored[34] |= 0b1 << G.me.x - -3;
				explored[35] |= 0b111 << G.me.x - -2;
				explored[36] |= 0b1111 << G.me.x - -1;
				explored[37] |= 0b11111 << G.me.x;
				explored[38] |= 0b1111 << G.me.x - -1;
				explored[39] |= 0b111 << G.me.x - -2;
				explored[40] |= 0b1 << G.me.x - -3;
				break;
			case 38:
				explored[35] |= 0b1 << G.me.x - -3;
				explored[36] |= 0b111 << G.me.x - -2;
				explored[37] |= 0b1111 << G.me.x - -1;
				explored[38] |= 0b11111 << G.me.x;
				explored[39] |= 0b1111 << G.me.x - -1;
				explored[40] |= 0b111 << G.me.x - -2;
				explored[41] |= 0b1 << G.me.x - -3;
				break;
			case 39:
				explored[36] |= 0b1 << G.me.x - -3;
				explored[37] |= 0b111 << G.me.x - -2;
				explored[38] |= 0b1111 << G.me.x - -1;
				explored[39] |= 0b11111 << G.me.x;
				explored[40] |= 0b1111 << G.me.x - -1;
				explored[41] |= 0b111 << G.me.x - -2;
				explored[42] |= 0b1 << G.me.x - -3;
				break;
			case 40:
				explored[37] |= 0b1 << G.me.x - -3;
				explored[38] |= 0b111 << G.me.x - -2;
				explored[39] |= 0b1111 << G.me.x - -1;
				explored[40] |= 0b11111 << G.me.x;
				explored[41] |= 0b1111 << G.me.x - -1;
				explored[42] |= 0b111 << G.me.x - -2;
				explored[43] |= 0b1 << G.me.x - -3;
				break;
			case 41:
				explored[38] |= 0b1 << G.me.x - -3;
				explored[39] |= 0b111 << G.me.x - -2;
				explored[40] |= 0b1111 << G.me.x - -1;
				explored[41] |= 0b11111 << G.me.x;
				explored[42] |= 0b1111 << G.me.x - -1;
				explored[43] |= 0b111 << G.me.x - -2;
				explored[44] |= 0b1 << G.me.x - -3;
				break;
			case 42:
				explored[39] |= 0b1 << G.me.x - -3;
				explored[40] |= 0b111 << G.me.x - -2;
				explored[41] |= 0b1111 << G.me.x - -1;
				explored[42] |= 0b11111 << G.me.x;
				explored[43] |= 0b1111 << G.me.x - -1;
				explored[44] |= 0b111 << G.me.x - -2;
				explored[45] |= 0b1 << G.me.x - -3;
				break;
			case 43:
				explored[40] |= 0b1 << G.me.x - -3;
				explored[41] |= 0b111 << G.me.x - -2;
				explored[42] |= 0b1111 << G.me.x - -1;
				explored[43] |= 0b11111 << G.me.x;
				explored[44] |= 0b1111 << G.me.x - -1;
				explored[45] |= 0b111 << G.me.x - -2;
				explored[46] |= 0b1 << G.me.x - -3;
				break;
			case 44:
				explored[41] |= 0b1 << G.me.x - -3;
				explored[42] |= 0b111 << G.me.x - -2;
				explored[43] |= 0b1111 << G.me.x - -1;
				explored[44] |= 0b11111 << G.me.x;
				explored[45] |= 0b1111 << G.me.x - -1;
				explored[46] |= 0b111 << G.me.x - -2;
				explored[47] |= 0b1 << G.me.x - -3;
				break;
			case 45:
				explored[42] |= 0b1 << G.me.x - -3;
				explored[43] |= 0b111 << G.me.x - -2;
				explored[44] |= 0b1111 << G.me.x - -1;
				explored[45] |= 0b11111 << G.me.x;
				explored[46] |= 0b1111 << G.me.x - -1;
				explored[47] |= 0b111 << G.me.x - -2;
				explored[48] |= 0b1 << G.me.x - -3;
				break;
			case 46:
				explored[43] |= 0b1 << G.me.x - -3;
				explored[44] |= 0b111 << G.me.x - -2;
				explored[45] |= 0b1111 << G.me.x - -1;
				explored[46] |= 0b11111 << G.me.x;
				explored[47] |= 0b1111 << G.me.x - -1;
				explored[48] |= 0b111 << G.me.x - -2;
				explored[49] |= 0b1 << G.me.x - -3;
				break;
			case 47:
				explored[44] |= 0b1 << G.me.x - -3;
				explored[45] |= 0b111 << G.me.x - -2;
				explored[46] |= 0b1111 << G.me.x - -1;
				explored[47] |= 0b11111 << G.me.x;
				explored[48] |= 0b1111 << G.me.x - -1;
				explored[49] |= 0b111 << G.me.x - -2;
				explored[50] |= 0b1 << G.me.x - -3;
				break;
			case 48:
				explored[45] |= 0b1 << G.me.x - -3;
				explored[46] |= 0b111 << G.me.x - -2;
				explored[47] |= 0b1111 << G.me.x - -1;
				explored[48] |= 0b11111 << G.me.x;
				explored[49] |= 0b1111 << G.me.x - -1;
				explored[50] |= 0b111 << G.me.x - -2;
				explored[51] |= 0b1 << G.me.x - -3;
				break;
			case 49:
				explored[46] |= 0b1 << G.me.x - -3;
				explored[47] |= 0b111 << G.me.x - -2;
				explored[48] |= 0b1111 << G.me.x - -1;
				explored[49] |= 0b11111 << G.me.x;
				explored[50] |= 0b1111 << G.me.x - -1;
				explored[51] |= 0b111 << G.me.x - -2;
				explored[52] |= 0b1 << G.me.x - -3;
				break;
			case 50:
				explored[47] |= 0b1 << G.me.x - -3;
				explored[48] |= 0b111 << G.me.x - -2;
				explored[49] |= 0b1111 << G.me.x - -1;
				explored[50] |= 0b11111 << G.me.x;
				explored[51] |= 0b1111 << G.me.x - -1;
				explored[52] |= 0b111 << G.me.x - -2;
				explored[53] |= 0b1 << G.me.x - -3;
				break;
			case 51:
				explored[48] |= 0b1 << G.me.x - -3;
				explored[49] |= 0b111 << G.me.x - -2;
				explored[50] |= 0b1111 << G.me.x - -1;
				explored[51] |= 0b11111 << G.me.x;
				explored[52] |= 0b1111 << G.me.x - -1;
				explored[53] |= 0b111 << G.me.x - -2;
				explored[54] |= 0b1 << G.me.x - -3;
				break;
			case 52:
				explored[49] |= 0b1 << G.me.x - -3;
				explored[50] |= 0b111 << G.me.x - -2;
				explored[51] |= 0b1111 << G.me.x - -1;
				explored[52] |= 0b11111 << G.me.x;
				explored[53] |= 0b1111 << G.me.x - -1;
				explored[54] |= 0b111 << G.me.x - -2;
				explored[55] |= 0b1 << G.me.x - -3;
				break;
			case 53:
				explored[50] |= 0b1 << G.me.x - -3;
				explored[51] |= 0b111 << G.me.x - -2;
				explored[52] |= 0b1111 << G.me.x - -1;
				explored[53] |= 0b11111 << G.me.x;
				explored[54] |= 0b1111 << G.me.x - -1;
				explored[55] |= 0b111 << G.me.x - -2;
				explored[56] |= 0b1 << G.me.x - -3;
				break;
			case 54:
				explored[51] |= 0b1 << G.me.x - -3;
				explored[52] |= 0b111 << G.me.x - -2;
				explored[53] |= 0b1111 << G.me.x - -1;
				explored[54] |= 0b11111 << G.me.x;
				explored[55] |= 0b1111 << G.me.x - -1;
				explored[56] |= 0b111 << G.me.x - -2;
				explored[57] |= 0b1 << G.me.x - -3;
				break;
			case 55:
				explored[52] |= 0b1 << G.me.x - -3;
				explored[53] |= 0b111 << G.me.x - -2;
				explored[54] |= 0b1111 << G.me.x - -1;
				explored[55] |= 0b11111 << G.me.x;
				explored[56] |= 0b1111 << G.me.x - -1;
				explored[57] |= 0b111 << G.me.x - -2;
				explored[58] |= 0b1 << G.me.x - -3;
				break;
			case 56:
				explored[53] |= 0b1 << G.me.x - -3;
				explored[54] |= 0b111 << G.me.x - -2;
				explored[55] |= 0b1111 << G.me.x - -1;
				explored[56] |= 0b11111 << G.me.x;
				explored[57] |= 0b1111 << G.me.x - -1;
				explored[58] |= 0b111 << G.me.x - -2;
				explored[59] |= 0b1 << G.me.x - -3;
				break;
			case 57:
				explored[54] |= 0b1 << G.me.x - -3;
				explored[55] |= 0b111 << G.me.x - -2;
				explored[56] |= 0b1111 << G.me.x - -1;
				explored[57] |= 0b11111 << G.me.x;
				explored[58] |= 0b1111 << G.me.x - -1;
				explored[59] |= 0b111 << G.me.x - -2;
				break;
			case 58:
				explored[55] |= 0b1 << G.me.x - -3;
				explored[56] |= 0b111 << G.me.x - -2;
				explored[57] |= 0b1111 << G.me.x - -1;
				explored[58] |= 0b11111 << G.me.x;
				explored[59] |= 0b1111 << G.me.x - -1;
				break;
			case 59:
				explored[56] |= 0b1 << G.me.x - -3;
				explored[57] |= 0b111 << G.me.x - -2;
				explored[58] |= 0b1111 << G.me.x - -1;
				explored[59] |= 0b11111 << G.me.x;
				break;
		}
	}
	public static void updateExploredBabyRat4() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111 << G.me.x;
				explored[1] |= 0b11111 << G.me.x;
				explored[2] |= 0b11111 << G.me.x;
				explored[3] |= 0b1111 << G.me.x;
				explored[4] |= 0b111 << G.me.x;
				break;
			case 1:
				explored[1] |= 0b11111 << G.me.x;
				explored[2] |= 0b11111 << G.me.x;
				explored[3] |= 0b11111 << G.me.x;
				explored[4] |= 0b1111 << G.me.x;
				explored[5] |= 0b111 << G.me.x;
				break;
			case 2:
				explored[2] |= 0b11111 << G.me.x;
				explored[3] |= 0b11111 << G.me.x;
				explored[4] |= 0b11111 << G.me.x;
				explored[5] |= 0b1111 << G.me.x;
				explored[6] |= 0b111 << G.me.x;
				break;
			case 3:
				explored[3] |= 0b11111 << G.me.x;
				explored[4] |= 0b11111 << G.me.x;
				explored[5] |= 0b11111 << G.me.x;
				explored[6] |= 0b1111 << G.me.x;
				explored[7] |= 0b111 << G.me.x;
				break;
			case 4:
				explored[4] |= 0b11111 << G.me.x;
				explored[5] |= 0b11111 << G.me.x;
				explored[6] |= 0b11111 << G.me.x;
				explored[7] |= 0b1111 << G.me.x;
				explored[8] |= 0b111 << G.me.x;
				break;
			case 5:
				explored[5] |= 0b11111 << G.me.x;
				explored[6] |= 0b11111 << G.me.x;
				explored[7] |= 0b11111 << G.me.x;
				explored[8] |= 0b1111 << G.me.x;
				explored[9] |= 0b111 << G.me.x;
				break;
			case 6:
				explored[6] |= 0b11111 << G.me.x;
				explored[7] |= 0b11111 << G.me.x;
				explored[8] |= 0b11111 << G.me.x;
				explored[9] |= 0b1111 << G.me.x;
				explored[10] |= 0b111 << G.me.x;
				break;
			case 7:
				explored[7] |= 0b11111 << G.me.x;
				explored[8] |= 0b11111 << G.me.x;
				explored[9] |= 0b11111 << G.me.x;
				explored[10] |= 0b1111 << G.me.x;
				explored[11] |= 0b111 << G.me.x;
				break;
			case 8:
				explored[8] |= 0b11111 << G.me.x;
				explored[9] |= 0b11111 << G.me.x;
				explored[10] |= 0b11111 << G.me.x;
				explored[11] |= 0b1111 << G.me.x;
				explored[12] |= 0b111 << G.me.x;
				break;
			case 9:
				explored[9] |= 0b11111 << G.me.x;
				explored[10] |= 0b11111 << G.me.x;
				explored[11] |= 0b11111 << G.me.x;
				explored[12] |= 0b1111 << G.me.x;
				explored[13] |= 0b111 << G.me.x;
				break;
			case 10:
				explored[10] |= 0b11111 << G.me.x;
				explored[11] |= 0b11111 << G.me.x;
				explored[12] |= 0b11111 << G.me.x;
				explored[13] |= 0b1111 << G.me.x;
				explored[14] |= 0b111 << G.me.x;
				break;
			case 11:
				explored[11] |= 0b11111 << G.me.x;
				explored[12] |= 0b11111 << G.me.x;
				explored[13] |= 0b11111 << G.me.x;
				explored[14] |= 0b1111 << G.me.x;
				explored[15] |= 0b111 << G.me.x;
				break;
			case 12:
				explored[12] |= 0b11111 << G.me.x;
				explored[13] |= 0b11111 << G.me.x;
				explored[14] |= 0b11111 << G.me.x;
				explored[15] |= 0b1111 << G.me.x;
				explored[16] |= 0b111 << G.me.x;
				break;
			case 13:
				explored[13] |= 0b11111 << G.me.x;
				explored[14] |= 0b11111 << G.me.x;
				explored[15] |= 0b11111 << G.me.x;
				explored[16] |= 0b1111 << G.me.x;
				explored[17] |= 0b111 << G.me.x;
				break;
			case 14:
				explored[14] |= 0b11111 << G.me.x;
				explored[15] |= 0b11111 << G.me.x;
				explored[16] |= 0b11111 << G.me.x;
				explored[17] |= 0b1111 << G.me.x;
				explored[18] |= 0b111 << G.me.x;
				break;
			case 15:
				explored[15] |= 0b11111 << G.me.x;
				explored[16] |= 0b11111 << G.me.x;
				explored[17] |= 0b11111 << G.me.x;
				explored[18] |= 0b1111 << G.me.x;
				explored[19] |= 0b111 << G.me.x;
				break;
			case 16:
				explored[16] |= 0b11111 << G.me.x;
				explored[17] |= 0b11111 << G.me.x;
				explored[18] |= 0b11111 << G.me.x;
				explored[19] |= 0b1111 << G.me.x;
				explored[20] |= 0b111 << G.me.x;
				break;
			case 17:
				explored[17] |= 0b11111 << G.me.x;
				explored[18] |= 0b11111 << G.me.x;
				explored[19] |= 0b11111 << G.me.x;
				explored[20] |= 0b1111 << G.me.x;
				explored[21] |= 0b111 << G.me.x;
				break;
			case 18:
				explored[18] |= 0b11111 << G.me.x;
				explored[19] |= 0b11111 << G.me.x;
				explored[20] |= 0b11111 << G.me.x;
				explored[21] |= 0b1111 << G.me.x;
				explored[22] |= 0b111 << G.me.x;
				break;
			case 19:
				explored[19] |= 0b11111 << G.me.x;
				explored[20] |= 0b11111 << G.me.x;
				explored[21] |= 0b11111 << G.me.x;
				explored[22] |= 0b1111 << G.me.x;
				explored[23] |= 0b111 << G.me.x;
				break;
			case 20:
				explored[20] |= 0b11111 << G.me.x;
				explored[21] |= 0b11111 << G.me.x;
				explored[22] |= 0b11111 << G.me.x;
				explored[23] |= 0b1111 << G.me.x;
				explored[24] |= 0b111 << G.me.x;
				break;
			case 21:
				explored[21] |= 0b11111 << G.me.x;
				explored[22] |= 0b11111 << G.me.x;
				explored[23] |= 0b11111 << G.me.x;
				explored[24] |= 0b1111 << G.me.x;
				explored[25] |= 0b111 << G.me.x;
				break;
			case 22:
				explored[22] |= 0b11111 << G.me.x;
				explored[23] |= 0b11111 << G.me.x;
				explored[24] |= 0b11111 << G.me.x;
				explored[25] |= 0b1111 << G.me.x;
				explored[26] |= 0b111 << G.me.x;
				break;
			case 23:
				explored[23] |= 0b11111 << G.me.x;
				explored[24] |= 0b11111 << G.me.x;
				explored[25] |= 0b11111 << G.me.x;
				explored[26] |= 0b1111 << G.me.x;
				explored[27] |= 0b111 << G.me.x;
				break;
			case 24:
				explored[24] |= 0b11111 << G.me.x;
				explored[25] |= 0b11111 << G.me.x;
				explored[26] |= 0b11111 << G.me.x;
				explored[27] |= 0b1111 << G.me.x;
				explored[28] |= 0b111 << G.me.x;
				break;
			case 25:
				explored[25] |= 0b11111 << G.me.x;
				explored[26] |= 0b11111 << G.me.x;
				explored[27] |= 0b11111 << G.me.x;
				explored[28] |= 0b1111 << G.me.x;
				explored[29] |= 0b111 << G.me.x;
				break;
			case 26:
				explored[26] |= 0b11111 << G.me.x;
				explored[27] |= 0b11111 << G.me.x;
				explored[28] |= 0b11111 << G.me.x;
				explored[29] |= 0b1111 << G.me.x;
				explored[30] |= 0b111 << G.me.x;
				break;
			case 27:
				explored[27] |= 0b11111 << G.me.x;
				explored[28] |= 0b11111 << G.me.x;
				explored[29] |= 0b11111 << G.me.x;
				explored[30] |= 0b1111 << G.me.x;
				explored[31] |= 0b111 << G.me.x;
				break;
			case 28:
				explored[28] |= 0b11111 << G.me.x;
				explored[29] |= 0b11111 << G.me.x;
				explored[30] |= 0b11111 << G.me.x;
				explored[31] |= 0b1111 << G.me.x;
				explored[32] |= 0b111 << G.me.x;
				break;
			case 29:
				explored[29] |= 0b11111 << G.me.x;
				explored[30] |= 0b11111 << G.me.x;
				explored[31] |= 0b11111 << G.me.x;
				explored[32] |= 0b1111 << G.me.x;
				explored[33] |= 0b111 << G.me.x;
				break;
			case 30:
				explored[30] |= 0b11111 << G.me.x;
				explored[31] |= 0b11111 << G.me.x;
				explored[32] |= 0b11111 << G.me.x;
				explored[33] |= 0b1111 << G.me.x;
				explored[34] |= 0b111 << G.me.x;
				break;
			case 31:
				explored[31] |= 0b11111 << G.me.x;
				explored[32] |= 0b11111 << G.me.x;
				explored[33] |= 0b11111 << G.me.x;
				explored[34] |= 0b1111 << G.me.x;
				explored[35] |= 0b111 << G.me.x;
				break;
			case 32:
				explored[32] |= 0b11111 << G.me.x;
				explored[33] |= 0b11111 << G.me.x;
				explored[34] |= 0b11111 << G.me.x;
				explored[35] |= 0b1111 << G.me.x;
				explored[36] |= 0b111 << G.me.x;
				break;
			case 33:
				explored[33] |= 0b11111 << G.me.x;
				explored[34] |= 0b11111 << G.me.x;
				explored[35] |= 0b11111 << G.me.x;
				explored[36] |= 0b1111 << G.me.x;
				explored[37] |= 0b111 << G.me.x;
				break;
			case 34:
				explored[34] |= 0b11111 << G.me.x;
				explored[35] |= 0b11111 << G.me.x;
				explored[36] |= 0b11111 << G.me.x;
				explored[37] |= 0b1111 << G.me.x;
				explored[38] |= 0b111 << G.me.x;
				break;
			case 35:
				explored[35] |= 0b11111 << G.me.x;
				explored[36] |= 0b11111 << G.me.x;
				explored[37] |= 0b11111 << G.me.x;
				explored[38] |= 0b1111 << G.me.x;
				explored[39] |= 0b111 << G.me.x;
				break;
			case 36:
				explored[36] |= 0b11111 << G.me.x;
				explored[37] |= 0b11111 << G.me.x;
				explored[38] |= 0b11111 << G.me.x;
				explored[39] |= 0b1111 << G.me.x;
				explored[40] |= 0b111 << G.me.x;
				break;
			case 37:
				explored[37] |= 0b11111 << G.me.x;
				explored[38] |= 0b11111 << G.me.x;
				explored[39] |= 0b11111 << G.me.x;
				explored[40] |= 0b1111 << G.me.x;
				explored[41] |= 0b111 << G.me.x;
				break;
			case 38:
				explored[38] |= 0b11111 << G.me.x;
				explored[39] |= 0b11111 << G.me.x;
				explored[40] |= 0b11111 << G.me.x;
				explored[41] |= 0b1111 << G.me.x;
				explored[42] |= 0b111 << G.me.x;
				break;
			case 39:
				explored[39] |= 0b11111 << G.me.x;
				explored[40] |= 0b11111 << G.me.x;
				explored[41] |= 0b11111 << G.me.x;
				explored[42] |= 0b1111 << G.me.x;
				explored[43] |= 0b111 << G.me.x;
				break;
			case 40:
				explored[40] |= 0b11111 << G.me.x;
				explored[41] |= 0b11111 << G.me.x;
				explored[42] |= 0b11111 << G.me.x;
				explored[43] |= 0b1111 << G.me.x;
				explored[44] |= 0b111 << G.me.x;
				break;
			case 41:
				explored[41] |= 0b11111 << G.me.x;
				explored[42] |= 0b11111 << G.me.x;
				explored[43] |= 0b11111 << G.me.x;
				explored[44] |= 0b1111 << G.me.x;
				explored[45] |= 0b111 << G.me.x;
				break;
			case 42:
				explored[42] |= 0b11111 << G.me.x;
				explored[43] |= 0b11111 << G.me.x;
				explored[44] |= 0b11111 << G.me.x;
				explored[45] |= 0b1111 << G.me.x;
				explored[46] |= 0b111 << G.me.x;
				break;
			case 43:
				explored[43] |= 0b11111 << G.me.x;
				explored[44] |= 0b11111 << G.me.x;
				explored[45] |= 0b11111 << G.me.x;
				explored[46] |= 0b1111 << G.me.x;
				explored[47] |= 0b111 << G.me.x;
				break;
			case 44:
				explored[44] |= 0b11111 << G.me.x;
				explored[45] |= 0b11111 << G.me.x;
				explored[46] |= 0b11111 << G.me.x;
				explored[47] |= 0b1111 << G.me.x;
				explored[48] |= 0b111 << G.me.x;
				break;
			case 45:
				explored[45] |= 0b11111 << G.me.x;
				explored[46] |= 0b11111 << G.me.x;
				explored[47] |= 0b11111 << G.me.x;
				explored[48] |= 0b1111 << G.me.x;
				explored[49] |= 0b111 << G.me.x;
				break;
			case 46:
				explored[46] |= 0b11111 << G.me.x;
				explored[47] |= 0b11111 << G.me.x;
				explored[48] |= 0b11111 << G.me.x;
				explored[49] |= 0b1111 << G.me.x;
				explored[50] |= 0b111 << G.me.x;
				break;
			case 47:
				explored[47] |= 0b11111 << G.me.x;
				explored[48] |= 0b11111 << G.me.x;
				explored[49] |= 0b11111 << G.me.x;
				explored[50] |= 0b1111 << G.me.x;
				explored[51] |= 0b111 << G.me.x;
				break;
			case 48:
				explored[48] |= 0b11111 << G.me.x;
				explored[49] |= 0b11111 << G.me.x;
				explored[50] |= 0b11111 << G.me.x;
				explored[51] |= 0b1111 << G.me.x;
				explored[52] |= 0b111 << G.me.x;
				break;
			case 49:
				explored[49] |= 0b11111 << G.me.x;
				explored[50] |= 0b11111 << G.me.x;
				explored[51] |= 0b11111 << G.me.x;
				explored[52] |= 0b1111 << G.me.x;
				explored[53] |= 0b111 << G.me.x;
				break;
			case 50:
				explored[50] |= 0b11111 << G.me.x;
				explored[51] |= 0b11111 << G.me.x;
				explored[52] |= 0b11111 << G.me.x;
				explored[53] |= 0b1111 << G.me.x;
				explored[54] |= 0b111 << G.me.x;
				break;
			case 51:
				explored[51] |= 0b11111 << G.me.x;
				explored[52] |= 0b11111 << G.me.x;
				explored[53] |= 0b11111 << G.me.x;
				explored[54] |= 0b1111 << G.me.x;
				explored[55] |= 0b111 << G.me.x;
				break;
			case 52:
				explored[52] |= 0b11111 << G.me.x;
				explored[53] |= 0b11111 << G.me.x;
				explored[54] |= 0b11111 << G.me.x;
				explored[55] |= 0b1111 << G.me.x;
				explored[56] |= 0b111 << G.me.x;
				break;
			case 53:
				explored[53] |= 0b11111 << G.me.x;
				explored[54] |= 0b11111 << G.me.x;
				explored[55] |= 0b11111 << G.me.x;
				explored[56] |= 0b1111 << G.me.x;
				explored[57] |= 0b111 << G.me.x;
				break;
			case 54:
				explored[54] |= 0b11111 << G.me.x;
				explored[55] |= 0b11111 << G.me.x;
				explored[56] |= 0b11111 << G.me.x;
				explored[57] |= 0b1111 << G.me.x;
				explored[58] |= 0b111 << G.me.x;
				break;
			case 55:
				explored[55] |= 0b11111 << G.me.x;
				explored[56] |= 0b11111 << G.me.x;
				explored[57] |= 0b11111 << G.me.x;
				explored[58] |= 0b1111 << G.me.x;
				explored[59] |= 0b111 << G.me.x;
				break;
			case 56:
				explored[56] |= 0b11111 << G.me.x;
				explored[57] |= 0b11111 << G.me.x;
				explored[58] |= 0b11111 << G.me.x;
				explored[59] |= 0b1111 << G.me.x;
				break;
			case 57:
				explored[57] |= 0b11111 << G.me.x;
				explored[58] |= 0b11111 << G.me.x;
				explored[59] |= 0b11111 << G.me.x;
				break;
			case 58:
				explored[58] |= 0b11111 << G.me.x;
				explored[59] |= 0b11111 << G.me.x;
				break;
			case 59:
				explored[59] |= 0b11111 << G.me.x;
				break;
		}
	}
	public static void updateExploredBabyRat5() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b1 << G.me.x;
				explored[1] |= 0b111 << G.me.x - 1;
				explored[2] |= 0b11111 << G.me.x - 2;
				explored[3] |= 0b1111111 << G.me.x - 3;
				explored[4] |= 0b11111 << G.me.x - 2;
				break;
			case 1:
				explored[1] |= 0b1 << G.me.x;
				explored[2] |= 0b111 << G.me.x - 1;
				explored[3] |= 0b11111 << G.me.x - 2;
				explored[4] |= 0b1111111 << G.me.x - 3;
				explored[5] |= 0b11111 << G.me.x - 2;
				break;
			case 2:
				explored[2] |= 0b1 << G.me.x;
				explored[3] |= 0b111 << G.me.x - 1;
				explored[4] |= 0b11111 << G.me.x - 2;
				explored[5] |= 0b1111111 << G.me.x - 3;
				explored[6] |= 0b11111 << G.me.x - 2;
				break;
			case 3:
				explored[3] |= 0b1 << G.me.x;
				explored[4] |= 0b111 << G.me.x - 1;
				explored[5] |= 0b11111 << G.me.x - 2;
				explored[6] |= 0b1111111 << G.me.x - 3;
				explored[7] |= 0b11111 << G.me.x - 2;
				break;
			case 4:
				explored[4] |= 0b1 << G.me.x;
				explored[5] |= 0b111 << G.me.x - 1;
				explored[6] |= 0b11111 << G.me.x - 2;
				explored[7] |= 0b1111111 << G.me.x - 3;
				explored[8] |= 0b11111 << G.me.x - 2;
				break;
			case 5:
				explored[5] |= 0b1 << G.me.x;
				explored[6] |= 0b111 << G.me.x - 1;
				explored[7] |= 0b11111 << G.me.x - 2;
				explored[8] |= 0b1111111 << G.me.x - 3;
				explored[9] |= 0b11111 << G.me.x - 2;
				break;
			case 6:
				explored[6] |= 0b1 << G.me.x;
				explored[7] |= 0b111 << G.me.x - 1;
				explored[8] |= 0b11111 << G.me.x - 2;
				explored[9] |= 0b1111111 << G.me.x - 3;
				explored[10] |= 0b11111 << G.me.x - 2;
				break;
			case 7:
				explored[7] |= 0b1 << G.me.x;
				explored[8] |= 0b111 << G.me.x - 1;
				explored[9] |= 0b11111 << G.me.x - 2;
				explored[10] |= 0b1111111 << G.me.x - 3;
				explored[11] |= 0b11111 << G.me.x - 2;
				break;
			case 8:
				explored[8] |= 0b1 << G.me.x;
				explored[9] |= 0b111 << G.me.x - 1;
				explored[10] |= 0b11111 << G.me.x - 2;
				explored[11] |= 0b1111111 << G.me.x - 3;
				explored[12] |= 0b11111 << G.me.x - 2;
				break;
			case 9:
				explored[9] |= 0b1 << G.me.x;
				explored[10] |= 0b111 << G.me.x - 1;
				explored[11] |= 0b11111 << G.me.x - 2;
				explored[12] |= 0b1111111 << G.me.x - 3;
				explored[13] |= 0b11111 << G.me.x - 2;
				break;
			case 10:
				explored[10] |= 0b1 << G.me.x;
				explored[11] |= 0b111 << G.me.x - 1;
				explored[12] |= 0b11111 << G.me.x - 2;
				explored[13] |= 0b1111111 << G.me.x - 3;
				explored[14] |= 0b11111 << G.me.x - 2;
				break;
			case 11:
				explored[11] |= 0b1 << G.me.x;
				explored[12] |= 0b111 << G.me.x - 1;
				explored[13] |= 0b11111 << G.me.x - 2;
				explored[14] |= 0b1111111 << G.me.x - 3;
				explored[15] |= 0b11111 << G.me.x - 2;
				break;
			case 12:
				explored[12] |= 0b1 << G.me.x;
				explored[13] |= 0b111 << G.me.x - 1;
				explored[14] |= 0b11111 << G.me.x - 2;
				explored[15] |= 0b1111111 << G.me.x - 3;
				explored[16] |= 0b11111 << G.me.x - 2;
				break;
			case 13:
				explored[13] |= 0b1 << G.me.x;
				explored[14] |= 0b111 << G.me.x - 1;
				explored[15] |= 0b11111 << G.me.x - 2;
				explored[16] |= 0b1111111 << G.me.x - 3;
				explored[17] |= 0b11111 << G.me.x - 2;
				break;
			case 14:
				explored[14] |= 0b1 << G.me.x;
				explored[15] |= 0b111 << G.me.x - 1;
				explored[16] |= 0b11111 << G.me.x - 2;
				explored[17] |= 0b1111111 << G.me.x - 3;
				explored[18] |= 0b11111 << G.me.x - 2;
				break;
			case 15:
				explored[15] |= 0b1 << G.me.x;
				explored[16] |= 0b111 << G.me.x - 1;
				explored[17] |= 0b11111 << G.me.x - 2;
				explored[18] |= 0b1111111 << G.me.x - 3;
				explored[19] |= 0b11111 << G.me.x - 2;
				break;
			case 16:
				explored[16] |= 0b1 << G.me.x;
				explored[17] |= 0b111 << G.me.x - 1;
				explored[18] |= 0b11111 << G.me.x - 2;
				explored[19] |= 0b1111111 << G.me.x - 3;
				explored[20] |= 0b11111 << G.me.x - 2;
				break;
			case 17:
				explored[17] |= 0b1 << G.me.x;
				explored[18] |= 0b111 << G.me.x - 1;
				explored[19] |= 0b11111 << G.me.x - 2;
				explored[20] |= 0b1111111 << G.me.x - 3;
				explored[21] |= 0b11111 << G.me.x - 2;
				break;
			case 18:
				explored[18] |= 0b1 << G.me.x;
				explored[19] |= 0b111 << G.me.x - 1;
				explored[20] |= 0b11111 << G.me.x - 2;
				explored[21] |= 0b1111111 << G.me.x - 3;
				explored[22] |= 0b11111 << G.me.x - 2;
				break;
			case 19:
				explored[19] |= 0b1 << G.me.x;
				explored[20] |= 0b111 << G.me.x - 1;
				explored[21] |= 0b11111 << G.me.x - 2;
				explored[22] |= 0b1111111 << G.me.x - 3;
				explored[23] |= 0b11111 << G.me.x - 2;
				break;
			case 20:
				explored[20] |= 0b1 << G.me.x;
				explored[21] |= 0b111 << G.me.x - 1;
				explored[22] |= 0b11111 << G.me.x - 2;
				explored[23] |= 0b1111111 << G.me.x - 3;
				explored[24] |= 0b11111 << G.me.x - 2;
				break;
			case 21:
				explored[21] |= 0b1 << G.me.x;
				explored[22] |= 0b111 << G.me.x - 1;
				explored[23] |= 0b11111 << G.me.x - 2;
				explored[24] |= 0b1111111 << G.me.x - 3;
				explored[25] |= 0b11111 << G.me.x - 2;
				break;
			case 22:
				explored[22] |= 0b1 << G.me.x;
				explored[23] |= 0b111 << G.me.x - 1;
				explored[24] |= 0b11111 << G.me.x - 2;
				explored[25] |= 0b1111111 << G.me.x - 3;
				explored[26] |= 0b11111 << G.me.x - 2;
				break;
			case 23:
				explored[23] |= 0b1 << G.me.x;
				explored[24] |= 0b111 << G.me.x - 1;
				explored[25] |= 0b11111 << G.me.x - 2;
				explored[26] |= 0b1111111 << G.me.x - 3;
				explored[27] |= 0b11111 << G.me.x - 2;
				break;
			case 24:
				explored[24] |= 0b1 << G.me.x;
				explored[25] |= 0b111 << G.me.x - 1;
				explored[26] |= 0b11111 << G.me.x - 2;
				explored[27] |= 0b1111111 << G.me.x - 3;
				explored[28] |= 0b11111 << G.me.x - 2;
				break;
			case 25:
				explored[25] |= 0b1 << G.me.x;
				explored[26] |= 0b111 << G.me.x - 1;
				explored[27] |= 0b11111 << G.me.x - 2;
				explored[28] |= 0b1111111 << G.me.x - 3;
				explored[29] |= 0b11111 << G.me.x - 2;
				break;
			case 26:
				explored[26] |= 0b1 << G.me.x;
				explored[27] |= 0b111 << G.me.x - 1;
				explored[28] |= 0b11111 << G.me.x - 2;
				explored[29] |= 0b1111111 << G.me.x - 3;
				explored[30] |= 0b11111 << G.me.x - 2;
				break;
			case 27:
				explored[27] |= 0b1 << G.me.x;
				explored[28] |= 0b111 << G.me.x - 1;
				explored[29] |= 0b11111 << G.me.x - 2;
				explored[30] |= 0b1111111 << G.me.x - 3;
				explored[31] |= 0b11111 << G.me.x - 2;
				break;
			case 28:
				explored[28] |= 0b1 << G.me.x;
				explored[29] |= 0b111 << G.me.x - 1;
				explored[30] |= 0b11111 << G.me.x - 2;
				explored[31] |= 0b1111111 << G.me.x - 3;
				explored[32] |= 0b11111 << G.me.x - 2;
				break;
			case 29:
				explored[29] |= 0b1 << G.me.x;
				explored[30] |= 0b111 << G.me.x - 1;
				explored[31] |= 0b11111 << G.me.x - 2;
				explored[32] |= 0b1111111 << G.me.x - 3;
				explored[33] |= 0b11111 << G.me.x - 2;
				break;
			case 30:
				explored[30] |= 0b1 << G.me.x;
				explored[31] |= 0b111 << G.me.x - 1;
				explored[32] |= 0b11111 << G.me.x - 2;
				explored[33] |= 0b1111111 << G.me.x - 3;
				explored[34] |= 0b11111 << G.me.x - 2;
				break;
			case 31:
				explored[31] |= 0b1 << G.me.x;
				explored[32] |= 0b111 << G.me.x - 1;
				explored[33] |= 0b11111 << G.me.x - 2;
				explored[34] |= 0b1111111 << G.me.x - 3;
				explored[35] |= 0b11111 << G.me.x - 2;
				break;
			case 32:
				explored[32] |= 0b1 << G.me.x;
				explored[33] |= 0b111 << G.me.x - 1;
				explored[34] |= 0b11111 << G.me.x - 2;
				explored[35] |= 0b1111111 << G.me.x - 3;
				explored[36] |= 0b11111 << G.me.x - 2;
				break;
			case 33:
				explored[33] |= 0b1 << G.me.x;
				explored[34] |= 0b111 << G.me.x - 1;
				explored[35] |= 0b11111 << G.me.x - 2;
				explored[36] |= 0b1111111 << G.me.x - 3;
				explored[37] |= 0b11111 << G.me.x - 2;
				break;
			case 34:
				explored[34] |= 0b1 << G.me.x;
				explored[35] |= 0b111 << G.me.x - 1;
				explored[36] |= 0b11111 << G.me.x - 2;
				explored[37] |= 0b1111111 << G.me.x - 3;
				explored[38] |= 0b11111 << G.me.x - 2;
				break;
			case 35:
				explored[35] |= 0b1 << G.me.x;
				explored[36] |= 0b111 << G.me.x - 1;
				explored[37] |= 0b11111 << G.me.x - 2;
				explored[38] |= 0b1111111 << G.me.x - 3;
				explored[39] |= 0b11111 << G.me.x - 2;
				break;
			case 36:
				explored[36] |= 0b1 << G.me.x;
				explored[37] |= 0b111 << G.me.x - 1;
				explored[38] |= 0b11111 << G.me.x - 2;
				explored[39] |= 0b1111111 << G.me.x - 3;
				explored[40] |= 0b11111 << G.me.x - 2;
				break;
			case 37:
				explored[37] |= 0b1 << G.me.x;
				explored[38] |= 0b111 << G.me.x - 1;
				explored[39] |= 0b11111 << G.me.x - 2;
				explored[40] |= 0b1111111 << G.me.x - 3;
				explored[41] |= 0b11111 << G.me.x - 2;
				break;
			case 38:
				explored[38] |= 0b1 << G.me.x;
				explored[39] |= 0b111 << G.me.x - 1;
				explored[40] |= 0b11111 << G.me.x - 2;
				explored[41] |= 0b1111111 << G.me.x - 3;
				explored[42] |= 0b11111 << G.me.x - 2;
				break;
			case 39:
				explored[39] |= 0b1 << G.me.x;
				explored[40] |= 0b111 << G.me.x - 1;
				explored[41] |= 0b11111 << G.me.x - 2;
				explored[42] |= 0b1111111 << G.me.x - 3;
				explored[43] |= 0b11111 << G.me.x - 2;
				break;
			case 40:
				explored[40] |= 0b1 << G.me.x;
				explored[41] |= 0b111 << G.me.x - 1;
				explored[42] |= 0b11111 << G.me.x - 2;
				explored[43] |= 0b1111111 << G.me.x - 3;
				explored[44] |= 0b11111 << G.me.x - 2;
				break;
			case 41:
				explored[41] |= 0b1 << G.me.x;
				explored[42] |= 0b111 << G.me.x - 1;
				explored[43] |= 0b11111 << G.me.x - 2;
				explored[44] |= 0b1111111 << G.me.x - 3;
				explored[45] |= 0b11111 << G.me.x - 2;
				break;
			case 42:
				explored[42] |= 0b1 << G.me.x;
				explored[43] |= 0b111 << G.me.x - 1;
				explored[44] |= 0b11111 << G.me.x - 2;
				explored[45] |= 0b1111111 << G.me.x - 3;
				explored[46] |= 0b11111 << G.me.x - 2;
				break;
			case 43:
				explored[43] |= 0b1 << G.me.x;
				explored[44] |= 0b111 << G.me.x - 1;
				explored[45] |= 0b11111 << G.me.x - 2;
				explored[46] |= 0b1111111 << G.me.x - 3;
				explored[47] |= 0b11111 << G.me.x - 2;
				break;
			case 44:
				explored[44] |= 0b1 << G.me.x;
				explored[45] |= 0b111 << G.me.x - 1;
				explored[46] |= 0b11111 << G.me.x - 2;
				explored[47] |= 0b1111111 << G.me.x - 3;
				explored[48] |= 0b11111 << G.me.x - 2;
				break;
			case 45:
				explored[45] |= 0b1 << G.me.x;
				explored[46] |= 0b111 << G.me.x - 1;
				explored[47] |= 0b11111 << G.me.x - 2;
				explored[48] |= 0b1111111 << G.me.x - 3;
				explored[49] |= 0b11111 << G.me.x - 2;
				break;
			case 46:
				explored[46] |= 0b1 << G.me.x;
				explored[47] |= 0b111 << G.me.x - 1;
				explored[48] |= 0b11111 << G.me.x - 2;
				explored[49] |= 0b1111111 << G.me.x - 3;
				explored[50] |= 0b11111 << G.me.x - 2;
				break;
			case 47:
				explored[47] |= 0b1 << G.me.x;
				explored[48] |= 0b111 << G.me.x - 1;
				explored[49] |= 0b11111 << G.me.x - 2;
				explored[50] |= 0b1111111 << G.me.x - 3;
				explored[51] |= 0b11111 << G.me.x - 2;
				break;
			case 48:
				explored[48] |= 0b1 << G.me.x;
				explored[49] |= 0b111 << G.me.x - 1;
				explored[50] |= 0b11111 << G.me.x - 2;
				explored[51] |= 0b1111111 << G.me.x - 3;
				explored[52] |= 0b11111 << G.me.x - 2;
				break;
			case 49:
				explored[49] |= 0b1 << G.me.x;
				explored[50] |= 0b111 << G.me.x - 1;
				explored[51] |= 0b11111 << G.me.x - 2;
				explored[52] |= 0b1111111 << G.me.x - 3;
				explored[53] |= 0b11111 << G.me.x - 2;
				break;
			case 50:
				explored[50] |= 0b1 << G.me.x;
				explored[51] |= 0b111 << G.me.x - 1;
				explored[52] |= 0b11111 << G.me.x - 2;
				explored[53] |= 0b1111111 << G.me.x - 3;
				explored[54] |= 0b11111 << G.me.x - 2;
				break;
			case 51:
				explored[51] |= 0b1 << G.me.x;
				explored[52] |= 0b111 << G.me.x - 1;
				explored[53] |= 0b11111 << G.me.x - 2;
				explored[54] |= 0b1111111 << G.me.x - 3;
				explored[55] |= 0b11111 << G.me.x - 2;
				break;
			case 52:
				explored[52] |= 0b1 << G.me.x;
				explored[53] |= 0b111 << G.me.x - 1;
				explored[54] |= 0b11111 << G.me.x - 2;
				explored[55] |= 0b1111111 << G.me.x - 3;
				explored[56] |= 0b11111 << G.me.x - 2;
				break;
			case 53:
				explored[53] |= 0b1 << G.me.x;
				explored[54] |= 0b111 << G.me.x - 1;
				explored[55] |= 0b11111 << G.me.x - 2;
				explored[56] |= 0b1111111 << G.me.x - 3;
				explored[57] |= 0b11111 << G.me.x - 2;
				break;
			case 54:
				explored[54] |= 0b1 << G.me.x;
				explored[55] |= 0b111 << G.me.x - 1;
				explored[56] |= 0b11111 << G.me.x - 2;
				explored[57] |= 0b1111111 << G.me.x - 3;
				explored[58] |= 0b11111 << G.me.x - 2;
				break;
			case 55:
				explored[55] |= 0b1 << G.me.x;
				explored[56] |= 0b111 << G.me.x - 1;
				explored[57] |= 0b11111 << G.me.x - 2;
				explored[58] |= 0b1111111 << G.me.x - 3;
				explored[59] |= 0b11111 << G.me.x - 2;
				break;
			case 56:
				explored[56] |= 0b1 << G.me.x;
				explored[57] |= 0b111 << G.me.x - 1;
				explored[58] |= 0b11111 << G.me.x - 2;
				explored[59] |= 0b1111111 << G.me.x - 3;
				break;
			case 57:
				explored[57] |= 0b1 << G.me.x;
				explored[58] |= 0b111 << G.me.x - 1;
				explored[59] |= 0b11111 << G.me.x - 2;
				break;
			case 58:
				explored[58] |= 0b1 << G.me.x;
				explored[59] |= 0b111 << G.me.x - 1;
				break;
			case 59:
				explored[59] |= 0b1 << G.me.x;
				break;
		}
	}
	public static void updateExploredBabyRat6() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111 << G.me.x - 4;
				explored[1] |= 0b11111 << G.me.x - 4;
				explored[2] |= 0b11111 << G.me.x - 4;
				explored[3] |= 0b1111 << G.me.x - 3;
				explored[4] |= 0b111 << G.me.x - 2;
				break;
			case 1:
				explored[1] |= 0b11111 << G.me.x - 4;
				explored[2] |= 0b11111 << G.me.x - 4;
				explored[3] |= 0b11111 << G.me.x - 4;
				explored[4] |= 0b1111 << G.me.x - 3;
				explored[5] |= 0b111 << G.me.x - 2;
				break;
			case 2:
				explored[2] |= 0b11111 << G.me.x - 4;
				explored[3] |= 0b11111 << G.me.x - 4;
				explored[4] |= 0b11111 << G.me.x - 4;
				explored[5] |= 0b1111 << G.me.x - 3;
				explored[6] |= 0b111 << G.me.x - 2;
				break;
			case 3:
				explored[3] |= 0b11111 << G.me.x - 4;
				explored[4] |= 0b11111 << G.me.x - 4;
				explored[5] |= 0b11111 << G.me.x - 4;
				explored[6] |= 0b1111 << G.me.x - 3;
				explored[7] |= 0b111 << G.me.x - 2;
				break;
			case 4:
				explored[4] |= 0b11111 << G.me.x - 4;
				explored[5] |= 0b11111 << G.me.x - 4;
				explored[6] |= 0b11111 << G.me.x - 4;
				explored[7] |= 0b1111 << G.me.x - 3;
				explored[8] |= 0b111 << G.me.x - 2;
				break;
			case 5:
				explored[5] |= 0b11111 << G.me.x - 4;
				explored[6] |= 0b11111 << G.me.x - 4;
				explored[7] |= 0b11111 << G.me.x - 4;
				explored[8] |= 0b1111 << G.me.x - 3;
				explored[9] |= 0b111 << G.me.x - 2;
				break;
			case 6:
				explored[6] |= 0b11111 << G.me.x - 4;
				explored[7] |= 0b11111 << G.me.x - 4;
				explored[8] |= 0b11111 << G.me.x - 4;
				explored[9] |= 0b1111 << G.me.x - 3;
				explored[10] |= 0b111 << G.me.x - 2;
				break;
			case 7:
				explored[7] |= 0b11111 << G.me.x - 4;
				explored[8] |= 0b11111 << G.me.x - 4;
				explored[9] |= 0b11111 << G.me.x - 4;
				explored[10] |= 0b1111 << G.me.x - 3;
				explored[11] |= 0b111 << G.me.x - 2;
				break;
			case 8:
				explored[8] |= 0b11111 << G.me.x - 4;
				explored[9] |= 0b11111 << G.me.x - 4;
				explored[10] |= 0b11111 << G.me.x - 4;
				explored[11] |= 0b1111 << G.me.x - 3;
				explored[12] |= 0b111 << G.me.x - 2;
				break;
			case 9:
				explored[9] |= 0b11111 << G.me.x - 4;
				explored[10] |= 0b11111 << G.me.x - 4;
				explored[11] |= 0b11111 << G.me.x - 4;
				explored[12] |= 0b1111 << G.me.x - 3;
				explored[13] |= 0b111 << G.me.x - 2;
				break;
			case 10:
				explored[10] |= 0b11111 << G.me.x - 4;
				explored[11] |= 0b11111 << G.me.x - 4;
				explored[12] |= 0b11111 << G.me.x - 4;
				explored[13] |= 0b1111 << G.me.x - 3;
				explored[14] |= 0b111 << G.me.x - 2;
				break;
			case 11:
				explored[11] |= 0b11111 << G.me.x - 4;
				explored[12] |= 0b11111 << G.me.x - 4;
				explored[13] |= 0b11111 << G.me.x - 4;
				explored[14] |= 0b1111 << G.me.x - 3;
				explored[15] |= 0b111 << G.me.x - 2;
				break;
			case 12:
				explored[12] |= 0b11111 << G.me.x - 4;
				explored[13] |= 0b11111 << G.me.x - 4;
				explored[14] |= 0b11111 << G.me.x - 4;
				explored[15] |= 0b1111 << G.me.x - 3;
				explored[16] |= 0b111 << G.me.x - 2;
				break;
			case 13:
				explored[13] |= 0b11111 << G.me.x - 4;
				explored[14] |= 0b11111 << G.me.x - 4;
				explored[15] |= 0b11111 << G.me.x - 4;
				explored[16] |= 0b1111 << G.me.x - 3;
				explored[17] |= 0b111 << G.me.x - 2;
				break;
			case 14:
				explored[14] |= 0b11111 << G.me.x - 4;
				explored[15] |= 0b11111 << G.me.x - 4;
				explored[16] |= 0b11111 << G.me.x - 4;
				explored[17] |= 0b1111 << G.me.x - 3;
				explored[18] |= 0b111 << G.me.x - 2;
				break;
			case 15:
				explored[15] |= 0b11111 << G.me.x - 4;
				explored[16] |= 0b11111 << G.me.x - 4;
				explored[17] |= 0b11111 << G.me.x - 4;
				explored[18] |= 0b1111 << G.me.x - 3;
				explored[19] |= 0b111 << G.me.x - 2;
				break;
			case 16:
				explored[16] |= 0b11111 << G.me.x - 4;
				explored[17] |= 0b11111 << G.me.x - 4;
				explored[18] |= 0b11111 << G.me.x - 4;
				explored[19] |= 0b1111 << G.me.x - 3;
				explored[20] |= 0b111 << G.me.x - 2;
				break;
			case 17:
				explored[17] |= 0b11111 << G.me.x - 4;
				explored[18] |= 0b11111 << G.me.x - 4;
				explored[19] |= 0b11111 << G.me.x - 4;
				explored[20] |= 0b1111 << G.me.x - 3;
				explored[21] |= 0b111 << G.me.x - 2;
				break;
			case 18:
				explored[18] |= 0b11111 << G.me.x - 4;
				explored[19] |= 0b11111 << G.me.x - 4;
				explored[20] |= 0b11111 << G.me.x - 4;
				explored[21] |= 0b1111 << G.me.x - 3;
				explored[22] |= 0b111 << G.me.x - 2;
				break;
			case 19:
				explored[19] |= 0b11111 << G.me.x - 4;
				explored[20] |= 0b11111 << G.me.x - 4;
				explored[21] |= 0b11111 << G.me.x - 4;
				explored[22] |= 0b1111 << G.me.x - 3;
				explored[23] |= 0b111 << G.me.x - 2;
				break;
			case 20:
				explored[20] |= 0b11111 << G.me.x - 4;
				explored[21] |= 0b11111 << G.me.x - 4;
				explored[22] |= 0b11111 << G.me.x - 4;
				explored[23] |= 0b1111 << G.me.x - 3;
				explored[24] |= 0b111 << G.me.x - 2;
				break;
			case 21:
				explored[21] |= 0b11111 << G.me.x - 4;
				explored[22] |= 0b11111 << G.me.x - 4;
				explored[23] |= 0b11111 << G.me.x - 4;
				explored[24] |= 0b1111 << G.me.x - 3;
				explored[25] |= 0b111 << G.me.x - 2;
				break;
			case 22:
				explored[22] |= 0b11111 << G.me.x - 4;
				explored[23] |= 0b11111 << G.me.x - 4;
				explored[24] |= 0b11111 << G.me.x - 4;
				explored[25] |= 0b1111 << G.me.x - 3;
				explored[26] |= 0b111 << G.me.x - 2;
				break;
			case 23:
				explored[23] |= 0b11111 << G.me.x - 4;
				explored[24] |= 0b11111 << G.me.x - 4;
				explored[25] |= 0b11111 << G.me.x - 4;
				explored[26] |= 0b1111 << G.me.x - 3;
				explored[27] |= 0b111 << G.me.x - 2;
				break;
			case 24:
				explored[24] |= 0b11111 << G.me.x - 4;
				explored[25] |= 0b11111 << G.me.x - 4;
				explored[26] |= 0b11111 << G.me.x - 4;
				explored[27] |= 0b1111 << G.me.x - 3;
				explored[28] |= 0b111 << G.me.x - 2;
				break;
			case 25:
				explored[25] |= 0b11111 << G.me.x - 4;
				explored[26] |= 0b11111 << G.me.x - 4;
				explored[27] |= 0b11111 << G.me.x - 4;
				explored[28] |= 0b1111 << G.me.x - 3;
				explored[29] |= 0b111 << G.me.x - 2;
				break;
			case 26:
				explored[26] |= 0b11111 << G.me.x - 4;
				explored[27] |= 0b11111 << G.me.x - 4;
				explored[28] |= 0b11111 << G.me.x - 4;
				explored[29] |= 0b1111 << G.me.x - 3;
				explored[30] |= 0b111 << G.me.x - 2;
				break;
			case 27:
				explored[27] |= 0b11111 << G.me.x - 4;
				explored[28] |= 0b11111 << G.me.x - 4;
				explored[29] |= 0b11111 << G.me.x - 4;
				explored[30] |= 0b1111 << G.me.x - 3;
				explored[31] |= 0b111 << G.me.x - 2;
				break;
			case 28:
				explored[28] |= 0b11111 << G.me.x - 4;
				explored[29] |= 0b11111 << G.me.x - 4;
				explored[30] |= 0b11111 << G.me.x - 4;
				explored[31] |= 0b1111 << G.me.x - 3;
				explored[32] |= 0b111 << G.me.x - 2;
				break;
			case 29:
				explored[29] |= 0b11111 << G.me.x - 4;
				explored[30] |= 0b11111 << G.me.x - 4;
				explored[31] |= 0b11111 << G.me.x - 4;
				explored[32] |= 0b1111 << G.me.x - 3;
				explored[33] |= 0b111 << G.me.x - 2;
				break;
			case 30:
				explored[30] |= 0b11111 << G.me.x - 4;
				explored[31] |= 0b11111 << G.me.x - 4;
				explored[32] |= 0b11111 << G.me.x - 4;
				explored[33] |= 0b1111 << G.me.x - 3;
				explored[34] |= 0b111 << G.me.x - 2;
				break;
			case 31:
				explored[31] |= 0b11111 << G.me.x - 4;
				explored[32] |= 0b11111 << G.me.x - 4;
				explored[33] |= 0b11111 << G.me.x - 4;
				explored[34] |= 0b1111 << G.me.x - 3;
				explored[35] |= 0b111 << G.me.x - 2;
				break;
			case 32:
				explored[32] |= 0b11111 << G.me.x - 4;
				explored[33] |= 0b11111 << G.me.x - 4;
				explored[34] |= 0b11111 << G.me.x - 4;
				explored[35] |= 0b1111 << G.me.x - 3;
				explored[36] |= 0b111 << G.me.x - 2;
				break;
			case 33:
				explored[33] |= 0b11111 << G.me.x - 4;
				explored[34] |= 0b11111 << G.me.x - 4;
				explored[35] |= 0b11111 << G.me.x - 4;
				explored[36] |= 0b1111 << G.me.x - 3;
				explored[37] |= 0b111 << G.me.x - 2;
				break;
			case 34:
				explored[34] |= 0b11111 << G.me.x - 4;
				explored[35] |= 0b11111 << G.me.x - 4;
				explored[36] |= 0b11111 << G.me.x - 4;
				explored[37] |= 0b1111 << G.me.x - 3;
				explored[38] |= 0b111 << G.me.x - 2;
				break;
			case 35:
				explored[35] |= 0b11111 << G.me.x - 4;
				explored[36] |= 0b11111 << G.me.x - 4;
				explored[37] |= 0b11111 << G.me.x - 4;
				explored[38] |= 0b1111 << G.me.x - 3;
				explored[39] |= 0b111 << G.me.x - 2;
				break;
			case 36:
				explored[36] |= 0b11111 << G.me.x - 4;
				explored[37] |= 0b11111 << G.me.x - 4;
				explored[38] |= 0b11111 << G.me.x - 4;
				explored[39] |= 0b1111 << G.me.x - 3;
				explored[40] |= 0b111 << G.me.x - 2;
				break;
			case 37:
				explored[37] |= 0b11111 << G.me.x - 4;
				explored[38] |= 0b11111 << G.me.x - 4;
				explored[39] |= 0b11111 << G.me.x - 4;
				explored[40] |= 0b1111 << G.me.x - 3;
				explored[41] |= 0b111 << G.me.x - 2;
				break;
			case 38:
				explored[38] |= 0b11111 << G.me.x - 4;
				explored[39] |= 0b11111 << G.me.x - 4;
				explored[40] |= 0b11111 << G.me.x - 4;
				explored[41] |= 0b1111 << G.me.x - 3;
				explored[42] |= 0b111 << G.me.x - 2;
				break;
			case 39:
				explored[39] |= 0b11111 << G.me.x - 4;
				explored[40] |= 0b11111 << G.me.x - 4;
				explored[41] |= 0b11111 << G.me.x - 4;
				explored[42] |= 0b1111 << G.me.x - 3;
				explored[43] |= 0b111 << G.me.x - 2;
				break;
			case 40:
				explored[40] |= 0b11111 << G.me.x - 4;
				explored[41] |= 0b11111 << G.me.x - 4;
				explored[42] |= 0b11111 << G.me.x - 4;
				explored[43] |= 0b1111 << G.me.x - 3;
				explored[44] |= 0b111 << G.me.x - 2;
				break;
			case 41:
				explored[41] |= 0b11111 << G.me.x - 4;
				explored[42] |= 0b11111 << G.me.x - 4;
				explored[43] |= 0b11111 << G.me.x - 4;
				explored[44] |= 0b1111 << G.me.x - 3;
				explored[45] |= 0b111 << G.me.x - 2;
				break;
			case 42:
				explored[42] |= 0b11111 << G.me.x - 4;
				explored[43] |= 0b11111 << G.me.x - 4;
				explored[44] |= 0b11111 << G.me.x - 4;
				explored[45] |= 0b1111 << G.me.x - 3;
				explored[46] |= 0b111 << G.me.x - 2;
				break;
			case 43:
				explored[43] |= 0b11111 << G.me.x - 4;
				explored[44] |= 0b11111 << G.me.x - 4;
				explored[45] |= 0b11111 << G.me.x - 4;
				explored[46] |= 0b1111 << G.me.x - 3;
				explored[47] |= 0b111 << G.me.x - 2;
				break;
			case 44:
				explored[44] |= 0b11111 << G.me.x - 4;
				explored[45] |= 0b11111 << G.me.x - 4;
				explored[46] |= 0b11111 << G.me.x - 4;
				explored[47] |= 0b1111 << G.me.x - 3;
				explored[48] |= 0b111 << G.me.x - 2;
				break;
			case 45:
				explored[45] |= 0b11111 << G.me.x - 4;
				explored[46] |= 0b11111 << G.me.x - 4;
				explored[47] |= 0b11111 << G.me.x - 4;
				explored[48] |= 0b1111 << G.me.x - 3;
				explored[49] |= 0b111 << G.me.x - 2;
				break;
			case 46:
				explored[46] |= 0b11111 << G.me.x - 4;
				explored[47] |= 0b11111 << G.me.x - 4;
				explored[48] |= 0b11111 << G.me.x - 4;
				explored[49] |= 0b1111 << G.me.x - 3;
				explored[50] |= 0b111 << G.me.x - 2;
				break;
			case 47:
				explored[47] |= 0b11111 << G.me.x - 4;
				explored[48] |= 0b11111 << G.me.x - 4;
				explored[49] |= 0b11111 << G.me.x - 4;
				explored[50] |= 0b1111 << G.me.x - 3;
				explored[51] |= 0b111 << G.me.x - 2;
				break;
			case 48:
				explored[48] |= 0b11111 << G.me.x - 4;
				explored[49] |= 0b11111 << G.me.x - 4;
				explored[50] |= 0b11111 << G.me.x - 4;
				explored[51] |= 0b1111 << G.me.x - 3;
				explored[52] |= 0b111 << G.me.x - 2;
				break;
			case 49:
				explored[49] |= 0b11111 << G.me.x - 4;
				explored[50] |= 0b11111 << G.me.x - 4;
				explored[51] |= 0b11111 << G.me.x - 4;
				explored[52] |= 0b1111 << G.me.x - 3;
				explored[53] |= 0b111 << G.me.x - 2;
				break;
			case 50:
				explored[50] |= 0b11111 << G.me.x - 4;
				explored[51] |= 0b11111 << G.me.x - 4;
				explored[52] |= 0b11111 << G.me.x - 4;
				explored[53] |= 0b1111 << G.me.x - 3;
				explored[54] |= 0b111 << G.me.x - 2;
				break;
			case 51:
				explored[51] |= 0b11111 << G.me.x - 4;
				explored[52] |= 0b11111 << G.me.x - 4;
				explored[53] |= 0b11111 << G.me.x - 4;
				explored[54] |= 0b1111 << G.me.x - 3;
				explored[55] |= 0b111 << G.me.x - 2;
				break;
			case 52:
				explored[52] |= 0b11111 << G.me.x - 4;
				explored[53] |= 0b11111 << G.me.x - 4;
				explored[54] |= 0b11111 << G.me.x - 4;
				explored[55] |= 0b1111 << G.me.x - 3;
				explored[56] |= 0b111 << G.me.x - 2;
				break;
			case 53:
				explored[53] |= 0b11111 << G.me.x - 4;
				explored[54] |= 0b11111 << G.me.x - 4;
				explored[55] |= 0b11111 << G.me.x - 4;
				explored[56] |= 0b1111 << G.me.x - 3;
				explored[57] |= 0b111 << G.me.x - 2;
				break;
			case 54:
				explored[54] |= 0b11111 << G.me.x - 4;
				explored[55] |= 0b11111 << G.me.x - 4;
				explored[56] |= 0b11111 << G.me.x - 4;
				explored[57] |= 0b1111 << G.me.x - 3;
				explored[58] |= 0b111 << G.me.x - 2;
				break;
			case 55:
				explored[55] |= 0b11111 << G.me.x - 4;
				explored[56] |= 0b11111 << G.me.x - 4;
				explored[57] |= 0b11111 << G.me.x - 4;
				explored[58] |= 0b1111 << G.me.x - 3;
				explored[59] |= 0b111 << G.me.x - 2;
				break;
			case 56:
				explored[56] |= 0b11111 << G.me.x - 4;
				explored[57] |= 0b11111 << G.me.x - 4;
				explored[58] |= 0b11111 << G.me.x - 4;
				explored[59] |= 0b1111 << G.me.x - 3;
				break;
			case 57:
				explored[57] |= 0b11111 << G.me.x - 4;
				explored[58] |= 0b11111 << G.me.x - 4;
				explored[59] |= 0b11111 << G.me.x - 4;
				break;
			case 58:
				explored[58] |= 0b11111 << G.me.x - 4;
				explored[59] |= 0b11111 << G.me.x - 4;
				break;
			case 59:
				explored[59] |= 0b11111 << G.me.x - 4;
				break;
		}
	}
	public static void updateExploredBabyRat7() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111 << G.me.x - 4;
				explored[1] |= 0b1111 << G.me.x - 4;
				explored[2] |= 0b111 << G.me.x - 4;
				explored[3] |= 0b1 << G.me.x - 3;
				break;
			case 1:
				explored[0] |= 0b1111 << G.me.x - 4;
				explored[1] |= 0b11111 << G.me.x - 4;
				explored[2] |= 0b1111 << G.me.x - 4;
				explored[3] |= 0b111 << G.me.x - 4;
				explored[4] |= 0b1 << G.me.x - 3;
				break;
			case 2:
				explored[0] |= 0b111 << G.me.x - 4;
				explored[1] |= 0b1111 << G.me.x - 4;
				explored[2] |= 0b11111 << G.me.x - 4;
				explored[3] |= 0b1111 << G.me.x - 4;
				explored[4] |= 0b111 << G.me.x - 4;
				explored[5] |= 0b1 << G.me.x - 3;
				break;
			case 3:
				explored[0] |= 0b1 << G.me.x - 3;
				explored[1] |= 0b111 << G.me.x - 4;
				explored[2] |= 0b1111 << G.me.x - 4;
				explored[3] |= 0b11111 << G.me.x - 4;
				explored[4] |= 0b1111 << G.me.x - 4;
				explored[5] |= 0b111 << G.me.x - 4;
				explored[6] |= 0b1 << G.me.x - 3;
				break;
			case 4:
				explored[1] |= 0b1 << G.me.x - 3;
				explored[2] |= 0b111 << G.me.x - 4;
				explored[3] |= 0b1111 << G.me.x - 4;
				explored[4] |= 0b11111 << G.me.x - 4;
				explored[5] |= 0b1111 << G.me.x - 4;
				explored[6] |= 0b111 << G.me.x - 4;
				explored[7] |= 0b1 << G.me.x - 3;
				break;
			case 5:
				explored[2] |= 0b1 << G.me.x - 3;
				explored[3] |= 0b111 << G.me.x - 4;
				explored[4] |= 0b1111 << G.me.x - 4;
				explored[5] |= 0b11111 << G.me.x - 4;
				explored[6] |= 0b1111 << G.me.x - 4;
				explored[7] |= 0b111 << G.me.x - 4;
				explored[8] |= 0b1 << G.me.x - 3;
				break;
			case 6:
				explored[3] |= 0b1 << G.me.x - 3;
				explored[4] |= 0b111 << G.me.x - 4;
				explored[5] |= 0b1111 << G.me.x - 4;
				explored[6] |= 0b11111 << G.me.x - 4;
				explored[7] |= 0b1111 << G.me.x - 4;
				explored[8] |= 0b111 << G.me.x - 4;
				explored[9] |= 0b1 << G.me.x - 3;
				break;
			case 7:
				explored[4] |= 0b1 << G.me.x - 3;
				explored[5] |= 0b111 << G.me.x - 4;
				explored[6] |= 0b1111 << G.me.x - 4;
				explored[7] |= 0b11111 << G.me.x - 4;
				explored[8] |= 0b1111 << G.me.x - 4;
				explored[9] |= 0b111 << G.me.x - 4;
				explored[10] |= 0b1 << G.me.x - 3;
				break;
			case 8:
				explored[5] |= 0b1 << G.me.x - 3;
				explored[6] |= 0b111 << G.me.x - 4;
				explored[7] |= 0b1111 << G.me.x - 4;
				explored[8] |= 0b11111 << G.me.x - 4;
				explored[9] |= 0b1111 << G.me.x - 4;
				explored[10] |= 0b111 << G.me.x - 4;
				explored[11] |= 0b1 << G.me.x - 3;
				break;
			case 9:
				explored[6] |= 0b1 << G.me.x - 3;
				explored[7] |= 0b111 << G.me.x - 4;
				explored[8] |= 0b1111 << G.me.x - 4;
				explored[9] |= 0b11111 << G.me.x - 4;
				explored[10] |= 0b1111 << G.me.x - 4;
				explored[11] |= 0b111 << G.me.x - 4;
				explored[12] |= 0b1 << G.me.x - 3;
				break;
			case 10:
				explored[7] |= 0b1 << G.me.x - 3;
				explored[8] |= 0b111 << G.me.x - 4;
				explored[9] |= 0b1111 << G.me.x - 4;
				explored[10] |= 0b11111 << G.me.x - 4;
				explored[11] |= 0b1111 << G.me.x - 4;
				explored[12] |= 0b111 << G.me.x - 4;
				explored[13] |= 0b1 << G.me.x - 3;
				break;
			case 11:
				explored[8] |= 0b1 << G.me.x - 3;
				explored[9] |= 0b111 << G.me.x - 4;
				explored[10] |= 0b1111 << G.me.x - 4;
				explored[11] |= 0b11111 << G.me.x - 4;
				explored[12] |= 0b1111 << G.me.x - 4;
				explored[13] |= 0b111 << G.me.x - 4;
				explored[14] |= 0b1 << G.me.x - 3;
				break;
			case 12:
				explored[9] |= 0b1 << G.me.x - 3;
				explored[10] |= 0b111 << G.me.x - 4;
				explored[11] |= 0b1111 << G.me.x - 4;
				explored[12] |= 0b11111 << G.me.x - 4;
				explored[13] |= 0b1111 << G.me.x - 4;
				explored[14] |= 0b111 << G.me.x - 4;
				explored[15] |= 0b1 << G.me.x - 3;
				break;
			case 13:
				explored[10] |= 0b1 << G.me.x - 3;
				explored[11] |= 0b111 << G.me.x - 4;
				explored[12] |= 0b1111 << G.me.x - 4;
				explored[13] |= 0b11111 << G.me.x - 4;
				explored[14] |= 0b1111 << G.me.x - 4;
				explored[15] |= 0b111 << G.me.x - 4;
				explored[16] |= 0b1 << G.me.x - 3;
				break;
			case 14:
				explored[11] |= 0b1 << G.me.x - 3;
				explored[12] |= 0b111 << G.me.x - 4;
				explored[13] |= 0b1111 << G.me.x - 4;
				explored[14] |= 0b11111 << G.me.x - 4;
				explored[15] |= 0b1111 << G.me.x - 4;
				explored[16] |= 0b111 << G.me.x - 4;
				explored[17] |= 0b1 << G.me.x - 3;
				break;
			case 15:
				explored[12] |= 0b1 << G.me.x - 3;
				explored[13] |= 0b111 << G.me.x - 4;
				explored[14] |= 0b1111 << G.me.x - 4;
				explored[15] |= 0b11111 << G.me.x - 4;
				explored[16] |= 0b1111 << G.me.x - 4;
				explored[17] |= 0b111 << G.me.x - 4;
				explored[18] |= 0b1 << G.me.x - 3;
				break;
			case 16:
				explored[13] |= 0b1 << G.me.x - 3;
				explored[14] |= 0b111 << G.me.x - 4;
				explored[15] |= 0b1111 << G.me.x - 4;
				explored[16] |= 0b11111 << G.me.x - 4;
				explored[17] |= 0b1111 << G.me.x - 4;
				explored[18] |= 0b111 << G.me.x - 4;
				explored[19] |= 0b1 << G.me.x - 3;
				break;
			case 17:
				explored[14] |= 0b1 << G.me.x - 3;
				explored[15] |= 0b111 << G.me.x - 4;
				explored[16] |= 0b1111 << G.me.x - 4;
				explored[17] |= 0b11111 << G.me.x - 4;
				explored[18] |= 0b1111 << G.me.x - 4;
				explored[19] |= 0b111 << G.me.x - 4;
				explored[20] |= 0b1 << G.me.x - 3;
				break;
			case 18:
				explored[15] |= 0b1 << G.me.x - 3;
				explored[16] |= 0b111 << G.me.x - 4;
				explored[17] |= 0b1111 << G.me.x - 4;
				explored[18] |= 0b11111 << G.me.x - 4;
				explored[19] |= 0b1111 << G.me.x - 4;
				explored[20] |= 0b111 << G.me.x - 4;
				explored[21] |= 0b1 << G.me.x - 3;
				break;
			case 19:
				explored[16] |= 0b1 << G.me.x - 3;
				explored[17] |= 0b111 << G.me.x - 4;
				explored[18] |= 0b1111 << G.me.x - 4;
				explored[19] |= 0b11111 << G.me.x - 4;
				explored[20] |= 0b1111 << G.me.x - 4;
				explored[21] |= 0b111 << G.me.x - 4;
				explored[22] |= 0b1 << G.me.x - 3;
				break;
			case 20:
				explored[17] |= 0b1 << G.me.x - 3;
				explored[18] |= 0b111 << G.me.x - 4;
				explored[19] |= 0b1111 << G.me.x - 4;
				explored[20] |= 0b11111 << G.me.x - 4;
				explored[21] |= 0b1111 << G.me.x - 4;
				explored[22] |= 0b111 << G.me.x - 4;
				explored[23] |= 0b1 << G.me.x - 3;
				break;
			case 21:
				explored[18] |= 0b1 << G.me.x - 3;
				explored[19] |= 0b111 << G.me.x - 4;
				explored[20] |= 0b1111 << G.me.x - 4;
				explored[21] |= 0b11111 << G.me.x - 4;
				explored[22] |= 0b1111 << G.me.x - 4;
				explored[23] |= 0b111 << G.me.x - 4;
				explored[24] |= 0b1 << G.me.x - 3;
				break;
			case 22:
				explored[19] |= 0b1 << G.me.x - 3;
				explored[20] |= 0b111 << G.me.x - 4;
				explored[21] |= 0b1111 << G.me.x - 4;
				explored[22] |= 0b11111 << G.me.x - 4;
				explored[23] |= 0b1111 << G.me.x - 4;
				explored[24] |= 0b111 << G.me.x - 4;
				explored[25] |= 0b1 << G.me.x - 3;
				break;
			case 23:
				explored[20] |= 0b1 << G.me.x - 3;
				explored[21] |= 0b111 << G.me.x - 4;
				explored[22] |= 0b1111 << G.me.x - 4;
				explored[23] |= 0b11111 << G.me.x - 4;
				explored[24] |= 0b1111 << G.me.x - 4;
				explored[25] |= 0b111 << G.me.x - 4;
				explored[26] |= 0b1 << G.me.x - 3;
				break;
			case 24:
				explored[21] |= 0b1 << G.me.x - 3;
				explored[22] |= 0b111 << G.me.x - 4;
				explored[23] |= 0b1111 << G.me.x - 4;
				explored[24] |= 0b11111 << G.me.x - 4;
				explored[25] |= 0b1111 << G.me.x - 4;
				explored[26] |= 0b111 << G.me.x - 4;
				explored[27] |= 0b1 << G.me.x - 3;
				break;
			case 25:
				explored[22] |= 0b1 << G.me.x - 3;
				explored[23] |= 0b111 << G.me.x - 4;
				explored[24] |= 0b1111 << G.me.x - 4;
				explored[25] |= 0b11111 << G.me.x - 4;
				explored[26] |= 0b1111 << G.me.x - 4;
				explored[27] |= 0b111 << G.me.x - 4;
				explored[28] |= 0b1 << G.me.x - 3;
				break;
			case 26:
				explored[23] |= 0b1 << G.me.x - 3;
				explored[24] |= 0b111 << G.me.x - 4;
				explored[25] |= 0b1111 << G.me.x - 4;
				explored[26] |= 0b11111 << G.me.x - 4;
				explored[27] |= 0b1111 << G.me.x - 4;
				explored[28] |= 0b111 << G.me.x - 4;
				explored[29] |= 0b1 << G.me.x - 3;
				break;
			case 27:
				explored[24] |= 0b1 << G.me.x - 3;
				explored[25] |= 0b111 << G.me.x - 4;
				explored[26] |= 0b1111 << G.me.x - 4;
				explored[27] |= 0b11111 << G.me.x - 4;
				explored[28] |= 0b1111 << G.me.x - 4;
				explored[29] |= 0b111 << G.me.x - 4;
				explored[30] |= 0b1 << G.me.x - 3;
				break;
			case 28:
				explored[25] |= 0b1 << G.me.x - 3;
				explored[26] |= 0b111 << G.me.x - 4;
				explored[27] |= 0b1111 << G.me.x - 4;
				explored[28] |= 0b11111 << G.me.x - 4;
				explored[29] |= 0b1111 << G.me.x - 4;
				explored[30] |= 0b111 << G.me.x - 4;
				explored[31] |= 0b1 << G.me.x - 3;
				break;
			case 29:
				explored[26] |= 0b1 << G.me.x - 3;
				explored[27] |= 0b111 << G.me.x - 4;
				explored[28] |= 0b1111 << G.me.x - 4;
				explored[29] |= 0b11111 << G.me.x - 4;
				explored[30] |= 0b1111 << G.me.x - 4;
				explored[31] |= 0b111 << G.me.x - 4;
				explored[32] |= 0b1 << G.me.x - 3;
				break;
			case 30:
				explored[27] |= 0b1 << G.me.x - 3;
				explored[28] |= 0b111 << G.me.x - 4;
				explored[29] |= 0b1111 << G.me.x - 4;
				explored[30] |= 0b11111 << G.me.x - 4;
				explored[31] |= 0b1111 << G.me.x - 4;
				explored[32] |= 0b111 << G.me.x - 4;
				explored[33] |= 0b1 << G.me.x - 3;
				break;
			case 31:
				explored[28] |= 0b1 << G.me.x - 3;
				explored[29] |= 0b111 << G.me.x - 4;
				explored[30] |= 0b1111 << G.me.x - 4;
				explored[31] |= 0b11111 << G.me.x - 4;
				explored[32] |= 0b1111 << G.me.x - 4;
				explored[33] |= 0b111 << G.me.x - 4;
				explored[34] |= 0b1 << G.me.x - 3;
				break;
			case 32:
				explored[29] |= 0b1 << G.me.x - 3;
				explored[30] |= 0b111 << G.me.x - 4;
				explored[31] |= 0b1111 << G.me.x - 4;
				explored[32] |= 0b11111 << G.me.x - 4;
				explored[33] |= 0b1111 << G.me.x - 4;
				explored[34] |= 0b111 << G.me.x - 4;
				explored[35] |= 0b1 << G.me.x - 3;
				break;
			case 33:
				explored[30] |= 0b1 << G.me.x - 3;
				explored[31] |= 0b111 << G.me.x - 4;
				explored[32] |= 0b1111 << G.me.x - 4;
				explored[33] |= 0b11111 << G.me.x - 4;
				explored[34] |= 0b1111 << G.me.x - 4;
				explored[35] |= 0b111 << G.me.x - 4;
				explored[36] |= 0b1 << G.me.x - 3;
				break;
			case 34:
				explored[31] |= 0b1 << G.me.x - 3;
				explored[32] |= 0b111 << G.me.x - 4;
				explored[33] |= 0b1111 << G.me.x - 4;
				explored[34] |= 0b11111 << G.me.x - 4;
				explored[35] |= 0b1111 << G.me.x - 4;
				explored[36] |= 0b111 << G.me.x - 4;
				explored[37] |= 0b1 << G.me.x - 3;
				break;
			case 35:
				explored[32] |= 0b1 << G.me.x - 3;
				explored[33] |= 0b111 << G.me.x - 4;
				explored[34] |= 0b1111 << G.me.x - 4;
				explored[35] |= 0b11111 << G.me.x - 4;
				explored[36] |= 0b1111 << G.me.x - 4;
				explored[37] |= 0b111 << G.me.x - 4;
				explored[38] |= 0b1 << G.me.x - 3;
				break;
			case 36:
				explored[33] |= 0b1 << G.me.x - 3;
				explored[34] |= 0b111 << G.me.x - 4;
				explored[35] |= 0b1111 << G.me.x - 4;
				explored[36] |= 0b11111 << G.me.x - 4;
				explored[37] |= 0b1111 << G.me.x - 4;
				explored[38] |= 0b111 << G.me.x - 4;
				explored[39] |= 0b1 << G.me.x - 3;
				break;
			case 37:
				explored[34] |= 0b1 << G.me.x - 3;
				explored[35] |= 0b111 << G.me.x - 4;
				explored[36] |= 0b1111 << G.me.x - 4;
				explored[37] |= 0b11111 << G.me.x - 4;
				explored[38] |= 0b1111 << G.me.x - 4;
				explored[39] |= 0b111 << G.me.x - 4;
				explored[40] |= 0b1 << G.me.x - 3;
				break;
			case 38:
				explored[35] |= 0b1 << G.me.x - 3;
				explored[36] |= 0b111 << G.me.x - 4;
				explored[37] |= 0b1111 << G.me.x - 4;
				explored[38] |= 0b11111 << G.me.x - 4;
				explored[39] |= 0b1111 << G.me.x - 4;
				explored[40] |= 0b111 << G.me.x - 4;
				explored[41] |= 0b1 << G.me.x - 3;
				break;
			case 39:
				explored[36] |= 0b1 << G.me.x - 3;
				explored[37] |= 0b111 << G.me.x - 4;
				explored[38] |= 0b1111 << G.me.x - 4;
				explored[39] |= 0b11111 << G.me.x - 4;
				explored[40] |= 0b1111 << G.me.x - 4;
				explored[41] |= 0b111 << G.me.x - 4;
				explored[42] |= 0b1 << G.me.x - 3;
				break;
			case 40:
				explored[37] |= 0b1 << G.me.x - 3;
				explored[38] |= 0b111 << G.me.x - 4;
				explored[39] |= 0b1111 << G.me.x - 4;
				explored[40] |= 0b11111 << G.me.x - 4;
				explored[41] |= 0b1111 << G.me.x - 4;
				explored[42] |= 0b111 << G.me.x - 4;
				explored[43] |= 0b1 << G.me.x - 3;
				break;
			case 41:
				explored[38] |= 0b1 << G.me.x - 3;
				explored[39] |= 0b111 << G.me.x - 4;
				explored[40] |= 0b1111 << G.me.x - 4;
				explored[41] |= 0b11111 << G.me.x - 4;
				explored[42] |= 0b1111 << G.me.x - 4;
				explored[43] |= 0b111 << G.me.x - 4;
				explored[44] |= 0b1 << G.me.x - 3;
				break;
			case 42:
				explored[39] |= 0b1 << G.me.x - 3;
				explored[40] |= 0b111 << G.me.x - 4;
				explored[41] |= 0b1111 << G.me.x - 4;
				explored[42] |= 0b11111 << G.me.x - 4;
				explored[43] |= 0b1111 << G.me.x - 4;
				explored[44] |= 0b111 << G.me.x - 4;
				explored[45] |= 0b1 << G.me.x - 3;
				break;
			case 43:
				explored[40] |= 0b1 << G.me.x - 3;
				explored[41] |= 0b111 << G.me.x - 4;
				explored[42] |= 0b1111 << G.me.x - 4;
				explored[43] |= 0b11111 << G.me.x - 4;
				explored[44] |= 0b1111 << G.me.x - 4;
				explored[45] |= 0b111 << G.me.x - 4;
				explored[46] |= 0b1 << G.me.x - 3;
				break;
			case 44:
				explored[41] |= 0b1 << G.me.x - 3;
				explored[42] |= 0b111 << G.me.x - 4;
				explored[43] |= 0b1111 << G.me.x - 4;
				explored[44] |= 0b11111 << G.me.x - 4;
				explored[45] |= 0b1111 << G.me.x - 4;
				explored[46] |= 0b111 << G.me.x - 4;
				explored[47] |= 0b1 << G.me.x - 3;
				break;
			case 45:
				explored[42] |= 0b1 << G.me.x - 3;
				explored[43] |= 0b111 << G.me.x - 4;
				explored[44] |= 0b1111 << G.me.x - 4;
				explored[45] |= 0b11111 << G.me.x - 4;
				explored[46] |= 0b1111 << G.me.x - 4;
				explored[47] |= 0b111 << G.me.x - 4;
				explored[48] |= 0b1 << G.me.x - 3;
				break;
			case 46:
				explored[43] |= 0b1 << G.me.x - 3;
				explored[44] |= 0b111 << G.me.x - 4;
				explored[45] |= 0b1111 << G.me.x - 4;
				explored[46] |= 0b11111 << G.me.x - 4;
				explored[47] |= 0b1111 << G.me.x - 4;
				explored[48] |= 0b111 << G.me.x - 4;
				explored[49] |= 0b1 << G.me.x - 3;
				break;
			case 47:
				explored[44] |= 0b1 << G.me.x - 3;
				explored[45] |= 0b111 << G.me.x - 4;
				explored[46] |= 0b1111 << G.me.x - 4;
				explored[47] |= 0b11111 << G.me.x - 4;
				explored[48] |= 0b1111 << G.me.x - 4;
				explored[49] |= 0b111 << G.me.x - 4;
				explored[50] |= 0b1 << G.me.x - 3;
				break;
			case 48:
				explored[45] |= 0b1 << G.me.x - 3;
				explored[46] |= 0b111 << G.me.x - 4;
				explored[47] |= 0b1111 << G.me.x - 4;
				explored[48] |= 0b11111 << G.me.x - 4;
				explored[49] |= 0b1111 << G.me.x - 4;
				explored[50] |= 0b111 << G.me.x - 4;
				explored[51] |= 0b1 << G.me.x - 3;
				break;
			case 49:
				explored[46] |= 0b1 << G.me.x - 3;
				explored[47] |= 0b111 << G.me.x - 4;
				explored[48] |= 0b1111 << G.me.x - 4;
				explored[49] |= 0b11111 << G.me.x - 4;
				explored[50] |= 0b1111 << G.me.x - 4;
				explored[51] |= 0b111 << G.me.x - 4;
				explored[52] |= 0b1 << G.me.x - 3;
				break;
			case 50:
				explored[47] |= 0b1 << G.me.x - 3;
				explored[48] |= 0b111 << G.me.x - 4;
				explored[49] |= 0b1111 << G.me.x - 4;
				explored[50] |= 0b11111 << G.me.x - 4;
				explored[51] |= 0b1111 << G.me.x - 4;
				explored[52] |= 0b111 << G.me.x - 4;
				explored[53] |= 0b1 << G.me.x - 3;
				break;
			case 51:
				explored[48] |= 0b1 << G.me.x - 3;
				explored[49] |= 0b111 << G.me.x - 4;
				explored[50] |= 0b1111 << G.me.x - 4;
				explored[51] |= 0b11111 << G.me.x - 4;
				explored[52] |= 0b1111 << G.me.x - 4;
				explored[53] |= 0b111 << G.me.x - 4;
				explored[54] |= 0b1 << G.me.x - 3;
				break;
			case 52:
				explored[49] |= 0b1 << G.me.x - 3;
				explored[50] |= 0b111 << G.me.x - 4;
				explored[51] |= 0b1111 << G.me.x - 4;
				explored[52] |= 0b11111 << G.me.x - 4;
				explored[53] |= 0b1111 << G.me.x - 4;
				explored[54] |= 0b111 << G.me.x - 4;
				explored[55] |= 0b1 << G.me.x - 3;
				break;
			case 53:
				explored[50] |= 0b1 << G.me.x - 3;
				explored[51] |= 0b111 << G.me.x - 4;
				explored[52] |= 0b1111 << G.me.x - 4;
				explored[53] |= 0b11111 << G.me.x - 4;
				explored[54] |= 0b1111 << G.me.x - 4;
				explored[55] |= 0b111 << G.me.x - 4;
				explored[56] |= 0b1 << G.me.x - 3;
				break;
			case 54:
				explored[51] |= 0b1 << G.me.x - 3;
				explored[52] |= 0b111 << G.me.x - 4;
				explored[53] |= 0b1111 << G.me.x - 4;
				explored[54] |= 0b11111 << G.me.x - 4;
				explored[55] |= 0b1111 << G.me.x - 4;
				explored[56] |= 0b111 << G.me.x - 4;
				explored[57] |= 0b1 << G.me.x - 3;
				break;
			case 55:
				explored[52] |= 0b1 << G.me.x - 3;
				explored[53] |= 0b111 << G.me.x - 4;
				explored[54] |= 0b1111 << G.me.x - 4;
				explored[55] |= 0b11111 << G.me.x - 4;
				explored[56] |= 0b1111 << G.me.x - 4;
				explored[57] |= 0b111 << G.me.x - 4;
				explored[58] |= 0b1 << G.me.x - 3;
				break;
			case 56:
				explored[53] |= 0b1 << G.me.x - 3;
				explored[54] |= 0b111 << G.me.x - 4;
				explored[55] |= 0b1111 << G.me.x - 4;
				explored[56] |= 0b11111 << G.me.x - 4;
				explored[57] |= 0b1111 << G.me.x - 4;
				explored[58] |= 0b111 << G.me.x - 4;
				explored[59] |= 0b1 << G.me.x - 3;
				break;
			case 57:
				explored[54] |= 0b1 << G.me.x - 3;
				explored[55] |= 0b111 << G.me.x - 4;
				explored[56] |= 0b1111 << G.me.x - 4;
				explored[57] |= 0b11111 << G.me.x - 4;
				explored[58] |= 0b1111 << G.me.x - 4;
				explored[59] |= 0b111 << G.me.x - 4;
				break;
			case 58:
				explored[55] |= 0b1 << G.me.x - 3;
				explored[56] |= 0b111 << G.me.x - 4;
				explored[57] |= 0b1111 << G.me.x - 4;
				explored[58] |= 0b11111 << G.me.x - 4;
				explored[59] |= 0b1111 << G.me.x - 4;
				break;
			case 59:
				explored[56] |= 0b1 << G.me.x - 3;
				explored[57] |= 0b111 << G.me.x - 4;
				explored[58] |= 0b1111 << G.me.x - 4;
				explored[59] |= 0b11111 << G.me.x - 4;
				break;
		}
	}
	public static void updateExploredRatKing() {
		switch (G.me.y) {
			case 0:
				explored[0] |= 0b11111111111 << G.me.x - 5;
				explored[1] |= 0b111111111 << G.me.x - 4;
				explored[2] |= 0b111111111 << G.me.x - 4;
				explored[3] |= 0b111111111 << G.me.x - 4;
				explored[4] |= 0b1111111 << G.me.x - 3;
				explored[5] |= 0b1 << G.me.x;
				break;
			case 1:
				explored[0] |= 0b111111111 << G.me.x - 4;
				explored[1] |= 0b11111111111 << G.me.x - 5;
				explored[2] |= 0b111111111 << G.me.x - 4;
				explored[3] |= 0b111111111 << G.me.x - 4;
				explored[4] |= 0b111111111 << G.me.x - 4;
				explored[5] |= 0b1111111 << G.me.x - 3;
				explored[6] |= 0b1 << G.me.x;
				break;
			case 2:
				explored[0] |= 0b111111111 << G.me.x - 4;
				explored[1] |= 0b111111111 << G.me.x - 4;
				explored[2] |= 0b11111111111 << G.me.x - 5;
				explored[3] |= 0b111111111 << G.me.x - 4;
				explored[4] |= 0b111111111 << G.me.x - 4;
				explored[5] |= 0b111111111 << G.me.x - 4;
				explored[6] |= 0b1111111 << G.me.x - 3;
				explored[7] |= 0b1 << G.me.x;
				break;
			case 3:
				explored[0] |= 0b111111111 << G.me.x - 4;
				explored[1] |= 0b111111111 << G.me.x - 4;
				explored[2] |= 0b111111111 << G.me.x - 4;
				explored[3] |= 0b11111111111 << G.me.x - 5;
				explored[4] |= 0b111111111 << G.me.x - 4;
				explored[5] |= 0b111111111 << G.me.x - 4;
				explored[6] |= 0b111111111 << G.me.x - 4;
				explored[7] |= 0b1111111 << G.me.x - 3;
				explored[8] |= 0b1 << G.me.x;
				break;
			case 4:
				explored[0] |= 0b1111111 << G.me.x - 3;
				explored[1] |= 0b111111111 << G.me.x - 4;
				explored[2] |= 0b111111111 << G.me.x - 4;
				explored[3] |= 0b111111111 << G.me.x - 4;
				explored[4] |= 0b11111111111 << G.me.x - 5;
				explored[5] |= 0b111111111 << G.me.x - 4;
				explored[6] |= 0b111111111 << G.me.x - 4;
				explored[7] |= 0b111111111 << G.me.x - 4;
				explored[8] |= 0b1111111 << G.me.x - 3;
				explored[9] |= 0b1 << G.me.x;
				break;
			case 5:
				explored[0] |= 0b1 << G.me.x;
				explored[1] |= 0b1111111 << G.me.x - 3;
				explored[2] |= 0b111111111 << G.me.x - 4;
				explored[3] |= 0b111111111 << G.me.x - 4;
				explored[4] |= 0b111111111 << G.me.x - 4;
				explored[5] |= 0b11111111111 << G.me.x - 5;
				explored[6] |= 0b111111111 << G.me.x - 4;
				explored[7] |= 0b111111111 << G.me.x - 4;
				explored[8] |= 0b111111111 << G.me.x - 4;
				explored[9] |= 0b1111111 << G.me.x - 3;
				explored[10] |= 0b1 << G.me.x;
				break;
			case 6:
				explored[1] |= 0b1 << G.me.x;
				explored[2] |= 0b1111111 << G.me.x - 3;
				explored[3] |= 0b111111111 << G.me.x - 4;
				explored[4] |= 0b111111111 << G.me.x - 4;
				explored[5] |= 0b111111111 << G.me.x - 4;
				explored[6] |= 0b11111111111 << G.me.x - 5;
				explored[7] |= 0b111111111 << G.me.x - 4;
				explored[8] |= 0b111111111 << G.me.x - 4;
				explored[9] |= 0b111111111 << G.me.x - 4;
				explored[10] |= 0b1111111 << G.me.x - 3;
				explored[11] |= 0b1 << G.me.x;
				break;
			case 7:
				explored[2] |= 0b1 << G.me.x;
				explored[3] |= 0b1111111 << G.me.x - 3;
				explored[4] |= 0b111111111 << G.me.x - 4;
				explored[5] |= 0b111111111 << G.me.x - 4;
				explored[6] |= 0b111111111 << G.me.x - 4;
				explored[7] |= 0b11111111111 << G.me.x - 5;
				explored[8] |= 0b111111111 << G.me.x - 4;
				explored[9] |= 0b111111111 << G.me.x - 4;
				explored[10] |= 0b111111111 << G.me.x - 4;
				explored[11] |= 0b1111111 << G.me.x - 3;
				explored[12] |= 0b1 << G.me.x;
				break;
			case 8:
				explored[3] |= 0b1 << G.me.x;
				explored[4] |= 0b1111111 << G.me.x - 3;
				explored[5] |= 0b111111111 << G.me.x - 4;
				explored[6] |= 0b111111111 << G.me.x - 4;
				explored[7] |= 0b111111111 << G.me.x - 4;
				explored[8] |= 0b11111111111 << G.me.x - 5;
				explored[9] |= 0b111111111 << G.me.x - 4;
				explored[10] |= 0b111111111 << G.me.x - 4;
				explored[11] |= 0b111111111 << G.me.x - 4;
				explored[12] |= 0b1111111 << G.me.x - 3;
				explored[13] |= 0b1 << G.me.x;
				break;
			case 9:
				explored[4] |= 0b1 << G.me.x;
				explored[5] |= 0b1111111 << G.me.x - 3;
				explored[6] |= 0b111111111 << G.me.x - 4;
				explored[7] |= 0b111111111 << G.me.x - 4;
				explored[8] |= 0b111111111 << G.me.x - 4;
				explored[9] |= 0b11111111111 << G.me.x - 5;
				explored[10] |= 0b111111111 << G.me.x - 4;
				explored[11] |= 0b111111111 << G.me.x - 4;
				explored[12] |= 0b111111111 << G.me.x - 4;
				explored[13] |= 0b1111111 << G.me.x - 3;
				explored[14] |= 0b1 << G.me.x;
				break;
			case 10:
				explored[5] |= 0b1 << G.me.x;
				explored[6] |= 0b1111111 << G.me.x - 3;
				explored[7] |= 0b111111111 << G.me.x - 4;
				explored[8] |= 0b111111111 << G.me.x - 4;
				explored[9] |= 0b111111111 << G.me.x - 4;
				explored[10] |= 0b11111111111 << G.me.x - 5;
				explored[11] |= 0b111111111 << G.me.x - 4;
				explored[12] |= 0b111111111 << G.me.x - 4;
				explored[13] |= 0b111111111 << G.me.x - 4;
				explored[14] |= 0b1111111 << G.me.x - 3;
				explored[15] |= 0b1 << G.me.x;
				break;
			case 11:
				explored[6] |= 0b1 << G.me.x;
				explored[7] |= 0b1111111 << G.me.x - 3;
				explored[8] |= 0b111111111 << G.me.x - 4;
				explored[9] |= 0b111111111 << G.me.x - 4;
				explored[10] |= 0b111111111 << G.me.x - 4;
				explored[11] |= 0b11111111111 << G.me.x - 5;
				explored[12] |= 0b111111111 << G.me.x - 4;
				explored[13] |= 0b111111111 << G.me.x - 4;
				explored[14] |= 0b111111111 << G.me.x - 4;
				explored[15] |= 0b1111111 << G.me.x - 3;
				explored[16] |= 0b1 << G.me.x;
				break;
			case 12:
				explored[7] |= 0b1 << G.me.x;
				explored[8] |= 0b1111111 << G.me.x - 3;
				explored[9] |= 0b111111111 << G.me.x - 4;
				explored[10] |= 0b111111111 << G.me.x - 4;
				explored[11] |= 0b111111111 << G.me.x - 4;
				explored[12] |= 0b11111111111 << G.me.x - 5;
				explored[13] |= 0b111111111 << G.me.x - 4;
				explored[14] |= 0b111111111 << G.me.x - 4;
				explored[15] |= 0b111111111 << G.me.x - 4;
				explored[16] |= 0b1111111 << G.me.x - 3;
				explored[17] |= 0b1 << G.me.x;
				break;
			case 13:
				explored[8] |= 0b1 << G.me.x;
				explored[9] |= 0b1111111 << G.me.x - 3;
				explored[10] |= 0b111111111 << G.me.x - 4;
				explored[11] |= 0b111111111 << G.me.x - 4;
				explored[12] |= 0b111111111 << G.me.x - 4;
				explored[13] |= 0b11111111111 << G.me.x - 5;
				explored[14] |= 0b111111111 << G.me.x - 4;
				explored[15] |= 0b111111111 << G.me.x - 4;
				explored[16] |= 0b111111111 << G.me.x - 4;
				explored[17] |= 0b1111111 << G.me.x - 3;
				explored[18] |= 0b1 << G.me.x;
				break;
			case 14:
				explored[9] |= 0b1 << G.me.x;
				explored[10] |= 0b1111111 << G.me.x - 3;
				explored[11] |= 0b111111111 << G.me.x - 4;
				explored[12] |= 0b111111111 << G.me.x - 4;
				explored[13] |= 0b111111111 << G.me.x - 4;
				explored[14] |= 0b11111111111 << G.me.x - 5;
				explored[15] |= 0b111111111 << G.me.x - 4;
				explored[16] |= 0b111111111 << G.me.x - 4;
				explored[17] |= 0b111111111 << G.me.x - 4;
				explored[18] |= 0b1111111 << G.me.x - 3;
				explored[19] |= 0b1 << G.me.x;
				break;
			case 15:
				explored[10] |= 0b1 << G.me.x;
				explored[11] |= 0b1111111 << G.me.x - 3;
				explored[12] |= 0b111111111 << G.me.x - 4;
				explored[13] |= 0b111111111 << G.me.x - 4;
				explored[14] |= 0b111111111 << G.me.x - 4;
				explored[15] |= 0b11111111111 << G.me.x - 5;
				explored[16] |= 0b111111111 << G.me.x - 4;
				explored[17] |= 0b111111111 << G.me.x - 4;
				explored[18] |= 0b111111111 << G.me.x - 4;
				explored[19] |= 0b1111111 << G.me.x - 3;
				explored[20] |= 0b1 << G.me.x;
				break;
			case 16:
				explored[11] |= 0b1 << G.me.x;
				explored[12] |= 0b1111111 << G.me.x - 3;
				explored[13] |= 0b111111111 << G.me.x - 4;
				explored[14] |= 0b111111111 << G.me.x - 4;
				explored[15] |= 0b111111111 << G.me.x - 4;
				explored[16] |= 0b11111111111 << G.me.x - 5;
				explored[17] |= 0b111111111 << G.me.x - 4;
				explored[18] |= 0b111111111 << G.me.x - 4;
				explored[19] |= 0b111111111 << G.me.x - 4;
				explored[20] |= 0b1111111 << G.me.x - 3;
				explored[21] |= 0b1 << G.me.x;
				break;
			case 17:
				explored[12] |= 0b1 << G.me.x;
				explored[13] |= 0b1111111 << G.me.x - 3;
				explored[14] |= 0b111111111 << G.me.x - 4;
				explored[15] |= 0b111111111 << G.me.x - 4;
				explored[16] |= 0b111111111 << G.me.x - 4;
				explored[17] |= 0b11111111111 << G.me.x - 5;
				explored[18] |= 0b111111111 << G.me.x - 4;
				explored[19] |= 0b111111111 << G.me.x - 4;
				explored[20] |= 0b111111111 << G.me.x - 4;
				explored[21] |= 0b1111111 << G.me.x - 3;
				explored[22] |= 0b1 << G.me.x;
				break;
			case 18:
				explored[13] |= 0b1 << G.me.x;
				explored[14] |= 0b1111111 << G.me.x - 3;
				explored[15] |= 0b111111111 << G.me.x - 4;
				explored[16] |= 0b111111111 << G.me.x - 4;
				explored[17] |= 0b111111111 << G.me.x - 4;
				explored[18] |= 0b11111111111 << G.me.x - 5;
				explored[19] |= 0b111111111 << G.me.x - 4;
				explored[20] |= 0b111111111 << G.me.x - 4;
				explored[21] |= 0b111111111 << G.me.x - 4;
				explored[22] |= 0b1111111 << G.me.x - 3;
				explored[23] |= 0b1 << G.me.x;
				break;
			case 19:
				explored[14] |= 0b1 << G.me.x;
				explored[15] |= 0b1111111 << G.me.x - 3;
				explored[16] |= 0b111111111 << G.me.x - 4;
				explored[17] |= 0b111111111 << G.me.x - 4;
				explored[18] |= 0b111111111 << G.me.x - 4;
				explored[19] |= 0b11111111111 << G.me.x - 5;
				explored[20] |= 0b111111111 << G.me.x - 4;
				explored[21] |= 0b111111111 << G.me.x - 4;
				explored[22] |= 0b111111111 << G.me.x - 4;
				explored[23] |= 0b1111111 << G.me.x - 3;
				explored[24] |= 0b1 << G.me.x;
				break;
			case 20:
				explored[15] |= 0b1 << G.me.x;
				explored[16] |= 0b1111111 << G.me.x - 3;
				explored[17] |= 0b111111111 << G.me.x - 4;
				explored[18] |= 0b111111111 << G.me.x - 4;
				explored[19] |= 0b111111111 << G.me.x - 4;
				explored[20] |= 0b11111111111 << G.me.x - 5;
				explored[21] |= 0b111111111 << G.me.x - 4;
				explored[22] |= 0b111111111 << G.me.x - 4;
				explored[23] |= 0b111111111 << G.me.x - 4;
				explored[24] |= 0b1111111 << G.me.x - 3;
				explored[25] |= 0b1 << G.me.x;
				break;
			case 21:
				explored[16] |= 0b1 << G.me.x;
				explored[17] |= 0b1111111 << G.me.x - 3;
				explored[18] |= 0b111111111 << G.me.x - 4;
				explored[19] |= 0b111111111 << G.me.x - 4;
				explored[20] |= 0b111111111 << G.me.x - 4;
				explored[21] |= 0b11111111111 << G.me.x - 5;
				explored[22] |= 0b111111111 << G.me.x - 4;
				explored[23] |= 0b111111111 << G.me.x - 4;
				explored[24] |= 0b111111111 << G.me.x - 4;
				explored[25] |= 0b1111111 << G.me.x - 3;
				explored[26] |= 0b1 << G.me.x;
				break;
			case 22:
				explored[17] |= 0b1 << G.me.x;
				explored[18] |= 0b1111111 << G.me.x - 3;
				explored[19] |= 0b111111111 << G.me.x - 4;
				explored[20] |= 0b111111111 << G.me.x - 4;
				explored[21] |= 0b111111111 << G.me.x - 4;
				explored[22] |= 0b11111111111 << G.me.x - 5;
				explored[23] |= 0b111111111 << G.me.x - 4;
				explored[24] |= 0b111111111 << G.me.x - 4;
				explored[25] |= 0b111111111 << G.me.x - 4;
				explored[26] |= 0b1111111 << G.me.x - 3;
				explored[27] |= 0b1 << G.me.x;
				break;
			case 23:
				explored[18] |= 0b1 << G.me.x;
				explored[19] |= 0b1111111 << G.me.x - 3;
				explored[20] |= 0b111111111 << G.me.x - 4;
				explored[21] |= 0b111111111 << G.me.x - 4;
				explored[22] |= 0b111111111 << G.me.x - 4;
				explored[23] |= 0b11111111111 << G.me.x - 5;
				explored[24] |= 0b111111111 << G.me.x - 4;
				explored[25] |= 0b111111111 << G.me.x - 4;
				explored[26] |= 0b111111111 << G.me.x - 4;
				explored[27] |= 0b1111111 << G.me.x - 3;
				explored[28] |= 0b1 << G.me.x;
				break;
			case 24:
				explored[19] |= 0b1 << G.me.x;
				explored[20] |= 0b1111111 << G.me.x - 3;
				explored[21] |= 0b111111111 << G.me.x - 4;
				explored[22] |= 0b111111111 << G.me.x - 4;
				explored[23] |= 0b111111111 << G.me.x - 4;
				explored[24] |= 0b11111111111 << G.me.x - 5;
				explored[25] |= 0b111111111 << G.me.x - 4;
				explored[26] |= 0b111111111 << G.me.x - 4;
				explored[27] |= 0b111111111 << G.me.x - 4;
				explored[28] |= 0b1111111 << G.me.x - 3;
				explored[29] |= 0b1 << G.me.x;
				break;
			case 25:
				explored[20] |= 0b1 << G.me.x;
				explored[21] |= 0b1111111 << G.me.x - 3;
				explored[22] |= 0b111111111 << G.me.x - 4;
				explored[23] |= 0b111111111 << G.me.x - 4;
				explored[24] |= 0b111111111 << G.me.x - 4;
				explored[25] |= 0b11111111111 << G.me.x - 5;
				explored[26] |= 0b111111111 << G.me.x - 4;
				explored[27] |= 0b111111111 << G.me.x - 4;
				explored[28] |= 0b111111111 << G.me.x - 4;
				explored[29] |= 0b1111111 << G.me.x - 3;
				explored[30] |= 0b1 << G.me.x;
				break;
			case 26:
				explored[21] |= 0b1 << G.me.x;
				explored[22] |= 0b1111111 << G.me.x - 3;
				explored[23] |= 0b111111111 << G.me.x - 4;
				explored[24] |= 0b111111111 << G.me.x - 4;
				explored[25] |= 0b111111111 << G.me.x - 4;
				explored[26] |= 0b11111111111 << G.me.x - 5;
				explored[27] |= 0b111111111 << G.me.x - 4;
				explored[28] |= 0b111111111 << G.me.x - 4;
				explored[29] |= 0b111111111 << G.me.x - 4;
				explored[30] |= 0b1111111 << G.me.x - 3;
				explored[31] |= 0b1 << G.me.x;
				break;
			case 27:
				explored[22] |= 0b1 << G.me.x;
				explored[23] |= 0b1111111 << G.me.x - 3;
				explored[24] |= 0b111111111 << G.me.x - 4;
				explored[25] |= 0b111111111 << G.me.x - 4;
				explored[26] |= 0b111111111 << G.me.x - 4;
				explored[27] |= 0b11111111111 << G.me.x - 5;
				explored[28] |= 0b111111111 << G.me.x - 4;
				explored[29] |= 0b111111111 << G.me.x - 4;
				explored[30] |= 0b111111111 << G.me.x - 4;
				explored[31] |= 0b1111111 << G.me.x - 3;
				explored[32] |= 0b1 << G.me.x;
				break;
			case 28:
				explored[23] |= 0b1 << G.me.x;
				explored[24] |= 0b1111111 << G.me.x - 3;
				explored[25] |= 0b111111111 << G.me.x - 4;
				explored[26] |= 0b111111111 << G.me.x - 4;
				explored[27] |= 0b111111111 << G.me.x - 4;
				explored[28] |= 0b11111111111 << G.me.x - 5;
				explored[29] |= 0b111111111 << G.me.x - 4;
				explored[30] |= 0b111111111 << G.me.x - 4;
				explored[31] |= 0b111111111 << G.me.x - 4;
				explored[32] |= 0b1111111 << G.me.x - 3;
				explored[33] |= 0b1 << G.me.x;
				break;
			case 29:
				explored[24] |= 0b1 << G.me.x;
				explored[25] |= 0b1111111 << G.me.x - 3;
				explored[26] |= 0b111111111 << G.me.x - 4;
				explored[27] |= 0b111111111 << G.me.x - 4;
				explored[28] |= 0b111111111 << G.me.x - 4;
				explored[29] |= 0b11111111111 << G.me.x - 5;
				explored[30] |= 0b111111111 << G.me.x - 4;
				explored[31] |= 0b111111111 << G.me.x - 4;
				explored[32] |= 0b111111111 << G.me.x - 4;
				explored[33] |= 0b1111111 << G.me.x - 3;
				explored[34] |= 0b1 << G.me.x;
				break;
			case 30:
				explored[25] |= 0b1 << G.me.x;
				explored[26] |= 0b1111111 << G.me.x - 3;
				explored[27] |= 0b111111111 << G.me.x - 4;
				explored[28] |= 0b111111111 << G.me.x - 4;
				explored[29] |= 0b111111111 << G.me.x - 4;
				explored[30] |= 0b11111111111 << G.me.x - 5;
				explored[31] |= 0b111111111 << G.me.x - 4;
				explored[32] |= 0b111111111 << G.me.x - 4;
				explored[33] |= 0b111111111 << G.me.x - 4;
				explored[34] |= 0b1111111 << G.me.x - 3;
				explored[35] |= 0b1 << G.me.x;
				break;
			case 31:
				explored[26] |= 0b1 << G.me.x;
				explored[27] |= 0b1111111 << G.me.x - 3;
				explored[28] |= 0b111111111 << G.me.x - 4;
				explored[29] |= 0b111111111 << G.me.x - 4;
				explored[30] |= 0b111111111 << G.me.x - 4;
				explored[31] |= 0b11111111111 << G.me.x - 5;
				explored[32] |= 0b111111111 << G.me.x - 4;
				explored[33] |= 0b111111111 << G.me.x - 4;
				explored[34] |= 0b111111111 << G.me.x - 4;
				explored[35] |= 0b1111111 << G.me.x - 3;
				explored[36] |= 0b1 << G.me.x;
				break;
			case 32:
				explored[27] |= 0b1 << G.me.x;
				explored[28] |= 0b1111111 << G.me.x - 3;
				explored[29] |= 0b111111111 << G.me.x - 4;
				explored[30] |= 0b111111111 << G.me.x - 4;
				explored[31] |= 0b111111111 << G.me.x - 4;
				explored[32] |= 0b11111111111 << G.me.x - 5;
				explored[33] |= 0b111111111 << G.me.x - 4;
				explored[34] |= 0b111111111 << G.me.x - 4;
				explored[35] |= 0b111111111 << G.me.x - 4;
				explored[36] |= 0b1111111 << G.me.x - 3;
				explored[37] |= 0b1 << G.me.x;
				break;
			case 33:
				explored[28] |= 0b1 << G.me.x;
				explored[29] |= 0b1111111 << G.me.x - 3;
				explored[30] |= 0b111111111 << G.me.x - 4;
				explored[31] |= 0b111111111 << G.me.x - 4;
				explored[32] |= 0b111111111 << G.me.x - 4;
				explored[33] |= 0b11111111111 << G.me.x - 5;
				explored[34] |= 0b111111111 << G.me.x - 4;
				explored[35] |= 0b111111111 << G.me.x - 4;
				explored[36] |= 0b111111111 << G.me.x - 4;
				explored[37] |= 0b1111111 << G.me.x - 3;
				explored[38] |= 0b1 << G.me.x;
				break;
			case 34:
				explored[29] |= 0b1 << G.me.x;
				explored[30] |= 0b1111111 << G.me.x - 3;
				explored[31] |= 0b111111111 << G.me.x - 4;
				explored[32] |= 0b111111111 << G.me.x - 4;
				explored[33] |= 0b111111111 << G.me.x - 4;
				explored[34] |= 0b11111111111 << G.me.x - 5;
				explored[35] |= 0b111111111 << G.me.x - 4;
				explored[36] |= 0b111111111 << G.me.x - 4;
				explored[37] |= 0b111111111 << G.me.x - 4;
				explored[38] |= 0b1111111 << G.me.x - 3;
				explored[39] |= 0b1 << G.me.x;
				break;
			case 35:
				explored[30] |= 0b1 << G.me.x;
				explored[31] |= 0b1111111 << G.me.x - 3;
				explored[32] |= 0b111111111 << G.me.x - 4;
				explored[33] |= 0b111111111 << G.me.x - 4;
				explored[34] |= 0b111111111 << G.me.x - 4;
				explored[35] |= 0b11111111111 << G.me.x - 5;
				explored[36] |= 0b111111111 << G.me.x - 4;
				explored[37] |= 0b111111111 << G.me.x - 4;
				explored[38] |= 0b111111111 << G.me.x - 4;
				explored[39] |= 0b1111111 << G.me.x - 3;
				explored[40] |= 0b1 << G.me.x;
				break;
			case 36:
				explored[31] |= 0b1 << G.me.x;
				explored[32] |= 0b1111111 << G.me.x - 3;
				explored[33] |= 0b111111111 << G.me.x - 4;
				explored[34] |= 0b111111111 << G.me.x - 4;
				explored[35] |= 0b111111111 << G.me.x - 4;
				explored[36] |= 0b11111111111 << G.me.x - 5;
				explored[37] |= 0b111111111 << G.me.x - 4;
				explored[38] |= 0b111111111 << G.me.x - 4;
				explored[39] |= 0b111111111 << G.me.x - 4;
				explored[40] |= 0b1111111 << G.me.x - 3;
				explored[41] |= 0b1 << G.me.x;
				break;
			case 37:
				explored[32] |= 0b1 << G.me.x;
				explored[33] |= 0b1111111 << G.me.x - 3;
				explored[34] |= 0b111111111 << G.me.x - 4;
				explored[35] |= 0b111111111 << G.me.x - 4;
				explored[36] |= 0b111111111 << G.me.x - 4;
				explored[37] |= 0b11111111111 << G.me.x - 5;
				explored[38] |= 0b111111111 << G.me.x - 4;
				explored[39] |= 0b111111111 << G.me.x - 4;
				explored[40] |= 0b111111111 << G.me.x - 4;
				explored[41] |= 0b1111111 << G.me.x - 3;
				explored[42] |= 0b1 << G.me.x;
				break;
			case 38:
				explored[33] |= 0b1 << G.me.x;
				explored[34] |= 0b1111111 << G.me.x - 3;
				explored[35] |= 0b111111111 << G.me.x - 4;
				explored[36] |= 0b111111111 << G.me.x - 4;
				explored[37] |= 0b111111111 << G.me.x - 4;
				explored[38] |= 0b11111111111 << G.me.x - 5;
				explored[39] |= 0b111111111 << G.me.x - 4;
				explored[40] |= 0b111111111 << G.me.x - 4;
				explored[41] |= 0b111111111 << G.me.x - 4;
				explored[42] |= 0b1111111 << G.me.x - 3;
				explored[43] |= 0b1 << G.me.x;
				break;
			case 39:
				explored[34] |= 0b1 << G.me.x;
				explored[35] |= 0b1111111 << G.me.x - 3;
				explored[36] |= 0b111111111 << G.me.x - 4;
				explored[37] |= 0b111111111 << G.me.x - 4;
				explored[38] |= 0b111111111 << G.me.x - 4;
				explored[39] |= 0b11111111111 << G.me.x - 5;
				explored[40] |= 0b111111111 << G.me.x - 4;
				explored[41] |= 0b111111111 << G.me.x - 4;
				explored[42] |= 0b111111111 << G.me.x - 4;
				explored[43] |= 0b1111111 << G.me.x - 3;
				explored[44] |= 0b1 << G.me.x;
				break;
			case 40:
				explored[35] |= 0b1 << G.me.x;
				explored[36] |= 0b1111111 << G.me.x - 3;
				explored[37] |= 0b111111111 << G.me.x - 4;
				explored[38] |= 0b111111111 << G.me.x - 4;
				explored[39] |= 0b111111111 << G.me.x - 4;
				explored[40] |= 0b11111111111 << G.me.x - 5;
				explored[41] |= 0b111111111 << G.me.x - 4;
				explored[42] |= 0b111111111 << G.me.x - 4;
				explored[43] |= 0b111111111 << G.me.x - 4;
				explored[44] |= 0b1111111 << G.me.x - 3;
				explored[45] |= 0b1 << G.me.x;
				break;
			case 41:
				explored[36] |= 0b1 << G.me.x;
				explored[37] |= 0b1111111 << G.me.x - 3;
				explored[38] |= 0b111111111 << G.me.x - 4;
				explored[39] |= 0b111111111 << G.me.x - 4;
				explored[40] |= 0b111111111 << G.me.x - 4;
				explored[41] |= 0b11111111111 << G.me.x - 5;
				explored[42] |= 0b111111111 << G.me.x - 4;
				explored[43] |= 0b111111111 << G.me.x - 4;
				explored[44] |= 0b111111111 << G.me.x - 4;
				explored[45] |= 0b1111111 << G.me.x - 3;
				explored[46] |= 0b1 << G.me.x;
				break;
			case 42:
				explored[37] |= 0b1 << G.me.x;
				explored[38] |= 0b1111111 << G.me.x - 3;
				explored[39] |= 0b111111111 << G.me.x - 4;
				explored[40] |= 0b111111111 << G.me.x - 4;
				explored[41] |= 0b111111111 << G.me.x - 4;
				explored[42] |= 0b11111111111 << G.me.x - 5;
				explored[43] |= 0b111111111 << G.me.x - 4;
				explored[44] |= 0b111111111 << G.me.x - 4;
				explored[45] |= 0b111111111 << G.me.x - 4;
				explored[46] |= 0b1111111 << G.me.x - 3;
				explored[47] |= 0b1 << G.me.x;
				break;
			case 43:
				explored[38] |= 0b1 << G.me.x;
				explored[39] |= 0b1111111 << G.me.x - 3;
				explored[40] |= 0b111111111 << G.me.x - 4;
				explored[41] |= 0b111111111 << G.me.x - 4;
				explored[42] |= 0b111111111 << G.me.x - 4;
				explored[43] |= 0b11111111111 << G.me.x - 5;
				explored[44] |= 0b111111111 << G.me.x - 4;
				explored[45] |= 0b111111111 << G.me.x - 4;
				explored[46] |= 0b111111111 << G.me.x - 4;
				explored[47] |= 0b1111111 << G.me.x - 3;
				explored[48] |= 0b1 << G.me.x;
				break;
			case 44:
				explored[39] |= 0b1 << G.me.x;
				explored[40] |= 0b1111111 << G.me.x - 3;
				explored[41] |= 0b111111111 << G.me.x - 4;
				explored[42] |= 0b111111111 << G.me.x - 4;
				explored[43] |= 0b111111111 << G.me.x - 4;
				explored[44] |= 0b11111111111 << G.me.x - 5;
				explored[45] |= 0b111111111 << G.me.x - 4;
				explored[46] |= 0b111111111 << G.me.x - 4;
				explored[47] |= 0b111111111 << G.me.x - 4;
				explored[48] |= 0b1111111 << G.me.x - 3;
				explored[49] |= 0b1 << G.me.x;
				break;
			case 45:
				explored[40] |= 0b1 << G.me.x;
				explored[41] |= 0b1111111 << G.me.x - 3;
				explored[42] |= 0b111111111 << G.me.x - 4;
				explored[43] |= 0b111111111 << G.me.x - 4;
				explored[44] |= 0b111111111 << G.me.x - 4;
				explored[45] |= 0b11111111111 << G.me.x - 5;
				explored[46] |= 0b111111111 << G.me.x - 4;
				explored[47] |= 0b111111111 << G.me.x - 4;
				explored[48] |= 0b111111111 << G.me.x - 4;
				explored[49] |= 0b1111111 << G.me.x - 3;
				explored[50] |= 0b1 << G.me.x;
				break;
			case 46:
				explored[41] |= 0b1 << G.me.x;
				explored[42] |= 0b1111111 << G.me.x - 3;
				explored[43] |= 0b111111111 << G.me.x - 4;
				explored[44] |= 0b111111111 << G.me.x - 4;
				explored[45] |= 0b111111111 << G.me.x - 4;
				explored[46] |= 0b11111111111 << G.me.x - 5;
				explored[47] |= 0b111111111 << G.me.x - 4;
				explored[48] |= 0b111111111 << G.me.x - 4;
				explored[49] |= 0b111111111 << G.me.x - 4;
				explored[50] |= 0b1111111 << G.me.x - 3;
				explored[51] |= 0b1 << G.me.x;
				break;
			case 47:
				explored[42] |= 0b1 << G.me.x;
				explored[43] |= 0b1111111 << G.me.x - 3;
				explored[44] |= 0b111111111 << G.me.x - 4;
				explored[45] |= 0b111111111 << G.me.x - 4;
				explored[46] |= 0b111111111 << G.me.x - 4;
				explored[47] |= 0b11111111111 << G.me.x - 5;
				explored[48] |= 0b111111111 << G.me.x - 4;
				explored[49] |= 0b111111111 << G.me.x - 4;
				explored[50] |= 0b111111111 << G.me.x - 4;
				explored[51] |= 0b1111111 << G.me.x - 3;
				explored[52] |= 0b1 << G.me.x;
				break;
			case 48:
				explored[43] |= 0b1 << G.me.x;
				explored[44] |= 0b1111111 << G.me.x - 3;
				explored[45] |= 0b111111111 << G.me.x - 4;
				explored[46] |= 0b111111111 << G.me.x - 4;
				explored[47] |= 0b111111111 << G.me.x - 4;
				explored[48] |= 0b11111111111 << G.me.x - 5;
				explored[49] |= 0b111111111 << G.me.x - 4;
				explored[50] |= 0b111111111 << G.me.x - 4;
				explored[51] |= 0b111111111 << G.me.x - 4;
				explored[52] |= 0b1111111 << G.me.x - 3;
				explored[53] |= 0b1 << G.me.x;
				break;
			case 49:
				explored[44] |= 0b1 << G.me.x;
				explored[45] |= 0b1111111 << G.me.x - 3;
				explored[46] |= 0b111111111 << G.me.x - 4;
				explored[47] |= 0b111111111 << G.me.x - 4;
				explored[48] |= 0b111111111 << G.me.x - 4;
				explored[49] |= 0b11111111111 << G.me.x - 5;
				explored[50] |= 0b111111111 << G.me.x - 4;
				explored[51] |= 0b111111111 << G.me.x - 4;
				explored[52] |= 0b111111111 << G.me.x - 4;
				explored[53] |= 0b1111111 << G.me.x - 3;
				explored[54] |= 0b1 << G.me.x;
				break;
			case 50:
				explored[45] |= 0b1 << G.me.x;
				explored[46] |= 0b1111111 << G.me.x - 3;
				explored[47] |= 0b111111111 << G.me.x - 4;
				explored[48] |= 0b111111111 << G.me.x - 4;
				explored[49] |= 0b111111111 << G.me.x - 4;
				explored[50] |= 0b11111111111 << G.me.x - 5;
				explored[51] |= 0b111111111 << G.me.x - 4;
				explored[52] |= 0b111111111 << G.me.x - 4;
				explored[53] |= 0b111111111 << G.me.x - 4;
				explored[54] |= 0b1111111 << G.me.x - 3;
				explored[55] |= 0b1 << G.me.x;
				break;
			case 51:
				explored[46] |= 0b1 << G.me.x;
				explored[47] |= 0b1111111 << G.me.x - 3;
				explored[48] |= 0b111111111 << G.me.x - 4;
				explored[49] |= 0b111111111 << G.me.x - 4;
				explored[50] |= 0b111111111 << G.me.x - 4;
				explored[51] |= 0b11111111111 << G.me.x - 5;
				explored[52] |= 0b111111111 << G.me.x - 4;
				explored[53] |= 0b111111111 << G.me.x - 4;
				explored[54] |= 0b111111111 << G.me.x - 4;
				explored[55] |= 0b1111111 << G.me.x - 3;
				explored[56] |= 0b1 << G.me.x;
				break;
			case 52:
				explored[47] |= 0b1 << G.me.x;
				explored[48] |= 0b1111111 << G.me.x - 3;
				explored[49] |= 0b111111111 << G.me.x - 4;
				explored[50] |= 0b111111111 << G.me.x - 4;
				explored[51] |= 0b111111111 << G.me.x - 4;
				explored[52] |= 0b11111111111 << G.me.x - 5;
				explored[53] |= 0b111111111 << G.me.x - 4;
				explored[54] |= 0b111111111 << G.me.x - 4;
				explored[55] |= 0b111111111 << G.me.x - 4;
				explored[56] |= 0b1111111 << G.me.x - 3;
				explored[57] |= 0b1 << G.me.x;
				break;
			case 53:
				explored[48] |= 0b1 << G.me.x;
				explored[49] |= 0b1111111 << G.me.x - 3;
				explored[50] |= 0b111111111 << G.me.x - 4;
				explored[51] |= 0b111111111 << G.me.x - 4;
				explored[52] |= 0b111111111 << G.me.x - 4;
				explored[53] |= 0b11111111111 << G.me.x - 5;
				explored[54] |= 0b111111111 << G.me.x - 4;
				explored[55] |= 0b111111111 << G.me.x - 4;
				explored[56] |= 0b111111111 << G.me.x - 4;
				explored[57] |= 0b1111111 << G.me.x - 3;
				explored[58] |= 0b1 << G.me.x;
				break;
			case 54:
				explored[49] |= 0b1 << G.me.x;
				explored[50] |= 0b1111111 << G.me.x - 3;
				explored[51] |= 0b111111111 << G.me.x - 4;
				explored[52] |= 0b111111111 << G.me.x - 4;
				explored[53] |= 0b111111111 << G.me.x - 4;
				explored[54] |= 0b11111111111 << G.me.x - 5;
				explored[55] |= 0b111111111 << G.me.x - 4;
				explored[56] |= 0b111111111 << G.me.x - 4;
				explored[57] |= 0b111111111 << G.me.x - 4;
				explored[58] |= 0b1111111 << G.me.x - 3;
				explored[59] |= 0b1 << G.me.x;
				break;
			case 55:
				explored[50] |= 0b1 << G.me.x;
				explored[51] |= 0b1111111 << G.me.x - 3;
				explored[52] |= 0b111111111 << G.me.x - 4;
				explored[53] |= 0b111111111 << G.me.x - 4;
				explored[54] |= 0b111111111 << G.me.x - 4;
				explored[55] |= 0b11111111111 << G.me.x - 5;
				explored[56] |= 0b111111111 << G.me.x - 4;
				explored[57] |= 0b111111111 << G.me.x - 4;
				explored[58] |= 0b111111111 << G.me.x - 4;
				explored[59] |= 0b1111111 << G.me.x - 3;
				break;
			case 56:
				explored[51] |= 0b1 << G.me.x;
				explored[52] |= 0b1111111 << G.me.x - 3;
				explored[53] |= 0b111111111 << G.me.x - 4;
				explored[54] |= 0b111111111 << G.me.x - 4;
				explored[55] |= 0b111111111 << G.me.x - 4;
				explored[56] |= 0b11111111111 << G.me.x - 5;
				explored[57] |= 0b111111111 << G.me.x - 4;
				explored[58] |= 0b111111111 << G.me.x - 4;
				explored[59] |= 0b111111111 << G.me.x - 4;
				break;
			case 57:
				explored[52] |= 0b1 << G.me.x;
				explored[53] |= 0b1111111 << G.me.x - 3;
				explored[54] |= 0b111111111 << G.me.x - 4;
				explored[55] |= 0b111111111 << G.me.x - 4;
				explored[56] |= 0b111111111 << G.me.x - 4;
				explored[57] |= 0b11111111111 << G.me.x - 5;
				explored[58] |= 0b111111111 << G.me.x - 4;
				explored[59] |= 0b111111111 << G.me.x - 4;
				break;
			case 58:
				explored[53] |= 0b1 << G.me.x;
				explored[54] |= 0b1111111 << G.me.x - 3;
				explored[55] |= 0b111111111 << G.me.x - 4;
				explored[56] |= 0b111111111 << G.me.x - 4;
				explored[57] |= 0b111111111 << G.me.x - 4;
				explored[58] |= 0b11111111111 << G.me.x - 5;
				explored[59] |= 0b111111111 << G.me.x - 4;
				break;
			case 59:
				explored[54] |= 0b1 << G.me.x;
				explored[55] |= 0b1111111 << G.me.x - 3;
				explored[56] |= 0b111111111 << G.me.x - 4;
				explored[57] |= 0b111111111 << G.me.x - 4;
				explored[58] |= 0b111111111 << G.me.x - 4;
				explored[59] |= 0b11111111111 << G.me.x - 5;
				break;
		}
	}


    public static boolean symmetryValid(int sym) throws Exception {
        int w = G.mapWidth;
        int h = G.mapHeight;
        int minY, maxY; // maxY is actually 1 higher than the actual maxY cuz inverted for loop
        switch (G.type) {
            case RAT_KING:
                minY = Math.max(G.me.y - 5, 0);
                maxY = Math.min(G.me.y + 6, h);
                break;
            case BABY_RAT:
                switch (G.dir) {
                    case Direction.SOUTHWEST:
                    case Direction.SOUTH:
                    case Direction.SOUTHEAST:
                        minY = Math.max(G.me.y - 4, 0);
                        maxY = G.me.y + 1;
                        break;
                    case Direction.NORTHEAST:
                    case Direction.NORTH:
                    case Direction.NORTHWEST:
                        minY = G.me.y;
                        maxY = Math.min(G.me.y + 5, h);
                        break;
                    case Direction.EAST:
                    case Direction.WEST:
                        minY = Math.max(G.me.y - 3, 0);
                        maxY = Math.min(G.me.y + 4, h);
                        break;
                    case Direction.CENTER:
                        //wtf?
                        throw new Exception("G.dir should not be CENTER");
                    default:
                        throw new Exception("something is very wrong with G.dir");
                }
                break;
            default:
                throw new Exception("something is very wrong with robot type");
        }
        switch (sym) {
            // only consider bits where we explored both it and its rotation
            case 0: // horz
                for (int i = maxY; --i >= minY;) {
                    long exploredRow = explored[i] & explored[h - i - 1];
                    if (((wall[i] ^ wall[h - i - 1]) & exploredRow) != 0)
                        return false;
                    if (((mine[i] ^ mine[h - i - 1]) & exploredRow) != 0) {
                        return false;
                    }
                }
                return true;
            case 1: // vert
                for (int i = maxY; --i >= minY;) {
                    long exploredRow = (Long.reverse(explored[i]) >> 64 - w) & explored[i];
                    if ((((Long.reverse(wall[i]) >> 64 - w) ^ wall[i]) & exploredRow) != 0)
                        return false;
                    if ((((Long.reverse(mine[i]) >> 64 - w) ^ mine[i]) & exploredRow) != 0)
                        return false;
                }
                return true;
            case 2: // rot
                for (int i = maxY; --i >= minY;) {
                    long exploredRow = (Long.reverse(explored[i]) >> 64 - w) & explored[h - i - 1];
                    if ((((Long.reverse(wall[i]) >> 64 - w) ^ wall[h - i - 1]) & exploredRow) != 0)
                        return false;
                    if ((((Long.reverse(mine[i]) >> 64 - w) ^ mine[h - i - 1]) & exploredRow) != 0)
                        return false;
                }
                return true;
            default:
                throw new Exception("invalid symmetry argument");
        }
    }

    public static MapLocation getOppositeMapLocation(MapLocation m, int sym) throws Exception {
        // get the opposite map location according to this symmetry
        switch (sym) {
            case 0:
                return new MapLocation(m.x, G.mapHeight - m.y - 1);
            case 1:
                return new MapLocation(G.mapWidth - m.x - 1, m.y);
            case 2:
                return new MapLocation(G.mapWidth - m.x - 1, G.mapHeight - m.y - 1);
            default:
                throw new Exception("invalid symmetry argument");
        }
    }
    
    public static int intifyCheeseMessageMine(MapLocation loc) throws Exception {
        return (((loc.y / 5) * 12) + (loc.x / 5));
    }
    public static MapLocation parseCheeseMessageMine(int n) throws Exception {
        return new MapLocation((n % 12) * 5 + 2, (n / 12) * 5 + 2);
    }
    public static int cheeseMessageSymmetry(int sym) throws Exception {
        return 144 + sym;
    }

    public static int[] cheeseMessage;
    public static int cheeseMessageTurn = -1;
    public static MapLocation cheeseMessageTarget;
    public static int cheeseMessageTargetID;

    public static final int cheeseMessageTurns = 3;
    public static final int cheeseMessageCheese = 15 + 1;

    public static final int[] cheeseMessageFirst = new int[]{
        364,
        286,
        220,
        165,
        120,
        84,
        56,
        35,
        20,
        10,
        4,
        1,
        0,
    };
    public static final int[] cheeseMessageSecond = new int[]{
        78,
        66,
        55,
        45,
        36,
        28,
        21,
        15,
        10,
        6,
        3,
        1,
        0,
    };

    public static void initCheeseMessage(int message, RobotInfo targetBot) throws Exception {
        if (G.rc.getRawCheese() < cheeseMessageCheese) {
            throw new Exception("not enough cheese for cheese message");
        }
        cheeseMessageTurn = 0;
        int[] cheeseMessageTemp = new int[3];
        for (int i = 0; i < cheeseMessageFirst.length; i++) {
            if (cheeseMessageFirst[i] <= message) {
                cheeseMessageTemp[0] = i + 1;
                message -= cheeseMessageFirst[i];
                break;
            }
        }
        for (int i = 0; i < cheeseMessageSecond.length; i++) {
            if (cheeseMessageSecond[i] <= message) {
                cheeseMessageTemp[1] = i + 2;
                message -= cheeseMessageSecond[i];
                break;
            }
        }
        cheeseMessageTemp[2] = cheeseMessageCheese - message;
        cheeseMessage = new int[3];
        cheeseMessage[0] = cheeseMessageTemp[0];
        cheeseMessage[1] = cheeseMessageTemp[1] - cheeseMessageTemp[0];
        cheeseMessage[2] = cheeseMessageTemp[2] - cheeseMessageTemp[1];

        cheeseMessageTarget = targetBot.getLocation();
        cheeseMessageTargetID = targetBot.getID();
    }
    public static boolean attemptSendCheeseMessage(RobotInfo targetBot) throws Exception {
        // if (G.rc.canTransferCheese(G.rc.adjacentLocation(G.me.directionTo(targetBot.getLocation())), cheeseMessage[cheeseMessageTurn])) {
        //     G.rc.transferCheese(G.rc.adjacentLocation(G.me.directionTo(targetBot.getLocation())), cheeseMessage[cheeseMessageTurn]);
        //     cheeseMessageTurn += 1;
        //     return true;
        // }
        if (G.rc.canTransferCheese(targetBot.getLocation(), cheeseMessage[cheeseMessageTurn])) {
            G.rc.transferCheese(targetBot.getLocation(), cheeseMessage[cheeseMessageTurn]);
            cheeseMessageTurn += 1;
            return true;
        }
        return false;
    }

    public static boolean enterCheeseMessageCheckMode() throws Exception {
        if (numberOfCriticalInformation > 0 && G.rc.getRawCheese() >= cheeseMessageCheese) {
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (G.allyRobots[i].getType() == UnitType.RAT_KING) {
                    int message = -1;
                    search: {
                        for (int j = numberOfMines; --j >= 0;) {
                            if (mineCritical[j]) {
                                message = intifyCheeseMessageMine(mineLocs[j]);
                                mineCritical[j] = false;
                                numberOfCriticalInformation--;
                                break search;
                            }
                        }
                        if (criticalSymmetry[0] || criticalSymmetry[1] || criticalSymmetry[2]) {
                            message = cheeseMessageSymmetry(intifySymmetry(symmetry));
                            if (criticalSymmetry[0]) {
                                criticalSymmetry[0] = false;
                                numberOfCriticalInformation--;
                            }
                            if (criticalSymmetry[1]) {
                                criticalSymmetry[1] = false;
                                numberOfCriticalInformation--;
                            }
                            if (criticalSymmetry[2]) {
                                criticalSymmetry[2] = false;
                                numberOfCriticalInformation--;
                            }
                        }
                    }
                    // G.indicatorString.append("SEND MESSAGE " + message + " ");
                    initCheeseMessage(message, G.allyRobots[i]);
                    return true;
                }
            }
        }
        return false;
    }
    public static boolean cheeseMessageCheckMode() throws Exception {
        if (cheeseMessageTurn == cheeseMessageTurns) {
            return false;
        }
        return true;
    }
    public static void cheeseMessage() throws Exception {
        RobotInfo targetBot = null;
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].getID() == cheeseMessageTargetID) {
                targetBot = G.allyRobots[i];
                break;
            }
        }
        if (targetBot != null) {
            if (attemptSendCheeseMessage(targetBot)) {
                return;
            }
            if (!Motion.attemptTurnToRatKing(targetBot.getLocation())) {
                Motion.bugnavTowards(targetBot.getLocation());
            }
            attemptSendCheeseMessage(targetBot);
        }
        else {
            Motion.bugnavTowards(cheeseMessageTarget);
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (G.allyRobots[i].getID() == cheeseMessageTargetID) {
                    targetBot = G.allyRobots[i];
                    break;
                }
            }
            if (targetBot != null) {
                if (attemptSendCheeseMessage(targetBot)) {
                    return;
                }
            }
        }
    }

    public static void sendSqueakMessages() throws Exception {
        // for (int i = 0; i < numberOfCats; i++) {
        //     // if (G.round - catRounds[i] < 50 && G.me.distanceSquaredTo(catLocations[i]) <= 80 * 4) {
        //         return;
        //     // }
        // }
        for (int i = numberOfCats; --i >= 0;) {
            // if (G.round - catRounds[i] < 50 && G.me.distanceSquaredTo(catLocations[i]) <= 80 * 4) {
            if (G.me.distanceSquaredTo(catLocations[i]) <= 80) {
                return;
            }
        }
        // G.indicatorString.append("NOCI=" + numberOfCriticalInformation);
        if (numberOfCriticalInformation > 0) {
            for (int i = G.allyRobots.length; --i >= 0;) {
                if (G.allyRobots[i].getType() == UnitType.RAT_KING) {
                    if (G.allyRobots[i].location.distanceSquaredTo(G.me) > 16) {
                        continue;
                    }
                    int message = -1;
                    search: {
                        for (int j = numberOfMines; --j >= 0;) {
                            if (mineCritical[j]) {
                                message = intifyCheeseMessageMine(mineLocs[j]);
                                mineCritical[j] = false;
                                numberOfCriticalInformation--;
                                break search;
                            }
                        }
                        for (int j = numberOfCats; --j >= 0;) {
                            if (catCritical[j]) {
                                message = 144 + 8 + intifyLocation(catLocations[j]) + (catRounds[j] << 10) + (catIDs[j] << 20);
                                catCritical[j] = false;
                                numberOfCriticalInformation--;
                                break search;
                            }
                        }
                        if (criticalSymmetry[0] || criticalSymmetry[1] || criticalSymmetry[2]) {
                            message = cheeseMessageSymmetry(intifySymmetry(symmetry));
                            if (criticalSymmetry[0]) {
                                criticalSymmetry[0] = false;
                                numberOfCriticalInformation--;
                            }
                            if (criticalSymmetry[1]) {
                                criticalSymmetry[1] = false;
                                numberOfCriticalInformation--;
                            }
                            if (criticalSymmetry[2]) {
                                criticalSymmetry[2] = false;
                                numberOfCriticalInformation--;
                            }
                        }
                    }
                    if (message != -1) {
                        message |= (3 << ENEMY_TYPE);
                        // G.indicatorString.append("SEND MESSAGE " + message + " ");
                        G.rc.squeak(message);
                        return;
                    }
                }
            }
        }
        for (int i = 5; --i >= 0;) {
            if (existsRatKing[i] && G.me.distanceSquaredTo(ratKingLocations[i]) < 80) {
                return;
            }
        }
        // if (G.me.distanceSquaredTo(ratKingLocations[0]) < 80) {
        //     return;
        // }
        RobotInfo bestOpponentRobot = null;
        for (int i = G.robots.length; --i >= 0;) {
            if ((G.robots[i].team == G.opponentTeam || G.robots[i].team == Team.NEUTRAL) && (bestOpponentRobot == null || G.me.distanceSquaredTo(G.robots[i].getLocation()) < G.me.distanceSquaredTo(bestOpponentRobot.getLocation()))) {
				if (G.robots[i].team == G.opponentTeam && squeakedEnemies.exists(G.robots[i].getLocation())) {
					RobotInfo ri = squeakedEnemies.get(G.robots[i].getLocation());
					if (ri.health == G.robots[i].health && ri.getID() == G.robots[i].getID() % 256 && ri.direction == G.robots[i].direction) {
						continue;
					}
				}
                bestOpponentRobot = G.robots[i];
            }
        }
        if (bestOpponentRobot != null) {
            int message = intifyLocationRelative(bestOpponentRobot.getLocation(), G.me) << ENEMY_LOCATION;
            message += (bestOpponentRobot.getID() % 256) << ENEMY_ID;
            message += G.dirOrd(bestOpponentRobot.getDirection()) << ENEMY_DIR;
            message += encodeNumber(bestOpponentRobot.getHealth(), bestOpponentRobot.getType().health, 64) << ENEMY_HP;
            message += encodeUnitType(bestOpponentRobot.getType()) << ENEMY_TYPE;
            message += BabyRat.mode << SELF_STATE;
            message += G.dirOrd(Motion.lastDir) << TRAVELLING_DIR;
            G.rc.squeak(message);
        }
    }
    public static StringBuilder readSqueaks = new StringBuilder();
    public static int numberOfAlliesNearby = 0;
    public static StringBuilder allyState = new StringBuilder();
    public static StringBuilder allyTravellingDir = new StringBuilder();
	public static RobotInfoMap squeakedEnemies = new RobotInfoMap(16);
    public static void readSqueakMessages() throws Exception {
        allyTravellingDir = new StringBuilder();
        StringBuilder sb = new StringBuilder();
		squeakedEnemies.clear();
        Message[] squeaks = G.rc.readSqueaks(G.round);
        Message[] lastSqueaks = G.rc.readSqueaks(G.round - 1);
        numberOfAlliesNearby = 0;
        Message[] readingSqueaks = new Message[squeaks.length + lastSqueaks.length];
        int readingIndex = 0;
        for (int i = 0; i < squeaks.length; i++) {
            if (squeaks[i].getSenderID() == G.rc.getID()) {
                continue;
            }
            numberOfAlliesNearby += 1;
            sb.append(squeaks[i].getSenderID() + " ");
            readingSqueaks[readingIndex] = squeaks[i];
            readingIndex++;
        }
        for (int i = 0; i < lastSqueaks.length; i++) {
            if (lastSqueaks[i].getSenderID() == G.rc.getID()) {
                continue;
            }
            if (sb.indexOf(lastSqueaks[i].getSenderID() + " ") != -1) {
                continue;
            }
            if (readSqueaks.indexOf(lastSqueaks[i].getSenderID() + " ") != -1) {
                continue;
            }
            numberOfAlliesNearby += 1;
            readingSqueaks[readingIndex] = lastSqueaks[i];
            readingIndex++;
        }
        for (int i = 0; i < readingIndex; i++) {
            readSqueakMessage(readingSqueaks[i]);
        }
        readSqueaks = sb;
    }
    public static void readSqueakMessage(Message message) throws Exception {
        int number = message.getBytes();
        // G.indicatorString.append("RECEIVE MESSAGE " + number + " ");
        int enemyType = (number >> ENEMY_TYPE) & 0b11;
		if (enemyType == 3) {
			number -= (3 << ENEMY_TYPE);
            if (number < 144) {
                addMine(0, parseCheeseMessageMine(number));
            }
            else if (number < 144 + 8) {
                boolean[] newSymmetry = parseSymmetry(number - 144);
                for (int j = 3; --j >= 0;) {
                    if (symmetry[j] && !newSymmetry[j]) {
                        removeValidSymmetry(1, j);
                    }
                }
            }
            else {
                if (((number >> 10) & (0b1111111111)) * 2 == 2046) {
                    return;
                }
                number -= 144 + 8;
                MapLocation loc = parseLocation(number & (0b1111111111));
                int round = ((number >> 10) & (0b1111111111)) * 2;
                int id = ((number >> 20) & 0b1111111111);
                addCat(1, id, round, loc);
            }
		} else {
			MapLocation allyLoc = message.getSource();
            int state = ((number >> SELF_STATE) & 0b111);
            G.allyRobotString.append(allyLoc.toString());
            if ((number >> ENEMY_HP & 0b1111111) != 127) {
                MapLocation enemyLoc = parseLocationRelative(number >> ENEMY_LOCATION & 0b1111111, allyLoc);
                int enemyId = number >> ENEMY_ID & 0b11111111;
                int enemyDir = number >> ENEMY_DIR & 0b111;
                UnitType enemyUnitType = decodeUnitType(enemyType);
                int enemyHp = decodeNumber(number >> ENEMY_HP & 0b111111, enemyUnitType.health, 64);
                // System.out.println(enemyLoc+" "+enemyId+" "+enemyDir+" "+enemyUnitType+" "+enemyHp);
                if (enemyUnitType == UnitType.CAT) {
                    if (G.lastSeenCatRound < message.getRound()) {
                        G.lastSeenCatRound = message.getRound();
                        G.lastSeenCatLocation = enemyLoc;
                    }
                } else {
					RobotInfo ri = new RobotInfo(enemyId, G.opponentTeam, enemyUnitType, enemyHp, enemyLoc, G.ALL_DIRECTIONS[enemyDir], 0, 0, null);
                    G.opponentRobots.set(ri);
					squeakedEnemies.set(ri);
                }
				if (G.type == UnitType.BABY_RAT) {
					if (state == BabyRat.ATTACK && BabyRat.mode != BabyRat.RETREAT && G.me.distanceSquaredTo(enemyLoc) <= 40) {
						BabyRat.lastMode = BabyRat.mode;
						BabyRat.mode = BabyRat.ATTACK;
						BabyRat.attackLocation = enemyLoc;
						BabyRat.attackLocationRatKing = enemyType == 1;
					}
				}
            }
            
            // allyState.append(message.getSenderID() + "-" + state + " ");
		}
    }

    public static HashMap<Integer, Integer> lastCheese = new HashMap<Integer, Integer>();
    public static HashMap<Integer, Integer> cheeseMessageReceiveTurns = new HashMap<Integer, Integer>();
    public static HashMap<Integer, Integer> cheeseMessageReceiveCheese = new HashMap<Integer, Integer>();
    public static HashMap<Integer, Integer> cheeseMessageReceiveMessage = new HashMap<Integer, Integer>();
    public static HashMap<Integer, Integer> cheeseMessageReceiveLastTransfer = new HashMap<Integer, Integer>();

    public static void readCheeseMessages() throws Exception {
        for (int i = G.allyRobots.length; --i >= 0;) {
            int id = G.allyRobots[i].ID;
            if (lastCheese.containsKey(id) && lastCheese.get(id) != G.allyRobots[i].cheeseAmount) {
                int diff = lastCheese.get(id) - G.allyRobots[i].cheeseAmount;
                if (diff <= 0) {
                    continue;
                }
                if (G.allyRobots[i].cheeseAmount == 0) {
                    continue;
                }
                // G.indicatorString.append("D=" + diff + ":" + G.allyRobots[i].getID() + " ");
                if (cheeseMessageReceiveLastTransfer.containsKey(id) && cheeseMessageReceiveLastTransfer.get(id) - G.round > 5) {
                    cheeseMessageReceiveTurns.remove(id);
                    cheeseMessageReceiveCheese.remove(id);
                    cheeseMessageReceiveMessage.remove(id);
                    cheeseMessageReceiveLastTransfer.remove(id);
                }
                if (cheeseMessageReceiveTurns.containsKey(id)) {
                    int turn = cheeseMessageReceiveTurns.get(id);
                    turn += 1;
                    if (turn == 1) {
                        int cheese = diff + cheeseMessageReceiveCheese.get(id);
                        cheeseMessageReceiveTurns.put(id, turn);
                        cheeseMessageReceiveCheese.put(id, cheese);
                        cheeseMessageReceiveMessage.put(id, cheeseMessageReceiveMessage.get(id) + cheeseMessageSecond[cheese - 2]);
                        // cheeseMessageReceiveLastTransfer.put(id, G.round);
                    }
                    else if (turn == 2) {
                        int cheese = diff + cheeseMessageReceiveCheese.get(id);
                        int message = cheeseMessageReceiveMessage.get(id) + cheeseMessageCheese - cheese;
                        // G.indicatorString.append("RECEIVE MESSAGE " + message + " ");
                        if (message < 144) {
                            addMine(0, parseCheeseMessageMine(message));
                        }
                        else {
                            boolean[] newSymmetry = parseSymmetry(message - 144);
                            for (int j = 3; --j >= 0;) {
                                if (symmetry[j] && !newSymmetry[j]) {
                                    removeValidSymmetry(1, j);
                                }
                            }
                        }
                        cheeseMessageReceiveTurns.remove(id);
                        cheeseMessageReceiveCheese.remove(id);
                        cheeseMessageReceiveMessage.remove(id);
                        cheeseMessageReceiveLastTransfer.remove(id);
                    }
                }
                // else if (G.allyRobots[i].cheeseAmount != 0) {
                else {
                    cheeseMessageReceiveTurns.put(id, 0);
                    cheeseMessageReceiveCheese.put(id, diff);
                    cheeseMessageReceiveMessage.put(id, cheeseMessageFirst[diff - 1]);
                    cheeseMessageReceiveLastTransfer.put(id, G.round);
                }
            }
        }
        lastCheese.clear();
        for (int i = G.allyRobots.length; --i >= 0;) {
            if (G.allyRobots[i].cheeseAmount != 0) {
                lastCheese.put(G.allyRobots[i].ID, G.allyRobots[i].cheeseAmount);
            }
        }
    }

    public static void updateSymmetry() throws Exception {
        boolean[] newSymmetry = parseSymmetry(G.rc.readSharedArray(SYMMETRY));
        if (!newSymmetry[2]) {
            removeValidSymmetry(1, 2);
        }
        if (!newSymmetry[1]) {
            removeValidSymmetry(1, 1);
        }
        if (!newSymmetry[0]) {
            removeValidSymmetry(1, 0);
        }
        if (G.type == UnitType.RAT_KING) {
            G.rc.writeSharedArray(SYMMETRY, intifySymmetry(symmetry));
        }
    }

    public static void writeSelfLocation() throws Exception {
        G.rc.writeSharedArray(RAT_KING_CURR_ROUND + ratKingID, G.round / 2); // hopefully wont break
        G.rc.writeSharedArray(RAT_KING_LOC + ratKingID, intifyLocation(G.me));
        // if (G.round == 1) {
        //     G.rc.writeSharedArray(RAT_KING_INIT_LOC + ratKingID, intifyLocation(G.me));
        // }
    }
    public static void readRatKingLocations() throws Exception {
        numberOfRatKings = 0;
        for (int i = 0; i < 5; i++) {
            existsRatKing[i] = G.rc.readSharedArray(RAT_KING_CURR_ROUND + i) >= ((G.round - 3) / 2);
            if (existsRatKing[i]) {
                numberOfRatKings++;
                ratKingLocations[i] = parseLocation(G.rc.readSharedArray(RAT_KING_LOC + i));
            }
            else {
                ratKingLocations[i] = null;
            }
        }
        // ratKingInitLocations[0] = parseLocation(G.rc.readSharedArray(RAT_KING_INIT_LOC));
    }

    public static void writeCheeseMineLocations() throws Exception {
        if (numberOfMines == 0) {
            G.rc.writeSharedArray(CHEESE_MINE_LOC + ratKingID, 1023);
            G.rc.writeSharedArray(CHEESE_MINE_CLOSEST_LOC + ratKingID, 1023);
            return;
        }
        G.rc.writeSharedArray(CHEESE_MINE_LOC + ratKingID, intifyCheeseMessageMine(mineLocs[G.round % numberOfMines]));

        MapLocation closest = mineLocs[0];
        for (int i = 1; i < numberOfMines; i++) {
            if (G.me.distanceSquaredTo(closest) > G.me.distanceSquaredTo(mineLocs[i])) {
                closest = mineLocs[i];
            }
        }
        G.rc.writeSharedArray(CHEESE_MINE_CLOSEST_LOC + ratKingID, intifyCheeseMessageMine(closest));
    }
    public static void readCheeseMineLocations() throws Exception {
        for (int i = CHEESE_MINE_LOC; i < CHEESE_MINE_LOC + 5; i++) {
            int message = G.rc.readSharedArray(i);
            if (message == 1023) {
                continue;
            }
            addMine(1, parseCheeseMessageMine(message));
        }
        for (int i = CHEESE_MINE_CLOSEST_LOC; i < CHEESE_MINE_CLOSEST_LOC + 5; i++) {
            int message = G.rc.readSharedArray(i);
            if (message == 1023) {
                continue;
            }
            addMine(1, parseCheeseMessageMine(message));
        }
    }

    public static int[][] newRatKingLocations = new int[][]{
        {-2, -3},
        {-1, -3},
        {0, -3},
        {1, -3},
        {2, -3},
        {-2, 3},
        {-1, 3},
        {0, 3},
        {1, 3},
        {2, 3},
        {-3, -2},
        {-3, -1},
        {-3, 0},
        {-3, 1},
        {-3, 2},
        {3, -2},
        {3, -1},
        {3, 0},
        {3, 1},
        {3, 2},
    };
    public static void writeFormRatKingLocations() throws Exception {
        // if (numberOfMines > numberOfRatKings * 2 && G.rc.getGlobalCheese() > 500 && G.rc.getCurrentRatCost() >= 70 - numberOfMines * 2) {
        int distance = -1;
        for (int i = 0; i < numberOfMines; i++) {
            if (distance == -1 || distance < mineLocs[i].distanceSquaredTo(G.me)) {
                distance = mineLocs[i].distanceSquaredTo(G.me);
            }
        }
        // G.indicatorString.append("REQ_CHE=" + RatKing.cheeseThreshold + " ");
        // if (numberOfMines > numberOfRatKings * 3 && G.rc.getGlobalCheese() > 1000 && Comms.minNumberOfBabyRats >= 12 && G.round > 100) {
        if (numberOfMines >= numberOfRatKings * 2 && G.rc.getGlobalCheese() > RatKing.cheeseThreshold - 100 && Comms.minNumberOfBabyRats >= 24 && ((RatKing.atCheeseMine && distance > 900 && numberOfMines > numberOfRatKings * 2 + 3) || G.round > 600)) {
            MapLocation bestLoc = null;
            int bestWeight = 0;
            for (int i = 0; i < numberOfMines; i++) {
                int weight = 0;
                for (int j = 0; j < numberOfMines; j++) {
                    weight -= mineLocs[i].distanceSquaredTo(mineLocs[j]);
                }
                weight += mineLocs[i].distanceSquaredTo(G.me) * 2;
                if (bestLoc == null || weight > bestWeight) {
                    bestLoc = mineLocs[i];
                    bestWeight = weight;
                }
            }

            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2, intifyLocation(bestLoc));
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2 + 1, 0);
        }
        else
        // if ((numberOfRatKings <= 2 && G.round > 1100 && G.round < 1200) || (numberOfRatKings == 1 && G.round > 1900) || (numberOfRatKings == 1 && G.rc.getHealth() < 100)) {
        if (numberOfRatKings == 1 && (G.round > 1900 || G.rc.getHealth() < 100)) {
            MapLocation bestLoc = null;
            int bestWeight = 0;
            search: for (int i = 0; i < newRatKingLocations.length; i++) {
                int weight = 0;
                for (int j = 0; j < 9; j++) {
                    MapLocation loc = G.me.translate(newRatKingLocations[i][0], newRatKingLocations[i][1]).add(G.ALL_DIRECTIONS[j]);
                    if (!G.rc.onTheMap(loc)) {
                        continue search;
                    }
                    MapInfo info = G.rc.senseMapInfo(loc);
                    if (info.isWall()) {
                        continue search;
                    }
                    if (info.isDirt()) {
                        weight -= 1;
                    }
                    if (G.rc.canSenseRobotAtLocation(loc)) {
                        if (G.rc.senseRobotAtLocation(loc).team == G.team) {
                            weight += 100;
                        }
                        else {
                            weight -= 100;
                        }
                    }
                }
                if (bestLoc == null || bestWeight < weight) {
                    bestLoc = G.me.translate(newRatKingLocations[i][0], newRatKingLocations[i][1]);
                    bestWeight = weight;
                }
            }
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2, intifyLocation(bestLoc));
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2 + 1, 0);
        }
        else {
            // G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2, 1023);
        }
    }
    public static void readFormRatKingLocations() throws Exception {
        for (int i = 0; i < 5; i++) {
            int message = G.rc.readSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + i * 2);
            if (message == 1023 || G.rc.readSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + i * 2 + 1) != 0) {
                formRatKingLocations[i] = null;
                continue;
            }
            formRatKingLocations[i] = parseLocation(message);
        }
    }

    public static void writeDefendRatKingLocations() throws Exception {
        if (G.lastSeenOpponentLocation != null && G.lastSeenOpponentRound >= G.round - 10) {
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2, intifyLocation(G.lastSeenOpponentLocation));
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2 + 1, 2 + G.opponentRobots.length);
        }
        else if (G.lastSeenCatLocation != null && G.lastSeenCatRound >= G.round - 10) {
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2, intifyLocation(G.lastSeenCatLocation));
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2 + 1, 1);
        }
        else {
            G.rc.writeSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + ratKingID * 2, 1023);
        }
    }
    public static void readDefendRatKingLocations() throws Exception {
        for (int i = 0; i < 5; i++) {
            int message = G.rc.readSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + i * 2);
            int indicator = G.rc.readSharedArray(FORM_OR_DEFEND_RAT_KING_LOC + i * 2 + 1);
            if (message == 1023 || indicator == 0) {
                defendRatKingLocations[i] = null;
                continue;
            }
            defendRatKingLocations[i] = parseLocation(message);
            defendRatKingFromCat[i] = indicator == 1;
            if (indicator == 1) {
                defendNumberOfOpponentRobots[i] = 0;
            }
            else {
                defendNumberOfOpponentRobots[i] = indicator = 2;
            }
        }
    }


    public static void writeCatLocations() throws Exception {
        if (numberOfCats == 0) {
            G.rc.writeSharedArray(CAT_LOC + ratKingID * 3, 1023);
            G.rc.writeSharedArray(CAT_CLOSEST_LOC + ratKingID * 3, 1023);
            return;
        }
        G.rc.writeSharedArray(CAT_LOC + ratKingID * 3, catIDs[G.round % numberOfCats]);
        G.rc.writeSharedArray(CAT_LOC + ratKingID * 3 + 1, catRounds[G.round % numberOfCats] / 2);
        G.rc.writeSharedArray(CAT_LOC + ratKingID * 3 + 2, intifyLocation(catLocations[G.round % numberOfCats]));

        int closestID = catIDs[0];
        int closestRound = catRounds[0];
        MapLocation closest = catLocations[0];
        for (int i = 1; i < numberOfCats; i++) {
            if (G.me.distanceSquaredTo(closest) > G.me.distanceSquaredTo(catLocations[i])) {
                closest = catLocations[i];
            }
        }
        G.rc.writeSharedArray(CAT_CLOSEST_LOC + ratKingID * 3, closestID);
        G.rc.writeSharedArray(CAT_CLOSEST_LOC + ratKingID * 3 + 1, closestRound / 2);
        G.rc.writeSharedArray(CAT_CLOSEST_LOC + ratKingID * 3 + 2, intifyLocation(closest));
    }
    public static void readCatLocations() throws Exception {
        for (int i = CAT_LOC; i < CAT_LOC + 15; i += 3) {
            int message = G.rc.readSharedArray(i + 2);
            if (message == 1023) {
                continue;
            }
            int id = G.rc.readSharedArray(i);
            int round = G.rc.readSharedArray(i + 1);
            addCat(1, id, round, parseLocation(message));
        }
        for (int i = CAT_CLOSEST_LOC; i < CAT_CLOSEST_LOC + 15; i += 3) {
            int message = G.rc.readSharedArray(i + 2);
            if (message == 1023) {
                continue;
            }
            int id = G.rc.readSharedArray(i);
            int round = G.rc.readSharedArray(i + 1);
            addCat(1, id, round, parseLocation(message));
        }
    }


    public static MapLocation parseLocation(int n) {
        return new MapLocation((n & 0b11111) * 2, ((n >> 5) & 0b11111) * 2);
    }
    public static int intifyLocation(MapLocation loc) {
        return (((loc.y / 2) << 5) | (loc.x / 2));
    }
    public static MapLocation parseLocationAccurate(int n) {
        return new MapLocation(n & 0b111111, (n >> 6) & 0b111111);
    }
    public static int intifyLocationAccurate(MapLocation loc) {
        return ((loc.y << 6) | loc.x);
    }
    public static int intifyLocationRelative(MapLocation loc, MapLocation meLoc) {
        int dx = loc.x - meLoc.x;
        int dy = loc.y - meLoc.y;
        return (dx + 5) * 11 + dy + 5;
    }
    public static MapLocation parseLocationRelative(int n, MapLocation meLoc) {
        return meLoc.translate(n / 11 - 5, n % 11 - 5);
    }
    //encode a number n in range [0, a] into a number in range [0, b], losing precision
    public static int encodeNumber(int n, int a, int b) {
        return (int)((double)n * b / a);
    }
    public static int decodeNumber(int n, int a, int b) {
        return (int)((double)(n+0.5) * a / b);
    }
    public static int encodeUnitType(UnitType i) {
        switch (i) {
            case BABY_RAT:
                return 0;
            case RAT_KING:
                return 1;
            case CAT:
                return 2;
            default:
                return -1;
        }
    }
    public static UnitType decodeUnitType(int i) {
        switch (i) {
            case 0:
                return UnitType.BABY_RAT;
            case 1:
                return UnitType.RAT_KING;
            case 2:
                return UnitType.CAT;
            default:
                return UnitType.CAT;
        }
    }

    public static boolean[] parseSymmetry(int n) {
        return new boolean[]{(n & 0b1) != 0, (n & 0b10) != 0, (n & 0b100) != 0};
    }
    public static int intifySymmetry(boolean[] sym) {
        return (sym[2] ? 4 : 0) + (sym[1] ? 2 : 0) + (sym[0] ? 1 : 0);
    }

    public static void drawIndicators() {
        if (ENABLE_INDICATORS) {
            for (int i = numberOfMines; --i >= 0;) {
                // if (mines[i] == -1) {
                // break;
                // }
                // System.out.println(parseLocation(mines[i]));
                // G.indicatorString.append(i + " ");
                try {
                    // if (parsemineTeam(mines[i]) == G.team) {
                    G.rc.setIndicatorLine(G.me, mineLocs[i], 125, 125, 0);
                    MapLocation loc = mineLocs[i];
                    for (int j = 8; --j >= 0;) {
                        G.rc.setIndicatorLine(loc, loc.add(G.DIRECTIONS[j]), 255, 255, 0);
                    }
                } catch (Exception e) {
                }
            }
            for (int i = numberOfCats; --i >= 0;) {
                // if (mines[i] == -1) {
                // break;
                // }
                // System.out.println(parseLocation(mines[i]));
                // G.indicatorString.append(i + " ");
                try {
                    // if (parsemineTeam(mines[i]) == G.team) {
                    G.rc.setIndicatorLine(G.me, catLocations[i], 255, 125, 125);
                } catch (Exception e) {
                }
            }
            for (int i = G.opponentRobots.length; --i >= 0;) {
                try {
                    G.rc.setIndicatorLine(G.me, G.opponentRobots.infos[i].getLocation(), 128, 0, 0);
                } catch (Exception e) {

                }
            }
        }
    }
}