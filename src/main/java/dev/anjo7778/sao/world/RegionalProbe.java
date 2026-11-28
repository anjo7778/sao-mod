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
    private static UUID observedResident;
    private static double startX,startZ;
    private static NpcDailyPlan.Plan observedPlan;
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
        if(++ticks<(phase==4?650:150))return;ticks=0;
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
                boolean movement=floor==1&&index==0;
                action(c,p->{
                    verifyResidents(p,site);
                    if(movement)startMovement(p,site);
                });
                if(movement){phase=4;}else{phase=6;}
            }
            case 4 -> {
                action(c,p->finishMovement(p));
                phase=6;
            }
            case 6 -> {
                index++;
                if(index<4){
                    action(c,p->overview(p,RegionalSettlements.sites(floor).get(index)));phase=2;
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
        for(int[] edge:new int[][]{{-h-16,0},{h+16,0},{0,-h-16},{0,h+16}}){
            int x=x0+edge[0],z=z0+edge[1];
            require(!CityRules.isSafeZone(level,x,z),"Decoration buffer incorrectly grants safety");
            level.getChunk(x>>4,z>>4);
            require(level.getBiome(new BlockPos(x,ground,z)).is(capital),"Missing decoration buffer biome at "+x+","+z);
        }
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
            var feet=level.getBlockState(pos);var head=level.getBlockState(pos.above());var base=level.getBlockState(pos.below());
            require(feet.isAir()&&head.isAir()&&base.is(Blocks.COBBLESTONE)
                &&base.isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP),
                "Road obstruction or foundation gap "+pos+" feet="+feet+" head="+head+" base="+base);
        }
        LOG.info("SAO_REGIONAL_SITE_OK floor={} kind={} cells={} paths={} safe=true",floor,site.kind(),cells,paths);
    }
    private static List<CityNpc> residents(ServerPlayer p,RegionalSettlements.Site site){
        return p.serverLevel().getEntitiesOfClass(CityNpc.class,p.getBoundingBox().inflate(100),
            e->e.isAlive()&&e.originFloor()==floor&&site.equals(Optional.ofNullable(NpcPopulationData.get(p.serverLevel().getServer()).state(floor,e.getUUID())).map(s->s.profile().regionalHome()).orElse(null)));
    }
    private static void verifyResidents(ServerPlayer p,RegionalSettlements.Site site){
        var data=NpcPopulationData.get(p.serverLevel().getServer());var roster=data.regionalRoster(floor,site.kind());
        require(roster.size()==RegionalSettlements.buildings(site).size(),"Regional roster not discovered");
        var actors=residents(p,site);
        require(!actors.isEmpty()&&actors.size()<=roster.size(),"No local NPC projections or duplicate population");
        var ids=new HashSet<UUID>();
        for(var actor:actors){
            require(ids.add(actor.getUUID()),"Duplicate projected identity");
            require(data.state(floor,actor.getUUID())!=null,"No authoritative resident record");
        }
        var regs=p.serverLevel().registryAccess();
        var restored=NpcPopulationData.load(data.save(new net.minecraft.nbt.CompoundTag(),regs),regs);
        require(restored.regionalRoster(floor,site.kind()).equals(roster),"Regional roster round trip");
        LOG.info("SAO_REGIONAL_NPCS_OK floor={} kind={} profiles={} actors={}",floor,site.kind(),roster.size(),actors.size());
    }
    private static void startMovement(ServerPlayer p,RegionalSettlements.Site site){
        var level=p.serverLevel();var data=NpcPopulationData.get(level.getServer());var center=FloorLayout.center(floor);
        for(var actor:residents(p,site)){
            var state=data.state(floor,actor.getUUID());
            var plan=NpcDailyPlan.at(state,11500);
            if(!actor.activity().equals("WORK")||plan.activity()!=NpcDailyPlan.Activity.SOCIALIZE||
                actor.distanceToSqr(center.x()+plan.destination().x()+.5,CityPlan.GROUND+1,center.z()+plan.destination().z()+.5)<20*20)continue;
            observedResident=actor.getUUID();startX=actor.getX();startZ=actor.getZ();observedPlan=plan;
            level.setDayTime(11500);return;
        }
        throw new IllegalStateException("No work-to-social resident fixture among naturally projected actors");
    }
    private static void finishMovement(ServerPlayer p){
        var level=p.serverLevel();var center=FloorLayout.center(floor);
        require(level.getEntity(observedResident) instanceof CityNpc,"Regional resident vanished during route");
        var actor=(CityNpc)level.getEntity(observedResident);
        double moved=Math.hypot(actor.getX()-startX,actor.getZ()-startZ);
        double remaining=Math.hypot(actor.getX()-(center.x()+observedPlan.destination().x()+.5),actor.getZ()-(center.z()+observedPlan.destination().z()+.5));
        require(actor.matchesPlan(observedPlan)&&moved>=12&&remaining<=8,"Regional schedule movement failed moved="+moved+" remaining="+remaining);
        // Direct server interaction fixture: exercise the actual greeting handler, not a fake memory insertion.
        var data=NpcPopulationData.get(level.getServer());var before=data.state(floor,observedResident);
        actor.interact(p,net.minecraft.world.InteractionHand.MAIN_HAND);
        var after=data.state(floor,observedResident);
        require(after.memories().size()>before.memories().size(),"Regional greeting did not persist");
        LOG.info("SAO_REGIONAL_MOVEMENT_OK moved={} remaining={} greeting=true",moved,remaining);
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
