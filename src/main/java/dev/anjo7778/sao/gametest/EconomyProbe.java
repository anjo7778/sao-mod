package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.*;

@EventBusSubscriber(modid="sao")
public final class EconomyProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static boolean running;
    private static UUID id;
    private static int lastOrders;
    private static NpcRoster.State candidate(long seed){
        return NpcRoster.generate(seed,1).stream().filter(s->s.profile().occupation()==NpcRoster.Occupation.ADVENTURER
            &&!NpcRoutine.resting(s.profile(),6000)).findFirst().orElseThrow();
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("sao.economyProbe","off");
        if(phase.equals("off"))return;
        var server=event.getServer();var level=server.overworld();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("sao-economy-test")&&CityRules.hasRegionalSettlements(level),"Not isolated regional test world");
        var initial=candidate(level.getSeed());id=initial.profile().id();
        var pop=NpcPopulationData.get(server);
        if(phase.equals("write")){
            require(pop.discoveredCapitals()==0,"Fresh world required");pop.discover(1,level.getSeed());
            // Seed only the buyer's need. No purchases, currency transfers or reward fixtures.
            var n=initial.needs();
            require(pop.updateNeeds(1,id,new AdventurerMind.Needs(n.health(),10,20,n.partyReadiness(),n.danger())),"Seed buyer needs");
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
            server.tickRateManager().setTickRate(200.0f);
            running=true;LOG.info("SAO_ECONOMY_FIXTURE id={}",id);
        }else if(phase.equals("read")){
            LOG.info("SAO_ECONOMY_READ_OK {}",snapshot(server));server.halt(false);
        }else throw new IllegalArgumentException("Unknown probe phase");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!running)return;
        var server=event.getServer();var pop=NpcPopulationData.get(server);
        var s=pop.state(1,id);var w=pop.wallet(s.profile());
        require(server.getPlayerList().getPlayers().isEmpty(),"Unexpected player");
        require(server.overworld().getEntity(id)==null,"Unexpected projection");
        if(w.orders()!=lastOrders){
            require(w.orders()==lastOrders+1,"Skipped or duplicate order");
            LOG.info("SAO_ECONOMY_SALE count={} col={} supplies={} condition={}",w.orders(),w.col(),s.needs().supplies(),s.needs().equipment());
            lastOrders=w.orders();
        }
        if(w.orders()==3){
            LOG.info("SAO_ECONOMY_WRITE_OK {}",snapshot(server));running=false;server.halt(false);
        }
        require(server.overworld().getGameTime()/AdventureProgress.EPOCH_TICKS<15,"Autonomous purchases did not complete");
    }
    private static String snapshot(MinecraftServer server){
        var level=server.overworld();var pop=NpcPopulationData.get(server);var s=pop.state(1,id);var w=pop.wallet(s.profile());
        require(s.profile().equals(candidate(level.getSeed()).profile()),"Identity changed");
        require(w.supplyOrders()==2&&w.repairOrders()==1&&w.col()==58&&w.observedOrders()==0,"Buyer balance or order counts");
        require(s.needs().supplies()==58&&s.needs().equipment()==45,"Wrong service results");
        int suppliesSold=0,repairsSold=0,till=0;
        var receipts=new ArrayList<String>();
        for(var v:pop.roster(1)){
            var role=AdventurerMarket.forRole(v.profile().occupation());if(role==null)continue;
            var shop=pop.shop(v.profile());if(shop.sold()==0)continue;
            if(role==AdventurerMarket.Service.SUPPLIES)suppliesSold+=shop.sold();else repairsSold+=shop.sold();
            till+=shop.till();receipts.add(v.profile().id()+":"+shop.sold()+":"+shop.remaining()+":"+shop.till());
        }
        require(suppliesSold==2&&repairsSold==1&&till==42,"Stock/receipt conservation failed");
        require(w.col()+till==AdventurerMarket.STARTING_COL,"Currency created or lost");
        require(server.getLevel(FloorTravel.UPPER).getChunkSource().getLoadedChunksCount()==0&&level.getForcedChunks().isEmpty(),"Unnecessary chunk loading");
        for(var site:RegionalSettlements.sites(1))require(!level.hasChunkAt(new BlockPos(site.x(),CityPlan.GROUND+1,site.z())),"Regional chunk loaded");
        require(SaoWorldData.get(server).highestUnlocked()==1,"Service unlocked floor");
        Collections.sort(receipts);
        return "id="+id+",wallet="+w+",needs="+s.needs()+",receipts="+receipts;
    }
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
