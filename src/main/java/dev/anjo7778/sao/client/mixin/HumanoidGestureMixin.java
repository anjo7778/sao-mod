package dev.anjo7778.sao.client.mixin;
import dev.anjo7778.sao.client.MenuGesture;
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
        float partial=Math.max(0,Math.min(1,age-entity.tickCount));
        if(entity instanceof CityNpc npc&&npc.menuOpen()){
            float pitchValue=MenuGesture.npcPitch(npc,partial);
            if(!Float.isFinite(pitchValue))return;
            // Prefer the empty hand rather than pretending a held weapon is unequipped.
            boolean left=!npc.getMainHandItem().isEmpty()&&npc.getOffhandItem().isEmpty();
            var arm=left?model.leftArm:model.rightArm;
            arm.xRot=pitchValue;arm.yRot=left?.17f:-.17f;arm.zRot=left?-.14f:.14f;
        }else if(entity==Minecraft.getInstance().player){
            float t=MenuGesture.progress(partial);if(t<0)return;
            model.rightArm.xRot=MenuGesture.armPitch(t);model.rightArm.yRot=-.17f;model.rightArm.zRot=.14f;
        }
    }
}
