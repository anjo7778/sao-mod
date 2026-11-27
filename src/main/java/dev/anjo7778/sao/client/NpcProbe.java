package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class NpcProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.npcProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static UUID greeted;
    private static long departureTick,offscreenBefore,practiceBefore;
    private static boolean outsideVerified;
    private static UUID socialFirst,socialSecond,eventId;
    private static final Map<UUID,Vec3> before=new HashMap<>();
    private NpcProbe(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==8)return;
        Minecraft c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            if(Files.exists(c.gameDirectory.toPath().resolve("saves/sao-npc-test")))throw new IllegalStateException("Disposable save already exists");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-npc-test",
                new LevelSettings("SAO NPC test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),
                access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null){ticks=0;return;}
        ticks++;
        switch(phase){
            case 1 -> {
                if(ticks<60)return;
                c.options.hideGui=true;
                action(c,p->p.serverLevel().setDayTime(6000));
                advance(2);
            }
            case 2 -> {
                if(ticks<340)return;
                action(c,p->{
                    var list=npcs(p);
                    if(list.size()<8||list.size()>NpcPresence.CAP)throw new IllegalStateException("Incorrect visible count "+list.size());
                    Set<UUID> ids=new HashSet<>();
                    var d=NpcPopulationData.get(p.serverLevel().getServer());
                    if(d.roster(1).size()!=148||d.discoveredCapitals()!=1)throw new IllegalStateException("Unexpected roster");
                    CityNpc closest=null;
                    for(var npc:list){
                        if(!ids.add(npc.getUUID())||d.state(1,npc.getUUID())==null||npc.getCustomName()==null)
                            throw new IllegalStateException("Projection identity invalid");
                        if(closest==null||npc.distanceToSqr(p)<closest.distanceToSqr(p))closest=npc;
                        before.put(npc.getUUID(),npc.position());
                    }
                    if(closest==null)throw new IllegalStateException("No NPC");
                    greeted=closest.getUUID();
                    // Real interaction path. Keep the player at a normal conversational distance.
                    var pos=closest.blockPosition();
                    if(!p.teleportTo(p.serverLevel(),pos.getX()+.5,pos.getY(),pos.getZ()-2.5,Set.of(),0,0))
                        throw new IllegalStateException("Cannot approach NPC");
                    closest.interact(p,InteractionHand.MAIN_HAND);
                    var state=d.state(1,greeted);
                    if(state.relationship(p.getUUID()).trust()!=51||greetings(state,p.getUUID())!=1)
                        throw new IllegalStateException("Greeting did not change saved relationship");
                    closest.interact(p,InteractionHand.MAIN_HAND);
                    if(greetings(d.state(1,greeted),p.getUUID())!=1)throw new IllegalStateException("Repeated greeting inflated memory");
                    LOG.info("SAO_NPC_PRESENCE_OK count={} profiles={}",list.size(),d.roster(1).size());
                    LOG.info("SAO_NPC_GREETING_OK id={}",greeted);
                });
                advance(3);
            }
            case 3 -> {
                if(ticks<60)return;
                capture(c,"01-plaza-npcs");
                action(c,p->{
                    int moved=0;
                    for(var npc:npcs(p)){
                        Vec3 v=before.get(npc.getUUID());
                        if(v!=null&&v.distanceTo(npc.position())>1.5)moved++;
                    }
                    LOG.info("SAO_NPC_DAY_MOVEMENT count={}",moved);
                    p.serverLevel().setDayTime(12000);
                });
                advance(4);
            }
            case 4 -> {
                if(socialFirst!=null){advance(5);return;}
                // Observe an autonomously selected pair; never invoke converse/beginConversation in the fixture.
                if(ticks<180||ticks%10!=0)return;
                if(ticks>1200)throw new IllegalStateException("No autonomous reciprocal conversation observed");
                action(c,p->{
                    var data=NpcPopulationData.get(p.serverLevel().getServer());
                    for(var a:npcs(p)){
                        var partner=a.conversationPartner();
                        if(partner==null||!(p.serverLevel().getEntity(partner) instanceof CityNpc b)
                            ||!a.getUUID().equals(b.conversationPartner()))continue;
                        var as=data.state(1,a.getUUID());var bs=data.state(1,b.getUUID());
                        if(as==null||bs==null)throw new IllegalStateException("Conversation identity missing");
                        var m=as.memories().stream().filter(x->NpcSocial.socialMemory(x)&&x.actor().equals(b.getUUID())).findFirst().orElseThrow();
                        if(bs.memories().stream().noneMatch(x->x.eventId().equals(m.eventId())&&x.actor().equals(a.getUUID())&&x.type()==m.type()))
                            throw new IllegalStateException("Unpaired encounter");
                        if(NpcSocial.dailyCount(as,m.day())>NpcSocial.DAILY_LIMIT||NpcSocial.dailyCount(bs,m.day())>NpcSocial.DAILY_LIMIT)
                            throw new IllegalStateException("Exceeded daily social budget");
                        socialFirst=a.getUUID();socialSecond=b.getUUID();eventId=m.eventId();
                        var midpoint=a.position().add(b.position()).scale(.5);
                        var delta=b.position().subtract(a.position());
                        double len=Math.hypot(delta.x,delta.z);
                        if(len<.1)continue;
                        double cx=midpoint.x-delta.z/len*5,cz=midpoint.z+delta.x/len*5;
                        float yaw=(float)-Math.toDegrees(Math.atan2(midpoint.x-cx,midpoint.z-cz));
                        if(!p.teleportTo(p.serverLevel(),cx,midpoint.y+.1,cz,Set.of(),yaw,4))
                            throw new IllegalStateException("Social camera teleport failed");
                        LOG.info("SAO_NPC_SOCIAL_OK first={} second={} event={} type={}",socialFirst,socialSecond,eventId,m.type());
                        break;
                    }
                });
            }
            case 5 -> {
                if(ticks<10)return;
                capture(c,"02-social-encounter");
                action(c,p->{
                    var data=NpcPopulationData.get(p.serverLevel().getServer());
                    var a=data.state(1,socialFirst);
                    var b=data.state(1,socialSecond);
                    var restored=NpcPopulationData.load(data.save(new net.minecraft.nbt.CompoundTag(),p.serverLevel().registryAccess()),p.serverLevel().registryAccess());
                    if(!a.equals(restored.state(1,socialFirst))||!b.equals(restored.state(1,socialSecond)))
                        throw new IllegalStateException("Social snapshots not restored");
                    var m=a.memories().stream().filter(x->x.eventId().equals(eventId)).findFirst().orElseThrow();
                    if(restored.converse(1,socialFirst,socialSecond,m.day()).isPresent())
                        throw new IllegalStateException("Saved conversation replayed");
                    LOG.info("SAO_NPC_SOCIAL_RELOAD_OK");
                    p.serverLevel().setDayTime(18000);
                });
                advance(6);
            }
            case 6 -> {
                if(ticks<240)return;
                capture(c,"03-night-capital");
                action(c,p->{
                    int rest=0,moved=0,awake=0;
                    var population=NpcPopulationData.get(p.serverLevel().getServer());
                    for(var npc:npcs(p)){
                        var state=population.state(1,npc.getUUID());
                        if(state==null||!npc.activity().equals(AdventureSimulationData.plan(p.serverLevel(),state).activity().name()))
                            throw new IllegalStateException("NPC did not follow its personal routine");
                        if(npc.activity().equals("REST"))rest++;else awake++;
                        Vec3 v=before.get(npc.getUUID());
                        if(v!=null&&v.distanceTo(npc.position())>3)moved++;
                    }
                    if(rest<1||awake<1||moved<1)throw new IllegalStateException("No mixed nighttime behavior: "+rest+"/"+awake+"/"+moved);
                    var data=NpcPopulationData.get(p.serverLevel().getServer());
                    var restored=NpcPopulationData.load(data.save(new net.minecraft.nbt.CompoundTag(),p.serverLevel().registryAccess()),p.serverLevel().registryAccess());
                    if(restored.state(1,greeted).relationship(p.getUUID()).trust()!=51)
                        throw new IllegalStateException("Relationship not restored");
                    LOG.info("SAO_NPC_NIGHT_SCHEDULE_OK rest={} awake={} moved={}",rest,awake,moved);
                    if(!p.teleportTo(p.serverLevel(),500.5,130,0.5,Set.of(),0,0))
                        throw new IllegalStateException("Cannot move away");
                    p.getAbilities().flying=true;p.onUpdateAbilities();
                    p.serverLevel().setDayTime(6000);
                    departureTick=p.serverLevel().getGameTime();
                    var stats=totals(p);
                    offscreenBefore=stats[0];practiceBefore=stats[1];
                });
                advance(7);
            }
            case 7 -> {
                if(outsideVerified){advance(8);return;}
                if(ticks<220||ticks%40!=0)return;
                if(ticks>3000)throw new IllegalStateException("Server did not advance enough for offscreen probe");
                action(c,p->{
                    if(p.serverLevel().getGameTime()-departureTick<1450)return;
                    if(!npcs(p).isEmpty())throw new IllegalStateException("NPC projections spawned outside city");
                    var server=p.serverLevel().getServer();
                    var data=NpcPopulationData.get(server);
                    if(data.state(1,greeted)==null||data.state(1,greeted).relationship(p.getUUID()).trust()!=51)
                        throw new IllegalStateException("Roster lost after travel");
                    var total=totals(p);
                    if(total[0]<=offscreenBefore||total[1]<=practiceBefore)
                        throw new IllegalStateException("No offscreen routine/training advancement: "+Arrays.toString(total));
                    var sim=AdventureSimulationData.get(server);
                    var r=p.serverLevel().registryAccess();
                    var restored=AdventureSimulationData.load(sim.save(new net.minecraft.nbt.CompoundTag(),r),r);
                    for(var state:data.roster(1)){
                        var id=state.profile().id();
                        if(!Objects.equals(sim.ledger(1,id),restored.ledger(1,id)))
                            throw new IllegalStateException("Offscreen ledger failed persistence roundtrip");
                    }
                    LOG.info("SAO_NPC_OFFSCREEN_OK updates={} practice={} elapsed={} no_nearby_projections=true",
                        total[0]-offscreenBefore,total[1]-practiceBefore,p.serverLevel().getGameTime()-departureTick);
                    LOG.info("SAO_NPC_PROBE_PASS");
                    outsideVerified=true;
                });
            }
            default -> {}
        }
    }
    private static long[] totals(ServerPlayer p){
        var server=p.serverLevel().getServer();
        var sim=AdventureSimulationData.get(server);
        long offscreen=0,practice=0;
        for(var s:NpcPopulationData.get(server).roster(1)){
            var l=sim.ledger(1,s.profile().id());
            if(l!=null){offscreen+=l.abstractUpdates();practice+=l.practice();}
        }
        return new long[]{offscreen,practice};
    }
    private static long greetings(NpcRoster.State s,UUID player){
        return s.memories().stream().filter(m->m.actor().equals(player)&&m.type()==AdventurerMind.EventType.GREETED).count();
    }
    private static List<CityNpc> npcs(ServerPlayer p){
        return p.serverLevel().getEntitiesOfClass(CityNpc.class,p.getBoundingBox().inflate(104),x->x.isAlive());
    }
    private static void advance(int p){phase=p;ticks=0;}
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{
            var p=server.getPlayerList().getPlayer(uuid);
            if(p==null)throw new IllegalStateException("Missing test player");
            a.accept(p);
        },server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("npc-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
            LOG.info("SAO_NPC_CAPTURE {}",name);
        }catch(Exception e){throw new IllegalStateException("Capture failed",e);}
    }
}
