package dev.anjo7778.sao.simulation;

/** Bounded analytic density; no stored map, per-column allocation, or loaded dimension needed. */
public final class FloorGeometry {
    private FloorGeometry() {}
    public static final int FIRST_RADIUS = 4000;

    public static double radialDensity(int radius, int x, int z) {
        if (radius <= 0) throw new IllegalArgumentException("Radius must be positive");
        double distance = Math.sqrt((double) x * x + (double) z * z);
        return Math.max(-4.0, Math.min(4.0, (radius - distance) / 32.0));
    }
}
