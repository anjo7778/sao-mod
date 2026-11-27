package dev.anjo7778.sao.simulation;

/** Pure deterministic floor descriptions; no dimensions or chunks are allocated here. */
public final class FloorCatalog {
    public enum Theme { PLAINS, MOUNTAINS, ANCIENT_FOREST, WATER, RUINS, DESERT, FROST, VOLCANIC, CELESTIAL }
    public record Floor(long number, long seed, Theme theme, int radiusBlocks, double threat, boolean milestone) {}
    private static final Theme[] THEMES = Theme.values();
    private final long worldSeed;

    public FloorCatalog(long worldSeed) { this.worldSeed = worldSeed; }

    public Floor describe(long number) {
        if (number < 1) throw new IllegalArgumentException("Floor must be positive");
        long seed = mix(worldSeed + number * 0x9E3779B97F4A7C15L);
        Theme theme = themeFor(number);
        int radius = radiusFor(number);
        double threat = 1.0 + 0.65 * Math.log(number);
        return new Floor(number, seed, theme, radius, threat, number % 10 == 0);
    }

    /** Theme is stable by floor identity across world seeds; terrain noise still uses the world seed. */
    public static Theme themeFor(long number) {
        if(number<1) throw new IllegalArgumentException("Floor must be positive");
        return number<=7?THEMES[(int)number-1]:THEMES[(int)Math.floorMod(mix(number),number<50?7:THEMES.length)];
    }

    public static int radiusFor(long number) {
        if (number < 1) throw new IllegalArgumentException("Floor must be positive");
        return 4000 - 2 * (int)Math.min(number - 1, 1600);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
