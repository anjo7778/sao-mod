package dev.anjo7778.sao.client;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.NpcMenuReview;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/** Shared raise/downstroke timeline, without fake finger joints on cuboid arms. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class MenuGesture {
    private MenuGesture(){}
    public static float progress(float partial){return Minecraft.getInstance().screen instanceof AincradMenu m&&m.summoning()?m.gesture(partial):-1;}
    public static float npcPitch(CityNpc npc,float partial){
        int age=npc.menuAge();if(age<0)return Float.NaN;
        if(age<NpcMenuReview.SUMMON){
            float t=(age+partial)/NpcMenuReview.SUMMON;
            if(t<.78f)return armPitch(t);
            return -.55f-.27f*smooth((t-.78f)/.22f);
        }
        if(age>=NpcMenuReview.SUMMON+NpcMenuReview.HOLD)
            return -.82f*smooth((NpcMenuReview.DURATION-age-partial)/NpcMenuReview.CLOSE);
        float hold=(age+partial-NpcMenuReview.SUMMON)/NpcMenuReview.HOLD;
        // Gentle read/selection motion; the review is still of the one committed action.
        return -.82f-.16f*(float)Math.sin(hold*Math.PI*2)*smooth(Math.min(hold,1-hold)*4);
    }
    private static float smooth(float t){t=Math.max(0,Math.min(1,t));return t*t*(3-2*t);}
    public static float armPitch(float t){
        if(t<.35f)return -1.9f*smooth(t/.35f);
        if(t<.78f)return -1.9f+1.35f*smooth((t-.35f)/.43f);
        return -.55f*(1-smooth((t-.78f)/.22f));
    }
    @SubscribeEvent public static void hand(RenderHandEvent event){
        var c=Minecraft.getInstance();float t=progress(event.getPartialTick());
        if(t<0||c.player==null||!c.options.getCameraType().isFirstPerson())return;
        boolean right=event.getHand()==InteractionHand.MAIN_HAND?c.player.getMainArm()==HumanoidArm.RIGHT:c.player.getMainArm()==HumanoidArm.LEFT;
        if(!right||!(c.getEntityRenderDispatcher().getRenderer(c.player) instanceof PlayerRenderer renderer))return;
        event.setCanceled(true);var pose=event.getPoseStack();pose.pushPose();
        float lift=-armPitch(t)/1.9f;pose.translate(.54,-.82+lift*.62,-.98);
        pose.mulPose(Axis.YP.rotationDegrees(35));pose.mulPose(Axis.ZP.rotationDegrees(-22-lift*20));pose.mulPose(Axis.XP.rotationDegrees(-20-lift*48));
        renderer.renderRightHand(pose,event.getMultiBufferSource(),event.getPackedLight(),c.player);pose.popPose();
    }
}
