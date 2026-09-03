package bench_stroke;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;

import static bench_stroke.RobotPlayer.rc;

public class Debug {
    static boolean debugOn = false;

    public static void enableDebug() {
        debugOn = true;
    }

    public static void disableDebug() {
        debugOn = false;
    }

    public static void log(String msg) {
        if (debugOn) {
            System.out.println(msg);
        }
    }

    public static void setIndicatorDot(MapLocation location, int r, int g, int b) throws GameActionException {
        if (debugOn) {
            rc.setIndicatorDot(location, r, g, b);
        }
    }

    public static void setIndicatorString(String str) {
        if (debugOn) {
            rc.setIndicatorString(str);
        }
    }

    public static int measureBytecodesForOperation(Runnable operation) {
        int bytecodesBefore = Clock.getBytecodeNum();
        operation.run();
        return Clock.getBytecodeNum() - bytecodesBefore;
    }
}
