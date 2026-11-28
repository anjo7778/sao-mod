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
public final class SupplyProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.supplyProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static UUID actor,smith;
    private static long began,baselineMining;
    private static NpcSupplyData.Cargo collected;
    private static final Map<BlockPos,GateProspects.Resource> natural=new LinkedHashMap<>();
    private static NpcForgeWorkshop.Fixtures forge;
    private static boolean captured,delivered,finished;
    private static long lastTrace;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-supply-test")),"Fresh isolated save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-supply-test",
                new LevelSettings("SAO supply test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=80){c.options.hideGui=true;action(c,SupplyProbe::setup);phase=2;ticks=0;return;}
        if(phase!=2)return;
        if(collected!=null&&!captured){capture(c,"01-gathering");captured=true;}
        if(finished){capture(c,"02-workshop-complete");LOG.info("SAO_SUPPLY_PROBE_PASS");phase=3;return;}
        if(ticks%10==0)action(c,SupplyProbe::observe);
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
        long iron=natural.values().stream().filter(v->v==GateProspects.Resource.IRON).count();
        long coal=natural.values().stream().filter(v->v==GateProspects.Resource.COAL).count();
        long logs=natural.values().stream().filter(v->v==GateProspects.Resource.DEADWOOD).count();
        require(iron>=2&&coal>=3&&logs>=1,"Insufficient generated prospects iron="+iron+" coal="+coal+" logs="+logs);
        LOG.info("SAO_SUPPLY_NATURAL_OK iron={} coal={} logs={}",iron,coal,logs);
        // This is a surface-only supply fixture. Deep extraction has its own
        // separate live probe. Remove only generated deep pockets, without drops.
        var deepPockets=new ArrayList<>(GateMinePlan.veins(1,ScoutingTrip.Gate.EAST));
        deepPockets.addAll(GateMineExtension.veins(1,ScoutingTrip.Gate.EAST));
        for(var tree:GateWoodlandPlan.sites(1,ScoutingTrip.Gate.EAST))
            for(int y=CityPlan.GROUND-20;y<=CityPlan.GROUND+16;y++){
                var pos=new BlockPos(tree.x(),y,tree.z());
                if(level.getBlockState(pos).is(Blocks.ROOTED_DIRT))level.setBlockAndUpdate(pos,Blocks.DIRT.defaultBlockState());
            }
        for(var vein:deepPockets){
            var v=vein.point();var pos=new BlockPos(v.x(),v.y(),v.z());
            level.getChunk(v.x()>>4,v.z()>>4);
            var b=level.getBlockState(pos);
            boolean matches=vein.resource()==GateProspects.Resource.IRON
                ?b.is(Blocks.IRON_ORE)||b.is(Blocks.DEEPSLATE_IRON_ORE)
                :b.is(Blocks.COAL_ORE)||b.is(Blocks.DEEPSLATE_COAL_ORE);
            if(matches)level.destroyBlock(pos,false);
        }
        var at=ExpeditionGround.walkable(level,1,x,z);require(at!=null,"No gate surface");
        var npc=SaoEntities.CITY_NPC.get().create(level);require(npc!=null,"NPC missing");
        npc.bind(s);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        require(level.addFreshEntity(npc),"Actor spawn rejected");
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,x-6.5,at.getY()+4,z+7.5,Set.of(),-135,15),"Camera failed");
        began=level.getGameTime();LOG.info("SAO_SUPPLY_FIXTURE actor={} at={}",actor,at);
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
        require(level.getGameTime()-began<4800,"Physical supply chain timed out; cargo="+NpcSupplyData.get(server).cargo(actor));
        var supplies=NpcSupplyData.get(server);var cargo=supplies.cargo(actor);
        if(!delivered){
            require(level.getEntity(actor) instanceof CityNpc,"Physical gatherer vanished");
            var npc=(CityNpc)level.getEntity(actor);follow(p,npc);
            if(level.getGameTime()-lastTrace>=100){
                var state=pop.state(1,actor);var sim=AdventureSimulationData.get(server);
                var desired=AdventureSimulationData.plan(level,state);
                LOG.info("SAO_SUPPLY_TRACE age={} pos={} actual={} desired={} matched={} reached={} pathDone={} target={} trip={} epoch={} cargo={}",level.getGameTime()-began,npc.position(),npc.activity(),desired,npc.matchesPlan(desired),npc.atScheduledDestination(),npc.getNavigation().isDone(),npc.getNavigation().getTargetPos(),sim.trip(state),sim.ledger(state).epoch(),cargo);
                lastTrace=level.getGameTime();
            }
            if(cargo==null)return;
            if(cargo.size()>0){
                if(collected==null||cargo.size()>collected.size()){
                    collected=cargo;smith=cargo.smith();
                    var state=pop.state(1,smith);require(state!=null,"Assigned smith missing");
                    forge=NpcForgeWorkshop.fixtures(state.profile());
                    long brokenIron=natural.entrySet().stream().filter(e->e.getValue()==GateProspects.Resource.IRON&&level.getBlockState(e.getKey()).isAir()).count();
                    long brokenCoal=natural.entrySet().stream().filter(e->e.getValue()==GateProspects.Resource.COAL&&level.getBlockState(e.getKey()).isAir()).count();
                    long brokenLogs=natural.entrySet().stream().filter(e->e.getValue()==GateProspects.Resource.DEADWOOD&&level.getBlockState(e.getKey()).isAir()).count();
                    require(brokenIron>=cargo.iron()&&brokenCoal>=cargo.coal()&&brokenLogs>=cargo.logs(),"Cargo without actual block removal");
                    require(cargo.pickUses()==250-cargo.iron()-cargo.coal()&&cargo.axeUses()==250-cargo.logs(),"Tool wear mismatch");
                    var restored=NpcSupplyData.load(supplies.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
                    require(restored.cargo(actor).equals(cargo),"Cargo persistence mismatch");
                    LOG.info("SAO_SUPPLY_GATHER_OK {}",cargo);
                }
                return;
            }
            if(cargo.delivered()==0)return;
            require(collected!=null&&collected.iron()>=2&&collected.coal()>=4&&collected.logs()>=1,"Insufficient actual cargo for steel chain "+collected);
            require(cargo.delivered()==1&&cargo.pickUses()==collected.pickUses()&&cargo.axeUses()==collected.axeUses(),"Repeated delivery or reset tools");
            require(level.getBlockEntity(forge.stock()) instanceof BarrelBlockEntity,"Real forge stock missing");
            var barrel=(BarrelBlockEntity)level.getBlockEntity(forge.stock());
            require(count(barrel,Items.RAW_IRON)==collected.iron()&&count(barrel,Items.COAL)==collected.coal()&&count(barrel,Items.OAK_LOG)==collected.logs(),"Delivered actual inventory mismatch");
            var progress=CharacterProgressData.get(server);
            require(progress.state(new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,actor)).masteryExperience(CharacterProgress.Mastery.MINING)==baselineMining+4L*(collected.iron()+collected.coal()),"Mining XP mismatch");
            LOG.info("SAO_SUPPLY_DELIVERY_OK {}",cargo);delivered=true;
            level.setDayTime(4000);
            var state=pop.state(1,smith);
            var work=NpcDailyPlan.at(state,level.getDayTime());
            require(work.activity()==NpcDailyPlan.Activity.WORK,"Smith not scheduled");
            if(level.getEntity(smith)==null){
                var at=ExpeditionGround.walkable(level,1,work.destination().x(),work.destination().z());
                require(at!=null,"Forge destination unavailable");
                var worker=SaoEntities.CITY_NPC.get().create(level);require(worker!=null,"Smith creation");
                worker.bind(state);worker.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                require(level.addFreshEntity(worker),"Smith projection");
            }
            return;
        }
        if(level.getEntity(smith) instanceof CityNpc worker)follow(p,worker);
        require(level.getBlockEntity(forge.output()) instanceof BarrelBlockEntity,"Actual output barrel missing");
        var output=(BarrelBlockEntity)level.getBlockEntity(forge.output());
        for(int i=0;i<output.getContainerSize();i++){
            var item=output.getItem(i);
            if(!item.is(SaoItems.STEEL_SWORD.get()))continue;
            var tag=item.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getCompound("sao_forge");
            require(tag.getUUID("maker").equals(smith),"Wrong actual smith");
            require(supplies.cargo(actor).size()==0&&supplies.cargo(actor).delivered()==1,"Cargo replay");
            LOG.info("SAO_SUPPLY_SWORD_OK actor={} maker={} item={}",actor,smith,item);
            finished=true;return;
        }
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("supply-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
