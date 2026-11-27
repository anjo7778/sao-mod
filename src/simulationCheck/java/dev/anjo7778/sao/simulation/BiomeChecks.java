package dev.anjo7778.sao.simulation;

import java.util.EnumSet;

public final class BiomeChecks {
    public static void main(String[] args){
        EnumSet<FloorBiomes.Kind> all=EnumSet.noneOf(FloorBiomes.Kind.class);
        long count=0,start=System.nanoTime();
        for(long f=1;f<=1000;f++){
            var c=FloorLayout.center(f);
            check(FloorBiomes.at(f>1,c.x(),c.z())==FloorBiomes.Kind.PLAINS);
            for(int dx=-640;dx<=640;dx+=64)for(int dz=-640;dz<=640;dz+=64){
                var k=FloorBiomes.at(f>1,c.x()+dx,c.z()+dz);
                check(k==FloorBiomes.at(f>1,c.x()+dx,c.z()+dz));
                all.add(k);count++;
            }
        }
        check(all.size()==FloorBiomes.Kind.values().length);
        System.out.printf("PASS: %,d biome samples, %d palette entries, %.1f ms%n",count,all.size(),(System.nanoTime()-start)/1e6);
    }
    private static void check(boolean ok){if(!ok)throw new AssertionError("Biome invariant");}
}
