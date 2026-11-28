package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Comparator;

/** First physical melee encounter slice. Never simulates kills when the entity is absent. */
public final class HuntService {
    private HuntService(){}
    private static long epoch(ServerLevel level){return Math.max(0,level.getServer().overworld().getGameTime()/AdventureProgress.EPOCH_TICKS);}
    private static boolean allowedType(EntityType<?> type){
        return type==EntityType.ZOMBIE||type==EntityType.HUSK||type==EntityType.SPIDER||type==EntityType.SKELETON;
    }
    private static boolean arena(ServerLevel level,long floor,ScoutingTrip.Trip trip,Entity e){
        if(e.level()!=level||!CityRules.hasRegionalSettlements(level)||CityRules.isSafeZone(level,e.getBlockX(),e.getBlockZ())
            ||FloorTravel.currentFloor(level,e.getBlockX(),e.getBlockZ())!=floor)return false;
        var c=FloorLayout.center(floor);var p=ScoutingTrip.gatePoint(floor,trip.gate(),36);
        int x=e.getBlockX()-c.x(),z=e.getBlockZ()-c.z();
        return ScoutingTrip.inRange(floor,x,z)&&Math.hypot(x-p.x(),z-p.z())<=18;
    }
    private static boolean target(ServerLevel level,CityNpc actor,ScoutingTrip.Trip trip,Mob mob){
        return mob.isAlive()&&allowedType(mob.getType())&&arena(level,actor.originFloor(),trip,mob)
            &&(mob.getTarget()==null||mob.getTarget()==actor)
            &&(mob.getLastHurtByMob()==null||mob.getLastHurtByMob()==actor);
    }
    private static ScoutingTrip.Trip trip(ServerLevel level,CityNpc npc){
        var p=npc.boundProfile();if(p==null)return null;
        var ledger=AdventureSimulationData.get(level.getServer()).ledger(p);
        return ledger==null?null:ledger.trip();
    }
    private static boolean survey(ScoutingTrip.Trip t){return t!=null&&t.phase()==ScoutingTrip.Phase.SURVEY;}
    public static boolean tick(CityNpc npc,ServerLevel level,ScoutingTrip.Trip t){
        if(!npc.boundAdventurer()||!HuntContract.melee(npc.boundProfile())||!CityRules.hasRegionalSettlements(level)||!survey(t))return false;
        var pop=NpcPopulationData.get(level.getServer());long floor=npc.originFloor(),now=epoch(level);
        var state=pop.state(floor,npc.getUUID());if(state==null)return false;
        var h=pop.hunt(npc.getUUID());
        if(!arena(level,floor,t,npc)||state.needs().health()<35||now-t.since()>=HuntContract.WINDOW_EPOCHS||level.getDifficulty()==Difficulty.PEACEFUL){
            if(h!=null&&h.matches(floor,t)&&h.active())pop.expireHunt(floor,npc.getUUID(),t.since());
            return false;
        }
        if(h==null||!h.matches(floor,t)){
            if(npc.tickCount%20!=0||!HuntContract.ready(state)||!npc.atScheduledDestination())return false;
            var mob=level.getEntitiesOfClass(Mob.class,npc.getBoundingBox().inflate(12),e->target(level,npc,t,e)&&npc.hasLineOfSight(e))
                .stream().min(Comparator.comparingDouble(e->npc.distanceToSqr(e))).orElse(null);
            if(mob==null||!pop.openHunt(floor,npc.getUUID(),t.since(),mob.getUUID()))return false;
            h=pop.hunt(npc.getUUID());
        }
        if(!h.active())return false;
        if(!(level.getEntity(h.quarry()) instanceof Mob mob)||!target(level,npc,t,mob)){
            pop.expireHunt(floor,npc.getUUID(),t.since());npc.getNavigation().stop();return false;
        }
        npc.getLookControl().setLookAt(mob,30,30);
        if(npc.tickCount%20==0){
            if(npc.distanceToSqr(mob)<=9&&npc.hasLineOfSight(mob)){
                npc.getNavigation().stop();npc.swing(InteractionHand.MAIN_HAND);
                if(npc.doHurtTarget(mob)&&mob.isAlive()&&mob.getTarget()==null)mob.setTarget(npc);
            }else npc.getNavigation().moveTo(mob,1.0);
        }
        return true;
    }
    public static boolean holding(CityNpc npc,ServerLevel level,State s,ScoutingTrip.Trip t,long now){
        if(!survey(t)||s.needs().health()<35||now-t.since()>=HuntContract.WINDOW_EPOCHS||!arena(level,npc.originFloor(),t,npc))return false;
        var h=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
        return h!=null&&h.active()&&h.matches(npc.originFloor(),t)&&level.getEntity(h.quarry()) instanceof Mob mob&&target(level,npc,t,mob);
    }
    public static boolean canRetaliate(CityNpc npc,ServerLevel level,DamageSource source){
        if(!npc.boundAdventurer()||!(source.getEntity() instanceof Mob mob)||!allowedType(mob.getType()))return false;
        var t=trip(level,npc);if(!survey(t))return false;
        var h=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
        return h!=null&&h.active()&&h.matches(npc.originFloor(),t)&&mob.getUUID().equals(h.quarry())
            &&arena(level,npc.originFloor(),t,npc)&&arena(level,npc.originFloor(),t,mob);
    }
    public static void recordHealth(CityNpc npc,ServerLevel level){
        var pop=NpcPopulationData.get(level.getServer());int health=(int)Math.floor(100*npc.getHealth()/npc.getMaxHealth());
        pop.recordHuntHealth(npc.originFloor(),npc.getUUID(),health);
        var t=trip(level,npc);
        if(health<35&&survey(t)){pop.expireHunt(npc.originFloor(),npc.getUUID(),t.since());npc.getNavigation().stop();}
    }
    public static void death(LivingDeathEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof Mob mob)||!allowedType(mob.getType())
            ||!(mob.level() instanceof ServerLevel level)||!(event.getSource().getEntity() instanceof CityNpc npc)
            ||!npc.isAlive()||!npc.boundAdventurer())return;
        var t=trip(level,npc);
        if(!survey(t)||!arena(level,npc.originFloor(),t,npc)||!arena(level,npc.originFloor(),t,mob))return;
        if(NpcPopulationData.get(level.getServer()).finishHunt(npc.originFloor(),npc.getUUID(),t.since(),mob.getUUID()))npc.getNavigation().stop();
    }
}
