package bench_stroke.RatKingStateUpdaters;

import bench_stroke.Behavior;
import bench_stroke.StateUpdater;
import bench_stroke.RatKingBehaviors.RKFlee;
import bench_stroke.RatKingBehaviors.RKHeadquarter;

import static bench_stroke.RatKing.nearestCat;
import static bench_stroke.RatKing.nearestEnemy;
import static bench_stroke.RatKing.inDistress;

public class SimpleRKStateUpdater implements StateUpdater {
    public static final int HQ_CHEESE_THRESHOLD = 1000;

    @Override
    public Behavior decideBehavior() {
        // if (rc.getGlobalCheese() > HQ_CHEESE_THRESHOLD) {
        //     return RKHeadquarter.getInstance();
        // }

        // return RKExplore.getInstance();
        if(inDistress) {
            return RKFlee.getInstance();
        }

        return RKHeadquarter.getInstance();
    }
}
