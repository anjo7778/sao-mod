package dev.anjo7778.sao.simulation;

public final class CastleGeometryChecks {
    private static int checks;
    private static void check(boolean ok,String message) {
        checks++; if(!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        for(long floor:new long[]{1,2,3,100,1000,10000,FloorLayout.MAX_FLOOR}) {
            boolean upper=floor!=1;
            var c=FloorLayout.center(floor);
            int x=c.x(),z=c.z();
            check(CastleGeometry.density(upper,x,280,z)<0,"Open central spawn");
            check(CastleGeometry.density(upper,x+384,180,z+384)>0,"Pillar");
            check(CastleGeometry.density(upper,x+384,280,z+192)>0,"Roof rib");
            check(CastleGeometry.density(upper,x+576,280,z+576)<0,"Daylight court");
            check(CastleGeometry.density(upper,x+384,200,z+192)<0,"Space beneath rib");
            check(CastleGeometry.density(upper,x+384,310,z+384)<0,"Space above roof");
            check(CastleGeometry.density(upper,x+384,-60,z+384)<0,"Void below");
            int radius=FloorCatalog.radiusFor(floor);
            check(CastleGeometry.density(upper,x+radius+128,280,z)<0,"No roof beyond edge");
            check(CastleGeometry.density(upper,x+radius-76,280,z)>0,"Outer ring");
        }
        long start=System.nanoTime(); double sum=0;
        for(int y=40;y<304;y+=8) for(int x=-2000;x<2000;x+=16) for(int z=-2000;z<2000;z+=16)
            sum+=CastleGeometry.density(false,x,y,z);
        check(Double.isFinite(sum),"Finite density");
        System.out.printf("PASS: %d castle checks; 2,062,500 density samples in %.1f ms (%.2f)%n",checks,(System.nanoTime()-start)/1e6,sum);
    }
}
