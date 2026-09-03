package bench_spaark.fast;

import battlecode.common.MapLocation;

//removed some functionality from FastLocIntMap that im pretty sure we dont need
public class LocInt32Map {
    public StringBuilder keys;

    public LocInt32Map() {
        keys = new StringBuilder();
    }

    public void set(MapLocation loc, int val) {
        int ind = keys.indexOf(loc.toString());
        if (ind == -1) {
            keys.append(String.format("%-8s%-11s", loc, val));
        } else {
            keys.replace(ind + 8, ind + 19, String.format("%-11s", val));
        }
    }

    public void remove(MapLocation loc) {
        int ind = keys.indexOf(loc.toString());
        if (ind == -1) {
            keys.delete(ind, ind + 19);
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

    public int get(MapLocation loc) {
        int ind = keys.indexOf(loc.toString());
        if (ind == -1) {
            return -1;
        } else {
            return Integer.parseInt(keys.substring(ind + 8, ind + 19));
        }
    }
}