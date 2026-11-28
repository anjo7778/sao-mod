package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Test fixture chooses an existing NPC and materializes it on a street;
 * destination, walking, objective updates and practice credit are production code. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class VenueProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.venueProbe");
    private static int phase,ticks,initialPractice;
    private static long began;
    private static UUID id;
    private static NpcDailyPlan.Point target;
    private static boolean arrived,credited;
    private static CompletableFuture<Void> pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==4)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-venue-test")),"Disposable world required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-venue-test",
                new LevelSettings("SAO venue test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),
                access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=100){
            c.options.hideGui=true;
            action(c,VenueProbe::setup);phase=2;ticks=0;return;
        }
        if(phase==2&&arrived){
            capture(c,"01-adventurer-at-training-yard");phase=3;
        }
        if(phase==3&&credited){
            capture(c,"02-practice-credit");
            LOG.info("SAO_VENUE_PROBE_PASS");phase=4;return;
        }
        if((phase==2||phase==3)&&ticks%20==0)action(c,VenueProbe::observe);
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        require(CityRules.hasLandmarks(level),"Wrong generator");
        level.setDayTime(6000);
        var pop=NpcPopulationData.get(server);pop.discover(1,level.getSeed());
        NpcRoster.State selected=null;
        CityCourts.Site venue=null;
        var sim=AdventureSimulationData.get(server);
        for(var s:pop.roster(1)){
            if(s.profile().occupation()!=NpcRoster.Occupation.ADVENTURER||s.profile().ambition()!=NpcRoster.Ambition.MASTERY
                ||level.getEntity(s.profile().id())!=null)continue;
            var l=sim.ledger(1,s.profile().id());
            if(l!=null&&(l.trip().active()||l.goal().active()&&l.goal().kind()!=AdventurerGoals.Kind.PRACTICE))continue;
            var plan=AdventureSimulationData.plan(level,s);
            var site=CityCourts.at(1,plan.destination().x(),plan.destination().z());
            if(plan.activity()==NpcDailyPlan.Activity.TRAIN&&site!=null&&site.kind()==CityCourts.Kind.TRAINING_YARD){
                selected=s;target=plan.destination();venue=site;break;
            }
        }
        require(selected!=null&&venue!=null,"No independently selected practice venue");
        id=selected.profile().id();
        var ledger=sim.ledger(1,id);initialPractice=ledger==null?0:ledger.practice();
        int x=venue.x(),z=venue.z()-CityCourts.HALF_Z-8;
        for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=(z-8)>>4;cz<=(venue.z()+16)>>4;cz++)level.getChunk(cx,cz);
        require(ExpeditionGround.walkable(level,1,x,z)!=null,"Street fixture obstructed");
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,target.x()+.5,100,target.z()-6.5,Set.of(),0,18),"Camera placement failed");
        var npc=SaoEntities.CITY_NPC.get().create(level);require(npc!=null,"Entity type");
        npc.bind(selected);npc.moveTo(x+.5,97,z+.5,0,0);
        require(level.addFreshEntity(npc),"Cannot project adventurer");
        require(!npc.atScheduledDestination(),"Fixture already at venue");
        began=level.getGameTime();
        LOG.info("SAO_VENUE_SELECTED id={} start={} target={} goal={}",id,npc.position(),target,ledger==null?"not yet assigned":ledger.goal());
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();require(level.getGameTime()-began<3000,"Venue arrival/credit timeout");
        require(level.getEntity(id) instanceof CityNpc,"NPC projection disappeared");
        var npc=(CityNpc)level.getEntity(id);
        var ledger=AdventureSimulationData.get(level.getServer()).ledger(1,id);
        if(!arrived&&npc.activity().equals("TRAIN")&&npc.atScheduledDestination()
            &&CityCourts.at(1,npc.getBlockX(),npc.getBlockZ())!=null){
            arrived=true;LOG.info("SAO_VENUE_ARRIVAL_OK position={} elapsed={}",npc.position(),level.getGameTime()-began);
        }
        if(ledger!=null&&ledger.practice()>initialPractice){
            require(arrived&&ledger.updates()>ledger.abstractUpdates(),"Practice awarded before observed arrival");
            credited=true;
            LOG.info("SAO_VENUE_CREDIT_OK practice={} physicalUpdates={} goal={}",ledger.practice()-initialPractice,ledger.updates()-ledger.abstractUpdates(),ledger.goal());
        }
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("venue-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
