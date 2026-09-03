package bench_stroke.Communication;

import battlecode.common.MapLocation;
import battlecode.common.*;

public record NearbyCatSqueakInfo(MapLocation location, boolean cat, Direction direction) implements SqueakInfo{

    @Override
    public String toString() {
        return "NearbyCatSqueakInfo{" +
                "location=" + location +
                "cat= " + cat +
                "direction = " + direction +
                '}';
    }
}
