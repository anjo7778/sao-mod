package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Test-branch only: real dedicated server ticking with two seeded capital rosters and zero players. */
@EventBusSubscriber(modid="sao")
public final class AdventurePersistenceProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static int start=-1;
    private static long lastEpoch=-1;
    private static int venueSamples=0;
    private AdventurePersistenceProbe(){}

    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("sao.adventureProbe","off");
        if(phase.equals("off"))return;
        var server=event.getServer();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("mts-adventure-test")
            &&CityRules.hasLandmarks(server.overworld())&&server.getLevel(FloorTravel.UPPER)!=null,
            "Not isolated test world");
        require(server.getPlayerList().getPlayers().isEmpty(),"Unexpected connected player");
        var pop=NpcPopulationData.get(server);
        if(phase.equals("write")){
            require(pop.discoveredCapitals()==0,"Test save not fresh");
            // Fixture only. No production synthetic victory or discovery command.
            require(SaoWorldData.get(server).recordVictory(1,FloorProgress.Victor.PLAYER),"Fixture unlock");
            require(pop.discover(1,42)&&pop.discover(2,42),"Fixture capitals");
            server.overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            server.overworld().setDayTime(6000);
            server.tickRateManager().setTickRate(100.0f);
            start=server.getTickCount();
        }else if(phase.equals("read")){
            require(pop.discoveredCapitals()==2,"Population not restored");
            LOG.info("SAO_ADVENTURE_READ_OK {}",snapshot(server));
            server.halt(false);
        }else throw new IllegalArgumentException("Invalid phase");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(start<0)return;
        var server=event.getServer();
        require(server.getPlayerList().getPlayers().isEmpty(),"Unexpected player");
        int elapsed=server.getTickCount()-start;
        // Run actual world ticks across multiple complete day/night cycles.
        long epoch=server.overworld().getGameTime()/AdventureProgress.EPOCH_TICKS;
        if(lastEpoch!=epoch){
            lastEpoch=epoch;
            server.overworld().setDayTime(Math.floorMod(elapsed,24000));
            var pop=NpcPopulationData.get(server);
            for(long floor:new long[]{1,2}){
                var level=floor==1?server.overworld():server.getLevel(FloorTravel.UPPER);
                for(var s:pop.roster(floor)){
                    if(s.profile().occupation()!=NpcRoster.Occupation.ADVENTURER)continue;
                    var plan=AdventureSimulationData.plan(level,s);
                    if(CityCourts.at(floor,plan.destination().x(),plan.destination().z())!=null)venueSamples++;
                }
            }
        }
        if(elapsed>=58000){
            require(venueSamples>100,"No autonomous venue use");
            LOG.info("SAO_GOAL_VENUES_OK sampledPlans={}",venueSamples);
            require(server.getLevel(FloorTravel.UPPER).getChunkSource().getLoadedChunksCount()==0,"Upper chunks loaded");
            require(server.getLevel(FloorTravel.UPPER).getForcedChunks().isEmpty(),"Forced chunk ticket");
            LOG.info("SAO_ADVENTURE_SAVE_OK {}",snapshot(server));
            start=-1;server.halt(false);
        }
    }
    private static String snapshot(MinecraftServer server){
        var pop=NpcPopulationData.get(server);var sim=AdventureSimulationData.get(server);
        StringBuilder out=new StringBuilder(),exact=new StringBuilder();int paired=0,goalCompletedAll=0;
        for(long floor:new long[]{1,2}){
            int count=0,updates=0,practice=0,rests=0,social=0,fatigue=0,completed=0,aborted=0,active=0,spent=0,known=0,reported=0,multidirectional=0,goalActive=0,goalCompleted=0,goalExpired=0;
            var initial=new java.util.HashMap<java.util.UUID,NpcRoster.State>();
            for(var s:NpcRoster.generate(42,floor))initial.put(s.profile().id(),s);
            for(var s:pop.roster(floor)){
                if(s.profile().occupation()!=NpcRoster.Occupation.ADVENTURER)continue;
                count++;
                var l=sim.ledger(floor,s.profile().id());
                require(l!=null&&l.abstractUpdates()>0,"Adventurer did not advance");
                require(l.updates()==l.abstractUpdates(),"Unexpected physical update");
                updates+=l.updates();practice+=l.practice();rests+=l.restSessions();fatigue+=l.fatigue();
                var goal=l.goal();
                if(goal.active())goalActive++;
                goalCompleted+=goal.completed();goalExpired+=goal.expired();
                var trip=l.trip();
                require(trip.observed()==0,"Unobserved expedition incorrectly marked physical");
                completed+=trip.completed();aborted+=trip.aborted();if(trip.active())active++;
                int cost=initial.get(s.profile().id()).needs().supplies()-s.needs().supplies();
                require(cost==ScoutingTrip.SUPPLY_COST*(trip.completed()+trip.aborted()+(trip.active()?1:0)),"Incorrect abstract supply cost");
                spent+=cost;
                var journal=l.journal();
                require(journal.observed()==0,"Abstract trip created physical observations");
                require(Integer.bitCount(journal.surveyed())<=trip.completed(),"Knowledge without completed return");
                known+=Integer.bitCount(journal.surveyed());
                reported+=Integer.bitCount(journal.reported());
                if(Integer.bitCount(journal.surveyed())>1)multidirectional++;
                for(var gate:ScoutingTrip.Gate.values()){
                    if((journal.reported()&SurveyJournal.bit(gate))==0)continue;
                    require(pop.roster(floor).stream().anyMatch(peer->{
                        var other=sim.ledger(floor,peer.profile().id());
                        return other!=null&&other.journal().surveyed(gate);
                    }),"Report without firsthand source");
                }
                exact.append(floor).append(':').append(s).append(':').append(l).append('\n');
                for(var m:s.memories()){
                    if(!NpcSocial.socialMemory(m))continue;
                    var peer=pop.state(floor,m.actor());
                    require(peer!=null&&peer.memories().stream().anyMatch(e->e.eventId().equals(m.eventId())
                        &&e.actor().equals(s.profile().id())&&e.type()==m.type()),"Missing reciprocal offscreen memory");
                    social++;
                }
            }
            require(count==(floor==1?48:24)&&practice>0&&updates>=count,"No independent practice");
            require(social>0,"No offscreen social contacts on floor "+floor);
            require(completed>0&&known>0,"No autonomous scouting knowledge on floor "+floor);
            require(goalActive>0,"No persistent personal goals");
            goalCompletedAll+=goalCompleted;
            paired+=social;
            out.append("floor=").append(floor).append(",actors=").append(count).append(",updates=").append(updates)
                .append(",practice=").append(practice).append(",rests=").append(rests).append(",fatigue=").append(fatigue)
                .append(",memories=").append(social).append(",scouted=").append(completed).append(",aborted=").append(aborted).append(",active=").append(active).append(",suppliesSpent=").append(spent).append(",surveyedDirections=").append(known).append(",reportedDirections=").append(reported).append(",multiDirectionScouts=").append(multidirectional).append(",goalActive=").append(goalActive).append(",goalCompleted=").append(goalCompleted).append(",goalExpired=").append(goalExpired).append(';');
        }
        require(paired%2==0,"Non-reciprocal total");
        require(goalCompletedAll>0,"No autonomously completed goals");
        require(SaoWorldData.get(server).highestUnlocked()==2,"Reconnaissance granted a floor unlock");
        try{
            var digest=java.security.MessageDigest.getInstance("SHA-256").digest(exact.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return out.append("digest=").append(java.util.HexFormat.of().formatHex(digest)).toString();
        }catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
