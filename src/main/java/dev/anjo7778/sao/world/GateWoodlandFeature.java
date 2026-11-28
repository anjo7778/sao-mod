package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;

/** Only fresh chunks receive a marked nursery plot and one real vanilla sapling. */
public final class GateWoodlandFeature {
    private GateWoodlandFeature(){}
    public static int place(WorldGenLevel level,long floor,int ox,int oz){
        int placed=0;
        var center=FloorLayout.center(floor);
        for(var gate:ScoutingTrip.Gate.values())for(var site:GateWoodlandPlan.sites(floor,gate)){
            int x=site.x(),z=site.z();
            if(x<ox||x>=ox+16||z<oz||z>=oz+16
                ||CityPlan.inside(floor,x-center.x(),z-center.z())
                ||RegionalSettlements.inDecorationBuffer(floor,x-center.x(),z-center.z()))continue;
            for(int y=CityPlan.GROUND+18;y>=CityPlan.GROUND-18;y--){
                var p=new BlockPos(x,y,z);var below=level.getBlockState(p.below());
                if(!level.getBlockState(p.below(2)).isFaceSturdy(level,p.below(2),Direction.UP)
                    ||!level.getFluidState(p.below(2)).isEmpty()||level.getBlockEntity(p.below(2))!=null
                    ||!level.getBlockState(p).isAir()||!level.getBlockState(p.above()).isAir()
                    ||!level.getFluidState(p).isEmpty()||!level.getFluidState(p.below()).isEmpty()
                    ||!(below.is(Blocks.GRASS_BLOCK)||below.is(Blocks.DIRT)||below.is(Blocks.COARSE_DIRT))
                    ||!below.isFaceSturdy(level,p.below(),Direction.UP))continue;
                if(level.setBlock(p.below(2),Blocks.ROOTED_DIRT.defaultBlockState(),2)){
                    if(level.setBlock(p,Blocks.OAK_SAPLING.defaultBlockState(),2))placed++;
                }
                break;
            }
        }
        return placed;
    }
}
