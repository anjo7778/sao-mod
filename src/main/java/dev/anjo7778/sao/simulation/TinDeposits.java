package dev.anjo7778.sao.simulation;

import java.util.*;

/** Pure deterministic, chunk-local deposit plan. No retries, chunk loads, or player-dependent ore. */
public final class TinDeposits {
    private TinDeposits(){}
    public record Point(int x,int y,int z){}
    public static boolean eligible(long floor,int localX,int localZ){
        return FloorLayout.supported(floor)&&MaterialRules.Material.TIN.intendedDeposit(floor)
            &&Math.hypot((double)localX,(double)localZ)<FloorCatalog.radiusFor(floor)-24
            &&!CityPlan.inside(floor,localX,localZ)&&!RegionalSettlements.inDecorationBuffer(floor,localX,localZ);
    }
    public static List<Point> plan(long seed,long floor,int originX,int originZ){
        if(!FloorLayout.supported(floor)||!MaterialRules.Material.TIN.intendedDeposit(floor)
            ||Math.floorMod(originX,16)!=0||Math.floorMod(originZ,16)!=0)return List.of();
        var center=FloorLayout.center(floor);
        // Floor one uses the overworld origin; upper floors use their stable centers.
        Random r=new Random(seed^floor*0x9e3779b97f4a7c15L^originX*0xbf58476d1ce4e5b9L^originZ*0x94d049bb133111ebL);
        Set<Point> points=new LinkedHashSet<>();
        int top=FloorLayout.surfaceBase(floor)-20;
        for(int vein=0;vein<3;vein++){
            int x=originX+3+r.nextInt(10),z=originZ+3+r.nextInt(10),y=8+r.nextInt(top-7);
            int count=5+r.nextInt(4);
            for(int i=0;i<count;i++){
                int bx=x+r.nextInt(5)-2,bz=z+r.nextInt(5)-2,by=y+r.nextInt(5)-2;
                if(eligible(floor,bx-center.x(),bz-center.z()))points.add(new Point(bx,by,bz));
            }
        }
        return List.copyOf(points);
    }
}
