package dev.anjo7778.sao.world;

import dev.anjo7778.sao.block.SaoBlocks;
import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.simulation.TinDeposits;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** One bounded attempt per decorated chunk, gated to the regional Aincrad preset. */
public final class TinDepositFeature extends Feature<NoneFeatureConfiguration> {
    public TinDepositFeature(){super(NoneFeatureConfiguration.CODEC);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx){
        if(!(ctx.chunkGenerator() instanceof NoiseBasedChunkGenerator gen))return false;
        boolean upper=gen.generatorSettings().is(FloorTravel.UPPER_CITY_REGIONAL);
        if(!upper&&!gen.generatorSettings().is(FloorTravel.FIRST_CITY_REGIONAL))return false;
        int x=ctx.origin().getX()&~15,z=ctx.origin().getZ()&~15;
        long floor=upper?FloorLayout.floorAt(x,z):1;
        return placeDeposit(ctx.level(),ctx.level().getSeed(),floor,x,z)>0;
    }
    public static int placeDeposit(WorldGenLevel level,long seed,long floor,int originX,int originZ){
        int placed=0;
        for(var point:TinDeposits.plan(seed,floor,originX,originZ)){
            var pos=new BlockPos(point.x(),point.y(),point.z());
            var old=level.getBlockState(pos);
            if(old.is(Blocks.STONE)){
                if(level.setBlock(pos,SaoBlocks.TIN_ORE.get().defaultBlockState(),2))placed++;
            }else if(old.is(Blocks.DEEPSLATE)){
                if(level.setBlock(pos,SaoBlocks.DEEPSLATE_TIN_ORE.get().defaultBlockState(),2))placed++;
            }
        }
        return placed;
    }
}
