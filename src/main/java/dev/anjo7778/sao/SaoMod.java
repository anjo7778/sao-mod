package dev.anjo7778.sao;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.anjo7778.sao.world.CastleDensity;
import dev.anjo7778.sao.world.CityDensity;
import dev.anjo7778.sao.world.CapitalFeature;
import dev.anjo7778.sao.world.CityRules;
import dev.anjo7778.sao.world.NpcPopulationData;
import dev.anjo7778.sao.world.NpcPresence;
import dev.anjo7778.sao.world.AdventureSimulationData;
import dev.anjo7778.sao.world.CitySocial;
import dev.anjo7778.sao.entity.SaoEntities;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.world.BoarSpawns;
import net.minecraft.world.level.levelgen.feature.Feature;
import dev.anjo7778.sao.world.FloorBiomeSource;
import dev.anjo7778.sao.world.FloorGuardian;
import dev.anjo7778.sao.world.HuntService;
import dev.anjo7778.sao.world.RpgEvents;
import dev.anjo7778.sao.network.ProfileNetwork;
import net.minecraft.world.level.biome.BiomeSource;
import net.neoforged.bus.api.EventPriority;
import dev.anjo7778.sao.world.FloorRadialDensity;
import dev.anjo7778.sao.world.FloorRegionDensity;
import dev.anjo7778.sao.world.FloorTravel;
import dev.anjo7778.sao.world.SaoCommands;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(SaoMod.MOD_ID)
public final class SaoMod {
    public static final String MOD_ID = "sao";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITIES =
        DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);
    private static final DeferredRegister<MapCodec<? extends BiomeSource>> BIOMES = DeferredRegister.create(Registries.BIOME_SOURCE,MOD_ID);
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,MOD_ID);
    static {
        DENSITIES.register("floor_radial", () -> FloorRadialDensity.MAP_CODEC);
        DENSITIES.register("floor_region", () -> FloorRegionDensity.MAP_CODEC);
        DENSITIES.register("castle_architecture", () -> CastleDensity.MAP_CODEC);
        BIOMES.register("floor_biomes",()->FloorBiomeSource.CODEC);
        DENSITIES.register("city_blend",()->CityDensity.MAP_CODEC);
        FEATURES.register("capital",CapitalFeature::new);
        FEATURES.register("tin_deposit",dev.anjo7778.sao.world.TinDepositFeature::new);
    }

    public SaoMod(IEventBus modBus) {
        DENSITIES.register(modBus);
        BIOMES.register(modBus);
        FEATURES.register(modBus);
        SaoEntities.register(modBus);
        dev.anjo7778.sao.block.SaoBlocks.register(modBus);
        SaoItems.register(modBus);
        modBus.addListener(SaoItems::creativeTabs);
        modBus.addListener(ProfileNetwork::register);
        modBus.addListener(dev.anjo7778.sao.network.CombatNetwork::register);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,dev.anjo7778.sao.world.SwordCombat::attack);
        NeoForge.EVENT_BUS.addListener(dev.anjo7778.sao.world.SwordCombat::playerTick);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,dev.anjo7778.sao.world.SwordCombat::damage);
        NeoForge.EVENT_BUS.addListener(BoarSpawns::tick);
        NeoForge.EVENT_BUS.addListener(RpgEvents::discover);
        NeoForge.EVENT_BUS.addListener(RpgEvents::crafted);
        NeoForge.EVENT_BUS.addListener(dev.anjo7778.sao.world.ForgeService::useAnvil);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,RpgEvents::mined);
        NeoForge.EVENT_BUS.addListener(RpgEvents::smelted);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,RpgEvents::playerKill);
        NeoForge.EVENT_BUS.addListener(CityRules::spawn);
        NeoForge.EVENT_BUS.addListener(CityRules::spawnCheck);
        NeoForge.EVENT_BUS.addListener(CityRules::damage);
        NeoForge.EVENT_BUS.addListener(HuntService::damage);
        NeoForge.EVENT_BUS.addListener(HuntService::projectileTick);
        NeoForge.EVENT_BUS.addListener(NpcPopulationData::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(NpcPresence::tick);
        NeoForge.EVENT_BUS.addListener(AdventureSimulationData::onTick);
        NeoForge.EVENT_BUS.addListener(CitySocial::tick);
        NeoForge.EVENT_BUS.addListener(SaoCommands::register);
        NeoForge.EVENT_BUS.addListener(FloorTravel::guard);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,FloorGuardian::death);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,HuntService::death);
        NeoForge.EVENT_BUS.addListener(FloorGuardian::tick);
        LOGGER.info("SAO Mod loaded. Floor-one prototype and world progression registered.");
    }
}
