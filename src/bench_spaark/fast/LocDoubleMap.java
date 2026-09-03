package bench_spaark.fast;

import battlecode.common.MapLocation;

//removed some functionality from FastLocIntMap that im pretty sure we dont need
public class LocDoubleMap {
    public StringBuilder keys;

    public LocDoubleMap() {
        keys = new StringBuilder();
    }

    public void set(MapLocation loc, double val) {
        int ind = keys.indexOf(loc.toString());
        if (ind == -1) {
            keys.append(String.format("%-8s%-24s", loc, val));
        } else {
            keys.replace(ind + 8, ind + 32, String.format("%-24s", val));
        }
    }

    public void remove(MapLocation loc) {
        int ind = keys.indexOf(loc.toString());
        if (ind == -1) {
            keys.delete(ind, ind + 32);
        } else {
            System.out.println("Wtf, "+loc+" not found in map!");
        }
    }

    public boolean contains(MapLocation loc) {
        return keys.indexOf(loc.toString()) != -1;
    }

    public void clear() {
        keys = new StringBuilder();
    }

    public double get(MapLocation loc) {
        int ind = keys.indexOf(loc.toString());
        if (ind == -1) {
            return -1;
        } else {
            return Double.parseDouble(keys.substring(ind + 8, ind + 32));
        }
    }
}