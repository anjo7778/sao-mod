package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.entity.SaoEntities;
import dev.anjo7778.sao.item.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.CharacterProgress.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class ForgeProbe {
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.forgeProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==5)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-forge-test")),"Fresh isolated save");
            var rules=new GameRules();
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-forge-test",
                new LevelSettings("SAO Forge",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&c.screen==null&&ticks>=60){
            var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
            pending=CompletableFuture.runAsync(()->verify(server.getPlayerList().getPlayer(uuid)),server::execute);
            phase=2;ticks=0;return;
        }
        if(phase==2&&pending.isDone()){
            pending.join();phase=3;ticks=0;return;
        }
        if(phase==3&&ticks>=20){
            require(c.player.getMainHandItem().is(SaoItems.STEEL_SWORD.get()),"Sword not synchronized");
            require(NativeSteelSword.quality(c.player.getMainHandItem())==MaterialRules.CraftQuality.MASTERWORK,"Quality not synchronized");
            var model=c.getItemRenderer().getModel(c.player.getMainHandItem(),c.level,c.player,0);
            require(model!=c.getModelManager().getMissingModel(),"Model missing");
            require(model.isGui3d(),"Native blade must use 3D model");
            capture(c,"01-steel-sword-first-person");
            c.options.setCameraType(CameraType.THIRD_PERSON_FRONT);phase=4;ticks=0;return;
        }
        if(phase==4&&ticks>=12){
            capture(c,"02-steel-sword-third-person");
            LOG.info("SAO_FORGE_MODEL_OK");LOG.info("SAO_FORGE_PROBE_PASS");phase=5;
        }
    }
    private static void inputs(ServerPlayer p,int amount){
        for(int i=0;i<36;i++)p.getInventory().setItem(i,ItemStack.EMPTY);
        p.getInventory().setItem(0,new ItemStack(SaoItems.STEEL_INGOT.get(),amount));
        p.getInventory().setItem(1,new ItemStack(Items.STICK));
        p.getInventory().setItem(2,new ItemStack(Items.COAL));
        p.getInventory().selected=0;
    }
    private static ItemStack result(ServerPlayer p){
        ItemStack sword=ItemStack.EMPTY;int count=0;
        for(int i=0;i<36;i++){
            var s=p.getInventory().getItem(i);
            if(s.is(SaoItems.STEEL_SWORD.get())){count+=s.getCount();sword=s;}
        }
        require(count==1,"Exactly one forged blade");return sword;
    }
    private static void verify(ServerPlayer p){
        require(p!=null,"Missing player");var level=p.serverLevel();var server=level.getServer();
        var data=CharacterProgressData.get(server);
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
        var anvil=new BlockPos(1,CityPlan.GROUND+1,0);
        level.setBlockAndUpdate(anvil,Blocks.ANVIL.defaultBlockState());
        level.setBlockAndUpdate(anvil.east(),Blocks.BLAST_FURNACE.defaultBlockState());
        require(p.teleportTo(level,.5,CityPlan.GROUND+1,.5,Set.of(),0,0),"Forge fixture teleport");
        inputs(p,1);
        require(ForgeService.selfForge(p,anvil)==ForgeService.Outcome.MATERIALS_OR_SPACE,"Insufficient materials not rejected");
        require(p.getInventory().getItem(0).getCount()==1&&p.getInventory().getItem(1).is(Items.STICK),"Failed request consumed inputs");
        inputs(p,2);long previous=data.state(owner).masteryExperience(Mastery.BLACKSMITHING);
        require(ForgeService.selfForge(p,anvil)==ForgeService.Outcome.COMPLETED,"Self forging failed");
        var ordinary=result(p);
        require(NativeSteelSword.quality(ordinary)==MaterialRules.CraftQuality.STANDARD&&ordinary.getMaxDamage()==338,"Incorrect beginner result");
        require(data.state(owner).masteryExperience(Mastery.BLACKSMITHING)==previous+10,"Actual forge mastery");
        require(p.getInventory().getItem(1).isEmpty()&&p.getInventory().getItem(2).isEmpty(),"Fuel and handle not consumed");
        require(ForgeService.selfForge(p,anvil)==ForgeService.Outcome.MATERIALS_OR_SPACE,"Free repeated production");
        LOG.info("SAO_FORGE_SELF_OK");LOG.info("SAO_FORGE_MATERIALS_OK");
        // A real discovered roster blacksmith at their actual scheduled destination.
        level.setDayTime(4000);
        var pop=NpcPopulationData.get(server);pop.discover(1,level.getSeed());
        CityNpc smith=null;
        for(var state:pop.roster(1)){
            if(state.profile().occupation()!=NpcRoster.Occupation.BLACKSMITH||level.getEntity(state.profile().id())!=null)continue;
            var plan=NpcDailyPlan.at(state,level.getDayTime());
            if(plan.activity()!=NpcDailyPlan.Activity.WORK)continue;
            int x=plan.destination().x(),z=plan.destination().z();
            level.getChunk(x>>4,z>>4);
            var pos=ExpeditionGround.walkable(level,1,x,z);
            if(pos==null||!CityRules.isSafeZone(level,x,z))continue;
            var candidate=SaoEntities.CITY_NPC.get().create(level);
            if(candidate==null)continue;
            candidate.bind(state);candidate.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            if(!level.noCollision(candidate)||!level.addFreshEntity(candidate)){candidate.discard();continue;}
            smith=candidate;break;
        }
        require(smith!=null&&smith.atScheduledDestination(),"No real working smith");
        var work=smith.blockPosition();
        // Add real fixture equipment within the scheduled forge, not a virtual production stock.
        var station=work.east(2);
        level.setBlockAndUpdate(station,Blocks.ANVIL.defaultBlockState());
        level.setBlockAndUpdate(station.east(),Blocks.FURNACE.defaultBlockState());
        var stand=work.south();
        level.setBlockAndUpdate(stand,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(stand.above(),Blocks.AIR.defaultBlockState());
        require(p.teleportTo(level,stand.getX()+.5,stand.getY(),stand.getZ()+.5,Set.of(),180,0),"Commission fixture teleport");
        var smithOwner=new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,smith.getUUID());
        // Test-only seeded mastery allows high-quality transaction validation without weeks of repeated crafting.
        data.awardNext(smithOwner,Source.CRAFT,0,Mastery.BLACKSMITHING,3300);
        require(data.state(smithOwner).mastery(Mastery.BLACKSMITHING)>=12,"Masterwork fixture mastery");
        long smithXP=data.state(smithOwner).masteryExperience(Mastery.BLACKSMITHING);
        inputs(p,2);p.setShiftKeyDown(true);
        try{smith.interact(p,InteractionHand.MAIN_HAND);}
        finally{p.setShiftKeyDown(false);}
        var master=result(p);
        require(NativeSteelSword.quality(master)==MaterialRules.CraftQuality.MASTERWORK&&master.getMaxDamage()==456,"NPC craftsmanship");
        var record=master.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getCompound("sao_forge");
        require(record.getUUID("maker").equals(smith.getUUID()),"Wrong maker identity");
        require(data.state(smithOwner).masteryExperience(Mastery.BLACKSMITHING)==smithXP+10,"NPC forge mastery not credited");
        require(data.state(owner).masteryExperience(Mastery.BLACKSMITHING)==previous+10,"Customer incorrectly awarded smith XP");
        var restored=CharacterProgressData.load(data.save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        require(restored.state(smithOwner).equals(data.state(smithOwner)),"Saved maker progression");
        var itemTag=master.save(level.registryAccess());
        var restoredItem=ItemStack.parse(level.registryAccess(),itemTag).orElseThrow();
        require(ItemStack.matches(restoredItem,master)&&NativeSteelSword.quality(restoredItem)==MaterialRules.CraftQuality.MASTERWORK,"Item component save round trip");
        LOG.info("SAO_FORGE_NPC_OK maker={} mastery={}",smith.getUUID(),data.state(smithOwner).mastery(Mastery.BLACKSMITHING));
        LOG.info("SAO_FORGE_SAVE_OK");
        p.setItemInHand(InteractionHand.MAIN_HAND,master);p.setYRot(180);p.setXRot(0);
    }
    private static void capture(Minecraft c,String name){
        try{
            var dir=c.gameDirectory.toPath().resolve("forge-probe");Files.createDirectories(dir);
            try(var img=Screenshot.takeScreenshot(c.getMainRenderTarget())){img.writeToFile(dir.resolve(name+".png"));}
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
