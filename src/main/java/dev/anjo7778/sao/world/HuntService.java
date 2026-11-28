package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.entity.SaoEntities;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

import java.util.Comparator;

/** First physical melee encounter slice. Never simulates kills when the entity is absent. */
public final class HuntService {
    private static final String ARROW="sao_hunt_arrow";
    private HuntService(){}
    private static long epoch(ServerLevel level){return Math.max(0,level.getServer().overworld().getGameTime()/AdventureProgress.EPOCH_TICKS);}
    public static boolean allowedType(EntityType<?> type){
        return type==EntityType.ZOMBIE||type==EntityType.HUSK||type==EntityType.SPIDER||type==EntityType.SKELETON||type==SaoEntities.FRENZY_BOAR.get();
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
        if(!npc.boundAdventurer()||!HuntContract.supported(npc.boundProfile())||!CityRules.hasRegionalSettlements(level)||!survey(t))return false;
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
        if(!h.active()){npc.stopUsingItem();return false;}
        if(!(level.getEntity(h.quarry()) instanceof Mob mob)||!target(level,npc,t,mob)){
            pop.expireHunt(floor,npc.getUUID(),t.since());npc.getNavigation().stop();return false;
        }
        var style=HuntStyle.of(npc.boundProfile().combatClass());
        npc.getLookControl().setLookAt(mob,30,30);
        double distance=npc.distanceToSqr(mob);
        if(style.ranged){
            if(state.needs().supplies()<=HuntStyle.ARROW_RESERVE){
                pop.expireHunt(floor,npc.getUUID(),t.since());npc.stopUsingItem();npc.getNavigation().stop();return false;
            }
            if(distance<=style.reach*style.reach&&npc.hasLineOfSight(mob)){
                npc.getNavigation().stop();
                if(!npc.isUsingItem())npc.startUsingItem(InteractionHand.MAIN_HAND);
                if(npc.getTicksUsingItem()>=20&&npc.tickCount%style.interval==0){
                    fire(level,npc,mob,h,pop);
                    npc.stopUsingItem();
                }
            }else{
                npc.stopUsingItem();
                if(npc.tickCount%20==0)npc.getNavigation().moveTo(mob,style.speed);
            }
        }else if(SwordCombat.supports(npc)){
            SwordCombat.npcStrike(npc,level,mob,style,pop);
        }else if(npc.tickCount%style.interval==0){
            if(distance<=style.reach*style.reach&&npc.hasLineOfSight(mob)){
                npc.getNavigation().stop();npc.swing(InteractionHand.MAIN_HAND);
                var attack=npc.getAttribute(Attributes.ATTACK_DAMAGE);
                var held=npc.getMainHandItem().copy();
                boolean owned=held.is(dev.anjo7778.sao.item.SaoItems.STEEL_SWORD.get())&&net.minecraft.world.item.ItemStack.matches(held,pop.blade(npc.getUUID()));
                if(attack!=null)attack.setBaseValue(owned?1.0:style.baseAttack);
                if(npc.doHurtTarget(mob)){
                    if(owned&&pop.wearBlade(npc.getUUID(),held))npc.updateDisplayItem();
                    if(mob.isAlive()&&mob.getTarget()==null)mob.setTarget(npc);
                }
            }else npc.getNavigation().moveTo(mob,style.speed);
        }
        return true;
    }
    private static void fire(ServerLevel level,CityNpc npc,Mob mob,HuntContract h,NpcPopulationData pop){
        if(!npc.getMainHandItem().is(Items.BOW))return;
        var arrow=new Arrow(level,npc,new ItemStack(Items.ARROW),npc.getMainHandItem().copy());
        arrow.pickup=AbstractArrow.Pickup.DISALLOWED;arrow.setBaseDamage(3.0);
        arrow.getPersistentData().putBoolean(ARROW,true);
        arrow.getPersistentData().putUUID("sao_hunt_owner",npc.getUUID());
        arrow.getPersistentData().putUUID("sao_hunt_quarry",mob.getUUID());
        arrow.getPersistentData().putLong("sao_hunt_since",h.surveySince());
        double dx=mob.getX()-arrow.getX(),dz=mob.getZ()-arrow.getZ();
        double dy=mob.getY()+mob.getBbHeight()*.5-arrow.getY();
        arrow.shoot(dx,dy+Math.hypot(dx,dz)*.08,dz,1.6f,0.6f);
        if(!level.addFreshEntity(arrow))return;
        if(!pop.spendHuntArrow(npc.originFloor(),npc.getUUID(),h.surveySince(),mob.getUUID())){arrow.discard();return;}
        npc.playSound(SoundEvents.ARROW_SHOOT,1.0f,1.0f);
    }
    private static boolean tagged(Entity e){return e instanceof Arrow&&e.getPersistentData().getBoolean(ARROW);}
    private static boolean validArrow(ServerLevel level,Entity arrow,CityNpc npc){
        var n=arrow.getPersistentData();var t=trip(level,npc);
        var h=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
        return n.hasUUID("sao_hunt_owner")&&n.hasUUID("sao_hunt_quarry")&&n.getUUID("sao_hunt_owner").equals(npc.getUUID())
            &&npc.isAlive()&&survey(t)&&h!=null&&h.active()&&h.matches(npc.originFloor(),t)
            &&h.surveySince()==n.getLong("sao_hunt_since")&&h.quarry().equals(n.getUUID("sao_hunt_quarry"))
            &&arena(level,npc.originFloor(),t,npc)&&arena(level,npc.originFloor(),t,arrow)
            &&epoch(level)-t.since()<HuntContract.WINDOW_EPOCHS;
    }
    /** Cancel non-contract damage even when an arrow's owner has unloaded. */
    public static void damage(LivingIncomingDamageEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        var source=event.getSource();var direct=source.getDirectEntity();
        if(!tagged(direct)&&!(source.getEntity() instanceof CityNpc))return;
        if(!(source.getEntity() instanceof CityNpc npc)||!npc.boundAdventurer()
            ||!(event.getEntity() instanceof Mob mob)||!allowedType(mob.getType())){event.setCanceled(true);return;}
        var t=trip(level,npc);var h=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
        if(!survey(t)||h==null||!h.active()||!h.matches(npc.originFloor(),t)||!mob.getUUID().equals(h.quarry())
            ||!arena(level,npc.originFloor(),t,npc)||!arena(level,npc.originFloor(),t,mob)
            ||epoch(level)-t.since()>=HuntContract.WINDOW_EPOCHS||tagged(direct)&&!validArrow(level,direct,npc))event.setCanceled(true);
    }
    public static void projectileTick(EntityTickEvent.Post event){
        var e=event.getEntity();
        if(!tagged(e)||!(e.level() instanceof ServerLevel level))return;
        var tag=e.getPersistentData();
        if(!tag.hasUUID("sao_hunt_owner")||!(level.getEntity(tag.getUUID("sao_hunt_owner")) instanceof CityNpc npc)
            ||!validArrow(level,e,npc)||e.tickCount>=100)e.discard();
    }
    public static boolean holding(CityNpc npc,ServerLevel level,State s,ScoutingTrip.Trip t,long now){
        if(!survey(t)||s.needs().health()<35||now-t.since()>=HuntContract.WINDOW_EPOCHS||!arena(level,npc.originFloor(),t,npc))return false;
        var h=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
        return h!=null&&h.active()&&h.matches(npc.originFloor(),t)&&level.getEntity(h.quarry()) instanceof Mob mob&&target(level,npc,t,mob);
    }
    public static boolean canRetaliate(CityNpc npc,ServerLevel level,DamageSource source){
        if(!npc.boundAdventurer()||!HuntContract.supported(npc.boundProfile())||!(source.getEntity() instanceof Mob mob)||!allowedType(mob.getType()))return false;
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
        if(!survey(t)||!arena(level,npc.originFloor(),t,npc)||!arena(level,npc.originFloor(),t,mob)
            ||epoch(level)-t.since()>=HuntContract.WINDOW_EPOCHS)return;
        if(NpcPopulationData.get(level.getServer()).finishHunt(npc.originFloor(),npc.getUUID(),t.since(),mob.getUUID())){
            npc.getNavigation().stop();
            RpgEvents.npcHunt(level,npc.boundProfile(),mob,event.getSource().getDirectEntity());
        }
    }
}
