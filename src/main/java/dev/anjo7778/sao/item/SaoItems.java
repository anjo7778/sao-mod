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
    private SaoItems(){}
    public static void register(IEventBus bus){ITEMS.register(bus);}
}
