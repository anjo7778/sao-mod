package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.FloorProgress;
import dev.anjo7778.sao.simulation.FloorLayout;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/** One progression record per world save; never one instance per floor or player. */
public final class SaoWorldData extends SavedData {
    private static final int SCHEMA = 2;
    private static final Factory<SaoWorldData> FACTORY = new Factory<>(SaoWorldData::new, SaoWorldData::load, null);
    private final FloorProgress progress;
    private final boolean npcUnlocks;
    private UUID activeGuardian;

    public SaoWorldData() { this(1L, true); }

    private SaoWorldData(long highestUnlocked, boolean npcUnlocks) {
        this.npcUnlocks = npcUnlocks;
        this.progress = new FloorProgress(highestUnlocked, npcUnlocks);
    }

    public static SaoWorldData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "sao_world");
    }

    public static SaoWorldData load(CompoundTag tag, HolderLookup.Provider registries) {
        int schema=tag.getInt("schema");
        if (schema != 1 && schema != SCHEMA) throw new IllegalArgumentException("Unsupported SAO world data schema");
        long highest = tag.getLong("highest_unlocked");
        if (highest < 1 || !tag.contains("npc_unlocks", 1)) throw new IllegalArgumentException("Invalid SAO progression data");
        SaoWorldData data=new SaoWorldData(highest,tag.getBoolean("npc_unlocks"));
        if(schema>=2 && tag.hasUUID("active_guardian")) data.activeGuardian=tag.getUUID("active_guardian");
        return data;
    }

    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("schema", SCHEMA);
        tag.putLong("highest_unlocked", progress.highestUnlocked());
        tag.putBoolean("npc_unlocks", npcUnlocks);
        if(activeGuardian!=null) tag.putUUID("active_guardian",activeGuardian);
        else tag.remove("active_guardian");
        return tag;
    }

    public long highestUnlocked() { return progress.highestUnlocked(); }
    public boolean isUnlocked(long floor) { return progress.isUnlocked(floor); }

    /** For trusted server-side boss resolution only. No player command grants victories. */
    public boolean recordVictory(long floor, FloorProgress.Victor victor) {
        if (floor>=FloorLayout.MAX_FLOOR || !progress.recordVictory(floor, victor)) return false;
        activeGuardian=null;
        setDirty();
        return true;
    }
    public UUID activeGuardian(){return activeGuardian;}
    public boolean beginGuardian(long floor,UUID id){
        if(id==null||activeGuardian!=null||floor!=highestUnlocked()||floor>=FloorLayout.MAX_FLOOR)return false;
        activeGuardian=id;setDirty();return true;
    }
    public boolean clearGuardian(UUID id){
        if(id==null||!id.equals(activeGuardian))return false;
        activeGuardian=null;setDirty();return true;
    }
    public boolean completeGuardian(long floor,UUID id){
        if(id==null||!id.equals(activeGuardian))return false;
        return recordVictory(floor,FloorProgress.Victor.PLAYER);
    }
}
