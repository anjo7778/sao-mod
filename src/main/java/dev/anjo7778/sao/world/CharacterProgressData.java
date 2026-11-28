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
    private final Map<Owner,Set<Long>> discoveries=new HashMap<>();
    private record CraftWindow(long day,int operations){
        private CraftWindow{if(day<0||operations<1||operations>8)throw new IllegalArgumentException("Invalid craft window");}
    }
    private final Map<Owner,CraftWindow> craftWindows=new HashMap<>();
    private record WorkWindow(long day,int operations){
        private WorkWindow{if(day<0||operations<1||operations>32)throw new IllegalArgumentException("Invalid work window");}
    }
    private final Map<Owner,EnumMap<Mastery,WorkWindow>> workWindows=new HashMap<>();
    /** Capped, save-backed progression. Only called by authenticated completed actions. */
    public boolean profession(Owner owner,long day,Mastery mastery){
        if(owner.kind()!=Kind.PLAYER||day<0||(mastery!=Mastery.MINING&&mastery!=Mastery.METALLURGY))return false;
        var windows=workWindows.computeIfAbsent(owner,k->new EnumMap<>(Mastery.class));
        var before=windows.get(mastery);int limit=mastery==Mastery.MINING?32:16;
        if(before!=null&&(day<before.day()||day==before.day()&&before.operations()>=limit))return false;
        if(!awardNext(owner,Source.GATHER,mastery==Mastery.MINING?2:3,mastery,mastery==Mastery.MINING?4:5))return false;
        windows.put(mastery,new WorkWindow(day,before!=null&&before.day()==day?before.operations()+1:1));
        setDirty();return true;
    }
    public boolean awardNext(Owner owner,Source source,long xp,Mastery mastery,long masteryXp){
        long before=state(owner).watermarks().getOrDefault(source,0L);
        if(before==Long.MAX_VALUE)return false;
        try{return award(owner,source,before+1,xp,mastery,masteryXp);}
        catch(ArithmeticException exhausted){return false;} // explicit representational limit, never wrap or crash the world
    }
    /** Stable per-character, per-floor discovery IDs; 0 is the capital, 1–4 are regional sites. */
    public boolean discover(Owner owner,long floor,int site){
        if(!dev.anjo7778.sao.simulation.FloorLayout.supported(floor)||site<0||site>4)return false;
        long key=floor*5+site;
        var known=discoveries.get(owner);
        if(known!=null&&known.contains(key))return false;
        if(!awardNext(owner,Source.DISCOVERY,20,Mastery.EXPLORATION,12))return false;
        discoveries.computeIfAbsent(owner,k->new HashSet<>()).add(key);setDirty();return true;
    }
    /** Reward only authenticated completed meaningful crafting; eight operations per elapsed world day. */
    public boolean craft(Owner owner,long day,Mastery mastery){
        if(day<0||(mastery!=Mastery.BLACKSMITHING&&mastery!=Mastery.ARMORSMITHING)
            ||owner.kind()==Kind.NPC&&mastery!=Mastery.BLACKSMITHING)return false;
        var before=craftWindows.get(owner);
        if(before!=null&&(day<before.day()||day==before.day()&&before.operations()>=8))return false;
        if(!awardNext(owner,Source.CRAFT,8,mastery,10))return false;
        craftWindows.put(owner,new CraftWindow(day,before!=null&&before.day()==day?before.operations()+1:1));
        setDirty();return true;
    }
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
        root.putInt("schema",3);ListTag list=new ListTag();
        records.forEach((owner,state)->{
            var n=new CompoundTag();n.putString("kind",owner.kind().name());n.putUUID("id",owner.id());n.putLong("xp",state.xp());
            n.put("attributes",writeMap(state.attributes()));n.put("mastery",writeMap(state.masteryXp()));n.put("watermarks",writeMap(state.watermarks()));
            var known=discoveries.get(owner);
            if(known!=null)n.putLongArray("discoveries",known.stream().mapToLong(Long::longValue).sorted().toArray());
            var craft=craftWindows.get(owner);
            if(craft!=null){n.putLong("craftDay",craft.day());n.putInt("craftOperations",craft.operations());}
            var work=workWindows.get(owner);
            if(work!=null&&!work.isEmpty()){
                var w=new CompoundTag();work.forEach((m,window)->{
                    var record=new CompoundTag();record.putLong("day",window.day());record.putInt("operations",window.operations());w.put(m.name(),record);
                });n.put("workWindows",w);
            }
            list.add(n);
        });
        root.put("characters",list);return root;
    }
    public static CharacterProgressData load(CompoundTag root,HolderLookup.Provider registries){
        int schema=root.getInt("schema");
        if((schema!=1&&schema!=2&&schema!=3)||!root.contains("characters",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown character progression schema");
        var data=new CharacterProgressData();var list=root.getList("characters",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);var key=new Owner(Kind.valueOf(n.getString("kind")),n.getUUID("id"));
            var state=new State(n.getLong("xp"),readMap(n.getCompound("attributes"),Attribute.class),
                readMap(n.getCompound("mastery"),Mastery.class),readMap(n.getCompound("watermarks"),Source.class));
            if(data.records.put(key,state)!=null)throw new IllegalArgumentException("Duplicate character");
            if(schema>=2){
                if(n.contains("discoveries",Tag.TAG_LONG_ARRAY)){
                    var known=new HashSet<Long>();
                    for(long location:n.getLongArray("discoveries")){
                        if(location<5||!dev.anjo7778.sao.simulation.FloorLayout.supported(location/5)||!known.add(location))
                            throw new IllegalArgumentException("Invalid discovery record");
                    }
                    data.discoveries.put(key,known);
                }
                if(n.contains("craftDay",Tag.TAG_LONG)){
                    data.craftWindows.put(key,new CraftWindow(n.getLong("craftDay"),n.getInt("craftOperations")));
                }
            }
            if(schema>=3&&n.contains("workWindows",Tag.TAG_COMPOUND)){
                if(key.kind()!=Kind.PLAYER)throw new IllegalArgumentException("Invalid profession owner");
                var saved=n.getCompound("workWindows");var windows=new EnumMap<Mastery,WorkWindow>(Mastery.class);
                for(String name:saved.getAllKeys()){
                    var mastery=Mastery.valueOf(name);
                    if(mastery!=Mastery.MINING&&mastery!=Mastery.METALLURGY)throw new IllegalArgumentException("Invalid profession");
                    var record=saved.getCompound(name);var window=new WorkWindow(record.getLong("day"),record.getInt("operations"));
                    if(mastery==Mastery.METALLURGY&&window.operations()>16)throw new IllegalArgumentException("Invalid metallurgy window");
                    windows.put(mastery,window);
                }data.workWindows.put(key,windows);
            }
        }
        return data;
    }
}
