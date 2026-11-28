package dev.anjo7778.sao.world;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Explicit orientation of public fixtures; all states are vanilla blocks. */
final class CourtPalette {
    private CourtPalette(){}
    static BlockState state(char c){
        return switch(c){
            case '.'->Blocks.AIR.defaultBlockState();
            case '='->Blocks.SMOOTH_STONE.defaultBlockState();
            case '#'->Blocks.STONE_BRICKS.defaultBlockState();
            case 'q'->Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            case '+'->Blocks.SEA_LANTERN.defaultBlockState();
            case 'p'->Blocks.STRIPPED_OAK_LOG.defaultBlockState();
            case 'x'->Blocks.STRIPPED_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,Direction.Axis.X);
            case 'g'->Blocks.GRASS_BLOCK.defaultBlockState();
            case 'f'->Blocks.POPPY.defaultBlockState();
            case 't'->Blocks.TARGET.defaultBlockState();
            case 'b'->Blocks.SPRUCE_SLAB.defaultBlockState();
            case 'B'->Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING,Direction.UP);
            case 'T'->Blocks.SPRUCE_PLANKS.defaultBlockState();
            case 'r'->Blocks.RED_WOOL.defaultBlockState();
            case 'u'->Blocks.BLUE_WOOL.defaultBlockState();
            case 'w'->Blocks.WHITE_WOOL.defaultBlockState();
            case 'A'->Blocks.ANVIL.defaultBlockState().setValue(AnvilBlock.FACING,Direction.NORTH);
            case 'C'->Blocks.CRAFTING_TABLE.defaultBlockState();
            case 'l'->Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,false);
            case 'd'->Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING,Direction.NORTH);
            default->throw new IllegalArgumentException("Unknown courtyard symbol "+c);
        };
    }
}
