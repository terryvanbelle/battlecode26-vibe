package bench_stroke;

import battlecode.common.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static bench_stroke.RobotPlayer.MAP_HEIGHT;
import static bench_stroke.RobotPlayer.MAP_WIDTH;
import static bench_stroke.BabyRat.encodeDirectionAndHealth;
import static bench_stroke.BabyRat.nearestSeenEnemy;
import static bench_stroke.RatKing.nearestEnemy;
import static bench_stroke.BabyRat.turnsSinceEnemy;
import static bench_stroke.BabyRat.allEnemyLocations;
import static bench_stroke.BabyRat.allRobots;
import static bench_stroke.BabyRat.closestAllyRatKing;
import static bench_stroke.BabyRat.targetCheeseMine;
import static bench_stroke.Communication.Communicator.getClosestRatKing;
import static bench_stroke.Pathfinding.Pathfinding.calculateCatDist;
import static bench_stroke.RatKing.adjacentTiles;
import static bench_stroke.RatKing.currentLocation;
import static bench_stroke.BabyRat.nearbyEnemies;
import static bench_stroke.BabyRat.allyTraps;
import static bench_stroke.RobotPlayer.rc;
import static bench_stroke.SymmetryManager.seenMines;
import static bench_stroke.BabyRat.inDistressRatKing;
import static bench_stroke.BabyRat.closestAllyRatKing;

import bench_stroke.DataStructures.*;
import bench_stroke.Communication.*;;

public class Utilities {
    private static final MapLocation[] catOffsets = {
        new MapLocation(0, -1),
        new MapLocation(-1, 0),
        new MapLocation(-1, -1),
        new MapLocation(-1, 1),
        new MapLocation(-1, 2),
        new MapLocation(0, 2),
        new MapLocation(1, 2),
        new MapLocation(2, 2),
        new MapLocation(2, 1),
        new MapLocation(2, 0),
        new MapLocation(2, -1),
        new MapLocation(1, -1)
    };


    public static MapLocation add(MapLocation loc1, MapLocation loc2) {
        return loc1.translate(loc2.x, loc2.y);
    }

    //returns the closest cheese mine to origin that this robot has seen
    public static MapLocation closestCheeseMine(MapLocation origin) {
        seenMines.updateIterable();
        int smallestDist = Integer.MAX_VALUE;
        MapLocation closest = null;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            int dist = origin.distanceSquaredTo(mine);
            if (dist < smallestDist) {
                smallestDist = dist;
                closest = mine;
            }
        }
        return closest;
    }

    //returns the closest cheese mine in a given direction
    public static MapLocation closestCheeseMineInDirection(MapLocation origin, Direction dir) {
        if (seenMines.size == 0) return null;
        seenMines.updateIterable();
        int smallestDist = Integer.MAX_VALUE;
        MapLocation closest = null;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            int dist = origin.distanceSquaredTo(mine);
            Direction toMine = origin.directionTo(mine);
            if (dist < smallestDist && (toMine == dir || toMine == dir.rotateLeft() || toMine == dir.rotateRight())) {
                smallestDist = dist;
                closest = mine;
            }
        }
        return closest;
    }

    //returns a random cheese mine
    public static MapLocation randomCheeseMine() {
        if (seenMines.size == 0) return null;
        seenMines.updateIterable();
        int index = FastMath.randBound(seenMines.size);
        return seenMines.getKey(index);
    }

        //returns the closest cheese mine to origin that this robot has seen, minus the excluded maplocation
    public static MapLocation closestCheeseMine(MapLocation origin, MapLocation exclusion) {
        if (exclusion == null) return closestCheeseMine(origin);
        seenMines.updateIterable();
        int smallestDist = Integer.MAX_VALUE;
        MapLocation closest = null;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            if (mine.equals(exclusion)) continue;
            int dist = origin.distanceSquaredTo(mine);
            if (dist < smallestDist) {
                smallestDist = dist;
                closest = mine;
            }
        }
        return closest;
    }

    //returns a random cheese mine this baby rat has seen, weighted towards closer cheese mines
    public static MapLocation weightedClosestCheeseMine(MapLocation origin) {
        seenMines.updateIterable();
        int totalWeight = 0;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            int dist = origin.distanceSquaredTo(mine);
            // bias to nearer mines with a small constant to keep stochasticity
            int weight = (2048 / (dist + 16)) + 2;
            int threat = seenMines.get(mine, 0);
            int penalty = 32 + Math.min(threat, 96); // soft cap threat impact
            weight = (weight * 32) / penalty; // scales weight down modestly with threat
            if (weight < 1) weight = 1;
            totalWeight += weight;
        }
        if (totalWeight == 0) return null;
        int roll = RobotPlayer.rng.nextInt(totalWeight);
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            int dist = origin.distanceSquaredTo(mine);
            int weight = (2048 / (dist + 16)) + 2;
            int threat = seenMines.get(mine, 0);
            int penalty = 32 + Math.min(threat, 96);
            weight = (weight * 32) / penalty;
            if (weight < 1) weight = 1;
            roll -= weight;
            if (roll < 0) return mine;
        }
        return null;
    }

    //returns a random cheese mine this baby rat has seen, weighted towards closer cheese mines, and not including "exclusion"
    public static MapLocation weightedClosestCheeseMine(MapLocation origin, MapLocation exclusion) {
        if (exclusion == null) return weightedClosestCheeseMine(origin);
        seenMines.updateIterable();
        int totalWeight = 0;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            if (mine.equals(exclusion)) continue;
            int dist = origin.distanceSquaredTo(mine);
            int weight = (2048 / (dist + 16)) + 2;
            int threat = seenMines.get(mine, 0);
            int penalty = 32 + Math.min(threat, 96);
            weight = (weight * 32) / penalty;
            if (weight < 1) weight = 1;
            totalWeight += weight;
        }
        if (totalWeight == 0) return null;
        int roll = RobotPlayer.rng.nextInt(totalWeight);
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            if (mine.equals(exclusion)) continue;
            int dist = origin.distanceSquaredTo(mine);
            int weight = (2048 / (dist + 16)) + 2;
            int threat = seenMines.get(mine, 0);
            int penalty = 32 + Math.min(threat, 96);
            weight = (weight * 32) / penalty;
            if (weight < 1) weight = 1;
            roll -= weight;
            if (roll < 0) return mine;
        }
        return null;
    }

    //returns the closest cheese mine not in unsafeMines to origin that this robot has seen
    public static MapLocation closestCheeseMine(MapLocation origin, FastIterableLocSet unsafeMines) {
        seenMines.updateIterable();
        int smallestDist = Integer.MAX_VALUE;
        MapLocation closest = null;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            if (unsafeMines.contains(mine)) continue;
            int dist = origin.distanceSquaredTo(mine);
            if (dist < smallestDist) {
                smallestDist = dist;
                closest = mine;
            }
        }
        return closest;
    }

    //returns the closest cheese mine not in unsafeMines to origin that this robot has seen
    public static MapLocation furthestCheeseMine(MapLocation origin) {
        seenMines.updateIterable();
        int greatestDist = Integer.MIN_VALUE;
        MapLocation closest = null;
        for (int i = 0; i < seenMines.size; i++) {
            MapLocation mine = seenMines.getKey(i);
            int dist = origin.distanceSquaredTo(mine);
            if (dist > greatestDist) {
                greatestDist = dist;
                closest = mine;
            }
        }
        return closest;
    }
    

    //attempts to mine dirt from any of three front adjacent spaces
    public static boolean attemptDig() throws GameActionException {
        if (!rc.isActionReady()) {
            return false;
        }
        MapLocation curLoc = rc.getLocation();
        Direction facing = rc.getDirection();
        // MapInfo forwardleft = rc.senseMapInfo(curLoc.add(rc.getDirection().rotateLeft()));
        // MapInfo forward = rc.senseMapInfo(curLoc.add(rc.getDirection()));
        // MapInfo forwardright = rc.senseMapInfo(curLoc.add(rc.getDirection().rotateRight()));
        if(rc.canRemoveDirt(curLoc.add(facing.rotateLeft()))) {
            rc.removeDirt(curLoc.add(facing.rotateLeft()));
            return true;
        }
        if(rc.canRemoveDirt(curLoc.add(facing))) {
            rc.removeDirt(curLoc.add(facing));
            return true;
        }
        if(rc.canRemoveDirt(curLoc.add(facing.rotateRight()))) {
            rc.removeDirt(curLoc.add(facing.rotateRight()));
            return true;
        }
        return false;

    }

    //tries to drop carried robot in any direction possible
    public static boolean tryDrop() throws GameActionException {
        if (rc.getCarrying() == null) return false;
        if (rc.canDropRat(rc.getDirection()))  {
            rc.dropRat(rc.getDirection());
            return true;
        }
        else if (rc.canDropRat(rc.getDirection().rotateLeft())) {
            rc.dropRat(rc.getDirection().rotateLeft());
            return true;
        }
        else if (rc.canDropRat(rc.getDirection().rotateRight())) {
            rc.dropRat(rc.getDirection().rotateRight());
            return true;
        }
        return false;
    }

    //attempts to attack each of the three spaces in front of this rat
    public static boolean attemptAttack() throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        Team opponent = rc.getTeam().opponent();
        int forwardleft_health = Integer.MAX_VALUE;
        int forward_health = Integer.MAX_VALUE;
        int forwardright_health = Integer.MAX_VALUE;
        MapLocation forwardleftLoc = curLoc.add(rc.getDirection().rotateLeft());
        MapLocation forwardLoc = curLoc.add(rc.getDirection());
        MapLocation forwardrightLoc = curLoc.add(rc.getDirection().rotateRight());

        RobotInfo forwardleft = (rc.canSenseLocation(forwardleftLoc)) ? rc.senseRobotAtLocation(forwardleftLoc) : null;
        RobotInfo forward = (rc.canSenseLocation(forwardLoc)) ? rc.senseRobotAtLocation(forwardLoc) : null;
        RobotInfo forwardright = (rc.canSenseLocation(forwardrightLoc)) ? rc.senseRobotAtLocation(forwardrightLoc) : null;
        if (forwardleft != null && forwardleft.getType() == UnitType.CAT) {
            forwardleft = null;
        }
        if (forward != null && forward.getType() == UnitType.CAT) {
            forward = null;
        }
        if (forwardright != null && forwardright.getType() == UnitType.CAT) {
            forwardright = null;
        }
        if (forwardleft != null && forwardleft.getTeam() == opponent) {
            forwardleft_health = forwardleft.health;
            if (forwardleft.getType() == UnitType.RAT_KING) {
                forwardleft_health = -1;
            }
        }
        if (forward != null && forward.getTeam() == opponent) {
            forward_health = forward.health;
            if (forward.getType() == UnitType.RAT_KING) {
                forward_health = -1;
            }
        }
        if (forwardright != null && forwardright.getTeam() == opponent) {
            forwardright_health = forwardright.health;
            if (forwardright.getType() == UnitType.RAT_KING) {
                forwardright_health = -1;
            }
        }
        RobotInfo target = null;
        MapLocation targetLoc;
        if (forward_health <= forwardleft_health && forward_health <= forwardright_health) {
            target = forward;
            targetLoc = forwardLoc;
        }
        else if (forwardright_health <= forward_health && forwardright_health <= forwardleft_health) {
            target = forwardright;
            targetLoc = forwardrightLoc;
        }
        else {
            target = forwardleft;
            targetLoc = forwardleftLoc;
        }
        if (target != null && rc.canAttack(targetLoc)) {
            int curCheese = rc.getRawCheese();
            int globalCheese = rc.getGlobalCheese();
            if (rc.getType() == UnitType.BABY_RAT && (curCheese > 0 || globalCheese > 500)) { 
                //lets use our cheese for attack in one of these situations:
                //1. we would kill the opponent when otherwise we wouldnt
                //2. we are very far from home and will likely never return this cheese
                //3. we will probably die soon anyway
                int cheeseToKill = (target.health - GameConstants.RAT_BITE_DAMAGE);
                cheeseToKill = Math.max(cheeseToKill, 0);
                cheeseToKill *= cheeseToKill;
                if (target.getType() == UnitType.RAT_KING) {
                    //use no more than 17, because more efficient to spread out over multiple attacks. use at least two global cheese
                    rc.attack(targetLoc, Math.max(Math.min(curCheese, 17), Math.min(globalCheese, 2)));
                }
                else if (cheeseToKill != 0 && (cheeseToKill <= curCheese || (cheeseToKill - curCheese <= rc.getCurrentRatCost() && globalCheese > 500))) {
                    rc.attack(targetLoc, cheeseToKill);
                   // System.out.println("KILL EM WITH CHEESE");
                }
                else if (curCheese > 0 && ((closestAllyRatKing != null && rc.getLocation().distanceSquaredTo(closestAllyRatKing.loc()) > 150) || rc.getHealth() < 20)) {
                    rc.attack(targetLoc, curCheese);
                }
                else if (curCheese >= 50) {
                    //System.out.println("Using excess cheese");
                    rc.attack(targetLoc, Math.min(curCheese - 50, 17));
                }
                else {
                    rc.attack(targetLoc);
                }
            }
            else {
                rc.attack(targetLoc);
            }
            return true;
        }
        return false;
    }


    //gets the best cheese amount to use in an attack
    public static int bestCheeseAmount(int enemyHealth) {
        int curHealth = rc.getHealth();
        int curCheese = rc.getRawCheese();
        int allCheese = rc.getAllCheese();
        boolean cheeseToSpare = allCheese > 500;
        boolean haveCheese = curCheese > 0;
        //we want to try to make up this health diff, if possible, to avoid being ratnapped
        int healthDiff = enemyHealth - curHealth;

        int cheeseToKill = (enemyHealth - GameConstants.RAT_BITE_DAMAGE);
        cheeseToKill = Math.max(cheeseToKill, 0);
        cheeseToKill = (cheeseToKill > 0) ? (cheeseToKill - 1) * (cheeseToKill - 1) + 1 : 0;
        
        //lets kill the target
        if (enemyHealth <= 10 || cheeseToKill <= curCheese || (cheeseToKill <= 10 && cheeseToSpare)) {
            System.out.println("using cheese to kill");
            return cheeseToKill;
        }
        //we are lower health. lets see if we can even this out
        else if (healthDiff > 0) {
            //we can take the lead by attacking
            if (healthDiff < 10) {
                return 0;
            }
            // i bet we could even this out, or even take the lead!
            else if (healthDiff <= 15) {
                // System.out.println("evening this out!");
                int cheeseToLead = ((healthDiff  + 1) - GameConstants.RAT_BITE_DAMAGE);
                cheeseToLead = Math.max(cheeseToLead, 0);
                cheeseToLead = (cheeseToLead > 0) ? (cheeseToLead - 1) * (cheeseToLead - 1) + 1 : 0;
                if (cheeseToLead <= curCheese || allCheese > 1500) {
                    return cheeseToLead;
                }
                int cheeseToEven = (healthDiff - GameConstants.RAT_BITE_DAMAGE);
                cheeseToEven = Math.max(cheeseToEven, 0);
                cheeseToEven = (cheeseToEven > 0) ? (cheeseToEven - 1) * (cheeseToEven - 1) + 1 : 0;
                if (cheeseToEven <= curCheese || cheeseToSpare) {
                    return cheeseToEven;
                }
            }
            //once we are down in health by a certain amount, it makes sense to just accept that we may be ratnapped and not bother
            else {
                return 0;
            }
        }
        //we are higher health by a lot. probably dont need to worry
        else if (healthDiff < -10) {
            return 0;
        }
        //we are even in health, or slightly ahead. if we have cheese, or cheese to spare, then we will spare a little
        else if (haveCheese || cheeseToSpare){
            // System.out.println("Using spare cheese");
            if (curCheese > 50) return Math.min(curCheese - 50, 17);
            else if (curCheese > 0 || allCheese > 1500) return 2;
            else return 1;
        }
        return 0;
    }

    //attemtps to attack a specific robotInfo, using the optimal amount of cheese
    public static boolean attemptAttackEnemy(RobotInfo target) throws GameActionException {
        if (!rc.isActionReady() || ((BabyRat.currentLocation.distanceSquaredTo(target.location) > 2 && target.getType() != UnitType.RAT_KING) || BabyRat.currentLocation.distanceSquaredTo(target.location) > 8)) return false;
        MapLocation targetLoc = target.location;
        if (target.getType() == UnitType.RAT_KING) {
            targetLoc = targetLoc.add(target.location.directionTo(rc.getLocation()));
        }
        if (!rc.canAttack(targetLoc)) return false;

        int cheeseToUse = bestCheeseAmount(target.health);
        // System.out.println(cheeseToUse);
        rc.attack(targetLoc, cheeseToUse);
        return true;
    }

    //attempts to attack a cat
    public static boolean attemptAttackCat() throws GameActionException {
        MapLocation[] actionableLocations = getActionableLocations();
        for (MapLocation loc : actionableLocations) {
            if (rc.canSenseLocation(loc)) {
                RobotInfo robot = rc.senseRobotAtLocation(loc);
                if (robot != null && robot.getType() == UnitType.CAT) {
                    if (rc.canAttack(loc)) { 
                        rc.attack(loc);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //measures the number of rotations needed to reach target from original
    public static int directionalDistance(Direction target, Direction original) {
        if (target == original) {
            return 0;
        }
        else if (target == original.rotateLeft() || target == original.rotateRight()) {
            return 1;
        }
        else if (target == original.rotateLeft().rotateLeft() || target == original.rotateRight().rotateRight()) {
            return 2;
        }
        else if (target == original.rotateLeft().rotateLeft().rotateLeft() || target == original.rotateRight().rotateRight().rotateRight()) {
            return 3;
        }
        else {
            return 4;
        }
    }

    //attempts to place a cat trap
    public static boolean attemptCatTrap(MapLocation catLoc) throws GameActionException {
        if (catLoc == null || !rc.canSenseLocation(catLoc)) return false;
        RobotInfo cat = rc.senseRobotAtLocation(catLoc);
        if (cat == null) return false;
        MapLocation[] actionableLocations = getActionableLocations();
        for (MapLocation loc : actionableLocations) {
            if (catLoc.isWithinDistanceSquared(loc, 4, cat.direction, 180,  true)) {
                if (rc.canPlaceCatTrap(loc)) {
                    rc.placeCatTrap(loc);
                    return true;
                }
            }
        }
        return false;
    }

    

    //attempts to attack any enemies in range as a rat king
    public static boolean attemptAttackAsRatKing() throws GameActionException {
        RobotInfo[] potentialEnemies = rc.senseNearbyRobots(8, rc.getTeam().opponent());
        if (potentialEnemies.length > 0) {
            RobotInfo bestTarget = potentialEnemies[0];
            int lowestHealth = bestTarget.health;
            for (int i = 1; i < potentialEnemies.length; i++) {
                if (potentialEnemies[i].health < lowestHealth) {
                    lowestHealth = potentialEnemies[i].health;
                    bestTarget = potentialEnemies[i];
                }
            }
            if (rc.canAttack(bestTarget.location)) {
                rc.attack(bestTarget.location);
                return true;
            }
        }
        return false;
    }
    
    //attempts to remove cat traps so we can place them elsewhere
    public static boolean attemptRemoveCatTraps() throws GameActionException {
        MapLocation[] options = getActionableLocations();
        boolean removed = false;
        for (MapLocation loc : options) {
            if (rc.canRemoveCatTrap(loc)) {
                rc.removeCatTrap(loc);
                removed = true;
            }
        }
        return removed;
    }

    //attempts to make any possible attack as a rat king
    public static boolean placeTrapAsRatKing() throws GameActionException {
        RobotInfo[] adjacentEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        RobotInfo lowestEnemy = null;
        int lowestHealth = Integer.MAX_VALUE;
        for (RobotInfo enemy : adjacentEnemies) {
            if (enemy.health < lowestHealth) {
                lowestHealth = enemy.health;
                lowestEnemy = enemy;
            }
        }
        if (lowestEnemy != null && rc.canPlaceRatTrap(lowestEnemy.location)) {
            rc.placeRatTrap(lowestEnemy.location);
            return true;
        }
        return false;
    }

    //attempts to mine any possible dirt as a rat king
    public static boolean attemptMineDirtAsRatKing() throws GameActionException {
        MapLocation[] actionable = getActionableLocationsRatKing();
        for (MapLocation loc : actionable) {
            if (rc.canRemoveDirt(loc)) {
                rc.removeDirt(loc);
                return true;
            }
        }
        return false;
    }

    //attempts to mine dirt as a rat king, in a specific direction
    public static boolean attemptMineDirtAsRatKing(Direction dir) throws GameActionException {
        MapLocation[] self = rc.getAllPartLocations();
        for (MapLocation loc : self) {
            if (rc.canRemoveDirt(loc)) {
                rc.removeDirt(loc);
                return true;
            }
        }
        MapLocation[] actionable = getActionableLocationsRatKing();
        for (MapLocation loc : actionable) {
            Direction dirToLoc = rc.getLocation().directionTo(loc);
            if ((dirToLoc == dir || dirToLoc == dir.rotateLeft() || dirToLoc == dir.rotateRight()) && rc.canRemoveDirt(loc)) {
                rc.removeDirt(loc);
                return true;
            }
        }
        return false;
    }

    //returns the three locations that a baby rat can act on
    public static MapLocation[] getActionableLocations() {
        MapLocation[] actionable = new MapLocation[3];
        Direction curDir = rc.getDirection();
        actionable[0] = rc.adjacentLocation(curDir.rotateLeft());
        actionable[1] = rc.adjacentLocation(curDir);
        actionable[2] = rc.adjacentLocation(curDir.rotateRight());
        return actionable;
    }

    //returns the three locations that a baby rat can act on
    public static MapLocation[] getActionableLocationsRatKing() {
        MapLocation[] actionable = new MapLocation[16];
        MapLocation loc = rc.getLocation();
        int index = 0;
        for (MapLocation offset : adjacentTiles) {
            actionable[index] = add(loc, offset);
            index++;
        }
        return actionable;
    }

    //returns the three adjacent locations a rat can see
    public static FastLocSet getRatVisionLocs(RobotInfo rat) {
        FastLocSet ratVisionSquares = new FastLocSet();
        Direction ratDir = rat.direction;
        MapLocation ratLoc = rat.location;
        ratVisionSquares.add(ratLoc.add(ratDir));
        ratVisionSquares.add(ratLoc.add(ratDir.rotateLeft()));
        ratVisionSquares.add(ratLoc.add(ratDir.rotateRight()));
        return ratVisionSquares;
    }

    public static MapLocation[] getAdjacentLocations(RobotInfo rat) {
        MapLocation[] adj = new MapLocation[8];
        int dirIndex = 0;
        Direction dir;
        Direction[] directions = Direction.allDirections();
        for(int i = 0; i < 8; i++) {
            dir = directions[dirIndex];
            if (dir == Direction.CENTER) {
                dirIndex++;
                dir = directions[dirIndex];
            }
            adj[i] = rat.location.add(dir);
            dirIndex++;
        }
        return adj;
    }

    //attempts to ratnap in any possible location
    public static boolean attemptRatnap() throws GameActionException {
        MapLocation[] potential = getActionableLocations();
        MapLocation curLoc = rc.getLocation();
        for (MapLocation loc : potential) {
            if (!rc.canSenseLocation(loc)) continue;
            RobotInfo rat = rc.senseRobotAtLocation(loc);
            if (rat == null) continue;
            if (rat.getTeam() == rc.getTeam() || rat.getType() == UnitType.CAT) continue;
            if (rat.health <= 10 && rc.canAttack(rat.location)) {
                rc.attack(loc);
                allRobots.remove(loc);
                return true;
            }
            if (rc.canCarryRat(loc)) {
                rc.carryRat(loc);
                allRobots.remove(loc);
                return true;
            }
            if (rc.isMovementReady() && rc.canTurn()) {
                FastLocSet ratVisionSquares = getRatVisionLocs(rat);
                MapLocation[] options = getAdjacentLocations(rat);
                for (MapLocation option : options) {
                    if (option.isAdjacentTo(curLoc) && !ratVisionSquares.contains(option)) {
                        if (rc.canMove(curLoc.directionTo(option))) {
                            rc.move(curLoc.directionTo(option));
                            if (rc.canCarryRat(loc)) {
                                rc.carryRat(loc);
                                allRobots.remove(loc);
                                return true;
                            }
                            else {
                                rc.turn(rc.getLocation().directionTo(loc));
                                if (rc.canCarryRat(loc)) {
                                    rc.carryRat(loc);
                                    allRobots.remove(loc);
                                    return true;
                                }
                                // else {
                                //     System.out.println("Something is wrong! : " + loc);
                                // }

                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    // public static int traceThrowDamage(MapLocation origin, Direction dir, boolean considerMove) throws GameActionException {
    //     int damage = 0;
    //     MapLocation toCheck = origin;
    //     for (int i = 0; i < 4; i++) {
    //         toCheck = toCheck.add(dir);
    //         if (!rc.canSenseLocation(toCheck)) return (i == 0) ? -1 : 5 * (4 - i);
    //         if (!unseenEnemy && !rc.sensePassability(toCheck)) {
    //             if (i == 0) {
    //                 if (considerMove && rc.canMove(dir.opposite())) return 5 * (4 - (i/2)) - 1;
    //                 return 0;
    //             }
    //             return 5 * (4 - (i/2));
    //         }
    //         RobotInfo robot = (unseenEnemy) ? null : rc.senseRobotAtLocation(toCheck);
    //         if (robot != null || unseenEnemy) {
    //             if (!unseenEnemy && robot.getTeam() == rc.getTeam()) {
    //                 if (robot.getType() == UnitType.RAT_KING) return -2;
    //                 return -1;
    //             }
    //             else if (unseenEnemy || robot.getTeam() == rc.getTeam().opponent()) {
    //                 // return (i == 0) ? -1 : 2 * 5 * (4 - (i/2));
    //                 if (i == 0) {
    //                     if (considerMove && rc.canMove(dir.opposite())) return 2 * 5 * (4 - (i/2)) - 1; //-1 bc we would prefer to not have to move
    //                     return 0;
    //                 }
    //                 return 2 * 5 * (4 - (i/2));
    //             }
    //             else {
    //                 // return (i == 0) ? -1 : robot.health;
    //                 if (i == 0) {
    //                     if (considerMove && rc.canMove(dir.opposite())) return 2 * 5 * (4 - (i/2)) - 1; //-1 bc we would prefer to not have to move
    //                     return 0;
    //                 }
    //                 return robot.health;
    //             }
    //         }
    //     }
    //     return 0;
    // }

    //attempts to throw a bot at another enemy bot if possible, or just throw if about to expire or bot about to die
    // Original looped version retained for reference.
    /*
    public static boolean attemptThrow(int turnsCarrying, int currentHealth) throws GameActionException {
        if (BabyRat.ferrying) return false;
        final int TURNS_THRESHOLD = 8; //at or above this, always throw in case we run out of time
        final int HEALTH_THRESHOLD = 20; //below this, always throw in case we die

        MapLocation curLoc = rc.getLocation();
        Direction curDir = rc.getDirection();
        boolean canTurn = rc.canTurn();
        int bestScore = Integer.MIN_VALUE;
        Direction bestThrow = Direction.CENTER;
        Direction bestMove = null;

        int distFromRatKing = curLoc.distanceSquaredTo(closestAllyRatKing.loc());
        Direction dirToRatKing = curLoc.directionTo(closestAllyRatKing.loc());

        Direction left90 = curDir.rotateLeft().rotateLeft();
        Direction right90 = curDir.rotateRight().rotateRight();
        Direction diagLeft = curDir.rotateLeft();
        Direction diagRight = curDir.rotateRight();
        Direction backward = curDir.opposite();

        boolean canMove = rc.isMovementReady();

        // candidates as (moveDir, throwDir)
        Direction[] moveCandidates = {null, null, null, backward, left90, right90, left90, right90, diagLeft, diagRight, left90, right90, curDir, curDir};
        Direction[] throwCandidates = {curDir, diagLeft, diagRight, curDir, curDir, curDir, right90, left90, diagRight, diagLeft, diagRight, diagLeft, diagLeft, diagRight};


        for (int i = 0; i < moveCandidates.length; i++) {
            Direction moveDir = moveCandidates[i];
            Direction throwDir = throwCandidates[i];

            if (distFromRatKing <= 25 && throwDir == dirToRatKing) continue;
            if (moveDir != null && (!canMove || !rc.canMove(moveDir))) continue;
            if (!canTurn && throwDir != curDir) continue;

            MapLocation origin = (moveDir == null) ? curLoc : curLoc.add(moveDir);
            int score = scoreThrowPath(origin, throwDir, moveDir != null);
            if (score == Integer.MIN_VALUE) continue;

            if (score > bestScore) {
                bestScore = score;
                bestThrow = throwDir;
                bestMove = moveDir;
            }

            if (score >= 48) {
                break;
            }

            //round 32
            // if (score >= 39) {
            //     break;
            // }
        }


        int scoreThreshold;

        if (turnsCarrying >= 7) {
            scoreThreshold = 0;
        }
        else {
            scoreThreshold = 1;
        }

        if (bestScore >= scoreThreshold && (turnsCarrying >= TURNS_THRESHOLD || currentHealth <= HEALTH_THRESHOLD || bestScore >= 15)) {
            Direction facing = curDir;
            if (canTurn && facing != bestThrow) {
                rc.turn(bestThrow);
                facing = bestThrow;
            }
            if (bestMove != null && rc.canMove(bestMove)) {
                rc.move(bestMove);
            }
            if (rc.canThrowRat() && facing == bestThrow) {
                rc.throwRat();
                RobotInfo r = rc.senseRobotAtLocation(rc.getLocation().add(bestThrow));
                if (r != null) {
                    nearestSeenEnemy = r;
                    BabyRat.nearestEnemy = r.location;
                    allRobots.put(BabyRat.nearestEnemy, encodeDirectionAndHealth(r.direction, r.health));
                }
                return true;
            }
        }
        return false;
    }
    */

    public static boolean attemptThrow(int turnsCarrying, int currentHealth) throws GameActionException {
        if (BabyRat.ferrying) return false;
        final int TURNS_THRESHOLD = 8; //at or above this, always throw in case we run out of time
        final int HEALTH_THRESHOLD = 20; //below this, always throw in case we die

        MapLocation curLoc = rc.getLocation();
        Direction curDir = rc.getDirection();
        boolean canTurn = rc.canTurn();
        int bestScore = Integer.MIN_VALUE;
        Direction bestThrow = Direction.CENTER;
        Direction bestMove = null;

        int distFromRatKing = curLoc.distanceSquaredTo(closestAllyRatKing.loc());
        Direction dirToRatKing = curLoc.directionTo(closestAllyRatKing.loc());

        Direction left90 = curDir.rotateLeft().rotateLeft();
        Direction right90 = curDir.rotateRight().rotateRight();
        Direction diagLeft = curDir.rotateLeft();
        Direction diagRight = curDir.rotateRight();
        Direction backward = curDir.opposite();

        boolean canMove = rc.isMovementReady();

        // no-move candidates
        Direction throwDir = curDir;
        if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
            int score = scoreThrowPath(curLoc, throwDir, false);
            if (score != Integer.MIN_VALUE && score > bestScore) {
                bestScore = score;
                bestThrow = throwDir;
                bestMove = null;
            }
            if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
        }

        throwDir = diagLeft;
        if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
            int score = scoreThrowPath(curLoc, throwDir, false);
            if (score != Integer.MIN_VALUE && score > bestScore) {
                bestScore = score;
                bestThrow = throwDir;
                bestMove = null;
            }
            if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
        }

        throwDir = diagRight;
        if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
            int score = scoreThrowPath(curLoc, throwDir, false);
            if (score != Integer.MIN_VALUE && score > bestScore) {
                bestScore = score;
                bestThrow = throwDir;
                bestMove = null;
            }
            if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
        }

        // move-required candidates
        if (canMove) {
            if (rc.canMove(backward)) {
                Direction moveDir = backward;
                throwDir = curDir;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, true);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }
            }

            if (rc.canMove(left90)) {
                Direction moveDir = left90;

                throwDir = curDir;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, false);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }

                throwDir = right90;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, true);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }

                throwDir = diagRight;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, false);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }
            }

            if (rc.canMove(right90)) {
                Direction moveDir = right90;

                throwDir = curDir;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, false);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }

                throwDir = left90;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, true);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }

                throwDir = diagLeft;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, false);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }
            }

            if (rc.canMove(diagLeft)) {
                Direction moveDir = diagLeft;
                throwDir = diagRight;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, true);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }
            }

            if (rc.canMove(diagRight)) {
                Direction moveDir = diagRight;
                throwDir = diagLeft;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, true);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }
            }

            if (rc.canMove(curDir)) {
                Direction moveDir = curDir;

                throwDir = diagLeft;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, false);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }

                throwDir = diagRight;
                if (!(distFromRatKing <= 25 && throwDir == dirToRatKing) && (canTurn || throwDir == curDir)) {
                    MapLocation origin = curLoc.add(moveDir);
                    int score = scoreThrowPath(origin, throwDir, false);
                    if (score != Integer.MIN_VALUE && score > bestScore) {
                        bestScore = score;
                        bestThrow = throwDir;
                        bestMove = moveDir;
                    }
                    if (score >= 48) return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
                }
            }
        }

        int scoreThreshold;

        if (turnsCarrying >= 7) {
            scoreThreshold = 0;
        }
        else {
            scoreThreshold = 1;
        }

        return finalizeThrowDecision(bestScore, bestThrow, bestMove, curDir, canTurn, curLoc, turnsCarrying, currentHealth);
    }

    private static boolean finalizeThrowDecision(int bestScore, Direction bestThrow, Direction bestMove, Direction curDir, boolean canTurn, MapLocation curLoc, int turnsCarrying, int currentHealth) throws GameActionException {
        final int TURNS_THRESHOLD = 8;
        final int HEALTH_THRESHOLD = 20;

        int scoreThreshold = (turnsCarrying >= 7) ? 0 : 1;

        if (bestScore >= scoreThreshold && (turnsCarrying >= TURNS_THRESHOLD || currentHealth <= HEALTH_THRESHOLD || bestScore >= 15)) {
            Direction facing = curDir;
            if (canTurn && facing != bestThrow) {
                rc.turn(bestThrow);
                facing = bestThrow;
            }
            if (bestMove != null && rc.canMove(bestMove)) {
                rc.move(bestMove);
            }
            if (rc.canThrowRat() && facing == bestThrow) {
                rc.throwRat();
                turnsSinceEnemy = 0;
                RobotInfo r = rc.senseRobotAtLocation(rc.getLocation().add(bestThrow));
                if (r != null) {
                    nearestSeenEnemy = r;
                    BabyRat.nearestEnemy = r.location;
                    allRobots.put(BabyRat.nearestEnemy, encodeDirectionAndHealth(r.direction, r.health));
                }
                return true;
            }
        }
        return false;
    }

    //throws (or drops) a carried unit away from the ally rat king; evaluates only the three opposite directions
    public static boolean attemptThrowAwayFromRatKing(MapLocation ratKingLoc) throws GameActionException {
        if (ratKingLoc == null) return false;
        MapLocation curLoc = rc.getLocation();
        Direction away = ratKingLoc.directionTo(curLoc);
        if (away == Direction.CENTER) return false;

        // turn first so our forward-cone vision can evaluate the paths
        if (rc.canTurn() && rc.getDirection() != away) {
            rc.turn(away);
        }

        int score = scoreThrowPath(curLoc, away, false);
        if (score == Integer.MIN_VALUE) return false;

        if (score >= 0 && rc.canThrowRat()) {
            if (rc.canTurn() && rc.getDirection() != away) rc.turn(away);
            rc.throwRat();
            return true;
        }

        if (score < 0 && rc.canDropRat(away)) {
            if (rc.canTurn() && rc.getDirection() != away) rc.turn(away);
            rc.dropRat(away);
            return true;
        }

        return false;
    }

    //scores a throw path; negative/INT_MIN means the throw should not be attempted
    // Original looped version retained for quick reference.
    /*
    private static int scoreThrowPath(MapLocation origin, Direction throwDir, boolean movedFirst) throws GameActionException {
        // total throw distance is up to 8 tiles (2 tiles per turn for 4 turns)
        final int TOTAL_THROW_DISTANCE = 8;

        MapLocation check = origin.add(throwDir);
        if (!rc.canSenseLocation(check)) return Integer.MIN_VALUE;
        RobotInfo first = rc.senseRobotAtLocation(check);
        if (first != null) {
            // ignore seeing ourselves at the tile we would have just vacated when simulating move+throw
            if (!(movedFirst && first.ID == rc.getID())) {
                return Integer.MIN_VALUE; // cannot throw with a unit directly in front
            }
        } else if (!rc.sensePassability(check)) {
            return Integer.MIN_VALUE;
        }

        int score = 0;
        MapLocation cur = check;
        RobotInfo robot;
        int remainingTiles;
        int distFactor;
        for (int step = 1; step <= TOTAL_THROW_DISTANCE; step++) {
            if (step > 1 && !rc.canSenseLocation(cur)) break;

            robot = (step > 1) ? rc.senseRobotAtLocation(cur) : first;
            remainingTiles = TOTAL_THROW_DISTANCE - step;
            distFactor = (remainingTiles == 0) ? 1 : remainingTiles;

            if (robot != null) {
                if (movedFirst && robot.ID == rc.getID()) {
                    cur = cur.add(throwDir);
                    continue; // skip over the tile we would have occupied before moving
                }
                if (robot.getTeam() == rc.getTeam()) {
                    // hurting allies is bad; rat king worst of all
                    int allyPenalty = -4 * distFactor;
                    if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                    return allyPenalty;
                }
                if (robot.getType() == UnitType.CAT) {
                    // feeding the cat is very strong utility
                    return 200;
                }
                // hitting an enemy deals 4 * remainingTiles to both the thrown rat and the target
                score += 8 * distFactor;
                break;
            }

            if (!rc.sensePassability(cur)) {
                // hitting a wall deals 4 * remainingTiles damage to the thrown rat
                score += 4 * distFactor;
                break;
            }

            cur = cur.add(throwDir);
        }
        return score;
    }
    */

    private static int scoreThrowPath(MapLocation origin, Direction throwDir, boolean skipFirstTile) throws GameActionException {
        MapLocation cur = origin.add(throwDir);
        RobotInfo robot;
        int startStep = 1;

        if (skipFirstTile) {
            cur = cur.add(throwDir);
            if (!rc.canSenseLocation(cur)) return 0;
            robot = rc.senseRobotAtLocation(cur);
            startStep = 2;
        } else {
            if (!rc.canSenseLocation(cur)) return Integer.MIN_VALUE;
            robot = rc.senseRobotAtLocation(cur);
            if (robot == null && !rc.sensePassability(cur)) return Integer.MIN_VALUE;
        }

        switch (startStep) {
            case 1: {
                int dist = 7;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 2: {
                int dist = 6;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 3: {
                int dist = 5;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 4: {
                int dist = 4;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 5: {
                int dist = 3;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 6: {
                int dist = 2;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 7: {
                int dist = 1;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                cur = cur.add(throwDir);
                if (!rc.canSenseLocation(cur)) return 0;
                robot = rc.senseRobotAtLocation(cur);
            }
            case 8: {
                int dist = 1;
                if (robot != null) {
                    if (robot.getTeam() == rc.getTeam()) {
                        int allyPenalty = -4 * dist;
                        if (robot.getType() == UnitType.RAT_KING) allyPenalty *= 5;
                        return allyPenalty;
                    }
                    if (robot.getType() == UnitType.CAT) return 200;
                    return 8 * dist;
                }
                if (!rc.sensePassability(cur)) return 4 * dist;
                return 0;
            }
            default:
                return 0;
        }
    }

    //returns a random maplocation which is (roughly) in the specified direction
    public static MapLocation generateRandomInDirection(Direction dir) {
        Direction acceptableLeft = dir.rotateLeft();
        Direction acceptableRight = dir.rotateRight();
        Random random = RobotPlayer.rng;
        MapLocation curLoc = rc.getLocation();
        int minX = 0;
        int maxX = MAP_WIDTH - 1;
        int minY = 0;
        int maxY = MAP_HEIGHT - 1;

        switch (dir) {
            case NORTH:
                minY = curLoc.y;
                break;
            case SOUTH:
                maxY = curLoc.y;
                break;
            case EAST:
                minX = curLoc.x;
                break;
            case WEST:
                maxX = curLoc.x;
                break;
            case NORTHEAST:
                minX = curLoc.x;
                minY = curLoc.y;
                break;
            case NORTHWEST:
                maxX = curLoc.x;
                minY = curLoc.y;
                break;
            case SOUTHEAST:
                minX = curLoc.x;
                maxY = curLoc.y;
                break;
            case SOUTHWEST:
                maxX = curLoc.x;
                maxY = curLoc.y;
                break;
            default:
                break;
        }

        minX = Math.max(0, minX);
        maxX = Math.min(MAP_WIDTH - 1, maxX);
        minY = Math.max(0, minY);
        maxY = Math.min(MAP_HEIGHT - 1, maxY);

        //try to sample a point that is actually in the forward arc; fall back to the bounded box if none fit
        for (int attempts = 0; attempts < 15; attempts++) {
            MapLocation candidate = randomMapLocation(random, minX, maxX, minY, maxY);
            if (candidate.equals(curLoc)) continue;
            Direction directionToCandidate = curLoc.directionTo(candidate);
            if (directionToCandidate == dir || directionToCandidate == acceptableLeft || directionToCandidate == acceptableRight) {
                return candidate;
            }
        }
        return randomMapLocation(random, minX, maxX, minY, maxY);
    }

    // random map location
    public static MapLocation randomMapLocation(Random rand, int width, int height) {
        return randomMapLocation(rand, 0, width - 1, 0, height - 1);
    }

    //random map location bounded by inclusive ranges
    public static MapLocation randomMapLocation(Random rand, int minX, int maxX, int minY, int maxY) {
        // int x = minX + rand.nextInt(Math.max(1, maxX - minX + 1));
        // int y = minY + rand.nextInt(Math.max(1, maxY - minY + 1));
        int x = minX + FastMath.randBound(Math.max(1, maxX - minX + 1));
        int y = minY + FastMath.randBound(Math.max(1, maxY - minY + 1));
        return new MapLocation(x, y);
    }

    //attempts to transfer cheese to any valid spots
    public static boolean attemptCheeseTransfer() throws GameActionException {
        // for (MapLocation potentialTransfer : Utilities.getActionableLocations()) {
        //     if (rc.canTransferCheese(potentialTransfer, rc.getRawCheese())) {
        //         rc.transferCheese(potentialTransfer, rc.getRawCheese());
        //         return true;
        //     }
        // }
        MapLocation location = closestAllyRatKing.loc();
        Direction dirToMe = closestAllyRatKing.loc().directionTo(rc.getLocation());
        if (rc.canTransferCheese(location.add(dirToMe), rc.getRawCheese())) {
            rc.transferCheese(location.add(dirToMe), rc.getRawCheese());
            return true;
        }
        if (rc.canTransferCheese(location.add(dirToMe).add(dirToMe.rotateLeft().rotateLeft()), rc.getRawCheese())) {
            rc.transferCheese(location.add(dirToMe).add(dirToMe.rotateLeft().rotateLeft()), rc.getRawCheese());
            return true;
        }
        if (rc.canTransferCheese(location.add(dirToMe).add(dirToMe.rotateRight().rotateRight()), rc.getRawCheese())) {
            rc.transferCheese(location.add(dirToMe).add(dirToMe.rotateRight().rotateRight()), rc.getRawCheese());
            return true;
        }
        return false;
    }

    //returns closest robot given an array of robotInfos
    public static RobotInfo closestRobot(MapLocation origin, RobotInfo[] array) {
        if (array.length == 0) return null;
        RobotInfo closest = array[0];
        int closestDist = origin.distanceSquaredTo(closest.location);
        for (int i = 1; i < array.length; i++) {
            int dist = origin.distanceSquaredTo(array[i].location);
            if (dist < closestDist) {
                closestDist = dist;
                closest = array[i];
            }
        }
        return closest;
    }

    //attempts to place dirt at a cheese mine on the other side of your ally rat king
    public static boolean placeDirtMine(MapLocation cheeseMine) throws GameActionException {
        if (rc.getDirt() <= 15) return false;
        if (SymmetryManager.getSym() == 0) return false;
        Direction mineToEnemy = cheeseMine.directionTo(SymmetryManager.getSymmetric(closestAllyRatKing.loc()));
        MapLocation[] actionable = getActionableLocations();
        for (MapLocation loc : actionable) {
            if (loc.distanceSquaredTo(cheeseMine) <= 2) return false;
            Direction locToMine = loc.directionTo(cheeseMine).opposite();
            if (locToMine == mineToEnemy || locToMine == mineToEnemy.rotateLeft() || locToMine == mineToEnemy.rotateRight()) {
                if (rc.canPlaceDirt(loc)) {
                    rc.placeDirt(loc);
                    return true;
                }
            }
        }

        return false;
    }

    //checks if rc has a clear path in current direction to a space. used for throwing.
    public static boolean clearPathTo(MapLocation destination, UnitType target) throws GameActionException {
        if (!rc.canSenseLocation(destination)) return false;
        Direction dir = rc.getDirection();
        MapLocation cur = rc.getLocation();
        MapLocation toCheck = cur;
        while (true) {
            toCheck = toCheck.add(dir);
            RobotInfo rob = rc.senseRobotAtLocation(toCheck);
            if (toCheck.equals(destination)) return true;
            else if (!rc.sensePassability(toCheck) || (rob != null && rob.getType() != target)) {
                return false;
            }

        }
    }
    //checks if rc would hit a cat if it threw right now
    public static boolean clearCatPath() throws GameActionException {
        Direction dir = rc.getDirection();
        MapLocation cur = rc.getLocation();
        MapLocation toCheck = cur;
        for (int i = 0; i < 4; i++) {
            toCheck = toCheck.add(dir);
            if (!rc.canSenseLocation(toCheck)) return false;
            RobotInfo rob = rc.senseRobotAtLocation(toCheck);
            if (rob != null && rob.getType() == UnitType.CAT) return true;
            else if (!rc.sensePassability(toCheck) || rob != null) {
                return false;
            }
        }
        return false;
    }

    //     private static final MapLocation[] catOffsets = {
    //     new MapLocation(0, -1), 0
    //     new MapLocation(-1, 0), 1
    //     new MapLocation(-1, -1), 2
    //     new MapLocation(-1, 1), 3
    //     new MapLocation(-1, 2), 4
    //     new MapLocation(0, 2), 5
    //     new MapLocation(1, 2), 6
    //     new MapLocation(2, 2), 7
    //     new MapLocation(2, 1), 8
    //     new MapLocation(2, 0), 9
    //     new MapLocation(2, -1), 10
    //     new MapLocation(1, -1) 11
    // };

    public static FastLocSet catPath(MapLocation catLoc, Direction catDir) {
        FastLocSet catPath = new FastLocSet();
        if (catDir != null && catLoc != null) {
            switch(catDir) {
                case NORTH -> {
                    catPath.add(add(catLoc, catOffsets[5]));
                    catPath.add(add(catLoc, catOffsets[6]));
                }
                case NORTHEAST -> {
                    catPath.add(add(catLoc, catOffsets[6]));
                    catPath.add(add(catLoc, catOffsets[7]));
                    catPath.add(add(catLoc, catOffsets[8]));
                }
                case EAST -> {
                    catPath.add(add(catLoc, catOffsets[8]));
                    catPath.add(add(catLoc, catOffsets[9]));
                }
                case SOUTHEAST -> {
                    catPath.add(add(catLoc, catOffsets[9]));
                    catPath.add(add(catLoc, catOffsets[10]));
                    catPath.add(add(catLoc, catOffsets[11]));
                }
                case SOUTH -> {
                    catPath.add(add(catLoc, catOffsets[11]));
                    catPath.add(add(catLoc, catOffsets[0]));
                }
                case SOUTHWEST -> {
                    catPath.add(add(catLoc, catOffsets[0]));
                    catPath.add(add(catLoc, catOffsets[2]));
                    catPath.add(add(catLoc, catOffsets[1]));
                }
                case WEST -> {
                    catPath.add(add(catLoc, catOffsets[1]));
                    catPath.add(add(catLoc, catOffsets[3]));
                }
                case NORTHWEST -> {
                    catPath.add(add(catLoc, catOffsets[5]));
                    catPath.add(add(catLoc, catOffsets[4]));
                    catPath.add(add(catLoc, catOffsets[3]));
                }
            }
        }
        return catPath;
    }


    public static FastLocSet catPath(RobotInfo cat) {
        FastLocSet catPath = new FastLocSet();
        if (cat != null) {
            switch(cat.direction) {
                case NORTH -> {
                    catPath.add(add(cat.location, catOffsets[5]));
                    catPath.add(add(cat.location, catOffsets[6]));
                }
                case NORTHEAST -> {
                    catPath.add(add(cat.location, catOffsets[6]));
                    catPath.add(add(cat.location, catOffsets[7]));
                    catPath.add(add(cat.location, catOffsets[8]));
                }
                case EAST -> {
                    catPath.add(add(cat.location, catOffsets[8]));
                    catPath.add(add(cat.location, catOffsets[9]));
                }
                case SOUTHEAST -> {
                    catPath.add(add(cat.location, catOffsets[9]));
                    catPath.add(add(cat.location, catOffsets[10]));
                    catPath.add(add(cat.location, catOffsets[11]));
                }
                case SOUTH -> {
                    catPath.add(add(cat.location, catOffsets[11]));
                    catPath.add(add(cat.location, catOffsets[0]));
                }
                case SOUTHWEST -> {
                    catPath.add(add(cat.location, catOffsets[0]));
                    catPath.add(add(cat.location, catOffsets[2]));
                    catPath.add(add(cat.location, catOffsets[1]));
                }
                case WEST -> {
                    catPath.add(add(cat.location, catOffsets[1]));
                    catPath.add(add(cat.location, catOffsets[3]));
                }
                case NORTHWEST -> {
                    catPath.add(add(cat.location, catOffsets[5]));
                    catPath.add(add(cat.location, catOffsets[4]));
                    catPath.add(add(cat.location, catOffsets[3]));
                }
            }
        }
        return catPath;
    }

    //attempts to place a trap in the micro space with the highest trap benefit, if there is one with 2 or more
    public static boolean attemptTrap(RobotInfo[] nearbyEnemies, int threshold) throws GameActionException {
        //dont place a trap if we dont have a cheese buffer
        if (rc.getAllCheese() < 100) {
            return false;
        }
        MapLocation[] options = Utilities.getActionableLocations();
        int[] optionValues = {0, 0, 0};

        for (RobotInfo enemy : nearbyEnemies) {
            for (int i = 0; i < optionValues.length; i++) {
                int dist = enemy.location.distanceSquaredTo(options[i]);
                if (dist <= 2 && dist != 0) {
                    optionValues[i] += 1;
                }
                else if (dist == 0) {
                    optionValues[i] -= 1000;
                }
            }
        }
        int highestIndex = 0;
        for (int i = 1; i < optionValues.length; i++) {
            if (optionValues[i] > optionValues[highestIndex]) {
                highestIndex = i;
            }
        }
        if (optionValues[highestIndex] >= threshold && rc.onTheMap(options[highestIndex]) && rc.canPlaceRatTrap(options[highestIndex])) {
            rc.placeRatTrap(options[highestIndex]);
            return true;
        }
        return false;
    }

    //attempts to place a trap in front of an enemy
    public static boolean attemptTrapInFrontOfEnemy(RobotInfo enemy) throws GameActionException {
        if (enemy == null || !rc.isActionReady() || (rc.getAllCheese() < 100)) return false;
        MapLocation loc = enemy.location;
        Direction dir = enemy.direction;
        if (rc.canPlaceRatTrap(loc.add(dir))) {
            rc.placeRatTrap(loc.add(dir));
            return true;
        }
        else if (rc.canPlaceRatTrap(loc.add(dir).add(dir))){
            rc.placeRatTrap(loc.add(dir).add(dir));
            return true;
        }
        // else if (rc.canPlaceRatTrap(rc.getLocation().add(rc.getLocation().directionTo(loc))))  {
        //     rc.placeRatTrap(rc.getLocation().add(rc.getLocation().directionTo(loc)));
        //     return true;
        // }
        return false;
    }

    //attempts to place a trap in front of an enemy
    public static boolean attemptTrapTowardsEnemy(MapLocation enemyLoc, boolean strict) throws GameActionException {
        if (enemyLoc == null || !rc.isActionReady() || (rc.getAllCheese() < 100)) return false;
        MapLocation currentLoc = rc.getLocation();
        Direction dir = currentLoc.directionTo(enemyLoc);
        if (rc.onTheMap(currentLoc.add(dir))) {
            if (rc.canPlaceRatTrap(currentLoc.add(dir))) {
                rc.placeRatTrap(currentLoc.add(dir));
                return true;
            }
            else if (allyTraps.contains(currentLoc.add(dir))) {
                return false;
            }
        }
        if (!strict && rc.canSenseLocation(currentLoc.add(dir)) && rc.sensePassability(currentLoc.add(dir))) {
            if (rc.onTheMap(currentLoc.add(dir.rotateLeft()))) {
                if (rc.canPlaceRatTrap(currentLoc.add(dir.rotateLeft()))) {
                    rc.placeRatTrap(currentLoc.add(dir.rotateLeft()));
                    return true;
                }
                else if (allyTraps.contains(currentLoc.add(dir.rotateLeft()))) {
                    return false;
                }
            }
            if (rc.onTheMap(currentLoc.add(dir.rotateRight())) && rc.canPlaceRatTrap(currentLoc.add(dir.rotateRight()))) {
                rc.placeRatTrap(currentLoc.add(dir.rotateRight()));
                return true;
            }
        }
        return false;
    }

    //attempts to trap placing traps in front of enemies, instead of willy-nilly
    public static boolean attemptTrapRevamped(RobotInfo[] nearbyEnemies, int threshold) throws GameActionException {
        if (rc.getAllCheese() < 100 && rc.getRawCheese() < 20) return false;

        MapLocation[] options = getActionableLocations();
        int[] values = {0, 0, 0};
        for (int i = 0; i < 3; i++) {
            if (!rc.canPlaceRatTrap(options[i])) {
                values[i] = -1;
            }
        }
        int highestIndex = -1;

        for (RobotInfo enemy : nearbyEnemies) {
            MapLocation enemyLoc = enemy.location;
            //Direction dirToEnemy = rc.getLocation().directionTo(enemyLoc);
            Direction enemyDir = enemy.direction;
            MapLocation inFrontOfEnemy = enemyLoc.add(enemyDir);
            MapLocation twiceInFrontOfEnemy = inFrontOfEnemy.add(enemyDir);
            for (int i = 0; i < options.length; i++) {
                if (values[i] == -1) continue;
                if (options[i].equals(inFrontOfEnemy)) {
                    values[i] += 2;
                    if (highestIndex == -1 || values[i] > values[highestIndex]) highestIndex = i;
                }
                else if (options[i].isAdjacentTo(enemyLoc)) {
                    values[i] += 1;
                    if (highestIndex == -1 || values[i] > values[highestIndex]) highestIndex = i;
                }
                else if (rc.canSenseLocation(twiceInFrontOfEnemy) && options[i].equals(twiceInFrontOfEnemy)) {
                    values[i] += 1;
                    if (highestIndex == -1 || values[i] > values[highestIndex]) highestIndex = i;
                }
            }
        }

        if (highestIndex != -1 && values[highestIndex] >= threshold && rc.canPlaceRatTrap(options[highestIndex])) {
            rc.placeRatTrap(options[highestIndex]);
            return true;
        }
        return false;
    }

    //attempts to trap placing traps in between you and enemies who can see you
    public static boolean attemptTrapV3(RobotInfo[] nearbyEnemies, int threshold) throws GameActionException {
        if (rc.getAllCheese() < 100 && rc.getRawCheese() < 20) return false;

        currentLocation = rc.getLocation();

        MapLocation[] options = getActionableLocations();
        int[] values = {0, 0, 0};
        boolean oneValid = false;
        for (int i = 0; i < 3; i++) {
            if (!rc.canPlaceRatTrap(options[i])) {
                values[i] = -1;
            }
            else oneValid = true;
        }
        if (!oneValid) return false;
        int highestIndex = -1;

        for (RobotInfo enemy : nearbyEnemies) {
            MapLocation enemyLoc = enemy.location;
            int dist = currentLocation.distanceSquaredTo(enemyLoc);
            if (dist > 8) continue;
            Direction enemyDir = enemy.direction;
            boolean inVision = enemyLoc.isWithinDistanceSquared(currentLocation, 20, enemyDir, 90);
            if (!inVision) continue;
            boolean isClosest = false;
            if (nearestSeenEnemy != null && enemy.equals(nearestSeenEnemy)) isClosest = true;
            Direction dirToEnemy = currentLocation.directionTo(enemyLoc);
            MapLocation inFrontOfEnemy = enemyLoc.add(enemyDir);
            for (int i = 0; i < options.length; i++) {
                if (values[i] == -1) continue;
                if (options[i].equals(inFrontOfEnemy)) {
                    values[i] += (isClosest) ? 2 : 1;
                    if (highestIndex == -1 || values[i] > values[highestIndex]) highestIndex = i;
                }
                else if (options[i].isAdjacentTo(enemyLoc)) {
                    values[i] += 1;
                    if (highestIndex == -1 || values[i] > values[highestIndex]) highestIndex = i;  
                }
            }
        }

        if (highestIndex != -1 && values[highestIndex] >= threshold && rc.canPlaceRatTrap(options[highestIndex])) {
            rc.placeRatTrap(options[highestIndex]);
            return true;
        }
        return false;
    }

    //attempts to trap placing traps adjacent to a cheese mine, if there is not one already
    public static boolean attemptTrapMine(MapLocation cheeseMine, boolean looseTrap) throws GameActionException {
        if (rc.getAllCheese() < 100 || rc.getRawCheese() < 20 || !rc.canSenseLocation(cheeseMine) || SymmetryManager.getSym() == 0) return false;

        MapLocation enemyRatKingApprox = SymmetryManager.getSymmetric(closestAllyRatKing.loc());
        Direction dir = cheeseMine.directionTo(enemyRatKingApprox);
        MapLocation target = cheeseMine.add(dir);
        if (rc.canPlaceRatTrap(target)) {
            rc.placeRatTrap(target);
            return true;
        }
        if (looseTrap) {
            Direction leftDir = dir.rotateLeft();
            Direction rightDir = dir.rotateRight();
            if (rc.canPlaceRatTrap(target.add(leftDir))) {
                rc.placeRatTrap(target.add(leftDir));
                return true;
            }
            else if (rc.canPlaceRatTrap(target.add(rightDir))) {
                rc.placeRatTrap(target.add(rightDir));
                return true;
            }
        }
        return false;
    }

    //tries to pick up cheese in all valid locations
    public static boolean attemptCheesePickup() throws GameActionException {
        if (rc.getRawCheese() >= 100) {
            return false;
        }
        int curCheese = rc.getRawCheese();
        MapLocation[] attempts = getActionableLocations();
        for (MapLocation loc : attempts) {
            if (rc.onTheMap(loc) && rc.canPickUpCheese(loc)) {
                rc.pickUpCheese(loc);
            }
        }
        if (rc.canPickUpCheese(rc.getLocation())) {
            rc.pickUpCheese(rc.getLocation());
        }
        return rc.getRawCheese() > curCheese;
    }

    //returns which quadrant (top left, going clockwise) we are in
    //0 - top left, 1 - top right, 2 - bottom right, 3 - bottom left
    public static int calculateQuadrant(MapLocation loc) {
        int height = rc.getMapHeight();
        int width = rc.getMapWidth();
        int boundaryWidth = width / 2;
        int boundaryHeight = height / 2;
        int x = loc.x;
        int y = loc.y;
        if (x < boundaryWidth) {
            if (y < boundaryHeight) {
                return 3;
            }
            else {
                return 0;
            }
        }
        else {
            if (y < boundaryHeight) {
                return 2;
            }
            else {
                return 1;
            }
        }
    }

    //returns whether this bot is currently facing two dirt spaces
    public static boolean facingTwoDirt() throws GameActionException {
        MapLocation[] actionable = getActionableLocations();
        int numDirt = 0;
        for (MapLocation loc : actionable) {
            if (rc.canSenseLocation(loc) && rc.senseMapInfo(loc).isDirt()) numDirt++;
        }
        return numDirt >= 2;
    }

    //returns whether this bot is currently facing three wall spaces
    public static boolean facingThreeWalls() throws GameActionException {
        MapLocation[] actionable = getActionableLocations();
        int numWall = 0;
        for (MapLocation loc : actionable) {
            if (rc.canSenseLocation(loc) && rc.senseMapInfo(loc).isWall()) numWall++;
        }
        return numWall >= 3;
    }

    //returns the three locations a cat could "act on"
    public static MapLocation[] getCatActionableLocations(MapLocation catLoc) throws GameActionException {
        MapLocation[] actionable = new MapLocation[3];
        if (!rc.canSenseLocation(catLoc)) return null;
        RobotInfo cat = rc.senseRobotAtLocation(catLoc);
        if (cat == null) {
            return null;
        }

        Direction dir = cat.direction;
        if (dir == Direction.NORTH || dir == Direction.NORTHWEST) {
            catLoc = catLoc.add(Direction.NORTH);
        }
        else if (dir == Direction.NORTHEAST || dir == Direction.EAST) {
            catLoc = catLoc.add(Direction.NORTHEAST);
        }
        actionable[0] = catLoc.add(dir.rotateLeft());
        actionable[1] = catLoc.add(dir);
        actionable[2] = catLoc.add(dir.rotateRight());
        return actionable;
    }   

    //tries to place dirt in each space in spaces
    public static boolean tryBuild(MapLocation[] spaces) throws GameActionException {
        if (rc.isActionReady() && rc.getDirt() > 0) {
            for (MapLocation space : spaces) {
                if (rc.canPlaceDirt(space)) {
                    rc.placeDirt(space);
                    return true;
                }
            }
        }
        return false;
    }

    //tries to place dirt in each space in a given direction
    public static boolean tryBuild(Direction dir) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        MapLocation bestOpt = null;
        if (rc.isActionReady() && rc.getDirt() > 0) {
            MapLocation[] actionable = (rc.getType() == UnitType.BABY_RAT) ? getActionableLocations() : getActionableLocationsRatKing();
            for (MapLocation option : actionable) {
                Direction dirToOpt = curLoc.directionTo(option);
                if (dirToOpt == dir && rc.canPlaceDirt(option)) {
                    bestOpt = option;
                    break;
                }
                else if ((dirToOpt == dir.rotateLeft() || dirToOpt == dir.rotateRight()) && rc.canPlaceDirt(option)) {
                    bestOpt = option;
                }
            }
        }
        if (bestOpt != null) {
            rc.placeDirt(bestOpt);
            return true;
        }
        return false;
    }


    //tries to place dirt in each space in a given direction
    public static boolean tryBuild(Direction dir, boolean strict) throws GameActionException {
        MapLocation curLoc = rc.getLocation();
        MapLocation bestOpt = null;
        if (rc.isActionReady() && rc.getDirt() > 0) {
            MapLocation[] actionable = (rc.getType() == UnitType.BABY_RAT) ? getActionableLocations() : getActionableLocationsRatKing();
            for (MapLocation option : actionable) {
                Direction dirToOpt = curLoc.directionTo(option);
                if (dirToOpt == dir && rc.canPlaceDirt(option)) {
                    bestOpt = option;
                    break;
                }
                else if (!strict && (dirToOpt == dir.rotateLeft() || dirToOpt == dir.rotateRight()) && rc.canPlaceDirt(option)) {
                    bestOpt = option;
                }
            }
        }
        if (bestOpt != null) {
            rc.placeDirt(bestOpt);
            return true;
        }
        return false;
    }

    //tries to pick up cheese as a rat king
    public static boolean attemptToPickUpRatKingCheese() throws GameActionException {
        MapLocation[] options = rc.getAllLocationsWithinRadiusSquared(rc.getLocation(), 2);
        boolean pickedUp = false;
        for (MapLocation option : options) {
            if (rc.canPickUpCheese(option)) {
                rc.pickUpCheese(option);
                pickedUp = true;
            }
        }
        return pickedUp;
    }


    public static boolean attemptCatTrap() throws GameActionException {
        RobotInfo[] cats = rc.senseNearbyRobots(-1, Team.NEUTRAL);
        if (cats.length == 0) return false;
        RobotInfo cat = cats[0];
        MapLocation[] actionable = Utilities.getActionableLocations();
        if (actionable != null) {
            for (MapLocation loc : actionable) {
                if (cat.location.isWithinDistanceSquared(loc, 7, cat.direction, 180, true)) {
                    if (rc.canPlaceCatTrap(loc)) {
                        rc.placeCatTrap(loc);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    //returns whether location is within the vision radius of a given cat
    public static boolean withinCatVision(MapLocation loc, RobotInfo cat) {
        return cat.location.isWithinDistanceSquared(loc, 17, cat.direction, 180, true);
    }

    //returns the squares that the cat can see (that you can also see)
    public static FastLocSet catVisionSquares(RobotInfo cat, FastLocSet catSquares) throws GameActionException {
        if (catSquares == null) catSquares = new FastLocSet();
        else if (catSquares.size > 0) catSquares.clear();

        MapLocation[] candidates = rc.getAllLocationsWithinRadiusSquared(cat.location, 17);

        for (MapLocation info : candidates) {
            if (cat.location.isWithinDistanceSquared(info, 17, cat.direction, 180, true)) {
                catSquares.add(info);
            }
        }
        return catSquares;
    }

    //returns the squares that the cat can see (that you can also see)
    public static FastLocSet catVisionSquares(MapLocation catLoc, Direction catDir, FastLocSet catSquares) throws GameActionException {
        if (catSquares == null) catSquares = new FastLocSet();
        else if (catSquares.size > 0) catSquares.clear();

        MapLocation[] candidates = rc.getAllLocationsWithinRadiusSquared(catLoc, 17);

        for (MapLocation info : candidates) {
            if (catLoc.isWithinDistanceSquared(info, 17, catDir, 180, true)) {
                catSquares.add(info);
            }
        }
        return catSquares;
    }

    //tries to leave the squares in a cats path by any means possible (preferably by leaving catVision)
    public static boolean attemptLeaveCatPath(FastLocSet catPath, FastLocSet catVision, RobotInfo cat) throws GameActionException {
        MapLocation best = null;
        double highestCatDist = calculateCatDist(cat, rc.getLocation());
        if (catVision.contains(rc.getLocation())) highestCatDist++;
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;
            else if (!rc.canMove(dir)) continue;
            MapLocation potential = rc.adjacentLocation(dir);
            if (catPath.contains(potential)) continue;
            double catDist = calculateCatDist(cat, potential);
            if (catVision.contains(potential)) catDist++;
            if (catDist > highestCatDist) {
                highestCatDist = catDist;
                best = potential;
            }
        }
        if (best != null && rc.canMove(rc.getLocation().directionTo(best))) {
            if (rc.canTurn()) rc.turn(rc.getLocation().directionTo(best));
            rc.move(rc.getLocation().directionTo(best));
            return true;
        }
        return false;
    }

    //tries to leave the squares in a cats path by any means possible (preferably by leaving catVision)
    public static boolean attemptLeaveCatPath(FastLocSet catPath, FastLocSet catVision, MapLocation catLoc) throws GameActionException {
        MapLocation best = null;
        double highestCatDist = calculateCatDist(catLoc, rc.getLocation());
        if (catVision.contains(rc.getLocation())) highestCatDist++;
        for (Direction dir : Direction.allDirections()) {
            if (dir == Direction.CENTER) continue;
            else if (!rc.canMove(dir)) continue;
            MapLocation potential = rc.adjacentLocation(dir);
            if (catPath.contains(potential)) continue;
            double catDist = calculateCatDist(catLoc, potential);
            if (catVision.contains(potential)) catDist++;
            if (catDist > highestCatDist) {
                highestCatDist = catDist;
                best = potential;
            }
        }
        if (best != null && rc.canMove(rc.getLocation().directionTo(best))) {
            if (rc.canTurn()) rc.turn(rc.getLocation().directionTo(best));
            rc.move(rc.getLocation().directionTo(best));
            return true;
        }
        return false;
    }

    //calculates an approximate distance from the middle of the cat to the location
    public static double calculateCatDist(RobotInfo cat, MapLocation loc) {
        double catX = cat.location.x + 0.5;
        double catY = cat.location.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

    //calculates an approximate distance from the middle of the cat to the location
    public static double calculateCatDist(MapLocation catLoc, MapLocation loc) {
        double catX = catLoc.x + 0.5;
        double catY = catLoc.y + 0.5;
        double dist = Math.sqrt((loc.x - catX) * (loc.x -catX) + (loc.y - catY) * (loc.y - catY));
        return dist;
    }

    //returns ALL (not just what you can see) of the squares adjacent to the cat
    public static FastIterableLocSet catAdjacentSquares(RobotInfo cat) throws GameActionException {
        FastIterableLocSet catAdjacentSquares = new FastIterableLocSet(16);
        MapLocation bottomLeft = cat.location;
        for (MapLocation offset : catOffsets) {
            MapLocation temp = Utilities.add(bottomLeft, offset);
            catAdjacentSquares.add(temp);
        }
        return catAdjacentSquares;
        
    }

    public static MapLocation getAveragePresenceSqueakLocation() {
        int round = rc.getRoundNum();
        Squeak[] squeaks = Communicator.getAllSqueaks();
        if (squeaks.length == 0) return null;
        int x = 0;
        int y = 0;
        for (Squeak squeak : squeaks) {
            if (squeak.squeakInfo instanceof PresenceSqueakInfo && round - squeak.round <= 1) {
                x += squeak.source.x;
                y += squeak.source.y;
            }
        }
        return new MapLocation(x/squeaks.length, y/squeaks.length);
    }

    //returns the closest corner of the map
    public static MapLocation closestCorner(MapLocation origin) {
        int x = origin.x;
        int y = origin.y;
        int height = MAP_HEIGHT;
        int width = MAP_WIDTH;
        if (x < width / 2) {
            if (y < height / 2) {
                return new MapLocation(0, 0);
            }
            else {
                return new MapLocation(0, height - 1);
            }
        }
        else {
            if (y < height / 2) {
                return new MapLocation(width - 1, 0);
            }
            else {
                return new MapLocation(width - 1, height - 1);
            }
        }
    }

    public static int dist2ToRect(MapLocation loc, int minX, int maxX, int minY, int maxY) {
        int dx = 0;
        if (loc.x < minX) {
            dx = minX - loc.x;
        } else if (loc.x > maxX) {
            dx = loc.x - maxX;
        }

        int dy = 0;
        if (loc.y < minY) {
            dy = minY - loc.y;
        } else if (loc.y > maxY) {
            dy = loc.y - maxY;
        }

        return dx * dx + dy * dy;
    }

}
