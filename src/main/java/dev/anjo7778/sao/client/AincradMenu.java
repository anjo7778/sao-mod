package dev.anjo7778.sao.client;

import dev.anjo7778.sao.network.ProfileNetwork;
import dev.anjo7778.sao.simulation.CharacterProgress.Attribute;
import dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Arrays;
import java.util.Locale;

/** First functional native interface. Screen-space prototype before world-space projection. */
public final class AincradMenu extends Screen {
    private static final String[] CATEGORIES={"Character","Inventory","Weapon skills","Professions","Quests","Party","Guild","World map","Friends","Marketplace","Settings"};
    private static final String[] ICONS={"C","I","W","P","Q","T","G","M","F","$","S"};
    private static final int INK=0xff354955,FAINT=0xff657985,PANEL=0xeaf6fbfc,EDGE=0xafa8c6d2,ORANGE=0xffef9f42,WHITE=0xfff8ffff;
    private int selected,age,scroll;
    private int panelX,panelY,panelW,panelH,iconX,iconY,step,radius,attrTop,attrStep;
    public AincradMenu(){super(Component.literal("AINCRAD"));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void tick(){
        age++;if(age%20==0)ProfileNetwork.request();
        if(minecraft==null||minecraft.player==null||minecraft.level==null)onClose();
    }
    private void layout(){
        panelW=Math.min(260,Math.max(108,width-70));
        panelH=Math.min(226,Math.max(108,height-18));
        panelX=(width-panelW)/2+18;panelY=(height-panelH)/2;
        iconX=panelX-22;step=Math.max(10,Math.min(20,(height-16)/CATEGORIES.length));radius=Math.max(4,Math.min(8,step/2-1));
        iconY=(height-(CATEGORIES.length-1)*step)/2;
        attrTop=panelY+67;attrStep=Math.max(7,Math.min(17,(panelH-78)/Attribute.values().length));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        layout();
        int reveal=Math.max(0,12-age*2);
        int x=panelX+reveal,y=panelY;
        g.fill(x+3,y+4,x+panelW+3,y+panelH+4,0x300b1c29);
        g.fill(x,y,x+panelW,y+panelH,PANEL);
        g.fill(x,y,x+panelW,y+1,WHITE);
        g.fill(x,y+25,x+panelW,y+26,EDGE);
        g.fill(x,y+panelH-16,x+panelW,y+panelH-15,EDGE);
        g.fill(x,y,x+3,y+25,ORANGE);
        text(g,CATEGORIES[selected].toUpperCase(Locale.ROOT),x+11,y+8,INK,panelW-23);
        text(g,"ESC  CLOSE",x+8,y+panelH-11,FAINT,panelW-16);
        for(int i=0;i<CATEGORIES.length;i++){
            int cy=iconY+i*step;
            boolean hover=Math.abs(mx-iconX)<=radius+2&&Math.abs(my-cy)<=radius+2;
            int bg=i==selected?ORANGE:hover?0xffc8e0e9:0xdfeff7fa;
            circle(g,iconX,cy,radius+1,EDGE);circle(g,iconX,cy,radius,bg);
            String symbol=ICONS[i];g.drawString(font,symbol,iconX-font.width(symbol)/2,cy-4,i==selected?WHITE:INK,false);
            if(hover&&age>=6)g.renderTooltip(font,Component.literal(CATEGORIES[i]),mx,my);
        }
        var s=ProfileNetwork.clientSnapshot();
        if(s==null||!s.available()||minecraft==null||minecraft.player==null||!s.owner().equals(minecraft.player.getUUID())){
            text(g,"Awaiting server profile...",x+12,y+38,FAINT,panelW-24);return;
        }
        if(selected==0)character(g,s,x,y,mx,my);
        else if(selected==2||selected==3)mastery(g,s,x,y,selected==3);
        else{
            text(g,"Not available yet",x+12,y+43,INK,panelW-24);
            text(g,"This system is still in development.",x+12,y+59,FAINT,panelW-24);
        }
    }
    private void character(GuiGraphics g,ProfileNetwork.Snapshot s,int x,int y,int mx,int my){
        text(g,s.name()+"  |  LV "+s.level(),x+12,y+32,INK,panelW-24);
        int bar=panelW-24;
        g.fill(x+12,y+46,x+12+bar,y+51,0xffd0e1e7);
        int progress=(int)Math.round(bar*Math.max(0,Math.min(1,s.into()/(double)Math.max(1,s.required()))));
        g.fill(x+12,y+46,x+12+progress,y+51,0xff78aeba);
        text(g,s.into()+" / "+s.required()+" XP  •  Floor "+s.floor(),x+12,y+54,FAINT,panelW-24);
        for(var a:Attribute.values()){
            int row=attrTop+a.ordinal()*attrStep;
            if(row+8>=y+panelH-18)break;
            String name=label(a.name());
            text(g,name,x+12,row,INK,panelW/2);
            String value=Long.toString(s.attribute(a));
            int right=x+panelW-30;
            text(g,value,Math.max(x+panelW/2,right-font.width(value)),row,INK,panelW/2-20);
            if(s.unspent()>0&&attrStep>=10){
                int bx=x+panelW-24;
                boolean hover=mx>=bx&&mx<bx+14&&my>=row-1&&my<row+10;
                g.fill(bx,row-1,bx+14,row+10,hover?ORANGE:0xffd7e7ed);
                g.drawString(font,"+",bx+4,row,hover?WHITE:INK,false);
            }
        }
        if(s.unspent()>0&&panelW>150){
            String p=s.unspent()+" points";
            text(g,p,x+panelW-8-font.width(p),y+panelH-11,INK,panelW-16);
        }
    }
    private void mastery(GuiGraphics g,ProfileNetwork.Snapshot s,int x,int y,boolean profession){
        var list=Arrays.stream(Mastery.values()).filter(m->(m.ordinal()>=Mastery.BLACKSMITHING.ordinal())==profession).toList();
        int rows=Math.max(1,(panelH-54)/16);
        scroll=Math.max(0,Math.min(scroll,Math.max(0,list.size()-rows)));
        text(g,profession?"PROFESSION MASTERY":"WEAPON MASTERY",x+12,y+33,FAINT,panelW-24);
        for(int i=0;i<rows&&i+scroll<list.size();i++){
            var m=list.get(i+scroll);int ry=y+47+i*16;
            if(ry+10>=y+panelH-16)break;
            text(g,label(m.name()),x+12,ry,INK,panelW-94);
            String value=s.experience(m)==0?"Untrained":"Lv "+s.mastery(m);
            text(g,value,x+panelW-12-font.width(value),ry,FAINT,80);
            if(s.experience(m)>0)g.fill(x+12,ry+10,x+panelW-12,ry+11,EDGE);
        }
    }
    private static String label(String value){
        String v=value.toLowerCase(Locale.ROOT).replace('_',' ');return Character.toUpperCase(v.charAt(0))+v.substring(1);
    }
    private void text(GuiGraphics g,String text,int x,int y,int color,int max){
        if(max<=0)return;
        if(font.width(text)>max)text=font.plainSubstrByWidth(text,max);
        g.drawString(font,text,x,y,color,false);
    }
    private static void circle(GuiGraphics g,int x,int y,int r,int color){
        for(int dy=-r;dy<=r;dy++){
            int dx=(int)Math.sqrt(r*r-dy*dy);
            g.fill(x-dx,y+dy,x+dx+1,y+dy+1,color);
        }
    }
    @Override public boolean mouseClicked(double mx,double my,int button){
        if(button!=0||age<6)return super.mouseClicked(mx,my,button);
        layout();
        for(int i=0;i<CATEGORIES.length;i++){
            int cy=iconY+i*step;double dx=mx-iconX,dy=my-cy;
            if(dx*dx+dy*dy<=(radius+2)*(radius+2)){selected=i;scroll=0;return true;}
        }
        var s=ProfileNetwork.clientSnapshot();
        if(selected==0&&s!=null&&s.available()&&s.unspent()>0&&attrStep>=10&&mx>=panelX+panelW-24&&mx<panelX+panelW-10){
            for(var a:Attribute.values()){
                int y=attrTop+a.ordinal()*attrStep;
                if(y+8<panelY+panelH-18&&my>=y-1&&my<y+10){ProfileNetwork.allocate(a);return true;}
            }
        }
        return super.mouseClicked(mx,my,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){
        if(selected==2||selected==3){scroll=Math.max(0,scroll+(sy<0?1:sy>0?-1:0));return true;}
        return super.mouseScrolled(mx,my,sx,sy);
    }
}
