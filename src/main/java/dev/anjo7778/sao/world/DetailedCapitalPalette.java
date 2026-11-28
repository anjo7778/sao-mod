package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import java.util.EnumMap;

import static dev.anjo7778.sao.simulation.CityArchitecture.*;

/** Vanilla block palette; no external resource packs or mod dependencies. */
final class DetailedCapitalPalette {
    private final EnumMap<Style,EnumMap<Piece,BlockState>> styles=new EnumMap<>(Style.class);
    DetailedCapitalPalette(FloorCatalog.Theme theme){
        for(Style style:Style.values())styles.put(style,create(theme,style));
    }
    BlockState state(Piece p,CityPlan.Plot plot){return styles.get(style(plot)).get(p);}
    private static EnumMap<Piece,BlockState> create(FloorCatalog.Theme theme,Style style){
        var m=new EnumMap<Piece,BlockState>(Piece.class);
        for(Piece p:Piece.values())m.put(p,Blocks.AIR.defaultBlockState());
        Block stone=Blocks.STONE_BRICKS,trim=Blocks.CHISELED_STONE_BRICKS,plaster=Blocks.WHITE_TERRACOTTA,
            timber=Blocks.STRIPPED_DARK_OAK_LOG,floor=Blocks.OAK_PLANKS,paving=Blocks.SMOOTH_STONE,
            roof=Blocks.DARK_OAK_PLANKS,stairs=Blocks.DARK_OAK_STAIRS,bed=Blocks.RED_BED,carpet=Blocks.RED_CARPET;
        switch(style){
            case COTTAGE -> {plaster=Blocks.WHITE_TERRACOTTA;roof=Blocks.SPRUCE_PLANKS;stairs=Blocks.SPRUCE_STAIRS;}
            case TOWNHOUSE -> {plaster=Blocks.YELLOW_TERRACOTTA;roof=Blocks.BRICKS;stairs=Blocks.BRICK_STAIRS;}
            case INN -> {plaster=Blocks.ORANGE_TERRACOTTA;roof=Blocks.DARK_OAK_PLANKS;stairs=Blocks.DARK_OAK_STAIRS;bed=Blocks.BLUE_BED;}
            case MARKET -> {plaster=Blocks.WHITE_TERRACOTTA;roof=Blocks.BRICKS;stairs=Blocks.BRICK_STAIRS;}
            case GUILDHALL -> {roof=Blocks.DEEPSLATE_TILES;stairs=Blocks.DEEPSLATE_TILE_STAIRS;carpet=Blocks.BLUE_CARPET;}
            case FORGE -> {roof=Blocks.DEEPSLATE_BRICKS;stairs=Blocks.DEEPSLATE_BRICK_STAIRS;}
            case CLINIC -> {plaster=Blocks.WHITE_TERRACOTTA;roof=Blocks.SPRUCE_PLANKS;stairs=Blocks.SPRUCE_STAIRS;bed=Blocks.WHITE_BED;}
            case QUEST_HALL -> {roof=Blocks.BRICKS;stairs=Blocks.BRICK_STAIRS;carpet=Blocks.GREEN_CARPET;}
        }
        switch(theme){
            case MOUNTAINS -> {stone=Blocks.ANDESITE;trim=Blocks.POLISHED_ANDESITE;plaster=Blocks.LIGHT_GRAY_TERRACOTTA;timber=Blocks.STRIPPED_SPRUCE_LOG;floor=Blocks.SPRUCE_PLANKS;roof=Blocks.DEEPSLATE_TILES;stairs=Blocks.DEEPSLATE_TILE_STAIRS;}
            case ANCIENT_FOREST -> {stone=Blocks.MOSSY_STONE_BRICKS;trim=Blocks.CHISELED_STONE_BRICKS;plaster=Blocks.SPRUCE_PLANKS;floor=Blocks.BIRCH_PLANKS;roof=Blocks.DARK_OAK_PLANKS;stairs=Blocks.DARK_OAK_STAIRS;}
            case WATER -> {plaster=Blocks.WHITE_TERRACOTTA;timber=Blocks.STRIPPED_SPRUCE_LOG;roof=Blocks.PRISMARINE_BRICKS;stairs=Blocks.PRISMARINE_BRICK_STAIRS;}
            case DESERT -> {stone=Blocks.SANDSTONE;trim=Blocks.CHISELED_SANDSTONE;plaster=Blocks.SMOOTH_SANDSTONE;timber=Blocks.STRIPPED_ACACIA_LOG;floor=Blocks.SMOOTH_SANDSTONE;paving=Blocks.CUT_SANDSTONE;roof=Blocks.SANDSTONE;stairs=Blocks.SANDSTONE_STAIRS;}
            case FROST -> {plaster=Blocks.WHITE_TERRACOTTA;timber=Blocks.STRIPPED_SPRUCE_LOG;floor=Blocks.SPRUCE_PLANKS;roof=Blocks.SPRUCE_PLANKS;stairs=Blocks.SPRUCE_STAIRS;}
            case VOLCANIC -> {stone=Blocks.POLISHED_BLACKSTONE_BRICKS;trim=Blocks.CHISELED_POLISHED_BLACKSTONE;plaster=Blocks.GRAY_TERRACOTTA;timber=Blocks.STRIPPED_CRIMSON_STEM;roof=Blocks.NETHER_BRICKS;stairs=Blocks.NETHER_BRICK_STAIRS;}
            case CELESTIAL -> {stone=Blocks.QUARTZ_BLOCK;trim=Blocks.CHISELED_QUARTZ_BLOCK;plaster=Blocks.CALCITE;roof=Blocks.QUARTZ_BLOCK;stairs=Blocks.QUARTZ_STAIRS;}
            case RUINS -> {stone=Blocks.MOSSY_STONE_BRICKS;plaster=Blocks.LIGHT_GRAY_TERRACOTTA;roof=Blocks.DEEPSLATE_TILES;stairs=Blocks.DEEPSLATE_TILE_STAIRS;}
            default -> {}
        }
        m.put(Piece.PAVING,paving.defaultBlockState());m.put(Piece.ROAD,Blocks.COBBLESTONE.defaultBlockState());
        m.put(Piece.TRIM,trim.defaultBlockState());m.put(Piece.FOUNDATION,stone.defaultBlockState());
        m.put(Piece.FLOOR,floor.defaultBlockState());m.put(Piece.MASONRY,stone.defaultBlockState());
        m.put(Piece.PLASTER,plaster.defaultBlockState());m.put(Piece.POST,timber.defaultBlockState());
        m.put(Piece.BEAM_X,timber.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
        m.put(Piece.BEAM_Z,timber.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.Z));
        m.put(Piece.ROOF,roof.defaultBlockState());
        m.put(Piece.ROOF_EAST,stairs.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST));
        m.put(Piece.ROOF_WEST,stairs.defaultBlockState().setValue(StairBlock.FACING,Direction.WEST));
        m.put(Piece.ROOF_NORTH,stairs.defaultBlockState().setValue(StairBlock.FACING,Direction.NORTH));
        m.put(Piece.ROOF_SOUTH,stairs.defaultBlockState().setValue(StairBlock.FACING,Direction.SOUTH));
        m.put(Piece.CHIMNEY,Blocks.BRICKS.defaultBlockState());m.put(Piece.GLASS,Blocks.GLASS.defaultBlockState());
        // Full light block works for both freestanding and supported lantern-shaped geometry.
        m.put(Piece.LIGHT,Blocks.SEA_LANTERN.defaultBlockState());m.put(Piece.GRASS,Blocks.GRASS_BLOCK.defaultBlockState());
        m.put(Piece.LEAVES,Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT,true));
        m.put(Piece.FLOWER,Blocks.POPPY.defaultBlockState());m.put(Piece.FENCE,Blocks.SPRUCE_FENCE.defaultBlockState());
        m.put(Piece.BOOKSHELF,Blocks.BOOKSHELF.defaultBlockState());m.put(Piece.BARREL,Blocks.BARREL.defaultBlockState());
        m.put(Piece.CRAFTING,Blocks.CRAFTING_TABLE.defaultBlockState());m.put(Piece.FURNACE,Blocks.FURNACE.defaultBlockState());
        m.put(Piece.ANVIL,Blocks.ANVIL.defaultBlockState());m.put(Piece.TABLE,Blocks.SPRUCE_PLANKS.defaultBlockState());
        m.put(Piece.BENCH,Blocks.SPRUCE_SLAB.defaultBlockState());m.put(Piece.CARPET,carpet.defaultBlockState());
        m.put(Piece.BED_HEAD,bed.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.HEAD));
        m.put(Piece.BED_FOOT,bed.defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH).setValue(BedBlock.PART,BedPart.FOOT));
        return m;
    }
}
