package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import com.mojang.blaze3d.platform.NativeImage;
import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
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

/** Isolated client world; real production spawn, renderer, AI attack and loot-table death. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class BoarProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.boarProbe");
    private static int phase,ticks,entityId;
    private static UUID id;
    private static long began;
    private static boolean rendered,engaged,loot;
    private static CompletableFuture<Void> pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==4)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-boar-test")),"Fresh isolated save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-boar-test",
                new LevelSettings("SAO boar test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=100){c.options.hideGui=true;action(c,BoarProbe::setup);phase=2;ticks=0;return;}
        if(phase==2){
            require(ticks<400,"Client projection timeout");
            if(c.level.getEntity(entityId) instanceof FrenzyBoar boar&&ticks>=45){
                require(c.getEntityRenderDispatcher().getRenderer(boar) instanceof FrenzyBoarRenderer,"Custom renderer not registered");
                texture(c,"textures/entity/frenzy_boar.png",128,96);
                texture(c,"textures/item/boar_tusk.png",16,16);
                capture(c,"01-frenzy-boar");rendered=true;LOG.info("SAO_BOAR_RENDER_OK");
                action(c,BoarProbe::beginAttack);phase=3;ticks=0;
            }
            return;
        }
        if(phase==3){
            if(loot){capture(c,"02-boar-loot");LOG.info("SAO_BOAR_PROBE_PASS");phase=4;return;}
            if(ticks%5==0)action(c,BoarProbe::observe);
        }
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        require(CityRules.hasRegionalSettlements(level),"Wrong preset");
        level.setDayTime(6000);
        int x0=CityPlan.half(1)+64;
        for(int cx=(x0-80)>>4;cx<=(x0+80)>>4;cx++)for(int cz=-5;cz<=5;cz++)level.getChunk(cx,cz);
        require(BoarSpawns.surface(level,0,0)==null,"Capital surface accepted");
        BlockPos start=null;
        outer:for(int x=x0-12;x<=x0+12;x++)for(int z=-12;z<=12;z++){
            var q=BoarSpawns.surface(level,x,z);if(q!=null){start=q;break outer;}
        }
        require(start!=null,"No outdoor test surface");
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,start.getX()+.5,start.getY()+2,start.getZ()+.5,Set.of(),0,0),"Fixture teleport");
        require(BoarSpawns.attempt(p)==null,"Spawning rule bypassed");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(true,server);
        FrenzyBoar boar=null;
        for(int i=0;i<12&&boar==null;i++)boar=BoarSpawns.attempt(p);
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);
        require(boar!=null&&boar.isAlive(),"Production spawn attempt failed");
        id=boar.getUUID();entityId=boar.getId();
        require(boar.getMaxHealth()==24&&BoarSpawns.allowedArea(level,boar.getBlockX(),boar.getBlockZ()),"Invalid spawned boar");
        camera(p,boar);
        LOG.info("SAO_BOAR_SPAWN_OK id={} pos={}",id,boar.position());
    }
    private static void beginAttack(ServerPlayer p){
        var level=p.serverLevel();
        require(rendered&&level.getEntity(id) instanceof FrenzyBoar,"Missing rendered boar");
        var boar=(FrenzyBoar)level.getEntity(id);
        BlockPos pos=null;
        for(int[] d:new int[][]{{3,0},{-3,0},{0,3},{0,-3},{4,0},{0,4}}){
            var q=BoarSpawns.surface(level,boar.getBlockX()+d[0],boar.getBlockZ()+d[1]);
            if(q!=null&&Math.abs(q.getY()-boar.getY())<=1){pos=q;break;}
        }
        require(pos!=null,"No adjacent attack test ground");
        p.setGameMode(GameType.SURVIVAL);p.setHealth(p.getMaxHealth());
        p.getAbilities().flying=false;p.onUpdateAbilities();
        require(p.teleportTo(level,pos.getX()+.5,pos.getY(),pos.getZ()+.5,Set.of(),0,0),"Attack fixture teleport");
        p.setDeltaMovement(0,0,0);p.fallDistance=0;
        began=level.getGameTime();
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();
        require(level.getGameTime()-began<400,"Boar AI did not attack");
        require(level.getEntity(id) instanceof FrenzyBoar,"Boar vanished before test kill");
        var boar=(FrenzyBoar)level.getEntity(id);
        if(p.getHealth()>=p.getMaxHealth()||p.getLastHurtByMob()!=boar)return;
        engaged=true;LOG.info("SAO_BOAR_ATTACK_OK playerHealth={} target={}",p.getHealth(),boar.getTarget());
        p.setGameMode(GameType.CREATIVE);p.getAbilities().flying=true;p.onUpdateAbilities();
        camera(p,boar);
        var box=boar.getBoundingBox().inflate(5);
        require(boar.hurt(level.damageSources().playerAttack(p),100)&&!boar.isAlive(),"Actual player-attributed death failed");
        var drops=level.getEntitiesOfClass(ItemEntity.class,box,e->e.isAlive());
        int tusks=0,pork=0;
        for(var item:drops){
            if(item.getItem().is(SaoItems.BOAR_TUSK.get()))tusks+=item.getItem().getCount();
            if(item.getItem().is(Items.PORKCHOP))pork+=item.getItem().getCount();
        }
        require(engaged&&tusks==1&&pork>=1&&pork<=3,"Actual loot table failed tusks="+tusks+" pork="+pork);
        require(SaoWorldData.get(level.getServer()).highestUnlocked()==1,"Boar unlocked floor");
        LOG.info("SAO_BOAR_LOOT_OK tusks={} pork={}",tusks,pork);loot=true;
    }
    private static void camera(ServerPlayer p,FrenzyBoar boar){
        double x=boar.getX()-5,z=boar.getZ()+6;
        float yaw=(float)-Math.toDegrees(Math.atan2(boar.getX()-x,boar.getZ()-z));
        require(p.teleportTo(p.serverLevel(),x,boar.getY()+3,z,Set.of(),yaw,16),"Camera failed");
    }
    private static void texture(Minecraft c,String path,int w,int h){
        try(var stream=c.getResourceManager().getResourceOrThrow(ResourceLocation.fromNamespaceAndPath("sao",path)).open();var png=NativeImage.read(stream)){
            require(png.getWidth()==w&&png.getHeight()==h,"Wrong texture dimensions "+path);
        }catch(Exception e){throw new IllegalStateException("Texture decode failed "+path,e);}
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("boar-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static void require(boolean b,String m){if(!b)throw new IllegalStateException(m);}
}
