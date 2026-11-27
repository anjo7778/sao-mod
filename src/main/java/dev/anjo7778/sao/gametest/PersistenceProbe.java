package dev.anjo7778.sao.gametest;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.FloorProgress;
import dev.anjo7778.sao.world.FloorTravel;
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
            LOG.info("SAO_PERSISTENCE_WRITE_OK floor={}",data.highestUnlocked());
        } else if (phase.equals("read")) {
            if(data.highestUnlocked()!=2 || !data.isUnlocked(2) || data.isUnlocked(3)
                || data.recordVictory(1,FloorProgress.Victor.PLAYER))
                throw new IllegalStateException("Progress did not survive actual restart");
            if(!upper.getBlockState(MARKER).is(Blocks.CHISELED_STONE_BRICKS))
                throw new IllegalStateException("Upper-floor block modification was lost");
            LOG.info("SAO_PERSISTENCE_READ_OK floor={} marker={}",data.highestUnlocked(),MARKER);
        } else throw new IllegalArgumentException("Invalid probe phase");
        // The vanilla graceful shutdown flushes SavedData and all dimensions to disk.
        server.halt(false);
    }
}
