package dev.anjo7778.sao.entity;

import dev.anjo7778.sao.world.BoarSpawns;
import dev.anjo7778.sao.world.CityRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/** First original Aincrad field monster. Ordinary real Minecraft health, AI, saves and loot. */
public final class FrenzyBoar extends Monster {
    public FrenzyBoar(EntityType<? extends FrenzyBoar> type,Level level){
        super(type,level);xpReward=4;
    }
    public static AttributeSupplier.Builder attributes(){
        return Monster.createMonsterAttributes().add(Attributes.MAX_HEALTH,24).add(Attributes.ATTACK_DAMAGE,4)
            .add(Attributes.MOVEMENT_SPEED,.28).add(Attributes.FOLLOW_RANGE,16).add(Attributes.KNOCKBACK_RESISTANCE,.2);
    }
    @Override protected void registerGoals(){
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1.15,true));
        goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.75));
        goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
        targetSelector.addGoal(1,new HurtByTargetGoal(this));
        targetSelector.addGoal(2,new NearestAttackableTargetGoal<>(this,Player.class,true));
    }
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel server)||!isAlive()||tickCount%20!=0)return;
        if(CityRules.hasRegionalSettlements(server)&&!BoarSpawns.allowedArea(server,getBlockX(),getBlockZ())){
            discard();return;
        }
        var target=getTarget();
        if(target!=null&&CityRules.isSafeZone(server,target.getBlockX(),target.getBlockZ())){
            setTarget(null);getNavigation().stop();
        }
    }
    @Override protected SoundEvent getAmbientSound(){return SoundEvents.PIG_AMBIENT;}
    @Override protected SoundEvent getHurtSound(DamageSource source){return SoundEvents.PIG_HURT;}
    @Override protected SoundEvent getDeathSound(){return SoundEvents.PIG_DEATH;}
}
