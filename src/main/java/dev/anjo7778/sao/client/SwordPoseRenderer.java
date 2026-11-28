package dev.anjo7778.sao.client;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.SwordPose;
import dev.anjo7778.sao.simulation.WeaponRules;
import dev.anjo7778.sao.world.WeaponItems;
import net.minecraft.client.Minecraft;
import dev.anjo7778.sao.simulation.HumanoidMotion;
import dev.anjo7778.sao.simulation.HumanoidMotion.Joint;
import dev.anjo7778.sao.simulation.HumanoidMotion.Pose;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/** Original client pose curves driven only by synchronized server action stamps.
 * Rendering never changes collision, damage, inventory or action timing. */
public final class SwordPoseRenderer {
    private SwordPoseRenderer(){}
    private static SwordPose.Frame frame(LivingEntity actor,float partial){
        if(!actor.isAlive()||actor.isUsingItem()
            ||WeaponItems.classify(actor.getMainHandItem())!=WeaponRules.Family.SWORD
            ||WeaponItems.resolve(actor.getMainHandItem(),actor.getOffhandItem()).style()!=WeaponRules.Style.SINGLE)return null;
        int stamp;
        if(actor instanceof CityNpc npc)stamp=npc.combatPose();
        else if(actor==Minecraft.getInstance().player){
            var snapshot=CombatNetwork.snapshot();
            if(snapshot==null||!snapshot.available()||!snapshot.owner().equals(actor.getUUID()))return null;
            stamp=snapshot.pose();
        }else return null;
        int age=SwordPose.age(stamp,actor.level().getGameTime());
        if(age<0)return null;
        return SwordPose.sample(SwordPose.kind(stamp),age+Math.max(0,Math.min(1,partial)));
    }
    /** Full-body combat layer over the native locomotion base. The body twist
     * follows the sword, the free arm counterbalances, and grounded feet brace. */
    public static Pose overlay(Pose base,LivingEntity actor,float partial){
        var f=frame(actor,partial);
        if(f==null||f.weight()<=0)return null;
        boolean right=actor.getMainArm()==HumanoidArm.RIGHT;
        float sign=right?1:-1,t=f.weight();
        var main=new Joint(f.x(),sign*f.y(),sign*f.z());
        var free=new Joint(-.45f-.17f*Math.abs(f.y()),-sign*f.y()*.20f,-sign*.32f);
        var body=new Joint(.10f+.18f*Math.max(0,-f.x()),sign*f.y()*.35f,sign*f.z()*.20f);
        var rightLeg=new Joint(right?-.28f:.23f,.13f,.11f);
        var leftLeg=new Joint(right?.23f:-.28f,-.13f,-.11f);
        return new Pose(base.body().blend(body,t),
            base.rightArm().blend(right?main:free,t),base.leftArm().blend(right?free:main,t),
            actor.onGround()?base.rightLeg().blend(rightLeg,t*.82f):base.rightLeg(),
            actor.onGround()?base.leftLeg().blend(leftLeg,t*.82f):base.leftLeg(),
            base.bob()*(1-t)+.5f*t,base.hipBack()*(1-t));
    }
}
