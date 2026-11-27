package dev.anjo7778.sao.simulation;

public final class GeometryChecks {
    public static void main(String[] args) {
        int radius = FloorGeometry.FIRST_RADIUS;
        check(new FloorCatalog(42).describe(1).radiusBlocks() == radius);
        check(FloorGeometry.radialDensity(radius, 0, 0) == 4);
        check(FloorGeometry.radialDensity(radius, radius, 0) == 0);
        check(FloorGeometry.radialDensity(radius, 3000, 3000) < 0);
        check(FloorGeometry.radialDensity(radius, Integer.MIN_VALUE, Integer.MAX_VALUE) == -4);
        long start = System.nanoTime();
        double checksum = 0;
        for (int x = -5000; x < 5000; x += 4)
            for (int z = -5000; z < 5000; z += 4)
                checksum += FloorGeometry.radialDensity(radius, x, z);
        check(Double.isFinite(checksum));
        System.out.printf("PASS: 6,250,000 radial samples in %.1f ms (checksum %.3f)%n",
            (System.nanoTime()-start)/1_000_000.0, checksum);
    }
    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Floor geometry invariant failed");
    }
}
