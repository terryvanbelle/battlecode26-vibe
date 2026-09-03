package bench_stroke;

import bench_stroke.Behavior;
import battlecode.common.GameActionException;

/**
 * State Updaters are how Baby Rats or Rat Kings can decide which behavior to run.
 * It is vital that State Updaters which correspond to Rat Kings only return behaviors meant for Rat Kings,
 * vice versa for Baby Rats.
 */
public interface StateUpdater {
    Behavior decideBehavior() throws GameActionException;
}
