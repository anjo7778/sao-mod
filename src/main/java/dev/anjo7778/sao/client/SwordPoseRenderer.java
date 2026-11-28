package dev.anjo7778.sao.client;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.SwordPose;
import dev.anjo7778.sao.simulation.WeaponRules;
import dev.anjo7778.sao.world.WeaponItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
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
    public static boolean apply(PlayerModel<?> model,LivingEntity actor,float partial){
        var f=frame(actor,partial);
        if(f==null||f.weight()<=0)return false;
        boolean right=actor.getMainArm()==HumanoidArm.RIGHT;
        float sign=right?1:-1;
        var arm=right?model.rightArm:model.leftArm;
        arm.xRot=blend(arm.xRot,f.x(),f.weight());
        arm.yRot=blend(arm.yRot,sign*f.y(),f.weight());
        arm.zRot=blend(arm.zRot,sign*f.z(),f.weight());
        // PlayerModel copies its sleeve from the arm after super.setupAnim.
        return true;
    }
    private static float blend(float from,float to,float amount){return from+(to-from)*amount;}
}
