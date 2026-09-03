package bench_stroke.Pathfinding;

import battlecode.common.*;

import static bench_stroke.BabyRat.*;
import static bench_stroke.RobotPlayer.*;
import bench_stroke.DataStructures.FastBitMap;
import bench_stroke.DataStructures.FastLocSet;
import bench_stroke.Utilities;
import bench_stroke.Communication.NearbyCatSqueakInfo;

public class Pathfinding {

    //calculates an approximate distance from the middle of the cat to the location
    public static double calculateCatDist(RobotInfo cat, MapLocation loc) {
        double catX = cat.location.x + 0.5;
        double catY = cat.location.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

    static int[] turnOrder = {1, 0, -1, 0}; //right is true
    static int turnCadence = 0;

    //Bug nav variables
    static MapLocation previousDestination = null;
    static boolean turningRight = true;
    static DirectionStack directionStack = new DirectionStack();
    static ObstacleMap obstacles;
    static int lastResetRound;

    static boolean exploring = false;

    public static MapLocation effectiveCatLoc;


    //BFS variables
    public static int[][] passable;
    static int closestDistanceOnPath = Integer.MAX_VALUE;
    static int RESET_THRESHOLD = 100;


    public static void initializeBugBFS(RobotController rc, MapLocation destination) {
        directionStack.clear();
        obstacles = new ObstacleMap(rc.getMapWidth(), rc.getMapHeight());
        previousDestination = destination;
        lastResetRound = rc.getRoundNum();
        closestDistanceOnPath = Integer.MAX_VALUE;
    }

    public static void attemptMoveExplore(MapLocation target, boolean avoidCats) throws GameActionException {
        exploring = true;
        attemptMove(target, avoidCats);
        exploring = false;
    }

    public static void attemptMove(MapLocation target, boolean avoidCats) throws GameActionException {
        if (rc.isMovementReady()) {
            boolean digDirt = (rc.getRawCheese() > 10 || rc.getAllCheese() > 1000) && rc.canTurn() && sawDirt;
            Direction dirToMove;
            if (nearestCat == null && mostRecentCat == null && catSqueak == null) avoidCats = false;
            if (turnCount == 1){
                initializeBugBFS(rc, target);
                dirToMove = bugNavClearDirt(target);
            }
            else {
                dirToMove = bugBFS(target, avoidCats, digDirt);
            }

            Direction dirToTurn = dirToMove;

            if(dirToMove == Direction.CENTER) {
                return;
            }

            if(digDirt) {
                if (dirToMove != rc.getDirection()) rc.turn(dirToMove);
                MapLocation toRemove = rc.getLocation().add(dirToMove);

                if(!rc.isActionReady() && rc.canSenseLocation(toRemove) && rc.senseMapInfo(toRemove).isDirt()) {
                    return;
                }

                if(rc.isActionReady()) {
                    if(rc.canRemoveDirt(toRemove)) {
                        rc.removeDirt(toRemove);
                    }
                }
            }

            Direction curDir = rc.getDirection();
            Direction right = dirToMove.rotateRight().rotateRight();
            Direction left = dirToMove.rotateLeft().rotateLeft();

            // if (avoidCats && rc.getRoundNum() <= 1200 && calculateCatDist(nearestCat, rc.getLocation().add(dirToMove)) < 2.9 && calculateCatDist(nearestCat, rc.getLocation()) > 2.9) {
            //     return;
            // }

            if (rc.canTurn() && (turnCadence == 0 || turnCadence == 2) && curDir != dirToMove) {
                rc.turn(dirToMove);
            }

            if (rc.canTurn() && turnsSinceEnemy <= 3) {
                rc.turn(dirToMove);
                turnCadence = 0;
            }

            if (avoidCats) {
                NearbyCatSqueakInfo squeakInfo = null;
                if (catSqueak != null) {
                    squeakInfo = (NearbyCatSqueakInfo) catSqueak.squeakInfo;
                }
                MapLocation catToAvoid = (nearestCat != null) ? nearestCat.location : (catSqueak != null) ? squeakInfo.location() : mostRecentCat.location;
                Direction catDir = (nearestCat != null) ? nearestCat.direction : (catSqueak != null) ? squeakInfo.direction() : mostRecentCat.direction;
                FastLocSet catPath;
                catPath = Utilities.catPath(catToAvoid, catDir);
                if (catPath.contains(rc.adjacentLocation(dirToMove))) {
                    return;
                }
            }

            if (rc.isMovementReady()) {
                if(rc.canTurn() && (exploring || rc.getLocation().distanceSquaredTo(target) > 16)) {
                    if(turnCadence == 0) {
                        dirToTurn = right;
                    } else if(turnCadence == 2){
                        dirToTurn = left;
                    }

                    if(turnCadence % 2 == 0) {
                        rc.move(dirToMove);
                        if (rc.isActionReady() && nearestCat != null) {
                            boolean catAttacked = Utilities.attemptAttackCat();
                        }
                        rc.turn(dirToTurn);
                    } else {
                        rc.turn(dirToTurn);
                        rc.move(dirToMove);
                    }
                    turnCadence = (turnCadence + 1) % 4;
                }
                else {
                    if(rc.canTurn()) {
                        rc.turn(dirToTurn);
                    }
                    if (rc.canMove(dirToMove)) rc.move(dirToMove);
                }
            }
            else if (rc.canTurn()) {
                rc.turn(dirToTurn);
            }
        }
    }

    public static Direction bugNav(MapLocation destination) throws GameActionException {
        // if (!destination.equals(previousDestination) || rc.getRoundNum() - lastResetRound >= RESET_THRESHOLD) {
        //     initializeBugBFS(rc, destination);
        // }

        if (!directionStack.isEmpty()) {
            Direction currentDirection = directionStack.peek();
            while (!rc.canMove(currentDirection)) {
                if (turningRight) {
                    currentDirection = currentDirection.rotateRight();
                }
                else {
                    currentDirection = currentDirection.rotateLeft();
                }

                /*
                    We don't want to follow a border all the way around so let's turn around and go the opposite way
                 */
                if (!rc.onTheMap(rc.getLocation().add(currentDirection))) {
                    turningRight = !turningRight;
                    directionStack.clear();
                    return bugNav(destination);
                }

                directionStack.push(currentDirection);
                if (directionStack.isFull()) {
                    directionStack.clear();
                    return Direction.CENTER;
                }
            }
            return directionStack.pop();
        }

        Direction dirTo = rc.getLocation().directionTo(destination);
        if (rc.canMove(dirTo)) {
            return dirTo;
        }
        else {
            turningRight = decideTurningDirection(destination);
            Direction currentDirection = dirTo;
            directionStack.push(currentDirection);
            while (!rc.canMove(currentDirection)) {
                if (turningRight) {
                    currentDirection = currentDirection.rotateRight();
                }
                else {
                    currentDirection = currentDirection.rotateLeft();
                }
                directionStack.push(currentDirection);
                if (directionStack.isFull()) {
                    directionStack.clear();
                    return Direction.CENTER;
                }
            }
            return directionStack.pop();
        }
    }

    public static Direction bugNavClearDirt(MapLocation destination) throws GameActionException {
        // if (!destination.equals(previousDestination) || rc.getRoundNum() - lastResetRound >= RESET_THRESHOLD) {
        //     initializeBugBFS(rc, destination);
        // }

        if (!directionStack.isEmpty()) {
            Direction currentDirection = directionStack.peek();
            MapLocation loc = currentLocation.add(currentDirection);
            while (!rc.canMove(currentDirection) && rc.onTheMap(loc) && passable[loc.x][loc.y] != 3) { //if we are in dig dirt mode then we can't move if rc.canMove fails and its not dirt
                /*
                    We don't want to follow a border all the way around so let's turn around and go the opposite way
                 */

                if (turningRight) {
                    currentDirection = currentDirection.rotateRight();
                }
                else {
                    currentDirection = currentDirection.rotateLeft();
                }

                if (!rc.onTheMap(rc.getLocation().add(currentDirection))) {
                    turningRight = !turningRight;
                    directionStack.clear();
                    return bugNavClearDirt(destination);
                }

                directionStack.push(currentDirection);
                if (directionStack.isFull()) {
                    directionStack.clear();
                    return Direction.CENTER;
                }
            }
            return directionStack.pop();
        }

        Direction dirTo = rc.getLocation().directionTo(destination);
        if (rc.canMove(dirTo)) {
            return dirTo;
        }
        else {
            turningRight = decideTurningDirection(destination);
            Direction currentDirection = dirTo;
            directionStack.push(currentDirection);

            MapLocation loc = currentLocation.add(currentDirection);
            while (!rc.canMove(currentDirection) && passable[loc.x][loc.y] != 3) {
                if (turningRight) {
                    currentDirection = currentDirection.rotateRight();
                }
                else {
                    currentDirection = currentDirection.rotateLeft();
                }
                directionStack.push(currentDirection);
                if (directionStack.isFull()) {
                    directionStack.clear();
                    return Direction.CENTER;
                }
            }
            return directionStack.pop();
        }
    }

    public static Direction bugBFS(MapLocation destination, boolean avoidCats, boolean digDirt) throws GameActionException {
        if (previousDestination == null || !previousDestination.equals(destination) || rc.getRoundNum() - lastResetRound >= RESET_THRESHOLD) {
            initializeBugBFS(rc, destination);
        }

        if (rc.getLocation().equals(destination)) {
            return Direction.CENTER;
        }

        Direction direction;
        if(rc.getType() == UnitType.RAT_KING) {
            direction = RatKingBFS.pathfind(destination);
        } else {
           // rc.setIndicatorString("bfs");
            direction = BabyRatBFS.pathfind(destination, avoidCats, digDirt);
        }

        if (direction != null) {
            return direction;
        }
        else {
         //   rc.setIndicatorString("bug nav");
            if(digDirt)
                return bugNavClearDirt(destination);
            else
                return bugNav(destination);
        }
    }

    public static boolean decideTurningDirection(MapLocation destination) throws GameActionException {
        MapLocation currentLocation = rc.getLocation();
        //if we see come to a new obstacle, decide the best turning direction and put in in the map
        if (obstacles.obstacleExists(currentLocation)) {
            boolean bestTurningDirection = bestTurnDirection(destination);
            obstacles.setTurnDirection(currentLocation, bestTurningDirection);
            return bestTurningDirection;
        }
        else {
            boolean previousDirection = obstacles.getTurnDirection(currentLocation);
            obstacles.setTurnDirection(currentLocation, !previousDirection);
            return !previousDirection;
        }

        //return bestTurnDirection(destination);
    }


    public static boolean bestTurnDirection(MapLocation destination) throws GameActionException {
        int finalDistanceRight = distanceAfterSimulation(destination, true);
        int finalDistanceLeft = distanceAfterSimulation(destination, false);

        return finalDistanceRight < finalDistanceLeft;
    }

    private static int distanceAfterSimulation(MapLocation destination, boolean turningRight) throws GameActionException {
        MapLocation virtualBug = rc.getLocation();
        DirectionStack virtualStack = new DirectionStack();

        int cutOff = 5;
        int i = 0;
        while (i < cutOff) {
            if (virtualBug.equals(destination) || !rc.canSenseLocation(virtualBug)) {
                break;
            }

            if (!virtualStack.isEmpty()) {
                Direction currentDirection = virtualStack.peek();
                while (!virtualCanMove(virtualBug, currentDirection)) {
                    if (turningRight)
                        currentDirection = currentDirection.rotateRight();
                    else
                        currentDirection = currentDirection.rotateLeft();
                    if (virtualStack.isFull()) {
                        virtualStack.clear();
                        virtualStack.push(Direction.CENTER);
                        break;
                    }
                    virtualStack.push(currentDirection);
                }
                virtualBug = virtualBug.add(virtualStack.pop());
                i++;
                continue;

            }

            Direction dirTo = virtualBug.directionTo(destination);
            if (virtualCanMove(virtualBug, dirTo)) {
                virtualBug = virtualBug.add(dirTo);
                i++;
                continue;

            }
            else {
                Direction currentDirection = dirTo;
                while (!virtualCanMove(virtualBug, currentDirection)) {
                    if (turningRight)
                        currentDirection = currentDirection.rotateRight();
                    else
                        currentDirection = currentDirection.rotateLeft();
                    virtualStack.push(currentDirection);
                    if (virtualStack.isFull()) {
                        virtualStack.clear();
                        virtualStack.push(Direction.CENTER);
                        break;
                    }
                }
            }
            virtualBug = virtualBug.add(virtualStack.pop());
            i++;
        }

        return virtualBug.distanceSquaredTo(destination);
    }


    static boolean virtualCanMove(MapLocation start, Direction direction) throws GameActionException {
        MapLocation location = start.add(direction);
        if (!rc.onTheMap(location)) {
            return false;
        }
        else {
            return passable[location.x][location.y] == 0 || passable[location.x][location.y] == 3;
        }
    }


}

class DirectionStack {
    private Direction[] directionStack;
    private int stackPointer = 0;

    public DirectionStack() {
        directionStack = new Direction[8];
    }

    public void push(Direction direction) {
        directionStack[stackPointer] = direction;
        stackPointer++;
    }

    public Direction pop() {
        stackPointer--;
        return directionStack[stackPointer];
    }

    public Direction peek() {
        return directionStack[stackPointer - 1];
    }

    public boolean isEmpty() {
        return stackPointer == 0;
    }

    public boolean isFull() {
        return stackPointer == 8;
    }

    public void clear() {
        stackPointer = 0;
    }

    public void displayDirections(RobotController rc) throws GameActionException {
        for (int i = 0; i < stackPointer; i++) {
            if (rc.onTheMap(rc.getLocation().add(directionStack[i]))) {
                rc.setIndicatorDot(rc.getLocation().add(directionStack[i]), 255, 255, 0);
            }
        }
    }
}

class ObstacleMap {
    enum TurnDirections {
        RIGHT,
        LEFT
    }

    private FastBitMap obstacles; //0 == no obstacle, 1 == obstacle
    private FastBitMap turnDirections; //0 == right, 1 == left

    public ObstacleMap(int width, int height) {
        //obstacles = new TurnDirections[width][height];
        obstacles = new FastBitMap(width, height);
        turnDirections = new FastBitMap(width, height);
    }

    public boolean obstacleExists(MapLocation location) {
        //return obstacles[location.x][location.y] == null;
        return !obstacles.get(location.x, location.y);
    }

    public boolean getTurnDirection(MapLocation location) {
        // return obstacles[location.x][location.y].equals(TurnDirections.RIGHT);
        return !turnDirections.get(location.x, location.y);
    }

    public void setTurnDirection(MapLocation location, boolean turningRight) {
        //true == left, false == right
        turnDirections.set(location.x, location.y, !turningRight);
        obstacles.set(location.x, location.y, true);
      //  obstacles[location.x][location.y] = turningRight ? TurnDirections.RIGHT : TurnDirections.LEFT;
    }
}
