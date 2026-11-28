package dev.anjo7778.sao.world;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.entity.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class RegionalProbe{
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.regionalProbe");
    private static int phase,ticks,index;
    private static long floor=1;
    private static CompletableFuture<Void> pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==5)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-regional-test")),"Fresh world required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-regional-test",
                new LevelSettings("SAO regional test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),
                access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null){ticks=0;return;}
        if(++ticks<100)return;ticks=0;
        switch(phase){
            case 1 -> {
                require(Math.abs(c.player.getX())<=16&&Math.abs(c.player.getZ())<=16,"Capital spawn lost");
                c.options.hideGui=true;
                action(c,p->{
                    require(CityRules.hasRegionalSettlements(p.serverLevel()),"Wrong regional preset");
                    require(CityRules.isCity(p.serverLevel(),0,0),"Capital regression");
                    p.serverLevel().setDayTime(6000);
                    overview(p,RegionalSettlements.sites(1).get(index));
                });
                phase=2;
            }
            case 2 -> {
                var site=RegionalSettlements.sites(floor).get(index);
                capture(c,"floor-"+floor+"-"+site.kind().name().toLowerCase(Locale.ROOT)+"-overview");
                action(c,p->{
                    verify(p,site);
                    var center=FloorLayout.center(floor);
                    camera(p,center.x()+site.x()+.5,97.1,center.z()+site.z()-12.5,0,0);
                });phase=3;
            }
            case 3 -> {
                var site=RegionalSettlements.sites(floor).get(index);
                capture(c,"floor-"+floor+"-"+site.kind().name().toLowerCase(Locale.ROOT)+"-street");
                index++;
                if(index<4&&floor==1){
                    action(c,p->overview(p,RegionalSettlements.sites(1).get(index)));phase=2;
                }else if(floor==1){
                    action(c,p->{
                        // Test fixture unlock only, no production synthetic victory.
                        require(SaoWorldData.get(p.serverLevel().getServer()).recordVictory(1,FloorProgress.Victor.PLAYER),"Fixture unlock");
                        require(FloorTravel.request(p,2)==1,"Actual upper-floor travel failed");
                        floor=2;index=0;
                        overview(p,RegionalSettlements.sites(2).get(0));
                    });phase=2;
                }else{
                    LOG.info("SAO_REGIONAL_PROBE_PASS");phase=5;
                }
            }
            default -> {}
        }
    }
    private static void overview(ServerPlayer p,RegionalSettlements.Site site){
        var center=FloorLayout.center(floor);
        p.serverLevel().setDayTime(6000);
        camera(p,center.x()+site.x()+.5,166,center.z()+site.z()-52.5,0,50);
    }
    private static void verify(ServerPlayer p,RegionalSettlements.Site site){
        var level=p.serverLevel();var center=FloorLayout.center(floor);
        int x0=center.x()+site.x(),z0=center.z()+site.z(),ground=CityPlan.GROUND;
        int h=site.half();
        for(int cx=(x0-h)>>4;cx<=((x0+h-1)>>4);cx++)
            for(int cz=(z0-h)>>4;cz<=((z0+h-1)>>4);cz++)level.getChunk(cx,cz);
        require(CityRules.isSafeZone(level,x0,z0)&&!CityRules.isCity(level,x0,z0),"Regional zone classified as capital");
        require(!CityRules.isSafeZone(level,x0+h+1,z0),"Safety leaks outside settlement");
        var capital=ResourceKey.create(Registries.BIOME,ResourceLocation.fromNamespaceAndPath("sao","capital"));
        require(level.getBiome(new BlockPos(x0,ground,z0)).is(capital),"Missing settlement biome");
        var spawn=new MobSpawnEvent.SpawnPlacementCheck(EntityType.ZOMBIE,level,MobSpawnType.NATURAL,new BlockPos(x0,ground+1,z0),p.getRandom(),true);
        NeoForge.EVENT_BUS.post(spawn);
        require(!spawn.getPlacementCheckResult(),"Hostile spawn not blocked");
        var palette=new TemplateCapitalPalette(FloorCatalog.themeFor(floor));
        int cells=0,paths=0;
        for(var b:RegionalSettlements.buildings(site)){
            var m=b.model();int bx=center.x()+b.x(),bz=center.z()+b.z();
            for(int x=-m.halfWidth();x<=m.halfWidth();x++)for(int z=-m.halfDepth();z<=m.halfDepth();z++)
                for(int y=0;y<m.height();y++){
                    char symbol=m.at(x,y,z);if(y==0&&symbol=='.')continue;
                    var pos=new BlockPos(bx+x,ground+y,bz+z);
                    var state=level.getBlockState(pos);
                    require(state.equals(palette.state(symbol,b.plot())),"Building state mismatch "+site.kind()+" "+pos+" "+state);
                    if(symbol=='H'||symbol=='l')require(state.canSurvive(level,pos),"Unsupported light "+pos);
                    cells++;
                }
            var npc=SaoEntities.CITY_NPC.get().create(level);require(npc!=null,"Missing path fixture");
            npc.moveTo(bx+.5,ground+1,bz-m.halfDepth()-1+.5,0,0);npc.setOnGround(true);
            var path=npc.getNavigation().createPath(new BlockPos(bx+3,ground+1,bz-3),0);
            require(path!=null&&path.canReach(),"Regional entry path failed "+site.kind()+" "+b.plot().role());
            paths++;
        }
        for(int i=-h;i<h;i++)for(int[] offset:new int[][]{{i,0},{0,i}}){
            var pos=new BlockPos(x0+offset[0],ground+1,z0+offset[1]);
            require(level.getBlockState(pos).isAir()&&level.getBlockState(pos.above()).isAir()
                &&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP),"Road obstruction or foundation gap "+pos);
        }
        LOG.info("SAO_REGIONAL_SITE_OK floor={} kind={} cells={} paths={} safe=true",floor,site.kind(),cells,paths);
    }
    private static void camera(ServerPlayer p,double x,double y,double z,float yaw,float pitch){
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(p.serverLevel(),x,y,z,Set.of(),yaw,pitch),"Camera teleport failed");
        p.setDeltaMovement(0,0,0);p.fallDistance=0;
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> action){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");action.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("regional-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
            LOG.info("SAO_REGIONAL_CAPTURE {}",name);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
