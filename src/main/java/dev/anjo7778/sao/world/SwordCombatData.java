package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.SwordActions;
import dev.anjo7778.sao.simulation.SwordActions.Skill;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Stamina, recovery, selected sword skill and cooldowns belong to durable identity,
 * not to a disposable NPC projection or a player entity instance. */
public final class SwordCombatData extends SavedData {
    public record Key(boolean npc,UUID id){public Key{Objects.requireNonNull(id);}}
    public record Entry(SwordActions.Saved clock,Skill selected){
        public Entry{Objects.requireNonNull(clock);Objects.requireNonNull(selected);}
    }
    private static final Factory<SwordCombatData> FACTORY=new Factory<>(SwordCombatData::new,SwordCombatData::load,null);
    private final Map<Key,Entry> states=new LinkedHashMap<>();
    public static SwordCombatData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_sword_combat");
    }
    public Entry entry(Key key){return states.get(key);}
    void store(Key key,Entry next){
        var previous=states.get(key);
        if(next.clock().rested()&&next.selected()==Skill.HORIZONTAL){
            if(states.remove(key)!=null)setDirty();return;
        }
        // No periodic dirty writes for a fully rested character with a non-default selection.
        if(previous!=null&&previous.clock().rested()&&next.clock().rested()&&previous.selected()==next.selected())return;
        if(!next.equals(previous)){states.put(key,next);setDirty();}
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",2);var list=new ListTag();
        states.forEach((key,entry)->{
            var n=new CompoundTag();n.putBoolean("npc",key.npc());n.putUUID("id",key.id());
            var s=entry.clock();n.putInt("stamina",s.stamina());n.putLong("tick",s.tick());
            n.putInt("recovery",s.recovery());n.putInt("global",s.globalCooldown());
            n.putIntArray("cooldowns",s.cooldowns().stream().mapToInt(Integer::intValue).toArray());
            n.putString("selected",entry.selected().name());list.add(n);
        });
        root.put("combatants",list);return root;
    }
    public static SwordCombatData load(CompoundTag root,HolderLookup.Provider registries){
        int schema=root.getInt("schema");
        if((schema!=1&&schema!=2)||!root.contains("combatants",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown sword combat schema");
        var d=new SwordCombatData();var list=root.getList("combatants",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);
            if(!n.hasUUID("id")||!n.contains("tick",Tag.TAG_LONG)||!n.contains("cooldowns",Tag.TAG_INT_ARRAY))
                throw new IllegalArgumentException("Invalid combat entry");
            var key=new Key(n.getBoolean("npc"),n.getUUID("id"));
            int[] timers=n.getIntArray("cooldowns");
            // v0.2.47 stored the first four skills in their stable enum order.
            if(schema==1){
                if(timers.length!=4)throw new IllegalArgumentException("Invalid legacy cooldown count");
                timers=Arrays.copyOf(timers,Skill.values().length);
            }else if(timers.length!=Skill.values().length)throw new IllegalArgumentException("Invalid cooldown count");
            var clock=new SwordActions.Saved(n.getInt("stamina"),n.getLong("tick"),n.getInt("recovery"),
                n.getInt("global"),Arrays.stream(timers).boxed().toList());
            var entry=new Entry(clock,Skill.valueOf(n.getString("selected")));
            if(d.states.put(key,entry)!=null)throw new IllegalArgumentException("Duplicate combat identity");
        }
        return d;
    }
}
