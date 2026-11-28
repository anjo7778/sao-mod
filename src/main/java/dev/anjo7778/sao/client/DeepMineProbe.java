package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.item.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Natural prospect worldgen + actual production AI. Only initial actor survey is seeded. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class DeepMineProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.deepMineProbe");
    private static final boolean EXTENDED=Boolean.getBoolean("sao.extendedMineProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static UUID actor,smith;
    private static long began,baselineMining;
    private static NpcSupplyData.Cargo collected;
    private static final Map<BlockPos,GateProspects.Resource> natural=new LinkedHashMap<>();
    private static NpcForgeWorkshop.Fixtures forge;
    private static boolean captured,delivered,finished;
    private static long lastTrace;
    private static boolean descended;
    private static int entryY,depthY;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-deep-mine-test")),"Fresh isolated save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-deep-mine-test",
                new LevelSettings("SAO deep mine test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=80){c.options.hideGui=true;action(c,DeepMineProbe::setup);phase=2;ticks=0;return;}
        if(phase!=2)return;
        if(collected!=null&&!captured){capture(c,"01-gathering");captured=true;}
        if(finished){capture(c,"02-workshop-complete");LOG.info("SAO_DEEP_MINE_PROBE_PASS");phase=3;return;}
        if(ticks%10==0)action(c,DeepMineProbe::observe);
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        require(CityRules.hasRegionalSettlements(level),"Wrong world");
        level.setDayTime(11500);
        var pop=NpcPopulationData.get(server);pop.discover(1,server.overworld().getSeed());
        var s=pop.roster(1).stream().filter(HuntContract::ready)
            .filter(v->!NpcRoutine.resting(v.profile(),11500)&&level.getEntity(v.profile().id())==null).findFirst().orElseThrow();
        actor=s.profile().id();
        var progress=CharacterProgressData.get(server);
        baselineMining=progress.state(new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,actor)).masteryExperience(CharacterProgress.Mastery.MINING);
        var sim=AdventureSimulationData.get(server);
        long surveySince=level.getGameTime()/AdventureProgress.EPOCH_TICKS;
        if(sim.ledger(s)==null)sim.update(1,actor,surveySince,AdventureProgress.Mode.IDLE,false);
        var root=sim.save(new CompoundTag(),level.registryAccess());
        boolean patched=false;var groups=root.getList("capitals",10);
        for(int i=0;i<groups.size();i++){
            var group=groups.getCompound(i);if(group.getLong("floor")!=1)continue;
            var list=group.getList("npcs",10);
            for(int j=0;j<list.size();j++){
                var e=list.getCompound(j);if(!e.getUUID("id").equals(actor))continue;
                e.putLong("epoch",surveySince);
                var trip=e.getCompound("trip");trip.putString("phase","SURVEY");trip.putString("gate","EAST");
                trip.putLong("since",surveySince);trip.putLong("next",surveySince);
                trip.putIntArray("counts",new int[]{0,0,0});trip.putBoolean("abstract",false);trip.putBoolean("surveyed",false);
                patched=true;
            }
        }
        require(patched,"Cannot seed survey fixture");
        var loaded=AdventureSimulationData.load(root,level.registryAccess());loaded.setDirty();server.overworld().getDataStorage().set("sao_adventures",loaded);
        var site=ScoutingTrip.gatePoint(1,ScoutingTrip.Gate.EAST,36);
        int x=site.x(),z=site.z();
        for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=(z-16)>>4;cz<=(z+16)>>4;cz++)level.getChunk(cx,cz);
        for(var col:GateProspects.columns(1,ScoutingTrip.Gate.EAST))for(int y=CityPlan.GROUND+18;y>=CityPlan.GROUND-18;y--){
            var pos=new BlockPos(col.x(),y,col.z());var b=level.getBlockState(pos);
            boolean match=switch(col.resource()){
                case IRON->b.is(Blocks.IRON_ORE);
                case COAL->b.is(Blocks.COAL_ORE);
                case DEADWOOD->b.is(Blocks.OAK_LOG);
            };
            if(match&&level.getBlockState(pos.above()).isAir()){natural.put(pos,col.resource());break;}
        }
        // A depleted surface is an explicit test fixture. Never create mining blocks or stock.
        for(var pos:natural.keySet())level.destroyBlock(pos,false);
        natural.clear();
        // Isolated mining acceptance: disable managed woodland only in this fixture.
        for(var tree:GateWoodlandPlan.sites(1,ScoutingTrip.Gate.EAST))
            for(int y=CityPlan.GROUND-20;y<=CityPlan.GROUND+16;y++){
                var at=new BlockPos(tree.x(),y,tree.z());
                if(level.getBlockState(at).is(Blocks.ROOTED_DIRT))level.setBlockAndUpdate(at,Blocks.DIRT.defaultBlockState());
            }
        var route=EXTENDED?GateMineExtension.route(1,ScoutingTrip.Gate.EAST):GateMinePlan.route(1,ScoutingTrip.Gate.EAST);
        for(var v:route)level.getChunk(v.x()>>4,v.z()>>4);
        if(EXTENDED)for(var vein:GateMinePlan.veins(1,ScoutingTrip.Gate.EAST)){
            var v=vein.point();level.getChunk(v.x()>>4,v.z()>>4);
            var pos=new BlockPos(v.x(),v.y(),v.z());var b=level.getBlockState(pos);
            if(b.is(Blocks.IRON_ORE)||b.is(Blocks.DEEPSLATE_IRON_ORE)||b.is(Blocks.COAL_ORE)||b.is(Blocks.DEEPSLATE_COAL_ORE))
                level.destroyBlock(pos,false);
        }
        entryY=route.getFirst().y();depthY=route.getLast().y();
        for(var v:route){
            var pos=new BlockPos(v.x(),v.y(),v.z());
            require(level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                &&level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                &&level.getBlockState(pos.below()).isFaceSturdy(level,pos.below(),net.minecraft.core.Direction.UP),"Generated mine route blocked "+pos);
        }
        var expectedVeins=EXTENDED?GateMineExtension.veins(1,ScoutingTrip.Gate.EAST):GateMinePlan.veins(1,ScoutingTrip.Gate.EAST);
        for(var vein:expectedVeins){
            var v=vein.point();level.getChunk(v.x()>>4,v.z()>>4);
            var pos=new BlockPos(v.x(),v.y(),v.z());var state=level.getBlockState(pos);
            boolean match=vein.resource()==GateProspects.Resource.IRON?state.is(Blocks.IRON_ORE)||state.is(Blocks.DEEPSLATE_IRON_ORE)
                :state.is(Blocks.COAL_ORE)||state.is(Blocks.DEEPSLATE_COAL_ORE);
            if(match)natural.put(pos,vein.resource());
        }
        require(natural.size()>=6,"Actual deep ore pockets missing");
        LOG.info("SAO_DEEP_MINE_NATURAL_OK pockets={} depth={} extended={}",natural.size(),entryY-depthY,EXTENDED);
        var at=ExpeditionGround.walkable(level,1,x,z);require(at!=null,"No gate surface");
        var npc=SaoEntities.CITY_NPC.get().create(level);require(npc!=null,"NPC missing");
        npc.bind(s);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        require(level.addFreshEntity(npc),"Actor spawn rejected");
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,x-6.5,at.getY()+4,z+7.5,Set.of(),-135,15),"Camera failed");
        began=level.getGameTime();LOG.info("SAO_DEEP_MINE_FIXTURE actor={} at={}",actor,at);
    }
    private static int count(BarrelBlockEntity barrel,net.minecraft.world.item.Item item){
        int n=0;for(int i=0;i<barrel.getContainerSize();i++)if(barrel.getItem(i).is(item))n+=barrel.getItem(i).getCount();return n;
    }
    private static void follow(ServerPlayer p,CityNpc npc){
        if(p.distanceToSqr(npc)>25*25){
            require(p.teleportTo(p.serverLevel(),npc.getX()-4,npc.getY()+4,npc.getZ()+4,Set.of(),-135,15),"Follow camera");
        }
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();var pop=NpcPopulationData.get(server);
        require(level.getGameTime()-began<(EXTENDED?2600:1800),"Deep mine timed out");
        require(level.getEntity(actor) instanceof CityNpc,"Physical miner vanished");
        var npc=(CityNpc)level.getEntity(actor);follow(p,npc);
        var supplies=NpcSupplyData.get(server);var cargo=supplies.cargo(actor);
        if(level.getGameTime()-lastTrace>=80){
            LOG.info("SAO_DEEP_MINE_TRACE age={} pos={} route={} target={} cargo={}",level.getGameTime()-began,npc.position(),npc.activity(),npc.getNavigation().getTargetPos(),cargo);
            lastTrace=level.getGameTime();
        }
        if(!descended&&npc.getY()<=depthY+3){
            descended=true;LOG.info("SAO_DEEP_MINE_DESCENT_OK y={}",npc.getY());
        }
        if(cargo!=null&&cargo.size()>0){
            if(collected==null||cargo.size()>collected.size()){
                require(descended,"Ore without descending");
                collected=cargo;
                long iron=natural.entrySet().stream().filter(e->e.getValue()==GateProspects.Resource.IRON&&level.getBlockState(e.getKey()).isAir()).count();
                long coal=natural.entrySet().stream().filter(e->e.getValue()==GateProspects.Resource.COAL&&level.getBlockState(e.getKey()).isAir()).count();
                require(iron==cargo.iron()&&coal==cargo.coal()&&cargo.pickUses()==250-cargo.iron()-cargo.coal(),"Actual deep extraction conservation");
                var restored=NpcSupplyData.load(supplies.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
                require(restored.cargo(actor).equals(cargo),"Saved actual ore");
                LOG.info("SAO_DEEP_MINE_GATHER_OK {}",cargo);
            }
        }
        if(descended&&collected!=null&&collected.size()>0&&npc.getY()>=entryY-2){
            var progress=CharacterProgressData.get(server);
            require(progress.state(new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,actor)).masteryExperience(CharacterProgress.Mastery.MINING)==baselineMining+4L*(collected.iron()+collected.coal()),"Real mining mastery");
            LOG.info("SAO_DEEP_MINE_RETURN_OK y={} cargo={}",npc.getY(),cargo);
            finished=true;
        }
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("deep-mine-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
