package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.RobotPlayer;
import battlecode.common.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import bench_stroke.FastIterableLocSet;

import bench_stroke.DataStructures.FastMath;
import bench_stroke.DataStructures.FastLocSet;


import bench_stroke.Utilities;

import static bench_stroke.Utilities.attemptTrapTowardsEnemy;
import static bench_stroke.BabyRat.*;
import static bench_stroke.RatKing.inDistress;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.Utilities.attemptAttack;
import static bench_stroke.Utilities.attemptRatnap;
import static bench_stroke.Utilities.attemptAttackEnemy;
import static bench_stroke.Utilities.attemptThrow;
import static bench_stroke.Utilities.attemptTrap;
import static bench_stroke.Utilities.attemptTrapInFrontOfEnemy;
import static bench_stroke.Utilities.attemptTrapRevamped;
import bench_stroke.Communication.*;
import bench_stroke.Pathfinding.Pathfinding;
import bench_stroke.DirectionHealthInfo;


//micro idea: start from target, go backwards from there?

class microInfo {
    boolean passable;
    public MapLocation loc;
    public int minDistanceToEnemy;
    public boolean catAdjacent;
    public boolean canAttackRatKing;
    public boolean forward;
    public boolean canRatnap;
    public int knownAdjacentAllies;
    public boolean canBeRatnapped;
    public int distToAverageAlly;
    public int minDistToAlly;
    public boolean inCatVision;
    public int enemyVision;

    //public int minDistToRatKing;

    public MapLocation closestEnemy;

    public int enemies2;
    public int enemies8;
    public int enemies17;

    public int allies2;
    public int allies8;
    public int allies17;

    public boolean canBeRatnappedNoMove = false;

    public boolean ratTrap;

    public double minDistanceToCat;
    // public boolean catTrap;
    
    public boolean inCatPath;
    public boolean canKill;
    //public boolean towardsRatKing;

    //creates a micro Info tile, populating its information based on a map info tile
    public microInfo(MapLocation loc) throws GameActionException {
        //note that we want to consider our current square as a valid option, so it is alwasy "passable"
        passable =  currentLocation.equals(loc) || rc.canMove(currentLocation.directionTo(loc));
        if(!passable) {
            this.loc = loc;
            return;
        }

        forward = loc.equals(currentLocation.add(rc.getDirection())) || loc.equals(currentLocation);

        //consider trimming this for bytecode...
        // if (rc.canSenseLocation(loc)) {
        //     MapInfo tile = rc.senseMapInfo(loc);
        //     TrapType t = tile.getTrap();
        //     if (t == TrapType.CAT_TRAP) {
        //         catTrap = true;
        //         ratTrap = false;
        //     }
        //     else if (t == TrapType.RAT_TRAP) {
        //         ratTrap = true;
        //         catTrap = false;
        //     }
        //     else {
        //         ratTrap = false;
        //         catTrap = false;
        //     }
        // }
        // else {
        //     ratTrap = false;
        //     catTrap = false;
        // }

        //we don't need to populate info if the tile is not passable - waste of bytecodes
        this.loc = loc;
        knownAdjacentAllies = 0;
        minDistanceToEnemy = Integer.MAX_VALUE;
        catAdjacent = false;
        canAttackRatKing = false;
        canRatnap = false;
        canBeRatnapped = false;
        minDistToAlly = Integer.MAX_VALUE;
       // minDistToRatKing = loc.distanceSquaredTo(closestAllyRatKing.loc());
        inCatVision = false;
        enemyVision = 0;
        closestEnemy = null;
        inCatPath = false;
        canBeRatnappedNoMove = false;
        ratTrap = allyTraps.contains(loc);
        minDistanceToCat = Integer.MAX_VALUE;
       // towardsRatKing = false;

        enemies2= 0;
        enemies8 = 0;
        enemies17 = 0;

        allies2 = 0;
        allies8 = 0;
        allies17 = 0;

        canKill = false;
        
        distToAverageAlly = (averageAlly != null) ? loc.distanceSquaredTo(averageAlly) : Integer.MAX_VALUE;
    }
    //default constructor for a microInfo, setting passable to false so it will never be considered
    //used for spaces which are not on the map
    public microInfo() {
        passable = false;
    }

    //calculates an approximate distance from the middle of the cat to the location
    public double calculateCatDist(RobotInfo cat) {
        double catX = cat.location.x + 0.5;
        double catY = cat.location.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

    //calculates an approximate distance from the middle of the cat to the location
    public double calculateCatDist(MapLocation catLoc) {
        double catX = catLoc.x + 0.5;
        double catY = catLoc.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

    //UTILITY METHODS
    //update our understanding of this space based on this enemy
    public void updateEnemy(MapLocation location, int enemyHealth, Direction dir, boolean isRatKing) throws GameActionException {
        if (!passable) return;
        // rc.setIndicatorDot(location, 255, 0, 0);

        MapLocation enemyLoc = location;
        //boolean isFacing = isFacingLoc(enemyLoc, dir);
        boolean isFacing = location.isWithinDistanceSquared(loc, 20, dir, 90);

        int dist = loc.distanceSquaredTo(enemyLoc);
        if (dist < minDistanceToEnemy) {
            closestEnemy = enemyLoc;
            minDistanceToEnemy = dist;
        }
        if (dist <= 2) {
            enemies2++;
            enemies8++;
            enemies17++;
            if (enemyHealth < health || !isFacing) canRatnap = true;
            if (isFacing && (enemyHealth > health) && !isRatKing) {
                canBeRatnapped = true;
                canBeRatnappedNoMove = true;
            }
            else if (isFacing && dist == 1) {
                canBeRatnapped = true;
            }
            if (enemyHealth <= 10) {
                canKill = true;
            }
        }
        else if (dist <= 8){
            enemies8++;
            enemies17++;
            if (isFacing && (enemyHealth > health) && !isRatKing) {
                canBeRatnapped = true;
            }
        }

        else if (dist <= 17) {
            enemies17++;
        }

        if (isFacing || (isRatKing && dist <= 25)) enemyVision++;

        if (isRatKing && dist <= 8) {
            canAttackRatKing = true;
        }

        // if (isRatKing && dist < currentLocation.distanceSquaredTo(enemyLoc)) {
        //     towardsRatKing = true;
        // }
    }

    public void updateCat(RobotInfo cat, FastLocSet catPath) {
        if (!passable) return;
        double dist = calculateCatDist(cat);

        if (dist < minDistanceToCat) {
            minDistanceToCat = dist;
        }
        // if (dist <= 4) {
        //     catAdjacent = true;
        // } 

        if (cat.location.isWithinDistanceSquared(this.loc, 17, cat.direction, 180, true)) {
            inCatVision = true;
        }
        if (catPath.contains(loc)) {
          //  System.out.println(loc);
            inCatPath = true;
        }
    }

    public void updateCat(MapLocation catLoc, Direction catDir, FastLocSet catPath) {
        if (!passable) return;
        double dist = calculateCatDist(catLoc);

        if (dist < minDistanceToCat) {
            minDistanceToCat = dist;
        }
        // if (dist <= 4) {
        //     catAdjacent = true;
        // } 

        if (catLoc.isWithinDistanceSquared(this.loc, 17, catDir, 180, true)) {
            inCatVision = true;
        }
        if (catPath.contains(loc)) {
          //  System.out.println(loc);
            inCatPath = true;
        }
    }


    //update our understanding of this space based on this ally
    public void updateAlly(MapLocation allyLoc) throws GameActionException {
        if (!passable) return;
        // rc.setIndicatorDot(allyLoc, 0, 255, 0);
        int dist = allyLoc.distanceSquaredTo(loc);
        if (dist <= 2) {
            knownAdjacentAllies++;
            allies2++;
            allies8++;
            allies17++;
        }
        else if (dist <= 8) {
            allies8++;
            allies17++;
        }
        else if (dist <= 17) {
            allies17++;
        }
        if (dist <= minDistToAlly) minDistToAlly = dist;
    }

    //returns whether a robot at location loc facing direction dir is facing the current space
    public boolean isFacingLoc(MapLocation other, Direction dir) {
        return other.directionTo(loc) == dir || other.directionTo(loc) == dir.rotateLeft() || other.directionTo(loc) == dir.rotateRight();
    }

    //returns whether a robot at location loc facing direction dir is facing space other
    public boolean isFacingOutwardsLoc(MapLocation other, Direction dir) {
        return loc.directionTo(other) == dir || loc.directionTo(other) == dir.rotateLeft() || loc.directionTo(other) == dir.rotateRight();
    }

    public int safe() {
        // boolean trapProtected = (closestEnemy == null || (minDistanceToEnemy > 2 && minDistanceToEnemy <= 5 && allyTraps.contains(loc.add(loc.directionTo(closestEnemy)))));
        // if (trapProtected) System.out.println(loc);

        if (canBeRatnappedNoMove && !((canRatnap || canKill) && enemies2 == 1) && enemyVision > 0) return 0;
        if (inCatPath) return 1;
        if (canBeRatnapped && enemyVision > 0 && !((canRatnap || canKill) && enemies8 == 1)) return 2;
        if (enemies2 > 1 && allies2 <=1) return 3;
        if (minDistanceToCat < 3) return 4;
        if (enemies8 > 1 && allies8 == 0) return 5;
        return 6;
    }

    public int inRange() {
        if (canRatnap || canAttackRatKing) return 4;
        if (enemies2 == 1 || canKill) return 3;
        if (enemies2 > 0) return 2;
        if (enemies8 > 0) return 1;
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        microInfo other = (microInfo) obj;
        if(loc == null) return false;
        return loc.equals(other.loc);
    }

    //returns whether this microInfo is better than other microInfo
    public boolean isBetter(microInfo other, boolean actionReady, boolean bold, boolean inRetreat) {
        if (!passable) return false;

        //round 95
        if (rc.getID() == 13613) {
            System.out.println(loc.toString() + " : " + safe() + " : " + inRange());
            System.out.println(other.loc.toString() + " : " + other.safe() + " : " + other.inRange());
        }

        if (safe() > other.safe()) return true;
        else if (safe() < other.safe()) return false;

        if (actionReady && inRange() > other.inRange()) return true;
        else if (actionReady && inRange() < other.inRange()) return false;

        if (ratTrap && !other.ratTrap) return true;
        else if (!ratTrap && other.ratTrap) return false;


        //specific case to force us to kite rat kings
        //we are treating canAttackRatKing as canbeAttackedByRatKing basically
        if (bold && !actionReady && allRobots.size - numberAllies == 1) {
            if (!canAttackRatKing && other.canAttackRatKing) return true;
            else if (canAttackRatKing && !other.canAttackRatKing) return false;
        }

        if (actionReady && !ratTrap && minDistanceToEnemy == 2 && other.minDistanceToEnemy == 1) return true;
        if (actionReady && !ratTrap && minDistanceToEnemy == 1 && other.minDistanceToEnemy == 2) return false;

        if (bold && minDistanceToEnemy < other.minDistanceToEnemy) return true;
        else if (bold && minDistanceToEnemy > other.minDistanceToEnemy) return false;

        if (inRetreat && minDistanceToEnemy > other.minDistanceToEnemy) return true;
        else if (inRetreat && minDistanceToEnemy < other.minDistanceToEnemy) return false;

        if (enemies8 < other.enemies8) return true;
        else if (enemies8 > other.enemies8) return false;

        if (minDistanceToEnemy < other.minDistanceToEnemy) return true;
        else if (minDistanceToEnemy > other.minDistanceToEnemy) return false;

        // if (minDistToRatKing < other.minDistToRatKing) return true;
        // else if (minDistToRatKing > other.minDistToRatKing) return false;


        if (forward && !other.forward) return true;
        if (!forward && other.forward) return false;

        return this.loc.equals(rc.getLocation());
    }

}
public class BRMicro implements Behavior {

    //instance variables
    private microInfo[] microArray = null;
    private RobotInfo[] enemyRobots;
    private RobotInfo[] allyRobots;

    private boolean thrown = false; //used to encapsulate anytime we would need to resense enemies

    private int enemyHealth = 0;

    private MapLocation averageEnemy;

    private boolean inRetreat;

    private boolean bold;

  //  private FastIterableLocSet allyIDs = new FastIterableLocSet();

    //Singleton Stuff
    private static BRMicro instance;
    private BRMicro() {}
    public static BRMicro getInstance() {
        if(instance == null) {
            instance = new BRMicro();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        thrown = false;
        // //sent here because we heard allies needed help
        // if (nearbyEnemies.length == 0) {
        //     if (rc.canTurn() && !averageAlly.equals(currentLocation)) rc.turn(currentLocation.directionTo(averageAlly));
        //     nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        //     if (nearbyEnemies.length == 0) {
        //         Pathfinding.attemptMove(averageAlly, false);
        //     }
        // }

        inRetreat = false;
        averageEnemy = null;

        if (nearestSeenEnemy == null && nearestEnemy != null && !rc.isMovementReady()) {
            if (rc.canTurn() && rc.getDirection() != currentLocation.directionTo(nearestEnemy)) {
                rc.turn(currentLocation.directionTo(nearestEnemy));
                updateNearestSeenEnemy();
            }
        }
        if (nearestSeenEnemy == null && nearestEnemy == null) {
            nearestEnemy = Communicator.getAverageEnemyFromSharedArray();
            if (rc.canTurn() && rc.getDirection() != currentLocation.directionTo(nearestEnemy) && currentLocation.directionTo(nearestEnemy) != null) {
                rc.turn(currentLocation.directionTo(nearestEnemy));
                updateNearestSeenEnemy();
            }

        }
        

        if (!(turnsCarrying > 5 || nearbyCats.length > 0) && rc.isActionReady() && rc.getCarrying() != null) {
            if (attemptThrow(turnsCarrying, health)) {
               // updateNearestSeenEnemy();
                thrown = true;
            }
        }

        else if (rc.isActionReady())  {
            if (attemptRatnap()) {
                thrown = true;
            }
        }
        if (rc.isActionReady() && nearestSeenEnemy != null) {
            if (attemptAttackEnemy(nearestSeenEnemy)) thrown = true;
        }
        microInfo bestMicro = null;
        if (rc.isMovementReady()) {
            bestMicro = microMovement();           
        }
        else if (rc.canTurn()) {
            updateNearestSeenEnemy();
            if (nearestEnemy != null && nearestSeenEnemy != null) {
                int nEdist = currentLocation.distanceSquaredTo(nearestEnemy);
                int nSEdist = currentLocation.distanceSquaredTo(nearestSeenEnemy.location);
                if (Math.min(nEdist, nSEdist) <= 4) {
                    if (nEdist < nSEdist) {
                        Direction dir = rc.getLocation().directionTo(nearestEnemy);
                        if (dir != null && dir != Direction.CENTER && dir != rc.getDirection()) {
                            rc.turn(dir);
                         //   System.out.println("turn!");
                        }
                    }
                    else {
                        Direction dir = rc.getLocation().directionTo(nearestSeenEnemy.location);
                        if (dir != null && dir != Direction.CENTER && dir != rc.getDirection()) {
                            rc.turn(dir);
                        }
                    }
                }
                else {
                    Direction dir = currentLocation.directionTo(averageEnemy);
                    if (dir != null && dir != rc.getDirection() && dir != Direction.CENTER) {
                        rc.turn(dir);
                    }
                }
            }
        }
        if (rc.isActionReady() && rc.getCarrying() != null) {
            attemptThrow(turnsCarrying, health);
        }
        else if (rc.isActionReady()) {
            attemptRatnap();
        }
        if (rc.isActionReady() && (bestMicro == null || bestMicro.minDistanceToEnemy > 2 && bestMicro.minDistanceToEnemy <= 5) && (rc.getRawCheese() >= 20 || rc.getAllCheese() > 600) && !bold) {
            boolean strict = (rc.getRawCheese() < 20 && rc.getNumberRatTraps() <= 20);
           // boolean trapped = attemptTrapRevamped(nearbyEnemies, threshold);
           boolean trapped = false;
           if (bestMicro != null && bestMicro.enemyVision > 0) {
                trapped = attemptTrapTowardsEnemy(bestMicro.closestEnemy, strict);

           }
           else if (bestMicro == null && nearestSeenEnemy != null) {
            int dist = currentLocation.distanceSquaredTo(nearestSeenEnemy.location);
            if (dist > 2 && dist <= 5) {
                trapped = attemptTrapTowardsEnemy(nearestSeenEnemy.location, strict);
            }
           }

            // if (!trapped && rc.getDirt() > 10 && nearestSeenEnemy != null && inRetreat) {
            //     Direction dirToNearest = rc.getLocation().directionTo(nearestSeenEnemy.location);
            //     boolean placed = Utilities.tryBuild(dirToNearest, true);
            //     if (placed) System.out.println("placed dirt!");
            // }

        }
        if (rc.isActionReady() && nearestSeenEnemy != null) {
            if (attemptAttackEnemy(nearestSeenEnemy)) thrown = true;
        }
        //if (Clock.getBytecodesLeft() > 500) updateNearestSeenEnemy();
        if (nearestSeenEnemy != null && allRobots.contains(nearestSeenEnemy.location)) {
            Communicator.sendSqueak(new PresenceSqueakInfo(rc.getHealth(), nearestSeenEnemy.location, nearestSeenEnemy.direction, nearestSeenEnemy.health));
        }
        // else if (Clock.getBytecodesLeft() > 1000) {
        //     updateNearestSeenEnemy();
        //     if (nearestSeenEnemy != null) {
        //         Communicator.sendSqueak(new PresenceSqueakInfo(rc.getHealth(), nearestSeenEnemy.location, nearestSeenEnemy.direction, nearestSeenEnemy.health));
        //         System.out.println("squeak!: " + nearestSeenEnemy.location);
        //     }
        // }
    }

    public void updateNearestSeenEnemy() throws GameActionException {
        RobotInfo oldNearestSeenEnemy = nearestSeenEnemy;
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        nearbyEnemies = enemies;
        enemyRobots = enemies;
        int smallestDist = Integer.MAX_VALUE;
        RobotInfo closest = null;
        MapLocation curLoc = rc.getLocation();
        int x = 0;
        int y = 0;
        int count = 0;
        for (RobotInfo enemy : enemies) {
            int dist = curLoc.distanceSquaredTo(enemy.location);
            if (dist < smallestDist) {
                closest = enemy;
                smallestDist = dist;
            }
            if (dist <= 8) {
                x += enemy.location.x;
                y += enemy.location.y;
                count++;
            }
        }
        nearestSeenEnemy = closest;
        if (count > 0)
            averageEnemy = new MapLocation(x / count, y / count);
        else if (nearestSeenEnemy != null)
            averageEnemy = nearestSeenEnemy.location;
        if (nearestSeenEnemy == null) nearestSeenEnemy = oldNearestSeenEnemy;
    }


    //attempts to move to an adjacent space
    public microInfo microMovement() throws GameActionException {

        populateMicroArray();

        microInfo bestMicro = microArray[0];

        //encodes decision heuristic (currently a decision tree)
        inRetreat = false;

        bold = false;
        boolean actionReady = rc.isActionReady();
        int diff = (numberAllies) - (allRobots.size - numberAllies);
        double healthRatio = (double) allyHealth / (double) enemyHealth;

        if (rc.getRawCheese() >= 40 || diff <= -2) {
            inRetreat = true;
        }
        else if (diff >= 2 || healthRatio > 2 || (seeEnemyRatKing && allRobots.size - numberAllies == 1)) {
            bold = true;
        }
        if (inDistressRatKing != null && rc.getLocation().distanceSquaredTo(inDistressRatKing.loc()) <= ANSWER_DISTRESS_DIST_THRESHOLD) {
            bold = true;
            inRetreat = false;
        }

        if (microArray[1].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[1];
        if (microArray[2].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[2];
        if (microArray[3].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[3];
        if (microArray[4].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[4];
        if (microArray[5].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[5];
        if (microArray[6].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[6];
        if (microArray[7].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[7];
        if (microArray[8].isBetter(bestMicro, actionReady, bold, inRetreat)) bestMicro = microArray[8];

        MapLocation curLoc = rc.getLocation();
        Direction curDir = rc.getDirection();
        // Direction dirToAverage = null;

        //round 20

        if(bestMicro.passable && !bestMicro.loc.equals(rc.getLocation())) {
            Direction toMicro = curLoc.directionTo(bestMicro.loc);
            if(rc.canMove(toMicro)) {
                if (rc.canTurn() && bestMicro.closestEnemy != null) {
                    Direction toClosest = bestMicro.loc.directionTo(bestMicro.closestEnemy);
                    if (toMicro == toClosest && toMicro != curDir) rc.turn(toMicro);
                }
                if (bestMicro.minDistanceToEnemy > 2 && bestMicro.minDistanceToEnemy <= 5 && rc.isActionReady() && !bold) {
                    if (rc.canPlaceRatTrap(bestMicro.loc) && (rc.getRawCheese() >= 20 || rc.getAllCheese() > 800) && diff <= 0 && bestMicro.canBeRatnapped) {
                        rc.placeRatTrap(bestMicro.loc);
                    }
                }
                rc.move(toMicro);
                currentLocation = bestMicro.loc;
                if (rc.canTurn() && bestMicro.closestEnemy != null && bestMicro.minDistanceToEnemy <= 4 && bestMicro.enemyVision > 0) {
                    Direction toClosest = rc.getLocation().directionTo(bestMicro.closestEnemy);
                    if (toClosest != Direction.CENTER && toClosest != curDir) rc.turn(toClosest);
                }
            }
        }
        //round 14, 14022
        updateNearestSeenEnemy();
        if (nearestSeenEnemy != null && rc.canTurn() && currentLocation.distanceSquaredTo(nearestSeenEnemy.location) <= 4) {
            if (rc.getLocation().directionTo(nearestSeenEnemy.location) != Direction.CENTER && rc.getLocation().directionTo(nearestSeenEnemy.location) != curDir) rc.turn(rc.getLocation().directionTo(nearestSeenEnemy.location));
        }
        else if (rc.canTurn() && averageEnemy != null) {
            Direction dir = rc.getDirection();
            Direction toTurn = currentLocation.directionTo(averageEnemy);
            if (toTurn != Direction.CENTER && toTurn != dir && toTurn != null) {
                rc.turn(toTurn);
            }
        }
        
        return bestMicro;

    }

    // Apply an update to every micro slot for an ally location
    private void applyAllyAll(MapLocation loc) throws GameActionException {
        microArray[0].updateAlly(loc);
        microArray[1].updateAlly(loc);
        microArray[2].updateAlly(loc);
        microArray[3].updateAlly(loc);
        microArray[4].updateAlly(loc);
        microArray[5].updateAlly(loc);
        microArray[6].updateAlly(loc);
        microArray[7].updateAlly(loc);
        microArray[8].updateAlly(loc);
    }

    // Apply an update to every micro slot for an enemy location
    private void applyEnemyAll(MapLocation loc, DirectionHealthInfo info) throws GameActionException {
        enemyHealth += info.health;
        microArray[0].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[1].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[2].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[3].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[4].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[5].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[6].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[7].updateEnemy(loc, info.health, info.dir, info.ratKing);
        microArray[8].updateEnemy(loc, info.health, info.dir, info.ratKing);
    }

    //instantiates and fills in the micro array for a given turn
    void populateMicroArray() throws GameActionException {
        microArray = new microInfo[9];
        enemyHealth = 0;
        MapLocation curLoc = rc.getLocation();
        int x = curLoc.x;
        int y = curLoc.y;


        int index = 1;
        //fill each index with a corresponding space
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                MapLocation microLoc = new MapLocation(x + dx, y + dy);
                microArray[index] = new microInfo(microLoc);
                index++;
            }
        }
        // FastLocSet processed = new FastLocSet();
        microArray[0] = new microInfo(rc.getLocation());
        allyRobots = nearbyAllies;

        allRobots.updateIterable();
        // Original allRobots iteration (editable for re-unrolling):
        // for (int j = 0; j < allRobots.size; j++) {
        //     MapLocation loc = allRobots.getKey(j);
        //     DirectionHealthInfo info = decodeDirectionAndHealth(allRobots.getValue(j));
        //     if (info.dir == Direction.CENTER) {
        //         for (int k = 0; k < 9; k++) microArray[k].updateAlly(loc);
        //     } else {
        //         for (int k = 0; k < 9; k++) microArray[k].updateEnemy(loc, info.health, info.dir, info.ratKing);
        //     }
        // }
        switch (allRobots.size) {
            case 4 -> handleCase4();
            case 5 -> handleCase5();
            case 6 -> handleCase6();
            case 7 -> handleCase7();
            case 8 -> handleCase8();
            case 9 -> handleCase9();
            case 10 -> handleCase10();
            case 11 -> handleCase11();
            case 12 -> handleCase12();
            case 13 -> handleCase13();
            case 14 -> handleCase14();
            case 15 -> handleCase15();
            case 16 -> handleCase16();
            case 17 -> handleCase17();
            case 18 -> handleCase18();
            case 19 -> handleCase19();
            case 20 -> handleCase20();
            default -> handleDefaultCase();
        }
        for (RobotInfo cat : nearbyCats) {
            FastLocSet catPath = Utilities.catPath(cat);
            microArray[0].updateCat(cat, catPath);
            microArray[1].updateCat(cat, catPath);
            microArray[2].updateCat(cat, catPath);
            microArray[3].updateCat(cat, catPath);
            microArray[4].updateCat(cat, catPath);
            microArray[5].updateCat(cat, catPath);
            microArray[6].updateCat(cat, catPath);
            microArray[7].updateCat(cat, catPath);
            microArray[8].updateCat(cat, catPath);
        }
        if (nearbyCats.length == 0 && catSqueak != null) {
            NearbyCatSqueakInfo squeakInfo = (NearbyCatSqueakInfo) catSqueak.squeakInfo;
            FastLocSet catPath = Utilities.catPath(squeakInfo.location(), squeakInfo.direction());
            microArray[0].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[1].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[2].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[3].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[4].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[5].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[6].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[7].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
            microArray[8].updateCat(squeakInfo.location(), squeakInfo.direction(), catPath);
        }
        else if (nearbyCats.length == 0 && mostRecentCat != null) {
            RobotInfo cat = mostRecentCat;
            FastLocSet catPath = Utilities.catPath(cat);
            microArray[0].updateCat(cat, catPath);
            microArray[1].updateCat(cat, catPath);
            microArray[2].updateCat(cat, catPath);
            microArray[3].updateCat(cat, catPath);
            microArray[4].updateCat(cat, catPath);
            microArray[5].updateCat(cat, catPath);
            microArray[6].updateCat(cat, catPath);
            microArray[7].updateCat(cat, catPath);
            microArray[8].updateCat(cat, catPath);
        }

    }
    private void handleCase4() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
    }

    private void handleCase5() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
    }

    private void handleCase6() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
    }

    private void handleCase7() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
    }

    private void handleCase8() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
    }

    private void handleCase9() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
    }

    private void handleCase10() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
    }

    private void handleCase11() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
    }

    private void handleCase12() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
    }

    private void handleCase13() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
    }

    private void handleCase14() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
    }

    private void handleCase15() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
        MapLocation loc14 = allRobots.getKey(14);
        DirectionHealthInfo info14 = decodeDirectionAndHealth(allRobots.getValue(14));
        if (info14.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc14);
            microArray[1].updateAlly(loc14);
            microArray[2].updateAlly(loc14);
            microArray[3].updateAlly(loc14);
            microArray[4].updateAlly(loc14);
            microArray[5].updateAlly(loc14);
            microArray[6].updateAlly(loc14);
            microArray[7].updateAlly(loc14);
            microArray[8].updateAlly(loc14);
        } else {
            microArray[0].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[1].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[2].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[3].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[4].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[5].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[6].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[7].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[8].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            enemyHealth += info14.health;
        }
    }

    private void handleCase16() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
        MapLocation loc14 = allRobots.getKey(14);
        DirectionHealthInfo info14 = decodeDirectionAndHealth(allRobots.getValue(14));
        if (info14.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc14);
            microArray[1].updateAlly(loc14);
            microArray[2].updateAlly(loc14);
            microArray[3].updateAlly(loc14);
            microArray[4].updateAlly(loc14);
            microArray[5].updateAlly(loc14);
            microArray[6].updateAlly(loc14);
            microArray[7].updateAlly(loc14);
            microArray[8].updateAlly(loc14);
        } else {
            microArray[0].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[1].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[2].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[3].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[4].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[5].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[6].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[7].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[8].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            enemyHealth += info14.health;
        }
        MapLocation loc15 = allRobots.getKey(15);
        DirectionHealthInfo info15 = decodeDirectionAndHealth(allRobots.getValue(15));
        if (info15.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc15);
            microArray[1].updateAlly(loc15);
            microArray[2].updateAlly(loc15);
            microArray[3].updateAlly(loc15);
            microArray[4].updateAlly(loc15);
            microArray[5].updateAlly(loc15);
            microArray[6].updateAlly(loc15);
            microArray[7].updateAlly(loc15);
            microArray[8].updateAlly(loc15);
        } else {
            microArray[0].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[1].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[2].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[3].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[4].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[5].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[6].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[7].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[8].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            enemyHealth += info15.health;
        }
    }

    private void handleCase17() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
        MapLocation loc14 = allRobots.getKey(14);
        DirectionHealthInfo info14 = decodeDirectionAndHealth(allRobots.getValue(14));
        if (info14.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc14);
            microArray[1].updateAlly(loc14);
            microArray[2].updateAlly(loc14);
            microArray[3].updateAlly(loc14);
            microArray[4].updateAlly(loc14);
            microArray[5].updateAlly(loc14);
            microArray[6].updateAlly(loc14);
            microArray[7].updateAlly(loc14);
            microArray[8].updateAlly(loc14);
        } else {
            microArray[0].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[1].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[2].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[3].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[4].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[5].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[6].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[7].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[8].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            enemyHealth += info14.health;
        }
        MapLocation loc15 = allRobots.getKey(15);
        DirectionHealthInfo info15 = decodeDirectionAndHealth(allRobots.getValue(15));
        if (info15.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc15);
            microArray[1].updateAlly(loc15);
            microArray[2].updateAlly(loc15);
            microArray[3].updateAlly(loc15);
            microArray[4].updateAlly(loc15);
            microArray[5].updateAlly(loc15);
            microArray[6].updateAlly(loc15);
            microArray[7].updateAlly(loc15);
            microArray[8].updateAlly(loc15);
        } else {
            microArray[0].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[1].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[2].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[3].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[4].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[5].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[6].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[7].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[8].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            enemyHealth += info15.health;
        }
        MapLocation loc16 = allRobots.getKey(16);
        DirectionHealthInfo info16 = decodeDirectionAndHealth(allRobots.getValue(16));
        if (info16.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc16);
            microArray[1].updateAlly(loc16);
            microArray[2].updateAlly(loc16);
            microArray[3].updateAlly(loc16);
            microArray[4].updateAlly(loc16);
            microArray[5].updateAlly(loc16);
            microArray[6].updateAlly(loc16);
            microArray[7].updateAlly(loc16);
            microArray[8].updateAlly(loc16);
        } else {
            microArray[0].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[1].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[2].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[3].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[4].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[5].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[6].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[7].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[8].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            enemyHealth += info16.health;
        }
    }

    private void handleCase18() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
        MapLocation loc14 = allRobots.getKey(14);
        DirectionHealthInfo info14 = decodeDirectionAndHealth(allRobots.getValue(14));
        if (info14.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc14);
            microArray[1].updateAlly(loc14);
            microArray[2].updateAlly(loc14);
            microArray[3].updateAlly(loc14);
            microArray[4].updateAlly(loc14);
            microArray[5].updateAlly(loc14);
            microArray[6].updateAlly(loc14);
            microArray[7].updateAlly(loc14);
            microArray[8].updateAlly(loc14);
        } else {
            microArray[0].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[1].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[2].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[3].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[4].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[5].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[6].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[7].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[8].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            enemyHealth += info14.health;
        }
        MapLocation loc15 = allRobots.getKey(15);
        DirectionHealthInfo info15 = decodeDirectionAndHealth(allRobots.getValue(15));
        if (info15.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc15);
            microArray[1].updateAlly(loc15);
            microArray[2].updateAlly(loc15);
            microArray[3].updateAlly(loc15);
            microArray[4].updateAlly(loc15);
            microArray[5].updateAlly(loc15);
            microArray[6].updateAlly(loc15);
            microArray[7].updateAlly(loc15);
            microArray[8].updateAlly(loc15);
        } else {
            microArray[0].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[1].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[2].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[3].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[4].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[5].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[6].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[7].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[8].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            enemyHealth += info15.health;
        }
        MapLocation loc16 = allRobots.getKey(16);
        DirectionHealthInfo info16 = decodeDirectionAndHealth(allRobots.getValue(16));
        if (info16.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc16);
            microArray[1].updateAlly(loc16);
            microArray[2].updateAlly(loc16);
            microArray[3].updateAlly(loc16);
            microArray[4].updateAlly(loc16);
            microArray[5].updateAlly(loc16);
            microArray[6].updateAlly(loc16);
            microArray[7].updateAlly(loc16);
            microArray[8].updateAlly(loc16);
        } else {
            microArray[0].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[1].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[2].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[3].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[4].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[5].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[6].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[7].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[8].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            enemyHealth += info16.health;
        }
        MapLocation loc17 = allRobots.getKey(17);
        DirectionHealthInfo info17 = decodeDirectionAndHealth(allRobots.getValue(17));
        if (info17.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc17);
            microArray[1].updateAlly(loc17);
            microArray[2].updateAlly(loc17);
            microArray[3].updateAlly(loc17);
            microArray[4].updateAlly(loc17);
            microArray[5].updateAlly(loc17);
            microArray[6].updateAlly(loc17);
            microArray[7].updateAlly(loc17);
            microArray[8].updateAlly(loc17);
        } else {
            microArray[0].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[1].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[2].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[3].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[4].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[5].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[6].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[7].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[8].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            enemyHealth += info17.health;
        }
    }

    private void handleCase19() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
        MapLocation loc14 = allRobots.getKey(14);
        DirectionHealthInfo info14 = decodeDirectionAndHealth(allRobots.getValue(14));
        if (info14.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc14);
            microArray[1].updateAlly(loc14);
            microArray[2].updateAlly(loc14);
            microArray[3].updateAlly(loc14);
            microArray[4].updateAlly(loc14);
            microArray[5].updateAlly(loc14);
            microArray[6].updateAlly(loc14);
            microArray[7].updateAlly(loc14);
            microArray[8].updateAlly(loc14);
        } else {
            microArray[0].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[1].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[2].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[3].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[4].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[5].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[6].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[7].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[8].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            enemyHealth += info14.health;
        }
        MapLocation loc15 = allRobots.getKey(15);
        DirectionHealthInfo info15 = decodeDirectionAndHealth(allRobots.getValue(15));
        if (info15.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc15);
            microArray[1].updateAlly(loc15);
            microArray[2].updateAlly(loc15);
            microArray[3].updateAlly(loc15);
            microArray[4].updateAlly(loc15);
            microArray[5].updateAlly(loc15);
            microArray[6].updateAlly(loc15);
            microArray[7].updateAlly(loc15);
            microArray[8].updateAlly(loc15);
        } else {
            microArray[0].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[1].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[2].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[3].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[4].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[5].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[6].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[7].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[8].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            enemyHealth += info15.health;
        }
        MapLocation loc16 = allRobots.getKey(16);
        DirectionHealthInfo info16 = decodeDirectionAndHealth(allRobots.getValue(16));
        if (info16.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc16);
            microArray[1].updateAlly(loc16);
            microArray[2].updateAlly(loc16);
            microArray[3].updateAlly(loc16);
            microArray[4].updateAlly(loc16);
            microArray[5].updateAlly(loc16);
            microArray[6].updateAlly(loc16);
            microArray[7].updateAlly(loc16);
            microArray[8].updateAlly(loc16);
        } else {
            microArray[0].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[1].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[2].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[3].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[4].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[5].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[6].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[7].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[8].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            enemyHealth += info16.health;
        }
        MapLocation loc17 = allRobots.getKey(17);
        DirectionHealthInfo info17 = decodeDirectionAndHealth(allRobots.getValue(17));
        if (info17.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc17);
            microArray[1].updateAlly(loc17);
            microArray[2].updateAlly(loc17);
            microArray[3].updateAlly(loc17);
            microArray[4].updateAlly(loc17);
            microArray[5].updateAlly(loc17);
            microArray[6].updateAlly(loc17);
            microArray[7].updateAlly(loc17);
            microArray[8].updateAlly(loc17);
        } else {
            microArray[0].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[1].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[2].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[3].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[4].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[5].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[6].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[7].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[8].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            enemyHealth += info17.health;
        }
        MapLocation loc18 = allRobots.getKey(18);
        DirectionHealthInfo info18 = decodeDirectionAndHealth(allRobots.getValue(18));
        if (info18.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc18);
            microArray[1].updateAlly(loc18);
            microArray[2].updateAlly(loc18);
            microArray[3].updateAlly(loc18);
            microArray[4].updateAlly(loc18);
            microArray[5].updateAlly(loc18);
            microArray[6].updateAlly(loc18);
            microArray[7].updateAlly(loc18);
            microArray[8].updateAlly(loc18);
        } else {
            microArray[0].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[1].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[2].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[3].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[4].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[5].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[6].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[7].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[8].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            enemyHealth += info18.health;
        }
    }

    private void handleCase20() throws GameActionException {
        MapLocation loc0 = allRobots.getKey(0);
        DirectionHealthInfo info0 = decodeDirectionAndHealth(allRobots.getValue(0));
        if (info0.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc0);
            microArray[1].updateAlly(loc0);
            microArray[2].updateAlly(loc0);
            microArray[3].updateAlly(loc0);
            microArray[4].updateAlly(loc0);
            microArray[5].updateAlly(loc0);
            microArray[6].updateAlly(loc0);
            microArray[7].updateAlly(loc0);
            microArray[8].updateAlly(loc0);
        } else {
            microArray[0].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[1].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[2].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[3].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[4].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[5].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[6].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[7].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            microArray[8].updateEnemy(loc0, info0.health, info0.dir, info0.ratKing);
            enemyHealth += info0.health;
        }
        MapLocation loc1 = allRobots.getKey(1);
        DirectionHealthInfo info1 = decodeDirectionAndHealth(allRobots.getValue(1));
        if (info1.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc1);
            microArray[1].updateAlly(loc1);
            microArray[2].updateAlly(loc1);
            microArray[3].updateAlly(loc1);
            microArray[4].updateAlly(loc1);
            microArray[5].updateAlly(loc1);
            microArray[6].updateAlly(loc1);
            microArray[7].updateAlly(loc1);
            microArray[8].updateAlly(loc1);
        } else {
            microArray[0].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[1].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[2].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[3].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[4].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[5].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[6].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[7].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            microArray[8].updateEnemy(loc1, info1.health, info1.dir, info1.ratKing);
            enemyHealth += info1.health;
        }
        MapLocation loc2 = allRobots.getKey(2);
        DirectionHealthInfo info2 = decodeDirectionAndHealth(allRobots.getValue(2));
        if (info2.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc2);
            microArray[1].updateAlly(loc2);
            microArray[2].updateAlly(loc2);
            microArray[3].updateAlly(loc2);
            microArray[4].updateAlly(loc2);
            microArray[5].updateAlly(loc2);
            microArray[6].updateAlly(loc2);
            microArray[7].updateAlly(loc2);
            microArray[8].updateAlly(loc2);
        } else {
            microArray[0].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[1].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[2].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[3].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[4].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[5].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[6].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[7].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            microArray[8].updateEnemy(loc2, info2.health, info2.dir, info2.ratKing);
            enemyHealth += info2.health;
        }
        MapLocation loc3 = allRobots.getKey(3);
        DirectionHealthInfo info3 = decodeDirectionAndHealth(allRobots.getValue(3));
        if (info3.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc3);
            microArray[1].updateAlly(loc3);
            microArray[2].updateAlly(loc3);
            microArray[3].updateAlly(loc3);
            microArray[4].updateAlly(loc3);
            microArray[5].updateAlly(loc3);
            microArray[6].updateAlly(loc3);
            microArray[7].updateAlly(loc3);
            microArray[8].updateAlly(loc3);
        } else {
            microArray[0].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[1].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[2].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[3].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[4].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[5].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[6].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[7].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            microArray[8].updateEnemy(loc3, info3.health, info3.dir, info3.ratKing);
            enemyHealth += info3.health;
        }
        MapLocation loc4 = allRobots.getKey(4);
        DirectionHealthInfo info4 = decodeDirectionAndHealth(allRobots.getValue(4));
        if (info4.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc4);
            microArray[1].updateAlly(loc4);
            microArray[2].updateAlly(loc4);
            microArray[3].updateAlly(loc4);
            microArray[4].updateAlly(loc4);
            microArray[5].updateAlly(loc4);
            microArray[6].updateAlly(loc4);
            microArray[7].updateAlly(loc4);
            microArray[8].updateAlly(loc4);
        } else {
            microArray[0].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[1].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[2].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[3].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[4].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[5].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[6].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[7].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            microArray[8].updateEnemy(loc4, info4.health, info4.dir, info4.ratKing);
            enemyHealth += info4.health;
        }
        MapLocation loc5 = allRobots.getKey(5);
        DirectionHealthInfo info5 = decodeDirectionAndHealth(allRobots.getValue(5));
        if (info5.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc5);
            microArray[1].updateAlly(loc5);
            microArray[2].updateAlly(loc5);
            microArray[3].updateAlly(loc5);
            microArray[4].updateAlly(loc5);
            microArray[5].updateAlly(loc5);
            microArray[6].updateAlly(loc5);
            microArray[7].updateAlly(loc5);
            microArray[8].updateAlly(loc5);
        } else {
            microArray[0].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[1].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[2].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[3].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[4].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[5].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[6].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[7].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            microArray[8].updateEnemy(loc5, info5.health, info5.dir, info5.ratKing);
            enemyHealth += info5.health;
        }
        MapLocation loc6 = allRobots.getKey(6);
        DirectionHealthInfo info6 = decodeDirectionAndHealth(allRobots.getValue(6));
        if (info6.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc6);
            microArray[1].updateAlly(loc6);
            microArray[2].updateAlly(loc6);
            microArray[3].updateAlly(loc6);
            microArray[4].updateAlly(loc6);
            microArray[5].updateAlly(loc6);
            microArray[6].updateAlly(loc6);
            microArray[7].updateAlly(loc6);
            microArray[8].updateAlly(loc6);
        } else {
            microArray[0].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[1].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[2].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[3].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[4].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[5].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[6].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[7].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            microArray[8].updateEnemy(loc6, info6.health, info6.dir, info6.ratKing);
            enemyHealth += info6.health;
        }
        MapLocation loc7 = allRobots.getKey(7);
        DirectionHealthInfo info7 = decodeDirectionAndHealth(allRobots.getValue(7));
        if (info7.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc7);
            microArray[1].updateAlly(loc7);
            microArray[2].updateAlly(loc7);
            microArray[3].updateAlly(loc7);
            microArray[4].updateAlly(loc7);
            microArray[5].updateAlly(loc7);
            microArray[6].updateAlly(loc7);
            microArray[7].updateAlly(loc7);
            microArray[8].updateAlly(loc7);
        } else {
            microArray[0].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[1].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[2].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[3].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[4].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[5].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[6].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[7].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            microArray[8].updateEnemy(loc7, info7.health, info7.dir, info7.ratKing);
            enemyHealth += info7.health;
        }
        MapLocation loc8 = allRobots.getKey(8);
        DirectionHealthInfo info8 = decodeDirectionAndHealth(allRobots.getValue(8));
        if (info8.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc8);
            microArray[1].updateAlly(loc8);
            microArray[2].updateAlly(loc8);
            microArray[3].updateAlly(loc8);
            microArray[4].updateAlly(loc8);
            microArray[5].updateAlly(loc8);
            microArray[6].updateAlly(loc8);
            microArray[7].updateAlly(loc8);
            microArray[8].updateAlly(loc8);
        } else {
            microArray[0].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[1].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[2].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[3].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[4].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[5].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[6].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[7].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            microArray[8].updateEnemy(loc8, info8.health, info8.dir, info8.ratKing);
            enemyHealth += info8.health;
        }
        MapLocation loc9 = allRobots.getKey(9);
        DirectionHealthInfo info9 = decodeDirectionAndHealth(allRobots.getValue(9));
        if (info9.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc9);
            microArray[1].updateAlly(loc9);
            microArray[2].updateAlly(loc9);
            microArray[3].updateAlly(loc9);
            microArray[4].updateAlly(loc9);
            microArray[5].updateAlly(loc9);
            microArray[6].updateAlly(loc9);
            microArray[7].updateAlly(loc9);
            microArray[8].updateAlly(loc9);
        } else {
            microArray[0].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[1].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[2].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[3].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[4].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[5].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[6].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[7].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            microArray[8].updateEnemy(loc9, info9.health, info9.dir, info9.ratKing);
            enemyHealth += info9.health;
        }
        MapLocation loc10 = allRobots.getKey(10);
        DirectionHealthInfo info10 = decodeDirectionAndHealth(allRobots.getValue(10));
        if (info10.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc10);
            microArray[1].updateAlly(loc10);
            microArray[2].updateAlly(loc10);
            microArray[3].updateAlly(loc10);
            microArray[4].updateAlly(loc10);
            microArray[5].updateAlly(loc10);
            microArray[6].updateAlly(loc10);
            microArray[7].updateAlly(loc10);
            microArray[8].updateAlly(loc10);
        } else {
            microArray[0].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[1].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[2].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[3].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[4].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[5].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[6].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[7].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            microArray[8].updateEnemy(loc10, info10.health, info10.dir, info10.ratKing);
            enemyHealth += info10.health;
        }
        MapLocation loc11 = allRobots.getKey(11);
        DirectionHealthInfo info11 = decodeDirectionAndHealth(allRobots.getValue(11));
        if (info11.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc11);
            microArray[1].updateAlly(loc11);
            microArray[2].updateAlly(loc11);
            microArray[3].updateAlly(loc11);
            microArray[4].updateAlly(loc11);
            microArray[5].updateAlly(loc11);
            microArray[6].updateAlly(loc11);
            microArray[7].updateAlly(loc11);
            microArray[8].updateAlly(loc11);
        } else {
            microArray[0].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[1].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[2].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[3].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[4].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[5].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[6].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[7].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            microArray[8].updateEnemy(loc11, info11.health, info11.dir, info11.ratKing);
            enemyHealth += info11.health;
        }
        MapLocation loc12 = allRobots.getKey(12);
        DirectionHealthInfo info12 = decodeDirectionAndHealth(allRobots.getValue(12));
        if (info12.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc12);
            microArray[1].updateAlly(loc12);
            microArray[2].updateAlly(loc12);
            microArray[3].updateAlly(loc12);
            microArray[4].updateAlly(loc12);
            microArray[5].updateAlly(loc12);
            microArray[6].updateAlly(loc12);
            microArray[7].updateAlly(loc12);
            microArray[8].updateAlly(loc12);
        } else {
            microArray[0].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[1].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[2].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[3].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[4].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[5].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[6].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[7].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            microArray[8].updateEnemy(loc12, info12.health, info12.dir, info12.ratKing);
            enemyHealth += info12.health;
        }
        MapLocation loc13 = allRobots.getKey(13);
        DirectionHealthInfo info13 = decodeDirectionAndHealth(allRobots.getValue(13));
        if (info13.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc13);
            microArray[1].updateAlly(loc13);
            microArray[2].updateAlly(loc13);
            microArray[3].updateAlly(loc13);
            microArray[4].updateAlly(loc13);
            microArray[5].updateAlly(loc13);
            microArray[6].updateAlly(loc13);
            microArray[7].updateAlly(loc13);
            microArray[8].updateAlly(loc13);
        } else {
            microArray[0].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[1].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[2].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[3].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[4].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[5].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[6].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[7].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            microArray[8].updateEnemy(loc13, info13.health, info13.dir, info13.ratKing);
            enemyHealth += info13.health;
        }
        MapLocation loc14 = allRobots.getKey(14);
        DirectionHealthInfo info14 = decodeDirectionAndHealth(allRobots.getValue(14));
        if (info14.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc14);
            microArray[1].updateAlly(loc14);
            microArray[2].updateAlly(loc14);
            microArray[3].updateAlly(loc14);
            microArray[4].updateAlly(loc14);
            microArray[5].updateAlly(loc14);
            microArray[6].updateAlly(loc14);
            microArray[7].updateAlly(loc14);
            microArray[8].updateAlly(loc14);
        } else {
            microArray[0].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[1].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[2].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[3].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[4].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[5].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[6].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[7].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            microArray[8].updateEnemy(loc14, info14.health, info14.dir, info14.ratKing);
            enemyHealth += info14.health;
        }
        MapLocation loc15 = allRobots.getKey(15);
        DirectionHealthInfo info15 = decodeDirectionAndHealth(allRobots.getValue(15));
        if (info15.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc15);
            microArray[1].updateAlly(loc15);
            microArray[2].updateAlly(loc15);
            microArray[3].updateAlly(loc15);
            microArray[4].updateAlly(loc15);
            microArray[5].updateAlly(loc15);
            microArray[6].updateAlly(loc15);
            microArray[7].updateAlly(loc15);
            microArray[8].updateAlly(loc15);
        } else {
            microArray[0].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[1].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[2].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[3].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[4].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[5].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[6].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[7].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            microArray[8].updateEnemy(loc15, info15.health, info15.dir, info15.ratKing);
            enemyHealth += info15.health;
        }
        MapLocation loc16 = allRobots.getKey(16);
        DirectionHealthInfo info16 = decodeDirectionAndHealth(allRobots.getValue(16));
        if (info16.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc16);
            microArray[1].updateAlly(loc16);
            microArray[2].updateAlly(loc16);
            microArray[3].updateAlly(loc16);
            microArray[4].updateAlly(loc16);
            microArray[5].updateAlly(loc16);
            microArray[6].updateAlly(loc16);
            microArray[7].updateAlly(loc16);
            microArray[8].updateAlly(loc16);
        } else {
            microArray[0].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[1].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[2].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[3].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[4].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[5].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[6].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[7].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            microArray[8].updateEnemy(loc16, info16.health, info16.dir, info16.ratKing);
            enemyHealth += info16.health;
        }
        MapLocation loc17 = allRobots.getKey(17);
        DirectionHealthInfo info17 = decodeDirectionAndHealth(allRobots.getValue(17));
        if (info17.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc17);
            microArray[1].updateAlly(loc17);
            microArray[2].updateAlly(loc17);
            microArray[3].updateAlly(loc17);
            microArray[4].updateAlly(loc17);
            microArray[5].updateAlly(loc17);
            microArray[6].updateAlly(loc17);
            microArray[7].updateAlly(loc17);
            microArray[8].updateAlly(loc17);
        } else {
            microArray[0].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[1].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[2].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[3].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[4].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[5].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[6].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[7].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            microArray[8].updateEnemy(loc17, info17.health, info17.dir, info17.ratKing);
            enemyHealth += info17.health;
        }
        MapLocation loc18 = allRobots.getKey(18);
        DirectionHealthInfo info18 = decodeDirectionAndHealth(allRobots.getValue(18));
        if (info18.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc18);
            microArray[1].updateAlly(loc18);
            microArray[2].updateAlly(loc18);
            microArray[3].updateAlly(loc18);
            microArray[4].updateAlly(loc18);
            microArray[5].updateAlly(loc18);
            microArray[6].updateAlly(loc18);
            microArray[7].updateAlly(loc18);
            microArray[8].updateAlly(loc18);
        } else {
            microArray[0].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[1].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[2].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[3].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[4].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[5].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[6].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[7].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            microArray[8].updateEnemy(loc18, info18.health, info18.dir, info18.ratKing);
            enemyHealth += info18.health;
        }
        MapLocation loc19 = allRobots.getKey(19);
        DirectionHealthInfo info19 = decodeDirectionAndHealth(allRobots.getValue(19));
        if (info19.dir == Direction.CENTER) {
            microArray[0].updateAlly(loc19);
            microArray[1].updateAlly(loc19);
            microArray[2].updateAlly(loc19);
            microArray[3].updateAlly(loc19);
            microArray[4].updateAlly(loc19);
            microArray[5].updateAlly(loc19);
            microArray[6].updateAlly(loc19);
            microArray[7].updateAlly(loc19);
            microArray[8].updateAlly(loc19);
        } else {
            microArray[0].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[1].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[2].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[3].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[4].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[5].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[6].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[7].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            microArray[8].updateEnemy(loc19, info19.health, info19.dir, info19.ratKing);
            enemyHealth += info19.health;
        }
    }

    private void handleDefaultCase() throws GameActionException {
        for (int j = 0; j < allRobots.size; j++) {
            MapLocation loc = allRobots.getKey(j);
            DirectionHealthInfo info = decodeDirectionAndHealth(allRobots.getValue(j));
            if (info.dir == Direction.CENTER) {
                microArray[0].updateAlly(loc);
                microArray[1].updateAlly(loc);
                microArray[2].updateAlly(loc);
                microArray[3].updateAlly(loc);
                microArray[4].updateAlly(loc);
                microArray[5].updateAlly(loc);
                microArray[6].updateAlly(loc);
                microArray[7].updateAlly(loc);
                microArray[8].updateAlly(loc);
            } else {
                microArray[0].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[1].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[2].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[3].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[4].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[5].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[6].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[7].updateEnemy(loc, info.health, info.dir, info.ratKing);
                microArray[8].updateEnemy(loc, info.health, info.dir, info.ratKing);
                enemyHealth += info.health;
            }
        }
    }



}
