package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.network.ProfileNetwork;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.CharacterProgress.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import org.lwjgl.glfw.GLFW;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/** Real XP events, live server snapshots and native key/mouse gesture test. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class RpgMenuProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.rpgMenuProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static boolean earned,allocated,thirdPose,handSeen;
    private static int hudPhase;
    private static long menuStart;
    private static java.util.UUID observedNpc;
    private static boolean npcPose,npcCaptured;
    private static long beforeEpoch;
    private static int beforePrep,beforePractice;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==13)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-rpg-menu-test")),"Fresh isolated save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-rpg-menu-test",
                new LevelSettings("SAO RPG menu test",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;ticks=0;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&c.screen==null&&ticks>=100){
            action(c,RpgMenuProbe::earn);phase=2;ticks=0;return;
        }
        if(phase==2){
            require(ticks<400,"Server profile not synchronized");
            if(!earned)return;
            var s=ProfileNetwork.clientSnapshot();
            if(s!=null&&s.available()&&s.xp()==100&&s.level()==2&&s.unspent()==3){
                require(MenuPreferences.mode==MenuPreferences.Mode.KEY_ONLY,"Dedicated key is not default");
                require(AincradHud.active(),"Aincrad HUD not active");
                if(hudPhase==0){if(ticks<12)return;capture(c,"08-hud-full");requireGauge(c,"08-hud-full",AincradHud.GREEN,false);action(c,p->{p.setHealth(5);p.getFoodData().setFoodLevel(6);p.getFoodData().setSaturation(0);});hudPhase=1;return;}
                if(hudPhase==1){
                    if(c.player.getHealth()>5.5f||c.player.getFoodData().getFoodLevel()>6)return;
                    hudPhase=2;ticks=0;return;
                }
                if(hudPhase==2){
                    if(ticks<30)return;
                    capture(c,"09-hud-low");requireGauge(c,"09-hud-low",AincradHud.RED,true);
                    int hungerY=8+AincradHealthGauge.height(AincradHealthGauge.width(c.getWindow().getGuiScaledWidth()))+4;
                    requireColor(c,"09-hud-low",48,hungerY+4,112,hungerY+10,AincradHud.RED,12,10);
                    LOG.info("SAO_MENU_HUD_OK green/red health and low food rendered");
                    action(c,p->{p.setHealth(p.getMaxHealth());p.getFoodData().setFoodLevel(20);});hudPhase=3;return;
                }
                LOG.info("SAO_MENU_DISABLED_CHORD_READY");phase=11;ticks=0;
            }else if(ticks%20==0)ProfileNetwork.request();
            return;
        }
        if(phase==11){
            require(c.screen==null,"Disabled mouse chord opened the menu");
            if(ticks>=90){LOG.info("SAO_MENU_DEFAULT_OK");LOG.info("SAO_MENU_KEY_READY");phase=3;ticks=0;}
            return;
        }
        if(phase==3){
            require(ticks<500,"Native M key did not open menu");
            if(c.screen instanceof AincradMenu menu){
                require(!menu.isPauseScreen(),"Menu pauses simulation");
                menuStart=c.level.getGameTime();phase=4;ticks=0;
            }
            return;
        }
        if(phase==4&&ticks==4)capture(c,"00-first-person-summon");
        if(phase==4&&ticks>=50){
            require(c.screen instanceof AincradMenu,"Menu closed unexpectedly");
            require(c.level.getGameTime()>menuStart+10,"World time did not advance in menu");
            capture(c,"01-character");
            click(c,356,81,"Attribute button");
            LOG.info("SAO_MENU_KEY_OK nonpausing=true");phase=5;ticks=0;return;
        }
        if(phase==5){
            require(ticks<300,"Attribute server response timeout");
            var s=ProfileNetwork.clientSnapshot();
            if(s==null||s.unspent()!=2||s.attribute(Attribute.STRENGTH)!=11)return;
            action(c,p->{
                var data=CharacterProgressData.get(p.serverLevel().getServer());
                var state=data.state(owner(p));
                require(state.unspent()==2&&state.attribute(Attribute.STRENGTH)==11&&state.xp()==100,"Server allocation mismatch");
                var regs=p.serverLevel().registryAccess();
                var restored=CharacterProgressData.load(data.save(new net.minecraft.nbt.CompoundTag(),regs),regs);
                require(restored.state(owner(p)).equals(state),"Profile state round trip");
                p.getInventory().setItem(9,new ItemStack(Items.COBBLESTONE,12));
                p.getInventory().setItem(10,new ItemStack(Items.IRON_BOOTS));
                p.getInventory().setChanged();
                allocated=true;LOG.info("SAO_MENU_ALLOCATION_OK");
            });phase=6;ticks=0;return;
        }
        if(phase==6&&allocated&&ticks>=8){
            select(c,1);phase=18;ticks=0;return;
        }
        if(phase==18&&ticks>=10){
            capture(c,"13-items-summary");
            click(c,294,171,"Manage inventory");
            require(c.screen instanceof AincradInventoryScreen,"Custom inventory not opened");
            phase=19;ticks=0;return;
        }
        if(phase==19&&ticks>=12){
            require(c.screen instanceof AincradInventoryScreen,"Inventory screen lost");
            var inv=(AincradInventoryScreen)c.screen;
            require(!inv.isPauseScreen(),"Inventory paused the world");
            require(inv.getMenu()==c.player.inventoryMenu,"Wrong actual container");
            require(inv.getMenu().getSlot(9).getItem().is(Items.COBBLESTONE)&&inv.getMenu().getSlot(9).getItem().getCount()==12,"Source stack missing");
            require(inv.getMenu().getSlot(10).getItem().is(Items.IRON_BOOTS),"Boots missing");
            require(inv.getMenu().getSlot(11).getItem().isEmpty(),"Destination occupied");
            capture(c,"14-aincrad-inventory");
            slotClick(inv,9,0);phase=20;ticks=0;return;
        }
        if(phase==20&&ticks>=8){
            require(c.screen instanceof AincradInventoryScreen,"Inventory lost while holding");
            var inv=(AincradInventoryScreen)c.screen;
            require(inv.getMenu().getCarried().is(Items.COBBLESTONE)&&inv.getMenu().getCarried().getCount()==12,"Real pickup failed");
            slotClick(inv,11,0);phase=21;ticks=0;return;
        }
        if(phase==21&&ticks>=8){
            var inv=(AincradInventoryScreen)c.screen;
            require(inv.getMenu().getSlot(9).getItem().isEmpty()&&inv.getMenu().getSlot(11).getItem().getCount()==12&&inv.getMenu().getCarried().isEmpty(),"Actual transfer failed");
            action(c,p->{
                require(p.getInventory().getItem(9).isEmpty()&&p.getInventory().getItem(11).is(Items.COBBLESTONE)&&p.getInventory().getItem(11).getCount()==12,"Server transfer mismatch");
                require(p.containerMenu==p.inventoryMenu&&p.containerMenu.getCarried().isEmpty(),"Unexpected carried stack");
                LOG.info("SAO_MENU_INVENTORY_MOVE_OK");
            });
            int px=(int)Math.round(inv.slotX(10)*c.getWindow().getScreenWidth()/(double)c.getWindow().getGuiScaledWidth());
            int py=(int)Math.round(inv.slotY(10)*c.getWindow().getScreenHeight()/(double)c.getWindow().getGuiScaledHeight());
            LOG.info("SAO_MENU_INVENTORY_SHIFT_READY x={} y={}",px,py);phase=22;ticks=0;return;
        }
        if(phase==22){
            require(ticks<300,"Native shift equip timeout");
            if(!c.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(Items.IRON_BOOTS))return;
            var inv=(AincradInventoryScreen)c.screen;
            require(inv.getMenu().getSlot(10).getItem().isEmpty(),"Boots duplicated at source");
            capture(c,"15-inventory-equipped");
            action(c,p->{
                require(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(Items.IRON_BOOTS)&&p.getInventory().getItem(10).isEmpty(),"Server quick equip mismatch");
                LOG.info("SAO_MENU_INVENTORY_EQUIP_OK");
            });
            phase=23;ticks=0;return;
        }
        if(phase==23&&ticks>=8){
            var inv=(AincradInventoryScreen)c.screen;
            slotClick(inv,11,1);phase=24;ticks=0;return;
        }
        if(phase==24&&ticks>=8){
            var inv=(AincradInventoryScreen)c.screen;
            require(inv.getMenu().getCarried().getCount()==6&&inv.getMenu().getSlot(11).getItem().getCount()==6,"Real split failed");
            capture(c,"16-inventory-split");
            inv.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);phase=25;ticks=0;return;
        }
        if(phase==25&&ticks>=10){
            require(c.screen instanceof AincradMenu,"Escape did not restore owner menu");
            action(c,p->{
                int count=0;
                for(int i=0;i<36;i++){
                    var stack=p.getInventory().getItem(i);
                    if(stack.is(Items.COBBLESTONE))count+=stack.getCount();
                }
                require(count==12&&p.inventoryMenu.getCarried().isEmpty(),"Closing with cursor stack lost or duplicated items: "+count);
                require(p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET).is(Items.IRON_BOOTS),"Equipped item lost");
                LOG.info("SAO_MENU_INVENTORY_RETURN_OK total={}",count);
            });
            select(c,2);phase=7;ticks=0;return;
        }
        if(phase==7&&ticks>=10){
            capture(c,"02-weapon-mastery");select(c,3);phase=8;ticks=0;return;
        }
        if(phase==8&&ticks>=10){
            capture(c,"03-professions");
            click(c,92,167,"System group");phase=12;ticks=0;return;
        }
        if(phase==12&&ticks>=10){
            click(c,294,54,"Enable both input methods");
            require(MenuPreferences.mode==MenuPreferences.Mode.BOTH,"Settings did not enable chord");
            require(Files.exists(c.gameDirectory.toPath().resolve("config/sao-menu.properties")),"Settings not persisted");
            capture(c,"04-controls");
            c.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
            require(c.screen instanceof AincradMenu m&&m.closing(),"Close animation did not start");
            phase=14;ticks=0;return;
        }
        if(phase==14&&ticks>=10){
            require(c.screen==null,"Escape did not close after animation");
            c.options.setCameraType(CameraType.THIRD_PERSON_BACK);c.player.setXRot(0);c.player.setYRot(0);
            LOG.info("SAO_MENU_SETTINGS_OK");LOG.info("SAO_MENU_GESTURE_READY");phase=9;ticks=0;return;
        }
        if(phase==9){
            require(ticks<600,"Native downward chord did not reopen menu");
            if(c.screen instanceof AincradMenu menu){
                if(menu.age()>=3&&menu.age()<=6&&!handSeen){capture(c,"05-third-person-summon");handSeen=true;}
                if(menu.age()>=22){
                    require(thirdPose,"Third-person arm pose not observed");
                    capture(c,"06-third-person-owner");
                    int w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
                    double scale=Math.max(.1,Math.min(1.15,Math.min(w*.90/AincradMenu.W,h*.74/AincradMenu.H)));
                    double left=(w-AincradMenu.W*scale)/2,top=(h-AincradMenu.H*scale)/2+Math.min(10,h*.035);
                    requireColor(c,"06-third-person-owner",left+118*scale,top+15*scale,left+208*scale,top+30*scale,0xffe2a126,22,24);
                    click(c,356,93,"Full screen attribute");
                    LOG.info("SAO_MENU_OWNER_SCREEN_OK");phase=10;ticks=0;
                }
            }
            return;
        }
        if(phase==10){
            require(ticks<300,"Projected allocation not synchronized");
            var s=ProfileNetwork.clientSnapshot();
            if(s!=null&&s.unspent()==1&&s.attribute(Attribute.VITALITY)==11){
                capture(c,"07-owner-allocation");c.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                phase=15;ticks=0;
            }
        }
        if(phase==15&&ticks>=10){
            require(c.screen==null,"Gesture menu close failed");
            LOG.info("SAO_MENU_GESTURE_OK");
            c.options.setCameraType(CameraType.FIRST_PERSON);
            action(c,RpgMenuProbe::prepareNpcFixture);phase=16;ticks=0;return;
        }
        if(phase==16){
            require(ticks<1400,"No autonomous visible NPC review after real simulation updates");
            if(observedNpc==null)return;
            var entity=c.level.getEntitiesOfClass(dev.anjo7778.sao.entity.CityNpc.class,c.player.getBoundingBox().inflate(20),n->n.getUUID().equals(observedNpc)).stream().findFirst().orElse(null);
            if(entity==null||!entity.menuOpen())return;
            int age=entity.menuAge();
            if(age>=3&&age<10&&!npcCaptured){capture(c,"10-npc-summon");npcCaptured=true;}
            if(age>=24&&age<53){
                require(npcPose,"NPC summon arm pose not rendered");
                require(npcCaptured,"Missing NPC summon frame");
                require(entity.displayLevel()>0&&!entity.menuDetail().isBlank(),"NPC details not synced");
                capture(c,"11-npc-menu");
                int w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
                requireColor(c,"11-npc-menu",w*.15,h*.15,w*.85,h*.84,0xffe2a126,22,18);
                action(c,p->{
                    var level=p.serverLevel();
                    var npc=(dev.anjo7778.sao.entity.CityNpc)level.getEntity(observedNpc);
                    require(npc!=null&&npc.boundAdventurer(),"Missing authoritative actor");
                    var ledger=AdventureSimulationData.get(level.getServer()).ledger(npc.boundProfile());
                    require(ledger!=null&&ledger.epoch()>beforeEpoch,"No actual committed ledger advance");
                    require(ledger.preparations()>beforePrep||ledger.practice()>beforePractice||ledger.goal().active(),"No real routine or objective");
                    LOG.info("SAO_MENU_NPC_ACTION_OK epoch={} prep={} practice={} goal={}",ledger.epoch(),ledger.preparations(),ledger.practice(),ledger.goal().kind());
                });
                LOG.info("SAO_MENU_NPC_PIXELS_OK");
                phase=17;ticks=0;return;
            }
        }
        if(phase==17){
            require(ticks<140,"NPC menu did not close");
            var entity=c.level.getEntitiesOfClass(dev.anjo7778.sao.entity.CityNpc.class,c.player.getBoundingBox().inflate(24),n->n.getUUID().equals(observedNpc)).stream().findFirst().orElse(null);
            if(entity==null||entity.menuOpen())return;
            capture(c,"12-npc-menu-closed");
            LOG.info("SAO_MENU_NPC_CLOSE_OK");LOG.info("SAO_RPG_MENU_PROBE_PASS");phase=13;return;
        }
    }
    /** Position a real roster identity at a safe scheduled preparation destination, then let
     * the normal simulation and entity ticks choose and open the menu. No manual menu request. */
    private static void prepareNpcFixture(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        level.setDayTime(2000);
        var pop=NpcPopulationData.get(server);var sim=AdventureSimulationData.get(server);
        pop.discover(1,server.overworld().getSeed());
        for(var state:pop.roster(1)){
            var profile=state.profile();
            if(profile.occupation()!=NpcRoster.Occupation.ADVENTURER||level.getEntity(profile.id())!=null||sim.journey(state).active())continue;
            var ledger=sim.ledger(state);
            if(ledger!=null&&(ledger.trip().active()||ledger.needsRecovery()))continue;
            var plan=AdventureSimulationData.plan(level,state);
            if(plan.activity()!=NpcDailyPlan.Activity.PREPARE)continue;
            var dest=plan.destination();
            if(Math.abs(dest.x())>24||Math.abs(dest.z())>24)continue;
            int x=dest.x(),z=dest.z();
            var ground=ExpeditionGround.walkable(level,1,x,z);var view=ExpeditionGround.walkable(level,1,x,z-5);
            if(ground==null||view==null||!CityRules.isSafeZone(level,x,z))continue;
            var npc=dev.anjo7778.sao.entity.SaoEntities.CITY_NPC.get().create(level);
            require(npc!=null,"NPC fixture factory");
            npc.bind(state);npc.moveTo(x+.5,ground.getY(),z+.5,180,0);npc.yBodyRot=180;npc.yHeadRot=180;
            if(!level.noCollision(npc)||!level.addFreshEntity(npc)){npc.discard();continue;}
            if(ledger==null){sim.update(profile.birthFloor(),profile.id(),Math.max(0,level.getGameTime()/AdventureProgress.EPOCH_TICKS),AdventureProgress.Mode.IDLE,false);ledger=sim.ledger(state);}
            beforeEpoch=ledger.epoch();beforePrep=ledger.preparations();beforePractice=ledger.practice();
            observedNpc=profile.id();
            require(p.teleportTo(level,x+.5,view.getY(),z-4.5,Set.of(),0,0),"NPC observer camera");
            LOG.info("SAO_MENU_NPC_FIXTURE {} epoch={} position={}",observedNpc,beforeEpoch,npc.position());
            return;
        }
        throw new IllegalStateException("No suitable available preparation NPC");
    }
    @SubscribeEvent public static void npcRendered(net.neoforged.neoforge.client.event.RenderLivingEvent.Post<?,?> e){
        if(!ENABLED||phase!=16||observedNpc==null||!observedNpc.equals(e.getEntity().getUUID())||!(e.getEntity() instanceof dev.anjo7778.sao.entity.CityNpc npc))return;
        int age=npc.menuAge();
        if(age>=3&&age<=8&&e.getRenderer().getModel() instanceof net.minecraft.client.model.PlayerModel<?> model
            &&(model.leftArm.xRot<-.5f||model.rightArm.xRot<-.5f)){
            if(!npcPose)LOG.info("SAO_MENU_NPC_ARM_OK");npcPose=true;
        }
    }
    private static CharacterProgressData.Owner owner(ServerPlayer p){return new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());}
    private static void earn(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();var data=CharacterProgressData.get(server);
        require(CityRules.hasRegionalSettlements(level),"Wrong world");
        require(data.state(owner(p)).xp()==20,"Expected one actual capital discovery, found "+data.state(owner(p)).xp());
        int x=CityPlan.half(1)+36;
        for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=-1;cz<=1;cz++)level.getChunk(cx,cz);
        var pos=ExpeditionGround.walkable(level,1,x,0);
        require(pos!=null&&!CityRules.isSafeZone(level,x,0),"No wilderness surface");
        require(p.teleportTo(level,x+.5,pos.getY(),.5,Set.of(),0,0),"Wilderness test teleport");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        // Four real, reduced-HP hostile entities. No synthetic experience event or direct XP grant.
        for(int i=0;i<4;i++){
            var mob=EntityType.HUSK.create(level);require(mob!=null,"Husk type missing");
            mob.setNoAi(true);mob.setHealth(1);mob.moveTo(x+.5,pos.getY(),.5,0,0);
            require(level.addFreshEntity(mob),"Fixture spawn failed");
            require(mob.hurt(level.damageSources().playerAttack(p),5)&&!mob.isAlive(),"Real kill failed");
        }
        var s=data.state(owner(p));
        require(s.xp()==100&&s.level()==2&&s.masteryExperience(Mastery.SWORD)==80,"Actual combat XP mismatch: "+s);
        require(p.teleportTo(level,.5,CityPlan.GROUND+1,.5,Set.of(),0,0),"Return to capital failed");
        p.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
        earned=true;LOG.info("SAO_MENU_XP_OK total={} level={} sword={}",s.xp(),s.level(),s.masteryExperience(Mastery.SWORD));
    }
    @SubscribeEvent public static void rendered(RenderPlayerEvent.Post e){
        if(!ENABLED||phase!=9||e.getEntity()!=Minecraft.getInstance().player)return;
        float t=MenuGesture.progress(e.getPartialTick());
        if(t>=.22f&&t<=.7f&&e.getRenderer().getModel().rightArm.xRot<-.5f){
            if(!thirdPose)LOG.info("SAO_MENU_ARM_OK");thirdPose=true;
        }
    }
    private static void slotClick(AincradInventoryScreen inv,int slot,int button){
        double x=inv.slotX(slot),y=inv.slotY(slot);
        inv.mouseClicked(x,y,button);inv.mouseReleased(x,y,button);
    }
    private static void click(Minecraft c,double x,double y,String description){
        require(c.screen instanceof AincradMenu,"Menu missing");
        int w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
        double scale=Math.max(.1,Math.min(1.15,Math.min(w*.90/AincradMenu.W,h*.74/AincradMenu.H)));
        require(c.screen.mouseClicked((w-AincradMenu.W*scale)/2+x*scale,(h-AincradMenu.H*scale)/2+Math.min(10,h*.035)+y*scale,0),description+" click failed");
    }
    private static void select(Minecraft c,int index){click(c,165,22+index*19,"Submenu");}
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> action){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");action.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("rpg-menu-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void requireGauge(Minecraft c,String name,int color,boolean low){
        double scale=AincradHealthGauge.width(c.getWindow().getGuiScaledWidth())/400.0;
        // Test actual screenshot geometry at proportional locations, not the old rectangle.
        requireColor(c,name,9+scale*93,8+scale*18,9+scale*(low?148:342),8+scale*28,color,35,45);
        requireColor(c,name,9+scale*26,8+scale*2,9+scale*76,8+scale*10,0xff4d4f4f,22,20);
        requireColor(c,name,9+scale*246,8+scale*37,9+scale*334,8+scale*40,0xff848686,18,12);
        // The green or red fill must not spill into the lower-right notch in the bar.
        requireColor(c,name,9+scale*255,8+scale*28,9+scale*345,8+scale*33,0xff4d4f4f,22,18);
        if(low)requireColor(c,name,9+scale*190,8+scale*18,9+scale*325,8+scale*23,0xff353934,16,30);
        LOG.info("SAO_MENU_STEPPED_GAUGE_OK {} low={}",name,low);
    }
    private static void requireColor(Minecraft c,String name,double x0,double y0,double x1,double y1,int color,int tolerance,int minimum){
        try{
            var im=javax.imageio.ImageIO.read(c.gameDirectory.toPath().resolve("rpg-menu-probe/"+name+".png").toFile());
            double sx=im.getWidth()/(double)c.getWindow().getGuiScaledWidth(),sy=im.getHeight()/(double)c.getWindow().getGuiScaledHeight();
            int count=0;
            for(int y=Math.max(0,(int)(y0*sy));y<Math.min(im.getHeight(),(int)(y1*sy));y++)for(int x=Math.max(0,(int)(x0*sx));x<Math.min(im.getWidth(),(int)(x1*sx));x++){
                int v=im.getRGB(x,y);
                if(Math.abs((v>>16&255)-(color>>16&255))<=tolerance&&Math.abs((v>>8&255)-(color>>8&255))<=tolerance&&Math.abs((v&255)-(color&255))<=tolerance)count++;
            }
            require(count>=minimum,name+" rendered color "+Integer.toHexString(color)+" missing: "+count+" pixels, expected "+minimum);
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
}
