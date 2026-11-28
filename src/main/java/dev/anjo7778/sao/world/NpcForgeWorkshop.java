package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.item.NativeSteelSword;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import dev.anjo7778.sao.simulation.NpcRoster.Occupation;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;

import java.util.*;
import java.util.function.Predicate;

/**
 * First autonomous stocked workshop. Only exact native fixtures in the smith's assigned
 * v2 forge, only while a real NPC is present at work. Never scans player containers.
 * Two barrels: (6,1,2) ingredients/intermediates, (6,1,3) finished blades.
 * Furnace (-6,1,-4) processes actual recipes; other furnace remains available.
 * Existing ordinary furnaces refine raw iron but cannot process steel bloom until replaced.
 * No automatic input generation, no offscreen production, no market sale yet.
 */
public final class NpcForgeWorkshop {
    private static final String JOB="sao_workshop_job";
    private NpcForgeWorkshop(){}
    public enum Result { IDLE, BLEND, LOADED, COLLECTED, FORGED }
    public record Fixtures(BlockPos stock,BlockPos output,BlockPos furnace,BlockPos anvil){}
    public static Fixtures fixtures(NpcRoster.Profile p){
        var c=FloorLayout.center(p.currentFloor());
        int x=c.x()+p.workX(),z=c.z()+p.workZ(),y=CityPlan.GROUND+1;
        return new Fixtures(new BlockPos(x+6,y,z+2),new BlockPos(x+6,y,z+3),
            new BlockPos(x-6,y,z-4),new BlockPos(x-6,y,z-1));
    }
    static List<ItemStack> copy(Container c){
        var list=new ArrayList<ItemStack>(c.getContainerSize());
        for(int i=0;i<c.getContainerSize();i++)list.add(c.getItem(i).copy());
        return list;
    }
    static void apply(Container c,List<ItemStack> list){
        for(int i=0;i<list.size();i++)if(!ItemStack.matches(c.getItem(i),list.get(i)))c.setItem(i,list.get(i));
        c.setChanged();
    }
    static boolean insert(List<ItemStack> items,ItemStack addition){
        if(addition.isEmpty())return false;
        var rest=addition.copy();
        for(var existing:items)if(ItemStack.isSameItemSameComponents(existing,rest)&&existing.getCount()<existing.getMaxStackSize()){
            int n=Math.min(rest.getCount(),existing.getMaxStackSize()-existing.getCount());existing.grow(n);rest.shrink(n);
            if(rest.isEmpty())return true;
        }
        for(int i=0;i<items.size();i++)if(items.get(i).isEmpty()){
            int n=Math.min(rest.getCount(),rest.getMaxStackSize());items.set(i,rest.copyWithCount(n));rest.shrink(n);
            if(rest.isEmpty())return true;
        }
        return false;
    }
    private static boolean consume(List<ItemStack> list,Item item,int n){return ForgeService.take(list,s->s.is(item),n);}
    private static boolean fuel(List<ItemStack> list){return ForgeService.take(list,s->s.is(Items.COAL)||s.is(Items.CHARCOAL),1);}
    private static boolean has(List<ItemStack> items,Item item){return items.stream().anyMatch(s->s.is(item));}
    private static boolean isWorking(CityNpc npc,ServerLevel level){
        var profile=npc.boundProfile();
        if(profile==null||profile.occupation()!=Occupation.BLACKSMITH||!npc.isAlive()
            ||!CityRules.hasRegionalSettlements(level)||!npc.atScheduledDestination())return false;
        var state=NpcPopulationData.get(level.getServer()).state(npc.originFloor(),npc.getUUID());
        var plan=state==null?null:NpcDailyPlan.at(state,level.getDayTime());
        return plan!=null&&plan.activity()==NpcDailyPlan.Activity.WORK&&npc.matchesPlan(plan);
    }
    public static Result tick(CityNpc npc,ServerLevel level){
        if(!isWorking(npc,level))return Result.IDLE;
        var profile=npc.boundProfile();var pos=fixtures(profile);
        for(var p:List.of(pos.stock(),pos.output(),pos.furnace(),pos.anvil()))
            if(!level.hasChunkAt(p)||!CityRules.isSafeZone(level,p.getX(),p.getZ()))return Result.IDLE;
        if(!(level.getBlockEntity(pos.stock()) instanceof BarrelBlockEntity stock)
            ||!(level.getBlockEntity(pos.output()) instanceof BarrelBlockEntity output)
            ||!(level.getBlockEntity(pos.furnace()) instanceof AbstractFurnaceBlockEntity furnace)
            ||!level.getBlockState(pos.anvil()).is(BlockTags.ANVIL))return Result.IDLE;
        // Do not modify a furnace or barrel while a player has that native menu open.
        if(level.players().stream().anyMatch(p->p.containerMenu!=p.inventoryMenu&&p.distanceToSqr(npc)<20*20))return Result.IDLE;
        var result=process(level,profile,stock,output,furnace);
        if(result!=Result.IDLE)npc.swing(InteractionHand.MAIN_HAND);
        return result;
    }
    /** All changes run on the server thread. Furnace owns pending native processing and persists the owner marker. */
    public static Result process(ServerLevel level,NpcRoster.Profile smith,Container stock,Container output,AbstractFurnaceBlockEntity furnace){
        if(smith.occupation()!=Occupation.BLACKSMITH)return Result.IDLE;
        var progress=CharacterProgressData.get(level.getServer());
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,smith.id());
        long day=Math.max(0,level.getServer().overworld().getGameTime()/24000);
        var tag=furnace.getPersistentData();
        if(tag.contains(JOB)){
            var job=tag.getCompound(JOB);
            if(!job.hasUUID("owner")||!job.getUUID("owner").equals(smith.id()))return Result.IDLE;
            var actual=furnace.getItem(2);
            // If a player removed the input/output while the job was in flight, abandon the marker.
            if(actual.isEmpty()){
                if(furnace.getItem(0).isEmpty()){tag.remove(JOB);furnace.setChanged();}
                return Result.IDLE;
            }
            if(!furnace.getItem(0).isEmpty()||actual.getCount()!=1
                ||!net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(actual.getItem()).toString().equals(job.getString("expected")))return Result.IDLE;
            var after=copy(stock);
            if(!insert(after,actual))return Result.IDLE;
            // Real output already processed; transfer exactly once.
            furnace.removeItemNoUpdate(2);tag.remove(JOB);furnace.setChanged();apply(stock,after);
            progress.profession(owner,day,Mastery.METALLURGY);
            return Result.COLLECTED;
        }
        // Do not claim player-loaded input, output or fuel.
        if(!furnace.getItem(0).isEmpty()||!furnace.getItem(1).isEmpty()||!furnace.getItem(2).isEmpty())return Result.IDLE;
        var before=copy(stock);
        // Convert real gathered deadwood with native recipes only when a handle is needed.
        if(!has(before,Items.STICK)){
            var planks=CraftingInput.of(1,2,List.of(new ItemStack(Items.OAK_PLANKS),new ItemStack(Items.OAK_PLANKS)));
            if(craftStock(level,stock,planks,Items.STICK))return Result.BLEND;
            var log=CraftingInput.of(1,1,List.of(new ItemStack(Items.OAK_LOG)));
            if(craftStock(level,stock,log,Items.OAK_PLANKS))return Result.BLEND;
        }
        // Produce actual native tools only when one of this smith's gatherers is running low.
        var supply=NpcSupplyData.get(level.getServer());
        Item replacement=null;
        if(supply.needsTool(smith.id(),false)&&!has(copy(output),Items.IRON_PICKAXE))replacement=Items.IRON_PICKAXE;
        else if(supply.needsTool(smith.id(),true)&&!has(copy(output),Items.IRON_AXE))replacement=Items.IRON_AXE;
        if(replacement!=null){
            var toolInput=replacement==Items.IRON_PICKAXE
                ?CraftingInput.of(3,3,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),
                    ItemStack.EMPTY,new ItemStack(Items.STICK),ItemStack.EMPTY,ItemStack.EMPTY,new ItemStack(Items.STICK),ItemStack.EMPTY))
                :CraftingInput.of(2,3,List.of(new ItemStack(Items.IRON_INGOT),new ItemStack(Items.IRON_INGOT),
                    new ItemStack(Items.IRON_INGOT),new ItemStack(Items.STICK),ItemStack.EMPTY,new ItemStack(Items.STICK)));
            if(craftGoods(level,stock,output,toolInput,replacement)){
                progress.craft(owner,day,Mastery.BLACKSMITHING);return Result.FORGED;
            }
            // Keep iron for actual tool replacement rather than consuming it in steel.
            if(has(before,Items.RAW_IRON))return furnace.getBlockState().is(Blocks.BLAST_FURNACE)
                ?load(level,smith,stock,furnace,Items.RAW_IRON,RecipeType.BLASTING)
                :load(level,smith,stock,furnace,Items.RAW_IRON,RecipeType.SMELTING);
            return Result.IDLE;
        }
        var steel=SaoItems.STEEL_INGOT.get();
        var in=copy(stock);var out=copy(output);
        if(consume(in,steel,2)&&consume(in,Items.STICK,1)&&fuel(in)){
            var blade=NativeSteelSword.forged(MaterialRules.steelQuality(progress.state(owner).mastery(Mastery.BLACKSMITHING)),smith.id(),smith.name());
            if(!insert(out,blade))return Result.IDLE;
            apply(stock,in);apply(output,out);progress.craft(owner,day,Mastery.BLACKSMITHING);
            return Result.FORGED;
        }
        // Give existing steel bloom first priority, then create new bloom from real iron and carbon,
        // finally smelt actual raw iron. Do not create blends if the furnace cannot refine them.
        boolean blast=furnace.getBlockState().is(Blocks.BLAST_FURNACE);
        if(blast&&has(before,SaoItems.STEEL_BLOOM.get())){
            return load(level,smith,stock,furnace,SaoItems.STEEL_BLOOM.get(),RecipeType.BLASTING);
        }
        in=copy(stock);
        if(blast&&consume(in,Items.IRON_INGOT,1)&&fuel(in)
            &&insert(in,new ItemStack(SaoItems.STEEL_BLOOM.get()))){
            apply(stock,in);return Result.BLEND;
        }
        if(has(before,Items.RAW_IRON))return blast
            ?load(level,smith,stock,furnace,Items.RAW_IRON,RecipeType.BLASTING)
            :load(level,smith,stock,furnace,Items.RAW_IRON,RecipeType.SMELTING);
        return Result.IDLE;
    }
    private static boolean craftGoods(ServerLevel level,Container stock,Container output,CraftingInput input,Item expected){
        var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElse(null);
        if(recipe==null)return false;
        var result=recipe.value().assemble(input,level.registryAccess());
        if(result.isEmpty()||!result.is(expected)||result.getCount()!=1)return false;
        var contents=copy(stock);var out=copy(output);
        for(var ingredient:input.items())if(!ingredient.isEmpty()&&!consume(contents,ingredient.getItem(),ingredient.getCount()))return false;
        if(!insert(out,result))return false;
        apply(stock,contents);apply(output,out);return true;
    }
    private static boolean craftStock(ServerLevel level,Container stock,CraftingInput input,Item expected){
        var recipe=level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,input,level).orElse(null);
        if(recipe==null)return false;
        var result=recipe.value().assemble(input,level.registryAccess());
        if(result.isEmpty()||!result.is(expected))return false;
        var contents=copy(stock);
        for(var ingredient:input.items())if(!ingredient.isEmpty()&&!consume(contents,ingredient.getItem(),ingredient.getCount()))return false;
        if(!insert(contents,result))return false;
        apply(stock,contents);return true;
    }
    private static <T extends AbstractCookingRecipe> Result load(ServerLevel level,NpcRoster.Profile smith,Container stock,
            AbstractFurnaceBlockEntity furnace,Item input,RecipeType<T> type){
        var recipe=level.getRecipeManager().getRecipeFor(type,new SingleRecipeInput(new ItemStack(input)),level).orElse(null);
        if(recipe==null)return Result.IDLE;
        var expected=recipe.value().assemble(new SingleRecipeInput(new ItemStack(input)),level.registryAccess());
        if(expected.isEmpty()||expected.getCount()!=1)return Result.IDLE;
        var after=copy(stock);
        if(!consume(after,input,1))return Result.IDLE;
        // Reuse existing native burn time. Otherwise consume and insert one actual unit of fuel.
        Item coal=null;
        if(!furnace.getBlockState().getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)){
            coal=has(after,Items.COAL)?Items.COAL:Items.CHARCOAL;
            if(!consume(after,coal,1))return Result.IDLE;
        }
        apply(stock,after);furnace.setItem(0,new ItemStack(input));
        if(coal!=null)furnace.setItem(1,new ItemStack(coal));
        var job=new net.minecraft.nbt.CompoundTag();job.putUUID("owner",smith.id());
        job.putString("expected",net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(expected.getItem()).toString());
        furnace.getPersistentData().put(JOB,job);furnace.setChanged();
        return Result.LOADED;
    }
}
