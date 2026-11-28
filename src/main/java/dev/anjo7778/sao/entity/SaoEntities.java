package dev.anjo7778.sao.entity;

import dev.anjo7778.sao.SaoMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

public final class SaoEntities {
    private static final DeferredRegister<EntityType<?>> TYPES=DeferredRegister.create(Registries.ENTITY_TYPE,SaoMod.MOD_ID);
    public static final Supplier<EntityType<CityNpc>> CITY_NPC=TYPES.register("city_npc",()->EntityType.Builder
        .of(CityNpc::new,MobCategory.MISC).sized(.6f,1.8f).clientTrackingRange(8).updateInterval(3)
        .noSummon().noSave().build("sao:city_npc"));
    public static final Supplier<EntityType<FrenzyBoar>> FRENZY_BOAR=TYPES.register("frenzy_boar",()->EntityType.Builder
        .of(FrenzyBoar::new,MobCategory.MONSTER).sized(1.25f,1.4f).clientTrackingRange(8).updateInterval(3)
        .build("sao:frenzy_boar"));
    private SaoEntities(){}
    public static void register(IEventBus bus){TYPES.register(bus);bus.addListener(SaoEntities::attributes);}
    private static void attributes(EntityAttributeCreationEvent event){event.put(CITY_NPC.get(),CityNpc.attributes().build());event.put(FRENZY_BOAR.get(),FrenzyBoar.attributes().build());}
}
