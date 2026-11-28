package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.*;

@EventBusSubscriber(modid="sao")
public final class MigrationProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static int start=-1;
    private static UUID id;
    private static boolean unlocked;
    private static AdventureProgress.Ledger eligibleLedger(){
        return new AdventureProgress.Ledger(23,8,0,3,2,12,12,AdventureProgress.Mode.IDLE,
            new ScoutingTrip.Trip(ScoutingTrip.Phase.IDLE,ScoutingTrip.Gate.EAST,23,200,1,0,0,false,false),
            new SurveyJournal(1,0,0),new AdventurerGoals.Goal(AdventurerGoals.Kind.NONE,0,0,0,200,1,0)).onFloor(1);
    }
    private static NpcRoster.State candidate(long seed){
        return NpcRoster.generate(seed,1).stream().filter(s->FloorMigration.eligible(s,eligibleLedger(),25,6000))
            .filter(s->s.profile().personality().sociability()>=30).findFirst().orElseThrow();
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        var phase=System.getProperty("sao.migrationProbe","off");
        if(phase.equals("off"))return;
        var server=event.getServer();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("mts-migration-test")
            &&CityRules.hasLandmarks(server.overworld()),"Not isolated test world");
        id=candidate(server.overworld().getSeed()).profile().id();
        var pop=NpcPopulationData.get(server);
        if(phase.equals("write")){
            require(pop.discoveredCapitals()==0,"World must be fresh");
            require(pop.discover(1,server.overworld().getSeed()),"Create starting roster");
            // Controlled eligibility fixture only; migration itself is entirely normal production code.
            var sim=AdventureSimulationData.get(server);
            sim.update(1,id,23,AdventureProgress.Mode.IDLE,true);
            var root=sim.save(new CompoundTag(),server.overworld().registryAccess());
            var entry=root.getList("capitals",10).getCompound(0).getList("npcs",10).getCompound(0);
            entry.putIntArray("stats",new int[]{8,0,3,2,12,12});entry.putIntArray("journal",new int[]{1,0,0});
            var trip=entry.getCompound("trip");trip.putLong("next",200);trip.putIntArray("counts",new int[]{1,0,0});
            var goal=entry.getCompound("goal");goal.putInt("completed",1);goal.putLong("next",200);
            var seeded=AdventureSimulationData.load(root,server.overworld().registryAccess());seeded.setDirty();
            server.overworld().getDataStorage().set("sao_adventures",seeded);
            var other=pop.roster(1).stream().filter(s->NpcSocial.offer(pop.state(1,id),s,0).map(e->e.friendly()).orElse(false)).findFirst().orElseThrow();
            require(pop.converse(1,id,other.profile().id(),0).isPresent(),"Memory fixture");
            server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            server.overworld().setDayTime(6000);
            server.tickRateManager().setTickRate(200.0f);
            start=server.getTickCount();
            LOG.info("SAO_MIGRATION_FIXTURE id={} birth=1",id);
        }else if(phase.equals("read")){
            LOG.info("SAO_MIGRATION_READ_OK {}",snapshot(server));server.halt(false);
        }else throw new IllegalArgumentException("Unknown probe phase");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(start<0)return;
        var server=event.getServer();var pop=NpcPopulationData.get(server);
        require(server.getPlayerList().getPlayers().isEmpty(),"Unexpected player");
        long epoch=server.overworld().getGameTime()/AdventureProgress.EPOCH_TICKS;
        if(!unlocked&&epoch>=25){
            require(pop.state(1,id)!=null&&pop.state(2,id)==null,"Locked floor incorrectly reached");
            LOG.info("SAO_MIGRATION_LOCKED_OK epoch={}",epoch);
            require(SaoWorldData.get(server).recordVictory(1,FloorProgress.Victor.PLAYER),"Unlock fixture");
            require(pop.discover(2,server.overworld().getSeed()),"Destination fixture");
            unlocked=true;
        }
        if(unlocked&&pop.state(2,id)!=null){
            var moved=pop.state(2,id);
            var ledger=AdventureSimulationData.get(server).ledger(moved);
            if(ledger.epoch()<=moved.profile().relocatedAt())return;
            require(server.getLevel(FloorTravel.UPPER).getChunkSource().getLoadedChunksCount()==0,"Migration loaded upper chunks");
            require(server.getLevel(FloorTravel.UPPER).getForcedChunks().isEmpty(),"Migration forced chunks");
            LOG.info("SAO_MIGRATION_WRITE_OK {}",snapshot(server));start=-1;server.halt(false);
        }
        require(epoch<34,"No autonomous transfer by deadline");
    }
    private static String snapshot(MinecraftServer server){
        var pop=NpcPopulationData.get(server);var sim=AdventureSimulationData.get(server);
        var moved=pop.state(2,id);
        require(moved!=null&&pop.state(1,id)==null,"Duplicate or missing migrated actor");
        var p=moved.profile();var initial=candidate(server.overworld().getSeed());
        require(p.birthFloor()==1&&p.currentFloor()==2&&p.relocatedAt()>0&&p.id().equals(initial.profile().id())
            &&p.name().equals(initial.profile().name())&&p.combatClass()==initial.profile().combatClass()
            &&p.personality().equals(initial.profile().personality()),"Identity changed");
        require(moved.memories().stream().anyMatch(m->NpcSocial.socialMemory(m)&&m.day()==0),"Original social memory lost");
        var l=sim.ledger(moved);
        require(l!=null&&l.practice()>=8&&l.goal().completed()>=1&&l.trip().completed()>=1,"Progress lost");
        require(l.journalFor(1).surveyed()==1&&l.journal().surveyed()==0,"Local journal contamination");
        require(sim.ledger(2,id)==null&&sim.ledger(1,id)!=null,"Lifetime ledger duplicated");
        int a=0,b=0;Set<UUID> identities=new HashSet<>();
        for(long floor:new long[]{1,2})for(var state:pop.roster(floor)){
            require(identities.add(state.profile().id()),"Duplicate population identity");
            if(state.profile().occupation()==NpcRoster.Occupation.ADVENTURER){if(floor==1)a++;else b++;}
        }
        require(a+b==72&&a>=12&&b<=48&&b>24,"Population was created or lost");
        require(SaoWorldData.get(server).highestUnlocked()==2,"Migration granted a floor unlock");
        return "id="+id+",birth=1,current=2,epoch="+p.relocatedAt()+",source="+a+",destination="+b
            +",practice="+l.practice()+",goals="+l.goal().completed()+",oldJournal="+l.journalFor(1)+",currentJournal="+l.journal()
            +",memories="+moved.memories().size()+",ledgerEpoch="+l.epoch();
    }
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
