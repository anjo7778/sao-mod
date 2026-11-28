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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Test-only branch. Actual generated models, block states, pathfinding, lighting, and cameras. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class TemplateProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.templateProbe");
    private static final List<CityPlan.Plot> plots=representatives();
    private static CompletableFuture<Void> pending;
    private static int phase,index,ticks,courtIndex;
    private static final List<CityCourts.Site> courts=courtRepresentatives(1);
    private TemplateProbe(){}
    private static List<CityPlan.Plot> representatives(){
        var map=new EnumMap<CityArchitecture.Style,CityPlan.Plot>(CityArchitecture.Style.class);
        for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++){
            var p=CityPlan.plot(1,x,z);if(p==null)continue;
            map.merge(CityArchitecture.style(p),p,(a,b)->a.x()*a.x()+a.z()*a.z()<=b.x()*b.x()+b.z()*b.z()?a:b);
        }
        return List.copyOf(map.values());
    }
    private static List<CityCourts.Site> courtRepresentatives(long floor){
        var out=new EnumMap<CityCourts.Kind,CityCourts.Site>(CityCourts.Kind.class);
        int n=CityPlan.half(floor)/CityPlan.LOT;
        for(int gap=-n;gap<=n;gap++)for(int row=-n;row<=n;row++){
            var site=CityCourts.site(floor,gap,row);
            if(site!=null)out.merge(site.kind(),site,(a,b)->a.x()*a.x()+a.z()*a.z()<=b.x()*b.x()+b.z()*b.z()?a:b);
        }
        return List.copyOf(out.values());
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==8)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-template-test")),"Disposable save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-template-test",
                new LevelSettings("SAO template test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),
                access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null){ticks=0;return;}
        if(++ticks<60)return;
        ticks=0;
        switch(phase){
            case 1 -> {
                require(Math.abs(c.player.getX())<=16&&Math.abs(c.player.getZ())<=16&&Math.abs(c.player.getY()-97)<=1,"Spawn regression");
                require(plots.size()==8,"All styles required");
                c.options.hideGui=true;
                action(c,p->{
                    p.serverLevel().setDayTime(6000);
                    require(FloorGuardian.challenge(p)==0,"City protection regression");
                    var actors=p.serverLevel().getEntitiesOfClass(CityNpc.class,p.getBoundingBox().inflate(104),CityNpc::isAlive);
                    require(!actors.isEmpty(),"NPC presence regression");
                    LOG.info("SAO_TEMPLATE_SPAWN_OK npcs={}",actors.size());
                    camera(p,0.5,144,-18.5,42,100,60);
                });
                phase=2;
            }
            case 2 -> {
                capture(c,"00-city-overview");
                action(c,p->{
                    var level=p.serverLevel();
                    var extra=CityFabric.infillPlot(1,0,1);
                    require(extra!=null,"Missing boulevard infill");
                    // Validate a complete new building, including chunk-crossing cells.
                    verify(level,1,extra);
                    LOG.info("SAO_DISTRICT_INFILL_OK floor=1 x={} z={}",extra.x(),extra.z());
                    camera(p,0.5,101,35.5,1,101,76);
                });
                phase=13;
            }
            case 13 -> {
                capture(c,"city-boulevard");
                action(c,p->{
                    var extra=CityFabric.infillPlot(1,0,1);
                    camera(p,extra.x()+.5,97.1,extra.z()-3.5,extra.x()+.5,98,extra.z()+3);
                });
                phase=14;
            }
            case 14 -> {
                capture(c,"infill-home-interior");
                action(c,p->{
                    // Compare exact template blocks and collision states in a normal street.
                    var level=p.serverLevel();
                    for(int x=-60;x<=60;x++){
                        var pos=new BlockPos(x,CityPlan.GROUND+1,24);
                        level.getChunk(x>>4,24>>4);
                        require(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty(),"Obstructed cross street at "+pos);
                    }
                    LOG.info("SAO_DISTRICT_STREET_OK");
                    require(courts.size()==4,"Missing four district court types");
                    courtOverview(p,courts.get(courtIndex));
                });
                phase=15;
            }
            case 15 -> {
                var site=courts.get(courtIndex);
                capture(c,"court-"+site.kind().name().toLowerCase(Locale.ROOT)+"-overview");
                action(c,p->camera(p,site.x()+.5,97.1,site.z()-7.5,site.x()+.5,98.5,site.z()+3.5));
                phase=16;
            }
            case 16 -> {
                var site=courts.get(courtIndex);
                capture(c,"court-"+site.kind().name().toLowerCase(Locale.ROOT)+"-walk");
                courtIndex++;
                if(courtIndex<courts.size()){
                    var next=courts.get(courtIndex);
                    action(c,p->courtOverview(p,next));phase=15;
                }else{
                    action(c,p->exterior(p,plots.get(index)));phase=3;
                }
            }
            case 3 -> {
                var plot=plots.get(index);var style=CityArchitecture.style(plot);
                capture(c,style.name().toLowerCase(Locale.ROOT)+"-exterior");
                action(c,p->interior(p,plot));
                phase=4;
            }
            case 4 -> {
                var plot=plots.get(index);var style=CityArchitecture.style(plot);
                capture(c,style.name().toLowerCase(Locale.ROOT)+"-interior");
                action(c,p->verify(p.serverLevel(),1,plot));
                if(style==CityArchitecture.Style.INN){phase=10;return;}
                index++;
                phase=index<plots.size()?5:6;
            }
            case 10 -> {
                var plot=plots.get(index);
                action(c,p->camera(p,plot.x()-3.5,97.1,plot.z()+1.5,plot.x()-3.5,98.2,plot.z()+6.5));
                phase=11;
            }
            case 11 -> {
                capture(c,"inn-sleeping-alcove");
                index++;phase=index<plots.size()?5:6;
            }
            case 5 -> {
                action(c,p->exterior(p,plots.get(index)));phase=3;
            }
            case 6 -> {
                action(c,p->{
                    var level=p.serverLevel();
                    boolean moved=false;
                    for(int x:new int[]{416,432,448,464,480})for(int z:new int[]{96,112,128}){
                        if(moved)break;
                        level.getChunk(x>>4,z>>4);
                        int y=level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z);
                        if(!FloorTravel.isOutdoorArrival(level,new BlockPos(x,y,z)))continue;
                        p.getAbilities().flying=false;p.onUpdateAbilities();
                        require(p.teleportTo(level,x+.5,y,z+.5,Set.of(),0,0),"Guardian test teleport");
                        moved=true;
                    }
                    require(moved,"No safe exterior spot");
                });phase=7;
            }
            case 7 -> {
                action(c,p->{
                    var level=p.serverLevel();
                    require(FloorGuardian.challenge(p)==1,"Guardian challenge failed");
                });phase=12;
            }
            case 12 -> {
                action(c,p->{
                    var level=p.serverLevel();
                    var data=SaoWorldData.get(level.getServer());
                    // Allow entity registration and chunk tracking to settle before checking the guardian.
                    require(level.getEntity(data.activeGuardian()) instanceof net.minecraft.world.entity.monster.Ravager,"Guardian not registered in loaded arena");
                    var boss=(net.minecraft.world.entity.monster.Ravager)level.getEntity(data.activeGuardian());
                    require(!CityRules.isCity(level,boss.getBlockX(),boss.getBlockZ()),"Guardian inside capital");
                    require(boss.hurt(p.damageSources().playerAttack(p),10000f)&&data.highestUnlocked()==2,"Real guardian kill regression");
                    require(FloorTravel.request(p,2)==1,"Upper travel failed");
                    var upper=p.serverLevel();
                    verify(upper,2,CityPlan.plot(2,1,1));
                    var extra=CityFabric.infillPlot(2,0,1);
                    require(extra!=null,"Missing upper boulevard infill");
                    verify(upper,2,extra);
                    LOG.info("SAO_DISTRICT_INFILL_OK floor=2 x={} z={}",extra.x(),extra.z());
                    var upperCourts=courtRepresentatives(2);
                    require(!upperCourts.isEmpty(),"No upper court");
                    verifyCourt(upper,2,upperCourts.getFirst());
                    LOG.info("SAO_TEMPLATE_UPPER_OK");
                    LOG.info("SAO_TEMPLATE_PROBE_PASS");
                });phase=9;
            }
            case 9 -> {
                capture(c,"09-upper-capital");phase=8;
                LOG.info("SAO_TEMPLATE_CAPTURE_COMPLETE");
            }
            default -> {}
        }
    }
    private static void courtOverview(ServerPlayer p,CityCourts.Site s){
        var level=p.serverLevel();
        // Use the generated site, not a fixture or a reconstructed image.
        verifyCourt(level,1,s);
        camera(p,s.x()+.5,112,s.z()-9.5,s.x()+.5,97.5,s.z()+.5);
    }
    private static void verifyCourt(ServerLevel level,long floor,CityCourts.Site s){
        var center=FloorLayout.center(floor);
        int x0=center.x()+s.x(),z0=center.z()+s.z(),ground=CityPlan.GROUND;
        int hw=CityCourts.HALF_X,hd=CityCourts.HALF_Z;
        for(int cx=(x0-hw-3)>>4;cx<=(x0+hw+3)>>4;cx++)
            for(int cz=(z0-hd-3)>>4;cz<=(z0+hd+3)>>4;cz++)level.getChunk(cx,cz);
        int compared=0,fixtures=0;
        for(int x=-hw;x<=hw;x++)for(int z=-hd;z<=hd;z++)for(int y=0;y<=CityCourts.HEIGHT;y++){
            char symbol=CityCourts.cell(s.kind(),x,y,z);
            var pos=new BlockPos(x0+x,ground+y,z0+z);
            var state=level.getBlockState(pos);
            require(state.equals(CourtPalette.state(symbol)),"Court block-state mismatch "+s.kind()+" "+pos+" "+state);
            if(symbol=='f'||symbol=='l')require(state.canSurvive(level,pos),"Unsupported court fixture "+pos);
            if(y>0&&symbol!='.')fixtures++;
            compared++;
        }
        var npc=SaoEntities.CITY_NPC.get().create(level);
        require(npc!=null,"NPC type missing");
        npc.moveTo(x0+.5,ground+1,z0-hd+.5,0,0);npc.setOnGround(true);
        var path=npc.getNavigation().createPath(new BlockPos(x0,ground+1,z0+hd-1),0);
        require(path!=null&&path.canReach(),"Court through-route failed "+s.kind());
        LOG.info("SAO_COURT_OK floor={} kind={} x={} z={} cells={} fixtures={} path=true",floor,s.kind(),s.x(),s.z(),compared,fixtures);
    }
    private static void exterior(ServerPlayer p,CityPlan.Plot plot){
        var b=BuildingTemplates.forPlot(plot);
        camera(p,plot.x()+b.halfWidth()+8.5,105,plot.z()-b.halfDepth()-11.5,plot.x()+.5,102,plot.z()+.5);
    }
    private static void interior(ServerPlayer p,CityPlan.Plot plot){
        var b=BuildingTemplates.forPlot(plot);
        camera(p,plot.x()+.5,97.1,plot.z()-b.halfDepth()+4.5,plot.x()+.5,98,plot.z()+3.5);
    }
    private static void camera(ServerPlayer p,double x,double y,double z,double tx,double ty,double tz){
        p.getAbilities().flying=true;p.onUpdateAbilities();
        double dx=tx-x,dz=tz-z;
        float yaw=(float)-Math.toDegrees(Math.atan2(dx,dz)),pitch=(float)-Math.toDegrees(Math.atan2(ty-y-1.62,Math.hypot(dx,dz)));
        require(p.teleportTo(p.serverLevel(),x,y,z,Set.of(),yaw,pitch),"Camera teleport failed");
        p.setDeltaMovement(0,0,0);p.fallDistance=0;
    }
    private static void verify(ServerLevel level,long floor,CityPlan.Plot plot){
        require(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator gen&&gen.generatorSettings().is(floor==1?FloorTravel.FIRST_CITY_LANDMARKS:FloorTravel.UPPER_CITY_LANDMARKS),"Wrong template generator");
        var model=BuildingTemplates.forPlot(plot);
        var center=FloorLayout.center(floor);
        int x0=center.x()+plot.x(),z0=center.z()+plot.z(),y0=CityPlan.GROUND;
        int hw=model.halfWidth(),hd=model.halfDepth();
        for(int cx=(x0-hw-3)>>4;cx<=(x0+hw+3)>>4;cx++)for(int cz=(z0-hd-3)>>4;cz<=(z0+hd+3)>>4;cz++)level.getChunk(cx,cz);
        var palette=new TemplateCapitalPalette(FloorCatalog.themeFor(floor));
        int compared=0,beds=0,fixtures=0,light=15,walkable=0;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int z=-hd;z<=hd;z++)for(int x=-hw;x<=hw;x++){
            char ground=model.at(x,0,z);
            for(int y=0;y<model.height();y++){
                char symbol=model.at(x,y,z);
                if(y==0&&symbol=='.')continue;
                pos.set(x0+x,y0+y,z0+z);
                var expect=palette.state(symbol,plot);
                var got=level.getBlockState(pos);
                require(got.equals(expect),"Block-state mismatch "+model.style()+" at "+x+","+y+","+z+": "+got+" expected "+expect);
                if(symbol=='h')beds++;
                if("BCFATSLJ".indexOf(symbol)>=0)fixtures++;
                if(symbol=='l'||symbol=='H')require(got.canSurvive(level,pos),"Unsupported lantern "+pos);
                compared++;
            }
            if("=+i".indexOf(ground)>=0&&model.at(x,1,z)=='.'&&model.at(x,2,z)=='.'){
                walkable++;
                light=Math.min(light,level.getBrightness(LightLayer.BLOCK,new BlockPos(x0+x,y0+1,z0+z)));
            }
        }
        require(walkable>10,"No walking space");
        // Check engine pathfinding, not just mathematical floor connectivity.
        var npc=SaoEntities.CITY_NPC.get().create(level);
        require(npc!=null,"NPC type missing");
        npc.moveTo(x0+.5,y0+1,z0-hd-1+.5,0,0);npc.setOnGround(true);
        var path=npc.getNavigation().createPath(new BlockPos(x0+3,y0+1,z0-3),0);
        require(path!=null&&path.canReach(),"Actual NPC path to destination failed "+model.style());
        require(light>=8,"Too dark: "+model.style()+" minimum block light "+light);
        LOG.info("SAO_TEMPLATE_MODEL_OK floor={} style={} cells={} fixtures={} beds={} walkable={} minBlockLight={} path=true",floor,model.style(),compared,fixtures,beds,walkable,light);
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("template-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
            LOG.info("SAO_TEMPLATE_CAPTURE {}",name);
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
