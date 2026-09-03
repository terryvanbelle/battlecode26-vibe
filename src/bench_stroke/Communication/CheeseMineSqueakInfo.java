package bench_stroke.Communication;

import battlecode.common.MapLocation;

public record CheeseMineSqueakInfo(MapLocation location, int threatLevel) implements SqueakInfo{

    @Override
    public String toString() {
        return "CheeseMineSqueakInfo{" +
                "location=" + location +
                ", threatLevel=" + threatLevel +
                '}';
    }
}
