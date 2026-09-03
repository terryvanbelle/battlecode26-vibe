package bench_stroke;

import battlecode.common.GameActionException;

/**
 * Behaviors are how Baby Rats or Rat Kings can execute specific actions.
 * Each behavior should ideally correspond to a unique state and have cohesive logic.
 */
public interface Behavior {
    void execute() throws GameActionException;
}
