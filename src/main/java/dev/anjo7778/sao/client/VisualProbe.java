package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.simulation.FloorProgress;
import dev.anjo7778.sao.world.FloorTravel;
import dev.anjo7778.sao.world.SaoWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.Blocks;
import java.util.Set;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;

/** Cloud development test only; never active on a normal run. */
@EventBusSubscriber(modid = "sao", value = Dist.CLIENT)
public final class VisualProbe {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("sao.visualProbe");
    private enum Phase { START, FIRST_SETTLE, FIRST_CAPTURE, CASTLE_SETTLE, CASTLE_CAPTURE, UPPER_SETTLE, UPPER_CAPTURE, RETURN_SETTLE, DONE }
    private static Phase phase = Phase.START;
    private static int settledTicks;
    private static String lastScreen = "uninitialized";
    private static CompletableFuture<Void> pending;

    private VisualProbe() {}

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        if (!ENABLED || phase == Phase.DONE) return;
        Minecraft client = Minecraft.getInstance();
        String screen = client.screen == null ? "none" : client.screen.getClass().getName();
        if (!screen.equals(lastScreen)) {
            LOGGER.info("SAO_VISUAL_PROBE_SCREEN {}", screen);
            lastScreen = screen;
        }
        if (phase == Phase.START && client.screen instanceof TitleScreen title) {
            if (Files.exists(client.gameDirectory.toPath().resolve("saves/sao-visual-test")))
                throw new IllegalStateException("Probe requires a fresh disposable test world");
            phase = Phase.FIRST_SETTLE;
            client.options.renderDistance().set(6);
            client.options.simulationDistance().set(5);
            var preset = ResourceKey.create(Registries.WORLD_PRESET, ResourceLocation.fromNamespaceAndPath("sao", "aincrad"));
            var settings = new LevelSettings("SAO visual test", GameType.CREATIVE, false,
                Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel("sao-visual-test", settings,
                new WorldOptions(42L, true, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(), title);
            return;
        }
        if (pending != null) {
            if (!pending.isDone()) return;
            pending.join();
            pending = null;
        }
        if (client.screen != null || client.player == null || client.level == null || client.getSingleplayerServer() == null) {
            settledTicks = 0;
            return;
        }
        switch (phase) {
            case FIRST_SETTLE -> {
                if (!settled(client, false)) return;
                var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one_castle"));
                var generator = client.getSingleplayerServer().overworld().getChunkSource().getGenerator();
                if (!(generator instanceof NoiseBasedChunkGenerator noise) || !noise.generatorSettings().is(key))
                    throw new IllegalStateException("Wrong first-floor generator");
                LOGGER.info("SAO_VISUAL_PROBE_FIRST_READY at {} {} {}", client.player.getX(), client.player.getY(), client.player.getZ());
                advance(Phase.FIRST_CAPTURE);
            }
            case FIRST_CAPTURE -> {
                if (!Files.exists(client.gameDirectory.toPath().resolve("probe-first-captured"))) return;
                surveyCastle(client);
                advance(Phase.CASTLE_SETTLE);
            }
            case CASTLE_SETTLE -> {
                if (!settled(client, false) || Math.abs(client.player.getX()-320)>3 || Math.abs(client.player.getZ()-320)>3) return;
                LOGGER.info("SAO_VISUAL_PROBE_CASTLE_READY at {} {} {}", client.player.getX(), client.player.getY(), client.player.getZ());
                advance(Phase.CASTLE_CAPTURE);
            }
            case CASTLE_CAPTURE -> {
                if (!Files.exists(client.gameDirectory.toPath().resolve("probe-castle-captured"))) return;
                startTravel(client, true);
                advance(Phase.UPPER_SETTLE);
            }
            case UPPER_SETTLE -> {
                if (!settled(client, true)) return;
                LOGGER.info("SAO_VISUAL_PROBE_UPPER_READY at {} {} {}", client.player.getX(), client.player.getY(), client.player.getZ());
                advance(Phase.UPPER_CAPTURE);
            }
            case UPPER_CAPTURE -> {
                if (!Files.exists(client.gameDirectory.toPath().resolve("probe-upper-captured"))) return;
                startTravel(client, false);
                advance(Phase.RETURN_SETTLE);
            }
            case RETURN_SETTLE -> {
                if (!settled(client, false)) return;
                LOGGER.info("SAO_VISUAL_PROBE_RETURN_READY at {} {} {}", client.player.getX(), client.player.getY(), client.player.getZ());
                advance(Phase.DONE);
            }
            default -> {}
        }
    }

    private static void advance(Phase next) {
        phase = next;
        settledTicks = 0;
    }

    private static boolean settled(Minecraft client, boolean upper) {
        var player = client.player;
        boolean correct = upper ? client.level.dimension().equals(FloorTravel.UPPER)
            && FloorLayout.floorAt(player.getBlockX(), player.getBlockZ()) == 2
            : client.level.dimension().equals(Level.OVERWORLD);
        if (!correct || !player.onGround() || player.getY() < 48 || player.getY() > 205
                || Math.abs(player.getX()) > 3000 || Math.abs(player.getZ()) > 3000) {
            settledTicks = 0;
            return false;
        }
        return ++settledTicks >= 80;
    }

    private static void surveyCastle(Minecraft client) {
        MinecraftServer server = client.getSingleplayerServer();
        var uuid=client.player.getUUID();
        pending=CompletableFuture.runAsync(()->{
            var player=server.getPlayerList().getPlayer(uuid);
            if(player==null) throw new IllegalStateException("Missing test player");
            var level=server.overworld();
            // Generate actual structure chunks and verify placed blocks, not just density.
            level.getChunk(384>>4,384>>4);
            level.getChunk(384>>4,192>>4);
            level.getChunk(320>>4,320>>4);
            if (!level.getBlockState(new BlockPos(384,220,384)).is(Blocks.STONE_BRICKS))
                throw new IllegalStateException("Pillar block missing");
            if (!level.getBlockState(new BlockPos(384,280,192)).is(Blocks.STONE_BRICKS))
                throw new IllegalStateException("Ceiling block missing");
            if (!level.getBlockState(new BlockPos(320,280,320)).isAir())
                throw new IllegalStateException("Daylight opening obstructed");
            LOGGER.info("SAO_VISUAL_PROBE_CASTLE_BLOCKS_VERIFIED");
            int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,320,320);
            if(y<48 || y>205) throw new IllegalStateException("Unsafe castle viewpoint");
            if(!player.teleportTo(level,320.5,y,320.5,Set.of(),-45,-32))
                throw new IllegalStateException("Castle survey teleport failed");
            player.setDeltaMovement(0,0,0);player.fallDistance=0;
        },server::execute);
    }

    private static void startTravel(Minecraft client, boolean upper) {
        MinecraftServer server = client.getSingleplayerServer();
        var uuid = client.player.getUUID();
        pending = CompletableFuture.runAsync(() -> {
            var player = server.getPlayerList().getPlayer(uuid);
            if (player == null) throw new IllegalStateException("Missing server player");
            if (upper) {
                var data = SaoWorldData.get(server);
                if (data.highestUnlocked() != 1 || FloorTravel.request(player, 2) != 0)
                    throw new IllegalStateException("Locked floor was not rejected");
                LOGGER.info("SAO_VISUAL_PROBE_LOCKED_REJECTED");
                if (!data.recordVictory(1, FloorProgress.Victor.PLAYER))
                    throw new IllegalStateException("Synthetic test victory rejected");
                LOGGER.info("SAO_VISUAL_PROBE_SYNTHETIC_VICTORY");
            }
            int result = FloorTravel.request(player, upper ? 2 : 1);
            if (result != 1) throw new IllegalStateException("Travel request failed");
        }, server::execute);
    }
}
