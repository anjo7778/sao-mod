package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import java.util.*;

/** A tiny local nursery bank containing only saplings obtained from actual leaf loot. */
public final class NpcWoodlandData extends SavedData {
    private record Site(long floor,BlockPos root){}
    private final Map<Site,Integer> seeds=new HashMap<>();
    public static final int CAPACITY=8;
    private static final Factory<NpcWoodlandData> FACTORY=new Factory<>(NpcWoodlandData::new,NpcWoodlandData::load,null);
    public static NpcWoodlandData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_woodland");
    }
    private static boolean valid(long floor,BlockPos root){
        return root.getY()>=CityPlan.GROUND-18&&root.getY()<=CityPlan.GROUND+18
            &&GateWoodlandPlan.isSite(floor,root.getX(),root.getZ());
    }
    public int seeds(long floor,BlockPos root){return seeds.getOrDefault(new Site(floor,root),0);}
    int store(long floor,BlockPos root,int amount){
        if(!valid(floor,root)||amount<=0)return 0;
        var key=new Site(floor,root);int before=seeds(floor,root),accepted=Math.min(amount,CAPACITY-before);
        if(accepted>0){seeds.put(key,before+accepted);setDirty();}
        return accepted;
    }
    boolean use(long floor,BlockPos root){
        var key=new Site(floor,root);int before=seeds(floor,root);
        if(before<=0)return false;
        if(before==1)seeds.remove(key);else seeds.put(key,before-1);
        setDirty();return true;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",1);var entries=new ListTag();
        seeds.forEach((site,count)->{
            var n=new CompoundTag();n.putLong("floor",site.floor());n.putLong("root",site.root().asLong());
            n.putInt("seeds",count);entries.add(n);
        });
        root.put("plots",entries);return root;
    }
    public static NpcWoodlandData load(CompoundTag root,HolderLookup.Provider registries){
        if(root.getInt("schema")!=1||!root.contains("plots",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown woodland schema");
        var data=new NpcWoodlandData();var list=root.getList("plots",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++){
            var n=list.getCompound(i);long floor=n.getLong("floor");var pos=BlockPos.of(n.getLong("root"));int count=n.getInt("seeds");
            if(!valid(floor,pos)||count<=0||count>CAPACITY||data.seeds.put(new Site(floor,pos),count)!=null)
                throw new IllegalArgumentException("Invalid woodland reserve");
        }
        return data;
    }
}
