package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.FloorLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.Set;

/** Prototype travel API. No floor creation, progression grants, or commands on ordinary worlds. */
public final class FloorTravel {
    public static final ResourceKey<Level> UPPER = ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("sao", "upper_floors"));
    private static final ResourceKey<NoiseGeneratorSettings> FIRST_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one"));
    private static final ResourceKey<NoiseGeneratorSettings> UPPER_SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "upper_floors"));
    private static final ResourceKey<NoiseGeneratorSettings> FIRST_CASTLE = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one_castle"));
    private static final ResourceKey<NoiseGeneratorSettings> UPPER_CASTLE = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "upper_floors_castle"));
    private FloorTravel() {}

    public static boolean isAincrad(MinecraftServer server) { return isFirst(server.overworld()); }

    private static boolean uses(ServerLevel level, ResourceKey<NoiseGeneratorSettings> key) {
        return level != null && level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen && gen.generatorSettings().is(key);
    }

    private static boolean isFirst(ServerLevel level) { return uses(level,FIRST_SETTINGS) || uses(level,FIRST_CASTLE); }
    private static boolean isUpper(ServerLevel level) { return uses(level,UPPER_SETTINGS) || uses(level,UPPER_CASTLE); }

    public static long currentFloor(ServerLevel level, int x, int z) {
        if (level.dimension().equals(Level.OVERWORLD) && isFirst(level)) return 1;
        if (level.dimension().equals(UPPER) && isUpper(level)) return FloorLayout.floorAt(x, z);
        return 0;
    }

    public static int request(ServerPlayer player, long floor) {
        MinecraftServer server = player.serverLevel().getServer();
        if (!isAincrad(server)) return reject(player, "Travel is available only in an Aincrad world.");
        if (currentFloor(player.serverLevel(), player.getBlockX(), player.getBlockZ()) == 0)
            return reject(player, "Return to an Aincrad floor before using travel.");
        if (!FloorLayout.supported(floor)) return reject(player, "Floor is outside the current world layout.");
        if (!SaoWorldData.get(server).isUnlocked(floor)) return reject(player, "Floor " + floor + " is locked.");
        if (player.isPassenger() || player.isSleeping() || !player.isAlive())
            return reject(player, "Travel is unavailable in your current state.");
        return teleport(player, floor);
    }

    private static int teleport(ServerPlayer player, long floor) {
        MinecraftServer server = player.serverLevel().getServer();
        ServerLevel target = floor == 1 ? server.overworld() : server.getLevel(UPPER);
        if (!(floor == 1 ? isFirst(target) : isUpper(target)))
            return reject(player, "Destination unavailable. Older prototype saves do not contain the upper realm.");
        FloorLayout.Center c = FloorLayout.center(floor);
        BlockPos safe = safeArrival(target, c.x(), c.z());
        if (safe == null) return reject(player, "No safe arrival point found; you were not moved.");
        if (!player.teleportTo(target, safe.getX()+0.5, safe.getY(), safe.getZ()+0.5, Set.of(), player.getYRot(), 0))
            return reject(player, "Minecraft rejected the teleport.");
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;
        player.sendSystemMessage(Component.literal("Arrived at floor " + floor));
        return 1;
    }

    /** Bounded central search, intentionally refuses rather than overwriting terrain. */
    private static BlockPos safeArrival(ServerLevel level, int cx, int cz) {
        // Search at most 9 columns to bound synchronous chunk work. Prefer the center.
        int[] offsets = {0, -8, 8};
        for (int dx : offsets) for (int dz : offsets) {
            int x = cx + dx, z = cz + dz;
            // Explicitly materialize the destination chunk before asking its heightmap.
            // Prior v0.1.3 client test returned minimum height for an unloaded realm.
            level.getChunk(x >> 4, z >> 4);
            int top = Math.min(205, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z));
            // Bounded scan also handles overhanging roofs and trees.
            for(int y=top; y>=48; y--) {
                BlockPos p = new BlockPos(x,y,z);
                var ground=level.getBlockState(p.below());
                if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                    && ground.isFaceSturdy(level,p.below(),Direction.UP)
                    && level.getFluidState(p.below()).isEmpty()
                    && !ground.is(Blocks.MAGMA_BLOCK) && !ground.is(Blocks.CACTUS)
                    && !ground.is(Blocks.CAMPFIRE) && !ground.is(Blocks.SOUL_CAMPFIRE)) return p;
            }
        }
        return null;
    }

    /** Detect bypasses from elytra, portals, commands or other mods. Not a security sandbox. */
    public static void guard(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !player.isAlive()) return;
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(UPPER) || !isAincrad(level.getServer()) || !isUpper(level)) return;
        long floor = FloorLayout.floorAt(player.getBlockX(), player.getBlockZ());
        if (!FloorLayout.supported(floor) || !SaoWorldData.get(level.getServer()).isUnlocked(floor))
            teleport(player, 1);
    }

    private static int reject(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason));
        return 0;
    }
}
