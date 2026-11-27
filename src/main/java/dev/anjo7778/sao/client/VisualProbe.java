package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;

/** Explicitly opt-in cloud development probe. Never creates worlds during normal play. */
@EventBusSubscriber(modid = "sao", value = Dist.CLIENT)
public final class VisualProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("sao.visualProbe");
    private static boolean started;
    private static boolean ready;
    private static int settledTicks;

    private VisualProbe() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || ready) return;
        Minecraft client = Minecraft.getInstance();
        if (!started && client.screen instanceof TitleScreen title) {
            started = true;
            LOGGER.info("SAO_VISUAL_PROBE_TITLE_READY");
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            var preset = ResourceKey.create(Registries.WORLD_PRESET,
                ResourceLocation.fromNamespaceAndPath("sao", "aincrad"));
            var settings = new LevelSettings("SAO visual test", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("sao-visual-test", settings,
                new WorldOptions(42L, true, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),
                title);
            return;
        }
        if (!started || client.screen != null || client.player == null || client.level == null
                || client.getSingleplayerServer() == null) {
            settledTicks = 0;
            return;
        }

        // Require a settled player and the actual SAO generator, not merely a launched client.
        var key = ResourceKey.create(Registries.NOISE_SETTINGS,
            ResourceLocation.fromNamespaceAndPath("sao", "floor_one"));
        var generator = client.getSingleplayerServer().overworld().getChunkSource().getGenerator();
        if (!(generator instanceof NoiseBasedChunkGenerator noise) || !noise.generatorSettings().is(key)) {
            throw new IllegalStateException("Visual probe opened the wrong world generator");
        }
        if (!client.player.onGround() || client.player.getY() < 55 || client.player.getY() > 150
                || Math.abs(client.player.getX()) > 3000 || Math.abs(client.player.getZ()) > 3000) {
            settledTicks = 0;
            return;
        }
        if (++settledTicks < 80) return;

        ready = true;
        LOGGER.info("SAO_VISUAL_PROBE_WORLD_READY at {} {} {}", client.player.getX(),
            client.player.getY(), client.player.getZ());
    }
}
