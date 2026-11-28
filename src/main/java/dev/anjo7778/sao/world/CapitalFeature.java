package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

import java.util.EnumMap;

/** One invocation per decorated chunk; no writes to neighboring chunks or existing saves. */
public final class CapitalFeature extends Feature<NoneFeatureConfiguration>{
    public CapitalFeature(){super(NoneFeatureConfiguration.CODEC);}
    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> ctx){
        if(!(ctx.chunkGenerator() instanceof NoiseBasedChunkGenerator gen))return false;
        boolean detailed=gen.generatorSettings().is(FloorTravel.FIRST_CITY_DETAIL)||gen.generatorSettings().is(FloorTravel.UPPER_CITY_DETAIL);
        boolean upper=gen.generatorSettings().is(FloorTravel.UPPER_CITY)||gen.generatorSettings().is(FloorTravel.UPPER_CITY_DETAIL);
        if(!upper&&!detailed&&!gen.generatorSettings().is(FloorTravel.FIRST_CITY))return false;
        int ox=ctx.origin().getX()&~15,oz=ctx.origin().getZ()&~15;
        long floor=upper?FloorLayout.floorAt(ox,oz):1;
        if(!FloorLayout.supported(floor))return false;
        var theme=FloorCatalog.themeFor(floor);
        var palette=palette(theme);
        var detail=detailed?new DetailedCapitalPalette(theme):null;
        var level=ctx.level();var pos=new BlockPos.MutableBlockPos();
        boolean changed=false;
        for(int dx=0;dx<16;dx++)for(int dz=0;dz<16;dz++){
            int x=ox+dx,z=oz+dz;
            var col=CityPlan.column(floor,upper?FloorLayout.local(x):x,upper?FloorLayout.local(z):z);
            if(col.zone()==CityPlan.Zone.OUTSIDE)continue;
            for(int y=93;y<CityPlan.GROUND;y++)level.setBlock(pos.set(x,y,z),palette.get(CityPlan.Piece.FOUNDATION),2);
            for(int y=0;y<=(detailed?CityArchitecture.MAX_HEIGHT:17);y++){
                var state=detailed?detail.state(CityArchitecture.piece(col,y),col.plot()):palette.get(CityPlan.piece(col,y));
                pos.set(x,CityPlan.GROUND+y,z);
                if(!level.getBlockState(pos).equals(state))level.setBlock(pos,state,2);
            }
            changed=true;
        }
        return changed;
    }

    private static EnumMap<CityPlan.Piece,BlockState> palette(FloorCatalog.Theme theme){
        var m=new EnumMap<CityPlan.Piece,BlockState>(CityPlan.Piece.class);
        for(var p:CityPlan.Piece.values())m.put(p,Blocks.AIR.defaultBlockState());
        var wall=Blocks.STONE_BRICKS;var frame=Blocks.STRIPPED_OAK_LOG;var roof=Blocks.DARK_OAK_PLANKS;
        var floor=Blocks.OAK_PLANKS;var paving=Blocks.SMOOTH_STONE;
        switch(theme){
            case MOUNTAINS -> {wall=Blocks.ANDESITE;frame=Blocks.POLISHED_ANDESITE;roof=Blocks.DEEPSLATE_BRICKS;floor=Blocks.SPRUCE_PLANKS;}
            case ANCIENT_FOREST -> {wall=Blocks.SPRUCE_PLANKS;frame=Blocks.STRIPPED_DARK_OAK_LOG;roof=Blocks.DARK_OAK_PLANKS;floor=Blocks.BIRCH_PLANKS;}
            case WATER -> {wall=Blocks.WHITE_TERRACOTTA;frame=Blocks.STRIPPED_SPRUCE_LOG;roof=Blocks.PRISMARINE_BRICKS;}
            case DESERT -> {wall=Blocks.SANDSTONE;frame=Blocks.CUT_SANDSTONE;roof=Blocks.TERRACOTTA;floor=Blocks.SMOOTH_SANDSTONE;paving=Blocks.CUT_SANDSTONE;}
            case FROST -> {wall=Blocks.STONE_BRICKS;frame=Blocks.STRIPPED_SPRUCE_LOG;roof=Blocks.SPRUCE_PLANKS;floor=Blocks.SPRUCE_PLANKS;}
            case VOLCANIC -> {wall=Blocks.POLISHED_BLACKSTONE_BRICKS;frame=Blocks.POLISHED_BASALT;roof=Blocks.NETHER_BRICKS;}
            case CELESTIAL -> {wall=Blocks.CALCITE;frame=Blocks.QUARTZ_PILLAR;roof=Blocks.COPPER_BLOCK;}
            case RUINS -> {wall=Blocks.MOSSY_STONE_BRICKS;frame=Blocks.STRIPPED_DARK_OAK_LOG;roof=Blocks.DEEPSLATE_TILES;}
            default -> {}
        }
        m.put(CityPlan.Piece.PAVING,paving.defaultBlockState());m.put(CityPlan.Piece.PATH,Blocks.COBBLESTONE.defaultBlockState());
        m.put(CityPlan.Piece.FOUNDATION,Blocks.STONE_BRICKS.defaultBlockState());m.put(CityPlan.Piece.FLOOR,floor.defaultBlockState());
        m.put(CityPlan.Piece.WALL,wall.defaultBlockState());m.put(CityPlan.Piece.FRAME,frame.defaultBlockState());
        m.put(CityPlan.Piece.ROOF,roof.defaultBlockState());m.put(CityPlan.Piece.GLASS,Blocks.GLASS.defaultBlockState());
        m.put(CityPlan.Piece.LIGHT,Blocks.SEA_LANTERN.defaultBlockState());m.put(CityPlan.Piece.GRASS,Blocks.GRASS_BLOCK.defaultBlockState());
        m.put(CityPlan.Piece.BOOKSHELF,Blocks.BOOKSHELF.defaultBlockState());m.put(CityPlan.Piece.BARREL,Blocks.BARREL.defaultBlockState());
        m.put(CityPlan.Piece.CRAFTING,Blocks.CRAFTING_TABLE.defaultBlockState());m.put(CityPlan.Piece.FURNACE,Blocks.FURNACE.defaultBlockState());
        m.put(CityPlan.Piece.TABLE,Blocks.SPRUCE_PLANKS.defaultBlockState());m.put(CityPlan.Piece.BENCH,Blocks.SPRUCE_SLAB.defaultBlockState());
        m.put(CityPlan.Piece.WOOL,Blocks.WHITE_WOOL.defaultBlockState());return m;
    }
}
