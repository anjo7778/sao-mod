package dev.anjo7778.sao.block;

import dev.anjo7778.sao.SaoMod;
import dev.anjo7778.sao.simulation.MaterialRules.Material;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.*;
import java.util.function.Supplier;

public final class SaoBlocks {
    private static final DeferredRegister<Block> BLOCKS=DeferredRegister.create(Registries.BLOCK,SaoMod.MOD_ID);
    private static final Map<Material,Supplier<Block>> ORES=new EnumMap<>(Material.class),DEEP=new EnumMap<>(Material.class);
    public static final Supplier<Block> TIN_ORE=registerOre(Material.TIN,false);
    public static final Supplier<Block> DEEPSLATE_TIN_ORE=registerOre(Material.TIN,true);
    static {
        for(var m:List.of(Material.SILVER,Material.MITHRIL,Material.ADAMANTITE,Material.ORICHALCUM,Material.CELESTIUM,Material.ASTRALITE)){
            registerOre(m,false);registerOre(m,true);
        }
    }
    private static Supplier<Block> registerOre(Material m,boolean deep){
        String id=(deep?"deepslate_":"")+m.name().toLowerCase(Locale.ROOT)+"_ore";
        // Each ore has its own loot identity; do not copy the vanilla loot table.
        var registered=BLOCKS.register(id,()->new Block(BlockBehaviour.Properties.of()
            .strength(deep?4.5f:3f,3f).requiresCorrectToolForDrops().sound(deep?SoundType.DEEPSLATE:SoundType.STONE)));
        (deep?DEEP:ORES).put(m,registered);
        return registered;
    }
    public static Block ore(Material m,boolean deep){return (deep?DEEP:ORES).get(m).get();}
    public static Set<Material> metals(){return Collections.unmodifiableSet(ORES.keySet());}
    public static Material material(Block block){
        for(var m:ORES.keySet())if(ORES.get(m).get()==block||DEEP.get(m).get()==block)return m;
        return null;
    }
    private SaoBlocks(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);}
}
