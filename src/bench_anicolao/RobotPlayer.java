package bench_anicolao;

import battlecode.common.*;
import java.util.*;

public class RobotPlayer {
    static final Random rng = new Random(6147);
    static final Direction[] directions = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };
    
    static int turnCount = 0;
    static MapLocation kingLoc = null;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            turnCount++;
            try {
                rc.setIndicatorString("ROOT Loop " + turnCount);
                // System.out.println("DEBUG: ROOT Loop " + turnCount + " ID " + rc.getID() + " Type " + rc.getType());
                String typeName = rc.getType().toString();
                if (typeName.contains("KING") || typeName.contains("HQ")) {
                    runKing(rc);
                } else {
                    runRat(rc);
                }
            } catch (GameActionException e) {
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("CRASH: " + e.getMessage());
                e.printStackTrace();
            } catch (Throwable e) {
                System.out.println("FATAL: " + e.getMessage());
                e.printStackTrace();
            } finally {
                Clock.yield();
            }
        }
    }

    static void runKing(RobotController rc) throws GameActionException {
      try {
        RobotInfo[] enemyBots = rc.senseNearbyRobots(400, rc.getTeam().opponent());
        RobotInfo[] neutralBots = rc.senseNearbyRobots(400, Team.NEUTRAL);
        
        // Merge enemies
        int totalEnemies = enemyBots.length + neutralBots.length;
        RobotInfo[] enemies = new RobotInfo[totalEnemies];
        System.arraycopy(enemyBots, 0, enemies, 0, enemyBots.length);
        System.arraycopy(neutralBots, 0, enemies, enemyBots.length, neutralBots.length);
        
        boolean enemiesNearby = enemies.length > 0;
        rc.writeSharedArray(0, enemiesNearby ? 1 : 0);
        
        if (enemiesNearby) {
            int dx = 0, dy = 0;
            for (RobotInfo enemy : enemies) {
                dx += (enemy.location.x - rc.getLocation().x);
                dy += (enemy.location.y - rc.getLocation().y);
            }
            // Average direction
            if (dx != 0 || dy != 0) {
                 Direction avgDir = rc.getLocation().directionTo(rc.getLocation().translate(dx, dy));
                 rc.writeSharedArray(4, avgDir.ordinal());
            } else {
                 rc.writeSharedArray(4, 9); // 9 = Center/Unknown
            }
            
            // KING REPORTING: If Enemy King seen, tell everyone! (Only King can write)
            for (RobotInfo e : enemies) {
                 if (e.type.toString().contains("KING")) {
                      rc.writeSharedArray(5, e.location.x);
                      rc.writeSharedArray(6, e.location.y);
                 }
            }
        } else {
            rc.writeSharedArray(4, 9);
        }
        
        // Init Enemy King Location (if Turn 1)
        if (rc.getRoundNum() == 1) {
            rc.writeSharedArray(5, 99);
            rc.writeSharedArray(6, 99);
        }
        
        // CHEESE STATUS BROADCAST
        // 0 = FINE (> 500)
        // 1 = LOW (< 500)
        // 2 = CRITICAL (< 200) -> ALL FARMERS DELIVER
        int cheeseStatus = 0;
        int globalCheese = rc.getGlobalCheese();
        
        if (globalCheese < 200) {
            cheeseStatus = 2; // CRITICAL
        } else if (globalCheese < 500) {
            cheeseStatus = 1; // LOW
        }
        rc.writeSharedArray(1, cheeseStatus); // Re-purposing SA[1] from HP Deficit to Cheese Status
        
        MapLocation myLoc = rc.getLocation();
        rc.writeSharedArray(2, myLoc.x);
        rc.writeSharedArray(3, myLoc.y);

        // Bootstrap: Mine only if ON TOP
        if (rc.canPickUpCheese(rc.getLocation())) {
            rc.pickUpCheese(rc.getLocation());
            System.out.println("KING MINED LOCAL");
        }
        if (rc.getRoundNum() == 1) {
            System.out.println("I am a " + rc.getType());
            for (UnitType t : UnitType.values()) {
                System.out.println("TYPE: " + t);
            }
        }
        
        // DEBUG KING VITALS
        int currentCheese = rc.getGlobalCheese();
        int currentHP = rc.getHealth();
        rc.setIndicatorString("HP: " + currentHP + " | Cheese: " + currentCheese);
        System.out.println("[KING VITALS] R" + rc.getRoundNum() + " HP: " + currentHP + " Cheese: " + currentCheese);

        // THREAT ASSESSMENT (WAR MODE)
        // Trigger War Mode if:
        // 1. Enemies nearby (Standard Defense)
        // 2. HP < Max (Taking Damage)
        // REMOVED: Cheese < 300 (This caused death spiral by reducing farmers)
        // 4. Round > 1500 (Endgame)
        
        int warMode = 0;
        int maxHp = rc.getType().health;
        
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(rc.getType().getVisionRadiusSquared(), rc.getTeam().opponent());
        boolean isThreatened = nearbyEnemies.length > 0;
        
        // Broadcast Enemy Direction for Defenders
        if (isThreatened) {
            MapLocation enemyLoc = nearbyEnemies[0].location; // Just target the first one (closest?)
            Direction dirToEnemy = myLoc.directionTo(enemyLoc);
            rc.writeSharedArray(4, dirToEnemy.ordinal());
        } else {
             rc.writeSharedArray(4, 99); // 99 = No Enemy (Use > 7 to indicate invalid)
        }
        
        // HYBRID PIVOT: War Mode logic updated for "Eco First"
        int nearbyAllies = rc.senseNearbyRobots(400, rc.getTeam()).length;
        int totalCheese = rc.getGlobalCheese();
        
        // Trigger War Mode if:
        // 1. Critical Mass (Army > 20? No, rely on "Cheese Overflow" to build mass)
        // 2. Infinite Eco (Cheese > 2000)
        // 3. Endgame (Round > 1500)
        // 4. Under Siege (HP < Max)
        
        if (currentHP < maxHp || turnCount > 1500 || totalCheese > 2000 || nearbyAllies > 25) {
            warMode = 1;
        }
        
        // BOOTSTRAP: Early game safety.
        // Force War Mode (90% Soldiers) until we have a standing army of 12.
        if (nearbyAllies < 12) {
             warMode = 1;
        }
        
        // System.out.println("KING DEBUG: WarMode=" + warMode + " HP=" + currentHP + "/" + maxHp + " Allies=" + nearbyAllies + " Cheese=" + totalCheese);
        rc.writeSharedArray(7, warMode);
        
        // DEFENSE SIGNAL (Index 0): Distinct from War Mode.
        // Only trigger Emergency Defense if enemies are ACTUALLY nearby or we are taking critical damage.
        // We use a threshold to prevent jitter.
        int defenseSignal = 0;
        if (isThreatened || currentHP < maxHp - 10) defenseSignal = 1; // Trigger if damaged (accounting for small regen/rounding)
        
        // System.out.println("KING DEBUG: DefenseSignal=" + defenseSignal + " EnemiesNearby=" + isThreatened);
        rc.writeSharedArray(0, defenseSignal);


        if (rc.getGlobalCheese() < 150 && nearbyAllies < 2) {
            MapLocation cheeseLoc = findCheese(rc); 
            System.out.println("KING SEARCHING CHEESE: " + cheeseLoc);
            if (cheeseLoc != null) {
                swarmMove(rc, cheeseLoc);
            } else {
                rc.setIndicatorString("KING EXPLORING");
                randomMove(rc);
            }
        }

        // Mobile King: Move away from start if safe
        // Try to reach a map corner or generally move away from start loc.
        // Also evade if enemies are near.
        if (turnCount == 1) {
            // Store starting location in an unused persistent array index if possible?
            // Actually, just using turnCount logic is fine.
        }
        
        // MOVEMENT LOGIC
        if (enemiesNearby) {
            // EVADE: Move away from average enemy direction
            Direction evadeDir = Direction.CENTER;
            int enemyDirOrd = rc.readSharedArray(4);
            if (enemyDirOrd >= 0 && enemyDirOrd < 8) {
                 Direction enemyDir = Direction.values()[enemyDirOrd];
                 evadeDir = enemyDir.opposite();
            }
            if (evadeDir != Direction.CENTER) {
                if (rc.canMove(evadeDir)) {
                     rc.move(evadeDir);
                } else if (rc.canMove(evadeDir.rotateLeft())) {
                     rc.move(evadeDir.rotateLeft());
                } else if (rc.canMove(evadeDir.rotateRight())) {
                     rc.move(evadeDir.rotateRight());
                }
            }
        } else {
             // NO THREAT: Move to a "Safe Corner" (Hidden Strategy)
             // Determine Safety Destination once
             int mapW = rc.getMapWidth();
             int mapH = rc.getMapHeight();
             MapLocation startLoc = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3)); 
             // Note: SA 2/3 are updated to CURRENT loc every turn, so valid for finding start only at turn 1? 
             // Actually, we can just pick a corner based on ID or simple math.
             
             // Strategy: Go to (0, H) or (W, 0).
             // Let's pick based on map quadrants.
             MapLocation dest = new MapLocation(0, mapH-1);
             if (startLoc.distanceSquaredTo(dest) < (mapW*mapW)/4) {
                 // If we started near (0,H), go to (W,0)
                 dest = new MapLocation(mapW-1, 0);
             }
             
             // Move towards Dest
             if (rc.getLocation().distanceSquaredTo(dest) > 4) {
                 rc.setIndicatorString("MOVING TO SAFE CORNER: " + dest);
                 BugPathing.move(rc, dest);
             } else {
                 rc.setIndicatorString("HOLDING SAFE CORNER");
                 // Jitter slightly to avoid being static target?
                 if (rng.nextInt(100) < 10) randomMove(rc);
             }
        }

        // SPAWNING LOGIC
        int cheese = rc.getGlobalCheese();
        int ratCost = rc.getCurrentRatCost();
        // Reserve enough cheese for King survival (2 per round)
        int reserve = 1000; // Default: Eco/Defense buffer
        
        int wm = rc.readSharedArray(7);
        if (wm == 1) {
            reserve = 20; // Panic Spawn / Rush Spawn (Drain bank)
        } 

        // BOOTSTRAP: If we have very few units (early game or wiped), spawn aggressively!
        int alliesForBootstrap = rc.senseNearbyRobots(400, rc.getTeam()).length;
        if (alliesForBootstrap < 8) { // 8 allows for 2 farmers, 2 soldiers, etc.
             reserve = 10;
             rc.setIndicatorString("BOOTSTRAP SPAWN: Low Reserve");
        }
        
        // CRITICAL STARVATION: If we have NO units (or just 1), we must spawn ASAP.
        if (alliesForBootstrap < 2) {
            reserve = 0;
            rc.setIndicatorString("CRITICAL SPAWN: NO RESERVE");
        } 

        // IF safe, we can be more aggressive? No, starvation is instant death.
        // Always maintain reserve.

        // EXPANSION: Second King
        // Only if we have massive excess? 
        // Actually, just regular spawning is fine.
        
        if (cheese > ratCost + reserve) {
            // DIRECTIONAL SPAWNING
            // Predict Enemy King location to spawn units towards the fight.
            MapLocation startLoc = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
            MapLocation enemyKing = Exploration.predictEnemyKing(rc, startLoc);
            
            Direction targetDir = Direction.NORTH; // Default
            if (enemyKing != null) {
                targetDir = rc.getLocation().directionTo(enemyKing);
            }
            
            // Create priority list: Target, Left, Right, ...
            Direction[] priorityDirs = {
                targetDir,
                targetDir.rotateLeft(),
                targetDir.rotateRight(),
                targetDir.rotateLeft().rotateLeft(),
                targetDir.rotateRight().rotateRight(),
                targetDir.opposite().rotateLeft(),
                targetDir.opposite().rotateRight(),
                targetDir.opposite()
            };

            for (Direction d : priorityDirs) {
                MapLocation spawnLoc = rc.getLocation().add(d).add(d); // Range 2 (Default build dist)
                if (rc.canBuildRat(spawnLoc)) {
                    rc.buildRat(spawnLoc);
                    rc.setIndicatorString("SPAWN: Direction " + d);
                    // System.out.println("KING SPAWNED RAT at " + spawnLoc + " (" + d + ")");
                    break;
                }
            }
        }
        
        // UPGRADE RAT TO KING logic... handled by Rats checking cheese?
        // If we want a second king, we need 5000+ cheese usually to justify it early on?
        // But the user asked for "Second King" strategy.
        // We need a Rat to detect it can upgrade.
        // King just needs to not spend ALL cheese if we are nearing upgrade threshold?
        // Let's set a high reserve if we have many units?
        // For now, simple survival is priority.
      } catch (Exception e) {
          rc.setIndicatorString("ERROR: " + e.getMessage());
          e.printStackTrace();
      }
    }

    static void runRat(RobotController rc) throws GameActionException {
        // HYBRID PIVOT:
        // By Default: ECONOMY (Farmers).
        // If WarMode: SOLDIERS (Defenders/Attackers).
        
        int warMode = rc.readSharedArray(7);
        // int role = rc.getID() % 10;
        
        boolean isFarmer = true;
        boolean isSoldier = false;
        boolean isScout = false;
        
        // WAR MODE (1) -> Everything is a Soldier
        // PEACE MODE (0) -> Farmers unless excess?
        // Actually, King controls the population usage via SharedArray or Spawning?
        // King just spawns. The unit must decide.
        // Let's use ID but skew heavily.
        
        // UNIVERSAL SCOUT ROLE:
        // Always reserve 10% for Intelligence (Role 9).
        // War Mode: 10% Farmer, 80% Soldier, 10% Scout.
        // Peace Mode: 80% Farmer, 10% Soldier, 10% Scout.
        
        int role = rc.getID() % 10;
        System.out.println("DEBUG: Rat Run ID=" + rc.getID() + " Role=" + role + " WarMode=" + warMode);
        
        if (role == 9) {
            isScout = true;
            isFarmer = false;
            isSoldier = false;
        } else if (warMode == 1) {
            // WAR: 1 Farmer (Logistics), 8 Soldiers
            if (role < 1) isFarmer = true;
            else isFarmer = false;
        } else {
            // PEACE: 8 Farmers, 1 Soldier (Defender)
            if (role < 8) isFarmer = true;
            else isFarmer = false;
        }
        
        // Is Soldier?
        if (!isScout && !isFarmer) isSoldier = true;

        if (isFarmer) {
             runCarrier(rc);
        } else if (isScout) {
             runScout(rc);
        } else {
             runSoldier(rc);
        }
    }

    static void runScout(RobotController rc) throws GameActionException {
        // SCOUT LOGIC:
        // 1. Survival: Flee if threatened.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemies.length > 0) {
            rc.setIndicatorString("SCOUT FLEEING");
            Micro.flee(rc, enemies);
            return;
        }
        
        // 2. Intel: Check for Enemy King
        for (RobotInfo e : enemies) { // Wait, enemies length is 0 here due to check above?
             // Ah, flee might not trigger if enemies are far but visible?
             // No, senseNearbyRobots(-1) gets all visible.
             // If we want to report before fleeing, needs reordering.
        }
        
        // Re-sense for reporting (or just use the array if we didn't return)
        // Actually, if enemies > 0, we flee. But we should report King first!
        
        // Correct Order:
        // 1. Scan & Report
        // 2. Flee if necessary
        // 3. Explore
        
        boolean foundKing = false;
        if (enemies.length > 0) {
             for (RobotInfo e : enemies) {
                 if (e.type.toString().contains("KING")) {
                     rc.setIndicatorString("TARGET ACQUIRED: " + e.location);
                     System.out.println("TARGET ACQUIRED: " + e.location);
                     rc.writeSharedArray(5, e.location.x);
                     rc.writeSharedArray(6, e.location.y);
                     foundKing = true;
                 }
             }
             rc.setIndicatorString("SCOUT FLEEING");
             Micro.flee(rc, enemies);
             return;
        }
        
        // 3. Explore
        // Move towards Predicted King. If there, Spiraling or Random?
        MapLocation myStart = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
        MapLocation target = Exploration.predictEnemyKing(rc, myStart);
        
        // If we have a confirmed target, verify it?
        int confirmedX = rc.readSharedArray(5);
        int confirmedY = rc.readSharedArray(6);
        if (confirmedX != 0) {
             target = new MapLocation(confirmedX, confirmedY);
             // If we are AT the confirmed location and no King, clear it?
             if (rc.canSenseLocation(target)) {
                 RobotInfo info = rc.senseRobotAtLocation(target);
                 if (info == null || !info.type.toString().contains("KING")) {
                      // King moved or died. Clear intel.
                      rc.writeSharedArray(5, 0); 
                      rc.writeSharedArray(6, 0);
                      rc.setIndicatorString("TARGET LOST (Cleared Intel)");
                 }
             }
        }
        
        if (target != null) {
            rc.setIndicatorString("SCOUTING -> " + target);
            BugPathing.move(rc, target);
        } else {
            randomMove(rc);
        }
    }

    static void runCarrier(RobotController rc) throws GameActionException {
        int cheese = rc.getRawCheese();
        boolean isFull = cheese >= 50; 
        
        MapLocation kingLoc = findKing(rc);
        MapLocation cheeseLoc = findCheese(rc); 

        // 1. UNIVERSAL FEED
        if (kingLoc != null) {
            if (cheese > 0) {
                int distSq = rc.getLocation().distanceSquaredTo(kingLoc);
                if (distSq <= 2) {
                     boolean canTransfer = rc.canTransferCheese(kingLoc, cheese);
                     if (canTransfer) {
                         rc.transferCheese(kingLoc, cheese);
                         rc.setIndicatorString("FED KING (Universal) " + cheese);
                         System.out.println("SUCCESS: Transferred " + cheese);
                         return; 
                     }
                }
            }
        }

        // 2. FULL DELIVERY
        if (isFull) {
            if (kingLoc != null) {
                MapLocation target = kingLoc;
                int minDist = 9999;
                Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
                for (Direction d : cardinals) {
                    MapLocation cand = kingLoc.add(d);
                    int dist = rc.getLocation().distanceSquaredTo(cand);
                    if (dist < minDist) {
                         minDist = dist;
                         target = cand;
                    }
                }
                swarmMove(rc, target);
                return;
            } else {
                 rc.setIndicatorString("LOST KING");
                 swarmMove(rc, rc.getLocation().translate(rng.nextInt(3)-1, rng.nextInt(3)-1));
                 return;
            }
        }

        // 3. MINING (The Stomp)
        if (cheeseLoc != null) {
            rc.setIndicatorString("MINING: " + cheeseLoc);
            if (rc.getLocation().equals(cheeseLoc) && rc.canPickUpCheese(cheeseLoc)) {
                rc.pickUpCheese(cheeseLoc);
            } else {
                swarmMove(rc, cheeseLoc);
            }
            return;
        }

        // 4. PARTIAL DELIVERY
        if (cheese > 0) {
             if (kingLoc != null) {
                 swarmMove(rc, kingLoc); 
                 return;
             }
        }

        // 5. IDLE / EVACUATE
        if (kingLoc != null) {
             int distSq = rc.getLocation().distanceSquaredTo(kingLoc);
             if (distSq <= 2) {
                 Direction away = kingLoc.directionTo(rc.getLocation());
                 if (away == Direction.CENTER) away = directions[rng.nextInt(directions.length)];
                 if (away.dx == 0 || away.dy == 0) { 
                     away = (rc.getID() % 2 == 0) ? away.rotateLeft() : away.rotateRight(); 
                 }
                 rc.setIndicatorString("EVACUATING");
                 MapLocation target = kingLoc.add(away).add(away); 
                 swarmMove(rc, target);
                 return;
             }
             Direction away = kingLoc.directionTo(rc.getLocation());
             swarmMove(rc, rc.getLocation().add(away));
        } else {
             MapLocation target = Exploration.getExploreTarget(rc);
             rc.setIndicatorString("EXPLORE: " + target);
             swarmMove(rc, target);
        }
    }
    
    static void runSoldier(RobotController rc) throws GameActionException {
        System.out.println("DEBUG: Soldier Run ID=" + rc.getID());
        // HYBRID DEFENSE:
        // Default: DEFEND (Patrol King/Cheese).
        // Trigger: War Mode -> RUSH.
        // Trigger: Emergency Defense (Under Siege).
        
        // 1. ATTACK (Priority 1)
        int visionRadius = 400; 
        RobotInfo[] enemyBots = rc.senseNearbyRobots(visionRadius, rc.getTeam().opponent());
        
        // NEUTRALS: Ignore them for now. We want to kill the King.
        if (enemyBots.length > 0) {
            // REPORT KING IF SEEN
            for (RobotInfo e : enemyBots) {
                if (e.type.toString().contains("KING")) {
                    rc.setIndicatorString("FOUND KING! Attacking " + e.location);
                }
            }
            Micro.runSoldierMicro(rc, enemyBots);
            return;
        }
        
        // 2. EMERGENCY DEFENSE (Under Siege) - Overrides War/Peace
        int kingUnderAttack = rc.readSharedArray(0);
        if (kingUnderAttack == 1) {
            MapLocation king = findKing(rc);
             if (king != null) {
                // System.out.println("SOLDIER: EMERGENCY DEFENSE! ID=" + rc.getID());
                rc.setIndicatorString("EMERGENCY DEFENSE ACTIVATED");
                // Targeted Defense logic
                int enemyDirOrd = rc.readSharedArray(4);
                if (enemyDirOrd >= 0 && enemyDirOrd < 8) {
                    Direction enemyDir = Direction.values()[enemyDirOrd];
                    // Move to flank
                    BugPathing.move(rc, king.add(enemyDir).add(enemyDir));
                } else {
                     BugPathing.move(rc, king);
                }
                return;
            }
        }
        
        // 3. WAR MODE / PEACE MODE
        int warMode = rc.readSharedArray(7);
        MapLocation myStart = new MapLocation(rc.readSharedArray(2), rc.readSharedArray(3));
        
        // TARGETING PRIORITY:
        // 1. Confirmed Intel (SharedArray 5, 6)
        // 2. Predicted Location
        MapLocation target = null;
        int confirmedX = rc.readSharedArray(5);
        int confirmedY = rc.readSharedArray(6);
        if (confirmedX != 0) {
            target = new MapLocation(confirmedX, confirmedY);
            rc.setIndicatorString("TARGETING CONFIRMED: " + target);
        } else {
            target = Exploration.predictEnemyKing(rc, myStart);
        }
        
        MapLocation kingLoc = findKing(rc);

        if (warMode == 1) {
             // RUSH LOGIC
             if (target != null) {
                 // Dynamic Group Up (Iteration 0049)
                 int dim = (rc.getMapWidth() + rc.getMapHeight()) / 2;
                 int GROUP_SIZE = 6;
                 if (dim <= 32) GROUP_SIZE = 4;
                 else if (dim >= 50) GROUP_SIZE = 8;
                 
                 int distToStart = rc.getLocation().distanceSquaredTo(myStart);
                 int RALLY_DIST = 100;
                 boolean committed = distToStart > RALLY_DIST;
                 int nearbyFriends = rc.senseNearbyRobots(36, rc.getTeam()).length;
                 
                 if (!committed && nearbyFriends < GROUP_SIZE) {
                     rc.setIndicatorString("WAITING FOR GROUP (" + nearbyFriends + ")");
                     MapLocation rally = myStart;
                     if (target != null) {
                         Direction toEnemy = myStart.directionTo(target);
                         rally = myStart.translate(toEnemy.dx * 4, toEnemy.dy * 4);
                     }
                     BugPathing.move(rc, rally);
                     return;
                 }
                 
                 rc.setIndicatorString("RUSHING (WAR MODE)");
                 BugPathing.move(rc, target);
                 return;
             } else {
                 // Fallback if target is null (shouldn't happen, but prevents blocking)
                 rc.setIndicatorString("RUSHING (NO TARGET - RANDOM)");
                 randomMove(rc);
             }
        } else {
             // PEACE MODE: PATROL
             rc.setIndicatorString("PATROLLING (PEACE)");
             if (kingLoc != null) {
                 if (rc.getLocation().distanceSquaredTo(kingLoc) > 49) {
                     BugPathing.move(rc, kingLoc);
                 } else {
                     if (rng.nextInt(10) < 3) randomMove(rc);
                 }
             } else {
                 randomMove(rc);
             }
        }
    }
    
    static MapLocation findKing(RobotController rc) throws GameActionException {
         if (kingLoc != null && rc.canSenseLocation(kingLoc)) {
            RobotInfo info = rc.senseRobotAtLocation(kingLoc);
            if (info != null && info.team == rc.getTeam() && info.type.toString().contains("KING")) {
                return kingLoc;
            }
        }
        int kx = rc.readSharedArray(2);
        int ky = rc.readSharedArray(3);
        if (kx != 0 || ky != 0) { 
            kingLoc = new MapLocation(kx, ky);
            return kingLoc;
        }
        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo robot : allies) {
            if (robot.type.toString().contains("KING") || robot.type.toString().contains("HQ")) {
                kingLoc = robot.location;
                return kingLoc;
            }
        }
        return kingLoc;
    }
    
    static MapLocation findCheese(RobotController rc) throws GameActionException {
        MapInfo[] nearby = rc.senseNearbyMapInfos();
        MapLocation bestLoc = null;
        int maxCheese = -1;
        
        for (MapInfo info : nearby) {
             int amt = info.getCheeseAmount();
             if (amt > 0) {
                 // Prioritize Amount over Distance (Greedy for Richness)
                 // But don't go too far for slightly more?
                 // Simple greedy: Find Max Cheese.
                 if (amt > maxCheese) {
                     maxCheese = amt;
                     bestLoc = info.getMapLocation();
                 } else if (amt == maxCheese) {
                     if (bestLoc == null || rc.getLocation().distanceSquaredTo(info.getMapLocation()) < rc.getLocation().distanceSquaredTo(bestLoc)) {
                         bestLoc = info.getMapLocation();
                     }
                 }
             }
        }
        return bestLoc;
    }

    static void randomMove(RobotController rc) throws GameActionException {
        int r = rng.nextInt(8);
        for (int i = 0; i < 8; i++) {
            Direction d = directions[(r + i) % 8];
            if (rc.canMove(d)) {
                rc.move(d);
                return;
            }
        }
    }

    static void swarmMove(RobotController rc, MapLocation target) throws GameActionException {
        if (target == null || !rc.isMovementReady()) return;
        if (target.equals(rc.getLocation())) return;
        Direction dir = rc.getLocation().directionTo(target);
        if (dir == Direction.CENTER) return;
        try {
            if (rc.canMove(dir)) {
                rc.move(dir);
            } else if (rc.canMove(dir.rotateLeft())) {
                rc.move(dir.rotateLeft());
            } else if (rc.canMove(dir.rotateRight())) {
                rc.move(dir.rotateRight());
            } else {
                randomMove(rc);
            }
        } catch (GameActionException e) {
            rc.setIndicatorString("SWARM_MOVE ERROR: " + e.getMessage());
        } catch (Exception e) {
             e.printStackTrace();
        }
    }
}
