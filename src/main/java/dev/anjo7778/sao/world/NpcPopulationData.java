package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.AdventurerMind.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Persistent authoritative profiles of visited capitals; nearby entities are temporary projections. */
public final class NpcPopulationData extends SavedData {
    private static final Factory<NpcPopulationData> FACTORY=new Factory<>(NpcPopulationData::new,NpcPopulationData::load,null);
    private final Map<Long,LinkedHashMap<UUID,State>> floors=new TreeMap<>();
    public static NpcPopulationData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_population");
    }
    public static void onPlayerTick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%100!=0)return;
        var level=p.serverLevel();
        if(!CityRules.isCity(level,p.getBlockX(),p.getBlockZ()))return;
        long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
        var server=level.getServer();
        if(FloorLayout.supported(floor)&&SaoWorldData.get(server).isUnlocked(floor))
            get(server).discover(floor,server.overworld().getSeed());
    }
    public boolean discover(long floor,long seed){
        if(floors.containsKey(floor))return false;
        LinkedHashMap<UUID,State> roster=new LinkedHashMap<>();
        for(State s:NpcRoster.generate(seed,floor))
            if(roster.put(s.profile().id(),s)!=null)throw new IllegalStateException("Duplicate identity");
        floors.put(floor,roster);setDirty();return true;
    }
    public List<State> roster(long floor){
        var r=floors.get(floor);
        return r==null?List.of():List.copyOf(r.values());
    }
    public State state(long floor,UUID id){var r=floors.get(floor);return r==null?null:r.get(id);}
    public int discoveredCapitals(){return floors.size();}
    public boolean remember(long floor,UUID npc,Memory event){
        var r=floors.get(floor);if(r==null||!r.containsKey(npc))return false;
        var before=r.get(npc);var after=before.remember(event);
        if(after==before)return false;
        r.put(npc,after);setDirty();return true;
    }
    public boolean updateNeeds(long floor,UUID npc,Needs needs){
        var r=floors.get(floor);if(r==null||!r.containsKey(npc))return false;
        var before=r.get(npc);
        if(before.needs().equals(needs))return false;
        r.put(npc,before.withNeeds(needs));setDirty();return true;
    }

    public static NpcPopulationData load(CompoundTag root,HolderLookup.Provider registries){
        if(root.getInt("schema")!=1||!root.contains("capitals",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown population schema");
        var d=new NpcPopulationData();var all=root.getList("capitals",Tag.TAG_COMPOUND);
        for(int i=0;i<all.size();i++){
            var entry=all.getCompound(i);long floor=entry.getLong("floor");
            if(!FloorLayout.supported(floor)||d.floors.containsKey(floor))throw new IllegalArgumentException("Invalid capital record");
            var list=entry.getList("npcs",Tag.TAG_COMPOUND);
            if(list.isEmpty()||list.size()>512)throw new IllegalArgumentException("Invalid capital population");
            var roster=new LinkedHashMap<UUID,State>();
            for(int k=0;k<list.size();k++){
                var s=readState(list.getCompound(k),floor);
                if(roster.put(s.profile().id(),s)!=null)throw new IllegalArgumentException("Duplicate identity");
            }
            d.floors.put(floor,roster);
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",1);ListTag capitals=new ListTag();
        floors.forEach((floor,roster)->{
            CompoundTag entry=new CompoundTag();entry.putLong("floor",floor);
            ListTag npcs=new ListTag();roster.values().forEach(s->npcs.add(writeState(s)));
            entry.put("npcs",npcs);capitals.add(entry);
        });
        root.put("capitals",capitals);return root;
    }
    private static CompoundTag writeState(State s){
        var p=s.profile();var n=s.needs();var t=p.personality();
        CompoundTag b=new CompoundTag();b.putUUID("id",p.id());b.putString("name",p.name());
        b.putString("job",p.occupation().name());b.putString("class",p.combatClass().name());b.putString("ambition",p.ambition().name());
        b.putIntArray("destinations",new int[]{p.homeX(),p.homeZ(),p.workX(),p.workZ()});
        b.putIntArray("traits",new int[]{t.courage(),t.loyalty(),t.sociability(),t.ambition()});
        b.putIntArray("needs",new int[]{n.health(),n.supplies(),n.equipment(),n.partyReadiness(),n.danger()});
        ListTag memories=new ListTag();
        for(var m:s.memories()){
            CompoundTag q=new CompoundTag();q.putUUID("event",m.eventId());q.putUUID("actor",m.actor());q.putLong("day",m.day());q.putString("type",m.type().name());memories.add(q);
        }
        b.put("memories",memories);return b;
    }
    private static State readState(CompoundTag b,long floor){
        int[] pos=b.getIntArray("destinations"),t=b.getIntArray("traits"),n=b.getIntArray("needs");
        if(pos.length!=4||t.length!=4||n.length!=5)throw new IllegalArgumentException("Invalid NPC vectors");
        var p=new Profile(b.getUUID("id"),b.getString("name"),floor,Occupation.valueOf(b.getString("job")),
            CombatClass.valueOf(b.getString("class")),Ambition.valueOf(b.getString("ambition")),pos[0],pos[1],pos[2],pos[3],new Personality(t[0],t[1],t[2],t[3]));
        List<Memory> mem=new ArrayList<>();
        var a=b.getList("memories",Tag.TAG_COMPOUND);
        if(a.size()>128)throw new IllegalArgumentException("Too many memories");
        for(int i=0;i<a.size();i++){
            var q=a.getCompound(i);mem.add(new Memory(q.getUUID("event"),q.getUUID("actor"),q.getLong("day"),EventType.valueOf(q.getString("type"))));
        }
        return new State(p,new Needs(n[0],n[1],n[2],n[3],n[4]),mem);
    }
}
