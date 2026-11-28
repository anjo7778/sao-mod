package dev.anjo7778.sao.client.mixin;
import dev.anjo7778.sao.client.MenuGesture;
import dev.anjo7778.sao.client.AincradFingerRig;
import dev.anjo7778.sao.client.MenuPreferences;
import dev.anjo7778.sao.simulation.NpcMenuReview;
import net.minecraft.world.entity.HumanoidArm;
import dev.anjo7778.sao.entity.CityNpc;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(HumanoidModel.class)
public abstract class HumanoidGestureMixin {
    @Inject(method="setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",at=@At("TAIL"))
    private void sao$gesture(LivingEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        if(!((Object)this instanceof PlayerModel<?> model))return;
        AincradFingerRig.clear(model);
        float partial=Math.max(0,Math.min(1,age-entity.tickCount));
        HumanoidArm hand=MenuGesture.freeArm(entity);
        if(hand==null)return;
        float pitchValue,extension;
        if(entity instanceof CityNpc npc&&npc.menuOpen()&&MenuPreferences.npcMenus){
            pitchValue=MenuGesture.npcPitch(npc,partial);
            if(!Float.isFinite(pitchValue))return;
            int elapsed=npc.menuAge();
            if(elapsed<NpcMenuReview.SUMMON)extension=Math.min(1,(elapsed+partial)/(NpcMenuReview.SUMMON*.26f));
            else if(elapsed>=NpcMenuReview.SUMMON+NpcMenuReview.HOLD)
                extension=Math.max(0,(NpcMenuReview.DURATION-elapsed-partial)/NpcMenuReview.CLOSE);
            else extension=1;
        }else if(entity==Minecraft.getInstance().player){
            float t=MenuGesture.progress(partial);if(t<0)return;
            pitchValue=MenuGesture.armPitch(t);extension=AincradFingerRig.extension(t);
        }else return;
        boolean right=hand==HumanoidArm.RIGHT;
        var arm=right?model.rightArm:model.leftArm;
        arm.xRot=pitchValue;arm.yRot=right?-.17f:.17f;arm.zRot=right?.14f:-.14f;
        AincradFingerRig.arm(model,entity,hand,extension);
    }
}
