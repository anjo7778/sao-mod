package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.CityPlan;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class CityProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.cityProbe");
    private static int phase,settled;
    private static CompletableFuture<Void> pending;
    private CityProbe(){}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==5)return;
        Minecraft c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            if(Files.exists(c.gameDirectory.toPath().resolve("saves/sao-city-test")))throw new IllegalStateException("Fresh test save required");
            c.options.renderDistance().set(8);
            c.options.simulationDistance().set(5);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            c.createWorldOpenFlows().createFreshLevel("sao-city-test",
                new LevelSettings("SAO city test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),
                access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null){settled=0;return;}
        if(++settled<100)return;
        settled=0;
        switch(phase){
            case 1 -> {
                if(!c.player.onGround()||Math.abs(c.player.getX())>16||Math.abs(c.player.getZ())>16||Math.abs(c.player.getY()-97)>1)
                    throw new IllegalStateException("Player did not spawn in the plaza: "+c.player.position());
                c.options.hideGui=true;
                capture(c,"01-spawn");
                action(c,p->{
                    verifyCapital(p.serverLevel(),1);
                    if(FloorGuardian.challenge(p)!=0)throw new IllegalStateException("Guardian accepted in safe city");
                    var spawn=new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE,p.serverLevel(),MobSpawnType.NATURAL,p.blockPosition(),p.getRandom(),true);
                    NeoForge.EVENT_BUS.post(spawn);
                    if(spawn.getPlacementCheckResult())throw new IllegalStateException("Hostile spawn not blocked");
                    var villager=EntityType.VILLAGER.create(p.serverLevel());
                    if(villager==null)throw new IllegalStateException("Missing damage fixture");
                    villager.moveTo(p.getX()+3,p.getY(),p.getZ(),0,0);
                    if(!p.serverLevel().addFreshEntity(villager))throw new IllegalStateException("Could not create damage fixture");
                    float hp=villager.getHealth();
                    if(villager.hurt(p.damageSources().playerAttack(p),5)||villager.getHealth()!=hp)
                        throw new IllegalStateException("City damage not blocked");
                    villager.discard();
                    LOG.info("SAO_CITY_SAFE_ZONE_OK");
                    point(p,12.5,130,-18.5,-45,20,true);
                });
                phase=2;
            }
            case 2 -> {
                capture(c,"02-capital-overview");
                action(c,p->point(p,48.5,98,24.5,0,0,true));
                phase=3;
            }
            case 3 -> {
                capture(c,"03-street-and-building");
                action(c,p->{
                    var level=p.serverLevel();
                    boolean moved=false;
                    for(int x:new int[]{448,464,480})for(int z:new int[]{96,112,128}){
                        if(moved)break;
                        level.getChunk(x>>4,z>>4);
                        int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                        if(!FloorTravel.isOutdoorArrival(level,new BlockPos(x,y,z)))continue;
                        point(p,x+.5,y,z+.5,0,0,false);moved=true;
                    }
                    if(!moved||FloorGuardian.challenge(p)!=1)throw new IllegalStateException("Exterior guardian challenge failed");
                    var data=SaoWorldData.get(level.getServer());
                    if(!(level.getEntity(data.activeGuardian()) instanceof Ravager boss)||CityRules.isCity(level,boss.getBlockX(),boss.getBlockZ()))
                        throw new IllegalStateException("Wrong guardian position");
                    if(!boss.hurt(p.damageSources().playerAttack(p),10000f)||data.highestUnlocked()!=2)
                        throw new IllegalStateException("Actual guardian death did not unlock floor");
                    if(FloorTravel.request(p,2)!=1)throw new IllegalStateException("Capital travel failed");
                    verifyCapital(p.serverLevel(),2);
                    LOG.info("SAO_CITY_GUARDIAN_AND_UPPER_OK");
                });
                phase=4;
            }
            case 4 -> {
                if(!c.level.dimension().equals(FloorTravel.UPPER)||Math.abs(c.player.getY()-97)>1)
                    throw new IllegalStateException("Incorrect arrival");
                capture(c,"04-upper-capital-arrival");
                LOG.info("SAO_CITY_PROBE_PASS");
                phase=5;
            }
            default -> {}
        }
    }

    private static void verifyCapital(ServerLevel level,long floor){
        int y=CityPlan.GROUND;
        for(int[] pos:new int[][]{{0,0},{0,80},{48,48},{48,31},{63,48},{30,CityPlan.half(floor)-1},{0,CityPlan.half(floor)-1}})
            level.getChunk(pos[0]>>4,pos[1]>>4);
        var capital=ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("sao","capital"));
        if(!level.getBiome(new BlockPos(0,y,0)).is(capital))throw new IllegalStateException("Missing city biome");
        if(!level.getBlockState(new BlockPos(0,y,0)).is(Blocks.SMOOTH_STONE)||
           !level.getBlockState(new BlockPos(0,y+1,0)).isAir())throw new IllegalStateException("Missing plaza");
        if(!level.getBlockState(new BlockPos(0,y,80)).is(Blocks.COBBLESTONE))throw new IllegalStateException("Missing road");
        var plot=CityPlan.plot(floor,1,1);
        int doorZ=plot.z()-plot.depth();
        if(!level.getBlockState(new BlockPos(plot.x(),y+1,doorZ)).isAir()||
           !level.getBlockState(new BlockPos(plot.x(),y+2,doorZ)).isAir())throw new IllegalStateException("Blocked door");
        var floorBlock=floor==1?Blocks.OAK_PLANKS:Blocks.SPRUCE_PLANKS;
        if(!level.getBlockState(new BlockPos(48,y,48)).is(floorBlock)||
           !level.getBlockState(new BlockPos(48,y+2,48)).isAir())throw new IllegalStateException("Missing building floor/interior");
        if(level.getBlockState(new BlockPos(48,y+1,50)).isAir())throw new IllegalStateException("Missing furnishing");
        int h=CityPlan.half(floor);
        if(level.getBlockState(new BlockPos(30,y+4,h-1)).isAir()||
            !level.getBlockState(new BlockPos(0,y+2,h-1)).isAir())throw new IllegalStateException("Missing wall/gate");
        LOG.info("SAO_CITY_BLOCKS_OK floor={} role={}",floor,plot.role());
    }

    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> action){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{
            var p=server.getPlayerList().getPlayer(uuid);
            if(p==null)throw new IllegalStateException("Missing player");
            action.accept(p);
        },server::execute);
    }
    private static void point(ServerPlayer p,double x,double y,double z,float yaw,float pitch,boolean flying){
        p.getAbilities().flying=flying;p.onUpdateAbilities();
        if(!p.teleportTo(p.serverLevel(),x,y,z,Set.of(),yaw,pitch))throw new IllegalStateException("Camera teleport failed");
        p.setDeltaMovement(0,0,0);p.fallDistance=0;
    }
    private static void capture(Minecraft c,String name){
        try {
            var dir=c.gameDirectory.toPath().resolve("city-probe");
            Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
            LOG.info("SAO_CITY_CAPTURE {}",name);
        } catch(Exception e){throw new IllegalStateException("Screenshot failed",e);}
    }
}
