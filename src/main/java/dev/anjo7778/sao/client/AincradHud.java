package dev.anjo7778.sao.client;

import dev.anjo7778.sao.network.ProfileNetwork;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.NpcRoster;
import net.minecraft.client.Minecraft;
import net.minecraft.client.AttackIndicatorStatus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import java.util.Locale;

/** Replaces only the explicitly handled vanilla layers in an eligible Aincrad survival view.
 * Uses synchronized real HP, food, air, inventory and RPG XP. No stamina/mana placeholder.
 */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class AincradHud {
    static final int WHITE=0xfff6faf9,INK=0xff3c4b50,GRAY=0xff64747b,EDGE=0xffa5b7bc,PALE=0xe8edf4f3,
        DARK=0xd936474e,AMBER=0xffeda726,GREEN=0xff86cb18,YELLOW=0xffe8c451,RED=0xffd65a55,BLUE=0xff70b2cd;
    private static Object priorPlayer,priorLevel;
    private static long seenXp=-1,seenLevel=-1,noticeUntil,lastFrame;
    private static String notice="";
    private static float hpTrail=1;
    private AincradHud(){}
    public static boolean active(){
        var c=Minecraft.getInstance();var p=c.player;var s=ProfileNetwork.clientSnapshot();
        return MenuPreferences.hud&&p!=null&&c.level!=null&&s!=null&&s.available()&&s.owner().equals(p.getUUID())
            &&!p.isCreative()&&!p.isSpectator()&&!p.isPassenger()&&!c.options.hideGui
            &&c.getCameraEntity()==p;
    }
    @SubscribeEvent public static void replace(RenderGuiLayerEvent.Pre e){
        if(!active())return;
        var n=e.getName();
        if(n.equals(VanillaGuiLayers.PLAYER_HEALTH)||n.equals(VanillaGuiLayers.ARMOR_LEVEL)
            ||n.equals(VanillaGuiLayers.FOOD_LEVEL)||n.equals(VanillaGuiLayers.AIR_LEVEL)
            ||n.equals(VanillaGuiLayers.EXPERIENCE_BAR)||n.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)
            ||n.equals(VanillaGuiLayers.HOTBAR))e.setCanceled(true);
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post e){
        if(!active())return;
        var c=Minecraft.getInstance();var p=c.player;var s=ProfileNetwork.clientSnapshot();var g=e.getGuiGraphics();
        int width=g.guiWidth(),height=g.guiHeight();long now=System.nanoTime();
        if(priorPlayer!=p||priorLevel!=c.level){priorPlayer=p;priorLevel=c.level;seenXp=s.xp();seenLevel=s.level();hpTrail=ratio(p.getHealth(),p.getMaxHealth());noticeUntil=0;lastFrame=now;}
        double dt=Math.min(.1,Math.max(0,(now-lastFrame)/1e9));lastFrame=now;
        float hp=ratio(p.getHealth(),p.getMaxHealth());
        hpTrail=hp>=hpTrail?hp:Math.max(hp,hpTrail-(float)(dt*.55));
        if(s.xp()>seenXp){
            if(s.level()>seenLevel)AincradSounds.play(AincradSounds.Cue.LEVEL_UP);
            notice=s.level()>seenLevel?"LEVEL UP  /  Lv. "+s.level():"+"+(s.xp()-seenXp)+" XP";
            noticeUntil=now+3_000_000_000L;
        }
        seenXp=s.xp();seenLevel=s.level();
        int x=9,y=8,w=AincradHealthGauge.width(width);
        String value=number(p.getHealth())+"/"+number(p.getMaxHealth());
        AincradHealthGauge.draw(g,x,y,w,s.name(),s.level(),value,hp,hpTrail,ratio(p.getAbsorptionAmount(),p.getMaxHealth()));
        int info=y+AincradHealthGauge.height(w)+4;
        if(p.getAbsorptionAmount()>0){
            small(g,x,info,Math.min(w,150),"ABSORB","+"+number(p.getAbsorptionAmount()),ratio(p.getAbsorptionAmount(),p.getMaxHealth()),AMBER);info+=16;
        }
        w=Math.min(w,150);
        int food=p.getFoodData().getFoodLevel();
        if(food<20){
            small(g,x,info,w,"HUNGER",food+"/20",ratio(food,20),food<=6?RED:AMBER);info+=16;
        }
        if(p.getAirSupply()<p.getMaxAirSupply()){
            small(g,x,info,w,"AIR",Math.max(0,p.getAirSupply())+"/"+p.getMaxAirSupply(),ratio(p.getAirSupply(),p.getMaxAirSupply()),BLUE);info+=16;
        }
        if(now<noticeUntil){
            int n=Math.min(w,AincradType.width(c.font,notice)+12);chamfer(g,x,info,n,13,4,DARK);text(g,notice,x+5,info+2,WHITE,n-10);
        }
        if(!(c.screen instanceof AincradMenu)){
            hotbar(g,width,height,p);
            if(c.hitResult instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target
                &&(target instanceof Enemy||target instanceof CityNpc)&&target.isAlive()){
                int tw=Math.min(150,Math.max(78,width/3)),tx=width-tw-9,ty=36;
                boolean resident=target instanceof CityNpc npc&&npc.displayOccupation()!=null;
                int ph=resident?34:25;
                chamfer(g,tx,ty,tw,ph,5,PALE);
                String name=target.getDisplayName().getString();
                if(target instanceof CityNpc npc&&npc.displayLevel()>0){
                    String lv="Lv. "+npc.displayLevel();
                    int available=Math.min(55,AincradType.width(c.font,lv)+6);
                    text(g,name,tx+5,ty+2,INK,tw-12-available);
                    right(g,lv,tx+tw-7,ty+2,INK,available);
                }else text(g,name,tx+5,ty+2,INK,tw-10);
                if(resident){
                    var occupation=((CityNpc)target).displayOccupation();
                    String role=occupation.name().toLowerCase(Locale.ROOT).replace('_',' ');
                    text(g,role,tx+5,ty+12,occupation==NpcRoster.Occupation.ADVENTURER?0xff386c36:0xff916016,tw-10);
                }
                float th=ratio(target.getHealth(),target.getMaxHealth());
                gauge(g,tx+4,ty+ph-10,tw-9,7,th,th,healthColor(th));
            }
            long time=c.level.getDayTime()%24000;
            int minutes=(int)((time+6000)%24000*1440/24000);
            String clock=String.format(Locale.ROOT,"%02d:%02d",minutes/60,minutes%60);
            chamfer(g,9,height-15,45,12,4,DARK);text(g,clock,14,height-13,WHITE,35);
        }
    }
    private static void hotbar(GuiGraphics g,int w,int h,net.minecraft.client.player.LocalPlayer p){
        var c=Minecraft.getInstance();int step=Math.max(17,Math.min(21,(w-62)/9));
        int x=w/2-step*9/2,y=h-23;
        for(int i=0;i<9;i++){
            int sx=x+i*step;boolean selected=p.getInventory().selected==i;
            g.fill(sx,y,sx+step-1,y+20,selected?AMBER:0xd9dbe5e5);
            g.fill(sx+1,y+1,sx+step-2,y+19,selected?0xeef9f4df:0xcc405058);
            item(g,p.getInventory().getItem(i),sx+(step-16)/2,y+2);
        }
        var off=p.getOffhandItem();
        if(!off.isEmpty()){
            int ox=p.getMainArm()==HumanoidArm.RIGHT?x-23:x+step*9+3;
            g.fill(ox,y,ox+20,y+20,EDGE);g.fill(ox+1,y+1,ox+19,y+19,DARK);item(g,off,ox+2,y+2);
        }
        if(c.options.attackIndicator().get()==AttackIndicatorStatus.HOTBAR){
            float r=ratio(p.getAttackStrengthScale(0),1);
            if(r<1){g.fill(x,y-4,x+step*9-1,y-2,DARK);g.fill(x,y-4,x+(int)((step*9-1)*r),y-2,WHITE);}
        }
    }
    private static void item(GuiGraphics g,ItemStack stack,int x,int y){
        if(stack.isEmpty())return;
        g.renderItem(stack,x,y);
        g.renderItemDecorations(Minecraft.getInstance().font,stack,x,y);
    }
    private static void small(GuiGraphics g,int x,int y,int w,String label,String value,float r,int color){
        chamfer(g,x,y,w,13,4,PALE);
        text(g,label,x+4,y+2,INK,36);int start=x+41,end=x+w-36;
        if(end>start){g.fill(start,y+5,end,y+9,0xffa1aeb0);g.fill(start,y+5,start+(int)((end-start)*r),y+9,color);}
        right(g,value,x+w-5,y+2,INK,33);
    }
    static int healthColor(float hp){return hp<=.30f?RED:hp<=.50f?YELLOW:GREEN;}
    static float ratio(float a,float b){return Math.max(0,Math.min(1,a/Math.max(1,b)));}
    private static String number(float v){return v==Math.floor(v)?Integer.toString((int)v):String.format(Locale.ROOT,"%.1f",v);}
    static void chamfer(GuiGraphics g,int x,int y,int w,int h,int cut,int color){
        for(int i=0;i<h;i++){int inset=Math.max(0,cut-Math.min(i,h-1-i)*2);g.fill(x,y+i,x+w-inset,y+i+1,color);}
    }
    static void gauge(GuiGraphics g,int x,int y,int w,int h,float value,float trailing,int color){
        chamfer(g,x,y,w,h,4,WHITE);chamfer(g,x+1,y+1,w-2,h-2,3,0xff78888a);
        int end=x+w-2,inner=w-4;int current=x+2+(int)(inner*value),old=x+2+(int)(inner*trailing);
        for(int i=2;i<h-2;i++){
            int rowEnd=end-Math.max(0,2-Math.min(i-2,h-3-i));
            if(old>x+2)g.fill(x+2,y+i,Math.min(old,rowEnd),y+i+1,0xffd7a04d);
            if(current>x+2)g.fill(x+2,y+i,Math.min(current,rowEnd),y+i+1,i==2?light(color):color);
        }
    }
    private static int light(int c){return 0xff000000|Math.min(255,((c>>>16)&255)+30)<<16|Math.min(255,((c>>>8)&255)+30)<<8|Math.min(255,(c&255)+30);}
    private static void text(GuiGraphics g,String s,int x,int y,int c,int max){AincradType.draw(g,Minecraft.getInstance().font,s,x,y,c,max);}
    private static void right(GuiGraphics g,String s,int right,int y,int c,int max){int n=Math.min(max,AincradType.width(Minecraft.getInstance().font,s));text(g,s,right-n,y,c,max);}
}
