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
    private final NavigableMap<Long,LinkedHashMap<UUID,State>> floors=new TreeMap<>();
    private record Region(long floor,RegionalSettlements.Kind kind){}
    private final Map<Region,LinkedHashMap<UUID,State>> regions=new LinkedHashMap<>();
    private final Map<UUID,AdventurerMarket.Wallet> wallets=new HashMap<>();
    private final Map<UUID,AdventurerMarket.Shop> shops=new HashMap<>();
    private final Map<Long,AdventurerMarket.Treasury> treasuries=new HashMap<>();
    public AdventurerMarket.Treasury treasury(long floor){return treasuries.getOrDefault(floor,AdventurerMarket.NEW_TREASURY);}
    /** Settlement of a newly completed authoritative scouting trip, not an arbitrary quest reward. */
    public int settleScouting(long floor,UUID id,int before,int after){
        var capital=floors.get(floor);if(capital==null)return 0;
        var state=capital.get(id);
        if(state==null||state.profile().occupation()!=Occupation.ADVENTURER)return 0;
        var c=AdventurerMarket.commission(wallet(state.profile()),treasury(floor),before,after);
        if(c==null)return 0;
        wallets.put(id,c.wallet());treasuries.put(floor,c.treasury());
        capital.put(id,state.withNeeds(AdventurerMarket.wear(state.needs(),4)));
        setDirty();return c.paid();
    }
    public AdventurerMarket.Wallet wallet(Profile p){
        if(p.occupation()!=Occupation.ADVENTURER)throw new IllegalArgumentException("Not an adventurer");
        return wallets.getOrDefault(p.id(),AdventurerMarket.NEW_WALLET);
    }
    public AdventurerMarket.Shop shop(Profile p){
        var service=AdventurerMarket.forRole(p.occupation());
        if(service==null)throw new IllegalArgumentException("Not a shopkeeper");
        return shops.getOrDefault(p.id(),new AdventurerMarket.Shop(service,0));
    }
    /** Server-thread atomic update; MarketService establishes physical presence or an abstract local visit. */
    public boolean purchase(long floor,UUID buyer,UUID seller,RegionalSettlements.Kind region,NpcDailyPlan.Point destination,long time,long epoch,boolean observed){
        var capital=floors.get(floor);
        var local=region==null?capital:regions.get(new Region(floor,region));
        if(capital==null||local==null||epoch<0)return false;
        var b=capital.get(buyer);var s=local.get(seller);
        if(b==null||s==null||b.profile().occupation()!=Occupation.ADVENTURER||buyer.equals(seller)
            ||NpcRoutine.resting(b.profile(),time)||AdventurerMarket.forRole(s.profile().occupation())==null)return false;
        var sellerPlan=NpcDailyPlan.at(s,time);
        if(sellerPlan.activity()!=NpcDailyPlan.Activity.WORK||!sellerPlan.destination().equals(destination))return false;
        var w=wallet(b.profile());var stock=shop(s.profile());var treasury=treasury(floor);
        if(epoch<=w.lastEpoch()||!AdventurerMarket.canOffer(b.needs(),w,stock,treasury))return false;
        AdventurerMarket.Restock restock=stock.remaining()==0?AdventurerMarket.restock(stock,treasury):null;
        if(restock!=null)stock=restock.shop();
        var sale=AdventurerMarket.buy(b.needs(),w,stock,epoch,observed);
        if(sale==null)return false;
        capital.put(buyer,b.withNeeds(sale.needs()));
        wallets.put(buyer,sale.wallet());shops.put(seller,sale.shop());
        if(restock!=null)treasuries.put(floor,restock.treasury());
        setDirty();return true;
    }
    public boolean discoverRegional(long floor,RegionalSettlements.Kind kind,long seed){
        var key=new Region(floor,kind);
        if(regions.containsKey(key))return false;
        var roster=new LinkedHashMap<UUID,State>();
        for(var s:NpcRoster.generateRegional(seed,floor,kind))roster.put(s.profile().id(),s);
        regions.put(key,roster);setDirty();return true;
    }
    public List<State> regionalRoster(long floor,RegionalSettlements.Kind kind){
        var r=regions.get(new Region(floor,kind));return r==null?List.of():List.copyOf(r.values());
    }
    private LinkedHashMap<UUID,State> rosterContaining(long floor,UUID id){
        var capital=floors.get(floor);
        if(capital!=null&&capital.containsKey(id))return capital;
        for(var kind:RegionalSettlements.Kind.values()){
            var r=regions.get(new Region(floor,kind));if(r!=null&&r.containsKey(id))return r;
        }
        return null;
    }
    public static NpcPopulationData get(MinecraftServer server){
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY,"sao_population");
    }
    public static void onPlayerTick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.tickCount%100!=0)return;
        var level=p.serverLevel();
        if(p.isSpectator()||!p.isAlive()||!CityRules.hasCities(level))return;
        long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
        var server=level.getServer();
        if(!FloorLayout.supported(floor)||!SaoWorldData.get(server).isUnlocked(floor))return;
        if(CityRules.isCity(level,p.getBlockX(),p.getBlockZ()))get(server).discover(floor,server.overworld().getSeed());
        else if(CityRules.hasRegionalSettlements(level)){
            var c=FloorLayout.center(floor);
            var site=RegionalSettlements.at(floor,p.getBlockX()-c.x(),p.getBlockZ()-c.z());
            if(site!=null)get(server).discoverRegional(floor,site.kind(),server.overworld().getSeed());
        }
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
    public State state(long floor,UUID id){var r=rosterContaining(floor,id);return r==null?null:r.get(id);}
    public long nextDiscoveredFloor(long after){var next=floors.higherKey(after);return next!=null?next:floors.isEmpty()?0:floors.firstKey();}
    public int discoveredCapitals(){return floors.size();}
    /** Location is authoritative here. The lifetime ledger stays under birthFloor. */
    public boolean relocate(long from,long to,UUID id,long epoch,long highestUnlocked){
        if(to!=from+1||to>highestUnlocked||epoch<=0)return false;
        var a=floors.get(from);var b=floors.get(to);
        if(a==null||b==null||b.containsKey(id))return false;
        var s=a.get(id);
        if(s==null||s.profile().occupation()!=Occupation.ADVENTURER||s.profile().birthFloor()!=from||s.profile().relocatedAt()!=0)return false;
        long source=a.values().stream().filter(v->v.profile().occupation()==Occupation.ADVENTURER).count();
        long dest=b.values().stream().filter(v->v.profile().occupation()==Occupation.ADVENTURER).count();
        if(source<=FloorMigration.MIN_SOURCE_ADVENTURERS||dest>=FloorMigration.MAX_DEST_ADVENTURERS||b.size()>=512)return false;
        State moved=FloorMigration.relocate(s,to,epoch);
        a.remove(id);b.put(id,moved);setDirty();return true;
    }
    public boolean remember(long floor,UUID npc,Memory event){
        var r=rosterContaining(floor,npc);if(r==null)return false;
        var before=r.get(npc);var after=before.remember(event);
        if(after==before)return false;
        r.put(npc,after);setDirty();return true;
    }
    /** Commit both perspectives together on the server thread. No one-sided social event. */
    public Optional<NpcSocial.Encounter> converse(long floor,UUID first,UUID second,long day){
        var r=rosterContaining(floor,first);
        if(r==null||!r.containsKey(second))return Optional.empty();
        var a=r.get(first);var b=r.get(second);
        var result=NpcSocial.offer(a,b,day);
        if(result.isEmpty())return result;
        var e=result.get();
        State na=a.remember(e.first()),nb=b.remember(e.second());
        if(na==a||nb==b)return Optional.empty();
        r.put(first,na);r.put(second,nb);setDirty();
        return result;
    }
    public boolean updateNeeds(long floor,UUID npc,Needs needs){
        var r=rosterContaining(floor,npc);if(r==null)return false;
        var before=r.get(npc);
        if(before.needs().equals(needs))return false;
        r.put(npc,before.withNeeds(needs));setDirty();return true;
    }

    public static NpcPopulationData load(CompoundTag root,HolderLookup.Provider registries){
        int schema=root.getInt("schema");
        if((schema!=1&&schema!=2&&schema!=3&&schema!=4&&schema!=5)||!root.contains("capitals",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown population schema");
        var d=new NpcPopulationData();var all=root.getList("capitals",Tag.TAG_COMPOUND);var identities=new HashSet<UUID>();
        for(int i=0;i<all.size();i++){
            var entry=all.getCompound(i);long floor=entry.getLong("floor");
            if(!FloorLayout.supported(floor)||d.floors.containsKey(floor))throw new IllegalArgumentException("Invalid capital record");
            var list=entry.getList("npcs",Tag.TAG_COMPOUND);
            if(list.isEmpty()||list.size()>512)throw new IllegalArgumentException("Invalid capital population");
            var roster=new LinkedHashMap<UUID,State>();
            for(int k=0;k<list.size();k++){
                var s=readState(list.getCompound(k),floor,schema);
                if(s.profile().regionalHome()!=null||!identities.add(s.profile().id())||roster.put(s.profile().id(),s)!=null)throw new IllegalArgumentException("Invalid capital identity");
                if(schema>=4)d.readEconomy(list.getCompound(k),s.profile());
            }
            d.floors.put(floor,roster);
            if(schema>=5&&entry.contains("treasury",Tag.TAG_COMPOUND)){
                var t=entry.getCompound("treasury");d.treasuries.put(floor,new AdventurerMarket.Treasury(t.getInt("supplies"),t.getInt("repairs"),t.getInt("commissions")));
            }
        }
        if(schema>=3){
            var regional=root.getList("regions",Tag.TAG_COMPOUND);
            for(int i=0;i<regional.size();i++){
                var entry=regional.getCompound(i);long floor=entry.getLong("floor");
                var kind=RegionalSettlements.Kind.valueOf(entry.getString("kind"));
                var key=new Region(floor,kind);
                if(!FloorLayout.supported(floor)||d.regions.containsKey(key))throw new IllegalArgumentException("Invalid regional record");
                var list=entry.getList("npcs",Tag.TAG_COMPOUND);
                var site=RegionalSettlements.site(floor,kind);
                if(list.size()!=RegionalSettlements.buildings(site).size())throw new IllegalArgumentException("Invalid regional population");
                var roster=new LinkedHashMap<UUID,State>();
                var homes=new HashSet<List<Integer>>();
                for(int k=0;k<list.size();k++){
                    var s=readState(list.getCompound(k),floor,schema);var p=s.profile();
                    if(!site.equals(p.regionalHome())||!homes.add(List.of(p.homeX(),p.homeZ()))||!identities.add(p.id())||roster.put(p.id(),s)!=null)
                        throw new IllegalArgumentException("Invalid regional identity");
                    if(schema>=4)d.readEconomy(list.getCompound(k),p);
                }
                d.regions.put(key,roster);
            }
        }
        return d;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",5);ListTag capitals=new ListTag();
        floors.forEach((floor,roster)->{
            CompoundTag entry=new CompoundTag();entry.putLong("floor",floor);
            ListTag npcs=new ListTag();roster.values().forEach(s->npcs.add(writeState(s)));
            entry.put("npcs",npcs);
            var treasury=treasuries.get(floor);
            if(treasury!=null){
                var t=new CompoundTag();t.putInt("supplies",treasury.supplyReleased());t.putInt("repairs",treasury.repairReleased());
                t.putInt("commissions",treasury.commissions());entry.put("treasury",t);
            }
            capitals.add(entry);
        });
        root.put("capitals",capitals);
        ListTag regional=new ListTag();
        regions.forEach((key,roster)->{
            CompoundTag entry=new CompoundTag();entry.putLong("floor",key.floor());entry.putString("kind",key.kind().name());
            ListTag npcs=new ListTag();roster.values().forEach(s->npcs.add(writeState(s)));
            entry.put("npcs",npcs);regional.add(entry);
        });
        root.put("regions",regional);return root;
    }
    private CompoundTag writeState(State s){
        var p=s.profile();var n=s.needs();var t=p.personality();
        CompoundTag b=new CompoundTag();b.putUUID("id",p.id());b.putString("name",p.name());
        b.putLong("birthFloor",p.birthFloor());b.putLong("relocatedAt",p.relocatedAt());
        b.putString("job",p.occupation().name());b.putString("class",p.combatClass().name());b.putString("ambition",p.ambition().name());
        b.putIntArray("destinations",new int[]{p.homeX(),p.homeZ(),p.workX(),p.workZ()});
        b.putIntArray("traits",new int[]{t.courage(),t.loyalty(),t.sociability(),t.ambition()});
        b.putIntArray("needs",new int[]{n.health(),n.supplies(),n.equipment(),n.partyReadiness(),n.danger()});
        ListTag memories=new ListTag();
        for(var m:s.memories()){
            CompoundTag q=new CompoundTag();q.putUUID("event",m.eventId());q.putUUID("actor",m.actor());q.putLong("day",m.day());q.putString("type",m.type().name());memories.add(q);
        }
        b.put("memories",memories);
        var wallet=wallets.get(p.id());
        if(wallet!=null){
            var w=new CompoundTag();w.putInt("supply",wallet.supplyOrders());w.putInt("repair",wallet.repairOrders());
            w.putLong("lastEpoch",wallet.lastEpoch());w.putInt("observed",wallet.observedOrders());w.putInt("earned",wallet.earned());w.putInt("claimed",wallet.claimedThrough());b.put("wallet",w);
        }
        var shop=shops.get(p.id());if(shop!=null){b.putInt("sold",shop.sold());b.putInt("restocked",shop.restocked());}
        return b;
    }
    private void readEconomy(CompoundTag b,Profile p){
        if(b.contains("wallet",Tag.TAG_COMPOUND)){
            if(p.occupation()!=Occupation.ADVENTURER)throw new IllegalArgumentException("Wallet role mismatch");
            var w=b.getCompound("wallet");
            wallets.put(p.id(),new AdventurerMarket.Wallet(w.getInt("supply"),w.getInt("repair"),w.getLong("lastEpoch"),w.getInt("observed"),w.getInt("earned"),w.getInt("claimed")));
        }
        if(b.contains("sold",Tag.TAG_INT)){
            var service=AdventurerMarket.forRole(p.occupation());
            if(service==null)throw new IllegalArgumentException("Shop role mismatch");
            shops.put(p.id(),new AdventurerMarket.Shop(service,b.getInt("sold"),b.getInt("restocked")));
        }
    }
    private static State readState(CompoundTag b,long floor,int schema){
        int[] pos=b.getIntArray("destinations"),t=b.getIntArray("traits"),n=b.getIntArray("needs");
        if(pos.length!=4||t.length!=4||n.length!=5)throw new IllegalArgumentException("Invalid NPC vectors");
        var p=new Profile(b.getUUID("id"),b.getString("name"),floor,Occupation.valueOf(b.getString("job")),
            CombatClass.valueOf(b.getString("class")),Ambition.valueOf(b.getString("ambition")),pos[0],pos[1],pos[2],pos[3],new Personality(t[0],t[1],t[2],t[3]),schema>=2?b.getLong("birthFloor"):floor,schema>=2?b.getLong("relocatedAt"):0);
        List<Memory> mem=new ArrayList<>();
        var a=b.getList("memories",Tag.TAG_COMPOUND);
        if(a.size()>128)throw new IllegalArgumentException("Too many memories");
        for(int i=0;i<a.size();i++){
            var q=a.getCompound(i);mem.add(new Memory(q.getUUID("event"),q.getUUID("actor"),q.getLong("day"),EventType.valueOf(q.getString("type"))));
        }
        return new State(p,new Needs(n[0],n[1],n[2],n[3],n[4]),mem);
    }
}
