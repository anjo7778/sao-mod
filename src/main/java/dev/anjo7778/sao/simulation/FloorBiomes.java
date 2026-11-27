package dev.anjo7778.sao.simulation;

/** Stable biome palette and smooth, bounded regional selection; no per-floor arrays or caches. */
public final class FloorBiomes {
    public enum Kind { PLAINS, FOREST, FLOWER_FOREST, MOUNTAINS, TAIGA, ANCIENT_PINES, DARK_FOREST, SWAMP, BIRCH, DESERT, SNOW, BADLANDS, MEADOW }
    private FloorBiomes() {}
    public static Kind at(boolean upper, int x, int z) {
        long floor=upper?FloorLayout.floorAt(x,z):1;
        if (!FloorLayout.supported(floor)) return Kind.PLAINS;
        int lx=upper?FloorLayout.local(x):x, lz=upper?FloorLayout.local(z):z;
        if (Math.abs((long)lx)<=224 && Math.abs((long)lz)<=224) return Kind.PLAINS;
        if (Math.hypot((double)lx,(double)lz)>=FloorCatalog.radiusFor(floor)-16) return Kind.PLAINS;
        double n=field(floor,lx,lz);
        return switch(FloorCatalog.themeFor(floor)) {
            case PLAINS -> n<.30?Kind.FOREST:n>.72?Kind.FLOWER_FOREST:Kind.PLAINS;
            case MOUNTAINS -> n<.33?Kind.TAIGA:n>.72?Kind.MEADOW:Kind.MOUNTAINS;
            case ANCIENT_FOREST -> n<.31?Kind.BIRCH:n>.72?Kind.ANCIENT_PINES:Kind.DARK_FOREST;
            case WATER -> n<.35?Kind.BIRCH:n>.73?Kind.FOREST:Kind.SWAMP;
            case RUINS -> n<.30?Kind.DARK_FOREST:n>.70?Kind.MOUNTAINS:Kind.MEADOW;
            case DESERT -> n<.34?Kind.BADLANDS:Kind.DESERT;
            case FROST -> n<.35?Kind.TAIGA:Kind.SNOW;
            case VOLCANIC -> n<.35?Kind.DESERT:Kind.BADLANDS;
            case CELESTIAL -> n<.35?Kind.FLOWER_FOREST:n>.73?Kind.BIRCH:Kind.MEADOW;
        };
    }
    private static double field(long floor,int x,int z) {
        int gx=Math.floorDiv(x,256),gz=Math.floorDiv(z,256);
        double a=Math.floorMod(x,256)/256.0,b=Math.floorMod(z,256)/256.0;
        a=a*a*(3-2*a);b=b*b*(3-2*b);
        double v0=lerp(hash(floor,gx,gz),hash(floor,gx+1,gz),a);
        double v1=lerp(hash(floor,gx,gz+1),hash(floor,gx+1,gz+1),a);
        return lerp(v0,v1,b);
    }
    private static double lerp(double a,double b,double f){return a+(b-a)*f;}
    private static double hash(long floor,int x,int z){
        long v=floor*0x9E3779B97F4A7C15L+x*0xBF58476D1CE4E5B9L+z*0x94D049BB133111EBL;
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;
        v=(v^(v>>>27))*0x94D049BB133111EBL;
        return ((v^(v>>>31))>>>11)*0x1.0p-53;
    }
}
