package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.SwordActions.Phase;
import dev.anjo7778.sao.simulation.SwordActions.Move;
import dev.anjo7778.sao.simulation.SwordActions.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
        final SwordActions clock;
        ServerLevel level;
        ItemStack main=ItemStack.EMPTY,off=ItemStack.EMPTY;
        UUID target,comboTarget,resolvingTarget;
        double resolvingDamage=1;
        boolean resolvingHitSeen;
        Skill selected=Skill.HORIZONTAL;
        Vec3 leapStart;
        Vec3 dodgeStart;
        boolean resolving,shown;
        long lastIntent=-100;
        int lastPose;
        Session(SwordCombatData.Entry saved,long now){
            clock=SwordActions.restore(saved==null?null:saved.clock(),now);
            if(saved!=null)selected=saved.selected();
        }
        void capture(LivingEntity actor,ServerLevel world){
            level=world;main=actor.getMainHandItem().copy();off=actor.getOffhandItem().copy();
        }
        boolean same(LivingEntity actor,ServerLevel world){
            return level==world&&ItemStack.matches(main,actor.getMainHandItem())&&ItemStack.matches(off,actor.getOffhandItem());
        }
    }
    private SwordCombat(){}
    private static long now(ServerLevel level){return level.getServer().overworld().getGameTime();}
    /** The common mixin must never grant charge to an ordinary attack or a client animation. */
    public static boolean authorizedPlayerHit(net.minecraft.world.entity.player.Player player){
        if(!(player instanceof ServerPlayer p))return false;
        var s=SESSIONS.get(p);
        return s!=null&&s.resolving&&s.resolvingTarget!=null&&eligible(p)&&supports(p);
    }
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
    private static SwordCombatData.Key key(LivingEntity e){return new SwordCombatData.Key(e instanceof CityNpc,e.getUUID());}
    private static Session session(LivingEntity e){
        var level=(ServerLevel)e.level();
        return SESSIONS.computeIfAbsent(e,k->new Session(SwordCombatData.get(level.getServer()).entry(key(e)),now(level)));
    }
    private static void persist(Session s,LivingEntity e,ServerLevel level){
        SwordCombatData.get(level.getServer()).store(key(e),new SwordCombatData.Entry(s.clock.save(now(level)),s.selected));
    }
    private static void advance(Session s,LivingEntity actor,ServerLevel level){
        long time=now(level);s.clock.advance(time);
        if(s.level!=null&&!s.same(actor,level)){
            if(s.clock.phase()!=Phase.RECOVERY)s.clock.cancel(time);
            else s.clock.breakCombo();
            s.capture(actor,level);
        }
        if(s.clock.phase()!=Phase.WINDUP){s.target=null;s.leapStart=null;}
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
        return target(actor,victim,level,REACH);
    }
    private static boolean target(LivingEntity actor,LivingEntity victim,ServerLevel level,double reach){
        return actor!=victim&&victim instanceof Enemy&&victim.level()==level&&outside(actor,level)&&outside(victim,level)
            &&FloorTravel.currentFloor(level,actor.getBlockX(),actor.getBlockZ())==FloorTravel.currentFloor(level,victim.getBlockX(),victim.getBlockZ())
            &&actor.distanceToSqr(victim)<=reach*reach&&actor.hasLineOfSight(victim)&&facing(actor,victim,.20);
    }
    private static long mastery(LivingEntity actor,ServerLevel level){
        var kind=actor instanceof ServerPlayer?CharacterProgressData.Kind.PLAYER:CharacterProgressData.Kind.NPC;
        var owner=new CharacterProgressData.Owner(kind,actor.getUUID());
        return CharacterProgressData.get(level.getServer()).state(owner).mastery(CharacterProgress.Mastery.SWORD);
    }
    /** Small ground-level advancing thrust, never a teleport or automatic hit at extended reach. */
    private static Vec3 leapMotion(LivingEntity actor,LivingEntity victim,ServerLevel level){
        if(!actor.onGround()||actor.isInWater()||actor.isPassenger()
            ||Math.abs(actor.getY()-victim.getY())>.6)return null;
        Vec3 delta=victim.position().subtract(actor.position());
        double length=Math.hypot(delta.x,delta.z);
        if(length<2.4||length>4.1)return null;
        double travel=Math.min(1.55,Math.max(.6,length-2.15));
        Vec3 dir=new Vec3(delta.x/length,0,delta.z/length);
        long floor=FloorTravel.currentFloor(level,actor.getBlockX(),actor.getBlockZ());
        for(int i=1;i<=6;i++){
            Vec3 step=dir.scale(travel*i/6);
            BlockPos p=BlockPos.containing(actor.position().add(step));
            if(!level.hasChunkAt(p)||!level.noCollision(actor,actor.getBoundingBox().move(step))
                ||!level.getFluidState(p).isEmpty()||!level.getBlockState(p.below()).isFaceSturdy(level,p.below(),Direction.UP)
                ||CityRules.isSafeZone(level,p.getX(),p.getZ())||FloorTravel.currentFloor(level,p.getX(),p.getZ())!=floor)return null;
        }
        return dir.scale(Math.min(.62,travel*.40));
    }
    private static boolean begin(Session s,LivingEntity actor,LivingEntity victim,ServerLevel level,Skill skill){
        boolean leap=skill==Skill.SONIC_LEAP;
        if(!target(actor,victim,level,leap?4.1:REACH)||!supports(actor)||s.clock.phase()!=Phase.IDLE)return false;
        Vec3 motion=leap?leapMotion(actor,victim,level):null;
        if(leap&&motion==null)return false;
        if(!victim.getUUID().equals(s.comboTarget))s.clock.breakCombo();
        boolean accepted=skill==null?s.clock.attack(now(level)):s.clock.skill(now(level),mastery(actor,level),skill);
        if(!accepted)return false;
        s.capture(actor,level);s.target=victim.getUUID();s.leapStart=leap?actor.position():null;
        if(leap){
            if(actor instanceof CityNpc npc)npc.getNavigation().stop();
            actor.setDeltaMovement(motion);actor.hurtMarked=true;
        }
        return true;
    }
    private static boolean impact(Session s,LivingEntity actor,LivingEntity victim,ServerLevel level){
        if(!target(actor,victim,level))return false;
        if(s.clock.move()!=Move.SONIC_LEAP)return true;
        if(s.leapStart==null)return false;
        Vec3 moved=actor.position().subtract(s.leapStart),toward=victim.position().subtract(s.leapStart);
        double distance=Math.hypot(moved.x,moved.z),direction=Math.hypot(toward.x,toward.z);
        return distance>=.30&&distance<=2.2&&direction>.001
            &&(moved.x*toward.x+moved.z*toward.z)/(distance*direction)>=.5;
    }
    private static float remainingHealth(LivingEntity victim){
        return victim.getHealth()+victim.getAbsorptionAmount();
    }
    private static void confirm(Session s,LivingEntity actor,LivingEntity victim,ServerLevel level,float before){
        boolean landed=remainingHealth(victim)<before;
        if(landed&&s.clock.move().hits>1){
            // Cosmetic feedback only after real health/absorption loss, once per confirmed strike.
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK,
                victim.getX(),victim.getY()+victim.getBbHeight()*.55,victim.getZ(),1,0,0,0,0);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANTED_HIT,
                victim.getX(),victim.getY()+victim.getBbHeight()*.55,victim.getZ(),4,.2,.25,.2,.04);
        }
        s.clock.confirmHit(now(level),landed);
        if(!supports(actor)||!victim.isAlive()){
            if(s.clock.phase()==Phase.WINDUP)s.clock.cancel(now(level));
            s.clock.breakCombo();
        }
        s.comboTarget=s.clock.combo()>0&&victim.isAlive()?victim.getUUID():null;
        if(s.comboTarget==null)s.clock.breakCombo();
    }
    private static void startResolve(Session s,LivingEntity victim){
        s.resolvingDamage=s.clock.claimedDamage();
        s.resolvingHitSeen=false;s.resolving=true;s.resolvingTarget=victim.getUUID();
    }
    private static void endResolve(Session s){s.resolving=false;s.resolvingTarget=null;s.resolvingHitSeen=false;}
    private static void sync(ServerPlayer p,Session s,boolean available){
        int pose=available?s.clock.visualCode(now(p.serverLevel()),p.serverLevel().getGameTime()):0;
        CombatNetwork.send(p,available,s.clock.stamina(),s.clock.phase(),s.clock.move(),
            s.clock.combo(),s.clock.skillCooldown(s.selected,now(p.serverLevel())),available&&s.selected.unlocked(mastery(p,p.serverLevel())),pose,s.selected.ordinal(),s.clock.landedHits());
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
        if(!p.isUsingItem())begin(s,p,victim,p.serverLevel(),null);
        persist(s,p,p.serverLevel());sync(p,s,true);
    }
    public static void playerTick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var level=p.serverLevel();var s=SESSIONS.get(p);
        boolean available=eligible(p)&&supports(p);
        if(s==null){if(!available)return;s=session(p);}
        advance(s,p,level);
        if(!available||!outside(p,level)){
            if(s.clock.phase()!=Phase.RECOVERY)s.clock.cancel(now(level));
            else s.clock.breakCombo();
            s.target=null;s.dodgeStart=null;s.leapStart=null;
        }
        if(available&&s.target!=null&&s.clock.claimHit(now(level))){
            var id=s.target;
            if(s.same(p,level)&&level.getEntity(id) instanceof LivingEntity victim&&impact(s,p,victim,level)&&!p.isUsingItem()){
                float before=remainingHealth(victim);
                startResolve(s,victim);
                try{p.swing(InteractionHand.MAIN_HAND);p.attack(victim);}finally{endResolve(s);}
                confirm(s,p,victim,level,before);
            }else s.clock.confirmHit(now(level),false);
            // The native hit may have changed real durability. Recovery still applies.
            s.capture(p,level);
            if(s.clock.phase()!=Phase.WINDUP)s.target=null;
        }
        persist(s,p,level);
        if(available&&(p.tickCount%5==0||!s.shown||s.lastPose!=s.clock.visualCode(now(level),level.getGameTime()))||!available&&s.shown){
            sync(p,s,available);
        }
    }
    /** A skill can select the real hostile intersected by the server's view ray.
     * This lets Sonic Leap aim beyond vanilla crosshair entity range without accepting a client hit claim. */
    private static LivingEntity aimedTarget(ServerPlayer p,ServerLevel level,Skill skill){
        double reach=skill==Skill.SONIC_LEAP?4.1:REACH;
        Vec3 eye=p.getEyePosition(),delta=p.getViewVector(1).scale(reach),end=eye.add(delta);
        var block=level.clip(new net.minecraft.world.level.ClipContext(eye,end,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
        double limit=block.getType()==net.minecraft.world.phys.HitResult.Type.MISS?reach*reach:eye.distanceToSqr(block.getLocation());
        LivingEntity best=null;
        for(var other:level.getEntitiesOfClass(LivingEntity.class,p.getBoundingBox().expandTowards(delta).inflate(1),
                e->e!=p&&e.isAlive()&&e.isPickable()&&!e.isSpectator())){
            var contact=other.getBoundingBox().inflate(.1).clip(eye,end);
            if(contact.isEmpty())continue;
            double dist=eye.distanceToSqr(contact.get());
            if(dist>=limit)continue;
            limit=dist;best=other;
        }
        return best!=null&&target(p,best,level,reach)?best:null;
    }
    private static void notice(ServerPlayer p,String reason,Object... args){
        p.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.sao.control."+reason,args),true);
    }
    /** Only these explicit, bounded intents can be used by the local controls.
     * Action 2 is reserved for the existing server-side acceptance fixture. */
    public static void intent(ServerPlayer p,int action,int direction){
        var level=p.serverLevel();
        if(action<0||action>5)return;
        if(!eligible(p)){notice(p,scope(p,level)?"survival":"world");return;}
        if(!supports(p)){notice(p,"equip");return;}
        var s=session(p);advance(s,p,level);
        long time=now(level);if(time-s.lastIntent<2)return;s.lastIntent=time;
        if(p.isUsingItem()){notice(p,"using");return;}
        if(action!=3&&action!=5&&!outside(p,level)){notice(p,"safe_zone");return;}
        if(s.clock.phase()!=Phase.IDLE){notice(p,"recovering");sync(p,s,true);return;}
        if(action==3||action==5){
            long skillLevel=mastery(p,level);
            Skill selected=null;
            if(action==5)selected=Skill.fromIndex(direction);
            else for(int i=1;i<Skill.values().length;i++){
                var next=Skill.values()[(s.selected.ordinal()+i)%Skill.values().length];
                if(next.unlocked(skillLevel)){selected=next;break;}
            }
            if(selected==null){notice(p,"no_other_skill");return;}
            if(!selected.unlocked(skillLevel)){notice(p,"mastery",selected.mastery);return;}
            // Selection is a saved preference, not a new cooldown or an attack.
            s.selected=selected;notice(p,"selected",net.minecraft.network.chat.Component.translatable("skill.sao."+selected.name().toLowerCase(java.util.Locale.ROOT)));
        }else if(action==0){
            if(s.clock.stamina()<SwordActions.GUARD_COST){notice(p,"stamina",SwordActions.GUARD_COST);return;}
            if(s.clock.guard(time)){s.capture(p,level);s.target=null;}
        }else if(action==4||action==2){
            var selected=s.selected;
            if(!selected.unlocked(mastery(p,level))){notice(p,"mastery",selected.mastery);return;}
            int wait=s.clock.skillCooldown(selected,time);
            if(wait>0){notice(p,"cooldown",String.format(java.util.Locale.ROOT,"%.1f",wait/20.0));return;}
            if(s.clock.stamina()<selected.move.cost){notice(p,"stamina",selected.move.cost);return;}
            LivingEntity victim=action==4?aimedTarget(p,level,selected)
                :level.getEntity(direction) instanceof LivingEntity e?e:null;
            if(victim==null){notice(p,"target");return;}
            if(!begin(s,p,victim,level,selected)){notice(p,selected==Skill.SONIC_LEAP?"leap":"target");return;}
        }else if(action==1&&direction>=0&&direction<=3){
            if(s.clock.stamina()<SwordActions.DODGE_COST){notice(p,"stamina",SwordActions.DODGE_COST);return;}
            if(!p.onGround()||p.isInWater()){notice(p,"ground");return;}
            double yaw=Math.toRadians(p.getYRot()),x=-Math.sin(yaw),z=Math.cos(yaw);
            double dx=direction==0?-x:direction==1?z:direction==2?-z:x;
            double dz=direction==0?-z:direction==1?-x:direction==2?x:z;
            for(double d:new double[]{.3,.6,.9}){
                if(!level.noCollision(p,p.getBoundingBox().move(dx*d,0,dz*d))
                    ||CityRules.isSafeZone(level,(int)Math.floor(p.getX()+dx*d),(int)Math.floor(p.getZ()+dz*d))){
                    notice(p,"blocked");return;
                }
            }
            if(s.clock.dodge(time)){
                s.capture(p,level);s.dodgeStart=p.position();s.target=null;
                p.setDeltaMovement(dx*.65,Math.max(0,p.getDeltaMovement().y),dz*.65);p.hurtMarked=true;
            }
        }
        persist(s,p,level);sync(p,s,true);
    }
    /** Called by the existing actual NPC quarry controller, not a second source of damage. */
    public static void npcStrike(CityNpc npc,ServerLevel level,Mob victim,HuntStyle style,NpcPopulationData pop){
        var s=session(npc);advance(s,npc,level);
        long time=now(level);
        if(s.target!=null&&s.clock.claimHit(time)){
            var id=s.target;
            if(id.equals(victim.getUUID())&&s.same(npc,level)&&impact(s,npc,victim,level)){
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
            s.capture(npc,level);
            if(s.clock.phase()!=Phase.WINDUP)s.target=null;
            return;
        }
        if(s.clock.phase()==Phase.WINDUP||s.clock.phase()==Phase.GUARD||s.clock.phase()==Phase.DODGE){
            npc.getNavigation().stop();return;
        }
        if(!target(npc,victim,level)){
            if(s.clock.phase()==Phase.IDLE&&npc.tickCount%12==0&&s.clock.stamina()>=45
                &&Skill.SONIC_LEAP.unlocked(mastery(npc,level))&&begin(s,npc,victim,level,Skill.SONIC_LEAP))return;
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
            Skill[] choices={Skill.HORIZONTAL,Skill.SLANT,Skill.VERTICAL,Skill.HORIZONTAL_SQUARE,Skill.VERTICAL_SQUARE};
            Skill chosen=choices[Math.floorMod(npc.getUUID().hashCode()+npc.tickCount/12,choices.length)];
            if(!skill||s.clock.stamina()<chosen.move.cost+8||!begin(s,npc,victim,level,chosen))begin(s,npc,victim,level,null);
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
        persist(s,npc,level);
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
                if(attack.resolvingHitSeen||!actor.getUUID().equals(attack.resolvingTarget)||!target(attacker,actor,level)){event.setCanceled(true);return;}
                attack.resolvingHitSeen=true;
                event.setAmount((float)(event.getAmount()*attack.resolvingDamage));
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
        if(result!=SwordActions.Defense.NONE)persist(s,actor,level);
        switch(result){
            case PARRY->event.setCanceled(true);
            case BLOCK->event.setAmount(event.getAmount()*.4f);
            case EVADE->event.setAmount(event.getAmount()*.5f);
            default->{}
        }
    }
}
