package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.block.SaoBlocks;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.slf4j.Logger;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Fresh Aincrad world: actual decorated chunks and native survival harvest, never manual ore placement. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class MaterialProbe {
    private static final boolean ENABLED=Boolean.getBoolean("sao.materialProbe");
    private static final Logger LOG=LogUtils.getLogger();
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-material-test")),"Fresh save only");
            c.options.renderDistance().set(6);c.options.simulationDistance().set(5);
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-material-test",
                new LevelSettings("SAO material test",GameType.SURVIVAL,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        if(phase==1&&c.screen==null&&++ticks>=60){
            var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
            pending=CompletableFuture.runAsync(()->{
                var p=server.getPlayerList().getPlayer(uuid);require(p!=null,"Player unavailable");verify(p);
            },server::execute);
            phase=2;
        }
        if(phase==2&&pending.isDone()){
            pending.join();LOG.info("SAO_MATERIAL_PROBE_PASS");phase=3;
        }
    }
    private static List<BlockPos> generated(ServerLevel level,long floor,int x,int z){
        require(CityRules.hasRegionalSettlements(level),"Wrong world preset");
        var chunk=level.getChunk(x>>4,z>>4);
        var result=new ArrayList<BlockPos>();
        for(int bx=x;bx<x+16;bx++)for(int bz=z;bz<z+16;bz++)for(int y=6;y<=130;y++){
            var pos=new BlockPos(bx,y,bz);var state=chunk.getBlockState(pos);
            if(state.is(SaoBlocks.TIN_ORE.get())||state.is(SaoBlocks.DEEPSLATE_TIN_ORE.get()))result.add(pos);
        }
        var expected=TinDeposits.plan(level.getSeed(),floor,x,z).stream().map(v->new BlockPos(v.x(),v.y(),v.z())).collect(java.util.stream.Collectors.toSet());
        require(expected.containsAll(result),"Generated ore outside planned points "+floor);
        return result;
    }
    private static void verify(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        var ore=generated(level,1,384,384);
        require(ore.size()>=2,"No real ore from biome decoration; count="+ore.size());
        LOG.info("SAO_MATERIAL_FLOOR_ONE_OK count={}",ore.size());
        require(generated(level,1,0,0).isEmpty(),"Ore generated inside capital");
        LOG.info("SAO_MATERIAL_CAPITAL_OK");
        var upper=server.getLevel(FloorTravel.UPPER);require(upper!=null,"Missing upper realm");
        var c2=FloorLayout.center(2);var c9=FloorLayout.center(9);
        require(!generated(upper,2,c2.x()+384,c2.z()+384).isEmpty(),"No ore on second floor");
        require(generated(upper,9,c9.x()+384,c9.z()+384).isEmpty(),"Tin present outside floor band");
        LOG.info("SAO_MATERIAL_FLOOR_RANGE_OK");
        var pos=ore.get(0);
        // The fixture makes a small standing pocket beside a naturally generated ore;
        // it never adds or replaces an ore block.
        BlockPos stand=pos.east();
        if(ore.contains(stand)||ore.contains(stand.above()))stand=pos.west();
        require(!ore.contains(stand)&&!ore.contains(stand.above()),"Standing pocket overlaps ore");
        level.setBlockAndUpdate(stand,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(stand.above(),Blocks.AIR.defaultBlockState());
        require(p.teleportTo(level,stand.getX()+.5,stand.getY(),stand.getZ()+.5,Set.of(),0,0),"Teleport to mining fixture");
        p.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.STONE_PICKAXE));
        require(p.hasCorrectToolForDrops(level.getBlockState(pos)),"Stone pickaxe should be appropriate");
        require(p.gameMode.destroyBlock(pos),"Native survival harvest failed");
        require(level.getBlockState(pos).isAir(),"Mined ore still present");
        var dropped=level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2),e->e.getItem().is(SaoItems.RAW_TIN.get()));
        require(dropped.stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Raw tin drop missing or duplicated");
        require(p.getMainHandItem().getDamageValue()==1,"Pickaxe durability not consumed");
        LOG.info("SAO_MATERIAL_HARVEST_OK pos={} drops=1",pos);
        var silk=ore.get(1);
        var block=level.getBlockState(silk).getBlock().asItem();
        var pickaxe=new ItemStack(Items.STONE_PICKAXE);
        pickaxe.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
        p.setItemInHand(InteractionHand.MAIN_HAND,pickaxe);
        require(p.gameMode.destroyBlock(silk),"Native silk harvest failed");
        require(level.getEntitiesOfClass(ItemEntity.class,new AABB(silk).inflate(2),e->e.getItem().is(block)).stream().mapToInt(e->e.getItem().getCount()).sum()==1,"Silk Touch did not return ore block");
        LOG.info("SAO_MATERIAL_SILK_OK");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
