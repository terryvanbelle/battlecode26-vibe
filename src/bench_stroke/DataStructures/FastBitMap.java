package bench_stroke.DataStructures;

import battlecode.common.MapLocation;

public class FastBitMap {
    private final long[] map;
    public final int width;   // Logic width (reported to outside)
    public final int height;  // Logic height
    private final int paddedWidth; // Internal width (width + 6)

    public FastBitMap(int width, int height) {
        this.width = width;
        this.height = height;
        // Add 3 pixels of padding on all sides (Left, Right, Top, Bottom)
        this.paddedWidth = width + 6;
        int paddedHeight = height + 6;
        this.map = new long[Math.ceilDiv(paddedWidth * paddedHeight, 64)];
    }

    public boolean get(int x, int y) {
        // Offset coordinates by +3 to account for padding
        int cell = (y + 3) * paddedWidth + (x + 3);
        return ((map[cell >> 6] >> (cell & 63)) & 1) == 1;
    }

    public boolean get(MapLocation loc) {
        return get(loc.x, loc.y);
    }

    public void set(int x, int y, boolean value) {
        int cell = (y + 3) * paddedWidth + (x + 3);
        int index = cell >> 6;
        long mask = 1L << (cell & 63);

        if (value) {
            map[index] |= mask;
        } else {
            map[index] &= ~mask;
        }
    }

    public void toggle(int x, int y) {
        int cell = (y + 3) * paddedWidth + (x + 3);
        map[cell >> 6] ^= (1L << (cell & 63));
    }

    /**
     * Highly optimized 7x7 mask retrieval.
     * Because of padding, we NEVER need boundary checks.
     * The top-left corner of the mask for (x,y) is simply at internal index (y * paddedWidth + x).
     */
    public long get7x7BitMask(int x, int y) {
        long result = 0L;
        // Local Variable Caching
        long[] m = this.map;
        int pw = this.paddedWidth;

        // The logic is normally: (y_internal - 3) * w + (x_internal - 3).
        // Since y_internal = y + 3 and x_internal = x + 3, they cancel out!
        // The start index is just:
        int rowStart = y * pw + x;

        // --- UNROLLED LOOP (7 Rows) ---

        // Row 0
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) {
                raw = m[idx] >>> off;
            } else {
                raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            }
            // Reverse bits (Left-Right -> Right-Left) and shift to LSB
            result |= (Long.reverse(raw) >>> 57);
            rowStart += pw;
        }

        // Row 1
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) raw = m[idx] >>> off;
            else raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            result |= (Long.reverse(raw) >>> 57) << 7;
            rowStart += pw;
        }

        // Row 2
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) raw = m[idx] >>> off;
            else raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            result |= (Long.reverse(raw) >>> 57) << 14;
            rowStart += pw;
        }

        // Row 3
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) raw = m[idx] >>> off;
            else raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            result |= (Long.reverse(raw) >>> 57) << 21;
            rowStart += pw;
        }

        // Row 4
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) raw = m[idx] >>> off;
            else raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            result |= (Long.reverse(raw) >>> 57) << 28;
            rowStart += pw;
        }

        // Row 5
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) raw = m[idx] >>> off;
            else raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            result |= (Long.reverse(raw) >>> 57) << 35;
            rowStart += pw;
        }

        // Row 6
        {
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            long raw;
            if (off <= 57) raw = m[idx] >>> off;
            else raw = (m[idx] >>> off) | (m[idx + 1] << (64 - off));
            result |= (Long.reverse(raw) >>> 57) << 42;
        }

        return result;
    }

    /**
     * Highly optimized 7x7 mask writer.
     * No bounds checks required due to padding.
     */
    public void setBitsFromLong(int x, int y, long mask) {
        long[] m = this.map;
        int pw = this.paddedWidth;

        // Same simplification: internal (y-3), (x-3) becomes just (y, x)
        int rowStart = y * pw + x;

        // --- UNROLLED LOOP (7 Rows) ---

        // Row 0
        {
            long val = Long.reverse(mask & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
            rowStart += pw;
        }

        // Row 1
        {
            long val = Long.reverse((mask >>> 7) & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
            rowStart += pw;
        }

        // Row 2
        {
            long val = Long.reverse((mask >>> 14) & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
            rowStart += pw;
        }

        // Row 3
        {
            long val = Long.reverse((mask >>> 21) & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
            rowStart += pw;
        }

        // Row 4
        {
            long val = Long.reverse((mask >>> 28) & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
            rowStart += pw;
        }

        // Row 5
        {
            long val = Long.reverse((mask >>> 35) & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
            rowStart += pw;
        }

        // Row 6
        {
            long val = Long.reverse((mask >>> 42) & 0x7FL) >>> 57;
            int idx = rowStart >> 6;
            int off = rowStart & 63;
            if (off <= 57) {
                m[idx] = (m[idx] & ~(0x7FL << off)) | (val << off);
            } else {
                m[idx]     = (m[idx]     & ~(0x7FL << off))         | (val << off);
                m[idx + 1] = (m[idx + 1] & ~(0x7FL >>> (64 - off))) | (val >>> (64 - off));
            }
        }
    }
}