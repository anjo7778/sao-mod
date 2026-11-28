package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.SwordActions.Phase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Authoritative one-handed sword vertical slice. Vanilla damage, loot and durability
 * remain in the actual attack path; the shared clock owns action timing and stamina. */
public final class SwordCombat {
    private static final Map<LivingEntity,Session> SESSIONS=new WeakHashMap<>();
    private static final double REACH=WeaponRules.Family.SWORD.reach;
    private static final class Session {
        final SwordActions clock=new SwordActions();
        ServerLevel level;
        ItemStack main=ItemStack.EMPTY,off=ItemStack.EMPTY;
        UUID target;
        Vec3 dodgeStart;
        boolean resolving,shown;
        void capture(LivingEntity actor,ServerLevel world){
            level=world;main=actor.getMainHandItem().copy();off=actor.getOffhandItem().copy();
        }
        boolean same(LivingEntity actor,ServerLevel world){
            return level==world&&ItemStack.matches(main,actor.getMainHandItem())&&ItemStack.matches(off,actor.getOffhandItem());
        }
    }
    private SwordCombat(){}
    private static long now(ServerLevel level){return level.getServer().overworld().getGameTime();}
    public static boolean supports(LivingEntity e){
        return WeaponItems.classify(e.getMainHandItem())==WeaponRules.Family.SWORD
            &&WeaponItems.resolve(e.getMainHandItem(),e.getOffhandItem()).style()==WeaponRules.Style.SINGLE;
    }
    private static boolean scope(LivingEntity e,ServerLevel level){
        return e.isAlive()&&CityRules.hasRegionalSettlements(level)
            &&SaoWorldData.get(level.getServer()).isUnlocked(FloorTravel.currentFloor(level,e.getBlockX(),e.getBlockZ()));
    }
    private static boolean outside(LivingEntity e,ServerLevel level){
        return scope(e,level)&&!CityRules.isSafeZone(level,e.getBlockX(),e.getBlockZ());
    }
    private static boolean eligible(ServerPlayer p){
        return !p.isCreative()&&!p.isSpectator()&&!p.isPassenger()&&scope(p,p.serverLevel());
    }
    private static Session session(LivingEntity e){return SESSIONS.computeIfAbsent(e,k->new Session());}
    private static void advance(Session s,LivingEntity actor,ServerLevel level){
        long time=now(level);s.clock.advance(time);
        if(s.clock.phase()!=Phase.IDLE&&!s.same(actor,level)){
            if(s.clock.phase()!=Phase.RECOVERY)s.clock.cancel(time);
            s.capture(actor,level);
        }
        if(s.clock.phase()!=Phase.WINDUP)s.target=null;
        if(s.clock.phase()!=Phase.DODGE)s.dodgeStart=null;
    }
    private static boolean facing(LivingEntity actor,LivingEntity other,double threshold){
        Vec3 view=actor.getViewVector(1),delta=other.position().subtract(actor.position());
        double a=Math.hypot(view.x,view.z),b=Math.hypot(delta.x,delta.z);
        return a>.001&&b>.001&&(view.x*delta.x+view.z*delta.z)/(a*b)>=threshold;
    }
    private static boolean target(LivingEntity actor,LivingEntity victim,ServerLevel level){
        return actor!=victim&&victim instanceof Enemy&&victim.level()==level&&outside(actor,level)&&outside(victim,level)
            &&FloorTravel.currentFloor(level,actor.getBlockX(),actor.getBlockZ())==FloorTravel.currentFloor(level,victim.getBlockX(),victim.getBlockZ())
            &&actor.distanceToSqr(victim)<=REACH*REACH&&actor.hasLineOfSight(victim)&&facing(actor,victim,.20);
    }
    private static boolean begin(Session s,LivingEntity actor,LivingEntity victim,ServerLevel level){
        if(!target(actor,victim,level)||!supports(actor)||!s.clock.attack(now(level)))return false;
        s.capture(actor,level);s.target=victim.getUUID();return true;
    }
    public static void attack(AttackEntityEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||!eligible(p)
            ||!supports(p)||!(event.getTarget() instanceof LivingEntity victim)||!(victim instanceof Enemy))return;
        var s=session(p);if(s.resolving)return;
        event.setCanceled(true);
        advance(s,p,p.serverLevel());
        if(!p.isUsingItem())begin(s,p,victim,p.serverLevel());
    }
    public static void playerTick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var level=p.serverLevel();var s=SESSIONS.get(p);
        boolean available=eligible(p)&&supports(p);
        if(s==null){if(!available)return;s=session(p);}
        advance(s,p,level);
        if(!available||!outside(p,level)){
            if(s.clock.phase()!=Phase.IDLE&&s.clock.phase()!=Phase.RECOVERY)s.clock.cancel(now(level));
            s.target=null;s.dodgeStart=null;
        }
        if(available&&s.target!=null&&s.clock.claimHit(now(level))){
            var id=s.target;s.target=null;
            if(s.same(p,level)&&level.getEntity(id) instanceof LivingEntity victim&&target(p,victim,level)&&!p.isUsingItem()){
                s.resolving=true;
                try{p.swing(InteractionHand.MAIN_HAND);p.attack(victim);}finally{s.resolving=false;}
            }
            // The native hit may have changed real durability. Recovery still applies.
            s.capture(p,level);
        }
        if(available&&(p.tickCount%5==0||!s.shown)||!available&&s.shown){
            CombatNetwork.send(p,available,s.clock.stamina(),s.clock.phase());s.shown=available;
        }
    }
    /** Direction is relative to server-owned look yaw: backward, left, right, forward.
     * No positions, stamina or hit claims are accepted from the client. */
    public static void intent(ServerPlayer p,int action,int direction){
        var level=p.serverLevel();
        if(!eligible(p)||!supports(p)||!outside(p,level)||p.isUsingItem())return;
        var s=session(p);advance(s,p,level);
        if(action==0){
            if(s.clock.guard(now(level))){s.capture(p,level);s.target=null;}
        }else if(action==1&&direction>=0&&direction<=3&&p.onGround()&&!p.isInWater()){
            double yaw=Math.toRadians(p.getYRot()),x=-Math.sin(yaw),z=Math.cos(yaw);
            double dx=direction==0?-x:direction==1?z:direction==2?-z:x;
            double dz=direction==0?-z:direction==1?-x:direction==2?x:z;
            // Validate a short corridor against actual collision before applying native motion.
            for(double d:new double[]{.3,.6,.9}){
                if(!level.noCollision(p,p.getBoundingBox().move(dx*d,0,dz*d))
                    ||CityRules.isSafeZone(level,(int)Math.floor(p.getX()+dx*d),(int)Math.floor(p.getZ()+dz*d)))return;
            }
            if(s.clock.dodge(now(level))){
                s.capture(p,level);s.dodgeStart=p.position();s.target=null;
                p.setDeltaMovement(dx*.65,Math.max(0,p.getDeltaMovement().y),dz*.65);p.hurtMarked=true;
            }
        }
        CombatNetwork.send(p,true,s.clock.stamina(),s.clock.phase());s.shown=true;
    }
    /** Called by the existing actual NPC quarry controller, not a second source of damage. */
    public static void npcStrike(CityNpc npc,ServerLevel level,Mob victim,HuntStyle style,NpcPopulationData pop){
        var s=session(npc);advance(s,npc,level);
        long time=now(level);
        if(s.target!=null&&s.clock.claimHit(time)){
            var id=s.target;s.target=null;
            if(id.equals(victim.getUUID())&&s.same(npc,level)&&target(npc,victim,level)){
                npc.getNavigation().stop();npc.swing(InteractionHand.MAIN_HAND);
                var attack=npc.getAttribute(Attributes.ATTACK_DAMAGE);
                var held=npc.getMainHandItem().copy();
                boolean owned=held.is(dev.anjo7778.sao.item.SaoItems.STEEL_SWORD.get())&&ItemStack.matches(held,pop.blade(npc.getUUID()));
                if(attack!=null)attack.setBaseValue(owned?1.0:style.baseAttack);
                if(npc.doHurtTarget(victim)){
                    if(owned&&pop.wearBlade(npc.getUUID(),held))npc.updateDisplayItem();
                    if(victim.isAlive()&&victim.getTarget()==null)victim.setTarget(npc);
                }
            }
            s.capture(npc,level);return;
        }
        if(s.clock.phase()==Phase.WINDUP||s.clock.phase()==Phase.GUARD||s.clock.phase()==Phase.DODGE){
            npc.getNavigation().stop();return;
        }
        if(!target(npc,victim,level)){
            if(npc.tickCount%10==0)npc.getNavigation().moveTo(victim,style.speed);
            return;
        }
        npc.getNavigation().stop();
        // Imperfect reaction to a visible melee swing, not knowledge of a future damage event.
        if(s.clock.phase()==Phase.IDLE&&victim.swinging&&victim.getTarget()==npc&&npc.tickCount%7==0
            &&s.clock.guard(time)){s.capture(npc,level);return;}
        if(s.clock.phase()==Phase.IDLE&&npc.tickCount%4==0)begin(s,npc,victim,level);
    }
    public static void damage(LivingIncomingDamageEvent event){
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel level))return;
        var actor=event.getEntity();var source=event.getSource();
        if(!(actor instanceof ServerPlayer||actor instanceof CityNpc)||!outside(actor,level)||!supports(actor)
            ||!(source.getEntity() instanceof LivingEntity attacker)||source.getDirectEntity()!=attacker
            ||!(source.is(DamageTypes.MOB_ATTACK)||source.is(DamageTypes.PLAYER_ATTACK))
            ||!outside(attacker,level)||FloorTravel.currentFloor(level,actor.getBlockX(),actor.getBlockZ())!=FloorTravel.currentFloor(level,attacker.getBlockX(),attacker.getBlockZ()))return;
        var s=SESSIONS.get(actor);if(s==null)return;
        advance(s,actor,level);
        if(!s.same(actor,level))return;
        boolean moved=s.dodgeStart!=null&&actor.position().distanceToSqr(s.dodgeStart)>=.25*.25;
        var result=s.clock.defend(now(level),facing(actor,attacker,.25),moved);
        switch(result){
            case PARRY->event.setCanceled(true);
            case BLOCK->event.setAmount(event.getAmount()*.4f);
            case EVADE->event.setAmount(event.getAmount()*.5f);
            default->{}
        }
    }
}
