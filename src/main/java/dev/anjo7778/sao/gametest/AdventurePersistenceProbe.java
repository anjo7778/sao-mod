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
    private static boolean evening;
    private AdventurePersistenceProbe(){}

    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("sao.adventureProbe","off");
        if(phase.equals("off"))return;
        var server=event.getServer();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("mts-adventure-test")
            &&CityRules.hasCities(server.overworld())&&server.getLevel(FloorTravel.UPPER)!=null,
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
        if(!evening&&elapsed>=1450){
            server.overworld().setDayTime(12000);evening=true;
        }
        if(elapsed>=2250){
            require(server.getLevel(FloorTravel.UPPER).getChunkSource().getLoadedChunksCount()==0,"Upper chunks loaded");
            require(server.getLevel(FloorTravel.UPPER).getForcedChunks().isEmpty(),"Forced chunk ticket");
            LOG.info("SAO_ADVENTURE_SAVE_OK {}",snapshot(server));
            start=-1;server.halt(false);
        }
    }
    private static String snapshot(MinecraftServer server){
        var pop=NpcPopulationData.get(server);var sim=AdventureSimulationData.get(server);
        StringBuilder out=new StringBuilder();int paired=0;
        for(long floor:new long[]{1,2}){
            int count=0,updates=0,practice=0,rests=0,social=0,fatigue=0;
            for(var s:pop.roster(floor)){
                if(s.profile().occupation()!=NpcRoster.Occupation.ADVENTURER)continue;
                count++;
                var l=sim.ledger(floor,s.profile().id());
                require(l!=null&&l.abstractUpdates()>0,"Adventurer did not advance");
                require(l.updates()==l.abstractUpdates(),"Unexpected physical update");
                updates+=l.updates();practice+=l.practice();rests+=l.restSessions();fatigue+=l.fatigue();
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
            paired+=social;
            out.append("floor=").append(floor).append(",actors=").append(count).append(",updates=").append(updates)
                .append(",practice=").append(practice).append(",rests=").append(rests).append(",fatigue=").append(fatigue)
                .append(",memories=").append(social).append(';');
        }
        require(paired%2==0,"Non-reciprocal total");
        return out.toString();
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
