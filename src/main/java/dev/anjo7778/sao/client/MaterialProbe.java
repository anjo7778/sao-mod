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
    private static BlockPos furnacePos,miningPos,silkPos;
    private static long miningBefore;
    private static int rawDropEvent,silkDropEvent;
    private static long metallurgyBefore,smithBefore;
    private static CompletableFuture<Void> pending;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==5)return;
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
            pending.join();phase=6;ticks=0;
        }
        if(phase==6&&++ticks>=50){dispatch(c,MaterialProbe::harvestTin);phase=7;return;}
        if(phase==7&&pending.isDone()){pending.join();phase=8;ticks=0;}
        if(phase==8&&++ticks>=8){dispatch(c,MaterialProbe::prepareSilk);phase=9;return;}
        if(phase==9&&pending.isDone()){pending.join();phase=10;ticks=0;}
        if(phase==10&&++ticks>=50){dispatch(c,MaterialProbe::harvestSilk);phase=11;return;}
        if(phase==11&&pending.isDone()){pending.join();phase=12;ticks=0;}
        if(phase==12&&++ticks>=8){dispatch(c,MaterialProbe::prepareFurnace);phase=13;return;}
        if(phase==13&&pending.isDone()){pending.join();phase=3;ticks=0;}
        if(phase==3&&++ticks>=140){
            var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
            pending=CompletableFuture.runAsync(()->finishRefiningAndCrafting(server.getPlayerList().getPlayer(uuid)),server::execute);
            phase=4;
        }
        if(phase==4&&pending.isDone()){
            pending.join();LOG.info("SAO_MATERIAL_PROBE_PASS");phase=5;
        }
    }
    private static void dispatch(Minecraft c,java.util.function.Consumer<ServerPlayer> work){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->work.accept(server.getPlayerList().getPlayer(uuid)),server::execute);
    }
    @SubscribeEvent public static void actualDrop(net.neoforged.neoforge.event.level.BlockDropsEvent event){
        if(!ENABLED||event.isCanceled()||!(event.getBreaker() instanceof ServerPlayer))return;
        if(miningPos!=null&&event.getPos().equals(miningPos))
            rawDropEvent=event.getDrops().stream().filter(e->e.getItem().is(SaoItems.RAW_TIN.get())).mapToInt(e->e.getItem().getCount()).sum();
        if(silkPos!=null&&event.getPos().equals(silkPos))
            silkDropEvent=event.getDrops().stream().filter(e->e.getItem().is(event.getState().getBlock().asItem())).mapToInt(e->e.getItem().getCount()).sum();
        LOG.info("SAO_MATERIAL_NATIVE_DROP_EVENT {} {}",event.getPos(),event.getDrops().stream().map(e->e.getItem().toString()).toList());
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
        for(var spec:MetalDeposits.ADVANCED){
            long floor=spec.material().firstFloor;
            var center=FloorLayout.center(floor);
            int count=0;
            for(int dz=0;dz<2&&count==0;dz++)for(int dx=0;dx<2&&count==0;dx++){
                int x=center.x()+384+dx*16,z=center.z()+384+dz*16;
                var chunk=upper.getChunk(x>>4,z>>4);
                var expected=MetalDeposits.plan(spec,upper.getSeed(),floor,x,z).stream().map(v->new BlockPos(v.x(),v.y(),v.z())).collect(java.util.stream.Collectors.toSet());
                for(int bx=x;bx<x+16;bx++)for(int bz=z;bz<z+16;bz++)for(int y=-32;y<=130;y++){
                    var at=new BlockPos(bx,y,bz);
                    if(SaoBlocks.material(chunk.getBlockState(at).getBlock())==spec.material()){
                        require(expected.contains(at),"Advanced ore outside seeded plan "+spec.material());count++;
                    }
                }
            }
            require(count>0,"No real advanced ore generated "+spec.material());
            LOG.info("SAO_MATERIAL_ADVANCED_ORE_OK {} floor={} count={}",spec.material(),floor,count);
        }
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
        miningPos=pos;
        silkPos=ore.stream().filter(v->!v.equals(pos)).min(java.util.Comparator.comparingDouble(v->v.distSqr(pos))).orElseThrow();
    }
    private static void harvestTin(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();var pos=miningPos;
        require(p.hasCorrectToolForDrops(level.getBlockState(pos)),"Stone pickaxe should be appropriate");
        var progress=CharacterProgressData.get(server);
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
        miningBefore=progress.state(owner).masteryExperience(CharacterProgress.Mastery.MINING);
        var loot=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(pos),level,pos,null,p,p.getMainHandItem());
        LOG.info("SAO_MATERIAL_LOOT_RESOLUTION {}",loot);
        require(loot.stream().mapToInt(stack->stack.is(SaoItems.RAW_TIN.get())?stack.getCount():0).sum()==1,"Native loot table did not produce raw tin");
        require(p.gameMode.destroyBlock(pos),"Native survival harvest failed");
        require(level.getBlockState(pos).isAir(),"Mined ore still present");
        require(rawDropEvent==1,"Native harvest event missing raw tin: "+rawDropEvent);
        require(progress.state(owner).masteryExperience(CharacterProgress.Mastery.MINING)==miningBefore+4,"Actual ore drop did not earn mining mastery");
        require(p.getMainHandItem().getDamageValue()==1,"Pickaxe durability not consumed");
    }
    private static void prepareSilk(ServerPlayer p){
        var level=p.serverLevel();
        int raw=level.getEntitiesOfClass(ItemEntity.class,new AABB(miningPos).inflate(4),e->e.getItem().is(SaoItems.RAW_TIN.get())).stream().mapToInt(e->e.getItem().getCount()).sum();
        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(SaoItems.RAW_TIN.get()))raw+=p.getInventory().getItem(i).getCount();
        require(raw==1,"Actual raw tin was lost or duplicated: "+raw);
        LOG.info("SAO_MATERIAL_HARVEST_OK pos={} drops={}",miningPos,raw);
        require(!level.getBlockState(silkPos).isAir(),"Missing second ore");
        BlockPos stand=silkPos.east();
        if(SaoBlocks.material(level.getBlockState(stand).getBlock())!=null||SaoBlocks.material(level.getBlockState(stand.above()).getBlock())!=null)stand=silkPos.west();
        require(SaoBlocks.material(level.getBlockState(stand).getBlock())==null&&SaoBlocks.material(level.getBlockState(stand.above()).getBlock())==null,"Silk fixture overlaps ore");
        level.setBlockAndUpdate(stand,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(stand.above(),Blocks.AIR.defaultBlockState());
        require(p.teleportTo(level,stand.getX()+.5,stand.getY(),stand.getZ()+.5,Set.of(),0,0),"Reach silk fixture");
        var pickaxe=new ItemStack(Items.STONE_PICKAXE);
        pickaxe.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH),1);
        p.setItemInHand(InteractionHand.MAIN_HAND,pickaxe);
    }
    private static void harvestSilk(ServerPlayer p){
        var level=p.serverLevel();
        var loot=net.minecraft.world.level.block.Block.getDrops(level.getBlockState(silkPos),level,silkPos,null,p,p.getMainHandItem());
        require(loot.stream().anyMatch(stack->stack.is(level.getBlockState(silkPos).getBlock().asItem())),"Silk loot definition");
        require(p.gameMode.destroyBlock(silkPos),"Native silk harvest failed");
        require(silkDropEvent==1,"Silk Touch did not return ore block");
        var progress=CharacterProgressData.get(level.getServer());
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
        require(progress.state(owner).masteryExperience(CharacterProgress.Mastery.MINING)==miningBefore+4,"Silk Touch should not grant duplicate mining mastery");
        LOG.info("SAO_MATERIAL_SILK_OK");
        LOG.info("SAO_MATERIAL_MINING_XP_OK");
    }
    private static void prepareFurnace(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();
        var progress=CharacterProgressData.get(server);
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
        require(p.teleportTo(level,.5,CityPlan.GROUND+1,.5,Set.of(),0,0),"Return to capital");
        furnacePos=new BlockPos(2,CityPlan.GROUND+1,0);
        level.setBlockAndUpdate(furnacePos,Blocks.BLAST_FURNACE.defaultBlockState());
        var furnace=(net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity)level.getBlockEntity(furnacePos);
        require(furnace!=null,"Missing native blast furnace");
        furnace.setItem(0,new ItemStack(SaoItems.RAW_TIN.get()));
        furnace.setItem(1,new ItemStack(Items.COAL));
        metallurgyBefore=progress.state(owner).masteryExperience(CharacterProgress.Mastery.METALLURGY);
        smithBefore=progress.state(owner).masteryExperience(CharacterProgress.Mastery.BLACKSMITHING);
    }
    private static void finishRefiningAndCrafting(ServerPlayer p){
        require(p!=null,"Missing player after furnace processing");
        var level=p.serverLevel();var progress=CharacterProgressData.get(level.getServer());
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID());
        var furnace=(net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity)level.getBlockEntity(furnacePos);
        require(furnace!=null&&furnace.getItem(2).is(SaoItems.TIN_INGOT.get())&&furnace.getItem(2).getCount()==1,"Real processed output");
        require(furnace.getItem(0).isEmpty()&&furnace.getItem(1).isEmpty(),"Furnace consumed real input and fuel");
        require(p.openMenu(furnace).isPresent(),"Native furnace menu not opened");
        p.containerMenu.clicked(2,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,p);
        require(furnace.getItem(2).isEmpty(),"Native output not extracted");
        require(progress.state(owner).masteryExperience(CharacterProgress.Mastery.METALLURGY)==metallurgyBefore+5,"Actual furnace extraction not credited");
        LOG.info("SAO_MATERIAL_METALLURGY_XP_OK");
        p.closeContainer();
        var tablePos=new BlockPos(-2,CityPlan.GROUND+1,0);
        level.setBlockAndUpdate(tablePos,Blocks.CRAFTING_TABLE.defaultBlockState());
        // Give only the test materials, then use real native slot clicks and result extraction.
        p.getInventory().setItem(9,new ItemStack(SaoItems.STEEL_INGOT.get(),2));
        p.getInventory().setItem(10,new ItemStack(Items.STICK));
        require(p.openMenu(level.getBlockState(tablePos).getMenuProvider(level,tablePos)).isPresent(),"Crafting table unavailable");
        var menu=p.containerMenu;var pick=net.minecraft.world.inventory.ClickType.PICKUP;
        menu.clicked(10,0,pick,p);menu.clicked(1,1,pick,p);menu.clicked(4,1,pick,p);
        require(menu.getCarried().isEmpty(),"Steel not placed one per slot");
        menu.clicked(11,0,pick,p);menu.clicked(7,0,pick,p);
        require(menu.getSlot(0).getItem().is(SaoItems.STEEL_SWORD.get()),"Native sword recipe not available");
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.QUICK_MOVE,p);
        require(menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(4).getItem().isEmpty()&&menu.getSlot(7).getItem().isEmpty(),"Crafting materials not consumed");
        int count=0;
        for(int i=0;i<36;i++)if(p.getInventory().getItem(i).is(SaoItems.STEEL_SWORD.get()))count+=p.getInventory().getItem(i).getCount();
        require(count==1,"Steel sword output missing or duplicated");
        require(progress.state(owner).masteryExperience(CharacterProgress.Mastery.BLACKSMITHING)==smithBefore+10,"Real sword crafting not credited");
        p.closeContainer();
        LOG.info("SAO_MATERIAL_SWORD_CRAFT_OK");
    }
    private static void require(boolean ok,String message){if(!ok)throw new IllegalStateException(message);}
}
