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
import net.minecraft.tags.BlockTags;
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
    private static final ResourceKey<NoiseGeneratorSettings> FIRST_LIVING = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one_living"));
    private static final ResourceKey<NoiseGeneratorSettings> UPPER_LIVING = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "upper_floors_living"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY_DETAIL=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city_detail"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY_DETAIL=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city_detail"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY_TEMPLATES=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city_templates"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY_TEMPLATES=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city_templates"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY_TEMPLATES_V2=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city_templates_v2"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY_TEMPLATES_V2=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city_templates_v2"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY_DISTRICTS=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city_districts"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY_DISTRICTS=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city_districts"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY_LANDMARKS=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city_landmarks"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY_LANDMARKS=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city_landmarks"));
    public static final ResourceKey<NoiseGeneratorSettings> FIRST_CITY_REGIONAL=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","floor_one_city_regional"));
    public static final ResourceKey<NoiseGeneratorSettings> UPPER_CITY_REGIONAL=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao","upper_floors_city_regional"));
    private FloorTravel() {}

    public static boolean isAincrad(MinecraftServer server) { return isFirst(server.overworld()); }

    private static boolean uses(ServerLevel level, ResourceKey<NoiseGeneratorSettings> key) {
        return level != null && level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen && gen.generatorSettings().is(key);
    }

    private static boolean isFirst(ServerLevel level) { return uses(level,FIRST_SETTINGS) || uses(level,FIRST_CASTLE) || uses(level,FIRST_LIVING)||uses(level,FIRST_CITY)||uses(level,FIRST_CITY_DETAIL)||uses(level,FIRST_CITY_TEMPLATES)||uses(level,FIRST_CITY_TEMPLATES_V2)||uses(level,FIRST_CITY_DISTRICTS)||uses(level,FIRST_CITY_LANDMARKS)||uses(level,FIRST_CITY_REGIONAL); }
    private static boolean isUpper(ServerLevel level) { return uses(level,UPPER_SETTINGS) || uses(level,UPPER_CASTLE) || uses(level,UPPER_LIVING)||uses(level,UPPER_CITY)||uses(level,UPPER_CITY_DETAIL)||uses(level,UPPER_CITY_TEMPLATES)||uses(level,UPPER_CITY_TEMPLATES_V2)||uses(level,UPPER_CITY_DISTRICTS)||uses(level,UPPER_CITY_LANDMARKS)||uses(level,UPPER_CITY_REGIONAL); }

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

    /**
     * Bounded search of the open surface. Never scan downward from the roof:
     * that could select an underground air cavity, as the v0.1.5 screenshots showed.
     */
    static BlockPos safeArrival(ServerLevel level,int cx,int cz) {
        int[] offsets={0,4,-4,8,-8,16,-16};
        for(int dx:offsets) for(int dz:offsets) {
            int x=cx+dx,z=cz+dz;
            level.getChunk(x>>4,z>>4);
            int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
            if(y<48 || y>205)continue;
            BlockPos p=new BlockPos(x,y,z);
            if(isOutdoorArrival(level,p))return p;
        }
        return null;
    }

    /** A daylight column with firm, harmless support and a walkable neighborhood. */
    public static boolean isOutdoorArrival(ServerLevel level,BlockPos p) {
        int x=p.getX(),y=p.getY(),z=p.getZ();
        if(y<48||y>205||!level.getWorldBorder().isWithinBounds(p))return false;
        if(level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z)!=y || !level.canSeeSky(p.above()))return false;
        var ground=level.getBlockState(p.below());
        if(!level.getBlockState(p).isAir()||!level.getBlockState(p.above()).isAir()
            ||!ground.isFaceSturdy(level,p.below(),Direction.UP)||!level.getFluidState(p.below()).isEmpty()
            ||ground.is(BlockTags.LEAVES)||ground.is(BlockTags.LOGS)||ground.is(Blocks.MAGMA_BLOCK)
            ||ground.is(Blocks.CACTUS)||ground.is(Blocks.CAMPFIRE)||ground.is(Blocks.SOUL_CAMPFIRE)
            ||ground.is(Blocks.POWDER_SNOW))return false;
        // Adjacent heightmaps also need real chunks. Four corner loads bound this cost.
        for(int dx:new int[]{-1,1})for(int dz:new int[]{-1,1})level.getChunk((x+dx)>>4,(z+dz)>>4);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            int h=level.getHeight(Heightmap.Types.WORLD_SURFACE,x+dx,z+dz);
            if(Math.abs(h-y)>2)return false;
        }
        return true;
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
