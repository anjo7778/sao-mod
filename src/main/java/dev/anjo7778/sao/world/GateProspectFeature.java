package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Add small exposed boulders/deadfall to fresh wilderness chunks only. Never overwrite existing blocks. */
public final class GateProspectFeature {
    private GateProspectFeature(){}
    public static int place(WorldGenLevel level,long floor,int originX,int originZ){
        if(floor<1||floor>10)return 0;
        int placed=0;
        var fc=FloorLayout.center(floor);
        for(var gate:ScoutingTrip.Gate.values()){
            var g=ScoutingTrip.gatePoint(floor,gate,36);
            int gx=fc.x()+g.x(),gz=fc.z()+g.z();
            if(gx+4<originX||gx-4>=originX+16||gz+4<originZ||gz-4>=originZ+16)continue;
            for(var c:GateProspects.columns(floor,gate)){
            if(c.x()<originX||c.x()>=originX+16||c.z()<originZ||c.z()>=originZ+16)continue;
            var center=FloorLayout.center(floor);int lx=c.x()-center.x(),lz=c.z()-center.z();
            if(CityPlan.inside(floor,lx,lz)||RegionalSettlements.inDecorationBuffer(floor,lx,lz))continue;
            for(int y=CityPlan.GROUND+18;y>=CityPlan.GROUND-18;y--){
                var pos=new BlockPos(c.x(),y,c.z());var below=level.getBlockState(pos.below());
                if(!level.getBlockState(pos).isAir()||!level.getBlockState(pos.above()).isAir()
                    ||!level.getFluidState(pos).isEmpty()||!level.getFluidState(pos.below()).isEmpty())continue;
                if(!naturalGround(below)||!below.isFaceSturdy(level,pos.below(),Direction.UP))continue;
                BlockState state=switch(c.resource()){
                    case IRON->Blocks.IRON_ORE.defaultBlockState();
                    case COAL->Blocks.COAL_ORE.defaultBlockState();
                    case DEADWOOD->Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X);
                };
                if(level.setBlock(pos,state,2))placed++;
                break;
            }
        }
        }
        return placed;
    }
    private static boolean naturalGround(BlockState s){
        return s.is(Blocks.STONE)||s.is(Blocks.DEEPSLATE)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.DIRT)
            ||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.GRAVEL)||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)
            ||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.PODZOL)||s.is(Blocks.MOSS_BLOCK);
    }
}
