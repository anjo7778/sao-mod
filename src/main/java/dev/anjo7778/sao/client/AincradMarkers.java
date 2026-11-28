package dev.anjo7778.sao.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.NpcRoster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Matrix4f;

/** Small, depth-tested overhead cursors. Colors indicate role, not fictional reputation.
 * Only nearby actual rendered entities; no scans of distant chunks, fake levels, or new textures.
 */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class AincradMarkers {
    public static final int ADVENTURER=0xff88d53e,SERVICE=0xffe9bc46,HOSTILE=0xffde6256;
    private static final RenderType TYPE=RenderType.text(AincradMenu.ATLAS);
    private AincradMarkers(){}
    public static int color(NpcRoster.Occupation role){return role==null?0:role==NpcRoster.Occupation.ADVENTURER?ADVENTURER:SERVICE;}
    @SubscribeEvent public static void render(RenderLivingEvent.Post<?,?> event){
        if(!AincradHud.active())return;
        var c=Minecraft.getInstance();var e=event.getEntity();var player=c.player;
        if(player==null||e==player||!e.isAlive()||e.isInvisibleTo(player)||e.distanceToSqr(player)>24*24)return;
        boolean targeted=c.hitResult instanceof EntityHitResult hit&&hit.getEntity()==e;
        int color;
        if(e instanceof CityNpc npc){
            color=color(npc.displayOccupation());
            if(color==0)return;
        }else if(e instanceof Enemy&&targeted)color=HOSTILE;
        else return;
        PoseStack pose=event.getPoseStack();
        pose.pushPose();
        pose.translate(0,e.getBbHeight()+(targeted?.72:.40),0);
        pose.mulPose(c.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(.025f,-.025f,.025f);
        VertexConsumer out=event.getMultiBufferSource().getBuffer(TYPE);
        var m=pose.last().pose();
        diamond(out,m,5.4f,8.4f,0,0xd92b3735);
        diamond(out,m,4.6f,7.4f,.08f,0xffeef7f0);
        diamond(out,m,3.7f,6.3f,.16f,color);
        pose.popPose();
    }
    private static void diamond(VertexConsumer v,Matrix4f m,float w,float h,float z,int c){
        vertex(v,m,0,-h,z,c);vertex(v,m,-w,0,z,c);vertex(v,m,0,h,z,c);vertex(v,m,w,0,z,c);
    }
    private static void vertex(VertexConsumer out,Matrix4f m,float x,float y,float z,int color){
        out.addVertex(m,x,y,z).setColor(color).setUv(28.5f/256,28.5f/256).setLight(LightTexture.FULL_BRIGHT);
    }
}
