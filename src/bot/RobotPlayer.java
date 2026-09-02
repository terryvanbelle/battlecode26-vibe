package bot;

import battlecode.common.*;

import java.util.Random;

/**
 * Iteration 0 (see TRAINING_ALGORITHM.md): deliberately minimal. The Rat
 * King spawns exactly one Baby Rat and otherwise does nothing; the Baby Rat
 * wanders with random legal moves. No economy, no combat, no backstab
 * logic. This exists to validate build/run/replay/Gauntlet end to end
 * before any real strategy is written.
 *
 * Live bytecode-budget monitoring is wired in from the start (see
 * TRAINING_ALGORITHM.md's Iteration 0 section) rather than bolted on later.
 */
public class RobotPlayer {
    static final Random rng = new Random(6147);

    static final Direction[] DIRECTIONS = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST,
    };

    static boolean spawnedOne = false;

    public static void run(RobotController rc) throws GameActionException {
        while (true) {
            int roundAtTurnStart = rc.getRoundNum();
            try {
                if (rc.getType() == UnitType.RAT_KING) {
                    runRatKing(rc);
                } else {
                    runBabyRat(rc);
                }
            } catch (GameActionException e) {
                System.out.println("GameActionException: " + e.getMessage());
            } catch (Exception e) {
                System.out.println("Exception: " + e);
                e.printStackTrace();
            } finally {
                reportBytecodeBudget(rc, roundAtTurnStart);
                Clock.yield();
            }
        }
    }

    static void runRatKing(RobotController rc) throws GameActionException {
        if (!spawnedOne) {
            for (Direction dir : DIRECTIONS) {
                MapLocation loc = rc.adjacentLocation(dir);
                if (rc.canBuildRat(loc)) {
                    rc.buildRat(loc);
                    spawnedOne = true;
                    break;
                }
            }
        }
    }

    static void runBabyRat(RobotController rc) throws GameActionException {
        if (rc.canMoveForward()) {
            rc.moveForward();
        } else {
            Direction dir = DIRECTIONS[rng.nextInt(DIRECTIONS.length)];
            if (rc.canTurn(dir)) {
                rc.turn(dir);
            }
        }
    }

    /**
     * Compares the round number before/after this robot's own turn logic to
     * detect a confirmed bytecode overrun (the engine pauses mid-instruction
     * and resumes next round with no exception -- see TRAINING_ALGORITHM.md).
     * Also compares live bytecode usage against this unit type's limit to
     * catch near-misses before they become overruns. Surfaced via the
     * robot's own indicator string so it's visible in every replay.
     */
    static void reportBytecodeBudget(RobotController rc, int roundAtTurnStart) {
        int used = Clock.getBytecodeNum();
        int limit = rc.getType().getBytecodeLimit();
        boolean overran = rc.getRoundNum() != roundAtTurnStart;
        boolean nearMiss = used > (int) (limit * 0.9);
        String status = overran ? "OVERRAN" : (nearMiss ? "near-limit" : "ok");
        rc.setIndicatorString("bytecode " + used + "/" + limit + " (" + status + ")");
    }
}
