package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.network.CombatNetwork;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Isolated survival-client acceptance: actual native attacks against a real hostile mob. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class SwordComboProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.swordComboProbe");
    private static int phase,ticks,stage;
    private static CompletableFuture<Void> pending;
    private static UUID target;
    private static long began;
    private static float before,first,second,third;
    private static int damage;
    private static boolean finished,visual;
    private SwordComboProbe(){}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-sword-combo-test")),"Fresh isolated save required");
            c.options.renderDistance().set(8);c.options.simulationDistance().set(5);
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-sword-combo-test",
                new LevelSettings("SAO sword combo test",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.player==null||c.level==null||c.screen!=null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=80){
            c.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            action(c,SwordComboProbe::setup);phase=2;ticks=0;return;
        }
        if(phase!=2)return;
        require(ticks<1500,"Combat client probe timed out");
        if(finished){
            require(visual,"No synchronized Horizontal model pose rendered");
            LOG.info("SAO_SWORD_COMBO_PROBE_PASS");phase=3;return;
        }
        action(c,SwordComboProbe::observe);
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();level.setDayTime(4000);
        int x=CityPlan.half(1)+58,z=0; // outside the NPC quarry arena
        for(int cx=(x-16)>>4;cx<=(x+16)>>4;cx++)for(int cz=-1;cz<=1;cz++)level.getChunk(cx,cz);
        var from=ExpeditionGround.walkable(level,1,x,z);
        var to=ExpeditionGround.walkable(level,1,x,z+2);
        require(from!=null&&to!=null&&Math.abs(from.getY()-to.getY())<=1,"Wilderness fixture ground");
        require(p.teleportTo(level,x+.5,from.getY(),z+.5,Set.of(),0,0),"Wilderness test teleport");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_SWORD));
        var mob=EntityType.HUSK.create(level);require(mob!=null,"Missing husk");
        mob.setNoAi(true);mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        mob.setHealth(200);mob.moveTo(x+.5,to.getY(),z+2.5,180,0);
        require(level.addFreshEntity(mob)&&p.hasLineOfSight(mob),"Real hostile target");
        target=mob.getUUID();began=level.getGameTime();stage=0;
    }
    private static void startBasic(ServerPlayer p,Husk mob){
        before=mob.getHealth();damage=p.getMainHandItem().getDamageValue();
        p.attack(mob);
        require(mob.getHealth()==before&&p.getMainHandItem().getDamageValue()==damage,"Native attack must wait for windup");
        began=p.serverLevel().getGameTime();
    }
    private static float landed(ServerPlayer p,Husk mob){
        require(mob.getHealth()<before&&p.getMainHandItem().getDamageValue()==damage+1,"Exactly one real damage/durability transaction");
        return before-mob.getHealth();
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();
        require(level.getEntity(target) instanceof Husk,"Target missing");
        var mob=(Husk)level.getEntity(target);
        long age=level.getGameTime()-began;
        var data=CharacterProgressData.get(level.getServer());
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
        switch(stage){
            case 0->{
                if(age<35)return;
                require(data.state(owner).mastery(CharacterProgress.Mastery.SWORD)<3,"Fixture starts untrained");
                before=mob.getHealth();SwordCombat.intent(p,2,mob.getId());began=level.getGameTime();stage=1;
            }
            case 1->{
                if(age<25)return;
                require(mob.getHealth()==before&&p.getMainHandItem().getDamageValue()==0,"Locked skill must not deal damage");
                LOG.info("SAO_SWORD_LOCKED_OK");
                // Real kill events establish mastery; this is fixture preparation, not a skill hit.
                for(int i=0;i<8;i++){
                    var quarry=EntityType.HUSK.create(level);require(quarry!=null,"Mastery fixture");
                    quarry.setNoAi(true);quarry.setHealth(1);quarry.moveTo(p.getX()+2,p.getY(),p.getZ(),0,0);
                    require(level.addFreshEntity(quarry)&&quarry.hurt(level.damageSources().playerAttack(p),5),"Real training kill");
                }
                require(data.state(owner).mastery(CharacterProgress.Mastery.SWORD)>=3,"Mastery earned through actual kill events");
                startBasic(p,mob);stage=2;
            }
            case 2->{
                if(age<14)return;require(age<26,"Missed follow-up test timing");
                first=landed(p,mob);startBasic(p,mob);stage=3;
            }
            case 3->{
                if(age<14)return;require(age<26,"Missed finisher test timing");
                second=landed(p,mob);startBasic(p,mob);stage=4;
            }
            case 4->{
                if(age<18)return;third=landed(p,mob);
                require(second>first&&third>second,"Increasing actual hit damage across the chain: "+first+"/"+second+"/"+third);
                LOG.info("SAO_SWORD_CHAIN_OK first={} second={} third={}",first,second,third);
                before=mob.getHealth();damage=p.getMainHandItem().getDamageValue();
                SwordCombat.intent(p,2,mob.getId());
                require(mob.getHealth()==before,"Skill has a real windup");
                began=level.getGameTime();stage=5;
            }
            case 5->{
                if(age<23)return;
                float hit=landed(p,mob);require(hit>third,"Horizontal damage must exceed finisher");
                LOG.info("SAO_SWORD_HORIZONTAL_OK damage={}",hit);
                before=mob.getHealth();damage=p.getMainHandItem().getDamageValue();
                SwordCombat.intent(p,2,mob.getId());began=level.getGameTime();stage=6;
            }
            case 6->{
                if(age<25)return;
                require(mob.getHealth()==before&&p.getMainHandItem().getDamageValue()==damage,"Cooldown prevents repeated skill");
                LOG.info("SAO_SWORD_COOLDOWN_OK");finished=true;
            }
            default->{}
        }
    }
    @SubscribeEvent public static void rendered(RenderPlayerEvent.Post event){
        if(!ENABLED||phase!=2||visual||event.getEntity()!=Minecraft.getInstance().player)return;
        var snap=CombatNetwork.snapshot();if(snap==null||SwordPose.kind(snap.pose())!=SwordPose.Kind.HORIZONTAL)return;
        int age=SwordPose.age(snap.pose(),event.getEntity().level().getGameTime());
        if(age<4||age>10)return;
        var model=event.getRenderer().getModel();
        var arm=event.getEntity().getMainArm()==net.minecraft.world.entity.HumanoidArm.RIGHT?model.rightArm:model.leftArm;
        if(Math.abs(arm.yRot)<.35f)return;
        visual=true;LOG.info("SAO_SWORD_POSE_OK age={} yaw={}",age,arm.yRot);
        try{
            var c=Minecraft.getInstance();var dir=c.gameDirectory.toPath().resolve("sword-combo-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve("horizontal.png"));}
        }catch(Exception ex){throw new IllegalStateException(ex);}
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> a){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->{var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Missing player");a.accept(p);},server::execute);
    }
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
