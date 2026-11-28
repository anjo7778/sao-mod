package dev.anjo7778.sao.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.ChatFormatting;

/** Original redraw of the user-approved Aincrad health gauge. A neutral fill mask
 * and a separate frame keep the gauge shape identical at every health fraction.
 * All displayed values come from the live character rather than the reference image.
 */
final class AincradHealthGauge {
    static final int WIDTH=400,HEIGHT=64;
    private static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath("sao","textures/gui/health_gauge.png");
    private AincradHealthGauge(){}
    static int width(int screenWidth){return Math.min(264,Math.max(104,Math.round(screenWidth*.54f)));}
    static int height(int width){return (int)Math.ceil(width*HEIGHT/(float)WIDTH);}
    static void draw(GuiGraphics g,int x,int y,int width,String name,long level,String hp,float fraction,float trail,float absorption){
        float scale=width/(float)WIDTH;
        g.pose().pushPose();
        g.pose().translate(x,y,0);g.pose().scale(scale,scale,1);
        g.setColor(1,1,1,1);
        g.blit(TEXTURE,0,0,WIDTH,HEIGHT,0f,0f,WIDTH,HEIGHT,WIDTH,HEIGHT*2);
        fill(g,trail,0xffc9a039);
        fill(g,fraction,AincradHud.healthColor(fraction));
        if(absorption>0){
            // Additional protection is identified separately; never misrepresent it as base HP.
            g.fill(84,37,84+Math.round(142*Math.min(1,absorption)),39,0xffedbd4c);
        }
        label(g,name,33,14,45,false);
        label(g,hp,244,41,91,true);
        label(g,"LV. "+level,342,41,43,true);
        g.setColor(1,1,1,1);g.pose().popPose();
    }
    private static void fill(GuiGraphics g,float ratio,int tint){
        if(ratio<=0)return;
        int end=Math.min(WIDTH,84+(int)Math.ceil(290*Math.max(0,Math.min(1,ratio))));
        g.setColor(((tint>>>16)&255)/255f,((tint>>>8)&255)/255f,(tint&255)/255f,1f);
        g.blit(TEXTURE,0,0,end,HEIGHT,0f,(float)HEIGHT,end,HEIGHT,WIDTH,HEIGHT*2);
        g.setColor(1,1,1,1);
    }
    private static void label(GuiGraphics g,String value,int x,int y,int max,boolean centered){
        var font=Minecraft.getInstance().font;
        var text=AincradType.component(value).withStyle(ChatFormatting.BOLD);
        int natural=Math.max(1,font.width(text));
        float sx=Math.min(.94f,max/(float)natural);
        // Tall lettering and independently fitted width: no truncation of numerical HP.
        float actual=natural*sx;
        g.pose().pushPose();g.pose().translate(x+(centered?(max-actual)/2:0),y,0);g.pose().scale(sx,1.65f,1);
        g.drawString(font,text,0,0,0xfffcfcfc,false);
        g.pose().popPose();
    }
}
