package bench_stroke.Communication;

import battlecode.common.MapLocation;

public record PickupSqueakInfo() implements SqueakInfo{

    @Override
    public String toString() {
        return "PickupSqueakInfo{" +
                '}';
    }
}
