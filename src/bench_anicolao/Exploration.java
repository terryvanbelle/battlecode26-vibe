package bench_anicolao;

import battlecode.common.*;
import java.util.ArrayList;

public class Exploration {
    static ArrayList<MapLocation> potentialEnemyStarts = new ArrayList<>();
    static boolean initialized = false;
    static MapLocation currentExploreTarget = null;
    
    public static void init(RobotController rc) {
        if (initialized) return;
        
        MapLocation myLoc = rc.getLocation();
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();
        
        // 1. Horizontal Symmetry: (width - x, y)
        potentialEnemyStarts.add(new MapLocation(width - myLoc.x - 1, myLoc.y));
        
        // 2. Vertical Symmetry: (x, height - y)
        potentialEnemyStarts.add(new MapLocation(myLoc.x, height - myLoc.y - 1));
        
        // 3. Rotational Symmetry: (width - x, height - y)
        potentialEnemyStarts.add(new MapLocation(width - myLoc.x - 1, height - myLoc.y - 1));

        initialized = true;
    }

    public static MapLocation getExploreTarget(RobotController rc) {
        if (!initialized) init(rc);

        // Check if we reached our current target
        if (currentExploreTarget != null && rc.getLocation().distanceSquaredTo(currentExploreTarget) < 9) {
            currentExploreTarget = null; // Reached it
        }

        if (currentExploreTarget == null) {
            // Pick a new one
            // First, try potential starts
            for (MapLocation loc : potentialEnemyStarts) {
                // Heuristic: Pick one? Or check if visited?
                // For now, simple: Pick random one.
                // Or better: Cycle through them using ID?
                 int index = (rc.getID() + Clock.getBytecodeNum()) % potentialEnemyStarts.size();
                 currentExploreTarget = potentialEnemyStarts.get(index);
                 // Remove it from list? No, multiple units might need to go there.
            }
            
            // If we are already near that target, or just want variation:
            // Add randomness.
            // Fallback: Random location on map
            if (currentExploreTarget == null || Math.random() < 0.3) {
                currentExploreTarget = new MapLocation(
                    (int)(Math.random() * rc.getMapWidth()),
                    (int)(Math.random() * rc.getMapHeight())
                );
            }
        }
        
        return currentExploreTarget;
    }
    public static MapLocation predictEnemyKing(RobotController rc, MapLocation refLoc) {
        int width = rc.getMapWidth();
        int height = rc.getMapHeight();

        // Reverting to Rotational Symmetry (Standard) to ensure massed army.
        // Parallel search diluted our forces on standard maps.
        return new MapLocation(width - refLoc.x - 1, height - refLoc.y - 1);
    }
}
