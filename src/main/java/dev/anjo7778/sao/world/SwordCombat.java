package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.SwordActions.Phase;
import dev.anjo7778.sao.simulation.SwordActions.Move;
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
import net.neoforged.neoforge.event.tick.EntityTickEvent;
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
        UUID target,comboTarget,resolvingTarget;
        Move resolvingMove=Move.BASIC;
        Vec3 dodgeStart;
        boolean resolving,shown;
        long lastIntent=-100;
        int lastPose;
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
        if(s.level!=null&&!s.same(actor,level)){
            if(s.clock.phase()!=Phase.RECOVERY)s.clock.cancel(time);
            else s.clock.breakCombo();
            s.capture(actor,level);
        }
        if(s.clock.phase()!=Phase.WINDUP)s.target=null;
        if(s.clock.phase()!=Phase.DODGE)s.dodgeStart=null;
        if(s.clock.combo()==0)s.comboTarget=null;
    }
    private static boolean facing(LivingEntity actor,LivingEntity other,double threshold){
        double yaw=Math.toRadians(actor instanceof CityNpc?actor.getYHeadRot():actor.getYRot());
        Vec3 delta=other.position().subtract(actor.position());
        double b=Math.hypot(delta.x,delta.z);
        return b>.001&&(-Math.sin(yaw)*delta.x+Math.cos(yaw)*delta.z)/b>=threshold;
    }
    private static boolean target(LivingEntity actor,LivingEntity victim,ServerLevel level){
        return actor!=victim&&victim instanceof Enemy&&victim.level()==level&&outside(actor,level)&&outside(victim,level)
            &&FloorTravel.currentFloor(level,actor.getBlockX(),actor.getBlockZ())==FloorTravel.currentFloor(level,victim.getBlockX(),victim.getBlockZ())
            &&actor.distanceToSqr(victim)<=REACH*REACH&&actor.hasLineOfSight(victim)&&facing(actor,victim,.20);
    }
    private static long mastery(LivingEntity actor,ServerLevel level){
        var kind=actor instanceof ServerPlayer?CharacterProgressData.Kind.PLAYER:CharacterProgressData.Kind.NPC;
        var owner=new CharacterProgressData.Owner(kind,actor.getUUID());
        return CharacterProgressData.get(level.getServer()).state(owner).mastery(CharacterProgress.Mastery.SWORD);
    }
    private static boolean begin(Session s,LivingEntity actor,LivingEntity victim,ServerLevel level,boolean skill){
        if(!target(actor,victim,level)||!supports(actor))return false;
        if(s.clock.phase()!=Phase.IDLE)return false;
        if(!victim.getUUID().equals(s.comboTarget))s.clock.breakCombo();
        boolean accepted=skill?s.clock.horizontal(now(level),mastery(actor,level)):s.clock.attack(now(level));
        if(!accepted)return false;
        s.capture(actor,level);s.target=victim.getUUID();return true;
    }
    private static float remainingHealth(LivingEntity victim){
        return victim.getHealth()+victim.getAbsorptionAmount();
    }
    private static void confirm(Session s,LivingEntity actor,LivingEntity victim,ServerLevel level,float before){
        boolean landed=remainingHealth(victim)<before&&!victim.isRemoved()||!victim.isAlive()&&victim.getHealth()<=0&&before>0;
        s.clock.confirmHit(now(level),landed&&supports(actor));
        s.comboTarget=s.clock.combo()>0?victim.getUUID():null;
    }
    private static void startResolve(Session s,LivingEntity victim){
        s.resolving=true;s.resolvingTarget=victim.getUUID();s.resolvingMove=s.clock.move();
    }
    private static void endResolve(Session s){s.resolving=false;s.resolvingTarget=null;}
    private static void sync(ServerPlayer p,Session s,boolean available){
        int pose=available?s.clock.visualCode(now(p.serverLevel()),p.serverLevel().getGameTime()):0;
        CombatNetwork.send(p,available,s.clock.stamina(),s.clock.phase(),s.clock.move(),
            s.clock.combo(),s.clock.skillCooldown(now(p.serverLevel())),mastery(p,p.serverLevel())>=SwordActions.HORIZONTAL_MASTERY,pose);
        s.shown=available;s.lastPose=pose;
    }
    public static void attack(AttackEntityEvent event){
        if(event.isCanceled()||!(event.getEntity() instanceof ServerPlayer p)||!eligible(p))return;
        var s=SESSIONS.get(p);
        if(s!=null&&s.resolving)return;
        if(s!=null){
            advance(s,p,p.serverLevel());
            if(s.clock.phase()!=Phase.IDLE){event.setCanceled(true);return;}
        }
        if(!supports(p)||!(event.getTarget() instanceof LivingEntity victim)||!(victim instanceof Enemy))return;
        if(s==null)s=session(p);
        event.setCanceled(true);
        if(!p.isUsingItem())begin(s,p,victim,p.serverLevel(),false);
        sync(p,s,true);
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
                float before=remainingHealth(victim);
                startResolve(s,victim);
                try{p.swing(InteractionHand.MAIN_HAND);p.attack(victim);}finally{endResolve(s);}
                confirm(s,p,victim,level,before);
            }else s.clock.confirmHit(now(level),false);
            // The native hit may have changed real durability. Recovery still applies.
            s.capture(p,level);
        }
        if(available&&(p.tickCount%5==0||!s.shown||s.lastPose!=s.clock.visualCode(now(level),level.getGameTime()))||!available&&s.shown){
            sync(p,s,available);
        }
    }
    /** Direction is relative to server-owned look yaw: backward, left, right, forward.
     * No positions, stamina or hit claims are accepted from the client. */
    public static void intent(ServerPlayer p,int action,int direction){
        var level=p.serverLevel();
        if(!eligible(p)||!supports(p)||!outside(p,level)||p.isUsingItem())return;
        var s=session(p);advance(s,p,level);
        long time=now(level);if(time-s.lastIntent<2)return;s.lastIntent=time;
        if(action==0){
            if(s.clock.guard(now(level))){s.capture(p,level);s.target=null;}
        }else if(action==2){
            if(direction>=0&&level.getEntity(direction) instanceof LivingEntity victim)begin(s,p,victim,level,true);
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
        sync(p,s,true);
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
                float before=remainingHealth(victim);boolean hurt;
                startResolve(s,victim);
                try{hurt=npc.doHurtTarget(victim);}finally{endResolve(s);}
                if(hurt){
                    if(owned&&pop.wearBlade(npc.getUUID(),held))npc.updateDisplayItem();
                    if(victim.isAlive()&&victim.getTarget()==null)victim.setTarget(npc);
                }
                confirm(s,npc,victim,level,before);
            }else s.clock.confirmHit(time,false);
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
        if(s.clock.phase()==Phase.IDLE&&npc.tickCount%4==0){
            // Prefer to finish an earned chain; trained adventurers sometimes choose Horizontal.
            boolean skill=s.clock.combo()==0&&s.clock.stamina()>=40&&npc.tickCount%12==0;
            if(!skill||!begin(s,npc,victim,level,true))begin(s,npc,victim,level,false);
        }
    }
    /** Restore stamina during ordinary represented NPC rest, not only while in combat. */
    public static void npcTick(EntityTickEvent.Post event){
        if(!(event.getEntity() instanceof CityNpc npc)||!(npc.level() instanceof ServerLevel level))return;
        var s=SESSIONS.get(npc);if(s==null){if(npc.combatPose()!=0)npc.setCombatPose(0);return;}
        advance(s,npc,level);
        if(s.clock.phase()==Phase.WINDUP||s.clock.phase()==Phase.GUARD||s.clock.phase()==Phase.DODGE){
            var contract=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
            if(!supports(npc)||!outside(npc,level)||contract==null||!contract.active()){
                s.clock.cancel(now(level));s.target=null;s.dodgeStart=null;
            }
        }
        npc.setCombatPose(s.clock.visualCode(now(level),level.getGameTime()));
    }
    public static void damage(LivingIncomingDamageEvent event){
        if(event.isCanceled()||!(event.getEntity().level() instanceof ServerLevel level))return;
        var actor=event.getEntity();var source=event.getSource();
        if(source.getEntity() instanceof LivingEntity attacker&&source.getDirectEntity()==attacker
            &&(source.is(DamageTypes.MOB_ATTACK)||source.is(DamageTypes.PLAYER_ATTACK))){
            var attack=SESSIONS.get(attacker);
            if(attack!=null&&attack.resolving){
                // Only the selected hostile target receives this authored hit, never vanilla sweeping collateral.
                if(!actor.getUUID().equals(attack.resolvingTarget)||!target(attacker,actor,level)){event.setCanceled(true);return;}
                event.setAmount((float)(event.getAmount()*attack.resolvingMove.damage));
            }
        }
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
