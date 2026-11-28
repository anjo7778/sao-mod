package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.simulation.GateProspects.Resource;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** Cargo is persistent actual harvested material, keyed to durable NPC identity. */
public final class NpcSupplyData extends SavedData {
    public static final int CAPACITY=12, STARTING_TOOL_USES=250, REPLACE_AT=20;
    public record Cargo(long floor,UUID smith,int iron,int coal,int logs,int pickUses,int axeUses,long delivered){
        public Cargo{
            if(!FloorLayout.supported(floor)||smith==null||iron<0||coal<0||logs<0
                ||(long)iron+coal+logs>CAPACITY||pickUses<0||pickUses>STARTING_TOOL_USES
                ||axeUses<0||axeUses>STARTING_TOOL_USES||delivered<0)
                throw new IllegalArgumentException("Invalid supply cargo");
        }
        public int size(){return iron+coal+logs;}
        public boolean canGather(Resource r,int n){
            return n>0&&n<=CAPACITY-size()&&(r==Resource.DEADWOOD?axeUses>0:pickUses>0);
        }
        public Cargo gather(Resource r,int n){
            if(!canGather(r,n))throw new IllegalArgumentException("No cargo space or tool");
            return new Cargo(floor,smith,iron+(r==Resource.IRON?n:0),coal+(r==Resource.COAL?n:0),
                logs+(r==Resource.DEADWOOD?n:0),pickUses-(r==Resource.DEADWOOD?0:1),axeUses-(r==Resource.DEADWOOD?1:0),delivered);
        }
        public Cargo emptied(){
            if(size()==0||delivered==Long.MAX_VALUE)throw new IllegalStateException("No delivery");
            return new Cargo(floor,smith,0,0,0,pickUses,axeUses,delivered+1);
        }
        public Cargo replacement(Resource r,int remaining){
            if(size()!=0||remaining<=0||remaining>STARTING_TOOL_USES
                ||(r==Resource.DEADWOOD?axeUses:pickUses)>REPLACE_AT)throw new IllegalArgumentException("Tool replacement not needed");
            return new Cargo(floor,smith,0,0,0,r==Resource.DEADWOOD?pickUses:remaining,r==Resource.DEADWOOD?remaining:axeUses,delivered);
        }
        public Cargo reassigned(long nextFloor,UUID nextSmith){
            if(size()!=0)throw new IllegalStateException("Undelivered cargo");
            return new Cargo(nextFloor,nextSmith,0,0,0,pickUses,axeUses,delivered);
        }
    }
    private static final Factory<NpcSupplyData> FACTORY=new Factory<>(NpcSupplyData::new,NpcSupplyData::load,null);
    private final Map<UUID,Cargo> cargo=new LinkedHashMap<>();
    public static NpcSupplyData get(MinecraftServer server){return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_npc_supply");}
    public Cargo cargo(UUID id){return cargo.get(id);}
    public boolean needsTool(UUID smith,boolean axe){
        return cargo.values().stream().anyMatch(c->c.smith().equals(smith)&&(axe?c.axeUses():c.pickUses())<=REPLACE_AT);
    }
    public boolean carrying(UUID id){var c=cargo(id);return c!=null&&c.size()>0;}
    public Cargo candidate(UUID id,long floor,UUID smith){
        var before=cargo(id);
        if(before==null)return new Cargo(floor,smith,0,0,0,STARTING_TOOL_USES,STARTING_TOOL_USES,0);
        if(before.size()!=0)return before.floor()==floor?before:null;
        return before.reassigned(floor,smith);
    }
    void store(UUID id,Cargo value){cargo.put(id,value);setDirty();}
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",1);var list=new ListTag();
        cargo.forEach((id,c)->{
            var n=new CompoundTag();n.putUUID("id",id);n.putUUID("smith",c.smith());n.putLong("floor",c.floor());
            n.putIntArray("materials",new int[]{c.iron(),c.coal(),c.logs()});
            n.putIntArray("tools",new int[]{c.pickUses(),c.axeUses()});n.putLong("delivered",c.delivered());list.add(n);
        });root.put("cargo",list);return root;
    }
    public static NpcSupplyData load(CompoundTag root,HolderLookup.Provider registries){
        if(root.getInt("schema")!=1||!root.contains("cargo",Tag.TAG_LIST))throw new IllegalArgumentException("Invalid supply save");
        var data=new NpcSupplyData();
        var list=root.getList("cargo",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);int[] m=n.getIntArray("materials"),t=n.getIntArray("tools");
            if(!n.hasUUID("id")||!n.hasUUID("smith")||m.length!=3||t.length!=2)throw new IllegalArgumentException("Invalid supply entry");
            var c=new Cargo(n.getLong("floor"),n.getUUID("smith"),m[0],m[1],m[2],t[0],t[1],n.getLong("delivered"));
            if(data.cargo.put(n.getUUID("id"),c)!=null)throw new IllegalArgumentException("Duplicate cargo");
        }
        return data;
    }
}
