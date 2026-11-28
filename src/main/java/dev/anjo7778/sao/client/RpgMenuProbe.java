package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.network.ProfileNetwork;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.CharacterProgress.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
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
    private static boolean earned,allocated;
    private static long menuStart;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==10)return;
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
                LOG.info("SAO_MENU_KEY_READY");phase=3;ticks=0;
            }else if(ticks%20==0)ProfileNetwork.request();
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
        if(phase==4&&ticks>=50){
            require(c.screen instanceof AincradMenu,"Menu closed unexpectedly");
            require(c.level.getGameTime()>menuStart+10,"World time did not advance in menu");
            capture(c,"01-character");
            int w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
            int pw=Math.min(260,Math.max(108,w-70)),ph=Math.min(226,Math.max(108,h-18));
            int x=(w-pw)/2+18,y=(h-ph)/2;
            require(c.screen.mouseClicked(x+pw-18,y+71,0),"Attribute button missed");
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
                allocated=true;LOG.info("SAO_MENU_ALLOCATION_OK");
            });phase=6;ticks=0;return;
        }
        if(phase==6&&allocated&&ticks>=8){
            select(c,2);phase=7;ticks=0;return;
        }
        if(phase==7&&ticks>=10){
            capture(c,"02-weapon-mastery");select(c,3);phase=8;ticks=0;return;
        }
        if(phase==8&&ticks>=10){
            capture(c,"03-professions");
            require(c.screen!=null,"Menu vanished");
            c.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
            require(c.screen==null,"Escape did not close");
            LOG.info("SAO_MENU_GESTURE_READY");phase=9;ticks=0;return;
        }
        if(phase==9){
            require(ticks<600,"Native downward chord did not reopen menu");
            if(c.screen instanceof AincradMenu&&ticks>=20){
                capture(c,"04-gesture");
                c.screen.keyPressed(GLFW.GLFW_KEY_ESCAPE,0,0);
                require(c.screen==null,"Gesture menu close failed");
                LOG.info("SAO_MENU_GESTURE_OK");LOG.info("SAO_RPG_MENU_PROBE_PASS");phase=10;
            }
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
    private static void select(Minecraft c,int index){
        require(c.screen instanceof AincradMenu,"Menu missing");
        int w=c.getWindow().getGuiScaledWidth(),h=c.getWindow().getGuiScaledHeight();
        int pw=Math.min(260,Math.max(108,w-70)),x=(w-pw)/2+18-22;
        int step=Math.max(10,Math.min(20,(h-16)/11)),y=(h-10*step)/2;
        require(c.screen.mouseClicked(x,y+index*step,0),"Category click failed");
    }
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
    private static void require(boolean ok,String reason){if(!ok)throw new IllegalStateException(reason);}
}
