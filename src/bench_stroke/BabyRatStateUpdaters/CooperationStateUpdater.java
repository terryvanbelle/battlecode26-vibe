package bench_stroke.BabyRatStateUpdaters;

import bench_stroke.Behavior;
import bench_stroke.StateUpdater;
import battlecode.common.GameActionException;

public class CooperationStateUpdater implements StateUpdater {
    private static CooperationStateUpdater instance;
    private CooperationStateUpdater() {}
    public static CooperationStateUpdater getInstance() {
        if(instance == null) {
            instance = new CooperationStateUpdater();
        }

        return instance;
    }

    @Override
    public Behavior decideBehavior() throws GameActionException {
        return null;
    }
}
