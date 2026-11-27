package dev.anjo7778.sao.simulation;

import java.util.HashSet;
import java.util.Random;

public final class FloorLayoutChecks {
    public static void main(String[] args) {
        check(FloorLayout.MAX_FLOOR > 1_000_000, "Capacity");
        check(FloorLayout.center(1).equals(new FloorLayout.Center(0, 0)), "First floor");
        check(FloorLayout.center(2).equals(new FloorLayout.Center(0, 0)), "Upper origin");
        var centers = new HashSet<FloorLayout.Center>();
        long start = System.nanoTime();
        for (long i = 2; i <= 100_001; i++) {
            var c = FloorLayout.center(i);
            check(centers.add(c), "Duplicate address");
            check(FloorLayout.floorAt(c.x(), c.z()) == i, "Round-trip address " + i);
            check(FloorLayout.radialDensity(c.x(), c.z()) == 4, "Solid island center");
            int radius = FloorCatalog.radiusFor(i);
            check(FloorLayout.radialDensity(c.x() + radius + 64, c.z()) < 0, "Gap between islands");
            check(FloorLayout.floorAt(c.x()-32, c.z()+32) == i, "Nearby blocks");
        }
        var rng = new Random(42);
        for (int i = 0; i < 100_000; i++) {
            long floor = 2 + rng.nextLong(FloorLayout.MAX_FLOOR - 1);
            var c = FloorLayout.center(floor);
            check(FloorLayout.floorAt(c.x(), c.z()) == floor, "Random round-trip");
            check(Math.abs(c.x()) + 4000 < 29_999_984 && Math.abs(c.z()) + 4000 < 29_999_984, "World border");
        }
        var last = FloorLayout.center(FloorLayout.MAX_FLOOR);
        check(FloorLayout.floorAt(last.x(), last.z()) == FloorLayout.MAX_FLOOR, "Last supported floor");
        check(FloorLayout.floorAt(Integer.MIN_VALUE, Integer.MAX_VALUE) == 0, "Extreme coordinates");
        check(FloorLayout.radialDensity(Integer.MAX_VALUE, Integer.MAX_VALUE) == -4, "Unsupported coordinates");
        check(!FloorLayout.supported(FloorLayout.MAX_FLOOR + 1), "Capacity bound");
        System.out.printf("PASS: 200,000 floor address cases in %.1f ms; capacity %,d floors%n", (System.nanoTime()-start)/1_000_000.0, FloorLayout.MAX_FLOOR);
    }
    private static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); }
}
