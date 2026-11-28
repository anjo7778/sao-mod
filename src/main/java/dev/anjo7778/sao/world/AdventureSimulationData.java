package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import dev.anjo7778.sao.simulation.AdventureProgress.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/**
 * Budgeted simulation of visited-capital adventurers only. No chunk tickets, fake items, boss
 * wins, dungeon discoveries, or offline wall-clock progress. The durable ledger is authoritative.
 */
public final class AdventureSimulationData extends SavedData {
    public static final int BUDGET=8, PERIOD=20;
    private static final Factory<AdventureSimulationData> FACTORY=new Factory<>(AdventureSimulationData::new,AdventureSimulationData::load,null);
    private final Map<Long,Map<UUID,Ledger>> floors=new TreeMap<>();
    private long cursorFloor;
    private int cursorIndex;
    public static AdventureSimulationData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_adventures");
    }
    public Ledger ledger(long floor,UUID id){
        var r=floors.get(floor);return r==null?null:r.get(id);
    }
    public ScoutingTrip.Trip trip(long floor,UUID id){
        var l=ledger(floor,id);return l==null?null:l.trip();
    }
    public static boolean activeTrip(ServerLevel level,long floor,UUID id){
        var t=get(level.getServer()).trip(floor,id);return t!=null&&t.active();
    }
    public static NpcDailyPlan.Plan plan(ServerLevel level,State state){
        if(!CityRules.hasCities(level))return NpcDailyPlan.at(state,level.getDayTime());
        var ledger=state.profile().occupation()==Occupation.ADVENTURER?get(level.getServer()).ledger(state.profile().originFloor(),state.profile().id()):null;
        return CityVenueRoutine.plan(state,level.getDayTime(),ledger,CityRules.hasLandmarks(level));
    }
    public boolean update(long floor,UUID id,long epoch,Mode mode,boolean offscreen){
        if(!FloorLayout.supported(floor)||id==null||epoch<0)throw new IllegalArgumentException("Invalid simulation target");
        var r=floors.computeIfAbsent(floor,k->new LinkedHashMap<>());
        Ledger old=r.get(id);
        Ledger next=old==null?AdventureProgress.start(epoch):AdventureProgress.advance(old,epoch,mode,offscreen);
        if(next==old)return false;
        r.put(id,next);setDirty();
        return old!=null;
    }
    /** One real accepted social encounter is the only route to exchanging firsthand reports. */
    public Optional<NpcSocial.Encounter> converse(NpcPopulationData population,long floor,UUID first,UUID second,long day){
        var a=ledger(floor,first);var b=ledger(floor,second);
        return converse(population,floor,first,second,day,Math.max(a==null?0:a.epoch(),b==null?0:b.epoch()));
    }
    public Optional<NpcSocial.Encounter> converse(NpcPopulationData population,long floor,UUID first,UUID second,long day,long epoch){
        var result=population.converse(floor,first,second,day);
        if(result.isEmpty()||!result.get().friendly())return result;
        var a=ledger(floor,first);var b=ledger(floor,second);
        if(a==null||b==null||a.trip().active()||b.trip().active())return result;
        var ja=a.journal().learn(b.journal());var jb=b.journal().learn(a.journal());
        var ga=AdventurerGoals.conversation(a.goal(),epoch);
        var gb=AdventurerGoals.conversation(b.goal(),epoch);
        if(ja.equals(a.journal())&&jb.equals(b.journal())&&ga.equals(a.goal())&&gb.equals(b.goal()))return result;
        var roster=floors.get(floor);
        roster.put(first,a.withJournal(ja).withGoal(ga));roster.put(second,b.withJournal(jb).withGoal(gb));setDirty();
        return result;
    }
    public static void onTick(ServerTickEvent.Post event){
        var server=event.getServer();
        if(server.getTickCount()%PERIOD!=0||!CityRules.hasCities(server.overworld()))return;
        get(server).step(server);
    }
    private void step(MinecraftServer server){
        var population=NpcPopulationData.get(server);
        if(population.discoveredCapitals()==0)return;
        if(cursorFloor==0)cursorFloor=population.nextDiscoveredFloor(0);
        if(!SaoWorldData.get(server).isUnlocked(cursorFloor)){
            cursorFloor=population.nextDiscoveredFloor(cursorFloor);cursorIndex=0;return;
        }
        List<State> roster=population.roster(cursorFloor).stream()
            .filter(s->s.profile().occupation()==Occupation.ADVENTURER).toList();
        if(cursorIndex>=roster.size()){
            cursorFloor=population.nextDiscoveredFloor(cursorFloor);cursorIndex=0;return;
        }
        ServerLevel level=cursorFloor==1?server.overworld():server.getLevel(FloorTravel.UPPER);
        if(level==null){cursorFloor=population.nextDiscoveredFloor(cursorFloor);cursorIndex=0;return;}
        long epoch=Math.max(0,server.overworld().getGameTime()/AdventureProgress.EPOCH_TICKS);
        long day=Math.max(0,server.overworld().getGameTime()/24000);
        int limit=Math.min(roster.size(),cursorIndex+BUDGET);
        for(;cursorIndex<limit;cursorIndex++){
            State s=roster.get(cursorIndex);UUID id=s.profile().id();
            var previous=ledger(cursorFloor,id);
            if(previous!=null&&previous.epoch()>=epoch)continue;
            var desired=CityVenueRoutine.plan(s,level.getDayTime(),previous,CityRules.hasLandmarks(level));
            var actor=level.getEntity(id);
            // Visible actors earn only sampled activity at their actual destination, not abstract work.
            boolean offscreen=actor==null;
            Mode mode=offscreen?AdventureProgress.mode(desired.activity()):Mode.IDLE;
            if(actor instanceof CityNpc npc&&npc.isAlive()&&npc.originFloor()==cursorFloor){
                mode=npc.matchesPlan(desired)&&npc.atScheduledDestination()?AdventureProgress.mode(desired.activity()):Mode.TRAVEL;
            }
            if(previous==null){update(cursorFloor,id,epoch,mode,offscreen);continue;}
            boolean reached=actor instanceof CityNpc npc&&npc.isAlive()
                &&npc.originFloor()==cursorFloor&&npc.matchesPlan(desired)&&npc.atScheduledDestination();
            if(previous.trip().active()){
                mode=previous.trip().phase()==ScoutingTrip.Phase.SURVEY&&(offscreen||reached)?Mode.SCOUT:Mode.TRAVEL;
            }
            var transition=ScoutingTrip.advance(s,previous,epoch,level.getDayTime(),offscreen,reached);
            Ledger next=AdventureProgress.advance(previous,epoch,mode,offscreen).withTrip(transition.trip());
            if(next.trip().completed()==previous.trip().completed()+1){
                boolean observed=next.trip().observed()==previous.trip().observed()+1;
                next=next.withJournal(previous.journal().complete(next.trip().gate(),observed));
            }
            if(CityRules.hasLandmarks(level))next=next.withGoal(AdventurerGoals.update(s,previous,next,epoch));
            floors.get(cursorFloor).put(id,next);setDirty();
            if(transition.began()){
                var n=s.needs();
                population.updateNeeds(cursorFloor,id,new AdventurerMind.Needs(n.health(),n.supplies()-ScoutingTrip.SUPPLY_COST,
                    n.equipment(),n.partyReadiness(),n.danger()));
            }else if(offscreen&&mode==Mode.SOCIALIZE&&!next.trip().active()){
                socialize(population,level,roster,s,desired,day,epoch);
            }
        }
        if(cursorIndex>=roster.size()){cursorFloor=population.nextDiscoveredFloor(cursorFloor);cursorIndex=0;}
    }
    private void socialize(NpcPopulationData population,ServerLevel level,List<State> roster,State a,NpcDailyPlan.Plan plan,long day,long epoch){
        if(roster.size()<2)return;
        int start=Math.floorMod((int)(epoch+a.profile().id().hashCode()),roster.size());
        // Up to four candidates, never a global pair search. Both must be unrepresented.
        for(int k=0;k<Math.min(4,roster.size());k++){
            UUID id=roster.get((start+k)%roster.size()).profile().id();
            if(a.profile().id().equals(id)||level.getEntity(id)!=null)continue;
            State b=population.state(cursorFloor,id);
            if(b==null)continue;
            var other=CityVenueRoutine.plan(b,level.getDayTime(),ledger(cursorFloor,id),CityRules.hasLandmarks(level));
            if(other.activity()!=NpcDailyPlan.Activity.SOCIALIZE)continue;
            if(Math.hypot(plan.destination().x()-other.destination().x(),plan.destination().z()-other.destination().z())>16)continue;
            if(converse(population,cursorFloor,a.profile().id(),id,day,epoch).isPresent())return;
        }
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",4);ListTag all=new ListTag();
        floors.forEach((floor,records)->{
            CompoundTag group=new CompoundTag();group.putLong("floor",floor);ListTag npcs=new ListTag();
            records.forEach((id,l)->{
                CompoundTag n=new CompoundTag();n.putUUID("id",id);n.putLong("epoch",l.epoch());n.putIntArray("stats",
                    new int[]{l.practice(),l.fatigue(),l.preparations(),l.restSessions(),l.updates(),l.abstractUpdates()});
                n.putString("last",l.last().name());
                var trip=l.trip();CompoundTag q=new CompoundTag();
                q.putString("phase",trip.phase().name());q.putString("gate",trip.gate().name());
                q.putLong("since",trip.since());q.putLong("next",trip.nextStart());
                q.putIntArray("counts",new int[]{trip.completed(),trip.observed(),trip.aborted()});
                q.putBoolean("abstract",trip.abstractLeg());q.putBoolean("surveyed",trip.surveyed());
                n.put("trip",q);
                var journal=l.journal();n.putIntArray("journal",new int[]{journal.surveyed(),journal.observed(),journal.reported()});
                var goal=l.goal();var g=new CompoundTag();
                g.putString("kind",goal.kind().name());g.putInt("progress",goal.progress());g.putInt("target",goal.target());
                g.putLong("deadline",goal.deadline());g.putLong("next",goal.nextPick());g.putInt("completed",goal.completed());g.putInt("expired",goal.expired());
                n.put("goal",g);npcs.add(n);
            });
            group.put("npcs",npcs);all.add(group);
        });
        root.put("capitals",all);return root;
    }
    public static AdventureSimulationData load(CompoundTag root,HolderLookup.Provider registries){
        int schema=root.getInt("schema");
        if((schema!=1&&schema!=2&&schema!=3&&schema!=4)||!root.contains("capitals",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown adventure ledger schema");
        var data=new AdventureSimulationData();ListTag all=root.getList("capitals",Tag.TAG_COMPOUND);
        for(int i=0;i<all.size();i++){
            var group=all.getCompound(i);long floor=group.getLong("floor");
            if(!FloorLayout.supported(floor)||data.floors.containsKey(floor))throw new IllegalArgumentException("Invalid floor ledger");
            var records=new LinkedHashMap<UUID,Ledger>();var list=group.getList("npcs",Tag.TAG_COMPOUND);
            if(list.size()>512)throw new IllegalArgumentException("Invalid adventure population");
            for(int k=0;k<list.size();k++){
                var n=list.getCompound(k);int[] a=n.getIntArray("stats");
                if(a.length!=6)throw new IllegalArgumentException("Invalid progress vector");
                var l=new Ledger(n.getLong("epoch"),a[0],a[1],a[2],a[3],a[4],a[5],Mode.valueOf(n.getString("last")));
                if(schema>=2){
                    var q=n.getCompound("trip");int[] counts=q.getIntArray("counts");
                    if(counts.length!=3)throw new IllegalArgumentException("Invalid trip counts");
                    l=l.withTrip(new ScoutingTrip.Trip(ScoutingTrip.Phase.valueOf(q.getString("phase")),
                        ScoutingTrip.Gate.valueOf(q.getString("gate")),q.getLong("since"),q.getLong("next"),
                        counts[0],counts[1],counts[2],q.getBoolean("abstract"),q.getBoolean("surveyed")));
                }
                if(schema>=3){
                    int[] masks=n.getIntArray("journal");
                    if(masks.length!=3)throw new IllegalArgumentException("Invalid survey journal vector");
                    l=l.withJournal(new SurveyJournal(masks[0],masks[1],masks[2]));
                }
                if(schema>=4){
                    var g=n.getCompound("goal");
                    l=l.withGoal(new AdventurerGoals.Goal(AdventurerGoals.Kind.valueOf(g.getString("kind")),g.getInt("progress"),g.getInt("target"),g.getLong("deadline"),g.getLong("next"),g.getInt("completed"),g.getInt("expired")));
                }
                if(records.put(n.getUUID("id"),l)!=null)throw new IllegalArgumentException("Duplicate adventurer ledger");
            }
            data.floors.put(floor,records);
        }
        return data;
    }
}
