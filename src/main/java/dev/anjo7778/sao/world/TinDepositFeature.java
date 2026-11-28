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
        int placed=placeDeposit(ctx.level(),ctx.level().getSeed(),floor,x,z);
        for(var spec:dev.anjo7778.sao.simulation.MetalDeposits.ADVANCED)
            placed+=placePoints(ctx.level(),spec.material(),dev.anjo7778.sao.simulation.MetalDeposits.plan(spec,ctx.level().getSeed(),floor,x,z));
        placed+=GateProspectFeature.place(ctx.level(),floor,x,z);
        placed+=GateMineFeature.place(ctx.level(),floor,x,z);
        placed+=GateWoodlandFeature.place(ctx.level(),floor,x,z);
        return placed>0;
    }
    public static int placeDeposit(WorldGenLevel level,long seed,long floor,int originX,int originZ){
        return placePoints(level,dev.anjo7778.sao.simulation.MaterialRules.Material.TIN,TinDeposits.plan(seed,floor,originX,originZ));
    }
    private static int placePoints(WorldGenLevel level,dev.anjo7778.sao.simulation.MaterialRules.Material material,java.util.List<TinDeposits.Point> points){
        int placed=0;
        for(var point:points){
            var pos=new BlockPos(point.x(),point.y(),point.z());
            var old=level.getBlockState(pos);
            if(old.is(Blocks.STONE)){
                if(level.setBlock(pos,SaoBlocks.ore(material,false).defaultBlockState(),2))placed++;
            }else if(old.is(Blocks.DEEPSLATE)){
                if(level.setBlock(pos,SaoBlocks.ore(material,true).defaultBlockState(),2))placed++;
            }
        }
        return placed;
    }
}
