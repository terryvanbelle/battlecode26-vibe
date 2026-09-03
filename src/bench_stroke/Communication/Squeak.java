package bench_stroke.Communication;

import battlecode.common.MapLocation;

public class Squeak {
    public MapLocation source;
    public int senderID;
    public int round;
    public SqueakInfo squeakInfo;

    public Squeak(MapLocation source, int senderID, int round, SqueakInfo squeakInfo) {
        this.source = source;
        this.senderID = senderID;
        this.round = round;
        this.squeakInfo = squeakInfo;
    }

    @Override
    public String toString() {
        return "Squeak{" +
                "squeakInfo=" + squeakInfo +
                '}';
    }
}
