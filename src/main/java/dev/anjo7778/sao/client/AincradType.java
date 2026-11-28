package dev.anjo7778.sao.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

/** Local geometric bitmap lettering; vanilla fallback for names outside ASCII. */
final class AincradType {
    private static final ResourceLocation FONT=ResourceLocation.fromNamespaceAndPath("sao","interface");
    private AincradType(){}
    static Component component(String value){return Component.literal(value).withStyle(Style.EMPTY.withFont(FONT));}
    static int width(Font font,String text){return font.width(component(text));}
    static Component fit(Font font,String text,int max){
        if(max<=0)return Component.empty();
        if(width(font,text)<=max)return component(text);
        int end=text.length();
        while(end>0&&width(font,text.substring(0,end)+"...")>max)end=text.offsetByCodePoints(end,-1);
        return component(end==0?"":text.substring(0,end)+"...");
    }
    static void draw(GuiGraphics g,Font font,String text,int x,int y,int color,int max){
        if(max>0)g.drawString(font,fit(font,text,max),x,y,color,false);
    }
}
