package bench_stroke;

import static bench_stroke.BabyRat.*;
import bench_stroke.DataStructures.FastBitMap;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import static bench_stroke.RobotPlayer.rc;

public class Reachability
{
    public static FastBitMap reachable;
    public static FastBitMap map;

    public static void initializeReachability(int width, int height)
    {
        reachable = new FastBitMap(width, height);
        map = new FastBitMap(width, height);
    }

    public static boolean query(MapLocation loc) {
        int distX = Math.abs(loc.x - currentLocation.x);
        int distY = Math.abs(loc.y - currentLocation.y);
        if((distX <= 3 && distY <= 3) || distX >= 5 || distY >= 5)
            return reachable.get(loc.x, loc.y);
        else {
            Direction dirTo = loc.directionTo(currentLocation);
            return reachable.get(loc.add(dirTo))
                    || reachable.get(loc.add(dirTo.rotateLeft()))
                    || reachable.get(loc.add(dirTo.rotateRight()));
        }
//        return reachable.get(loc);
    }

    public static void drawIndicatorDots() throws GameActionException {
        for (MapLocation loc : rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), -1)) {
            boolean reachable = query(loc);
            rc.setIndicatorDot(loc, (reachable) ? 0 : 255, 255, 255);
        }
    }

    public static void updateReachability(MapLocation currentLocation) {
        reachable = new FastBitMap(reachable.width, reachable.height);
        long mask = map.get7x7BitMask(currentLocation.x, currentLocation.y);
        long reachable = bitBFS(mask);
        Reachability.reachable.setBitsFromLong(currentLocation.x, currentLocation.y, reachable);
    }

    public static long bitBFS(long map)
    {
        long frontier = 0x1000000L;
        long visited = frontier;

        long prev_frontier, cur_row;
        prev_frontier = frontier;

        cur_row = (prev_frontier & 0x1fc0000000000L) >> 42;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 35;
        }

        cur_row = (prev_frontier & 0x3f800000000L) >> 35;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 42;
            frontier |= cur_row << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 28;
        }

        cur_row = (prev_frontier & 0x7f0000000L) >> 28;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= cur_row << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 21;
        }

        cur_row = (prev_frontier & 0xfe00000L) >> 21;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 28;
            frontier |= cur_row << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 14;
        }

        cur_row = (prev_frontier & 0x1fc000L) >> 14;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 21;
            frontier |= cur_row << 7;
            frontier |= lateral << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 7;
        }

        cur_row = (prev_frontier & 0x3f80L) >> 7;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 14;
            frontier |= cur_row;
            frontier |= lateral << 7;
            frontier |= lateral << 14;
            frontier |= lateral;
        }

        cur_row = (prev_frontier & 0x7fL);

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 7;
            frontier |= lateral;
            frontier |= lateral << 7;
        }

        frontier &= map;
        visited |= frontier;
        prev_frontier = frontier;

        cur_row = (prev_frontier & 0x1fc0000000000L) >> 42;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 35;
        }

        cur_row = (prev_frontier & 0x3f800000000L) >> 35;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 42;
            frontier |= cur_row << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 28;
        }

        cur_row = (prev_frontier & 0x7f0000000L) >> 28;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= cur_row << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 21;
        }

        cur_row = (prev_frontier & 0xfe00000L) >> 21;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 28;
            frontier |= cur_row << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 14;
        }

        cur_row = (prev_frontier & 0x1fc000L) >> 14;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 21;
            frontier |= cur_row << 7;
            frontier |= lateral << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 7;
        }

        cur_row = (prev_frontier & 0x3f80L) >> 7;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 14;
            frontier |= cur_row;
            frontier |= lateral << 7;
            frontier |= lateral << 14;
            frontier |= lateral;
        }

        cur_row = (prev_frontier & 0x7fL);

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 7;
            frontier |= lateral;
            frontier |= lateral << 7;
        }

        frontier &= map;
        visited |= frontier;
        prev_frontier = frontier;

        cur_row = (prev_frontier & 0x1fc0000000000L) >> 42;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 35;
        }

        cur_row = (prev_frontier & 0x3f800000000L) >> 35;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 42;
            frontier |= cur_row << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 28;
        }

        cur_row = (prev_frontier & 0x7f0000000L) >> 28;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= cur_row << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 21;
        }

        cur_row = (prev_frontier & 0xfe00000L) >> 21;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 28;
            frontier |= cur_row << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 14;
        }

        cur_row = (prev_frontier & 0x1fc000L) >> 14;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 21;
            frontier |= cur_row << 7;
            frontier |= lateral << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 7;
        }

        cur_row = (prev_frontier & 0x3f80L) >> 7;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 14;
            frontier |= cur_row;
            frontier |= lateral << 7;
            frontier |= lateral << 14;
            frontier |= lateral;
        }

        cur_row = (prev_frontier & 0x7fL);

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 7;
            frontier |= lateral;
            frontier |= lateral << 7;
        }

        frontier &= map;
        visited |= frontier;
        prev_frontier = frontier;

        cur_row = (prev_frontier & 0x1fc0000000000L) >> 42;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 35;
        }

        cur_row = (prev_frontier & 0x3f800000000L) >> 35;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 42;
            frontier |= cur_row << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 42;
            frontier |= lateral << 28;
        }

        cur_row = (prev_frontier & 0x7f0000000L) >> 28;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 35;
            frontier |= cur_row << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 35;
            frontier |= lateral << 21;
        }

        cur_row = (prev_frontier & 0xfe00000L) >> 21;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 28;
            frontier |= cur_row << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 28;
            frontier |= lateral << 14;
        }

        cur_row = (prev_frontier & 0x1fc000L) >> 14;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 21;
            frontier |= cur_row << 7;
            frontier |= lateral << 14;
            frontier |= lateral << 21;
            frontier |= lateral << 7;
        }

        cur_row = (prev_frontier & 0x3f80L) >> 7;

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 14;
            frontier |= cur_row;
            frontier |= lateral << 7;
            frontier |= lateral << 14;
            frontier |= lateral;
        }

        cur_row = (prev_frontier & 0x7fL);

        if(!(cur_row == 0))
        {
            long lateral = ((cur_row << 1) | (cur_row >> 1)) & 0x7f;
            frontier |= cur_row << 7;
            frontier |= lateral;
            frontier |= lateral << 7;
        }

        frontier &= map;
        visited |= frontier;

        return visited;
    }
}
