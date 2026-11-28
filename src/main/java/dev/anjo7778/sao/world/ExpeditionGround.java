package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;

/** Bounded nearby terrain lookup. Only already loaded chunks; no destructive terrain repair. */
public final class ExpeditionGround {
    private ExpeditionGround(){}
    public static BlockPos walkable(ServerLevel level,long floor,int x,int z){
        var center=FloorLayout.center(floor);
        int lx=x-center.x(),lz=z-center.z();
        boolean city=CityRules.isSafeZone(level,x,z);
        if(!level.hasChunkAt(new BlockPos(x,CityPlan.GROUND+1,z))||(!city&&!ScoutingTrip.inRange(floor,lx,lz)))return null;
        int highest=city?CityPlan.GROUND+1:CityPlan.GROUND+18;
        int lowest=city?highest:CityPlan.GROUND-18;
        for(int y=highest;y>=lowest;y--){
            BlockPos p=new BlockPos(x,y,z);
            var ground=level.getBlockState(p.below());
            if(ground.is(BlockTags.LEAVES)||ground.is(BlockTags.LOGS)||ground.is(Blocks.MAGMA_BLOCK)||
                ground.is(Blocks.CACTUS)||ground.is(Blocks.POWDER_SNOW)||ground.is(Blocks.CAMPFIRE)||ground.is(Blocks.SOUL_CAMPFIRE)
                ||!level.getFluidState(p).isEmpty()||!level.getFluidState(p.below()).isEmpty())continue;
            if(!ground.isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP))continue;
            if(level.getBlockState(p).getCollisionShape(level,p).isEmpty()
                &&level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty())return p;
        }
        return null;
    }
}
