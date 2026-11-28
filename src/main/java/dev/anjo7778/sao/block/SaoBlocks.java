package dev.anjo7778.sao.block;

import dev.anjo7778.sao.SaoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class SaoBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,SaoMod.MOD_ID);
    public static final Supplier<Block> TIN_ORE=BLOCKS.register("tin_ore",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_ORE)));
    public static final Supplier<Block> DEEPSLATE_TIN_ORE=BLOCKS.register("deepslate_tin_ore",()->new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.DEEPSLATE_IRON_ORE)));
    private SaoBlocks(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);}
}
