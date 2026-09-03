package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.RobotPlayer;
import battlecode.common.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import bench_stroke.FastIterableLocSet;
import bench_stroke.DataStructures.*;

import bench_stroke.Utilities;

import static bench_stroke.BabyRat.*;
import static bench_stroke.BabyRat.nearestCat;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.Utilities.attemptAttack;
import static bench_stroke.Utilities.attemptAttackCat;
import static bench_stroke.Utilities.attemptCatTrap;
import static bench_stroke.Utilities.attemptRatnap;
import static bench_stroke.Utilities.attemptThrow;
import static bench_stroke.Utilities.attemptTrap;
import static bench_stroke.Utilities.tryBuild;

import bench_stroke.Pathfinding.*;
import bench_stroke.Communication.*;


class catMicroInfo {
    boolean passable;
    public MapLocation loc;
    public double minDistanceToCat;
    public boolean canAttackCat;
    public boolean inCatVision;
    public boolean forward;
    public boolean canRatnap;
    public boolean canRatnapAlly;
    public int knownAdjacentAllies;
    public boolean canBeRatnapped;
    public boolean canTrapCat;
    public int enemyDPS;
    public int minDistanceToEnemy;
    public boolean inCatPath;


    public boolean ratTrap;
    public boolean catTrap;

    //creates a micro Info tile, populating its information based on a map info tile
    public catMicroInfo(MapLocation loc) throws GameActionException {
        //note that we want to consider our current square as a valid option, so it is alwasy "passable"
        passable = rc.canMove(rc.getLocation().directionTo(loc)) || rc.getLocation().equals(loc);
        if(!passable) return;

        forward = Objects.equals(loc, rc.adjacentLocation(rc.getDirection()));

        if (rc.canSenseLocation(loc)) {
            MapInfo tile = rc.senseMapInfo(loc);
            TrapType t = tile.getTrap();
            if (t == TrapType.CAT_TRAP) {
                catTrap = true;
                ratTrap = false;
            }
            else if (t == TrapType.RAT_TRAP) {
                ratTrap = true;
                catTrap = false;
            }
            else {
                ratTrap = false;
                catTrap = false;
            }
        }
        else {
            ratTrap = false;
            catTrap = false;
        }

        //we don't need to populate info if the tile is not passable - waste of bytecodes
        this.loc = loc;
        knownAdjacentAllies = 0;
        canRatnap = false;
        canBeRatnapped = false;
        inCatVision = false;
        canAttackCat = false;
        minDistanceToCat = Integer.MAX_VALUE;
        canTrapCat = false;
        minDistanceToEnemy = Integer.MAX_VALUE;
        enemyDPS = 0;
        canRatnapAlly = true;
        inCatPath = false;

    }
    //default constructor for a catMicroInfo, setting passable to false so it will never be considered
    //used for spaces which are not on the map
    public catMicroInfo() {
        passable = false;
    }

    //UTILITY METHODS
    public void updateCat(RobotInfo cat, FastIterableLocSet adjacentCatSquares, FastLocSet catPath) throws GameActionException {
        int dist = loc.distanceSquaredTo(cat.location);
        double catDist = calculateCatDist(cat);
        if (catDist < minDistanceToCat) minDistanceToCat = catDist;
        if (nearestCatLocation.isWithinDistanceSquared(this.loc, 17, nearestCat.direction, 180, true)) {
            inCatVision = true;
        }
        if (adjacentCatSquares.contains(loc)) {
            canAttackCat = true;
        }
        if (dist <= 8) {
            canTrapCat = true;
        }
        // if (catPath.contains(loc)) {
        //     inCatPath = true;
        // }
    }
    //update our understanding of this space based on this enemy
    public void updateEnemy(RobotInfo enemy) {
        MapLocation enemyLoc = enemy.location;
        int dist = enemy.location.distanceSquaredTo(loc);
        if (dist < minDistanceToEnemy) {
            minDistanceToEnemy = dist;
        }
        if (isFacingLoc(enemyLoc, enemy.direction)) {
            enemyDPS += GameConstants.RAT_BITE_DAMAGE;
                if (enemy.health > health) {
                    canBeRatnapped = true;
                }
        }
        if (dist <= 2 && (enemy.health < health || !isFacingLoc(enemyLoc, enemy.direction))) {
            canRatnap = true;
        }
    }

    //update our understanding of this space based on this ally
    public void updateAlly(RobotInfo ally) {
        MapLocation allyLoc = ally.getLocation();
        int dist = allyLoc.distanceSquaredTo(loc);
        if (dist <= 2) {
            knownAdjacentAllies++;
            canRatnapAlly = true;
        }
    }

    //calculates an approximate distance from the middle of the cat to the location
    public double calculateCatDist(RobotInfo cat) {
        double catX = cat.location.x + 0.5;
        double catY = cat.location.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

    //returns whether a robot at location loc facing direction dir is facing the current space
    public boolean isFacingLoc(MapLocation other, Direction dir) {
        return other.directionTo(loc) == dir || other.directionTo(loc) == dir.rotateLeft() || other.directionTo(loc) == dir.rotateRight();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        catMicroInfo other = (catMicroInfo) obj;
        if(loc == null) return false;
        return loc.equals(other.loc);
    }

}
public class BRCatMicro implements Behavior {

    //instance variables
    private catMicroInfo[] microArray;
    private RobotInfo[] enemyRobots;
    private RobotInfo[] allyRobots;

    private int numberAllies;
    private FastIterableLocSet allyIDs = new FastIterableLocSet(40);

    //private FastIterableLocSet catVisionSquares;
    private FastIterableLocSet catAdjacentSquares;

    //Singleton Stuff
    private static BRCatMicro instance;
    private BRCatMicro() {}
    public static BRCatMicro getInstance() {
        if(instance == null) {
            instance = new BRCatMicro();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        if (nearestCatLocation == null || nearbyCats.length == 0) {
            NearbyCatSqueakInfo squeakInfo = (NearbyCatSqueakInfo) catSqueak.squeakInfo;
            nearestCatLocation = squeakInfo.location();
            Pathfinding.attemptMove(nearestCatLocation, false);
            if (rc.canSenseLocation(nearestCatLocation)) {
                nearestCat = rc.senseRobotAtLocation(nearestCatLocation);
            }
        }
        else if (rc.getLocation().distanceSquaredTo(nearestCatLocation) > 16) {
            Pathfinding.attemptMove(nearestCatLocation, false);
        }

        boolean rescueMode = inDistressRatKing != null && currentLocation.distanceSquaredTo(inDistressRatKing.loc()) <= ANSWER_DISTRESS_DIST_THRESHOLD;
        if (rescueMode) {
            //Squeak squeak = Communicator.getMostRecentSqueakOfType(ThrowingAtCatSqueakInfo.class);
            // int throw_round = -1;
            // if (squeak != null) {
            //     throw_round = squeak.round;
            // }
            if (rc.isActionReady() && (rc.isCooperation() || rc.getBackstabbingTeam() != rc.getTeam())) {
                attemptCatTrap(nearestCatLocation);
            }
            if (rc.isActionReady() && rc.getCarrying() == null && nearbyEnemies.length > 0) {
                //attemptEnemyThenAllyPickup();
                attemptEnemyPickup();
            }
            if (rc.isActionReady() && rc.getCarrying() != null) {
                attemptThrow(turnsCarrying, health);
            }
            if (rc.isActionReady() && nearestCatLocation != null) {
                MapLocation[] catSpaces = Utilities.getCatActionableLocations(nearestCatLocation);
                if (catSpaces != null && tryBuild(catSpaces)) {
                    // System.out.println("get blocked");
                }
            }
            if (rc.isMovementReady()) {
                microMovement(rescueMode);
            }
            if (rc.isActionReady() && (rc.isCooperation() || rc.getBackstabbingTeam() != rc.getTeam())) {
                attemptCatTrap(nearestCatLocation);
            }
            if (rc.isActionReady() && rc.getCarrying() == null) {
                attemptEnemyPickup();
            }
            if (rc.isActionReady() && rc.getCarrying() != null) {
                attemptThrow(turnsCarrying, health);
            }
            if (rc.isActionReady()) {
                attemptAttack();
            }
            if (rc.isActionReady()) {
                attemptAttackCat();
            }
            if (rc.isActionReady() && nearestCatLocation != null) {
                MapLocation[] catSpaces = Utilities.getCatActionableLocations(nearestCatLocation);
                if (catSpaces != null && tryBuild(catSpaces)) {
                    // System.out.println("get blocked");
                }
            }
        }
        else {
            if (rc.isCooperation() && rc.isActionReady()) {
                attemptCatTrap(nearestCatLocation);
            }
            if (rc.isActionReady() && rc.getCarrying() == null) {
                attemptRatnap();
            }
            if (rc.isActionReady() && rc.getCarrying() != null) {
                attemptThrow(turnsCarrying, health);
            }
            if (rc.isActionReady()) {
                attemptAttackCat();
            }
            if (rc.isMovementReady()) {
                microMovement(rescueMode);
            }
            if (rc.isCooperation() && rc.isActionReady()) {
                attemptCatTrap(nearestCatLocation);
            }
            if (rc.isActionReady() && rc.getCarrying() == null) {
                attemptRatnap();
            }
            if (rc.isActionReady() && rc.getCarrying() != null) {
                attemptThrow(turnsCarrying, health);
            }
            if (rc.isActionReady()) {
                attemptAttackCat();
            }
        }
    }

    //attempts to move to an adjacent space
    public void microMovement(boolean rescueMode) throws GameActionException {
        populateMicroArray();

        catMicroInfo bestMicro = microArray[0];

        //encodes decision heuristic (currently a decision tree)
        for(int i = 1; i < 9; i++) {
            // int diff = numberAllies - nearbyEnemies.length;
            if (rescueMode) {
                if (betterCatMicroRescue(bestMicro, microArray[i])) {
                    bestMicro = microArray[i];
                }
            }
            else {
                if (betterCatMicro(bestMicro, microArray[i])) {
                    bestMicro = microArray[i];
                }
            }
        }

        MapLocation curLoc = rc.getLocation();

        if (rc.canTurn() && nearbyCats.length > 0) {
            if (rc.getDirection() != curLoc.directionTo(nearestCatLocation) && curLoc.directionTo(nearestCatLocation) != Direction.CENTER) {
                rc.turn(curLoc.directionTo(nearestCatLocation));
            }
        }

        if(bestMicro.passable && !bestMicro.loc.equals(rc.getLocation())) {
            if(rc.canMove(curLoc.directionTo(bestMicro.loc))) {
                rc.move(curLoc.directionTo(bestMicro.loc));
            }
        }
    }

    public boolean betterCatMicro(catMicroInfo incumbent, catMicroInfo contender) {
        if (!contender.passable) return false;

        //1. try to avoid cat vision
        //(if action ready)
        //  2. try to attack the cat
        //  3. try to ratnap enemies to feed to cat
        //  4. try to trap the cat
        //5. try to avoid enemy ratnapping
        //6. try to avoid enemy DPS
        //7. move onto a cat trap
        //8. move away from cat

        if (incumbent.inCatVision && !contender.inCatVision) {
            return true;
        }
        else if (!incumbent.inCatVision && contender.inCatVision) {
            return false;
        }

        if (incumbent.inCatPath && !contender.inCatPath) {
            return true;
        }
        if (!incumbent.inCatPath && contender.inCatPath) {
            return false;
        }
        
        if (rc.isActionReady()) {
            if (!incumbent.canAttackCat && contender.canAttackCat) {
                return true;
            }
            else if (incumbent.canAttackCat && !contender.canAttackCat) {
                return false;
            }

            if (!incumbent.canRatnap && contender.canRatnap) {
                return true;
            }
            else if (incumbent.canRatnap && !contender.canRatnap) {
                return false;
            }

            if (!incumbent.canTrapCat && contender.canTrapCat) {
                return true;
            }
            else if (incumbent.canTrapCat && !contender.canTrapCat) {
                return false;
            }
        }

        else {
            if (incumbent.canAttackCat && !contender.canAttackCat) {
                return true;
            }
            else if (!incumbent.canAttackCat && contender.canAttackCat) {
                return false;
            }
        }

        if (incumbent.canBeRatnapped && !contender.canBeRatnapped) {
            return true;
        }
        else if (!incumbent.canBeRatnapped && contender.canBeRatnapped) {
            return false;
        }

        if (incumbent.enemyDPS > contender.enemyDPS) {
            return true;
        }
        else if (incumbent.enemyDPS < contender.enemyDPS) {
            return false;
        }

        if (!incumbent.catTrap && contender.catTrap) {
            return true;
        }
        else if (incumbent.catTrap && !contender.catTrap) {
            return false;
        }

        if (incumbent.minDistanceToCat < contender.minDistanceToCat) {
            return true;
        }
        else if (incumbent.minDistanceToCat > contender.minDistanceToCat) {
            return false;
        }

        return FastMath.fakefloat() > 0.5;

    }

    public boolean betterCatMicroRescue(catMicroInfo incumbent, catMicroInfo contender) {
        if (!contender.passable) return false;

        //(if action ready)
        //  1. try to ratnap enemies to feed to cat
        //  2. try to ratnap allies to feed to cat
        //  3. try to trap the cat
        //4. try to avoid cat vision
        //5. try to avoid enemy ratnapping
        //6. try to avoid enemy DPS
        
        if (rc.isActionReady()) {
            if (!incumbent.canRatnap && contender.canRatnap) {
                return true;
            }
            else if (incumbent.canRatnap && !contender.canRatnap) {
                return false;
            }

            if (!incumbent.canAttackCat && contender.canAttackCat) {
                return true;
            }
            else if (incumbent.canAttackCat && !contender.canAttackCat) {
                return false;
            }

            // if (!incumbent.canRatnapAlly && contender.canRatnapAlly) {
            //     return true;
            // }
            // else if (incumbent.canRatnapAlly && !contender.canRatnapAlly) {
            //     return false;
            // }

            if (!incumbent.canTrapCat && contender.canTrapCat) {
                return true;
            }
            else if (incumbent.canTrapCat && !contender.canTrapCat) {
                return false;
            }
        }

        if (incumbent.inCatVision && !contender.inCatVision) {
            return true;
        }
        else if (!incumbent.inCatVision && contender.inCatVision) {
            return false;
        }


        if (incumbent.inCatPath && !contender.inCatPath) {
            return true;
        }
        if (!incumbent.inCatPath && contender.inCatPath) {
            return false;
        }

        if (incumbent.canBeRatnapped && !contender.canBeRatnapped) {
            return true;
        }
        else if (!incumbent.canBeRatnapped && contender.canBeRatnapped) {
            return false;
        }

        if (incumbent.enemyDPS > contender.enemyDPS) {
            return true;
        }
        else if (incumbent.enemyDPS < contender.enemyDPS) {
            return false;
        }

        //try to move closer?
        if (incumbent.minDistanceToCat > contender.minDistanceToCat) {
            return true;
        }
        else if (incumbent.minDistanceToCat < contender.minDistanceToCat) {
            return false;
        }

        return RobotPlayer.rng.nextDouble() > 0.5;

    }

    //instantiates and fills in the micro array for a given turn
    void populateMicroArray() throws GameActionException {
        microArray = new catMicroInfo[9];

        MapLocation curLoc = rc.getLocation();
        int x = curLoc.x;
        int y = curLoc.y;


        int index = 1;
        //fill each index with a corresponding space
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                MapLocation microLoc = new MapLocation(x + dx, y + dy);
                microArray[index] = new catMicroInfo(microLoc);
                index++;
            }
        }
        microArray[0] = new catMicroInfo(rc.getLocation());
        //resense in the case of throwing
        enemyRobots = nearbyEnemies;
        nearbyEnemies = enemyRobots;
        allyRobots = nearbyAllies;
        RobotInfo[] allRobots = rc.senseNearbyRobots(-1);
        Team team = rc.getTeam();
        Team opp = rc.getTeam().opponent();
        FastLocSet catPath = null;

        //loop through allies and enemies we can see, updating each space as we go
        for (RobotInfo robot : allRobots) {
            if (robot.getType() == UnitType.CAT){
                catAdjacentSquares = Utilities.catAdjacentSquares(robot);
                catPath = Utilities.catPath(robot);
            }
            Team robotTeam = robot.getTeam();
            for (int i = 0; i < 9; i ++) {
                if (microArray[i].passable) {
                    if (robotTeam == team) {
                        microArray[i].updateAlly(robot);
                    }
                    else if (robotTeam == opp) {
                        microArray[i].updateEnemy(robot);
                    }
                    else {
                        microArray[i].updateCat(robot, catAdjacentSquares, catPath);
                    }
                }
            }
        }
    }

    public int getNumberPresenceSqueaks() {
        int round = rc.getRoundNum();
        Squeak[] squeaks = Communicator.getAllSqueaks();
        allyIDs.clear();
        for (Squeak squeak : squeaks) {
            if (squeak.squeakInfo instanceof PresenceSqueakInfo && round - squeak.round <= 1) {
                allyIDs.add(new MapLocation(squeak.senderID, 0));
            }
        }
        return allyIDs.size;
    }

    public static void attemptEnemyThenAllyPickup() throws GameActionException {
    for (RobotInfo enemy : nearbyEnemies) {
        if (rc.canCarryRat(enemy.location)) {
            rc.carryRat(enemy.location);
            return;
        }
    }
    for (RobotInfo ally : nearbyAllies) {
        if (rc.canCarryRat(ally.location)) {
            rc.carryRat(ally.location);
            return;
        }
    }
    }
    public static void attemptEnemyPickup() throws GameActionException {
    for (RobotInfo enemy : nearbyEnemies) {
        if (rc.canCarryRat(enemy.location)) {
            rc.carryRat(enemy.location);
            return;
        }
    }
    }

    //calculates an approximate distance from the middle of the cat to the location
    public static double calculateCatDist(RobotInfo cat, MapLocation loc) {
        double catX = cat.location.x + 0.5;
        double catY = cat.location.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

}
