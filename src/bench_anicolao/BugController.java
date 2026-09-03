package bench_anicolao;

import battlecode.common.*;

public class BugController {
    
    // State for Bug Pathfinding
    static boolean isBugging = false;
    static boolean clockwise = true;
    static Direction lastWallDirection = null;

    /**
     * Tries to move in the target direction using a simple Bug algorithm (Wall Following).
     * @param rc The RobotController
     * @param targetDir The desired direction to move
     * @throws GameActionException
     */
    public static void tryMove(RobotController rc, Direction targetDir) throws GameActionException {
        // Debug
        rc.setIndicatorString("Target: " + targetDir + " | Bugging: " + isBugging);
        System.out.println("Turn " + rc.getRoundNum() + ": Bugging=" + isBugging + " Target=" + targetDir);
        
        // 1. Try moving directly first
        if (!isBugging) {
            if (rc.canMove(targetDir)) {
                rc.move(targetDir);
            } else {
                // Hit a wall! Start bugging.
                isBugging = true;
                clockwise = true; 
                lastWallDirection = targetDir;
                // Move immediately in wall mode
                followWall(rc, targetDir);
            }
        } else {
            // We are in bugging mode
            // Stop if we can move to target
            if (rc.canMove(targetDir)) {
                isBugging = false;
                rc.move(targetDir);
            } else {
                followWall(rc, targetDir);
            }
        }
    }

    private static void followWall(RobotController rc, Direction targetHub) throws GameActionException {
        Direction testDir = (lastWallDirection != null) ? lastWallDirection : rc.getDirection();
        
        for (int i = 0; i < 8; i++) {
            if (clockwise) {
                testDir = testDir.rotateRight();
            } else {
                testDir = testDir.rotateLeft();
            }
            
            if (rc.canMove(testDir)) {
                rc.move(testDir);
                lastWallDirection = testDir.rotateLeft().rotateLeft(); 
                return;
            }
        }
    }
}
