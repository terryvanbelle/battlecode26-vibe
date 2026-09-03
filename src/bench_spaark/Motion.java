package bench_spaark;

import battlecode.common.*;

public class Motion {
    public static final boolean ENABLE_EXPLORE_INDICATORS = true;

    public static int movementCooldown = 0;
    public static int lastMove = -1;

    public static final int TOWARDS = 0;
    public static final int AWAY = 1;
    public static final int AROUND = 2;
    public static final int NONE = 0;
    public static final int CLOCKWISE = 1;
    public static final int COUNTER_CLOCKWISE = -1;

    public static Direction lastDir = Direction.CENTER;
    public static Direction optimalDir = Direction.CENTER;
    public static int rotation = NONE;
    public static int circleDirection = CLOCKWISE;

    public static Direction lastRandomDir = Direction.CENTER;
    public static MapLocation lastRandomSpread;

    // common distance stuff
    public static int getManhattanDistance(MapLocation a, MapLocation b) {
        return Math.abs(a.x - b.x) + Math.abs(a.y - b.y);
    }

    public static int getChebyshevDistance(MapLocation a, MapLocation b) {
        return Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y));
    }

    public static MapLocation getClosest(MapLocation[] a) throws Exception {
        return getClosest(a, G.me);
    }

    public static MapLocation getClosest(MapLocation[] a, MapLocation me) throws Exception {
        /* Get closest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) < distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    public static MapLocation getClosestPair(MapLocation[] a, MapLocation[] b) throws Exception {
        /* Get closest pair (Euclidean) */
        MapLocation closest = a[0];
        int distance = b[0].distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            for (int j = b.length; --j >= 0;) {
                MapLocation loc2 = b[i];
                if (loc2.distanceSquaredTo(loc) < distance) {
                    closest = loc;
                    distance = loc2.distanceSquaredTo(loc);
                }
            }
        }
        return closest;
    }

    public static MapLocation getClosestRobot(RobotInfo[] a) throws Exception {
        return getClosestRobot(a, G.me);
    }
    public static MapLocation getClosestRobot(RobotInfo[] a, MapLocation me) throws Exception {
        /* Get closest RobotInfo to me (Euclidean) */
        MapLocation closest = a[0].getLocation();
        int distance = me.distanceSquaredTo(a[0].getLocation());
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i].getLocation();
            if (me.distanceSquaredTo(loc) < distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    public static MapLocation getFarthest(MapLocation[] a) throws Exception {
        /* Get farthest MapLocation to this robot (Euclidean) */
        return getFarthest(a, G.me);
    }

    public static MapLocation getFarthest(MapLocation[] a, MapLocation me) throws Exception {
        /* Get farthest MapLocation to me (Euclidean) */
        MapLocation closest = a[0];
        int distance = me.distanceSquaredTo(a[0]);
        for (int i = a.length; --i >= 0;) {
            MapLocation loc = a[i];
            if (me.distanceSquaredTo(loc) > distance) {
                closest = loc;
                distance = me.distanceSquaredTo(loc);
            }
        }
        return closest;
    }

    // basic random movement
    public static void moveRandomly() throws Exception {
        moveRandomly(defaultMicro);
    }

    public static void moveRandomly(Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            boolean stuck = true;
            for (int i = 8; --i >= 0;) {
                if (G.rc.canMove(G.DIRECTIONS[i])) {
                    stuck = false;
                }
            }
            if (stuck) {
                return;
            }
            // move in a random direction but minimize making useless moves back to where
            // you came from
            Direction direction = G.DIRECTIONS[Random.rand() & 7];
            if (direction == lastRandomDir.opposite() && G.rc.canMove(direction.opposite())) {
                direction = direction.opposite();
            }
            if (microMove(m.micro(direction, G.rc.adjacentLocation(direction)))) {
                lastRandomDir = direction;
            }
        }
    }

    public static void spreadRandomly() throws Exception {
        spreadRandomly(defaultMicro);
    }

    public static void spreadRandomly(Micro m) throws Exception {
        boolean stuck = true;
        for (int i = G.DIRECTIONS.length; --i >= 0;) {
            if (G.rc.canMove(G.DIRECTIONS[i])) {
                stuck = false;
            }
        }
        if (stuck) {
            return;
        }
        if (G.rc.isMovementReady()) {
            MapLocation target = G.me;
            for (int i = G.allyRobots.length; --i >= 0;) {
                // ignore towers
                if (!G.allyRobots[i].type.isRobotType())
                    target = target.subtract(G.me.directionTo(G.allyRobots[i].getLocation()));
            }
            for (int i = 8; --i >= 0;) {
                if (!G.rc.canMove(G.DIRECTIONS[i])) {
                    target = target.subtract(G.DIRECTIONS[i]);
                }
            }
            if (target.equals(G.me)) {
                // just keep moving in the same direction as before if there's no robots nearby
                if (G.round % 3 == 0 || lastRandomSpread == null) {
                    moveRandomly(); // occasionally move randomly to avoid getting stuck
                } else if (Random.rand() % 20 == 0) {
                    // don't get stuck in corners
                    lastRandomSpread = G.rc.adjacentLocation(G.DIRECTIONS[Random.rand() & 7]);
                    moveRandomly();
                } else {
                    // Direction direction = bug2Helper(me, lastRandomSpread, TOWARDS, 0, 0);
                    Direction direction = G.me.directionTo(target);
                    if (microMove(m.micro(direction, target))) {
                        lastRandomSpread = lastRandomSpread.add(direction);
                        lastRandomDir = direction;
                    } else {
                        moveRandomly();
                    }
                }
                lastDir = Direction.CENTER;
                optimalDir = Direction.CENTER;
            } else {
                if (lastDir == G.me.directionTo(target)) {
                    lastDir = Direction.CENTER;
                }
                Direction direction = bug2Helper(G.me, target, TOWARDS, 0, 0);
                if (microMove(m.micro(direction, target))) {
                    lastRandomSpread = target;
                    lastRandomDir = direction;
                }
            }
        }
    }

    public static MapLocation exploreLoc;

    public static void exploreRandomly() throws Exception {
        exploreRandomly(defaultMicro);
    }

    public static void exploreRandomly(Micro m) throws Exception {
        exploreLoc = exploreRandomlyLoc();
        G.indicatorString.append(exploreLoc);
        if (G.rc.isMovementReady()) {
            bugnavTowards(exploreLoc, m);
            // if (ENABLE_EXPLORE_INDICATORS)
            //     G.rc.setIndicatorLine(G.me, exploreLoc, 0, 255, 0);
        }
    }

    public static void exploreCorners(Micro m) throws Exception {
        MapLocation best = null;
        int bestDist = 1000000;
        int dist = G.me.distanceSquaredTo(new MapLocation(0, 0));
        String s = "";
        s += dist;
        s += " ";
        if (dist > 25 && dist < bestDist && (((Comms.explored[0] >> 0) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(0, 0);
        }
        dist = G.me.distanceSquaredTo(new MapLocation(0, G.mapHeight - 1));
        s += dist;
        s += " ";
        if (dist > 25 && dist < bestDist && (((Comms.explored[G.mapHeight - 1] >> 0) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(0, G.mapHeight - 1);
        }
        dist = G.me.distanceSquaredTo(new MapLocation(G.mapWidth - 1, 0));
        s += dist;
        s += " ";
        if (dist > 25 && dist < bestDist && (((Comms.explored[0] >> G.mapWidth - 1) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(G.mapWidth - 1, 0);
        }
        dist = G.me.distanceSquaredTo(new MapLocation(G.mapWidth - 1, G.mapHeight - 1));
        s += dist;
        s += " ";
        System.out.println(s);
        if (dist > 25 && dist < bestDist && ((((Comms.explored[G.mapHeight - 1] >> G.mapWidth - 1)) & 1) == 0)) {
            bestDist = dist;
            best = new MapLocation(G.mapWidth - 1, G.mapHeight - 1);
        }
        if (best != null) {
            exploreLoc = best;
        }
        exploreRandomly(m);
    }

    public static int exploreTime = 0;

    public static final int SYMMETRY_EXPLORE_PERCENT = Integer.MAX_VALUE / 2; // OPTNET_PARAM
    // used for soldiers at low hp, avoid exploring enemy towers
    public static boolean avoidSymmetryExplore = false;
    public static boolean exploreTowerCheck = false;

    public static MapLocation exploreRandomlyLoc() throws Exception {
        if (G.rc.isMovementReady()) {
            --exploreTime;
        }
        if (exploreLoc != null) {
            if (G.rc.canSenseLocation(exploreLoc)) {
                exploreLoc = null;
            } else if (exploreTime == 0) {
                exploreLoc = null;
            // } else if (Random.rand() % 35 == 0) {
            //     exploreLoc = null;
            } else if (G.type == UnitType.RAT_KING && (Comms.catSeenLocs[exploreLoc.y] >> exploreLoc.x & 1) == 1) {
                exploreLoc = null;
            }
            // else if (exploreTowerCheck) {
            //     for (int i = Comms.numberOfMines; --i >= 0;) {
            //         if (exploreLoc.isWithinDistanceSquared(Comms.mineLocs[i], 20)) {
            //             exploreLoc = null;
            //             break;
            //         }
            //     }
            // }
        }
        //find a random location that doesn't have cats
        if (exploreLoc == null && G.type == UnitType.RAT_KING) {
            int sum = G.mapArea;
            for (int i = G.mapHeight; --i >= 0;) {
                sum -= Long.bitCount(Comms.catSeenLocs[i]);
            }
            // int a = Clock.getBytecodeNum();
            // for (int j = 10; --j >= 0;) {
            int rand = Random.rand() % sum;
            int cur = 0;
            for (int i = G.mapHeight; --i >= 0;) {
                cur += G.mapWidth - Long.bitCount(Comms.catSeenLocs[i]);
                if (cur > rand) {
                    rand -= cur - (G.mapWidth - Long.bitCount(Comms.catSeenLocs[i]));
                    int cur2 = 0;
                    for (int b = G.mapWidth; --b >= 0;) {
                        if (((Comms.catSeenLocs[i] >> b) & 1) == 0) {
                            if (++cur2 > rand) {
                                // if (exploreLoc == null || getChebyshevDistance(G.me, exploreLoc) >
                                // getChebyshevDistance(G.me, new MapLocation(b, i))) {
                                MapLocation loc = new MapLocation(b, i);
                                // }
                                exploreLoc = loc;
                                exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
                                exploreTowerCheck = true;
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }
        //Symmetry explore
        if (exploreLoc == null && !avoidSymmetryExplore && Comms.numValidSymmetries == 1
                && Random.rand() >= SYMMETRY_EXPLORE_PERCENT) {
            int rand = Random.rand() % Comms.numberOfMines;
            search: for (int j = Comms.numberOfMines; --j >= 0;) {
                int i = (j + rand) % Comms.numberOfMines;
                int rand2 = Random.rand() % 3;
                for (int j2 = 3; --j2 >= 0;) {
                    int i2 = (j2 + rand2) % 3;
                    if (Comms.symmetry[i2]) {
                        MapLocation loc = Comms.getOppositeMapLocation(Comms.mineLocs[i], i2);
                        if (G.lastSeenCatLocation != null && loc.distanceSquaredTo(G.lastSeenCatLocation) <= G.CAT_SIGHT_RANGE_SQUARED) {
                            continue;
                        }
                        // if (((Comms.explored[loc.y] >> Comms.explored[loc.x]) & 1) == 0) {
                        exploreLoc = loc;
                        exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
                        exploreTowerCheck = true;
                        break search;
                        // }
                    }
                }
            }
        }
        //Find a random location that we haven't explored
        if (exploreLoc == null) {
            int sum = G.mapArea;
            for (int i = G.mapHeight; --i >= 0;) {
                sum -= Long.bitCount(Comms.explored[i]);
            }
            // int a = Clock.getBytecodeNum();
            // for (int j = 10; --j >= 0;) {
            search: for (int j = 10; --j >= 0;) {
                int rand = Random.rand() % sum;
                int cur = 0;
                for (int i = G.mapHeight; --i >= 0;) {
                    cur += G.mapWidth - Long.bitCount(Comms.explored[i]);
                    if (cur > rand) {
                        rand -= cur - (G.mapWidth - Long.bitCount(Comms.explored[i]));
                        int cur2 = 0;
                        for (int b = G.mapWidth; --b >= 0;) {
                            if (((Comms.explored[i] >> b) & 1) == 0) {
                                if (++cur2 > rand) {
                                    // if (exploreLoc == null || getChebyshevDistance(G.me, exploreLoc) >
                                    // getChebyshevDistance(G.me, new MapLocation(b, i))) {
                                    MapLocation loc = new MapLocation(b, i);
                                    // }
                                    // if (G.lastSeenCatLocation != null && loc.distanceSquaredTo(G.lastSeenCatLocation) <= G.CAT_SIGHT_RANGE_SQUARED) {
                                    //     continue;
                                    // }
                                    for (int ind = Comms.numberOfMines; --ind >= 0;) {
                                        if (Comms.mineLocs[ind].distanceSquaredTo(loc) <= 20) {
                                            continue search;
                                        }
                                    }
                                    exploreLoc = loc;
                                    exploreTime = getChebyshevDistance(G.me, exploreLoc) + 40;
                                    // exploreTime = 50; // 47.7
                                    exploreTowerCheck = true;
                                    break search;
                                }
                            }
                        }
                        continue search;
                    }
                }
            }
            // if (exploreLoc != null && G.allyRobots.length > 5) {
            // MapLocation otherBots = G.me;
            // for (int i = G.allyRobots.length; --i >= 0;) {
            // otherBots = otherBots.translate(G.allyRobots[i].location.x,
            // G.allyRobots[i].location.y);
            // }
            // if (((double) (otherBots.x * exploreLoc.x + otherBots.y * exploreLoc.y)) /
            // ((double) otherBots.distanceSquaredTo(new MapLocation(0, 0)) *
            // G.me.distanceSquaredTo(new MapLocation(0, 0))) > 0) {
            // exploreLoc = null;
            // }
            // }

            // int radius = 20 + G.round / 10;
            // exploreLoc = new MapLocation(Math.min(Math.max(G.me.x + (Random.rand() % 15 - 7), 0), G.mapWidth - 1), Math.min(Math.max(G.me.y + (Random.rand() % 15 - 7), 0), G.mapHeight - 1));

            // MapLocation homeLoc = Comms.ratKingLocations[0];
            // exploreLoc = new MapLocation(Math.min(Math.max(homeLoc.x + (Random.rand() % (radius * 2 + 1) - radius), 0), G.mapWidth - 1), Math.min(Math.max(homeLoc.y + (Random.rand() % (radius * 2 + 1) - radius), 0), G.mapHeight - 1));
            
            // exploreLoc = new MapLocation(Math.min(Math.max(G.me.x + (Random.rand() % 15 - 7), 0), G.mapWidth - 1), Math.min(Math.max(G.me.y + (Random.rand() % 15 - 7), 0), G.mapHeight - 1));

            // int radius = 15;
            // exploreLoc = new MapLocation(Math.min(Math.max(G.me.x + (Random.rand() % (radius * 2 + 1) - radius), 0), G.mapWidth - 1), Math.min(Math.max(G.me.y + (Random.rand() % (radius * 2 + 1) - radius), 0), G.mapHeight - 1));

            // while (exploreLoc.distanceSquaredTo(homeLoc) > radius * radius || exploreLoc.distanceSquaredTo(homeLoc) < 7 * 7) {
            //     exploreLoc = new MapLocation(Math.min(Math.max(G.me.x + (Random.rand() % 15 - 7), 0), G.mapWidth - 1), Math.min(Math.max(G.me.y + (Random.rand() % 15 - 7), 0), G.mapHeight - 1));
            // }
            // exploreLoc = new MapLocation(Random.rand() % G.mapWidth, Random.rand() % G.mapHeight);
            // exploreTime = getChebyshevDistance(G.me, exploreLoc) + 20;
            // exploreTowerCheck = true;
        }
        //Random location
        while (exploreLoc == null || (G.lastSeenCatLocation != null && exploreLoc.distanceSquaredTo(G.lastSeenCatLocation) <= G.CAT_SIGHT_RANGE_SQUARED)){
            exploreLoc = new MapLocation(Random.rand() % G.mapWidth, Random.rand() % G.mapHeight);
        }
        return exploreLoc;
    }
    
    // cownav
    public static StringBuilder lastVisitedLocations = new StringBuilder();

    // bugnav helpers

    public static MapLocation bugnavTarget;
    public static int bugnavMode = -1;

    public static int minDistanceToTarget;
    public static int maxDistanceFromTarget;
    public static int minCircleDistance;
    public static int maxCircleDistance;
    public static boolean obstacleOnRight;
    public static MapLocation currentObstacle;
    public static StringBuilder visitedList = new StringBuilder();

    public static boolean[] canMove = new boolean[8];

    public static Direction bug2Helper(MapLocation me, MapLocation target, int mode, int minCircleDistance1,
            int maxCircleDistance1) throws Exception {
        boolean stuck = true;
        if (G.type.isRatKingType()) {
            for (int i = 8; --i >= 0;) {
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
                }
                canMove[i] = works;
                if (canMove[i]) {
                    stuck = false;
                }
            }
        }
        else {
            for (int i = 8; --i >= 0;) {
                canMove[i] = canMove(G.DIRECTIONS[i]);
                if (canMove[i]) {
                    stuck = false;
                }
            }
        }

        if (stuck) {
            return Direction.CENTER;
        }

        if (bugnavTarget == null || bugnavTarget.distanceSquaredTo(target) > 8 || bugnavMode != mode) {
            reset();
        }
        bugnavTarget = target;
        bugnavMode = mode;
        minCircleDistance = minCircleDistance1;
        maxCircleDistance = maxCircleDistance1;

        int distanceToTarget = getChebyshevDistance(G.me, target);
        switch (bugnavMode) {
            case TOWARDS:
                if (distanceToTarget < minDistanceToTarget) {
                    reset();
                    minDistanceToTarget = distanceToTarget;
                }
                break;
            case AWAY:
                if (distanceToTarget > maxDistanceFromTarget) {
                    reset();
                    maxDistanceFromTarget = distanceToTarget;
                }
                break;
            case AROUND:
                // kind of approximation
                // probably wont circle around something with very large radius?
                int dist = G.me.distanceSquaredTo(bugnavTarget);
                if (dist < minCircleDistance) {
                    if (distanceToTarget > maxDistanceFromTarget) {
                        reset();
                        maxDistanceFromTarget = distanceToTarget;
                    }
                } else if (dist > maxCircleDistance) {
                    if (distanceToTarget < minDistanceToTarget) {
                        reset();
                        minDistanceToTarget = distanceToTarget;
                    }
                }
                break;
        }

        if (currentObstacle != null && canMove[G.dirOrd(G.me.directionTo(currentObstacle))]) {
            reset();
        }

        if (visitedList.indexOf("" + getState()) != -1) {
            reset();
        }
        visitedList.append("" + getState());

        Direction targetDirection = getTargetDirection();

        if (currentObstacle == null) {
            if (!targetDirection.equals(Direction.CENTER) && canMove[G.dirOrd(targetDirection)]) {
                return targetDirection;
            }

            setInitialDirection(targetDirection);
            if (currentObstacle == null) {
                return Direction.CENTER;
            }
        }

        return followWall(true);
    }

    public static void reset() {
        minDistanceToTarget = Integer.MAX_VALUE;
        maxDistanceFromTarget = 0;
        obstacleOnRight = true;
        currentObstacle = null;
        visitedList = new StringBuilder();
    }

    public static Direction getTargetDirection() throws Exception {
        if (G.me.equals(bugnavTarget)) {
            if (bugnavMode == AROUND) {
                return Direction.EAST;
            } else {
                return Direction.CENTER;
            }
        }
        Direction direction = G.me.directionTo(bugnavTarget);
        switch (bugnavMode) {
            case AWAY:
                direction = direction.opposite();
                break;
            case AROUND:
                int dist = G.me.distanceSquaredTo(bugnavTarget);
                if (dist < minCircleDistance) {
                    direction = direction.opposite();
                } else if (dist <= maxCircleDistance) {
                    direction = direction.rotateLeft().rotateLeft();
                    if (circleDirection == COUNTER_CLOCKWISE) {
                        direction = direction.opposite();
                    }

                    if (!canMove[G.dirOrd(direction)]) {
                        direction = direction.opposite();
                        circleDirection *= -1;
                    }
                }
                break;
        }
        return direction;
    }

    public static void setInitialDirection(Direction forward) throws Exception {
        if (forward.equals(Direction.CENTER)) {
            return;
        }
        Direction left = forward.rotateLeft();
        for (int i = 8; --i >= 0;) {
            MapLocation location = G.rc.adjacentLocation(left);
            if (G.rc.onTheMap(location) && canMove[G.dirOrd(left)]) {
                break;
            }

            left = left.rotateLeft();
        }

        Direction right = forward.rotateRight();
        for (int i = 8; --i >= 0;) {
            MapLocation location = G.rc.adjacentLocation(right);
            if (G.rc.onTheMap(location) && canMove[G.dirOrd(right)]) {
                break;
            }

            right = right.rotateRight();
        }

        // TODO: add paint weightings

        MapLocation leftLocation = G.rc.adjacentLocation(left);
        MapLocation rightLocation = G.rc.adjacentLocation(right);

        int leftDistance = getChebyshevDistance(leftLocation, bugnavTarget);
        int rightDistance = getChebyshevDistance(rightLocation, bugnavTarget);

        if (leftDistance < rightDistance) {
            obstacleOnRight = true;
        } else if (rightDistance < leftDistance) {
            obstacleOnRight = false;
        } else {
            obstacleOnRight = bugnavTarget.distanceSquaredTo(leftLocation) < bugnavTarget.distanceSquaredTo(rightLocation);
        }

        if (obstacleOnRight) {
            currentObstacle = G.rc.adjacentLocation(left.rotateRight());
        } else {
            currentObstacle = G.rc.adjacentLocation(right.rotateLeft());
        }
    }

    public static Direction followWall(boolean canRotate) throws Exception {
        Direction direction = G.me.directionTo(currentObstacle);

        for (int i = 8; --i >= 0;) {
            direction = obstacleOnRight ? direction.rotateLeft() : direction.rotateRight();
            if (canMove[G.dirOrd(direction)]) {
                return direction;
            }

            MapLocation location = G.rc.adjacentLocation(direction);
            if (canRotate && !G.rc.onTheMap(location)) {
                obstacleOnRight = !obstacleOnRight;
                return followWall(false);
            }

            if (G.rc.onTheMap(location) && !canMove[G.dirOrd(direction)]) {
                currentObstacle = location;
            }
        }
        return Direction.CENTER;
    }

    public static char getState() {
        Direction direction = currentObstacle != null ? G.me.directionTo(currentObstacle) : Direction.CENTER;
        int rotation = obstacleOnRight ? 1 : 0;

        return (char) ((((G.me.x << 6) | G.me.y) << 5) | (direction.ordinal() << 1) |
                rotation);
    }

    public static int[] simulateMovement(MapLocation me, MapLocation dest) throws Exception {
        MapLocation clockwiseLoc = G.me;
        Direction clockwiseLastDir = lastDir;
        int clockwiseStuck = 0;
        MapLocation counterClockwiseLoc = G.me;
        Direction counterClockwiseLastDir = lastDir;
        int counterClockwiseStuck = 0;
        search: for (int t = 0; t < 10; t++) {
            // search: for (int t = 0; t < 2; t++) {
            if (clockwiseLoc.equals(dest)) {
                break;
            }
            if (counterClockwiseLoc.equals(dest)) {
                break;
            }
            Direction clockwiseDir = clockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = clockwiseLoc.add(clockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (clockwiseDir != clockwiseLastDir.opposite() && G.rc.senseMapInfo(loc).isPassable()
                                && G.rc.senseRobotAtLocation(loc) == null) {
                            clockwiseLastDir = clockwiseDir;
                            break;
                        }
                    }
                    clockwiseDir = clockwiseDir.rotateRight();
                    if (i == 7) {
                        clockwiseStuck = 1;
                        break search;
                    }
                }
            }
            Direction counterClockwiseDir = counterClockwiseLoc.directionTo(dest);
            {
                for (int i = 9; --i >= 0;) {
                    MapLocation loc = counterClockwiseLoc.add(counterClockwiseDir);
                    if (G.rc.onTheMap(loc)) {
                        if (!G.rc.canSenseLocation(loc)) {
                            break search;
                        }
                        if (counterClockwiseDir != counterClockwiseLastDir.opposite()
                                && G.rc.senseMapInfo(loc).isPassable() && G.rc.senseRobotAtLocation(loc) == null) {
                            counterClockwiseLastDir = counterClockwiseDir;
                            break;
                        }
                    }
                    counterClockwiseDir = counterClockwiseDir.rotateLeft();
                    if (i == 7) {
                        counterClockwiseStuck = 1;
                        break search;
                    }
                }
            }
            clockwiseLoc = clockwiseLoc.add(clockwiseDir);
            counterClockwiseLoc = counterClockwiseLoc.add(counterClockwiseDir);
        }

        int clockwiseDist = clockwiseLoc.distanceSquaredTo(dest);
        int counterClockwiseDist = counterClockwiseLoc.distanceSquaredTo(dest);

        return new int[] { clockwiseDist, clockwiseStuck, counterClockwiseDist, counterClockwiseStuck };
    }

    public static Direction bug2Helper(MapLocation me, MapLocation me2, MapLocation dest, int mode,
            int minRadiusSquared, int maxRadiusSquared) throws Exception {
        Direction direction = me.directionTo(dest);
        if (me.equals(dest)) {
            if (mode == AROUND) {
                direction = Direction.EAST;
            } else {
                return Direction.CENTER;
            }
        }
        if (mode == AWAY) {
            direction = direction.opposite();
        } else if (mode == AROUND) {
            if (me.distanceSquaredTo(dest) < minRadiusSquared) {
                direction = direction.opposite();
            } else if (me.distanceSquaredTo(dest) <= maxRadiusSquared) {
                direction = direction.rotateLeft().rotateLeft();
                if (circleDirection == COUNTER_CLOCKWISE) {
                    direction = direction.opposite();
                }
            }
            lastDir = Direction.CENTER;
        }

        boolean stuck = true;
        for (int i = 4; --i >= 0;) {
            String m = me + " " + i + " ";
            if (visitedList.indexOf(m) == -1) {
                visitedList.append(m);
                stuck = false;
                break;
            }
        }
        if (stuck) {
            moveRandomly();
            visitedList = new StringBuilder();
            return Direction.CENTER;
        }

        // G.indicatorString.append("DIR=" + direction + " ");
        if (optimalDir != Direction.CENTER && mode != AROUND) {
            if (canMove(optimalDir) && lastDir != optimalDir.opposite()) {
                optimalDir = Direction.CENTER;
                rotation = NONE;
                visitedList = new StringBuilder();
            } else {
                direction = optimalDir;
            }
        }
        // G.indicatorString.append("OPTIMAL=" + optimalDir + " ");

        // G.indicatorString.append("CIRCLE: " + circleDirection + " ");
        // G.indicatorString.append("DIR: " + direction + " ");
        // G.indicatorString.append("OFF: " + G.rc.onTheMap(me.add(direction)) + " ");

        if (lastDir != direction.opposite()) {
            if (canMove(direction)) {
                // if (!lastBlocked) {
                // rotation = NONE;
                // }
                // lastBlocked = false;
                // boolean touchingTheWallBefore = false;
                // for (int i = DIRECTIONS.length; --i>=0;) {
                // MapLocation translatedMapLocation = me.add(d);
                // if (G.rc.onTheMap(translatedMapLocation)) {
                // if (!G.rc.senseMapInfo(translatedMapLocation).isPassable()) {
                // touchingTheWallBefore = true;
                // break;
                // }
                // }
                // }
                // if (touchingTheWallBefore) {
                // rotation = NONE;
                // }
                return direction;
            }
        } else if (canMove(direction)) {
            Direction dir;
            if (rotation == CLOCKWISE) {
                dir = direction.rotateRight();
            } else {
                dir = direction.rotateLeft();
            }
            if (!G.rc.onTheMap(me.add(dir))) {
                // boolean touchingTheWallBefore = false;
                // for (int i = DIRECTIONS.length; --i>=0;) {
                // MapLocation translatedMapLocation = me.add(d);
                // if (G.rc.onTheMap(translatedMapLocation)) {
                // if (!G.rc.senseMapInfo(translatedMapLocation).isPassable()) {
                // touchingTheWallBefore = true;
                // break;
                // }
                // }
                // }
                // if (touchingTheWallBefore) {
                // rotation = NONE;
                // }
                rotation *= -1;
                return direction;
            }
        }
        if (!G.rc.onTheMap(me.add(direction))) {
            if (mode == AROUND) {
                circleDirection *= -1;
                direction = direction.opposite();
                // G.indicatorString.append("FLIPPED ");
            } else {
                direction = me.directionTo(dest);
            }
            if (canMove(direction)) {
                return direction;
            }
        }

        if (optimalDir == Direction.CENTER) {
            optimalDir = direction;
        }

        // G.indicatorString.append("ROTATION=" + rotation + " ");
        if (rotation == NONE) {
            // if (G.rng.nextInt(2) == 0) {
            // rotation = CLOCKWISE;
            // } else {
            // rotation = COUNTER_CLOCKWISE;
            // }
            int[] simulated = simulateMovement(me, dest);

            int clockwiseDist = simulated[0];
            int counterClockwiseDist = simulated[2];
            boolean clockwiseStuck = simulated[1] == 1;
            boolean counterClockwiseStuck = simulated[3] == 1;

            // G.indicatorString.append("DIST=" + clockwiseDist + " " +
            // counterClockwiseDist
            // + " ");
            int tempMode = mode;
            if (mode == AROUND) {
                if (clockwiseDist < minRadiusSquared) {
                    if (counterClockwiseDist < minRadiusSquared) {
                        tempMode = AWAY;
                    } else {
                        tempMode = AWAY;
                    }
                } else {
                    if (counterClockwiseDist < minRadiusSquared) {
                        tempMode = AWAY;
                    } else {
                        tempMode = TOWARDS;
                    }
                }
            }
            if (clockwiseStuck) {
                rotation = COUNTER_CLOCKWISE;
            } else if (counterClockwiseStuck) {
                rotation = CLOCKWISE;
            } else if (tempMode == TOWARDS) {
                if (clockwiseDist < counterClockwiseDist) {
                    rotation = CLOCKWISE;
                } else {
                    rotation = COUNTER_CLOCKWISE;
                }
            } else if (tempMode == AWAY) {
                if (clockwiseDist < counterClockwiseDist) {
                    rotation = COUNTER_CLOCKWISE;
                } else {
                    rotation = CLOCKWISE;
                }
            }
        }

        boolean flip = false;
        for (int i = 8; --i >= 0;) {
            if (rotation == CLOCKWISE) {
                direction = direction.rotateRight();
            } else {
                direction = direction.rotateLeft();
            }
            if (!G.rc.onTheMap(me.add(direction))) {
                flip = true;
            }
            // if (G.rc.onTheMap(me.add(direction)) &&
            // G.rc.senseMapInfo(me.add(direction)).isPassable() && lastDir !=
            // direction.opposite()) {
            // if (canMove(direction)) {
            // return direction;
            // }
            // return Direction.CENTER;
            // }
            if (canMove(direction) && lastDir != direction.opposite()) {
                if (flip) {
                    rotation *= -1;
                }
                if (canMove(direction)) {
                    return direction;
                }
                return Direction.CENTER;
            }
        }
        if (flip) {
            rotation *= -1;
        }
        if (canMove(lastDir.opposite())) {
            return lastDir.opposite();
        }
        return Direction.CENTER;
    }

    // IMPORTANT: bugnav takes around 1100 bytecode

    public static void bugnavTowards(MapLocation dest) throws Exception {
        bugnavTowards(dest, defaultMicro);
    }

    public static void bugnavTowards(MapLocation dest, Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.me, dest, TOWARDS, 0, 0);
            // Direction d = bug2Helper(dest, TOWARDS, 0, 0);
            // what is purpose of this v
            // if (d == Direction.CENTER) {
            //     d = G.me.directionTo(dest);
            // }
            microMove(m.micro(d, dest));
        }
    }

    public static void bugnavAway(MapLocation dest) throws Exception {
        bugnavAway(dest, defaultMicro);
    }

    public static void bugnavAway(MapLocation dest, Micro m) throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.me, dest, AWAY, 0, 0);
            if (d == Direction.CENTER) {
                d = G.me.directionTo(dest);
            }
            microMove(m.micro(d, dest));
        }
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared) throws Exception {
        bugnavAround(dest, minRadiusSquared, maxRadiusSquared, defaultMicro);
    }

    public static void bugnavAround(MapLocation dest, int minRadiusSquared, int maxRadiusSquared, Micro m)
            throws Exception {
        if (G.rc.isMovementReady()) {
            Direction d = bug2Helper(G.me, dest, AROUND, minRadiusSquared, maxRadiusSquared);
            // Direction d = bug2Helper(dest, AROUND, minRadiusSquared, maxRadiusSquared);
            if (d == Direction.CENTER) {
                d = G.me.directionTo(dest);
            }
            microMove(m.micro(d, dest));
        }
    }

    public static boolean attemptTurnToRatKing(MapLocation dest) throws Exception {
        if (G.me.distanceSquaredTo(dest) <= 8) {
            return turn(G.me.directionTo(dest));
        }
        return false;
    }
    public static boolean attemptTurnToRatKingForCheese(MapLocation dest) throws Exception {
        if (G.me.distanceSquaredTo(dest) <= 10) {
            return turn(G.me.directionTo(dest));
        }
        return false;
    }
    public static boolean attemptTurnToRat(MapLocation dest) throws Exception {
        if (G.me.distanceSquaredTo(dest) <= 2) {
            return turn(G.me.directionTo(dest));
        }
        return false;
    }

    /**
     * Default movement micro - avoid clusters of bots, especially on non-allied
     * paint
     */
    // public static Micro defaultMicro = (Direction d, MapLocation dest) -> {
    //     int[] scores = new int[9];
    //     scores[G.dirOrd(d)] += 20;
    //     if (d != Direction.CENTER) {
    //         scores[G.dirOrd(d.rotateLeft())] += 15;
    //         scores[G.dirOrd(d.rotateRight())] += 15;
    //     }
    //     for (int i = 8; --i >= 0;) {
    //         if (G.rc.canRemoveDirt(G.rc.adjacentLocation(G.ALL_DIRECTIONS[i]))) {
    //             scores[i] -= 6;
    //         }
    //     }
    //     return scores;
    // };
    public static Micro defaultMicro = BabyRatMicro.micro;

    // public static boolean microMove(int[] scores) throws Exception {
    //     int best = 0;
    //     int numBest = 1;
    //     // optimization, just start with best=0
    //     for (int i = scores.length; --i >= 1;) {
    //         if (scores[i] > scores[best]) {
    //             best = i;
    //             numBest = 1;
    //         } else if (scores[i] == scores[best] && Random.rand() % ++numBest == 0) {
    //             best = i;
    //         }
    //     }
    //     return move(G.ALL_DIRECTIONS[best]);
    // }

    // microMove but also attacks and places traps
    public static boolean microMove(MicroScores scores) throws Exception {
        int best = 0;
        int numBest = 1;
        // optimization, just start with best=0
        for (int i = scores.moveScores.length; --i >= 1;) {
            if (scores.moveScores[i] > scores.moveScores[best]) {
                best = i;
                numBest = 1;
            } else if (scores.moveScores[i] == scores.moveScores[best] && Random.rand() % ++numBest == 0) {
                best = i;
            }
        }
        Direction strafeDirection = null;
        if (G.rc.isActionReady() && BabyRatMicro.canAct) {
            MicroActionScore action = scores.actionScores[best];
            if (action.score > 0) {
                boolean didAction = doMicroAction(action);
                if (action.type == MicroActionScore.RATNAP && G.rc.getCarrying() != null && !didAction) {
                    if (G.rc.isTurningReady() && G.rc.adjacentLocation(action.dir).equals(action.target)) {
                        turn(action.dir);
                        doMicroAction(action);
                    }
                    else {
                        strafeDirection = G.rc.adjacentLocation(G.ALL_DIRECTIONS[best]).directionTo(action.target);
                    }
                }
            }
        }
        RobotInfo opponent = null;
        if (G.lastSeenOpponentLocation != null && G.me.distanceSquaredTo(G.lastSeenOpponentLocation) <= 8) {
            for(int i = G.opponentRobots.length; --i >= 0;) {
                if (G.opponentRobots.infos[i].type.isBabyRatType() && (opponent == null ||
                    G.rc.adjacentLocation(G.ALL_DIRECTIONS[best]).distanceSquaredTo(opponent.location) > G.rc.adjacentLocation(G.ALL_DIRECTIONS[best]).distanceSquaredTo(G.opponentRobots.infos[i].location)
                 || (G.rc.adjacentLocation(G.ALL_DIRECTIONS[best]).distanceSquaredTo(opponent.location) == G.rc.adjacentLocation(G.ALL_DIRECTIONS[best]).distanceSquaredTo(G.opponentRobots.infos[i].location) && G.opponentRobots.infos[i].health > opponent.health))) {
                    opponent = G.opponentRobots.infos[i];
                }
            }
        }
        else if (strafeDirection == null && G.dir != G.ALL_DIRECTIONS[best]) {
            turn(G.ALL_DIRECTIONS[best]);
        }
        boolean moveResult = move(G.ALL_DIRECTIONS[best]);
        if (opponent != null) {
            G.indicatorString.append("OPP=" + opponent.location + " ");
        }
        if (G.lastSeenOpponentLocation != null) {
            G.indicatorString.append("GOPP=" + G.lastSeenOpponentLocation + " ");
        }
        if (opponent != null && G.me.distanceSquaredTo(opponent.location) <= 8) {
            turn(G.me.directionTo(opponent.location));
        }
        else if (G.lastSeenOpponentLocation != null && G.me.distanceSquaredTo(G.lastSeenOpponentLocation) <= 8) {
            turn(G.me.directionTo(G.lastSeenOpponentLocation));
        }
        if (G.rc.isTurningReady()) {
            if (strafeDirection != null) {
                turn(strafeDirection);
            }
            else {
                // if (G.rc.isActionReady() && BabyRatMicro.canAct) {
                //     MicroActionScore stationaryAttackScore = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
                //     stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir.rotateLeft()), G.me));
                //     stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir), G.me));
                //     stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir.rotateRight()), G.me));
                //     if (stationaryAttackScore.score > 0) {
                //         boolean didAction = doMicroAction(stationaryAttackScore);
                //         if (stationaryAttackScore.type == MicroActionScore.RATNAP && G.rc.getCarrying() != null && !didAction) {
                //             if (G.rc.adjacentLocation(stationaryAttackScore.dir).equals(stationaryAttackScore.target)) {
                //                 turn(stationaryAttackScore.dir);
                //                 doMicroAction(stationaryAttackScore);
                //             }
                //         }
                //     }
                // }
                if (G.rc.isTurningReady()) {
                    int bestTurn = 0;
                    int numBestTurn = 1;
                    // optimization, just start with best=0
                    for (int i = scores.turnScores.length; --i >= 1;) {
                        if (scores.turnScores[i] > scores.turnScores[bestTurn]) {
                            bestTurn = i;
                            numBestTurn = 1;
                        } else if (scores.turnScores[i] == scores.turnScores[bestTurn] && Random.rand() % ++numBestTurn == 0) {
                            bestTurn = i;
                        }
                    }
                    turn(G.ALL_DIRECTIONS[bestTurn]);
                }
            }
        }
        if (G.rc.isActionReady() && BabyRatMicro.canAct) {
            MicroActionScore stationaryAttackScore = new MicroActionScore(0, G.invalidLoc, Direction.CENTER, 0);
            stationaryAttackScore.compare(BabyRatMicro.tileScore(G.rc.adjacentLocation(G.dir.rotateLeft())));
            stationaryAttackScore.compare(BabyRatMicro.tileScoreNoStrafe(G.rc.adjacentLocation(G.dir), G.me));
            stationaryAttackScore.compare(BabyRatMicro.tileScore(G.rc.adjacentLocation(G.dir.rotateRight())));
            if (stationaryAttackScore.score > 0) {
                doMicroAction(stationaryAttackScore);
            }
        }
        return moveResult;
    }
    public static boolean doMicroAction(MicroActionScore action) throws Exception {
        switch (action.type) {
            case MicroActionScore.ATTACK:
                if (G.rc.canAttack(action.target)) {
                    G.rc.attack(action.target);
                    G.indicatorString.append("ATK:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_1:
                if (G.rc.canAttack(action.target, 1)) {
                    G.rc.attack(action.target, 1);
                    G.indicatorString.append("ATK1:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_2:
                if (G.rc.canAttack(action.target, 2)) {
                    G.rc.attack(action.target, 2);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_3:
                if (G.rc.canAttack(action.target, 5)) {
                    G.rc.attack(action.target, 5);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_4:
                if (G.rc.canAttack(action.target, 10)) {
                    G.rc.attack(action.target, 10);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_5:
                if (G.rc.canAttack(action.target, 17)) {
                    G.rc.attack(action.target, 17);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_6:
                if (G.rc.canAttack(action.target, 26)) {
                    G.rc.attack(action.target, 26);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_7:
                if (G.rc.canAttack(action.target, 37)) {
                    G.rc.attack(action.target, 37);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_8:
                if (G.rc.canAttack(action.target, 50)) {
                    G.rc.attack(action.target, 50);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.ATTACK_9:
                if (G.rc.canAttack(action.target, 65)) {
                    G.rc.attack(action.target, 65);
                    G.indicatorString.append("ATK2:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.TRAP:
                if (G.rc.canPlaceRatTrap(action.target)) {
                    G.rc.placeRatTrap(action.target);
                    G.indicatorString.append("TRAP:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.CAT_TRAP:
                if (G.rc.canPlaceCatTrap(action.target)) {
                    G.rc.placeCatTrap(action.target);
                    G.indicatorString.append("CAT_TRAP:" + action.target + " ");
                    return true;
                }
            case MicroActionScore.RATNAP:
                if (G.rc.getCarrying() == null) {
                    if (G.rc.canCarryRat(action.target)) {
                        G.rc.carryRat(action.target);
                        BabyRatMicro.carryRound = G.round;
                        return true;
                    }
                }
                else {
                    if (G.rc.adjacentLocation(G.dir).equals(action.target)) {
                        // if (G.rc.canThrowRat(G.me.directionTo(action.target))) {
                        if (G.rc.canThrowRat()) {
                            G.rc.throwRat();
                            return true;
                            // System.out.println("dropped rat " + G.me.directionTo(action.target));
                            // BabyRatMicro.lastDroppedRatID = G.rc.senseRobotAtLocation(G.rc.adjacentLocation(G.me.directionTo(action.target))).ID;
                            // BabyRatMicro.lastDroppedRatRound = G.round;
                        }
                    }
                }
        }
        // System.out.println("Didn't perform action: "+action.type+" "+action.score+" "+action.target);
        return false;
    }

    // false if it didn't move
    public static boolean move(Direction dir) throws Exception {
        if (dir == Direction.CENTER) {
            return false;
        }
        if (G.type.isRatKingType()) {
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir).add(dir))) {
                G.rc.removeDirt(G.rc.adjacentLocation(dir).add(dir));
            }
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir).add(dir.rotateLeft()))) {
                G.rc.removeDirt(G.rc.adjacentLocation(dir).add(dir.rotateLeft()));
            }
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir).add(dir.rotateLeft().rotateLeft()))) {
                G.rc.removeDirt(G.rc.adjacentLocation(dir).add(dir.rotateLeft().rotateLeft()));
            }
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir).add(dir.rotateRight()))) {
                G.rc.removeDirt(G.rc.adjacentLocation(dir).add(dir.rotateRight()));
            }
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir).add(dir.rotateRight().rotateRight()))) {
                G.rc.removeDirt(G.rc.adjacentLocation(dir).add(dir.rotateRight().rotateRight()));
            }
            if (G.rc.canMove(dir)) {
                G.rc.move(dir);
                lastDir = dir;
                lastMove = G.round;
                RobotPlayer.updateMove();
                return true;
            }
            return false;
        }
        if (G.rc.canMove(dir) || G.rc.canRemoveDirt(G.rc.adjacentLocation(dir))) {
            if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir))) {
                G.rc.removeDirt(G.rc.adjacentLocation(dir));
            }
            G.rc.move(dir);
            lastDir = dir;
            lastMove = G.round;
            RobotPlayer.updateMove();
            return true;
        }
        return false;
    }

    public static boolean canMove(Direction dir) throws Exception {
        if (G.rc.canMove(dir)) {
            return true;
        }
        if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir))) {
            return true;
        }
        if (G.rc.canSenseRobotAtLocation(G.rc.adjacentLocation(dir))) {
            return false;
        }
        // if (G.rc.canSenseRobotAtLocation(G.rc.adjacentLocation(dir)) && Random.rand() % 10 == 0) {
        //     return true;
        // }
        return false;
    }

    public static boolean turn(Direction dir) throws Exception {
        if (G.rc.canTurn(dir) && dir != G.dir) {
            G.rc.turn(dir);
            if (G.type.isBabyRatType())
                RobotPlayer.updateInfo();
            return true;
        }
        return false;
    }

    public static boolean isPassable(Direction dir) throws Exception {
        if (G.rc.canMove(dir)) {
            return true;
        }
        if (G.rc.canRemoveDirt(G.rc.adjacentLocation(dir))) {
            return true;
        }
        if (G.rc.canSenseRobotAtLocation(G.rc.adjacentLocation(dir))) {
            return false;
        }
        return false;
    }
}