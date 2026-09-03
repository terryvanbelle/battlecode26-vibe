package bench_stroke.DataStructures;

import java.util.Arrays;

import battlecode.common.MapLocation;

public class FastIterableLocMap {
    public StringBuilder keys;
    public MapLocation[] locs;
    public int[] values;

    public int size;
    public int maxlen;
    private int earliestRemoved;

    public FastIterableLocMap() {
        this(100);
    }

    public FastIterableLocMap(int maxlen) {
        this.maxlen = maxlen;
        keys = new StringBuilder();
        locs = new MapLocation[maxlen];
        values = new int[maxlen];
        size = 0;
        earliestRemoved = 0;
    }

    /* ================= Encoding helpers ================= */

    private int indexOf(MapLocation loc) {
        return keys.indexOf("^" + (char) loc.x + (char) loc.y);
    }

    private void writeInt(int idx, int value) {
        keys.setCharAt(idx + 3, (char) (value >>> 16));
        keys.setCharAt(idx + 4, (char) value);
    }

    private int readInt(int idx) {
        return (keys.charAt(idx + 3) << 16) | keys.charAt(idx + 4);
    }

    /* ================= Map ops ================= */

    public boolean contains(MapLocation loc) {
        return indexOf(loc) >= 0;
    }

    public int get(MapLocation loc, int defaultVal) {
        int idx = indexOf(loc);
        return idx < 0 ? defaultVal : readInt(idx);
    }

    public void put(MapLocation loc, int value) {
        int idx = indexOf(loc);
        if (idx >= 0) {
            writeInt(idx, value);
            return;
        }
        if (size == maxlen) return;

        keys.append('^');
        keys.append((char) loc.x);
        keys.append((char) loc.y);
        keys.append((char) (value >>> 16));
        keys.append((char) value);

        size++;
    }

    public void remove(MapLocation loc) {
        int idx = indexOf(loc);
        if (idx < 0) return;

        keys.delete(idx, idx + 5);
        size--;

        if (earliestRemoved > idx)
            earliestRemoved = idx;
    }

    public void clear() {
        keys = new StringBuilder();
        size = 0;
        earliestRemoved = 0;
    }

    /* ================= Iteration ================= */

    public void updateIterable() {
        for (int i = earliestRemoved / 5; i < size; i++) {
            int base = i * 5;
            locs[i] = new MapLocation(
                    keys.charAt(base + 1),
                    keys.charAt(base + 2)
            );
            values[i] =
                    (keys.charAt(base + 3) << 16)
                            |  keys.charAt(base + 4);
        }
        //earliestRemoved = size * 5;
    }

    public MapLocation getKey(int i) {
        return locs[i];
    }

    public int getValue(int i) {
        return values[i];
    }
}
