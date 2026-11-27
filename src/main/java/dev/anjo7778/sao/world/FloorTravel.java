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
    private FloorTravel() {}

    public static boolean isAincrad(MinecraftServer server) { return uses(server.overworld(), FIRST_SETTINGS); }

    private static boolean uses(ServerLevel level, ResourceKey<NoiseGeneratorSettings> key) {
        return level != null && level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen && gen.generatorSettings().is(key);
    }

    public static long currentFloor(ServerLevel level, int x, int z) {
        if (level.dimension().equals(Level.OVERWORLD) && uses(level, FIRST_SETTINGS)) return 1;
        if (level.dimension().equals(UPPER) && uses(level, UPPER_SETTINGS)) return FloorLayout.floorAt(x, z);
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
        if (!uses(target, floor == 1 ? FIRST_SETTINGS : UPPER_SETTINGS))
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
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            if (y < 48 || y > 205) continue;
            BlockPos p = new BlockPos(x, y, z);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                && level.getBlockState(p.below()).isFaceSturdy(level, p.below(), Direction.UP)
                && level.getFluidState(p.below()).isEmpty()) return p;
        }
        return null;
    }

    /** Detect bypasses from elytra, portals, commands or other mods. Not a security sandbox. */
    public static void guard(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0 || !player.isAlive()) return;
        ServerLevel level = player.serverLevel();
        if (!level.dimension().equals(UPPER) || !isAincrad(level.getServer()) || !uses(level, UPPER_SETTINGS)) return;
        long floor = FloorLayout.floorAt(player.getBlockX(), player.getBlockZ());
        if (!FloorLayout.supported(floor) || !SaoWorldData.get(level.getServer()).isUnlocked(floor))
            teleport(player, 1);
    }

    private static int reject(ServerPlayer player, String reason) {
        player.sendSystemMessage(Component.literal(reason));
        return 0;
    }
}
