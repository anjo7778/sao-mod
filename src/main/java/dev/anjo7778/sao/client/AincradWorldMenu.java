package dev.anjo7778.sao.client;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.NpcMenuReview;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/** Bounded observer-only NPC menu geometry. The owner always uses the full local screen. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class AincradWorldMenu {
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
    private static final RenderType ICONS=RenderType.textSeeThrough(AincradMenu.ATLAS);
    private AincradWorldMenu(){}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_LEVEL)return;
        var c=Minecraft.getInstance();MenuPreferences.load();
        if(!MenuPreferences.npcMenus||c.player==null||c.level==null||c.options.hideGui)return;
        var camera=event.getCamera();var cam=camera.getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var visible=new java.util.ArrayList<CityNpc>();
        for(var e:c.level.entitiesForRendering())if(e instanceof CityNpc npc&&npc.isAlive()&&npc.menuAge()>=NpcMenuReview.SUMMON
            &&npc.distanceToSqr(cam.x,cam.y,cam.z)<=18*18&&!npc.isInvisibleTo(c.player))visible.add(npc);
        visible.sort(java.util.Comparator.comparingDouble(n->n.distanceToSqr(cam.x,cam.y,cam.z)));
        if(visible.isEmpty())return;
        c.getMainRenderTarget().bindWrite(false);RenderSystem.setShaderColor(1,1,1,1);
        var inverse=new Matrix4f(RenderSystem.getModelViewMatrix()).invert();
        int drawn=0;
        for(var npc:visible){
            if(drawn>=4)break;
            int age=npc.menuAge();var kind=NpcMenuReview.kind(npc.menuSession());if(kind==null)continue;
            float reveal=NpcMenuReview.reveal(age);if(reveal<=0)continue;
            double yaw=Math.toRadians(Mth.rotLerp(partial,npc.yBodyRotO,npc.yBodyRot));
            double px=Mth.lerp(partial,npc.xOld,npc.getX()),py=Mth.lerp(partial,npc.yOld,npc.getY()),pz=Mth.lerp(partial,npc.zOld,npc.getZ());
            // Front-of-body anchor with modest side offset; billboard for readable observers.
            double ax=px-Math.sin(yaw)*.85-Math.cos(yaw)*.42,ay=py+1.45,az=pz+Math.cos(yaw)*.85-Math.sin(yaw)*.42;
            var anchor=new net.minecraft.world.phys.Vec3(ax,ay,az);
            if(c.level.clip(new net.minecraft.world.level.ClipContext(cam,anchor,net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE,c.player)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            float scale=.0045f*reveal;
            var matrix=new Matrix4f(event.getModelViewMatrix()).translate((float)(ax-cam.x),(float)(ay-cam.y),(float)(az-cam.z))
                .rotate(camera.rotation()).scale(scale,-scale,scale).translate(-AincradMenu.W/2f,-AincradMenu.H/2f,0);
            var clip=new Matrix4f(event.getProjectionMatrix()).mul(matrix);
            var point=clip.transform(new Vector4f(AincradMenu.W/2f,AincradMenu.H/2f,0,1));
            if(point.w<=.05f||Math.abs(point.x/point.w)>1.3||Math.abs(point.y/point.w)>1.3)continue;
            var submitted=new Matrix4f(inverse).mul(matrix);
            AincradMenu.drawNpc(new WorldPainter(c.font,submitted),npc,kind);BUFFERS.endBatch();drawn++;
        }
    }
    private static final class WorldPainter implements AincradMenu.Painter{
        private final Font font;private final Matrix4f matrix;private int layer;
        WorldPainter(Font f,Matrix4f m){font=f;matrix=m;}
        private float z(){return ++layer*.08f;}
        public void rect(int x,int y,int w,int h,int c){if(w>0&&h>0)quad(x,y,w,h,c,28f/256,28f/256,29f/256,29f/256);}
        public void tile(int t,int x,int y,int size,int c){float u=t%4*64f/256,v=t/4*64f/256;quad(x,y,size,size,c,u,v,u+.25f,v+.25f);}
        private void quad(float x,float y,float w,float h,int c,float u0,float v0,float u1,float v1){
            var out=BUFFERS.getBuffer(ICONS);float z=z();vertex(out,x,y+h,z,u0,v1,c);vertex(out,x+w,y+h,z,u1,v1,c);vertex(out,x+w,y,z,u1,v0,c);vertex(out,x,y,z,u0,v0,c);
        }
        private void vertex(VertexConsumer out,float x,float y,float z,float u,float v,int c){out.addVertex(matrix,x,y,z).setColor(c).setUv(u,v).setLight(LightTexture.FULL_BRIGHT);}
        public void text(String s,int x,int y,int c,int max){if(max>0)font.drawInBatch(AincradType.fit(font,s,max),x,y,c,false,new Matrix4f(matrix).translate(0,0,z()),BUFFERS,Font.DisplayMode.SEE_THROUGH,0,LightTexture.FULL_BRIGHT);}
        public int width(String s){return AincradType.width(font,s);}
    }
}
