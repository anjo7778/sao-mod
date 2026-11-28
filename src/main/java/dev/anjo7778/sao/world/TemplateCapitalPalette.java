package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import static dev.anjo7778.sao.simulation.CityArchitecture.*;

/** Template-specific palette. Orientation is explicit, rather than inferred from the building position. */
final class TemplateCapitalPalette{
    private final DetailedCapitalPalette detail;
    TemplateCapitalPalette(FloorCatalog.Theme theme){
        detail=new DetailedCapitalPalette(theme);
        // Representative plots for each style are unnecessary: actual plot controls the theme/role palette.
    }
    BlockState state(char c,CityPlan.Plot plot){
        return switch(c){
            case '.'->Blocks.AIR.defaultBlockState();
            case '='->detail.state(Piece.FLOOR,plot);
            case '#'->detail.state(Piece.MASONRY,plot);
            case 'w'->detail.state(Piece.PLASTER,plot);
            case 'p'->detail.state(Piece.POST,plot);
            case 'x'->detail.state(Piece.BEAM_X,plot);
            case 'z'->detail.state(Piece.BEAM_Z,plot);
            case 'r'->detail.state(Piece.ROOF,plot);
            case '>'->detail.state(Piece.ROOF_EAST,plot);
            case '<'->detail.state(Piece.ROOF_WEST,plot);
            case 'v'->detail.state(Piece.ROOF_SOUTH,plot);
            case '^'->detail.state(Piece.ROOF_NORTH,plot);
            case 'k'->Blocks.BRICKS.defaultBlockState();
            case 'g'->Blocks.GLASS.defaultBlockState();
            case '+'->Blocks.SEA_LANTERN.defaultBlockState();
            case 'l'->Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,false);
            case 'H'->Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true);
            case 'B'->Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.UP);
            case 'C'->Blocks.CRAFTING_TABLE.defaultBlockState();
            case 'F'->Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING,Direction.EAST);
            case 'A'->Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING,Direction.NORTH);
            case 'T'->Blocks.SPRUCE_PLANKS.defaultBlockState();
            case 'L'->Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.EAST);
            case 'J'->Blocks.SPRUCE_STAIRS.defaultBlockState().setValue(StairBlock.FACING,Direction.WEST);
            case 'h'->detail.state(Piece.BED_HEAD,plot);
            case 'b'->detail.state(Piece.BED_FOOT,plot);
            case 'S'->Blocks.BOOKSHELF.defaultBlockState();
            case 'i'->detail.state(Piece.TRIM,plot);
            default->throw new IllegalArgumentException("Unknown template symbol "+c);
        };
    }
}
