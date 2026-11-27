package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.world.FloorGuardian;
import dev.anjo7778.sao.world.FloorBiomeSource;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.biome.Biomes;
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
    private enum Phase { START, FIRST_SETTLE, FIRST_CAPTURE, CASTLE_SETTLE, CASTLE_CAPTURE, GUARDIAN_SETTLE, GUARDIAN_CAPTURE, UPPER_SETTLE, UPPER_CAPTURE, RETURN_SETTLE, DONE }
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
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            var settings = new LevelSettings("SAO visual test", GameType.CREATIVE, false,
                Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
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
                var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one_living"));
                var generator = client.getSingleplayerServer().overworld().getChunkSource().getGenerator();
                if (!(generator instanceof NoiseBasedChunkGenerator noise) || !noise.generatorSettings().is(key) || !(noise.getBiomeSource() instanceof FloorBiomeSource))
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
                startGuardian(client);
                advance(Phase.GUARDIAN_SETTLE);
            }
            case GUARDIAN_SETTLE -> {
                if(!settled(client,false))return;
                LOGGER.info("SAO_VISUAL_PROBE_GUARDIAN_READY");
                advance(Phase.GUARDIAN_CAPTURE);
            }
            case GUARDIAN_CAPTURE -> {
                if(!Files.exists(client.gameDirectory.toPath().resolve("probe-guardian-captured")))return;
                startTravel(client,true);
                advance(Phase.UPPER_SETTLE);
            }
            case UPPER_SETTLE -> {
                if (!settled(client, true)) return;
                LOGGER.info("SAO_VISUAL_PROBE_UPPER_READY at {} {} {}", client.player.getX(), client.player.getY(), client.player.getZ());
                advance(Phase.UPPER_CAPTURE);
            }
            case UPPER_CAPTURE -> {
                if (!Files.exists(client.gameDirectory.toPath().resolve("probe-upper-captured"))) return;
                verifyUpperAndReturn(client);
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

    private static void startGuardian(Minecraft client) {
        MinecraftServer server=client.getSingleplayerServer();
        var uuid=client.player.getUUID();
        pending=CompletableFuture.runAsync(()->{
            var player=server.getPlayerList().getPlayer(uuid);
            if(player==null)throw new IllegalStateException("Missing test player");
            var data=SaoWorldData.get(server);
            if(data.highestUnlocked()!=1 || FloorTravel.request(player,2)!=0)
                throw new IllegalStateException("Locked travel was not rejected");
            LOGGER.info("SAO_VISUAL_PROBE_LOCKED_REJECTED");
            if(FloorTravel.request(player,1)!=1)throw new IllegalStateException("Cannot return to courtyard");
            if(FloorGuardian.challenge(player)!=1 || FloorGuardian.challenge(player)!=0)
                throw new IllegalStateException("Guardian spawn or duplicate prevention failed");
            var first=data.activeGuardian();
            if(!(player.serverLevel().getEntity(first) instanceof Ravager discarded))throw new IllegalStateException("Missing first guardian");
            if(FloorGuardian.abandon(player)!=1 || data.activeGuardian()!=null || data.highestUnlocked()!=1 || !discarded.isRemoved())
                throw new IllegalStateException("Abandonment must not unlock");
            if(FloorGuardian.challenge(player)!=1)throw new IllegalStateException("Challenge restart failed");
            if(!(player.serverLevel().getEntity(data.activeGuardian()) instanceof Ravager boss))
                throw new IllegalStateException("Missing active guardian");
            // Keep the fixture in view for screenshot; production AI stays enabled.
            double dx=boss.getX()-player.getX(),dz=boss.getZ()-player.getZ();
            float yaw=(float)(-Math.toDegrees(Math.atan2(dx,dz)));
            if(!player.teleportTo(player.serverLevel(),player.getX(),player.getY(),player.getZ(),Set.of(),yaw,0))
                throw new IllegalStateException("Unable to aim test camera");
            LOGGER.info("SAO_VISUAL_PROBE_GUARDIAN_SPAWNED id={} hp={}",boss.getUUID(),boss.getHealth());
        },server::execute);
    }

    private static void verifyUpperAndReturn(Minecraft client){
        MinecraftServer server=client.getSingleplayerServer();
        var uuid=client.player.getUUID();
        pending=CompletableFuture.runAsync(()->{
            var player=server.getPlayerList().getPlayer(uuid);
            if(player==null)throw new IllegalStateException("Missing test player");
            var level=player.serverLevel();
            if(!level.dimension().equals(FloorTravel.UPPER))throw new IllegalStateException("Wrong upper dimension");
            var key=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_living"));
            if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen) || !gen.generatorSettings().is(key) || !(gen.getBiomeSource() instanceof FloorBiomeSource))
                throw new IllegalStateException("Wrong upper generator");
            level.getChunk(640>>4,640>>4);
            var biome=level.getBiome(new BlockPos(640,120,640));
            if(biome.is(Biomes.PLAINS))throw new IllegalStateException("Mountain floor remained plains outside courtyard");
            LOGGER.info("SAO_VISUAL_PROBE_UPPER_BIOME {}",biome.unwrapKey().orElseThrow().location());
            if(FloorTravel.request(player,3)!=0)throw new IllegalStateException("Floor 3 was unlocked prematurely");
            if(FloorTravel.request(player,1)!=1)throw new IllegalStateException("Return failed");
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
                if(data.highestUnlocked()!=1 || !(player.serverLevel().getEntity(data.activeGuardian()) instanceof Ravager boss))
                    throw new IllegalStateException("No active guardian before death test");
                // Actual entity damage + LivingDeathEvent, not a direct progression API call.
                if(!boss.hurt(player.damageSources().playerAttack(player),10000.0F) || !boss.isDeadOrDying()
                    || data.highestUnlocked()!=2 || data.activeGuardian()!=null)
                    throw new IllegalStateException("Player-attributed guardian death did not unlock exactly one floor");
                LOGGER.info("SAO_VISUAL_PROBE_REAL_GUARDIAN_DEATH_UNLOCKED floor={}",data.highestUnlocked());
            }
            int result = FloorTravel.request(player, upper ? 2 : 1);
            if (result != 1) throw new IllegalStateException("Travel request failed");
        }, server::execute);
    }
}
