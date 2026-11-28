package dev.anjo7778.sao.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anjo7778.sao.client.AincradFingerRig;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Use the exact skin consumer and already selected arm transform, not a second character model. */
@Mixin(ModelPart.class)
public abstract class ModelPartGestureMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V",at=@At("TAIL"))
    private void sao$fingers(PoseStack pose,VertexConsumer out,int light,int overlay,int color,CallbackInfo ci){
        AincradFingerRig.render((ModelPart)(Object)this,pose,out,light,overlay,color);
    }
}
