package bench_anicolao;

import battlecode.common.*;

public class BugPathing {
    
    // State
    static boolean isBugging = false;
    static Direction bugDir = null; // Current direction we are trying to move while bugging (wall follow)
    static boolean clockwise = true; // Wall follow direction
    static MapLocation lastWall = null; // Location of the wall we hit
    static MapLocation startBugLocation = null; // Where we started bugging
    static int buggingMinDist = 999999; // Closest we've gotten to target while bugging

    static MapLocation lastTarget = null;
    static MapLocation lastMyLoc = null;
    static int stuckTurns = 0;

    public static void move(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null) return;
        if (!rc.isMovementReady()) return;

        MapLocation myLoc = rc.getLocation();

        // Check Stuck
        if (lastMyLoc != null && lastMyLoc.equals(myLoc)) {
            stuckTurns++;
        } else {
            stuckTurns = 0;
        }
        lastMyLoc = myLoc;

        if (stuckTurns > 2) {
            // Jitter / Random to break free
            if (rc.canMove(Direction.NORTH)) rc.move(Direction.NORTH); // Naive jitter
            else {
                 // Try random
                for(int i=0; i<8; i++) {
                     Direction d = Direction.values()[ (int)(Math.random()*8) ];
                     if(rc.canMove(d)) { rc.move(d); break; }
                }
            }
            stopBugging();
            return;
        }

        // Reset if target changes
        if (lastTarget == null || !lastTarget.equals(target)) {
            stopBugging();
            lastTarget = target;
        }
        int distToTarget = myLoc.distanceSquaredTo(target);

        if (!isBugging) {
            // DIRECT MODE
            Direction dir = myLoc.directionTo(target);
            if (canMoveIdeally(rc, dir)) {
                rc.move(dir);
            } else {
                // Obstructed! Start Bugging
                startBugging(rc, target);
            }
        } else {
            // BUGGING MODE
            
            // 1. Check if we can stop bugging (closer to target than when we started AND clear path? 
            // Simplified: If closer to target than the point we started bugging (or min dist seen), try direct.
            if (distToTarget < buggingMinDist) {
                buggingMinDist = distToTarget;
                // Try moving direct
                Direction dir = myLoc.directionTo(target);
                if (canMoveIdeally(rc, dir)) {
                    stopBugging();
                    rc.move(dir);
                    return;
                }
            }

            // 2. Follow Wall
            // We want to move in 'bugDir'. 
            // If we can move bugsDir, great. If not, we rotate until we can.
            // Actually, standard wall follow:
            // "Right Hand Rule": Keep wall on right. 
            // If we can move forward-right, do it (corner).
            // Else if forward, do it.
            // Else if forward-left, do it.
            // ...
            
            Direction moveDir = findBugMoveDir(rc);
            if (moveDir != null) {
                rc.move(moveDir);
                // Update bugDir to be the moveDir (or slightly adjusted to hug wall?)
                // Actually, for simple bug, just keep rotating.
            } else {
                // Stuck?
            }
        }
    }

    private static void startBugging(RobotController rc, MapLocation target) {
        isBugging = true;
        buggingMinDist = rc.getLocation().distanceSquaredTo(target);
        startBugLocation = rc.getLocation();
        
        // Decide bug direction (cw or ccw) - naive: always CW
        clockwise = true;
        
        // Find initial bugDir: Rotate from target dir until we find obstacle, then follow it?
        // Simple Bug 0:
        // Dir to target is blocked.
        // turn Left (if CW wall on Left) or Right?
        // Let's say we keep wall on Left -> Turn Right until free.
        Direction toTarget = rc.getLocation().directionTo(target);
        bugDir = toTarget; // Start trying here
    }

    private static void stopBugging() {
        isBugging = false;
        buggingMinDist = 999999;
    }

    private static boolean canMoveIdeally(RobotController rc, Direction dir) {
        return rc.canMove(dir);
    }
    
    private static Direction findBugMoveDir(RobotController rc) {
        // Wall Follower Logic (Wall on Left)
        // 1. Raycast? No.
        // Current 'bugDir' is roughly parallel to wall or towards opening.
        
        // Simple logic:
        // Try 'bugDir'. If blocked, describe wall: rotate right (since wall on left).
        // If free, try to rotate left (hug wall tighter).
        
        // Better implementation:
        // "Scan" from current bearing.
        
        // Let's use simple storage-less rotation for now to ensure it works.
        // We want to move roughly towards target but blocked.
        // Rotate perpendicular to targetDir?
        
        // Let's try the iterate-until-valid approach matching 'bugDir'
        // If we moved last turn, 'bugDir' is our heading.
        // Scan starting from (bugDir + 2 (left)) down to (bugDir - ...)?
        
        Direction candidate = bugDir;
        
        // Verify if we can move in candidate?
        // If we can't move candidate, rotate Right (assuming CW / Wall on Left) until we can.
        // If we CAN move candidate, check if we can rotate Left (to hug wall).
        
        // Algorithm:
        // 1. Check if we hit a wall in our face (cannot move `bugDir`).
        //    If yes, rotate RIGHT until free. Update `bugDir`.
        // 2. If we are free, check if we lost the wall (can move LEFT-FRONT?).
        //    If yes, rotate LEFT and move there. Update `bugDir`.
        
        // Handling:
        // A. candidate = bugDir.
        
        // Check if blocked
        int checks = 0;
        while (!rc.canMove(candidate) && checks < 8) {
            candidate = candidate.rotateRight();
            checks++;
        }
        
        if (checks >= 8) {
            // Blocked all around
            return null;
        }
        
        // Now 'candidate' is valid. 
        // Logic check: Can we turn left? (Hug wall)
        // Check candidate.rotateLeft(). If that is valid, maybe we should have taken it?
        // Wait, if we keep turning Right when blocked, we hug wall on LEFT.
        // So we want to try turning Left if possible to wrap around corners.
        
        Direction left = candidate.rotateLeft();
        if (rc.canMove(left)) {
            // We just rounded a corner!
            candidate = left;
        }
        
        // Update state
        bugDir = candidate;
        return candidate;
    }
}
