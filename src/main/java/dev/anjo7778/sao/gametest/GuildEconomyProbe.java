package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import java.util.*;

@EventBusSubscriber(modid="sao")
public final class GuildEconomyProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static boolean running,paid;
    private static UUID id;
    private static NpcRoster.State candidate(long seed){
        return NpcRoster.generate(seed,1).stream().filter(s->s.profile().occupation()==NpcRoster.Occupation.ADVENTURER
            &&!NpcRoutine.resting(s.profile(),6000)).findFirst().orElseThrow();
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        String phase=System.getProperty("sao.guildEconomyProbe","off");
        if(phase.equals("off"))return;
        var server=event.getServer();var level=server.overworld();
        require(server.isDedicatedServer()&&server.getWorldData().getLevelName().equals("sao-guild-economy-test")&&CityRules.hasRegionalSettlements(level),"Not isolated test world");
        var initial=candidate(level.getSeed());id=initial.profile().id();var pop=NpcPopulationData.get(server);
        if(phase.equals("write")){
            require(pop.discoveredCapitals()==0,"Fresh world required");pop.discover(1,level.getSeed());
            // Controlled fixture: ready returning scout with low supplies and depleted merchants.
            // Actual completion, treasury payout, wholesale debit and purchase use production code.
            var n=initial.needs();pop.updateNeeds(1,id,new AdventurerMind.Needs(n.health(),20,n.equipment(),n.partyReadiness(),n.danger()));
            var tag=pop.save(new CompoundTag(),level.registryAccess());
            var list=tag.getList("capitals",10).getCompound(0).getList("npcs",10);
            for(int i=0;i<list.size();i++){
                var entry=list.getCompound(i);
                if(entry.getString("job").equals("MERCHANT"))entry.putInt("sold",AdventurerMarket.Service.SUPPLIES.initialStock());
            }
            var seeded=NpcPopulationData.load(tag,level.registryAccess());seeded.setDirty();
            level.getDataStorage().set("sao_population",seeded);
            var sim=AdventureSimulationData.get(server);sim.update(1,id,0,AdventureProgress.Mode.IDLE,true);
            var data=sim.save(new CompoundTag(),level.registryAccess());
            var e=data.getList("capitals",10).getCompound(0).getList("npcs",10).getCompound(0);
            var trip=e.getCompound("trip");trip.putString("phase","RETURN");trip.putBoolean("abstract",true);trip.putBoolean("surveyed",true);
            var loaded=AdventureSimulationData.load(data,level.registryAccess());loaded.setDirty();
            level.getDataStorage().set("sao_adventures",loaded);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);level.setDayTime(6000);
            server.tickRateManager().setTickRate(200.0f);
            running=true;LOG.info("SAO_GUILD_FIXTURE id={}",id);
        }else if(phase.equals("read")){
            LOG.info("SAO_GUILD_READ_OK {}",snapshot(server));server.halt(false);
        }else throw new IllegalArgumentException("Unknown probe phase");
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        if(!running)return;
        var server=event.getServer();var pop=NpcPopulationData.get(server);var s=pop.state(1,id);var w=pop.wallet(s.profile());
        require(server.getPlayerList().getPlayers().isEmpty(),"Unexpected player");
        if(!paid&&w.earned()>0){
            require(w.col()==110&&w.claimedThrough()==1&&pop.treasury(1).col()==1190,"Commission transfer");
            paid=true;LOG.info("SAO_GUILD_COMMISSION_OK col={} treasury={}",w.col(),pop.treasury(1).col());
        }
        if(w.orders()>0){
            require(paid,"Purchase before completion");
            LOG.info("SAO_GUILD_WRITE_OK {}",snapshot(server));running=false;server.halt(false);
        }
        require(server.overworld().getGameTime()/AdventureProgress.EPOCH_TICKS<10,"Guild cycle not completed");
    }
    private static String snapshot(MinecraftServer server){
        var level=server.overworld();var pop=NpcPopulationData.get(server);var state=pop.state(1,id);var w=pop.wallet(state.profile());
        var t=pop.treasury(1);var sim=AdventureSimulationData.get(server);
        require(sim.ledger(state).trip().completed()==1&&!sim.ledger(state).trip().active(),"Completion missing");
        require(w.earned()==10&&w.claimedThrough()==1&&w.supplyOrders()==1&&w.repairOrders()==0&&w.col()==98,"Wallet");
        require(state.needs().supplies()==44&&state.needs().equipment()==candidate(level.getSeed()).needs().equipment()-4,"Service and wear");
        require(t.commissions()==1&&t.supplyReleased()==6&&t.repairReleased()==0&&t.col()==1238,"Treasury and reserve");
        int restocked=0,sold=0,merchants=0;long tills=0;var receipts=new ArrayList<String>();
        for(var seller:pop.roster(1)){
            if(seller.profile().occupation()!=NpcRoster.Occupation.MERCHANT)continue;
            merchants++;var shop=pop.shop(seller.profile());restocked+=shop.restocked();sold+=shop.sold();tills+=shop.till();
            if(shop.restocked()>0){require(shop.remaining()==5,"Stock not conserved");receipts.add(seller.profile().id()+":"+shop);}
        }
        require(restocked==6&&sold==merchants*24+1,"Exactly one restock and sale");
        long original=(long)merchants*24*12+AdventurerMarket.STARTING_COL+AdventurerMarket.STARTING_TREASURY;
        require(tills+w.col()+t.col()==original,"Currency not conserved relative to fixture");
        require(level.getForcedChunks().isEmpty()&&server.getLevel(FloorTravel.UPPER).getChunkSource().getLoadedChunksCount()==0,"Unnecessary chunk loading");
        for(var site:RegionalSettlements.sites(1))require(!level.hasChunkAt(new BlockPos(site.x(),CityPlan.GROUND+1,site.z())),"Regional chunk loaded");
        require(SaoWorldData.get(server).highestUnlocked()==1,"Service unlocked floor");
        Collections.sort(receipts);
        return "id="+id+",wallet="+w+",needs="+state.needs()+",treasury="+t+",receipts="+receipts;
    }
    private static void require(boolean b,String message){if(!b)throw new IllegalStateException(message);}
}
