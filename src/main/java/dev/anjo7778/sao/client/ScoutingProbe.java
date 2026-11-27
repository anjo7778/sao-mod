package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
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

/** Controlled trip-start fixture; all walking and subsequent transitions are production code. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class ScoutingProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.scoutProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static UUID id;
    private static long began;
    private static boolean outside,survey,complete,outsideCapture,returnCapture;
    private ScoutingProbe(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        Minecraft c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-scout-test")),"Disposable save already exists");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-scout-test",
                new LevelSettings("SAO scouting test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),
                access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=100){
            c.options.hideGui=true;
            action(c,ScoutingProbe::setup);
            phase=2;ticks=0;return;
        }
        if(phase!=2)return;
        if(outside&&!outsideCapture){
            capture(c,"01-outside-gate");outsideCapture=true;
        }
        if(complete&&!returnCapture){
            capture(c,"02-returned-to-capital");returnCapture=true;
            LOG.info("SAO_SCOUT_PROBE_PASS");
            phase=3;return;
        }
        if(ticks%20!=0)return;
        action(c,ScoutingProbe::observe);
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        require(CityRules.hasCities(level),"Wrong world");
        level.setDayTime(6000);
        var pop=NpcPopulationData.get(server);
        pop.discover(1,server.overworld().getSeed());
        var state=pop.roster(1).stream().filter(s->s.profile().occupation()==NpcRoster.Occupation.ADVENTURER&&level.getEntity(s.profile().id())==null).findFirst().orElseThrow();
        id=state.profile().id();
        var n=state.needs();
        require(pop.updateNeeds(1,id,new AdventurerMind.Needs(n.health(),n.supplies()-ScoutingTrip.SUPPLY_COST,n.equipment(),n.partyReadiness(),n.danger())),"Fixture supply reservation");
        state=pop.state(1,id);
        var sim=AdventureSimulationData.get(server);
        long epoch=level.getGameTime()/AdventureProgress.EPOCH_TICKS;
        if(sim.ledger(1,id)==null)sim.update(1,id,epoch,AdventureProgress.Mode.IDLE,false);
        var root=sim.save(new CompoundTag(),level.registryAccess());
        boolean patched=false;
        var capitals=root.getList("capitals",10);
        for(int i=0;i<capitals.size();i++){
            var group=capitals.getCompound(i);
            if(group.getLong("floor")!=1)continue;
            var npcs=group.getList("npcs",10);
            for(int k=0;k<npcs.size();k++){
                var entry=npcs.getCompound(k);
                if(!entry.getUUID("id").equals(id))continue;
                entry.putLong("epoch",epoch);
                CompoundTag trip=new CompoundTag();
                trip.putString("phase","OUTBOUND");trip.putString("gate","EAST");
                trip.putLong("since",epoch);trip.putLong("next",epoch);
                trip.putIntArray("counts",new int[]{0,0,0});
                trip.putBoolean("abstract",false);trip.putBoolean("surveyed",false);
                entry.put("trip",trip);patched=true;
            }
        }
        require(patched,"Cannot seed controlled trip");
        var loaded=AdventureSimulationData.load(root,level.registryAccess());loaded.setDirty();
        server.overworld().getDataStorage().set("sao_adventures",loaded);
        int h=CityPlan.half(1);
        // Load just the nearby fixture chunks, and check actual unmodified ground.
        for(int x=h-32;x<=h+48;x+=16)for(int z=-16;z<=16;z+=16)level.getChunk(x>>4,z>>4);
        var inner=ExpeditionGround.walkable(level,1,h-16,0);
        require(inner!=null&&ExpeditionGround.walkable(level,1,h+12,0)!=null&&ExpeditionGround.walkable(level,1,h+36,0)!=null,"No walkable scout route in fixture");
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,h-6.5,102,5.5,Set.of(),-100,18),"Cannot observe gate");
        var npc=SaoEntities.CITY_NPC.get().create(level);
        require(npc!=null,"NPC type missing");
        npc.bind(state);npc.moveTo(inner.getX()+.5,inner.getY(),inner.getZ()+.5,-90,0);
        require(level.addFreshEntity(npc),"NPC projection failed");
        began=level.getGameTime();
        LOG.info("SAO_SCOUT_FIXTURE id={} start={} seeded=true",id,npc.position());
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        require(level.getGameTime()-began<6500,"Trip did not finish before deadline");
        require(level.getEntity(id) instanceof CityNpc,"Observed NPC disappeared");
        var npc=(CityNpc)level.getEntity(id);
        var trip=AdventureSimulationData.get(server).trip(1,id);
        require(trip!=null,"Missing trip state");
        int h=CityPlan.half(1);
        if(!outside&&npc.getX()>h&&!CityRules.isCity(level,npc.getBlockX(),npc.getBlockZ())){
            outside=true;camera(p,npc);
            LOG.info("SAO_SCOUT_CROSSED_GATE pos={}",npc.position());
        }
        if(!survey&&trip.phase()==ScoutingTrip.Phase.RETURN&&trip.surveyed()){
            require(outside&&npc.getX()>h,"Survey completed without leaving city");
            survey=true;
            LOG.info("SAO_SCOUT_SURVEY_OK pos={}",npc.position());
        }
        if(trip.completed()>0){
            require(outside&&survey&&trip.completed()==1&&trip.observed()==1&&trip.aborted()==0&&!trip.active(),"Incorrect observed completion");
            require(CityRules.isCity(level,npc.getBlockX(),npc.getBlockZ()),"Returned actor still outside");
            require(SaoWorldData.get(server).highestUnlocked()==1,"Scout unlocked a floor");
            complete=true;camera(p,npc);
            LOG.info("SAO_SCOUT_RETURN_OK pos={} observed={}",npc.position(),trip.observed());
        }
    }
    private static void camera(ServerPlayer p,CityNpc npc){
        double x=npc.getX()-5,z=npc.getZ()+5;
        float yaw=(float)-Math.toDegrees(Math.atan2(npc.getX()-x,npc.getZ()-z));
        require(p.teleportTo(p.serverLevel(),x,npc.getY()+3,z,Set.of(),yaw,18),"Camera teleport failed");
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{
            var p=server.getPlayerList().getPlayer(uuid);
            require(p!=null,"Missing test player");a.accept(p);
        },server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("scout-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception ex){throw new IllegalStateException("Capture failed",ex);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
