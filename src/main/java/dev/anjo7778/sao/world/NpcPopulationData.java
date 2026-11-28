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
    private final Map<UUID,net.minecraft.world.item.ItemStack> blades=new HashMap<>();
    private final Map<UUID,Integer> goodsReceipts=new HashMap<>();
    private final Map<UUID,Integer> supplierPayments=new HashMap<>();
    public net.minecraft.world.item.ItemStack blade(UUID id){
        var stack=blades.get(id);return stack==null?net.minecraft.world.item.ItemStack.EMPTY:stack.copy();
    }
    public int goodsReceipts(UUID id){return goodsReceipts.getOrDefault(id,0);}
    public int supplierPayments(UUID id){return supplierPayments.getOrDefault(id,0);}
    public int supplierBudget(UUID id){return goodsReceipts(id)-supplierPayments(id);}
    /** Finish one actual barrel transfer on the server thread. Replays with old cargo are rejected.
     * Only real equipment-sale receipts fund payments; no treasury withdrawal or free currency. */
    boolean settleDelivery(NpcSupplyData supplies,long floor,UUID buyer,NpcSupplyData.Cargo cargo){
        if(cargo==null||cargo.floor()!=floor||cargo.size()==0||cargo.delivered()==Long.MAX_VALUE
            ||buyer.equals(cargo.smith())||!cargo.equals(supplies.cargo(buyer)))return false;
        var b=state(floor,buyer);var s=state(floor,cargo.smith());
        if(b==null||s==null||b.profile().occupation()!=Occupation.ADVENTURER||s.profile().occupation()!=Occupation.BLACKSMITH)return false;
        var payment=AdventurerMarket.payMaterials(wallet(b.profile()),
            AdventurerMarket.materialQuote(cargo.iron(),cargo.coal(),cargo.logs()),supplierBudget(cargo.smith()));
        supplies.store(buyer,cargo.emptied());
        if(payment.paid()>0){
            wallets.put(buyer,payment.wallet());
            supplierPayments.put(cargo.smith(),supplierPayments(cargo.smith())+payment.paid());
            setDirty();
        }
        return true;
    }
    /** Exact stock, price and physical presence are checked by NpcEquipmentService. */
    boolean buyGoods(long floor,UUID buyer,UUID seller,net.minecraft.world.item.ItemStack item,int price,long time,long epoch){
        var capital=floors.get(floor);
        if(capital==null||buyer.equals(seller)||item.isEmpty()||item.getCount()!=1)return false;
        var b=capital.get(buyer);var s=capital.get(seller);
        if(b==null||s==null||b.profile().occupation()!=Occupation.ADVENTURER||s.profile().occupation()!=Occupation.BLACKSMITH
            ||NpcRoutine.resting(b.profile(),time)||b.needs().health()<35||NpcDailyPlan.at(s,time).activity()!=NpcDailyPlan.Activity.WORK)return false;
        boolean blade=item.is(dev.anjo7778.sao.item.SaoItems.STEEL_SWORD.get());
        if(!blade&&!item.is(net.minecraft.world.item.Items.IRON_PICKAXE)&&!item.is(net.minecraft.world.item.Items.IRON_AXE))return false;
        if(blade&&(!HuntContract.melee(b.profile())||!blade(buyer).isEmpty()))return false;
        var w=AdventurerMarket.buyGoods(wallet(b.profile()),price,epoch);
        int received=goodsReceipts(seller);
        if(w==null||received>AdventurerMarket.MAX-price)return false;
        wallets.put(buyer,w);goodsReceipts.put(seller,received+price);
        if(blade){
            blades.put(buyer,item.copy());
            var n=b.needs();capital.put(buyer,b.withNeeds(new Needs(n.health(),n.supplies(),100,n.partyReadiness(),n.danger())));
        }
        setDirty();return true;
    }
    public boolean wearBlade(UUID id,net.minecraft.world.item.ItemStack held){
        var stack=blades.get(id);
        if(stack==null||!net.minecraft.world.item.ItemStack.matches(stack,held))return false;
        var next=stack.copy();next.setDamageValue(next.getDamageValue()+1);
        if(next.getDamageValue()>=next.getMaxDamage())blades.remove(id);else blades.put(id,next);
        setDirty();return true;
    }
    private final Map<Long,AdventurerMarket.Treasury> treasuries=new HashMap<>();
    private final Map<UUID,HuntContract> hunts=new HashMap<>();
    private final Map<UUID,UUID> quarryClaims=new HashMap<>();
    public HuntContract hunt(UUID actor){return hunts.get(actor);}
    /** Called only from the real loaded survey encounter service. No extra mob spawn or chunk ticket. */
    public boolean openHunt(long floor,UUID actor,long surveySince,UUID quarry){
        var capital=floors.get(floor);var state=capital==null?null:capital.get(actor);
        var old=hunts.get(actor);
        if(state==null||!HuntContract.ready(state)||quarry==null||actor.equals(quarry)||surveySince<0
            ||treasury(floor).col()<HuntContract.REWARD||quarryClaims.containsKey(quarry)
            ||old!=null&&old.surveySince()>=surveySince)return false;
        if(old!=null&&old.active())quarryClaims.remove(old.quarry(),actor);
        hunts.put(actor,new HuntContract(floor,surveySince,quarry,HuntContract.Status.ACTIVE));
        quarryClaims.put(quarry,actor);setDirty();return true;
    }
    public boolean expireHunt(long floor,UUID actor,long surveySince){
        var h=hunts.get(actor);
        if(h==null||!h.active()||h.floor()!=floor||h.surveySince()!=surveySince)return false;
        hunts.put(actor,h.close(HuntContract.Status.EXPIRED));quarryClaims.remove(h.quarry(),actor);setDirty();return true;
    }
    /** Exact live death attribution is checked by HuntService before this atomic transaction. */
    public boolean finishHunt(long floor,UUID actor,long surveySince,UUID quarry){
        var h=hunts.get(actor);var capital=floors.get(floor);var state=capital==null?null:capital.get(actor);
        if(state==null||!HuntContract.supported(state.profile())||h==null||!h.active()||h.floor()!=floor
            ||h.surveySince()!=surveySince||!h.quarry().equals(quarry)||!actor.equals(quarryClaims.get(quarry)))return false;
        var reward=AdventurerMarket.huntReward(wallet(state.profile()),treasury(floor));
        wallets.put(actor,reward.wallet());treasuries.put(floor,reward.treasury());
        capital.put(actor,state.withNeeds(AdventurerMarket.wear(state.needs(),2)));
        hunts.put(actor,h.close(HuntContract.Status.KILLED));quarryClaims.remove(quarry,actor);setDirty();return true;
    }
    /** A real launched arrow consumes one existing abstract supply unit. No new collectible items. */
    public boolean spendHuntArrow(long floor,UUID id,long since,UUID quarry){
        var state=state(floor,id);var h=hunts.get(id);
        if(state==null||state.profile().combatClass()!=CombatClass.ARCHER||h==null||!h.active()||h.floor()!=floor
            ||h.surveySince()!=since||!h.quarry().equals(quarry)||!id.equals(quarryClaims.get(quarry))
            ||state.needs().supplies()<=HuntStyle.ARROW_RESERVE)return false;
        var n=state.needs();
        return updateNeeds(floor,id,new Needs(n.health(),n.supplies()-HuntStyle.ARROW_SUPPLY_COST,n.equipment(),n.partyReadiness(),n.danger()));
    }
    public void recordHuntHealth(long floor,UUID id,int health){
        var state=state(floor,id);if(state==null||!HuntContract.supported(state.profile()))return;
        var n=state.needs();
        updateNeeds(floor,id,new Needs(Math.max(1,Math.min(n.health(),health)),n.supplies(),n.equipment(),n.partyReadiness(),n.danger()));
    }
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
        if((schema!=1&&schema!=2&&schema!=3&&schema!=4&&schema!=5&&schema!=6&&schema!=7&&schema!=8)||!root.contains("capitals",Tag.TAG_LIST))throw new IllegalArgumentException("Unknown population schema");
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
                if(schema>=4)d.readEconomy(list.getCompound(k),s.profile(),registries);
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
                    if(schema>=4)d.readEconomy(list.getCompound(k),p,registries);
                }
                d.regions.put(key,roster);
            }
        }
        long paid=d.supplierPayments.values().stream().mapToLong(Integer::longValue).sum();
        long received=d.wallets.values().stream().mapToLong(AdventurerMarket.Wallet::materialEarned).sum();
        if(paid!=received)throw new IllegalArgumentException("Unbalanced material trade ledger");
        return d;
    }
    @Override public CompoundTag save(CompoundTag root,HolderLookup.Provider registries){
        root.putInt("schema",8);ListTag capitals=new ListTag();
        floors.forEach((floor,roster)->{
            CompoundTag entry=new CompoundTag();entry.putLong("floor",floor);
            ListTag npcs=new ListTag();roster.values().forEach(s->npcs.add(writeState(s,registries)));
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
            ListTag npcs=new ListTag();roster.values().forEach(s->npcs.add(writeState(s,registries)));
            entry.put("npcs",npcs);regional.add(entry);
        });
        root.put("regions",regional);return root;
    }
    private CompoundTag writeState(State s,HolderLookup.Provider registries){
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
            w.putLong("lastEpoch",wallet.lastEpoch());w.putInt("observed",wallet.observedOrders());w.putInt("earned",wallet.earned());w.putInt("claimed",wallet.claimedThrough());w.putInt("huntKills",wallet.huntKills());w.putInt("paidHunts",wallet.paidHunts());w.putInt("goodsOrders",wallet.goodsOrders());w.putInt("goodsSpent",wallet.goodsSpent());w.putInt("materialEarned",wallet.materialEarned());b.put("wallet",w);
        }
        var hunt=hunts.get(p.id());
        if(hunt!=null){
            var h=new CompoundTag();h.putLong("floor",hunt.floor());h.putLong("since",hunt.surveySince());h.putUUID("quarry",hunt.quarry());
            h.putString("status",hunt.status().name());b.put("hunt",h);
        }
        var blade=blades.get(p.id());if(blade!=null&&!blade.isEmpty())b.put("blade",blade.save(registries));
        int receipts=goodsReceipts(p.id());if(receipts>0)b.putInt("goodsReceipts",receipts);
        int supplierPaid=supplierPayments(p.id());if(supplierPaid>0)b.putInt("supplierPayments",supplierPaid);
        var shop=shops.get(p.id());if(shop!=null){b.putInt("sold",shop.sold());b.putInt("restocked",shop.restocked());}
        return b;
    }
    private void readEconomy(CompoundTag b,Profile p,HolderLookup.Provider registries){
        if(b.contains("wallet",Tag.TAG_COMPOUND)){
            if(p.occupation()!=Occupation.ADVENTURER)throw new IllegalArgumentException("Wallet role mismatch");
            var w=b.getCompound("wallet");
            wallets.put(p.id(),new AdventurerMarket.Wallet(w.getInt("supply"),w.getInt("repair"),w.getLong("lastEpoch"),w.getInt("observed"),w.getInt("earned"),w.getInt("claimed"),w.getInt("huntKills"),w.getInt("paidHunts"),w.getInt("goodsOrders"),w.getInt("goodsSpent"),w.getInt("materialEarned")));
        }
        if(b.contains("blade")){
            var blade=net.minecraft.world.item.ItemStack.parse(registries,b.get("blade")).orElseThrow();
            if(!HuntContract.melee(p)||!blade.is(dev.anjo7778.sao.item.SaoItems.STEEL_SWORD.get())||blade.getCount()!=1
                ||blade.getDamageValue()>=blade.getMaxDamage()||wallet(p).goodsOrders()==0)throw new IllegalArgumentException("Invalid purchased blade");
            blades.put(p.id(),blade);
        }
        if(b.contains("goodsReceipts",Tag.TAG_INT)){
            int n=b.getInt("goodsReceipts");
            if(p.occupation()!=Occupation.BLACKSMITH||n<=0||n>AdventurerMarket.MAX)throw new IllegalArgumentException("Invalid goods receipts");
            goodsReceipts.put(p.id(),n);
        }
        if(b.contains("supplierPayments",Tag.TAG_INT)){
            int paid=b.getInt("supplierPayments");
            if(p.occupation()!=Occupation.BLACKSMITH||paid<0||paid>goodsReceipts(p.id()))throw new IllegalArgumentException("Invalid supplier payments");
            if(paid>0)supplierPayments.put(p.id(),paid);
        }
        if(b.contains("hunt",Tag.TAG_COMPOUND)){
            if(!HuntContract.supported(p))throw new IllegalArgumentException("Invalid hunt profile");
            var h=b.getCompound("hunt");
            var hunt=new HuntContract(h.getLong("floor"),h.getLong("since"),h.getUUID("quarry"),HuntContract.Status.valueOf(h.getString("status")));
            if(hunt.quarry().equals(p.id())||hunt.floor()>p.currentFloor()||hunt.floor()<p.birthFloor()
                ||hunt.active()&&hunt.floor()!=p.currentFloor())throw new IllegalArgumentException("Invalid hunt floor");
            hunts.put(p.id(),hunt);
            if(hunt.active()&&quarryClaims.put(hunt.quarry(),p.id())!=null)throw new IllegalArgumentException("Duplicate active quarry");
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
