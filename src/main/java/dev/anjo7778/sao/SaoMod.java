package dev.anjo7778.sao;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import dev.anjo7778.sao.world.CastleDensity;
import dev.anjo7778.sao.world.FloorBiomeSource;
import dev.anjo7778.sao.world.FloorGuardian;
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
    static {
        DENSITIES.register("floor_radial", () -> FloorRadialDensity.MAP_CODEC);
        DENSITIES.register("floor_region", () -> FloorRegionDensity.MAP_CODEC);
        DENSITIES.register("castle_architecture", () -> CastleDensity.MAP_CODEC);
        BIOMES.register("floor_biomes",()->FloorBiomeSource.CODEC);
    }

    public SaoMod(IEventBus modBus) {
        DENSITIES.register(modBus);
        BIOMES.register(modBus);
        NeoForge.EVENT_BUS.addListener(SaoCommands::register);
        NeoForge.EVENT_BUS.addListener(FloorTravel::guard);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST,FloorGuardian::death);
        NeoForge.EVENT_BUS.addListener(FloorGuardian::tick);
        LOGGER.info("SAO Mod loaded. Floor-one prototype and world progression registered.");
    }
}
