package dev.anjo7778.sao.simulation;

import dev.anjo7778.sao.simulation.MaterialRules.Material;
import java.util.*;

/** Shared bounded ore plan. Tin retains its existing coordinates; later metals have independent seeds. */
public final class MetalDeposits {
    private MetalDeposits(){}
    public record Spec(Material material,int attempts,int size,int minY,int surfaceOffset){}
    public static final List<Spec> ADVANCED=List.of(
        new Spec(Material.SILVER,2,7,4,24),
        new Spec(Material.MITHRIL,2,6,-8,34),
        new Spec(Material.ADAMANTITE,2,5,-14,43),
        new Spec(Material.ORICHALCUM,1,6,-16,43),
        new Spec(Material.CELESTIUM,1,5,-18,48),
        new Spec(Material.ASTRALITE,1,4,-20,52)
    );
    public static List<TinDeposits.Point> plan(Spec spec,long seed,long floor,int ox,int oz){
        if(!FloorLayout.supported(floor)||!spec.material().intendedDeposit(floor)||(ox&15)!=0||(oz&15)!=0)return List.of();
        var center=FloorLayout.center(floor);
        int top=Math.max(spec.minY()+4,FloorLayout.surfaceBase(floor)-spec.surfaceOffset());
        Random r=new Random(seed^floor*0x9e3779b97f4a7c15L^ox*0xbf58476d1ce4e5b9L^oz*0x94d049bb133111ebL^spec.material().name().hashCode()*0x632be59bd9b4e019L);
        Set<TinDeposits.Point> points=new LinkedHashSet<>();
        for(int vein=0;vein<spec.attempts();vein++){
            int x=ox+3+r.nextInt(10),z=oz+3+r.nextInt(10),y=spec.minY()+2+r.nextInt(top-spec.minY()-1);
            for(int i=0;i<spec.size();i++){
                int bx=x+r.nextInt(5)-2,bz=z+r.nextInt(5)-2,by=y+r.nextInt(5)-2;
                int lx=bx-center.x(),lz=bz-center.z();
                if(Math.hypot((double)lx,lz)<FloorCatalog.radiusFor(floor)-24
                    &&!CityPlan.inside(floor,lx,lz)&&!RegionalSettlements.inDecorationBuffer(floor,lx,lz))
                    points.add(new TinDeposits.Point(bx,by,bz));
            }
        }
        return List.copyOf(points);
    }
}
