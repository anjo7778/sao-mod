package dev.anjo7778.sao.simulation;

/** Chunk-local castle skeleton. No saved state or block placement outside the requested chunk. */
public final class CastleGeometry {
    public static final int SPACING = 384;
    public static final int ROOF_BOTTOM = 272;
    public static final int ROOF_TOP = 288;
    public static final int SPAWN_CLEARANCE = 224;
    private CastleGeometry() {}

    private static double clamp(double value) { return Math.max(-4, Math.min(4, value)); }
    private static double band(double value, double lo, double hi) {
        return Math.min(value-lo, hi-value) / 8.0;
    }
    private static int gridDistance(int v) {
        return Math.abs(Math.floorMod(v+SPACING/2, SPACING)-SPACING/2);
    }

    /**
     * Open daylight courts separated by massive ceiling ribs and tapered supporting towers.
     * A clear central courtyard keeps the vanilla spawn and floor travel unobstructed.
     */
    public static double density(boolean upper, int x, int y, int z) {
        if (y < 24 || y > ROOF_TOP+32) return -4;
        long floor = upper ? FloorLayout.floorAt(x,z) : 1;
        if (!FloorLayout.supported(floor)) return -4;
        int lx=upper?FloorLayout.local(x):x, lz=upper?FloorLayout.local(z):z;
        int radius=FloorCatalog.radiusFor(floor);
        double radial=Math.hypot((double)lx,(double)lz);
        if(radial<SPAWN_CLEARANCE-32 || radial>radius+32) return -4;
        double inside=Math.min((radius-24-radial)/12.0,(radial-SPAWN_CLEARANCE)/12.0);
        if(inside<=-4) return -4;

        int dx=gridDistance(lx), dz=gridDistance(lz);
        double nearestRib=Math.min(dx,dz);
        double roof=Math.min((42-nearestRib)/8.0,band(y,ROOF_BOTTOM,ROOF_TOP));
        // Broad edge ring and deep cross-ribs evoke the underside of the next floor.
        double rim=Math.min((34-Math.abs(radial-(radius-76)))/8.0,band(y,264,ROOF_TOP));

        // Octagonal pillars: wide footings, narrow shafts, flared cornice below the roof.
        double foot=20.0-8.0*Math.max(0,Math.min(1,(y-48)/40.0));
        double crown=12.0+22.0*Math.max(0,Math.min(1,(y-218)/54.0));
        double width=Math.max(foot,crown);
        double octagon=Math.max(Math.max(dx,dz),(dx+dz)*0.7071067811865476);
        double tower=Math.min((width-octagon)/6.0,band(y,40,ROOF_TOP));
        return clamp(Math.min(inside,Math.max(tower,Math.max(roof,rim))));
    }
}
