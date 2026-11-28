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
    private static final java.util.Map<dev.anjo7778.sao.simulation.MaterialRules.Material,Supplier<Item>> RAW=new java.util.EnumMap<>(dev.anjo7778.sao.simulation.MaterialRules.Material.class);
    private static final java.util.Map<dev.anjo7778.sao.simulation.MaterialRules.Material,Supplier<Item>> REFINED=new java.util.EnumMap<>(dev.anjo7778.sao.simulation.MaterialRules.Material.class);
    private static final java.util.List<Supplier<Item>> EXTRA_INGREDIENTS=new java.util.ArrayList<>(),EXTRA_BLOCKS=new java.util.ArrayList<>();
    static{
        RAW.put(dev.anjo7778.sao.simulation.MaterialRules.Material.TIN,RAW_TIN);
        REFINED.put(dev.anjo7778.sao.simulation.MaterialRules.Material.TIN,TIN_INGOT);
        REFINED.put(dev.anjo7778.sao.simulation.MaterialRules.Material.STEEL,STEEL_INGOT);
        REFINED.put(dev.anjo7778.sao.simulation.MaterialRules.Material.BRONZE,BRONZE_INGOT);
        for(var m:java.util.List.of(dev.anjo7778.sao.simulation.MaterialRules.Material.SILVER,dev.anjo7778.sao.simulation.MaterialRules.Material.MITHRIL,dev.anjo7778.sao.simulation.MaterialRules.Material.ADAMANTITE,dev.anjo7778.sao.simulation.MaterialRules.Material.ORICHALCUM,dev.anjo7778.sao.simulation.MaterialRules.Material.CELESTIUM,dev.anjo7778.sao.simulation.MaterialRules.Material.ASTRALITE)){
            String id=m.name().toLowerCase(java.util.Locale.ROOT);
            var raw=ITEMS.register("raw_"+id,()->new Item(new Item.Properties()));
            RAW.put(m,raw);EXTRA_INGREDIENTS.add(raw);
            registerRefined(m,id);
            for(boolean deep:new boolean[]{false,true}){
                var ore=ITEMS.register((deep?"deepslate_":"")+id+"_ore",()->new net.minecraft.world.item.BlockItem(dev.anjo7778.sao.block.SaoBlocks.ore(m,deep),new Item.Properties()));
                EXTRA_BLOCKS.add(ore);
            }
        }
        for(var m:java.util.List.of(dev.anjo7778.sao.simulation.MaterialRules.Material.SILVERSTEEL,dev.anjo7778.sao.simulation.MaterialRules.Material.MITHRIL_STEEL,dev.anjo7778.sao.simulation.MaterialRules.Material.DARKSTEEL)){
            String id=m.name().toLowerCase(java.util.Locale.ROOT);
            EXTRA_INGREDIENTS.add(ITEMS.register(id+"_blend",()->new Item(new Item.Properties())));
            registerRefined(m,id);
        }
    }
    private static void registerRefined(dev.anjo7778.sao.simulation.MaterialRules.Material m,String id){
        var item=ITEMS.register(id+"_ingot",()->new RefinedMetalItem(m,new Item.Properties()));
        REFINED.put(m,item);EXTRA_INGREDIENTS.add(item);
    }
    public static Item raw(dev.anjo7778.sao.simulation.MaterialRules.Material m){return RAW.get(m).get();}
    public static Item refined(dev.anjo7778.sao.simulation.MaterialRules.Material m){return REFINED.get(m).get();}
    public static final Supplier<Item> STEEL_SWORD=ITEMS.register("steel_sword",NativeSteelSword::new);
    private SaoItems(){}
    public static void creativeTabs(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){
        if(e.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.INGREDIENTS)){
            for(var item:java.util.List.of(BOAR_TUSK,STEEL_BLOOM,STEEL_INGOT,RAW_TIN,TIN_INGOT,BRONZE_BLEND,BRONZE_INGOT))e.accept(item.get());
            EXTRA_INGREDIENTS.forEach(i->e.accept(i.get()));
        }
        if(e.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.NATURAL_BLOCKS)){
            e.accept(TIN_ORE.get());e.accept(DEEPSLATE_TIN_ORE.get());EXTRA_BLOCKS.forEach(i->e.accept(i.get()));
        }
        if(e.getTabKey().equals(net.minecraft.world.item.CreativeModeTabs.COMBAT))e.accept(STEEL_SWORD.get());
    }
    public static void register(IEventBus bus){ITEMS.register(bus);}
}
