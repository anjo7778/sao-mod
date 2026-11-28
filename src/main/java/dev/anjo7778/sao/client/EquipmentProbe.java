package dev.anjo7778.sao.client;

import com.mojang.logging.LogUtils;
import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.item.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.world.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
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

/** Real generated forge with actual material inputs and two actual NPCs. Test fixture seeds worn tools, not stock items or currency. */
@EventBusSubscriber(modid="sao",value=Dist.CLIENT)
public final class EquipmentProbe{
    private static final Logger LOG=LogUtils.getLogger();
    private static final boolean ENABLED=Boolean.getBoolean("sao.equipmentProbe");
    private static int phase,ticks;
    private static CompletableFuture<Void> pending;
    private static UUID buyer,smith;
    private static NpcForgeWorkshop.Fixtures forge;
    private static long began;
    private static int money,receipts;
    private static boolean sawTool,sawBlade,boughtTool,finished;
    private static NpcRoster.State buyerState;
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!ENABLED||phase==3)return;
        var c=Minecraft.getInstance();
        if(phase==0&&c.screen instanceof TitleScreen title){
            require(!Files.exists(c.gameDirectory.toPath().resolve("saves/sao-equipment-test")),"Fresh isolated save");
            var rules=new GameRules();rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);rules.getRule(GameRules.RULE_DAYLIGHT).set(false,null);
            var preset=ResourceKey.create(Registries.WORLD_PRESET,ResourceLocation.fromNamespaceAndPath("sao","aincrad"));
            c.createWorldOpenFlows().createFreshLevel("sao-equipment-test",
                new LevelSettings("SAO equipment test",GameType.CREATIVE,false,Difficulty.NORMAL,true,rules,WorldDataConfiguration.DEFAULT),
                new WorldOptions(42L,true,false),access->access.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(preset).createWorldDimensions(),title);
            phase=1;return;
        }
        if(pending!=null){if(!pending.isDone())return;pending.join();pending=null;}
        if(c.screen!=null||c.player==null||c.level==null||c.getSingleplayerServer()==null)return;
        ticks++;
        if(phase==1&&ticks>=60){action(c,EquipmentProbe::setup);phase=2;ticks=0;return;}
        if(phase!=2)return;
        if(finished){LOG.info("SAO_EQUIPMENT_PROBE_PASS");phase=3;return;}
        if(ticks%10==0)action(c,EquipmentProbe::observe);
    }
    private static void setup(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();var pop=NpcPopulationData.get(server);
        level.setDayTime(4000);pop.discover(1,level.getSeed());
        buyerState=pop.roster(1).stream().filter(s->HuntContract.melee(s.profile())&&!NpcRoutine.resting(s.profile(),4000)&&level.getEntity(s.profile().id())==null).findFirst().orElseThrow();
        buyer=buyerState.profile().id();
        NpcRoster.State seller=null;
        for(var s:pop.roster(1)){
            if(s.profile().occupation()!=NpcRoster.Occupation.BLACKSMITH||NpcDailyPlan.at(s,4000).activity()!=NpcDailyPlan.Activity.WORK||level.getEntity(s.profile().id())!=null)continue;
            var fixtures=NpcForgeWorkshop.fixtures(s.profile());
            var dest=NpcDailyPlan.at(s,4000).destination();level.getChunk(dest.x()>>4,dest.z()>>4);
            level.getChunk(fixtures.stock().getX()>>4,fixtures.stock().getZ()>>4);
            if(!(level.getBlockEntity(fixtures.stock()) instanceof BarrelBlockEntity)||!(level.getBlockEntity(fixtures.output()) instanceof BarrelBlockEntity))continue;
            var at=ExpeditionGround.walkable(level,1,dest.x(),dest.z());
            if(at==null)continue;
            var npc=SaoEntities.CITY_NPC.get().create(level);if(npc==null)continue;
            npc.bind(s);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
            if(!level.noCollision(npc)||!level.addFreshEntity(npc)){npc.discard();continue;}
            seller=s;break;
        }
        require(seller!=null,"Working generated smith absent");
        smith=seller.profile().id();forge=NpcForgeWorkshop.fixtures(seller.profile());
        var stock=(BarrelBlockEntity)level.getBlockEntity(forge.stock());
        var output=(BarrelBlockEntity)level.getBlockEntity(forge.output());
        require(stock.isEmpty()&&output.isEmpty(),"No free generated stock");
        // Real inputs only; actual smith creates both tools and blades.
        stock.setItem(0,new ItemStack(Items.IRON_INGOT,3));
        stock.setItem(1,new ItemStack(Items.STICK,3));
        stock.setItem(2,new ItemStack(SaoItems.STEEL_INGOT.get(),2));
        stock.setItem(3,new ItemStack(Items.COAL));
        stock.setChanged();
        var supplyRoot=NpcSupplyData.get(server).save(new CompoundTag(),level.registryAccess());
        var entries=supplyRoot.getList("cargo",Tag.TAG_COMPOUND);
        var n=new CompoundTag();n.putUUID("id",buyer);n.putUUID("smith",smith);n.putLong("floor",1);
        n.putIntArray("materials",new int[]{0,0,0});n.putIntArray("tools",new int[]{0,250});n.putLong("delivered",1);entries.add(n);supplyRoot.put("cargo",entries);
        var supplied=NpcSupplyData.load(supplyRoot,level.registryAccess());supplied.setDirty();
        server.overworld().getDataStorage().set("sao_npc_supply",supplied);
        var sim=AdventureSimulationData.get(server);
        if(sim.ledger(buyerState)==null)sim.update(1,buyer,level.getGameTime()/AdventureProgress.EPOCH_TICKS,AdventureProgress.Mode.IDLE,false);
        var at=NpcDailyPlan.at(seller,4000).destination();
        p.getAbilities().flying=true;p.onUpdateAbilities();
        require(p.teleportTo(level,at.x()+.5,CityPlan.GROUND+5,at.z()+.5,Set.of(),0,30),"Observer teleport");
        money=pop.wallet(buyerState.profile()).col();receipts=pop.goodsReceipts(smith);
        began=level.getGameTime();
    }
    private static int count(BarrelBlockEntity barrel,Item item){
        int n=0;for(int i=0;i<barrel.getContainerSize();i++)if(barrel.getItem(i).is(item))n+=barrel.getItem(i).getCount();return n;
    }
    private static void observe(ServerPlayer p){
        var level=p.serverLevel();var server=level.getServer();var pop=NpcPopulationData.get(server);
        require(level.getGameTime()-began<1800,"Equipment purchase timed out: wallet="+pop.wallet(buyerState.profile())+" cargo="+NpcSupplyData.get(server).cargo(buyer));
        var out=(BarrelBlockEntity)level.getBlockEntity(forge.output());
        var stock=(BarrelBlockEntity)level.getBlockEntity(forge.stock());
        require(out!=null&&stock!=null,"Generated stock unavailable");
        if(!sawTool&&count(out,Items.IRON_PICKAXE)>0){
            sawTool=true;require(count(stock,Items.IRON_INGOT)==0&&count(stock,Items.STICK)<=1,"Real native tool recipe ingredients consumed");
            LOG.info("SAO_EQUIPMENT_TOOL_STOCK_OK");
            // Create the buyer after its actual item exists; the production planner selects the real shop.
            if(level.getEntity(buyer)==null){
                var state=pop.state(1,buyer);var plan=AdventureSimulationData.plan(level,state);
                require(plan.activity()==NpcDailyPlan.Activity.PREPARE,"No actual tool purchase plan");
                var at=ExpeditionGround.walkable(level,1,plan.destination().x(),plan.destination().z());
                require(at!=null,"No actual shop destination");
                var npc=SaoEntities.CITY_NPC.get().create(level);require(npc!=null,"Buyer missing");
                npc.bind(state);npc.moveTo(at.getX()+.5,at.getY(),at.getZ()+.5,0,0);
                require(level.addFreshEntity(npc),"Buyer spawn rejected");
            }
        }
        var cargo=NpcSupplyData.get(server).cargo(buyer);
        var wallet=pop.wallet(buyerState.profile());
        if(!boughtTool&&cargo.pickUses()==250){
            require(sawTool&&wallet.col()==money-12&&wallet.goodsOrders()==1&&pop.goodsReceipts(smith)==receipts+12,"Tool purchase conservation");
            require(count(out,Items.IRON_PICKAXE)==0&&cargo.axeUses()==250,"Actual purchased tool removed, unrelated wear retained");
            boughtTool=true;LOG.info("SAO_EQUIPMENT_TOOL_BUY_OK");
        }
        if(!sawBlade&&count(out,SaoItems.STEEL_SWORD.get())>0){
            sawBlade=true;require(count(stock,SaoItems.STEEL_INGOT.get())==0,"Actual steel consumed");
            LOG.info("SAO_EQUIPMENT_BLADE_STOCK_OK");
        }
        var blade=pop.blade(buyer);
        if(blade.isEmpty())return;
        require(boughtTool&&sawBlade&&wallet.goodsOrders()==2&&wallet.col()==money-12-40&&wallet.goodsSpent()==52,"Existing Col balance charged for actual equipment");
        require(pop.goodsReceipts(smith)==receipts+52&&count(out,SaoItems.STEEL_SWORD.get())==0,"Seller receives Col and real stock removed");
        var record=blade.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA).copyTag().getCompound("sao_forge");
        require(record.getUUID("maker").equals(smith)&&NativeSteelSword.quality(blade)==MaterialRules.CraftQuality.STANDARD,"Actual blade components preserved");
        LOG.info("SAO_EQUIPMENT_BLADE_BUY_OK {}",wallet);
        require(pop.wearBlade(buyer,blade),"Real stored wear operation");
        var worn=pop.blade(buyer);require(worn.getDamageValue()==1,"Persistent equipment wear");
        var regs=level.registryAccess();var restored=NpcPopulationData.load(pop.save(new CompoundTag(),regs),regs);
        require(ItemStack.matches(restored.blade(buyer),worn)&&restored.wallet(buyerState.profile()).equals(wallet)
            &&restored.goodsReceipts(smith)==receipts+52,"Saved blade, wear, wallet and seller receipt");
        LOG.info("SAO_EQUIPMENT_SAVE_OK");finished=true;
    }
    private static void action(Minecraft c,java.util.function.Consumer<ServerPlayer> work){
        var server=c.getSingleplayerServer();var uuid=c.player.getUUID();
        pending=CompletableFuture.runAsync(()->work.accept(server.getPlayerList().getPlayer(uuid)),server::execute);
    }
    private static void require(boolean ok,String msg){if(!ok)throw new IllegalStateException(msg);}
}
