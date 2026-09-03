package bench_stroke.BabyRatBehaviors;

import bench_stroke.Behavior;
import bench_stroke.Pathfinding.Pathfinding;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.*;
import bench_stroke.Communication.*;

import static bench_stroke.RobotPlayer.*;
import bench_stroke.DataStructures.FastLocSet;
import static bench_stroke.BabyRat.*;

import java.util.Random;

import bench_stroke.Utilities;
import bench_stroke.SymmetryManager;

public class BRThrownCarried implements Behavior {
    
    //Instance Variables (persist over state changes)

    //Singleton Stuff
    private static BRThrownCarried instance;
    private BRThrownCarried() {}
    public static BRThrownCarried getInstance() {
        if(instance == null) {
            instance = new BRThrownCarried();
        }

        return instance;
    }

    @Override
    public void execute() throws GameActionException {
        FastLocSet allies = buildAllySet();


        if (nearestSeenEnemy != null) {
            Communicator.sendSqueak(new PresenceSqueakInfo(0, nearestSeenEnemy.location, nearestSeenEnemy.direction, nearestSeenEnemy.health));
        }

        //not really sure how to help here... i guess if we are 
        // really low on health and see allies, we should disintegrate?
        if (rc.isBeingCarried() && rc.senseRobotAtLocation(rc.getLocation()).team != rc.getTeam()) {
            RobotInfo carrier = rc.senseRobotAtLocation(currentLocation);
            Direction potentialThrow = carrier.direction;
            //could check more?
            MapLocation[] potentialTargets = {currentLocation.add(potentialThrow).add(potentialThrow), currentLocation.add(potentialThrow.rotateLeft()).add(potentialThrow.rotateLeft()), currentLocation.add(potentialThrow.rotateRight()).add(potentialThrow.rotateRight())};
            for (MapLocation loc : potentialTargets) {
                if (allies.contains(loc)) {
                    if (rc.getHealth() < 20) {
                      //  System.out.println("disintegrating");
                        rc.disintegrate();
                    }
                    else return;
                }
            }
        }
        //this seems more simple: lets trace out projected path, 
        //and if we hit an ally and we will die, then better to just suicide
        else if (rc.isBeingThrown()) {
            MapLocation toCheck = currentLocation;
            for(int i = 0; i < 4; i++) {
                toCheck = toCheck.add(thrownDirection);
                if (allies.contains(toCheck)) {
                    if (rc.getHealth() < 20){
                       // System.out.println("disintegrating");
                        rc.disintegrate();
                    }
                    else return;
                }
            }
        }
    }

    //builds a loc set of allies we know about
    public static FastLocSet buildAllySet() {
        FastLocSet allies = new FastLocSet();
        Squeak[] squeaks = Communicator.getAllSqueaks(0);        
        for (Squeak squeak : squeaks) {
            if (squeak == null) continue;
            if (squeak.squeakInfo instanceof PresenceSqueakInfo) {
                allies.add(squeak.source);    
            }
        }
        return allies;
    }
}