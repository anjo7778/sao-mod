package dev.anjo7778.sao.client;

import dev.anjo7778.sao.network.ProfileNetwork;
import dev.anjo7778.sao.simulation.CharacterProgress.Attribute;
import dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.util.Arrays;
import java.util.Locale;

/** One layout, used in first-person fallback and the third-person world panel. */
public final class AincradMenu extends Screen {
    static final int W=368,H=194,SUMMON=10;
    static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("sao","textures/gui/aincrad_icons.png");
    private static final String[][] PAGES={{"Character","Items","Skills","Professions"},{"Party","Guild","Friends"},{"Messages"},{"Map","Quests","Market"},{"Controls"}};
    private static final int[] GROUP_ICON={3,4,5,6,7};
    private static final int[][] PAGE_ICON={{3,8,9,10},{4,12,3},{5},{13,11,14},{7}};
    private static final int INK=0xff45484b,FAINT=0xff737c82,WHITE=0xfffafcfc,PALE=0xeef9fafa,EDGE=0xd9bac1c5,ORANGE=0xffe8a413,GOLD=0xffffd36a,SHADOW=0x35505b61;
    private int group,page,age,slide,scroll;
    private WorldHit worldHit;
    record WorldHit(float left,float top,float sx,float sy,long stamp){
        double x(double x){return (x-left)/sx;}double y(double y){return (y-top)/sy;}
    }
    interface Painter{
        void rect(int x,int y,int w,int h,int color);
        void tile(int tile,int x,int y,int size,int tint);
        void text(String s,int x,int y,int color,int maxWidth);
        int width(String s);
    }
    public AincradMenu(){super(Component.literal("AINCRAD"));MenuPreferences.load();}
    public int age(){return age;}
    public float gesture(float partial){return Math.min(1f,(age+partial)/SUMMON);}
    public boolean summoning(){return age<SUMMON;}
    public boolean useWorld(){return minecraft!=null&&!MenuPreferences.flat&&!minecraft.options.getCameraType().isFirstPerson();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void tick(){
        age++;slide=Math.min(5,slide+1);
        if(age%20==0)ProfileNetwork.request();
        if(minecraft==null||minecraft.player==null||minecraft.level==null||!minecraft.player.isAlive())onClose();
    }
    private float scale(){return Math.max(.1f,Math.min(1.2f,Math.min((width-12f)/W,(height-12f)/H)));}
    private float left(){return (width-W*scale())/2;}
    private float top(){return (height-H*scale())/2;}
    void resetWorld(){worldHit=null;}
    void worldRendered(WorldHit hit){worldHit=hit;}
    public boolean worldActive(){return useWorld()&&worldHit!=null&&System.nanoTime()-worldHit.stamp()<250_000_000L;}
    private double localX(double x){return worldActive()?worldHit.x(x):(x-left())/scale();}
    private double localY(double y){return worldActive()?worldHit.y(y):(y-top())/scale();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(summoning())return;
        if(worldActive()){
            String hint="ESC Close  /  TAB Flat view";
            g.drawString(font,hint,(width-font.width(hint))/2,height-15,0xfff7f7f7,true);return;
        }
        g.pose().pushPose();g.pose().translate(left(),top(),0);g.pose().scale(scale(),scale(),1);
        draw(new Painter(){
            public void rect(int x,int y,int w,int h,int c){if(w>0&&h>0)g.fill(x,y,x+w,y+h,c);}
            public void tile(int t,int x,int y,int size,int c){
                g.setColor(((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f,((c>>>24)&255)/255f);
                g.blit(ATLAS,x,y,size,size,(t%4)*64f,(t/4)*64f,64,64,256,256);g.setColor(1,1,1,1);
            }
            public void text(String s,int x,int y,int c,int max){if(max>0)g.drawString(font,font.plainSubstrByWidth(s,max),x,y,c,false);}
            public int width(String s){return font.width(s);}
        },localX(mx),localY(my));g.pose().popPose();
    }
    private static void panel(Painter p,int x,int y,int w,int h){
        p.rect(x+2,y+2,w,h,SHADOW);p.rect(x,y,w,h,PALE);
        p.rect(x,y,w,1,WHITE);p.rect(x,y+1,1,h-2,WHITE);
        p.rect(x+w-1,y+1,1,h-2,EDGE);p.rect(x,y+h-1,w,1,EDGE);
    }
    private static void center(Painter p,String s,int x,int y,int w,int c){p.text(s,x+Math.max(0,(w-p.width(s))/2),y,c,w);}
    private int subTop(){return Math.max(9,Math.min(H-PAGES[group].length*19-12,23+36*group-9));}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    void draw(Painter p,double mx,double my){
        var s=ProfileNetwork.clientSnapshot();
        boolean real=s!=null&&s.available()&&minecraft!=null&&minecraft.player!=null&&s.owner().equals(minecraft.player.getUUID());
        panel(p,0,51,73,103);p.tile(3,22,57,29,0xff797f83);
        center(p,real?s.name():"AINCRAD",3,89,67,INK);center(p,real?"Lv. "+s.level():"Connecting",3,101,67,FAINT);
        if(real){
            int hp=(int)(61*Math.max(0,Math.min(1,minecraft.player.getHealth()/Math.max(1,minecraft.player.getMaxHealth()))));
            p.rect(6,114,61,5,0xffc4cacc);p.rect(6,114,hp,5,0xff86b64b);
            int xp=(int)(61*Math.max(0,Math.min(1,s.into()/(double)Math.max(1,s.required()))));
            p.rect(6,123,61,4,0xffc4cacc);p.rect(6,123,xp,4,0xff7da6bb);center(p,"Floor "+s.floor(),3,137,67,FAINT);
        }
        p.rect(92,23,1,144,0x80e1e7e9);
        for(int i=0;i<GROUP_ICON.length;i++){
            int cy=23+36*i;double dx=mx-92,dy=my-cy;boolean active=i==group,hover=dx*dx+dy*dy<=16*16;
            p.tile(2,75,cy-17,34,active?GOLD:0xccf5f7f7);
            p.tile(1,78,cy-14,28,active?ORANGE:hover?0xffc6d9dc:0xeef8faf9);
            p.tile(GROUP_ICON[i],81,cy-11,22,active?WHITE:0xff727a7e);
        }
        int sy=subTop(),sx=115;
        for(int i=0;i<PAGES[group].length;i++){
            int y=sy+i*19;boolean active=i==page,hover=inside(mx,my,sx,y,99,17);
            p.rect(sx+2,y+2,99,17,SHADOW);p.rect(sx,y,99,17,active?ORANGE:hover?0xffdce7e7:PALE);
            p.tile(PAGE_ICON[group][i],sx+2,y+1,15,active?WHITE:FAINT);p.text(PAGES[group][i],sx+19,y+4,active?WHITE:INK,75);
            if(active)p.tile(15,sx+87,y+3,11,WHITE);
        }
        int shift=Math.max(0,5-slide)*2,x=221+shift,y=10,w=147-shift;
        panel(p,x,y,w,174);p.rect(x,y,3,20,ORANGE);p.text(PAGES[group][page].toUpperCase(Locale.ROOT),x+8,y+6,INK,w-14);p.rect(x+5,y+19,w-10,1,EDGE);
        if(group==4){settings(p,x,y,w,mx,my);return;}
        if(!real){p.text("Awaiting profile...",x+7,y+32,FAINT,w-14);return;}
        if(group==0&&page==0)character(p,s,x,y,w,mx,my);
        else if(group==0&&(page==2||page==3))mastery(p,s,x,y,w,page==3);
        else{p.text("Not available yet",x+7,y+35,INK,w-14);p.text("In development",x+7,y+49,FAINT,w-14);}
    }
    private void character(Painter p,ProfileNetwork.Snapshot s,int x,int y,int w,double mx,double my){
        p.text("CHARACTER STATUS",x+7,y+25,FAINT,w-14);p.text("Lv. "+s.level(),x+7,y+38,INK,w-14);p.text(s.into()+" / "+s.required()+" XP",x+7,y+49,FAINT,w-14);
        p.rect(x+6,y+61,w-12,1,EDGE);
        for(var a:Attribute.values()){
            int row=y+67+a.ordinal()*12;String v=Long.toString(s.attribute(a));
            p.text(label(a.name()),x+7,row,INK,w-52);p.text(v,Math.max(x+70,x+w-27-p.width(v)),row,INK,30);
            if(s.unspent()>0){int bx=x+w-17;boolean hover=inside(mx,my,bx,row-1,12,11);p.rect(bx,row-1,12,11,hover?ORANGE:0xffd6dfe0);p.text("+",bx+3,row,hover?WHITE:INK,10);}
        }
        p.rect(x+6,y+152,w-12,1,EDGE);p.text(s.unspent()+" unspent points",x+7,y+159,INK,w-14);
    }
    private void mastery(Painter p,ProfileNetwork.Snapshot s,int x,int y,int w,boolean profession){
        var list=Arrays.stream(Mastery.values()).filter(m->(m.ordinal()>=Mastery.BLACKSMITHING.ordinal())==profession).toList();
        scroll=Math.max(0,Math.min(scroll,Math.max(0,list.size()-10)));p.text(profession?"PROFESSION MASTERY":"WEAPON MASTERY",x+7,y+26,FAINT,w-14);
        for(int i=0;i<10&&i+scroll<list.size();i++){
            var m=list.get(i+scroll);int ry=y+40+i*12;
            p.text(label(m.name()),x+7,ry,INK,w-64);String v=s.experience(m)==0?"Untrained":"Lv "+s.mastery(m);
            p.text(v,x+w-7-p.width(v),ry,FAINT,58);if(s.experience(m)>0)p.rect(x+7,ry+10,w-14,1,EDGE);
        }
    }
    private void settings(Painter p,int x,int y,int w,double mx,double my){
        String[] names={"Opening method","Gesture sensitivity","Combat lock","Presentation"};
        String[] values={label(MenuPreferences.mode.name()),label(MenuPreferences.sensitivity.name()),MenuPreferences.combatGuard?"On":"Off",MenuPreferences.flat?"Flat":"Auto / 3D"};
        for(int i=0;i<4;i++){int ry=y+26+i*30;p.text(names[i],x+7,ry,FAINT,w-14);p.rect(x+7,ry+10,w-14,16,inside(mx,my,x+7,ry+10,w-14,16)?ORANGE:0xffdce6e7);center(p,values[i],x+7,ry+14,w-14,INK);}
        p.text("M: default key / ESC",x+7,y+154,FAINT,w-14);
    }
    private static String label(String v){v=v.toLowerCase(Locale.ROOT).replace('_',' ');return Character.toUpperCase(v.charAt(0))+v.substring(1);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0||summoning())return super.mouseClicked(x,y,button);
        double mx=localX(x),my=localY(y);
        for(int i=0;i<GROUP_ICON.length;i++){double dx=mx-92,dy=my-(23+36*i);if(dx*dx+dy*dy<=17*17){if(group!=i){group=i;page=scroll=slide=0;}return true;}}
        for(int i=0;i<PAGES[group].length;i++)if(inside(mx,my,115,subTop()+i*19,99,17)){if(page!=i){page=i;scroll=slide=0;}return true;}
        if(slide<5)return false;
        if(group==4)for(int i=0;i<4;i++)if(inside(mx,my,228,46+i*30,133,16)){MenuPreferences.cycle(i);resetWorld();return true;}
        var s=ProfileNetwork.clientSnapshot();
        if(group==0&&page==0&&s!=null&&s.available()&&s.unspent()>0)for(var a:Attribute.values())if(inside(mx,my,351,76+a.ordinal()*12,12,11)){ProfileNetwork.allocate(a);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(group==0&&(page==2||page==3)){scroll=Math.max(0,scroll+(sy<0?1:sy>0?-1:0));return true;}return super.mouseScrolled(mx,my,sx,sy);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_TAB){MenuPreferences.cycle(3);resetWorld();return true;}return super.keyPressed(key,scan,mods);}
}
