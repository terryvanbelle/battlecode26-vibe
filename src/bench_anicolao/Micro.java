package bench_anicolao;

import battlecode.common.*;

public class Micro {

    public static void runSoldierMicro(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (enemies.length == 0) return;

        RobotInfo target = getBestTarget(rc, enemies);
        if (target == null) return;

        boolean readyToAttack = rc.isActionReady(); 
        MapLocation myLoc = rc.getLocation();
        String targetType = target.type.toString();
        
        if (readyToAttack) {
            // ATTACK LOGIC
            if (targetType.contains("CAT")) { // CAT LOGIC
                 if (rc.canPlaceCatTrap(target.location)) {
                     rc.placeCatTrap(target.location);
                     rc.setIndicatorString("TRAPPED CAT!");
                 } else {
                     boolean trapped = false;
                     Direction toCat = myLoc.directionTo(target.location);
                     Direction[] tryDirs = {toCat, toCat.rotateLeft(), toCat.rotateRight(), 
                                          toCat.rotateLeft().rotateLeft(), toCat.rotateRight().rotateRight(),
                                          toCat.opposite().rotateLeft(), toCat.opposite().rotateRight(), toCat.opposite()};
                     
                     for (Direction d : tryDirs) {
                         MapLocation trapLoc = myLoc.add(d);
                         if (rc.canPlaceCatTrap(trapLoc)) {
                             rc.placeCatTrap(trapLoc);
                             rc.setIndicatorString("TRAPPED CAT (Smart)!");
                             trapped = true;
                             break;
                         }
                     }
                     if (!trapped) {
                         if (myLoc.distanceSquaredTo(target.location) > 2) {
                             BugPathing.move(rc, target.location);
                         }
                     }
                 }
            } else if (rc.canAttack(target.location)) {
                // ATTACK
                int myCheese = rc.getGlobalCheese();
                int reserve = 2000;
                int spend = 0;
                if (targetType.contains("KING") || targetType.contains("HQ")) {
                     spend = Math.min(myCheese, 200);
                } else if (myCheese > reserve) {
                    spend = Math.min(myCheese - reserve, 50);
                }
                rc.attack(target.location, spend);
                rc.setIndicatorString("ATTACKING! Spend=" + spend);
            } else {
                // MOVE TO ATTACK
                int dist = myLoc.distanceSquaredTo(target.location);
                if (dist <= 13) {
                    Direction toTarget = myLoc.directionTo(target.location);
                    if (rc.isTurningReady() && toTarget != Direction.CENTER && rc.getDirection() != toTarget) {
                        rc.turn(toTarget);
                        rc.setIndicatorString("TURNING to " + toTarget);
                    } else {
                         rc.setIndicatorString("Face aligned/Turning CD? Dist=" + dist);
                         BugPathing.move(rc, target.location);
                    }
                } else {
                    BugPathing.move(rc, target.location);
                }
            }
        } else {
            // COOLDOWN LOGIC (Smart Kiting)
            // If action is NOT ready, we retreat!
            if (rc.getActionCooldownTurns() >= 10) {
                 Direction away = myLoc.directionTo(target.location).opposite();
                 if (rc.canMove(away)) {
                     rc.move(away);
                     rc.setIndicatorString("KITING (Cooldown)");
                 } else if (rc.canMove(away.rotateLeft())) {
                     rc.move(away.rotateLeft());
                     rc.setIndicatorString("KITING (Cooldown)");
                 } else if (rc.canMove(away.rotateRight())) {
                     rc.move(away.rotateRight());
                     rc.setIndicatorString("KITING (Cooldown)");
                 } else {
                 // Cornered: Slide/Fight
                 BugPathing.move(rc, target.location);
             }
            } else {
                 // Almost ready: Hold/Position
                 rc.setIndicatorString("HOLDING (Aiming)");
            }
        }
    }

    public static RobotInfo getBestTarget(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        RobotInfo best = null;
        double bestScore = -99999; 
        MapLocation myLoc = rc.getLocation();
        
        for (RobotInfo enemy : enemies) {
            double score = 0;
            int distSq = myLoc.distanceSquaredTo(enemy.location);
            String typeStr = enemy.type.toString();
            boolean isKing = typeStr.contains("KING");
            boolean isCat = typeStr.contains("CAT");
            boolean inAttackRange = distSq <= 13; // Approximate range (Soldier/Archer max)
            
            // Base score
            score += 100;

            if (isKing) {
                score += 100000; 
                score -= distSq * 10; 
            } else if (isCat) {
                score += 50000;
                score -= distSq * 5;
            } else if (inAttackRange) {
                score += 10000;
                double maxHp = (double) enemy.type.health;
                double percentMissing = (maxHp - enemy.health) / maxHp;
                score += (percentMissing * 500); 
                score += (100 - maxHp); 
            } else {
                score += 1000; // Vision range pursuit
                // Prioritize Soldier > Worker
                if (typeStr.contains("SOLDIER") || typeStr.contains("ARCHER")) score += 200;
                score -= distSq * 2;
            }
            
            // Kill Confirm (Absolute HP)
            if (enemy.health <= 20) score += 5000; 

            if (score > bestScore) {
                bestScore = score;
                best = enemy;
            }
        }
        
        if (best != null) {
            rc.setIndicatorLine(myLoc, best.location, 255, 0, 0); 
        }
        return best;
    }

    // FLEE: Move away from enemies
    public static void flee(RobotController rc, RobotInfo[] enemies) throws GameActionException {
        if (enemies.length == 0) return;
        
        // Calculate average enemy location (Centroid)
        int x = 0, y = 0;
        for (RobotInfo e : enemies) {
            x += e.location.x;
            y += e.location.y;
        }
        MapLocation centroid = new MapLocation(x / enemies.length, y / enemies.length);
        
        // Move opposite
        Direction away = centroid.directionTo(rc.getLocation());
        if (rc.canMove(away)) {
            rc.move(away);
        } else if (rc.canMove(away.rotateLeft())) {
            rc.move(away.rotateLeft());
        } else if (rc.canMove(away.rotateRight())) {
            rc.move(away.rotateRight());
        } else {
            // Cornered? Randomize.
            RobotPlayer.randomMove(rc);
        }
    }
}
