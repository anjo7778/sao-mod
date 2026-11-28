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
    private final Map<Long,Map<UUID,RegionalJourney.Journey>> regionalJourneys=new TreeMap<>();
    public RegionalJourney.Journey journey(Profile p){
        var r=regionalJourneys.get(p.currentFloor());
        return r==null?RegionalJourney.EMPTY:r.getOrDefault(p.id(),RegionalJourney.EMPTY);
    }
    public RegionalJourney.Journey journey(State s){return journey(s.profile());}
    public RegionalSettlements.Site localSite(Profile p){
        var home=p.regionalHome();return home!=null?home:RegionalJourney.site(p.currentFloor(),journey(p));
    }
    private void storeJourney(Profile p,RegionalJourney.Journey j){
        regionalJourneys.computeIfAbsent(p.currentFloor(),k->new LinkedHashMap<>()).put(p.id(),j);setDirty();
    }
    private long cursorFloor;
    private int cursorIndex;
    public static AdventureSimulationData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_adventures");
    }
    public Ledger ledger(long floor,UUID id){
        var r=floors.get(floor);return r==null?null:r.get(id);
    }
    /** Birth-floor keyed lifetime ledger. Current floor controls only journal scope. */
    public Ledger ledger(Profile profile){
        var l=ledger(profile.birthFloor(),profile.id());
        return l==null?null:l.onFloor(profile.currentFloor());
    }
    public Ledger ledger(State state){return state==null?null:ledger(state.profile());}
    public ScoutingTrip.Trip trip(State state){
        var l=ledger(state);return l==null?null:l.trip();
    }
    public ScoutingTrip.Trip trip(long floor,UUID id){
        var l=ledger(floor,id);return l==null?null:l.trip();
    }
    public static boolean activeTrip(ServerLevel level,long floor,UUID id){
        var t=get(level.getServer()).trip(NpcPopulationData.get(level.getServer()).state(floor,id));return t!=null&&t.active();
    }
    public static NpcDailyPlan.Plan plan(ServerLevel level,State state){
        if(!CityRules.hasCities(level))return NpcDailyPlan.at(state,level.getDayTime());
        var simulation=get(level.getServer());
        var ledger=state.profile().occupation()==Occupation.ADVENTURER?simulation.ledger(state):null;
        var base=CityVenueRoutine.plan(state,level.getDayTime(),ledger,CityRules.hasLandmarks(level));
        if(CityRules.hasRegionalSettlements(level)){
            var journey=simulation.journey(state);
            if(ledger!=null&&!ledger.trip().active()&&!journey.active()){
                var delivery=NpcResourceService.deliveryPlan(level,state);
                if(delivery!=null)return delivery;
            }
            if(journey.visiting())base=RegionalJourney.visitPlan(state.profile(),journey);
            return MarketService.plan(level,state,journey,base);
        }
        return base;
    }
    public boolean update(long floor,UUID id,long epoch,Mode mode,boolean offscreen){
        if(!FloorLayout.supported(floor)||id==null||epoch<0)throw new IllegalArgumentException("Invalid simulation target");
        var r=floors.computeIfAbsent(floor,k->new LinkedHashMap<>());
        Ledger old=r.get(id);
        Ledger next=old==null?AdventureProgress.start(epoch).onFloor(floor):AdventureProgress.advance(old,epoch,mode,offscreen);
        if(next==old)return false;
        r.put(id,next);setDirty();
        return old!=null;
    }
    /** One real accepted social encounter is the only route to exchanging firsthand reports. */
    public Optional<NpcSocial.Encounter> converse(NpcPopulationData population,long floor,UUID first,UUID second,long day){
        var a=ledger(population.state(floor,first));var b=ledger(population.state(floor,second));
        return converse(population,floor,first,second,day,Math.max(a==null?0:a.epoch(),b==null?0:b.epoch()));
    }
    public Optional<NpcSocial.Encounter> converse(NpcPopulationData population,long floor,UUID first,UUID second,long day,long epoch){
        var result=population.converse(floor,first,second,day);
        if(result.isEmpty()||!result.get().friendly())return result;
        var a=ledger(population.state(floor,first));var b=ledger(population.state(floor,second));
        if(a==null||b==null||a.trip().active()||b.trip().active())return result;
        var ja=a.journal().learn(b.journal());var jb=b.journal().learn(a.journal());
        var ga=AdventurerGoals.conversation(a.goal(),epoch);
        var gb=AdventurerGoals.conversation(b.goal(),epoch);
        if(ja.equals(a.journal())&&jb.equals(b.journal())&&ga.equals(a.goal())&&gb.equals(b.goal()))return result;
        var pa=population.state(floor,first).profile();var pb=population.state(floor,second).profile();
        floors.get(pa.birthFloor()).put(first,a.withJournal(ja).withGoal(ga));
        floors.get(pb.birthFloor()).put(second,b.withJournal(jb).withGoal(gb));setDirty();
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
        int limit=Math.min(roster.size(),cursorIndex+BUDGET),migrated=0;
        for(;cursorIndex<limit;cursorIndex++){
            State s=roster.get(cursorIndex);UUID id=s.profile().id();
            var previous=ledger(s);
            if(previous!=null&&previous.epoch()>=epoch)continue;
            var desired=plan(level,s);
            var actor=level.getEntity(id);
            // Visible actors earn only sampled activity at their actual destination, not abstract work.
            boolean offscreen=actor==null;
            Mode mode=offscreen?AdventureProgress.mode(desired.activity()):Mode.IDLE;
            if(actor instanceof CityNpc npc&&npc.isAlive()&&npc.originFloor()==cursorFloor){
                mode=npc.matchesPlan(desired)&&npc.atScheduledDestination()?AdventureProgress.mode(desired.activity()):Mode.TRAVEL;
            }
            if(previous==null){update(s.profile().birthFloor(),id,epoch,mode,offscreen);continue;}
            if(CityRules.hasRegionalSettlements(level)){
                var current=journey(s);
                // Retain real cargo, allow rest, and prevent migration/new abstract trips before delivery.
                if(!previous.trip().active()&&!current.active()&&NpcSupplyData.get(server).carrying(id)){
                    floors.get(s.profile().birthFloor()).put(id,AdventureProgress.advance(previous,epoch,offscreen?Mode.IDLE:mode,offscreen));
                    setDirty();continue;
                }
                if(!previous.trip().active()&&MarketService.transact(level,s,current,desired,epoch)){
                    floors.get(s.profile().birthFloor()).put(id,AdventureProgress.advance(previous,epoch,Mode.PREPARE,offscreen));
                    setDirty();continue;
                }
                // Regional legs and local scouting are mutually exclusive. A represented NPC
                // never departs via the abstract simulator or earns unobserved travel credit.
                var regional=RegionalJourney.advance(s,previous,current,epoch,level.getDayTime(),offscreen);
                if(current.active()||regional.began()){
                    var next=regional.journey();
                    if(!next.equals(current))storeJourney(s.profile(),next);
                    if(regional.began()){
                        var n=s.needs();
                        population.updateNeeds(cursorFloor,id,new AdventurerMind.Needs(n.health(),n.supplies()-RegionalJourney.supplyCost(cursorFloor),n.equipment(),n.partyReadiness(),n.danger()));
                    }
                    if(regional.arrived()){
                        population.discoverRegional(cursorFloor,next.target(),server.overworld().getSeed());
                        RpgEvents.npcDiscovery(server,s.profile(),next.target());
                    }
                    if(next.completed()>current.completed())population.updateNeeds(cursorFloor,id,AdventurerMarket.wear(s.needs(),2));
                    Mode regionalMode=offscreen&&(current.phase()==RegionalJourney.Phase.OUTBOUND||current.phase()==RegionalJourney.Phase.RETURNING||regional.began())?Mode.TRAVEL:Mode.IDLE;
                    floors.get(s.profile().birthFloor()).put(id,AdventureProgress.advance(previous,epoch,regionalMode,offscreen));setDirty();
                    continue;
                }
            }
            boolean reached=actor instanceof CityNpc npc&&npc.isAlive()
                &&npc.originFloor()==cursorFloor&&npc.matchesPlan(desired)&&npc.atScheduledDestination();
            if(previous.trip().active()){
                mode=previous.trip().phase()==ScoutingTrip.Phase.SURVEY&&(offscreen||reached)?Mode.SCOUT:Mode.TRAVEL;
            }
            if(!offscreen&&actor instanceof CityNpc npc&&CityRules.hasRegionalSettlements(level)
                &&(HuntService.holding(npc,level,s,previous.trip(),epoch)||NpcResourceService.holding(npc)))reached=false;
            // A mastery/contact objective is not randomly interrupted by a new
            // scouting expedition. Existing trips still finish and urgent needs retain priority.
            boolean deferScout=CityRules.hasLandmarks(level)&&!previous.trip().active()
                &&(previous.goal().kind()==AdventurerGoals.Kind.PRACTICE||previous.goal().kind()==AdventurerGoals.Kind.CONNECT);
            var transition=deferScout?new ScoutingTrip.Result(previous.trip(),false):
                ScoutingTrip.advance(s,previous,epoch,level.getDayTime(),offscreen,reached);
            Ledger next=AdventureProgress.advance(previous,epoch,mode,offscreen).withTrip(transition.trip());
            if(previous.trip().phase()==ScoutingTrip.Phase.SURVEY&&next.trip().phase()!=ScoutingTrip.Phase.SURVEY)
                population.expireHunt(cursorFloor,id,previous.trip().since());
            if(next.trip().completed()==previous.trip().completed()+1){
                boolean observed=next.trip().observed()==previous.trip().observed()+1;
                next=next.withJournal(previous.journal().complete(next.trip().gate(),observed));
            }
            if(CityRules.hasLandmarks(level))next=next.withGoal(AdventurerGoals.update(s,previous,next,epoch));
            floors.get(s.profile().birthFloor()).put(id,next);setDirty();
            if(actor instanceof CityNpc npc&&npc.isAlive()&&npc.originFloor()==cursorFloor&&npc.atScheduledDestination())
                npc.reviewMenu(NpcMenuReview.fromTransition(s,previous,next,desired,true));
            if(transition.began()){
                var n=s.needs();
                population.updateNeeds(cursorFloor,id,new AdventurerMind.Needs(n.health(),n.supplies()-ScoutingTrip.SUPPLY_COST,
                    n.equipment(),n.partyReadiness(),n.danger()));
            }else if(CityRules.hasRegionalSettlements(level)&&next.trip().completed()>previous.trip().completed()){
                int claimed=population.wallet(s.profile()).claimedThrough();
                population.settleScouting(cursorFloor,id,previous.trip().completed(),next.trip().completed());
                if(population.wallet(s.profile()).claimedThrough()>claimed)RpgEvents.npcScouting(server,s.profile());
            }else if(CityRules.hasLandmarks(level)&&offscreen&&!NpcSupplyData.get(server).carrying(id)&&FloorMigration.eligible(s,next,epoch,level.getDayTime())
                &&tryMigration(server,population,s,epoch)){
                migrated++;
            }else if(offscreen&&mode==Mode.SOCIALIZE&&!next.trip().active()){
                socialize(population,level,roster,s,desired,day,epoch);
            }
        }
        cursorIndex-=migrated;
        if(cursorIndex>=roster.size()-migrated){cursorFloor=population.nextDiscoveredFloor(cursorFloor);cursorIndex=0;}
    }
    private boolean tryMigration(MinecraftServer server,NpcPopulationData population,State s,long epoch){
        long from=s.profile().currentFloor(),to=from+1;
        var progress=SaoWorldData.get(server);
        if(!FloorLayout.supported(to)||!progress.isUnlocked(to)||population.roster(to).isEmpty())return false;
        var target=server.getLevel(FloorTravel.UPPER);
        if(target==null||!CityRules.hasLandmarks(target)||target.getEntity(s.profile().id())!=null)return false;
        // One location change, no duplicated ledger. No target chunk tickets.
        return population.relocate(from,to,s.profile().id(),epoch,progress.highestUnlocked());
    }
    private void socialize(NpcPopulationData population,ServerLevel level,List<State> roster,State a,NpcDailyPlan.Plan plan,long day,long epoch){
        if(roster.size()<2)return;
        int start=Math.floorMod((int)(epoch+a.profile().id().hashCode()),roster.size());
        // Up to four candidates, never a global pair search. Both must be unrepresented.
        for(int k=0;k<Math.min(4,roster.size());k++){
            UUID id=roster.get((start+k)%roster.size()).profile().id();
            if(a.profile().id().equals(id)||level.getEntity(id)!=null||journey(roster.get((start+k)%roster.size())).active())continue;
            State b=population.state(cursorFloor,id);
            if(b==null)continue;
            var other=CityVenueRoutine.plan(b,level.getDayTime(),ledger(b),CityRules.hasLandmarks(level));
            if(other.activity()!=NpcDailyPlan.Activity.SOCIALIZE)continue;
            if(Math.hypot(plan.destination().x()-other.destination().x(),plan.destination().z()-other.destination().z())>16)continue;
            if(converse(population,cursorFloor,a.profile().id(),id,day,epoch).isPresent())return;
        }
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",6);ListTag all=new ListTag();
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
                n.put("goal",g);
                n.putLong("journalFloor",l.journalFloor());n.putLong("priorJournalFloor",l.priorJournalFloor());
                var prior=l.priorJournal();n.putIntArray("priorJournal",new int[]{prior.surveyed(),prior.observed(),prior.reported()});npcs.add(n);
            });
            group.put("npcs",npcs);all.add(group);
        });
        root.put("capitals",all);
        ListTag journeys=new ListTag();
        regionalJourneys.forEach((floor,records)->{
            CompoundTag group=new CompoundTag();group.putLong("floor",floor);ListTag list=new ListTag();
            records.forEach((id,j)->{
                CompoundTag n=new CompoundTag();n.putUUID("id",id);n.putString("phase",j.phase().name());
                n.putString("target",j.target().name());n.putString("purpose",j.purpose().name());
                n.putLong("since",j.since());n.putLong("nextStart",j.nextStart());n.putInt("visited",j.visitedMask());
                list.add(n);
            });
            group.put("npcs",list);journeys.add(group);
        });
        root.put("regionalJourneys",journeys);return root;
    }
    public static AdventureSimulationData load(CompoundTag root,HolderLookup.Provider registries){
        int schema=root.getInt("schema");
        if((schema!=1&&schema!=2&&schema!=3&&schema!=4&&schema!=5&&schema!=6)||!root.contains("capitals",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown adventure ledger schema");
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
                if(schema>=5){
                    int[] prior=n.getIntArray("priorJournal");
                    if(prior.length!=3)throw new IllegalArgumentException("Invalid archived journal");
                    l=new Ledger(l.epoch(),l.practice(),l.fatigue(),l.preparations(),l.restSessions(),l.updates(),l.abstractUpdates(),l.last(),l.trip(),l.journal(),l.goal(),
                        n.getLong("journalFloor"),n.getLong("priorJournalFloor"),new SurveyJournal(prior[0],prior[1],prior[2]));
                }else l=l.onFloor(floor);
                if(records.put(n.getUUID("id"),l)!=null)throw new IllegalArgumentException("Duplicate adventurer ledger");
            }
            data.floors.put(floor,records);
        }
        if(schema>=6){
            var groups=root.getList("regionalJourneys",Tag.TAG_COMPOUND);
            for(int i=0;i<groups.size();i++){
                var g=groups.getCompound(i);long floor=g.getLong("floor");
                if(!FloorLayout.supported(floor)||data.regionalJourneys.containsKey(floor))throw new IllegalArgumentException("Invalid regional journey floor");
                var list=g.getList("npcs",Tag.TAG_COMPOUND);if(list.size()>512)throw new IllegalArgumentException("Invalid regional journey count");
                var records=new LinkedHashMap<UUID,RegionalJourney.Journey>();
                for(int k=0;k<list.size();k++){
                    var n=list.getCompound(k);
                    var j=new RegionalJourney.Journey(RegionalJourney.Phase.valueOf(n.getString("phase")),RegionalSettlements.Kind.valueOf(n.getString("target")),
                        RegionalJourney.Purpose.valueOf(n.getString("purpose")),n.getLong("since"),n.getLong("nextStart"),n.getInt("visited"));
                    if(records.put(n.getUUID("id"),j)!=null)throw new IllegalArgumentException("Duplicate regional traveler");
                }
                data.regionalJourneys.put(floor,records);
            }
        }
        return data;
    }
}
