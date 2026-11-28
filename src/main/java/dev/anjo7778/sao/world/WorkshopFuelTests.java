package dev.anjo7778.sao.world;

import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.NpcRoster;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static dev.anjo7778.sao.world.NpcForgeWorkshop.Result.*;

@GameTestHolder("sao")
@PrefixGameTestTemplate(false)
public final class WorkshopFuelTests {
    private static NpcRoster.Profile smith(){
        return NpcRoster.generate(42,1).stream().filter(s->s.profile().occupation()==NpcRoster.Occupation.BLACKSMITH)
            .findFirst().orElseThrow().profile();
    }
    @GameTest(template="empty",timeoutTicks=600)
    public static void charcoalFromActualWood(GameTestHelper helper){
        var level=helper.getLevel();var smith=smith();var stock=new SimpleContainer(27);
        var pos=helper.absolutePos(new BlockPos(0,1,0));
        level.setBlockAndUpdate(pos,Blocks.FURNACE.defaultBlockState());
        var kiln=(FurnaceBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(kiln!=null,"Actual normal furnace");
        helper.assertTrue(NpcForgeWorkshop.processCharcoal(level,smith,stock,kiln)==IDLE,"No free charcoal");
        stock.setItem(0,new ItemStack(Items.OAK_LOG));
        helper.assertTrue(NpcForgeWorkshop.processCharcoal(level,smith,stock,kiln)==IDLE,"Keep last log for handles");
        stock.setItem(0,new ItemStack(Items.OAK_LOG,2));
        helper.assertTrue(NpcForgeWorkshop.processCharcoal(level,smith,stock,kiln)==BLEND,"Native planks recipe");
        helper.assertTrue(NpcForgeWorkshop.processCharcoal(level,smith,stock,kiln)==LOADED,"Native charcoal input and plank fuel");
        helper.assertTrue(kiln.getItem(0).is(Items.OAK_LOG)&&kiln.getItem(1).is(Items.OAK_PLANKS),"Real native input");
        helper.assertTrue(count(stock,Items.OAK_LOG)==0&&count(stock,Items.OAK_PLANKS)==3,"Exactly two logs used; three planks remain");
        helper.runAfterDelay(220,()->{
            helper.assertTrue(kiln.getItem(2).is(Items.CHARCOAL)&&kiln.getItem(2).getCount()==1,"Real vanilla furnace produced one charcoal");
            helper.assertTrue(NpcForgeWorkshop.processCharcoal(level,smith,stock,kiln)==COLLECTED,"Transfer actual charcoal");
            helper.assertTrue(count(stock,Items.CHARCOAL)==1&&kiln.getItem(2).isEmpty(),"No duplicate output");
            helper.assertTrue(NpcForgeWorkshop.processCharcoal(level,smith,stock,kiln)==IDLE,"Cannot replay without log");
            helper.succeed();
        });
    }

    @GameTest(template="empty",timeoutTicks=400)
    public static void interruptedOwnedMetalBatch(GameTestHelper helper){
        var level=helper.getLevel();var smith=smith();
        var stock=new SimpleContainer(27);var output=new SimpleContainer(27);
        var pos=helper.absolutePos(new BlockPos(0,1,0));
        level.setBlockAndUpdate(pos,Blocks.BLAST_FURNACE.defaultBlockState());
        var furnace=(BlastFurnaceBlockEntity)level.getBlockEntity(pos);
        helper.assertTrue(furnace!=null,"Actual blast furnace");
        // Explicit fixture for a saved, interrupted, NPC-owned batch.
        furnace.setItem(0,new ItemStack(SaoItems.STEEL_BLOOM.get()));
        var job=new CompoundTag();job.putUUID("owner",smith.id());
        job.putString("input","sao:steel_bloom");job.putString("expected","sao:steel_ingot");
        furnace.getPersistentData().put("sao_workshop_job",job);
        helper.assertTrue(NpcForgeWorkshop.process(level,smith,stock,output,furnace)==IDLE,"No fuel means no recovery");
        stock.setItem(0,new ItemStack(Items.CHARCOAL));
        job.putString("input","minecraft:raw_iron");
        helper.assertTrue(NpcForgeWorkshop.process(level,smith,stock,output,furnace)==IDLE,"Modified input marker cannot refuel");
        helper.assertTrue(count(stock,Items.CHARCOAL)==1,"Rejected recovery consumes nothing");
        job.putString("input",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(SaoItems.STEEL_BLOOM.get()).toString());
        job.putString("expected",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(SaoItems.STEEL_INGOT.get()).toString());
        helper.assertTrue(NpcForgeWorkshop.process(level,smith,stock,output,furnace)==REFUELED,"Actual charcoal refuels matching job");
        helper.assertTrue(count(stock,Items.CHARCOAL)==0&&furnace.getItem(1).is(Items.CHARCOAL),"One fuel transferred");
        helper.assertTrue(NpcForgeWorkshop.process(level,smith,stock,output,furnace)==IDLE,"No second fuel or output before cooking");
        helper.runAfterDelay(115,()->{
            helper.assertTrue(NpcForgeWorkshop.process(level,smith,stock,output,furnace)==COLLECTED,"Collect recovered steel");
            helper.assertTrue(count(stock,SaoItems.STEEL_INGOT.get())==1&&furnace.getItem(2).isEmpty(),"Exactly one finished ingot");
            helper.assertTrue(NpcForgeWorkshop.process(level,smith,stock,output,furnace)==IDLE,"No repeated finished output");
            helper.succeed();
        });
    }
    private static int count(SimpleContainer stock,net.minecraft.world.item.Item item){
        int n=0;for(int i=0;i<stock.getContainerSize();i++)if(stock.getItem(i).is(item))n+=stock.getItem(i).getCount();
        return n;
    }
}
