package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.core.BlockPos;
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
public final class RegionalJourneyProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static boolean running;
    private static UUID id;
    private static RegionalJourney.Phase last=RegionalJourney.Phase.IDLE;
    private static int startedSupplies;
    private static AdventureProgress.Ledger ready(){
        return new AdventureProgress.Ledger(0,8,0,3,2,12,12,AdventureProgress.Mode.IDLE,
            new ScoutingTrip.Trip(ScoutingTrip.Phase.IDLE,ScoutingTrip.Gate.EAST,0,200,0,0,0,false,false),
            SurveyJournal.EMPTY,new AdventurerGoals.Goal(AdventurerGoals.Kind.NONE,0,0,0,200,0,0)).onFloor(1);
    }
    private static NpcRoster.State candidate(long seed){
        return NpcRoster.generate(seed,1).stream().filter(s->RegionalJourney.advance(s,ready(),RegionalJourney.EMPTY,1,6000,true).began()).findFirst().orElseThrow();
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("sao.regionalJourneyProbe","off");
        if(phase.equals("off"))return;
        var server=event.getServer();var level=server.overworld();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("sao-regional-journey-test")&&CityRules.hasRegionalSettlements(level),"Not isolated regional test world");
        var initial=candidate(level.getSeed());id=initial.profile().id();startedSupplies=initial.needs().supplies();
        var pop=NpcPopulationData.get(server);
        if(phase.equals("write")){
            require(pop.discoveredCapitals()==0,"Fresh world required");pop.discover(1,level.getSeed());
            // Seed readiness only; every phase transition and supply debit is production simulation.
            var sim=AdventureSimulationData.get(server);sim.update(1,id,0,AdventureProgress.Mode.IDLE,true);
            var root=sim.save(new CompoundTag(),level.registryAccess());
            var entry=root.getList("capitals",10).getCompound(0).getList("npcs",10).getCompound(0);
            entry.putIntArray("stats",new int[]{8,0,3,2,12,12});entry.getCompound("trip").putLong("next",200);entry.getCompound("goal").putLong("next",200);
            var seeded=AdventureSimulationData.load(root,level.registryAccess());seeded.setDirty();
            level.getDataStorage().set("sao_adventures",seeded);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
            server.tickRateManager().setTickRate(200.0f);
            running=true;LOG.info("SAO_JOURNEY_FIXTURE id={}",id);
        }else if(phase.equals("read")){
            LOG.info("SAO_JOURNEY_READ_OK {}",snapshot(server));server.halt(false);
        }else throw new IllegalArgumentException("Unknown probe phase");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!running)return;
        var server=event.getServer();var pop=NpcPopulationData.get(server);var sim=AdventureSimulationData.get(server);
        var s=pop.state(1,id);var j=sim.journey(s);
        require(server.getPlayerList().getPlayers().isEmpty(),"Unexpected player");
        require(server.overworld().getEntity(id)==null,"Unexpected physical projection");
        require(!sim.ledger(s).trip().active(),"Simultaneous scouting");
        if(j.phase()!=last){
            require(j.phase().ordinal()==(last.ordinal()+1)%4,"Skipped travel phase");
            require(s.needs().supplies()==startedSupplies-RegionalJourney.supplyCost(1),"Repeated or missing supply debit");
            if(j.visiting())require(pop.regionalRoster(1,j.target()).size()==RegionalSettlements.buildings(RegionalSettlements.site(1,j.target())).size(),"Arrival did not initialize regional population");
            LOG.info("SAO_JOURNEY_PHASE {} target={} epoch={}",j.phase(),j.target(),j.since());last=j.phase();
        }
        if(j.completed()>0){
            LOG.info("SAO_JOURNEY_WRITE_OK {}",snapshot(server));running=false;server.halt(false);
        }
        require(server.overworld().getGameTime()/AdventureProgress.EPOCH_TICKS<30,"Autonomous journey did not complete");
    }
    private static String snapshot(MinecraftServer server){
        var level=server.overworld();var pop=NpcPopulationData.get(server);var sim=AdventureSimulationData.get(server);
        var initial=candidate(level.getSeed());var state=pop.state(1,id);var j=sim.journey(state);var ledger=sim.ledger(state);
        require(!j.active()&&j.completed()==1,"Expected one completed round trip");
        require(state.profile().equals(initial.profile())&&state.needs().supplies()==initial.needs().supplies()-RegionalJourney.supplyCost(1),"Identity or debit mismatch");
        require(state.needs().equipment()==initial.needs().equipment()&&state.needs().health()==initial.needs().health(),"Unearned service benefit");
        require(ledger.practice()==8&&ledger.trip().completed()==0&&ledger.goal().completed()==0,"Unearned progression");
        require(pop.roster(1).size()==148&&pop.discoveredCapitals()==1,"Capital population changed");
        require(SaoWorldData.get(server).highestUnlocked()==1,"Regional journey unlocked floor");
        for(var site:RegionalSettlements.sites(1)){
            require(!level.hasChunkAt(new BlockPos(site.x(),CityPlan.GROUND+1,site.z())),"Regional trip loaded settlement chunk");
        }
        require(level.getForcedChunks().isEmpty()&&server.getLevel(FloorTravel.UPPER).getForcedChunks().isEmpty(),"Forced chunks");
        require(server.getLevel(FloorTravel.UPPER).getChunkSource().getLoadedChunksCount()==0,"Upper chunks loaded");
        var locals=pop.regionalRoster(1,j.target());
        require(locals.size()==RegionalSettlements.buildings(RegionalSettlements.site(1,j.target())).size(),"Missing saved local roster");
        require(locals.stream().map(s->s.profile().id()).distinct().count()==locals.size(),"Duplicate resident");
        return "id="+id+",journey="+j+",supplies="+state.needs().supplies()+",ledger="+ledger+",locals="+locals.stream().map(s->s.profile().id().toString()).sorted().toList();
    }
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
