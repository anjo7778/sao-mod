package dev.anjo7778.sao.client;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
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
import org.joml.Vector3f;
import org.joml.Vector4f;

/** Translucent world geometry anchored near the player's forward shoulder; real projected pointer mapping. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class AincradWorldMenu {
    private static final MultiBufferSource.BufferSource BUFFERS=MultiBufferSource.immediate(new ByteBufferBuilder(65536));
    private static final RenderType ICONS=RenderType.text(AincradMenu.ATLAS);
    private AincradWorldMenu(){}
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var c=Minecraft.getInstance();
        if(!(c.screen instanceof AincradMenu menu)||c.player==null)return;
        menu.resetWorld();if(!menu.useWorld()||menu.summoning())return;
        var player=c.player;var camera=event.getCamera();var cam=camera.getPosition();
        float partial=event.getPartialTick().getGameTimeDeltaPartialTick(false);
        var right=new Vector3f(1,0,0).rotate(camera.rotation());
        double yaw=Math.toRadians(Mth.rotLerp(partial,player.yBodyRotO,player.yBodyRot));
        double px=Mth.lerp(partial,player.xOld,player.getX()),py=Mth.lerp(partial,player.yOld,player.getY()),pz=Mth.lerp(partial,player.zOld,player.getZ());
        double ax=px-Math.sin(yaw)*.8+right.x*1.35,ay=py+1.42,az=pz+Math.cos(yaw)*.8+right.z*1.35;
        double distance=Math.sqrt((ax-cam.x)*(ax-cam.x)+(ay-cam.y)*(ay-cam.y)+(az-cam.z)*(az-cam.z));
        float scale=(float)Math.max(.004,Math.min(.012,distance*.0022));
        var matrix=new Matrix4f(event.getModelViewMatrix()).translate((float)(ax-cam.x),(float)(ay-cam.y),(float)(az-cam.z))
            .rotate(camera.rotation()).scale(scale,-scale,scale).translate(-AincradMenu.W/2f,-AincradMenu.H/2f,0);
        var clip=new Matrix4f(event.getProjectionMatrix()).mul(matrix);
        var a=project(clip,0,0,c);var b=project(clip,AincradMenu.W,AincradMenu.H,c);
        if(a==null||b==null||b[0]-a[0]<70||b[1]-a[1]<38||a[0]<2||a[1]<2||b[0]>c.getWindow().getGuiScaledWidth()-2||b[1]>c.getWindow().getGuiScaledHeight()-2)return;
        var hit=new AincradMenu.WorldHit(a[0],a[1],(b[0]-a[0])/AincradMenu.W,(b[1]-a[1])/AincradMenu.H,System.nanoTime());
        double mx=c.mouseHandler.xpos()*c.getWindow().getGuiScaledWidth()/c.getWindow().getScreenWidth();
        double my=c.mouseHandler.ypos()*c.getWindow().getGuiScaledHeight()/c.getWindow().getScreenHeight();
        menu.draw(new WorldPainter(c.font,matrix),hit.x(mx),hit.y(my));BUFFERS.endBatch();menu.worldRendered(hit);
    }
    private static float[] project(Matrix4f clip,float x,float y,Minecraft c){
        var v=clip.transform(new Vector4f(x,y,0,1));if(v.w<=.02f||!Float.isFinite(v.w))return null;
        return new float[]{(v.x/v.w+1)*.5f*c.getWindow().getGuiScaledWidth(),(1-v.y/v.w)*.5f*c.getWindow().getGuiScaledHeight()};
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
        public void text(String s,int x,int y,int c,int max){if(max>0)font.drawInBatch(font.plainSubstrByWidth(s,max),x,y,c,false,new Matrix4f(matrix).translate(0,0,z()),BUFFERS,Font.DisplayMode.NORMAL,0,LightTexture.FULL_BRIGHT);}
        public int width(String s){return font.width(s);}
    }
}
