package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.NpcSocial;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** A bounded local interaction pass; it never creates chunks or converses with absent actors. */
public final class CitySocial {
    public static final int PERIOD=100, PAIR_BUDGET=64;
    private CitySocial(){}
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer player)||!player.isAlive()||player.isSpectator()||player.tickCount%PERIOD!=0)return;
        var level=player.serverLevel();
        if(!CityRules.isCity(level,player.getBlockX(),player.getBlockZ()))return;
        long floor=FloorTravel.currentFloor(level,player.getBlockX(),player.getBlockZ());
        var data=NpcPopulationData.get(level.getServer());
        List<CityNpc> actors=level.getEntitiesOfClass(CityNpc.class,player.getBoundingBox().inflate(36),
            e->e.originFloor()==floor&&e.canConverse());
        actors.sort(Comparator.comparing(CityNpc::getUUID));
        if(actors.size()<2)return;
        if(actors.size()>NpcPresence.CAP)actors=new ArrayList<>(actors.subList(0,NpcPresence.CAP));
        // Use elapsed game time, not manually adjustable sky time, for social contact limits.
        long day=Math.max(0,level.getGameTime()/24000);
        int start=Math.floorMod(level.getGameTime()/PERIOD,actors.size()),tries=0;
        for(int i=0;i<actors.size();i++){
            CityNpc a=actors.get((start+i)%actors.size());
            var stateA=data.state(floor,a.getUUID());
            for(int k=1;k<actors.size();k++){
                CityNpc b=actors.get((start+i+k)%actors.size());
                if(a.distanceToSqr(b)>36||a.distanceToSqr(b)<1||!a.hasLineOfSight(b)||!b.hasLineOfSight(a))continue;
                if(++tries>PAIR_BUDGET)return;
                var stateB=data.state(floor,b.getUUID());
                if(!NpcSocial.canTalk(stateA,stateB,day))continue;
                if(!a.canConverse()||!b.canConverse())continue;
                var result=AdventureSimulationData.get(level.getServer()).converse(data,floor,a.getUUID(),b.getUUID(),day,Math.max(0,level.getGameTime()/dev.anjo7778.sao.simulation.AdventureProgress.EPOCH_TICKS));
                if(result.isEmpty())continue;
                a.beginConversation(b);b.beginConversation(a);
                if(player.distanceToSqr(a)<=12*12&&player.distanceToSqr(b)<=12*12){
                    // At most one short exchange per pass; no world chat broadcast.
                    var m=result.get();
                    player.sendSystemMessage(Component.literal(a.getName().getString()+": "+m.lineFirst()+"  "+b.getName().getString()+": "+m.lineSecond()));
                }
                return;
            }
        }
    }
}
