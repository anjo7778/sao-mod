package dev.anjo7778.mtstestbridge;

import com.google.gson.*;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="mtstestbridge", value=Dist.CLIENT)
public final class ClientBridge {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Run active;
    private static boolean claimedThisProcess;
    private ClientBridge() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        Minecraft client = Minecraft.getInstance();
        if (active == null) {
            if (!claimedThisProcess && client.screen instanceof TitleScreen title) claim(client, title);
            return;
        }
        try { active.tick(client); }
        catch (Exception ex) {
            LOG.error("MTS bridge scenario failed", ex);
            active.finish(client,"failed",ex.getClass().getSimpleName()+": "+String.valueOf(ex.getMessage()));
        }
    }

    private static void claim(Minecraft client, TitleScreen title) {
        Path root = client.gameDirectory.toPath().resolve("mts-bridge");
        Path inbox = root.resolve("request.json");
        if (!Files.isRegularFile(inbox, LinkOption.NOFOLLOW_LINKS)) return;
        claimedThisProcess = true;
        try {
            if (Files.isSymbolicLink(root) || Files.size(inbox)>8192) throw new IOException("Unsafe request");
            Request req = Request.parse(Files.readString(inbox),Instant.now().getEpochSecond());
            Path out = root.resolve(req.id());
            Files.createDirectory(out); // Existing IDs must never be reused.
            Files.move(inbox,out.resolve("request.json"));
            active = new Run(req,out,client);
            active.progress("creating_world");
            Path saves = client.gameDirectory.toPath().resolve("saves");
            if (Files.isSymbolicLink(saves) || Files.exists(saves.resolve(req.saveId()), LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Disposable world already exists or save path is redirected");
            String presetName = switch (req.world()) {
                case "sao" -> "sao:aincrad";
                case "flat" -> "minecraft:flat";
                default -> "minecraft:normal";
            };
            var preset = ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.parse(presetName));
            var rules = new GameRules();
            var settings = new LevelSettings(req.worldName(),GameType.CREATIVE,false,
                req.scenario().equals("animation")?Difficulty.NORMAL:Difficulty.PEACEFUL,true,rules,WorldDataConfiguration.DEFAULT);
            client.createWorldOpenFlows().createFreshLevel(req.saveId(),settings,new WorldOptions(req.seed(),true,false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
        } catch(Exception ex) {
            LOG.error("MTS bridge request rejected",ex);
            if(active!=null) active.finish(client,"failed","World creation: "+ex.getMessage());
        }
    }

    private static final class Run {
        final Request req;
        final Path out;
        final long started = System.nanoTime();
        final List<Map<String,Object>> samples = new ArrayList<>();
        final List<String> captures = new ArrayList<>();
        final boolean oldHideGui;
        String phase="loading";
        int settle=0, frame=0, delay=0;
        int spawnX,spawnZ,actorX,actorZ,actorY;
        UUID actor;
        boolean prepared, worldSeen;
        CompletableFuture<ServerSample> task;
        long previousSampleNs;
        int previousServerTick;
        record ServerSample(long ns,int ticks,double mspt) {}

        Run(Request req, Path out, Minecraft client) {
            this.req=req;this.out=out;this.oldHideGui=client.options.hideGui;
        }

        void tick(Minecraft c) throws Exception {
            if(Files.isRegularFile(out.resolve("stop"),LinkOption.NOFOLLOW_LINKS)) { finish(c,"cancelled","Stop requested"); return; }
            if(System.nanoTime()-started > 360_000_000_000L) { finish(c,"failed","Six-minute scenario time limit");return; }
            var server=c.getSingleplayerServer();
            if (server==null || c.player==null || c.level==null) {
                if(worldSeen && server==null) { finish(c,"cancelled","Test world closed");return; }
                settle=0;return;
            }
            // Never act in a multiplayer or pre-existing world, even if a request was present.
            if(server.isPublished() || !server.getWorldData().getLevelName().equals(req.worldName())
                    || !server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().equals(
                        c.gameDirectory.toPath().resolve("saves").resolve(req.saveId()).toAbsolutePath().normalize()))
                throw new IllegalStateException("Not the isolated scenario world");
            worldSeen=true;
            if(c.screen!=null) { settle=0;return; }
            if(!prepared) {
                if(!c.player.onGround() || ++settle<60) return;
                spawnX=c.player.getBlockX();spawnZ=c.player.getBlockZ();
                prepared=true;
                c.options.hideGui=true;
                progress("running");
                schedule(c,0,true);
                delay=60;
                return;
            }
            if(task!=null) {
                if(!task.isDone()) return;
                // Completion is handled before screenshot capture; errors become a terminal report.
                task.join();
                if(delay>0){delay--;return;}
                if(!c.level.hasChunkAt(c.player.blockPosition())) return;
                ServerSample s=task.join();task=null;
                capture(c,s);
                frame++;
                progress("running");
                if(frame>=req.frameCount()) {finish(c,"passed","Scenario actions and captures completed; visual review still required");return;}
                boolean move=req.scenario().equals("terrain") && frame%2==0 || req.scenario().equals("animation") && frame==12;
                schedule(c,frame,move);
                delay=move?60:req.frameInterval()-1;
            }
        }

        void schedule(Minecraft c,int index,boolean move) {
            MinecraftServer server=c.getSingleplayerServer();
            UUID playerId=c.player.getUUID();
            task=CompletableFuture.supplyAsync(()->{
                var player=server.getPlayerList().getPlayer(playerId);
                if(player==null || !player.isCreative() || !server.getWorldData().getLevelName().equals(req.worldName()))
                    throw new IllegalStateException("Scenario player not in creative test world");
                if(req.scenario().equals("animation")) {
                    if(actor==null) prepareActor(player);
                    var entity=player.serverLevel().getEntity(actor);
                    if(!(entity instanceof Zombie z) || !z.isAlive()) throw new IllegalStateException("Animation fixture missing");
                    if(move) {
                        double x=actorX+0.5+(index<12?0:5);
                        double zz=actorZ+0.5+(index<12?-5:0);
                        point(player,x,actorY+0.1,zz,index<12?0:90,0);
                    }
                    if(index==2 || index==8 || index==14 || index==20) z.swing(InteractionHand.MAIN_HAND,true);
                } else if(move && req.scenario().equals("terrain")) {
                    int step=index/2;
                    int x=spawnX,z=spawnZ;
                    if(step==1) { x=req.world().equals("sao")?1800:spawnX+96; }
                    if(step==2) { x=req.world().equals("sao")?3850:spawnX-96; z=req.world().equals("sao")?0:spawnZ+96; }
                    var level=player.serverLevel();
                    int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                    if(y<level.getMinBuildHeight()+2 || y>level.getMaxBuildHeight()-24)
                        throw new IllegalStateException("No valid terrain at survey point "+step);
                    point(player,x+0.5,y+12,z+0.5,step==2?90:0,18);
                }
                return new ServerSample(System.nanoTime(),server.getTickCount(),server.getAverageTickTimeNanos()/1_000_000.0);
            },server::execute);
        }

        private void prepareActor(ServerPlayer player) {
            var level=player.serverLevel();
            level.setDayTime(18000);
            actorX=spawnX;actorZ=spawnZ+5;
            actorY=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,actorX,actorZ);
            Zombie z=EntityType.ZOMBIE.create(level);
            if(z==null) throw new IllegalStateException("Cannot create animation fixture");
            z.moveTo(actorX+0.5,actorY,actorZ+0.5,180,0);
            z.setNoAi(true);z.setPersistenceRequired();z.setInvulnerable(true);
            z.setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
            if(!level.addFreshEntity(z)) throw new IllegalStateException("Fixture spawn rejected");
            actor=z.getUUID();
        }

        private static void point(ServerPlayer p,double x,double y,double z,float yaw,float pitch) {
            // Same-dimension, bounded survey positions in a creative disposable world only.
            p.getAbilities().flying=true;
            p.onUpdateAbilities();
            p.teleportTo(p.serverLevel(),x,y,z,yaw,pitch);
            p.setDeltaMovement(0,0,0);p.fallDistance=0;
        }

        void capture(Minecraft c,ServerSample s) throws IOException {
            String name=String.format(Locale.ROOT,"frame-%03d.png",frame+1);
            // Native Minecraft framebuffer, not the Windows desktop. Each capture is bounded.
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())) {
                if(img.getWidth()>4096 || img.getHeight()>2160) throw new IOException("Resolution exceeds 4096x2160 test limit");
                img.writeToFile(out.resolve(name));
            }
            captures.add(name);
            Runtime rt=Runtime.getRuntime();
            Map<String,Object> m=new LinkedHashMap<>();
            m.put("frame",name);m.put("elapsed_ms",(System.nanoTime()-started)/1_000_000);
            m.put("fps",c.getFps());m.put("mspt",s.mspt());
            if(previousSampleNs!=0 && s.ns()>previousSampleNs)
                m.put("observed_tps",(s.ticks()-previousServerTick)*1e9/(s.ns()-previousSampleNs));
            previousSampleNs=s.ns();previousServerTick=s.ticks();
            m.put("heap_used_mib",(rt.totalMemory()-rt.freeMemory())/1048576.0);
            m.put("heap_max_mib",rt.maxMemory()/1048576.0);
            m.put("x",c.player.getX());m.put("y",c.player.getY());m.put("z",c.player.getZ());
            m.put("yaw",c.player.getYRot());m.put("pitch",c.player.getXRot());
            samples.add(m);
        }

        void progress(String status) throws IOException {
            phase=status;
            write("progress.json",Map.of("protocol",1,"id",req.id(),"status",status,"scenario",req.scenario(),
                "captured",captures.size(),"expected",req.frameCount()));
        }
        void finish(Minecraft c,String status,String message) {
            try {
                c.options.hideGui=oldHideGui;
                Map<String,Object> data=new LinkedHashMap<>();
                data.put("protocol",1);data.put("id",req.id());data.put("scenario",req.scenario());
                data.put("status",status);data.put("message",message);
                data.put("world",req.world());data.put("seed",req.seed());
                data.put("expected_frames",req.frameCount());data.put("captures",captures);data.put("samples",samples);
                data.put("visual_verified",false);data.put("fixture",req.scenario().equals("animation")?"vanilla zombie iron-sword swing":"none");
                data.put("metric_notes","FPS from client; MSPT server moving average; observed TPS from server tick count over wall time; heap is Java heap, not total system RAM. Screenshots can affect performance.");
                data.put("duration_ms",(System.nanoTime()-started)/1_000_000);
                write("result.json",data);
                LOG.info("MTS_BRIDGE_RESULT {} {} frames={}",req.id(),status,captures.size());
            } catch(Exception e) { LOG.error("Could not save MTS report",e); }
            finally { active=null; }
        }
        void write(String name,Object obj) throws IOException {
            Path target=out.resolve(name),tmp=out.resolve(name+".tmp");
            Files.writeString(tmp,GSON.toJson(obj));
            try { Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}
        }
    }
}
