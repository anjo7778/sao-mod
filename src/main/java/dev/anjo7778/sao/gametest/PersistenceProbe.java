package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.FloorProgress;
import dev.anjo7778.sao.simulation.FloorLayout;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.biome.Biomes;
import java.util.UUID;
import dev.anjo7778.sao.world.FloorTravel;
import dev.anjo7778.sao.world.ArrivalProbeAccess;
import dev.anjo7778.sao.world.SaoWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/** Branch-only, explicitly enabled two-process disposable dedicated-server test. */
@EventBusSubscriber(modid="sao")
public final class PersistenceProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final BlockPos MARKER=new BlockPos(12,60,12);
    private static final UUID GUARDIAN = new UUID(16,27);
    private static final long[] SURVEY = {2,3,4,6,7,1000,10000,FloorLayout.MAX_FLOOR};
    private static int stressTick = -1;
    private static int index;
    private static long begin;
    private PersistenceProbe() {}

    @SubscribeEvent
    public static void started(ServerStartedEvent event) {
        String phase=System.getProperty("sao.persistenceProbe","off");
        if (phase.equals("off")) return;
        var server=event.getServer();
        if (!server.isDedicatedServer() || !server.getWorldData().getLevelName().equals("mts-persistence-test")
            || !FloorTravel.isAincrad(server) || !server.getPlayerList().getPlayers().isEmpty())
            throw new IllegalStateException("Probe only permitted in the isolated Aincrad test server");
        var upper=server.getLevel(FloorTravel.UPPER);
        if (upper==null) throw new IllegalStateException("Upper realm missing");
        var data=SaoWorldData.get(server);
        long start=System.nanoTime();
        upper.getChunk(MARKER.getX()>>4,MARKER.getZ()>>4);
        LOG.info("SAO_PERSISTENCE_CHUNK_READY phase={} elapsed_ms={} heap_used_mib={}",phase,
            (System.nanoTime()-start)/1_000_000,
            (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);
        if (phase.equals("write")) {
            if(data.highestUnlocked()!=1 || !data.recordVictory(1,FloorProgress.Victor.NPC_PARTY))
                throw new IllegalStateException("Expected fresh sequential progression");
            if(upper.getBlockState(MARKER).is(Blocks.CHISELED_STONE_BRICKS))
                throw new IllegalStateException("Marker already exists");
            if(!upper.setBlockAndUpdate(MARKER,Blocks.CHISELED_STONE_BRICKS.defaultBlockState()))
                throw new IllegalStateException("Could not write terrain marker");
            if(!data.beginGuardian(2,GUARDIAN)) throw new IllegalStateException("Could not save guardian identity");
            LOG.info("SAO_PERSISTENCE_WRITE_OK floor={}",data.highestUnlocked());
        } else if (phase.equals("read")) {
            if(data.highestUnlocked()!=2 || !data.isUnlocked(2) || data.isUnlocked(3)
                || data.recordVictory(1,FloorProgress.Victor.PLAYER))
                throw new IllegalStateException("Progress did not survive actual restart");
            if(!upper.getBlockState(MARKER).is(Blocks.CHISELED_STONE_BRICKS))
                throw new IllegalStateException("Upper-floor block modification was lost");
            if(!GUARDIAN.equals(data.activeGuardian()) || !data.clearGuardian(GUARDIAN))
                throw new IllegalStateException("Active encounter did not survive restart");
            LOG.info("SAO_PERSISTENCE_READ_OK floor={} marker={}",data.highestUnlocked(),MARKER);
            // Test distant chunks on successive server ticks, with no player or permanent tickets.
            stressTick=server.getTickCount();
            begin=System.nanoTime();
            return;
        } else throw new IllegalArgumentException("Invalid probe phase");
        // The vanilla graceful shutdown flushes SavedData and all dimensions to disk.
        server.halt(false);
    }
    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event){
        if(stressTick<0)return;
        var server=event.getServer();
        var upper=server.getLevel(FloorTravel.UPPER);
        var cache=upper.getChunkSource();
        int elapsed=server.getTickCount()-stressTick;
        if(index<SURVEY.length && elapsed>=index*15){
            long floor=SURVEY[index++];
            var c=FloorLayout.center(floor);
            int x=c.x(),z=c.z();
            if(floor>2 && cache.getChunkNow(x>>4,z>>4)!=null)
                throw new IllegalStateException("Distant center was already loaded: "+floor);
            long start=System.nanoTime();
            upper.getChunk(x>>4,z>>4);
            int y=upper.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
            if(y<48||y>205||!upper.getBiome(new BlockPos(x,y,z)).is(Biomes.PLAINS))
                throw new IllegalStateException("Unsafe or incorrect floor center: "+floor+" y="+y);
            BlockPos arrival=ArrivalProbeAccess.find(upper,x,z);
            if(arrival==null||!FloorTravel.isOutdoorArrival(upper,arrival))
                throw new IllegalStateException("Production search found no outdoor landing for floor "+floor);
            if(floor==2){
                BlockPos roof=arrival.above(4);
                if(!upper.getBlockState(roof).isAir())throw new IllegalStateException("Expected clear sky above landing");
                upper.setBlockAndUpdate(roof,Blocks.STONE.defaultBlockState());
                if(FloorTravel.isOutdoorArrival(upper,arrival))throw new IllegalStateException("Covered landing incorrectly accepted");
                upper.setBlockAndUpdate(roof,Blocks.AIR.defaultBlockState());
                LOG.info("SAO_PERSISTENCE_COVERED_LANDING_REJECTED");
            }
            LOG.info("SAO_PERSISTENCE_SURVEY floor={} y={} generation_ms={} loaded_chunks={} heap_mib={}",
                floor,arrival.getY(),(System.nanoTime()-start)/1_000_000,cache.getLoadedChunksCount(),
                (Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1048576);
        }
        if(index==SURVEY.length && elapsed>=600){
            int retained=0;
            for(long floor:SURVEY){
                var c=FloorLayout.center(floor);
                if(cache.getChunkNow(c.x()>>4,c.z()>>4)!=null)retained++;
            }
            if(!upper.getForcedChunks().isEmpty())throw new IllegalStateException("Unexpected permanent chunk tickets");
            if(retained!=0 && elapsed<1200)return;
            if(retained!=0)throw new IllegalStateException("Distant centers did not unload: "+retained);
            LOG.info("SAO_PERSISTENCE_EVICTION_OK centers={} ticks={} duration_ms={} remaining_chunks={} average_mspt={}",
                SURVEY.length,elapsed,(System.nanoTime()-begin)/1_000_000,cache.getLoadedChunksCount(),server.getAverageTickTimeNanos()/1_000_000.0);
            stressTick=-1;
            server.halt(false);
        }
    }
}
