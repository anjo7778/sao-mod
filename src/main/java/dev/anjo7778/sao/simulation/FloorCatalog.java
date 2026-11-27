package dev.anjo7778.sao.simulation;

/** Pure deterministic floor descriptions; no dimensions or chunks are allocated here. */
public final class FloorCatalog {
    public enum Theme { PLAINS, MOUNTAINS, ANCIENT_FOREST, WATER, RUINS, DESERT, FROST, VOLCANIC, CELESTIAL }
    public record Floor(long number, long seed, Theme theme, int radiusBlocks, double threat, boolean milestone) {}
    private final long worldSeed;

    public FloorCatalog(long worldSeed) { this.worldSeed = worldSeed; }

    public Floor describe(long number) {
        if (number < 1) throw new IllegalArgumentException("Floor must be positive");
        long seed = mix(worldSeed + number * 0x9E3779B97F4A7C15L);
        Theme[] themes = Theme.values();
        Theme theme = number <= 5 ? themes[(int) number - 1] : themes[(int) Math.floorMod(seed, number < 50 ? 7 : themes.length)];
        int radius = 4000 - 2 * (int) Math.min(number - 1, 1600);
        double threat = 1.0 + 0.65 * Math.log(number);
        return new Floor(number, seed, theme, radius, threat, number % 10 == 0);
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
