package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.FloorProgress;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One progression record per world save; never one instance per floor or player. */
public final class SaoWorldData extends SavedData {
    private static final int SCHEMA = 1;
    private static final Factory<SaoWorldData> FACTORY = new Factory<>(SaoWorldData::new, SaoWorldData::load, null);
    private final FloorProgress progress;
    private final boolean npcUnlocks;

    public SaoWorldData() { this(1L, true); }

    private SaoWorldData(long highestUnlocked, boolean npcUnlocks) {
        this.npcUnlocks = npcUnlocks;
        this.progress = new FloorProgress(highestUnlocked, npcUnlocks);
    }

    public static SaoWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "sao_world");
    }

    public static SaoWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.getInt("schema") != SCHEMA) throw new IllegalArgumentException("Unsupported SAO world data schema");
        long highest = tag.getLong("highest_unlocked");
        if (highest < 1 || !tag.contains("npc_unlocks", 1)) throw new IllegalArgumentException("Invalid SAO progression data");
        return new SaoWorldData(highest, tag.getBoolean("npc_unlocks"));
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", SCHEMA);
        tag.putLong("highest_unlocked", progress.highestUnlocked());
        tag.putBoolean("npc_unlocks", npcUnlocks);
        return tag;
    }

    public long highestUnlocked() { return progress.highestUnlocked(); }
    public boolean isUnlocked(long floor) { return progress.isUnlocked(floor); }

    /** For trusted server-side boss resolution only. No player command grants victories. */
    public boolean recordVictory(long floor, FloorProgress.Victor victor) {
        if (!progress.recordVictory(floor, victor)) return false;
        setDirty();
        return true;
    }
}
