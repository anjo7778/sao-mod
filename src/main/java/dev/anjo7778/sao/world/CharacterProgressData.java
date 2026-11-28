package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.CharacterProgress;
import dev.anjo7778.sao.simulation.CharacterProgress.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Identity-keyed shared progression; lives in overworld storage and survives floor changes and respawn. */
public final class CharacterProgressData extends SavedData {
    public enum Kind { PLAYER, NPC }
    public record Owner(Kind kind,UUID id){public Owner{Objects.requireNonNull(kind);Objects.requireNonNull(id);}}
    private static final Factory<CharacterProgressData> FACTORY=new Factory<>(CharacterProgressData::new,CharacterProgressData::load,null);
    private final Map<Owner,State> records=new LinkedHashMap<>();
    public static CharacterProgressData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_characters");}
    public State state(Owner owner){return records.getOrDefault(owner,CharacterProgress.EMPTY);}
    public boolean award(Owner owner,Source source,long sequence,long xp,Mastery mastery,long masteryXp){
        var before=state(owner);var after=CharacterProgress.award(before,source,sequence,xp,mastery,masteryXp);
        if(after==before)return false;
        records.put(owner,after);setDirty();return true;
    }
    public boolean allocate(Owner owner,Attribute attribute,long amount){
        var before=state(owner);var after=CharacterProgress.allocate(before,attribute,amount);
        if(after==before)return false;
        records.put(owner,after);setDirty();return true;
    }
    private static <E extends Enum<E>> CompoundTag writeMap(Map<E,Long> map){
        var tag=new CompoundTag();map.forEach((k,v)->tag.putLong(k.name(),v));return tag;
    }
    private static <E extends Enum<E>> Map<E,Long> readMap(CompoundTag tag,Class<E> type){
        var map=new EnumMap<E,Long>(type);
        for(String k:tag.getAllKeys()){
            if(!tag.contains(k,Tag.TAG_LONG))throw new IllegalArgumentException("Invalid progression value");
            map.put(Enum.valueOf(type,k),tag.getLong(k));
        }
        return map;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",1);ListTag list=new ListTag();
        records.forEach((owner,state)->{
            var n=new CompoundTag();n.putString("kind",owner.kind().name());n.putUUID("id",owner.id());n.putLong("xp",state.xp());
            n.put("attributes",writeMap(state.attributes()));n.put("mastery",writeMap(state.masteryXp()));n.put("watermarks",writeMap(state.watermarks()));list.add(n);
        });
        root.put("characters",list);return root;
    }
    public static CharacterProgressData load(CompoundTag root,HolderLookup.Provider registries){
        if(root.getInt("schema")!=1||!root.contains("characters",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown character progression schema");
        var data=new CharacterProgressData();var list=root.getList("characters",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);var key=new Owner(Kind.valueOf(n.getString("kind")),n.getUUID("id"));
            var state=new State(n.getLong("xp"),readMap(n.getCompound("attributes"),Attribute.class),
                readMap(n.getCompound("mastery"),Mastery.class),readMap(n.getCompound("watermarks"),Source.class));
            if(data.records.put(key,state)!=null)throw new IllegalArgumentException("Duplicate character");
        }
        return data;
    }
}
