package dev.anjo7778.sao.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.anjo7778.sao.network.ProfileNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/** Provisional mouse chord + downward movement. Vanilla input remains intact outside Aincrad. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class AincradMenuControls {
    private static final KeyMapping MENU=new KeyMapping("key.sao.menu",InputConstants.Type.KEYSYM,GLFW.GLFW_KEY_M,"key.categories.sao");
    private static ClientLevel lastLevel;
    private static int ticks,armedAt;
    private static boolean armed,latched;
    private static double startX,startY;
    private static float startPitch;
    private AincradMenuControls(){}
    @EventBusSubscriber(modid="sao",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event){event.register(MENU);}
    }
    private static boolean allowed(Minecraft c){
        var s=ProfileNetwork.clientSnapshot();
        return c.player!=null&&c.level!=null&&s!=null&&s.available()&&s.owner().equals(c.player.getUUID());
    }
    private static boolean chord(Minecraft c){
        long window=c.getWindow().getWindow();
        return GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_LEFT)==GLFW.GLFW_PRESS
            &&GLFW.glfwGetMouseButton(window,GLFW.GLFW_MOUSE_BUTTON_RIGHT)==GLFW.GLFW_PRESS;
    }
    private static void open(Minecraft c){
        latched=true;armed=false;
        c.setScreen(new AincradMenu());
        ProfileNetwork.request();
    }
    @SubscribeEvent public static void interaction(InputEvent.InteractionKeyMappingTriggered event){
        var c=Minecraft.getInstance();
        if(c.screen==null&&allowed(c)&&chord(c)&&(event.isAttack()||event.isUseItem())){
            event.setCanceled(true);event.setSwingHand(false);
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        var c=Minecraft.getInstance();ticks++;
        if(c.level!=lastLevel){
            lastLevel=c.level;ProfileNetwork.clearClient();armed=false;latched=false;
            ticks=0;
        }
        if(c.player==null||c.level==null||c.getConnection()==null)return;
        if(ticks%80==0||ticks==1||ProfileNetwork.clientSnapshot()==null&&ticks%20==0)ProfileNetwork.request();
        boolean pressed=MENU.consumeClick();while(MENU.consumeClick())pressed=true;
        boolean both=chord(c);
        if(!both){armed=false;latched=false;}
        if(c.screen!=null){armed=false;if(both)latched=true;return;}
        if(!allowed(c)){armed=false;return;}
        if(pressed){open(c);return;}
        if(!both||latched)return;
        if(!armed){
            armed=true;armedAt=ticks;startX=c.mouseHandler.xpos();startY=c.mouseHandler.ypos();startPitch=c.player.getXRot();return;
        }
        if(ticks-armedAt>20){armed=false;latched=true;return;}
        double dx=c.mouseHandler.xpos()-startX,dy=c.mouseHandler.ypos()-startY;
        if(dy>=42&&Math.abs(dx)<=dy*.8||c.player.getXRot()-startPitch>=12&&Math.abs(dx)<=65)open(c);
    }
}
