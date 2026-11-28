package dev.anjo7778.sao.client;

import dev.anjo7778.sao.network.ProfileNetwork;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.SwordActions;
import dev.anjo7778.sao.simulation.SwordActions.Skill;
import dev.anjo7778.sao.simulation.CharacterProgress.Attribute;
import dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Arrays;
import java.util.Locale;

/** Owner always uses a screen-space menu. NPC projections reuse its layout. */
public final class AincradMenu extends Screen {
    static final int W=368,H=194,SUMMON=12;
    static final ResourceLocation ATLAS=ResourceLocation.fromNamespaceAndPath("sao","textures/gui/aincrad_icons.png");
    private static final String[][] PAGES={{"Character","Items","Skills","Professions"},{"Party","Guild","Friends"},{"Messages"},{"Map","Quests","Market"},{"Controls"}};
    private static final int[] GROUP_ICON={3,4,5,6,7};
    private static final int[][] PAGE_ICON={{3,8,9,10},{4,12,3},{5},{13,11,14},{7}};
    private static final int INK=0xff45484b,FAINT=0xff737c82,WHITE=0xfffafcfc,PALE=0xd9f3f8f8,EDGE=0xd9bac1c5,ORANGE=0xffe2a126,GOLD=0xffffd582,SHADOW=0x35505b61;
    private int group,page,age,slide,scroll,closing=-1;
    private boolean weaponMasteryView;
    private Attribute pendingAttribute;
    private long pendingValue,pendingPoints;
    private int pendingAge;
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
    public boolean closing(){return closing>=0;}
    public float reveal(){return closing()?Math.max(.12f,1-closing/6f):Math.min(1,Math.max(.12f,(age-SUMMON+1)/6f));}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void renderBackground(GuiGraphics g,int x,int y,float partial){}
    @Override public void tick(){
        age++;slide=Math.min(5,slide+1);
        if(minecraft==null||minecraft.player==null||minecraft.level==null||!minecraft.player.isAlive()){super.onClose();return;}
        if(closing()&&++closing>=6){super.onClose();return;}
        if(age==SUMMON)AincradSounds.play(AincradSounds.Cue.OPEN);
        if(pendingAttribute!=null){
            var s=ProfileNetwork.clientSnapshot();
            if(s!=null&&s.owner().equals(minecraft.player.getUUID())&&s.available()
                &&s.attribute(pendingAttribute)>pendingValue&&s.unspent()<pendingPoints){
                AincradSounds.play(AincradSounds.Cue.CONFIRM);pendingAttribute=null;
            }else if(age-pendingAge>80)pendingAttribute=null;
        }
        if(age%20==0)ProfileNetwork.request();
    }
    private float scale(){return Math.max(.1f,Math.min(1.15f,Math.min(width*.90f/W,height*.74f/H)));}
    private float left(){return (width-W*scale())/2;}
    private float top(){return (height-H*scale())/2+Math.min(10,height*.035f);}
    @Override public void onClose(){if(!closing()){closing=0;AincradSounds.play(AincradSounds.Cue.CLOSE);}}
    private double localX(double x){return (x-left())/scale();}
    private double localY(double y){return (y-top())/scale();}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        if(summoning())return;
        g.pose().pushPose();float r=reveal();g.pose().translate(left()+W*scale()*(1-r)/2,top()+H*scale()*(1-r)/2,0);g.pose().scale(scale()*r,scale()*r,1);
        draw(new Painter(){
            public void rect(int x,int y,int w,int h,int c){if(w>0&&h>0)g.fill(x,y,x+w,y+h,c);}
            public void tile(int t,int x,int y,int size,int c){
                g.setColor(((c>>>16)&255)/255f,((c>>>8)&255)/255f,(c&255)/255f,((c>>>24)&255)/255f);
                g.blit(ATLAS,x,y,size,size,(t%4)*64f,(t/4)*64f,64,64,256,256);g.setColor(1,1,1,1);
            }
            public void text(String s,int x,int y,int c,int max){AincradType.draw(g,font,s,x,y,c,max);}
            public int width(String s){return AincradType.width(font,s);}
        },localX(mx),localY(my));g.pose().popPose();
    }
    private static void panel(Painter p,int x,int y,int w,int h){
        p.rect(x+2,y+3,w,h,SHADOW);p.rect(x,y,w,h,PALE);
        p.rect(x+2,y+2,w-4,17,0x60ffffff);
        p.rect(x,y,w,1,WHITE);p.rect(x,y+1,1,h-2,WHITE);
        p.rect(x+w-1,y+1,1,h-2,EDGE);p.rect(x,y+h-1,w,1,EDGE);
    }
    private static void center(Painter p,String s,int x,int y,int w,int c){p.text(s,x+Math.max(0,(w-p.width(s))/2),y,c,w);}
    private int subTop(){return subTop(group);}
    private static int subTop(int group){return Math.max(9,Math.min(H-PAGES[group].length*19-12,23+36*group-9));}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&mx<x+w&&my>=y&&my<y+h;}
    void draw(Painter p,double mx,double my){
        var s=ProfileNetwork.clientSnapshot();
        boolean real=s!=null&&s.available()&&minecraft!=null&&minecraft.player!=null&&s.owner().equals(minecraft.player.getUUID());
        panel(p,0,51,73,103);p.tile(3,22,57,29,0xff797f83);
        center(p,real?s.name():"AINCRAD",3,89,67,INK);center(p,real?"Lv. "+s.level():"Connecting",3,101,67,FAINT);
        if(real){
            int hp=(int)(61*Math.max(0,Math.min(1,minecraft.player.getHealth()/Math.max(1,minecraft.player.getMaxHealth()))));
            p.rect(6,114,61,5,0xffc4cacc);p.rect(6,114,hp,5,AincradHud.healthColor(AincradHud.ratio(minecraft.player.getHealth(),minecraft.player.getMaxHealth())));
            int xp=(int)(61*Math.max(0,Math.min(1,s.into()/(double)Math.max(1,s.required()))));
            p.rect(6,123,61,4,0xffc4cacc);p.rect(6,123,xp,4,0xff7da6bb);center(p,"Floor "+s.floor(),3,137,67,FAINT);
        }
        navigation(p,group,page,mx,my);
        int shift=Math.max(0,5-slide)*2,x=221+shift,y=10,w=147-shift;
        panel(p,x,y,w,174);p.rect(x,y,3,20,ORANGE);p.text(PAGES[group][page].toUpperCase(Locale.ROOT),x+8,y+6,INK,w-14);p.rect(x+5,y+19,w-10,1,EDGE);
        if(group==4){settings(p,x,y,w,mx,my);return;}
        if(!real){p.text("Awaiting profile...",x+7,y+32,FAINT,w-14);return;}
        if(group==0&&page==0)character(p,s,x,y,w,mx,my);
        else if(group==0&&page==1)equipment(p,x,y,w,mx,my);
        else if(group==0&&page==2){
            if(weaponMasteryView){mastery(p,s,x,y,w,false);masteryToggle(p,x,y,w,mx,my,"Sword skills");}
            else swordSkills(p,s,x,y,w,mx,my);
        }else if(group==0&&page==3)mastery(p,s,x,y,w,true);
        else{p.text("Not available yet",x+7,y+35,INK,w-14);p.text("In development",x+7,y+49,FAINT,w-14);}
    }
    private static void navigation(Painter p,int group,int page,double mx,double my){
        p.rect(92,23,1,144,0x80e1e7e9);
        for(int i=0;i<GROUP_ICON.length;i++){
            int cy=23+36*i;double dx=mx-92,dy=my-cy;boolean active=i==group,hover=dx*dx+dy*dy<=16*16;
            p.tile(1,75,cy-17,34,0x508b9ba1);
            p.tile(1,76,cy-16,32,active?ORANGE:hover?0xffd1e0e1:0xeaf6f8f8);
            p.tile(2,75,cy-17,34,active?GOLD:0xeefafcfc);
            p.tile(GROUP_ICON[i],81,cy-11,22,active?WHITE:0xff727a7e);
        }
        int sy=subTop(group),sx=115;
        for(int i=0;i<PAGES[group].length;i++){
            int y=sy+i*19;boolean active=i==page,hover=inside(mx,my,sx,y,99,17);
            p.rect(sx+2,y+2,99,17,SHADOW);p.rect(sx,y,99,17,active?ORANGE:hover?0xffdce7e7:PALE);
            p.tile(PAGE_ICON[group][i],sx+2,y+1,15,active?WHITE:FAINT);p.text(PAGES[group][i],sx+19,y+4,active?WHITE:INK,75);
            if(active)p.tile(15,sx+87,y+3,11,WHITE);
        }
    }
    static void drawNpc(Painter p,dev.anjo7778.sao.entity.CityNpc npc,dev.anjo7778.sao.simulation.NpcMenuReview.Kind kind){
        int group=kind.group,page=kind.page;
        panel(p,0,51,73,103);p.tile(3,22,57,29,0xff797f83);
        center(p,npc.getDisplayName().getString(),3,89,67,INK);
        center(p,"Lv. "+npc.displayLevel(),3,101,67,FAINT);
        float hp=AincradHud.ratio(npc.getHealth(),npc.getMaxHealth());
        p.rect(6,114,61,5,0xffc4cacc);p.rect(6,114,(int)(61*hp),5,AincradHud.healthColor(hp));
        center(p,"Adventurer",3,137,67,FAINT);
        navigation(p,group,page,-100,-100);
        panel(p,221,10,147,174);p.rect(221,10,3,20,ORANGE);
        p.text(PAGES[group][page].toUpperCase(Locale.ROOT),229,16,INK,133);
        p.rect(226,29,137,1,EDGE);p.text(kind.title,228,38,INK,133);
        int y=55;
        for(String row:npc.menuDetail().split("\\|",4)){
            p.text(row,228,y,INK,133);y+=15;
        }
        if(group==0&&page==1){
            p.rect(228,y+4,133,1,EDGE);p.text("Main hand",228,y+13,FAINT,133);
            p.text(npc.getMainHandItem().isEmpty()?"Empty":npc.getMainHandItem().getHoverName().getString(),228,y+26,INK,133);
        }
    }
    private void character(Painter p,ProfileNetwork.Snapshot s,int x,int y,int w,double mx,double my){
        p.text("CHARACTER STATUS",x+7,y+25,FAINT,w-14);p.text("Lv. "+s.level(),x+7,y+38,INK,w-14);p.text(s.into()+" / "+s.required()+" RPG XP",x+7,y+49,FAINT,w-14);
        p.rect(x+6,y+61,w-12,1,EDGE);
        for(var a:Attribute.values()){
            int row=y+67+a.ordinal()*12;String v=Long.toString(s.attribute(a));
            p.text(label(a.name()),x+7,row,INK,w-52);p.text(v,Math.max(x+70,x+w-27-p.width(v)),row,INK,30);
            if(s.unspent()>0){int bx=x+w-17;boolean hover=inside(mx,my,bx,row-1,12,11);p.rect(bx,row-1,12,11,hover?ORANGE:0xffd6dfe0);p.text("+",bx+3,row,hover?WHITE:INK,10);}
        }
        p.rect(x+6,y+152,w-12,1,EDGE);p.text(s.unspent()+" pts",x+7,y+159,INK,45);
        String vanilla="Enchant Lv "+minecraft.player.experienceLevel;p.text(vanilla,x+w-7-p.width(vanilla),y+159,FAINT,w-53);
    }
    private void equipment(Painter p,int x,int y,int w,double mx,double my){
        var player=minecraft.player;
        if(player==null)return;
        var slots=new net.minecraft.world.entity.EquipmentSlot[]{
            net.minecraft.world.entity.EquipmentSlot.MAINHAND,net.minecraft.world.entity.EquipmentSlot.OFFHAND,
            net.minecraft.world.entity.EquipmentSlot.HEAD,net.minecraft.world.entity.EquipmentSlot.CHEST,
            net.minecraft.world.entity.EquipmentSlot.LEGS,net.minecraft.world.entity.EquipmentSlot.FEET
        };
        String[] names={"Main hand","Off hand","Head","Chest","Legs","Feet"};
        p.text("EQUIPPED ITEMS",x+7,y+25,FAINT,w-14);
        for(int i=0;i<slots.length;i++){
            int ry=y+39+i*17;
            var stack=player.getItemBySlot(slots[i]);
            p.text(names[i],x+7,ry,FAINT,51);
            p.text(stack.isEmpty()?"Empty":stack.getHoverName().getString(),x+59,ry,INK,w-66);
            p.rect(x+7,ry+11,w-14,1,EDGE);
        }
        p.text("Armor "+player.getArmorValue(),x+7,y+142,INK,w-14);
        p.rect(x+7,y+154,w-14,14,inside(mx,my,x+7,y+154,w-14,14)?ORANGE:0xffdce6e7);
        center(p,"Manage inventory",x+7,y+156,w-14,INK);
    }
    // Two visual progression columns. Actual unlocks are the existing mastery
    // requirements; links do not invent additional skill-point prerequisites.
    private static final Skill[] SWORD_NODES={Skill.HORIZONTAL,Skill.SLANT,
        Skill.HORIZONTAL_SQUARE,Skill.VERTICAL,Skill.SONIC_LEAP,Skill.VERTICAL_SQUARE};
    private static final int NODE_W=64,NODE_H=29,NODE_DX=68,NODE_DY=34,NODE_Y=39;
    private static int nodeX(int x,int i){return x+7+(i%2)*NODE_DX;}
    private static int nodeY(int y,int i){return y+NODE_Y+(i/2)*NODE_DY;}
    private CombatNetwork.Snapshot combat(){
        var snapshot=CombatNetwork.snapshot();
        return snapshot!=null&&minecraft!=null&&minecraft.player!=null
            &&snapshot.owner().equals(minecraft.player.getUUID())?snapshot:null;
    }
    private static void masteryToggle(Painter p,int x,int y,int w,double mx,double my,String title){
        p.rect(x+7,y+157,w-14,13,inside(mx,my,x+7,y+157,w-14,13)?ORANGE:0xffdce6e7);
        center(p,title,x+7,y+159,w-14,INK);
    }
    private void swordSkills(Painter p,ProfileNetwork.Snapshot s,int x,int y,int w,double mx,double my){
        long level=s.mastery(Mastery.SWORD);var combat=combat();
        Skill selected=combat==null?null:Skill.fromIndex(combat.selectedSkill());
        p.text("SWORD MASTERY  "+level,x+7,y+26,FAINT,w-14);
        // Horizontal branch and diagonal/vertical branch; Sonic Leap is independent.
        for(int i:new int[]{0,1,3}){
            int bx=nodeX(x,i)+NODE_W/2;
            p.rect(bx,nodeY(y,i)+NODE_H,1,NODE_DY-NODE_H,EDGE);
        }
        Skill hovered=null;
        for(int i=0;i<SWORD_NODES.length;i++){
            Skill skill=SWORD_NODES[i];int bx=nodeX(x,i),by=nodeY(y,i);
            boolean unlocked=skill.unlocked(level),active=skill==selected,hover=inside(mx,my,bx,by,NODE_W,NODE_H);
            if(hover)hovered=skill;
            p.rect(bx,by,NODE_W,NODE_H,active?ORANGE:EDGE);
            p.rect(bx+1,by+1,NODE_W-2,NODE_H-2,unlocked?(hover?0xfff9e8bd:0xffe9f2f2):0xffd0d6d7);
            String title=label(skill.name());
            if(skill==Skill.HORIZONTAL_SQUARE||skill==Skill.VERTICAL_SQUARE){
                int split=title.indexOf(' ');p.text(title.substring(0,split),bx+3,by+3,INK,NODE_W-6);
                p.text("Square",bx+3,by+12,INK,NODE_W-6);
            }else{
                p.text(title,bx+3,by+3,INK,NODE_W-6);
                p.text(skill.move.cost+" STA",bx+3,by+12,FAINT,NODE_W-6);
            }
            p.text(active?"Selected":unlocked?"Learned":"Lv. "+skill.mastery,bx+3,by+21,active?0xff8d5c0a:FAINT,NODE_W-6);
        }
        String detail=hovered!=null?hovered.move.cost+" STA / "+(hovered.cooldown/20.0)+"s"
            :combat==null||!combat.available()?"Equip one sword to select":selected==null?"Select a learned skill":"R: "+label(selected.name());
        p.text(detail,x+7,y+141,FAINT,w-14);
        masteryToggle(p,x,y,w,mx,my,"Weapon mastery");
    }
    private void mastery(Painter p,ProfileNetwork.Snapshot s,int x,int y,int w,boolean profession){
        var list=Arrays.stream(Mastery.values()).filter(m->(m.ordinal()>=Mastery.BLACKSMITHING.ordinal())==profession).toList();
        scroll=Math.max(0,Math.min(scroll,Math.max(0,list.size()-10)));p.text(profession?"PROFESSION MASTERY":"WEAPON MASTERY",x+7,y+26,FAINT,w-14);
        int rows=profession?10:9;
        scroll=Math.max(0,Math.min(scroll,Math.max(0,list.size()-rows)));
        for(int i=0;i<rows&&i+scroll<list.size();i++){
            var m=list.get(i+scroll);int ry=y+40+i*12;
            p.text(label(m.name()),x+7,ry,INK,w-64);String v=s.experience(m)==0?"Untrained":"Lv "+s.mastery(m);
            p.text(v,x+w-7-p.width(v),ry,FAINT,58);if(s.experience(m)>0)p.rect(x+7,ry+10,w-14,1,EDGE);
        }
    }
    private void settings(Painter p,int x,int y,int w,double mx,double my){
        String[] names={"Opening method","Gesture sensitivity","Combat lock","NPC menus","Aincrad HUD"};
        String[] values={label(MenuPreferences.mode.name()),label(MenuPreferences.sensitivity.name()),MenuPreferences.combatGuard?"On":"Off",MenuPreferences.npcMenus?"On":"Off",MenuPreferences.hud?"On":"Vanilla"};
        for(int i=0;i<5;i++){int ry=y+26+i*25;p.text(names[i],x+7,ry,FAINT,w-14);p.rect(x+7,ry+10,w-14,16,inside(mx,my,x+7,ry+10,w-14,16)?ORANGE:0xffdce6e7);center(p,values[i],x+7,ry+14,w-14,INK);}
        p.text("M: default key / ESC",x+7,y+158,FAINT,w-14);
    }
    private static String label(String v){v=v.toLowerCase(Locale.ROOT).replace('_',' ');return Character.toUpperCase(v.charAt(0))+v.substring(1);}
    @Override public boolean mouseClicked(double x,double y,int button){
        if(button!=0||summoning()||closing()||reveal()<.99f)return super.mouseClicked(x,y,button);
        double mx=localX(x),my=localY(y);
        for(int i=0;i<GROUP_ICON.length;i++){double dx=mx-92,dy=my-(23+36*i);if(dx*dx+dy*dy<=17*17){if(group!=i){group=i;page=scroll=slide=0;AincradSounds.play(AincradSounds.Cue.SELECT);}return true;}}
        for(int i=0;i<PAGES[group].length;i++)if(inside(mx,my,115,subTop()+i*19,99,17)){if(page!=i){page=i;scroll=slide=0;AincradSounds.play(AincradSounds.Cue.SELECT);}return true;}
        if(slide<5)return false;
        if(group==0&&page==1&&inside(mx,my,228,164,133,14)&&minecraft!=null&&minecraft.player!=null){
            AincradSounds.play(AincradSounds.Cue.SELECT);
            if(minecraft.player.isCreative()||minecraft.player.containerMenu!=minecraft.player.inventoryMenu)
                minecraft.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(minecraft.player));
            else minecraft.setScreen(new AincradInventoryScreen(minecraft.player,this));
            return true;
        }
        if(group==4)for(int i=0;i<5;i++)if(inside(mx,my,228,46+i*25,133,16)){MenuPreferences.cycle(i);AincradSounds.play(AincradSounds.Cue.SELECT);return true;}
        var s=ProfileNetwork.clientSnapshot();
        if(group==0&&page==2&&s!=null&&s.available()){
            if(inside(mx,my,228,167,133,13)){weaponMasteryView=!weaponMasteryView;scroll=0;AincradSounds.play(AincradSounds.Cue.SELECT);return true;}
            if(!weaponMasteryView)for(int i=0;i<SWORD_NODES.length;i++){
                if(!inside(mx,my,nodeX(221,i),nodeY(10,i),NODE_W,NODE_H))continue;
                var skill=SWORD_NODES[i];var combat=combat();
                if(!skill.unlocked(s.mastery(Mastery.SWORD))){
                    minecraft.player.displayClientMessage(Component.translatable("message.sao.control.mastery",skill.mastery),true);
                }else if(combat==null||!combat.available()){
                    minecraft.player.displayClientMessage(Component.translatable("message.sao.control.equip"),true);
                }else{
                    CombatNetwork.intent(5,skill.ordinal());AincradSounds.play(AincradSounds.Cue.SELECT);
                }
                return true;
            }
        }
        if(group==0&&page==0&&s!=null&&s.available()&&s.unspent()>0)for(var a:Attribute.values())if(inside(mx,my,351,76+a.ordinal()*12,12,11)){pendingAttribute=a;pendingValue=s.attribute(a);pendingPoints=s.unspent();pendingAge=age;ProfileNetwork.allocate(a);return true;}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double mx,double my,double sx,double sy){if(group==0&&(page==3||page==2&&weaponMasteryView)){scroll=Math.max(0,scroll+(sy<0?1:sy>0?-1:0));return true;}return super.mouseScrolled(mx,my,sx,sy);}
}
