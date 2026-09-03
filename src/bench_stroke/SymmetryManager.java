package bench_stroke;

import bench_stroke.DataStructures.FastIterableLocMap;
import battlecode.common.*;
import static bench_stroke.RobotPlayer.*;

/**
 * Class used to compute / keep track of the symmetry. Only used for the first soldiers spawned from a paint tower.
 */

public class SymmetryManager {
    public static int SYMMETRY_INDEX = 48;

    static final int H = 1, V = 2, R = 4;
    static final int HC = (~H)&7, VC = (~V)&7, RC = (~R)&7;

    static boolean foundH = false, foundR = false, foundV = false;

    public static MapLocation base = null;
    static MapLocation bR, bH, bV;

    public static FastIterableLocMap seenMines = new FastIterableLocMap(45);

    static MapLocation target;

    static int discardedSyms = 0;

    static MapLocation L;
    static int symX, symY;

    static long w, r;

    static long[] mapWalls = new long[60];
    static long[] mapMines = new long[60];
    static long[] mapVision = new long[60];

    static void discardH(){
        discardedSyms |= H;
    }

    static void discardV(){
        discardedSyms |= V;
    }

    static void discardR(){
        discardedSyms |= R;
    }

    public static void setSym(int sym) {
        switch (sym) {
            case H -> {
                discardR(); discardV();
            }
            case V -> {
                discardR(); discardH();
            }
            case R -> {
                discardH(); discardV();
            }
            default -> {
                return;
            }
        }
    }

    public static int getSym() throws GameActionException {
        if(rc.readSharedArray(SYMMETRY_INDEX) != 0) {
            return rc.readSharedArray(SYMMETRY_INDEX);
        }
        return switch(discardedSyms){
            case HC -> H;
            case VC -> V;
            case RC -> R;
            default -> 0;
        };
    }

    static void checkSym() throws GameActionException {
        if (base == null) return;
        if (Clock.getBytecodesLeft() < 400) return;
        if (getSym() != 0) return;
        MapInfo[] infos = rc.senseNearbyMapInfos();
        for (MapInfo m : infos){
            if (Clock.getBytecodesLeft() < 400) return;
            L = m.getMapLocation();
            mapVision[L.x] |= (1L << L.y);
            w = m.isWall() ? 1L : 0L;
            r = m.hasCheeseMine() ? 1L : 0L;
            mapWalls[L.x] |= (w << L.y);
            mapMines[L.x] |= (r << L.y);
            symX = MAP_WIDTH - L.x - 1;
            symY = MAP_HEIGHT - L.y - 1;
            if (((mapVision[symX] & (1L << L.y)) != 0) && (((mapWalls[symX] >>> L.y) & 1) != w || ((mapMines[symX] >>> L.y) & 1) != r)) discardH();
            if (((mapVision[L.x] & (1L << symY)) != 0) && (((mapWalls[L.x] >>> symY) & 1) != w || ((mapMines[L.x] >>> symY) & 1) != r)) discardV();
            if (((mapVision[symX] & (1L << symY)) != 0) && (((mapWalls[symX] >>> symY) & 1) != w || ((mapMines[symX] >>> symY) & 1) != r)) discardR();
        }
    }

    public static MapLocation getSymmetric(MapLocation loc) throws GameActionException {
        return switch(getSym()){
            case H-> new MapLocation(MAP_WIDTH - loc.x - 1, loc.y);
            case V-> new MapLocation(loc.x, MAP_HEIGHT - loc.y - 1);
            case R-> new MapLocation(MAP_WIDTH - loc.x - 1, MAP_HEIGHT - loc.y - 1);
            default -> null;
        };
    }

    static void setBase(MapLocation loc){
        base = loc;
        bR =  new MapLocation(MAP_WIDTH - base.x - 1, MAP_HEIGHT - base.y - 1);
        bV = new MapLocation(base.x, MAP_HEIGHT - base.y - 1);
        bH = new MapLocation(MAP_WIDTH - base.x - 1, base.y);
    }

    public static MapLocation getTarget(){
        if (base == null) return null;
        target = null;
        MapLocation myLoc = rc.getLocation();
        if ((discardedSyms & R) == 0 && !foundR){
            if (myLoc.distanceSquaredTo(bR) < 10) foundR = true;
            else return bR;
        }
        if ((discardedSyms & V) == 0 && !foundV){
            if (myLoc.distanceSquaredTo(bV) < 10) foundV = true;
            else return bV;
        }
        if ((discardedSyms & H) == 0 && !foundH){
            if (myLoc.distanceSquaredTo(bH) < 10) foundH = true;
            else return bH;
        }
        // System.out.println(foundH);
        // System.out.println(foundV);
        // System.out.println(foundR);
        return null;
    }

}