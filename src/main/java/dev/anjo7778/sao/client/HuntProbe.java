package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Controlled survey and mob fixture. Actual production AI, melee damage, death event and wallet settlement. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class HuntProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.huntProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static UUID actorId,quarryId;
    private static long began,surveySince;
    private static int startingHealth,startingCondition;
    private static boolean engaged,injured,complete,captured;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-hunt-test")),"Fresh isolated save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-hunt-test",
                new LevelSettings("SAO hunt test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=100){c.options.hideGui=true;action(c,HuntProbe::setup);phase=2;ticks=0;return;}
        if(phase!=2)return;
        if(engaged&&!captured){capture(c,"01-engaged");captured=true;}
        if(complete){capture(c,"02-resolved");LOG.info("SAO_HUNT_PROBE_PASS");phase=3;return;}
        if(ticks%5==0)action(c,HuntProbe::observe);
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        require(CityRules.hasRegionalSettlements(level),"Wrong preset");
        level.setDayTime(6000);
        var pop=NpcPopulationData.get(server);pop.discover(1,server.overworld().getSeed());
        var s=pop.roster(1).stream().filter(HuntContract::ready)
            .filter(v->level.getEntity(v.profile().id())==null).findFirst().orElseThrow();
        actorId=s.profile().id();startingHealth=s.needs().health();startingCondition=s.needs().equipment();
        var sim=AdventureSimulationData.get(server);
        surveySince=level.getGameTime()/AdventureProgress.EPOCH_TICKS;
        if(sim.ledger(s)==null)sim.update(1,actorId,surveySince,AdventureProgress.Mode.IDLE,false);
        var root=sim.save(new CompoundTag(),level.registryAccess());
        boolean patched=false;
        var groups=root.getList("capitals",10);
        for(int i=0;i<groups.size();i++){
            var group=groups.getCompound(i);if(group.getLong("floor")!=1)continue;
            var list=group.getList("npcs",10);
            for(int j=0;j<list.size();j++){
                var e=list.getCompound(j);if(!e.getUUID("id").equals(actorId))continue;
                e.putLong("epoch",surveySince);
                var trip=e.getCompound("trip");trip.putString("phase","SURVEY");trip.putString("gate","EAST");
                trip.putLong("since",surveySince);trip.putLong("next",surveySince);
                trip.putIntArray("counts",new int[]{0,0,0});trip.putBoolean("abstract",false);trip.putBoolean("surveyed",false);
                patched=true;
            }
        }
        require(patched,"Cannot seed survey fixture");
        var loaded=AdventureSimulationData.load(root,level.registryAccess());loaded.setDirty();server.overworld().getDataStorage().set("sao_adventures",loaded);
        int x=CityPlan.half(1)+36;
        for(int cx=(x-24)>>4;cx<=(x+24)>>4;cx++)for(int cz=-2;cz<=2;cz++)level.getChunk(cx,cz);
        var at=ExpeditionGround.walkable(level,1,x,0);
        require(at!=null&&!CityRules.isSafeZone(level,x,0),"No safe fixture surface in wilderness");
        var npc=SaoEntities.CITY_NPC.get().create(level);require(npc!=null,"NPC missing");
        npc.bind(s);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
        require(level.addFreshEntity(npc),"Actor spawn rejected");
        var mob=EntityType.HUSK.create(level);require(mob!=null,"Husk missing");
        boolean placed=false;
        for(int[] d:new int[][]{{2,0},{-2,0},{0,2},{0,-2},{3,0},{0,3}}){
            var q=ExpeditionGround.walkable(level,1,x+d[0],d[1]);
            if(q==null||Math.abs(q.getY()-at.getY())>1)continue;
            mob.moveTo(q.getX()+.5,q.getY(),q.getZ()+.5,0,0);
            if(level.noCollision(mob)&&npc.hasLineOfSight(mob)){placed=true;break;}
        }
        require(placed,"No adjacent quarry ground");
        // Reduced HP is an explicit test fixture; no synthetic death or direct bounty grant.
        mob.setHealth(12);mob.setPersistenceRequired();quarryId=mob.getUUID();
        require(level.addFreshEntity(mob),"Quarry spawn rejected");
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,x-5.5,at.getY()+3,6.5,Set.of(),-135,15),"Camera failed");
        began=level.getGameTime();LOG.info("SAO_HUNT_FIXTURE actor={} quarry={}",actorId,quarryId);
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();var pop=NpcPopulationData.get(server);
        require(level.getGameTime()-began<700,"Physical combat did not resolve");
        require(level.getEntity(actorId) instanceof CityNpc,"NPC projection vanished");
        var npc=(CityNpc)level.getEntity(actorId);var s=pop.state(1,actorId);var h=pop.hunt(actorId);var w=pop.wallet(s.profile());
        if(h==null)return;
        require(h.quarry().equals(quarryId)&&h.surveySince()==surveySince,"Wrong contract quarry");
        if(h.active()){
            if(!engaged){engaged=true;LOG.info("SAO_HUNT_CONTRACT_OK {}",h);}
            // Exercise genuine mob attack handling once if AI has not already retaliated.
            if(s.needs().health()==startingHealth&&level.getEntity(quarryId) instanceof Mob m&&m.isAlive()
                &&m.distanceToSqr(npc)<=9&&m.hasLineOfSight(npc))m.doHurtTarget(npc);
        }
        s=pop.state(1,actorId);
        if(!injured&&s.needs().health()<startingHealth){
            require(npc.getHealth()<npc.getMaxHealth()&&npc.isAlive(),"No real entity injury");
            injured=true;LOG.info("SAO_HUNT_INJURY_OK health={} entityHP={}",s.needs().health(),npc.getHealth());
        }
        if(h.status()!=HuntContract.Status.KILLED)return;
        require(engaged&&injured,"Missing observed engagement or injury");
        require(!(level.getEntity(quarryId) instanceof Mob m)||!m.isAlive(),"Quarry still alive");
        require(w.huntKills()==1&&w.paidHunts()==1&&w.col()==110&&pop.treasury(1).col()==1190,"Incorrect attributed bounty");
        require(s.needs().equipment()==startingCondition-2,"Wrong combat wear");
        require(SaoWorldData.get(server).highestUnlocked()==1,"Hunt unlocked floor");
        require(!pop.finishHunt(1,actorId,surveySince,quarryId),"Duplicate death paid");
        var regs=level.registryAccess();var restored=NpcPopulationData.load(pop.save(new CompoundTag(),regs),regs);
        require(restored.wallet(s.profile()).equals(w)&&restored.hunt(actorId).equals(h)&&restored.state(1,actorId).equals(s),"Combat state round trip");
        require(!restored.finishHunt(1,actorId,surveySince,quarryId),"Reload allowed replay");
        LOG.info("SAO_HUNT_KILL_OK wallet={} health={} treasury={}",w,s.needs().health(),pop.treasury(1).col());complete=true;
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("hunt-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
