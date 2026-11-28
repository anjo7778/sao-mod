package dev.anjo7778.sao.item;

import dev.anjo7778.sao.SaoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class SaoItems {
    private static final DeferredRegister<Item> ITEMS=DeferredRegister.create(Registries.ITEM,SaoMod.MOD_ID);
    public static final Supplier<Item> BOAR_TUSK=ITEMS.register("boar_tusk",()->new Item(new Item.Properties()));
    public static final Supplier<Item> STEEL_BLOOM=ITEMS.register("steel_bloom",()->new Item(new Item.Properties()));
    public static final Supplier<Item> STEEL_INGOT=ITEMS.register("steel_ingot",()->new RefinedMetalItem(dev.anjo7778.sao.simulation.MaterialRules.Material.STEEL,new Item.Properties()));
    public static final Supplier<Item> RAW_TIN=ITEMS.register("raw_tin",()->new Item(new Item.Properties()));
    public static final Supplier<Item> TIN_INGOT=ITEMS.register("tin_ingot",()->new RefinedMetalItem(dev.anjo7778.sao.simulation.MaterialRules.Material.TIN,new Item.Properties()));
    public static final Supplier<Item> BRONZE_BLEND=ITEMS.register("bronze_blend",()->new Item(new Item.Properties()));
    public static final Supplier<Item> BRONZE_INGOT=ITEMS.register("bronze_ingot",()->new RefinedMetalItem(dev.anjo7778.sao.simulation.MaterialRules.Material.BRONZE,new Item.Properties()));
    public static final Supplier<Item> TIN_ORE=ITEMS.register("tin_ore",()->new net.minecraft.world.item.BlockItem(dev.anjo7778.sao.block.SaoBlocks.TIN_ORE.get(),new Item.Properties()));
    public static final Supplier<Item> DEEPSLATE_TIN_ORE=ITEMS.register("deepslate_tin_ore",()->new net.minecraft.world.item.BlockItem(dev.anjo7778.sao.block.SaoBlocks.DEEPSLATE_TIN_ORE.get(),new Item.Properties()));
    private SaoItems(){}
    public static void creativeTabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){
        if(e.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)){
            for(var item:java.util.List.of(BOAR_TUSK,STEEL_BLOOM,STEEL_INGOT,RAW_TIN,TIN_INGOT,BRONZE_BLEND,BRONZE_INGOT))e.accept(item.get());
        }
        if(e.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.NATURAL_BLOCKS)){
            e.accept(TIN_ORE.get());e.accept(DEEPSLATE_TIN_ORE.get());
        }
    }
    public static void register(IEventBus bus){ITEMS.register(bus);}
}
