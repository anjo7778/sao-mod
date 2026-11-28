package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.item.NativeSteelSword;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import java.util.*;

/** Only a physically present buyer and working local smith can transfer actual goods.
 * Existing Col wallet funds it; sale proceeds are persisted separately from repair receipts.
 * No abstract stock or offscreen gear awards. */
public final class NpcEquipmentService {
    private NpcEquipmentService(){}
    private record Offer(Profile seller,int slot,ItemStack item,int price,NpcDailyPlan.Plan destination){}
    private static int price(ItemStack item){
        return item.is(SaoItems.STEEL_SWORD.get())?32+8*NativeSteelSword.quality(item).ordinal():12;
    }
    private static Item desired(ServerLevel level,State s,NpcPopulationData pop){
        var cargo=NpcSupplyData.get(level.getServer()).cargo(s.profile().id());
        if(cargo!=null){
            if(cargo.size()!=0)return null;
            if(cargo.pickUses()<=NpcSupplyData.REPLACE_AT)return Items.IRON_PICKAXE;
            if(cargo.axeUses()<=NpcSupplyData.REPLACE_AT)return Items.IRON_AXE;
        }
        return HuntContract.melee(s.profile())&&pop.blade(s.profile().id()).isEmpty()?SaoItems.STEEL_SWORD.get():null;
    }
    private static Offer offer(ServerLevel level,State s){
        if(s.profile().occupation()!=Occupation.ADVENTURER||s.needs().health()<35||s.needs().supplies()<35
            ||NpcRoutine.resting(s.profile(),level.getDayTime()))return null;
        var pop=NpcPopulationData.get(level.getServer());var want=desired(level,s,pop);
        if(want==null||pop.wallet(s.profile()).col()<12)return null;
        // Scan only loaded actual smith workshops. Never generate chunks or plan fake stock.
        var choices=pop.roster(s.profile().currentFloor()).stream().filter(v->v.profile().occupation()==Occupation.BLACKSMITH)
            .filter(v->NpcDailyPlan.at(v,level.getDayTime()).activity()==NpcDailyPlan.Activity.WORK)
            .sorted(Comparator.<State>comparingLong(v->{long dx=v.profile().workX()-s.profile().homeX(),dz=v.profile().workZ()-s.profile().homeZ();return dx*dx+dz*dz;})
                .thenComparing(v->v.profile().id())).toList();
        for(var smith:choices){
            var pos=NpcForgeWorkshop.fixtures(smith.profile()).output();
            if(!level.hasChunkAt(pos)||!(level.getBlockEntity(pos) instanceof BarrelBlockEntity stock))continue;
            var dest=NpcDailyPlan.at(smith,level.getDayTime()).destination();
            for(int i=0;i<stock.getContainerSize();i++){
                var item=stock.getItem(i);
                if(!item.is(want)||item.getCount()!=1||item.getDamageValue()!=0)continue;
                int price=price(item);
                if(pop.wallet(s.profile()).col()<price+12)continue;
                return new Offer(smith.profile(),i,item.copy(),price,new NpcDailyPlan.Plan(NpcDailyPlan.Activity.PREPARE,dest));
            }
        }
        return null;
    }
    public static NpcDailyPlan.Plan plan(ServerLevel level,State s){
        var offer=offer(level,s);return offer==null?null:offer.destination();
    }
    public static boolean tick(CityNpc buyer,ServerLevel level){
        if(!buyer.boundAdventurer()||!buyer.isAlive()||!CityRules.hasRegionalSettlements(level))return false;
        var pop=NpcPopulationData.get(level.getServer());var s=pop.state(buyer.originFloor(),buyer.getUUID());
        if(s==null||AdventureSimulationData.get(level.getServer()).journey(s).active())return false;
        var offer=offer(level,s);
        if(offer==null||!buyer.matchesPlan(offer.destination())||!buyer.atScheduledDestination())return false;
        if(!(level.getEntity(offer.seller().id()) instanceof CityNpc smith)||!smith.isAlive()
            ||smith.originFloor()!=buyer.originFloor()||!smith.atScheduledDestination()
            ||!smith.matchesPlan(new NpcDailyPlan.Plan(NpcDailyPlan.Activity.WORK,offer.destination().destination()))
            ||buyer.distanceToSqr(smith)>36||!buyer.hasLineOfSight(smith))return false;
        var pos=NpcForgeWorkshop.fixtures(offer.seller()).output();
        if(!level.hasChunkAt(pos)||!CityRules.isSafeZone(level,pos.getX(),pos.getZ())
            ||!(level.getBlockEntity(pos) instanceof BarrelBlockEntity stock)
            ||!ItemStack.matches(stock.getItem(offer.slot()),offer.item())
            ||level.players().stream().anyMatch(p->p.containerMenu!=p.inventoryMenu&&p.distanceToSqr(smith)<400))return false;
        var supplies=NpcSupplyData.get(level.getServer());var cargo=supplies.cargo(buyer.getUUID());
        NpcSupplyData.Cargo replacement=null;
        if(offer.item().is(Items.IRON_PICKAXE)||offer.item().is(Items.IRON_AXE)){
            if(cargo==null||cargo.size()!=0)return false;
            var r=offer.item().is(Items.IRON_AXE)?GateProspects.Resource.DEADWOOD:GateProspects.Resource.IRON;
            int remaining=offer.item().getMaxDamage()-offer.item().getDamageValue();
            try{replacement=cargo.replacement(r,remaining);}catch(IllegalArgumentException noNeed){return false;}
        }
        long epoch=Math.max(0,level.getServer().overworld().getGameTime()/AdventureProgress.EPOCH_TICKS);
        if(!pop.buyGoods(buyer.originFloor(),buyer.getUUID(),smith.getUUID(),offer.item(),offer.price(),level.getDayTime(),epoch))return false;
        stock.removeItemNoUpdate(offer.slot());stock.setChanged();
        if(replacement!=null)supplies.store(buyer.getUUID(),replacement);
        buyer.updateDisplayItem();
        buyer.reviewMenu(new NpcMenuReview(NpcMenuReview.Kind.PURCHASE,offer.item().getHoverName().getString()+"|"+offer.price()+" Col"));
        return true;
    }
}
