package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import dev.anjo7778.sao.simulation.NpcDailyPlan.*;
import dev.anjo7778.sao.simulation.AdventurerMarket.Service;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Service requires local stock, funds, an open shop and physical arrival if represented. */
public final class MarketService {
    private MarketService(){}
    private static Service desired(State s,RegionalJourney.Journey j,Plan base,long time){
        if(s.profile().occupation()!=Occupation.ADVENTURER||base.activity()!=Activity.PREPARE
            ||NpcRoutine.resting(s.profile(),time)||s.needs().health()<35)return null;
        if(j.active()){
            if(!j.visiting())return null;
            return switch(j.purpose()){
                case SUPPLIES->Service.SUPPLIES.needsService(s.needs())?Service.SUPPLIES:null;
                case EQUIPMENT->Service.REPAIR.needsService(s.needs())?Service.REPAIR:null;
                default->null;
            };
        }
        return s.needs().supplies()<35?Service.SUPPLIES:s.needs().equipment()<40?Service.REPAIR:null;
    }
    private static List<State> local(NpcPopulationData pop,long floor,RegionalJourney.Journey j){
        return j.visiting()?pop.regionalRoster(floor,j.target()):pop.roster(floor);
    }
    public static Plan plan(ServerLevel level,State s,RegionalJourney.Journey j,Plan base){
        long time=level.getDayTime();var want=desired(s,j,base,time);
        if(want==null)return base;
        var pop=NpcPopulationData.get(level.getServer());var p=s.profile();
        var best=local(pop,p.currentFloor(),j).stream()
            .filter(v->v.profile().occupation()==want.seller()&&AdventurerMarket.canOffer(s.needs(),pop.wallet(p),pop.shop(v.profile()),pop.treasury(p.currentFloor())))
            .min(Comparator.<State>comparingInt(v->NpcDailyPlan.at(v,time).activity()==Activity.WORK?0:1)
                .thenComparingLong(v->{long dx=(long)v.profile().workX()+3-base.destination().x(),dz=(long)v.profile().workZ()-3-base.destination().z();return dx*dx+dz*dz;})
                .thenComparing(v->v.profile().id())).orElse(null);
        return best==null?base:new Plan(Activity.PREPARE,new Point(best.profile().workX()+3,best.profile().workZ()-3));
    }
    public static boolean transact(ServerLevel level,State buyer,RegionalJourney.Journey j,Plan desiredPlan,long epoch){
        long time=level.getDayTime(),floor=buyer.profile().currentFloor();var want=desired(buyer,j,desiredPlan,time);
        if(want==null)return false;
        var pop=NpcPopulationData.get(level.getServer());var id=buyer.profile().id();
        var seller=local(pop,floor,j).stream().filter(v->v.profile().occupation()==want.seller())
            .filter(v->{var plan=NpcDailyPlan.at(v,time);return plan.activity()==Activity.WORK&&plan.destination().equals(desiredPlan.destination());})
            .findFirst().orElse(null);
        if(seller==null)return false;
        var b=level.getEntity(id);var v=level.getEntity(seller.profile().id());
        boolean observed=b!=null||v!=null;
        if(observed){
            if(!(b instanceof CityNpc client)||!(v instanceof CityNpc merchant)||!client.isAlive()||!merchant.isAlive()
                ||client.originFloor()!=floor||merchant.originFloor()!=floor||!client.matchesPlan(desiredPlan)
                ||!merchant.matchesPlan(NpcDailyPlan.at(seller,time))||!client.atScheduledDestination()||!merchant.atScheduledDestination()
                ||client.distanceToSqr(merchant)>36||!client.hasLineOfSight(merchant))return false;
        }else{
            var c=FloorLayout.center(floor);var pos=desiredPlan.destination();
            if(level.players().stream().anyMatch(p->!p.isSpectator()&&p.distanceToSqr(c.x()+pos.x()+.5,CityPlan.GROUND+1,c.z()+pos.z()+.5)<=100*100))return false;
        }
        return pop.purchase(floor,id,seller.profile().id(),j.visiting()?j.target():null,desiredPlan.destination(),time,epoch,observed);
    }
}
