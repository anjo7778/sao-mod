package dev.anjo7778.sao.simulation;

/**
 * Stable square-spiral addressing for floors 2+. Floor 1 remains the Overworld.
 * One extra dimension, no per-floor registry entries, no preallocated terrain.
 * Never change the stride, ordering or supported ring without a save migration.
 */
public final class FloorLayout {
    public static final int STRIDE = 16384;
    public static final int MAX_RING = 1800;
    public static final long MAX_FLOOR = (2L * MAX_RING + 1) * (2L * MAX_RING + 1) + 1;
    public record Center(int x, int z) {}
    private FloorLayout() {}

    public static boolean supported(long floor) { return floor >= 1 && floor <= MAX_FLOOR; }

    public static Center center(long floor) {
        if (!supported(floor)) throw new IllegalArgumentException("Floor outside world layout");
        if (floor <= 2) return new Center(0, 0);
        long index = floor - 2;
        int ring = (int)Math.ceil((Math.sqrt(index + 1.0) - 1.0) / 2.0);
        long end = (2L * ring + 1) * (2L * ring + 1) - 1;
        int d = (int)(end - index);
        int side = 2 * ring;
        int x, z;
        if (d < side) { x = ring - d; z = -ring; }
        else if (d < 2 * side) { x = -ring; z = -ring + d - side; }
        else if (d < 3 * side) { x = -ring + d - 2 * side; z = ring; }
        else { x = ring; z = ring - (d - 3 * side); }
        return new Center(x * STRIDE, z * STRIDE);
    }

    public static int cell(int block) { return (int)Math.floorDiv((long)block + STRIDE / 2, STRIDE); }
    public static int local(int block) { return (int)(block - (long)cell(block) * STRIDE); }

    /** Nearest floor region; 0 denotes an unsupported region. Call only in the upper realm. */
    public static long floorAt(int x, int z) {
        int cx = cell(x), cz = cell(z);
        int ring = Math.max(Math.abs(cx), Math.abs(cz));
        if (ring > MAX_RING) return 0;
        if (ring == 0) return 2;
        long end = (2L * ring + 1) * (2L * ring + 1) - 1;
        int side = 2 * ring;
        int d;
        if (cz == -ring) d = ring - cx;
        else if (cx == -ring) d = side + cz + ring;
        else if (cz == ring) d = 2 * side + cx + ring;
        else d = 3 * side + ring - cz;
        return end - d + 2;
    }

    public static double radialDensity(int x, int z) {
        long floor = floorAt(x, z);
        if (floor == 0) return -4.0;
        return FloorGeometry.radialDensity(FloorCatalog.radiusFor(floor), local(x), local(z));
    }

    /** Provisional profiles, independent of climate/biome integration. */
    public static int surfaceBase(long floor) {
        if (!supported(floor)) throw new IllegalArgumentException("Unsupported floor");
        return switch ((int)Math.min(floor, 6)) {
            case 1 -> 96;
            case 2 -> 126;
            case 3 -> 103;
            case 4 -> 76;
            case 5 -> 91;
            default -> 80 + (int)Math.floorMod(floor * 37L, 49L);
        };
    }

    public static double heightDensity(int x, int y, int z) {
        long floor = floorAt(x, z);
        if (floor == 0) return -4.0;
        return Math.max(-4.0, Math.min(4.0, (surfaceBase(floor) - (double)y) / 32.0));
    }
}
