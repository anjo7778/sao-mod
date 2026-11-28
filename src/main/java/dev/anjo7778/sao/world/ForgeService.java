package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.item.NativeSteelSword;
import dev.anjo7778.sao.item.SaoItems;
import dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import dev.anjo7778.sao.simulation.MaterialRules;
import dev.anjo7778.sao.simulation.NpcDailyPlan;
import dev.anjo7778.sao.simulation.NpcRoster.Occupation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import java.util.*;
import java.util.function.Predicate;

/**
 * First real forge transaction. Standard table crafting remains available. A dedicated
 * forge action uses 2 steel + stick + fuel, and the actual smith's mastery. Never
 * removes inputs unless the output can fit. No free Col, virtual metal, or generated NPC stock.
 * Instant production is a first slice, not a timed forge animation or autonomous supply chain.
 */
public final class ForgeService {
    private ForgeService(){}
    public static final int MAIN_SLOTS=36;
    public enum Outcome { COMPLETED, UNAVAILABLE, MATERIALS_OR_SPACE }
    /** Side-effect free inventory simulation; original stacks are never mutated. */
    public static Optional<List<ItemStack>> plan(List<ItemStack> before,ItemStack output){
        if(before.size()!=MAIN_SLOTS||!output.is(SaoItems.STEEL_SWORD.get())||output.getCount()!=1)return Optional.empty();
        var after=new ArrayList<ItemStack>(MAIN_SLOTS);
        for(var stack:before)after.add(stack.copy());
        if(!take(after,s->s.is(SaoItems.STEEL_INGOT.get()),2)
            ||!take(after,s->s.is(Items.STICK),1)
            ||!take(after,s->s.is(Items.COAL)||s.is(Items.CHARCOAL),1))return Optional.empty();
        for(int i=0;i<after.size();i++)if(after.get(i).isEmpty()){
            after.set(i,output.copy());
            return Optional.of(List.copyOf(after));
        }
        return Optional.empty();
    }
    static boolean take(List<ItemStack> stacks,Predicate<ItemStack> matches,int count){
        for(var stack:stacks){
            if(count==0)return true;
            if(!stack.isEmpty()&&matches.test(stack)){
                int n=Math.min(count,stack.getCount());
                stack.shrink(n);count-=n;
            }
        }
        return count==0;
    }
    private static boolean eligible(ServerPlayer p){
        var level=p.serverLevel();long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
        return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&p.containerMenu==p.inventoryMenu
            &&CityRules.hasRegionalSettlements(level)&&CityRules.isSafeZone(level,p.getBlockX(),p.getBlockZ())
            &&SaoWorldData.get(level.getServer()).isUnlocked(floor);
    }
    private static boolean furnaceNear(ServerLevel level,BlockPos center){
        for(var pos:BlockPos.betweenClosed(center.offset(-6,-3,-6),center.offset(6,3,6))){
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            if(state.is(Blocks.FURNACE)||state.is(Blocks.BLAST_FURNACE))return true;
        }
        return false;
    }
    private static BlockPos anvilNear(ServerLevel level,BlockPos center){
        BlockPos nearest=null;double distance=65;
        for(var pos:BlockPos.betweenClosed(center.offset(-8,-3,-8),center.offset(8,3,8))){
            if(!level.hasChunkAt(pos)||!level.getBlockState(pos).is(BlockTags.ANVIL))continue;
            double d=pos.distSqr(center);
            if(d<distance){nearest=pos.immutable();distance=d;}
        }
        return nearest;
    }
    /** Shift-use steel at an anvil in a settlement, with a nearby furnace. */
    public static void useAnvil(PlayerInteractEvent.RightClickBlock event){
        if(!(event.getEntity() instanceof ServerPlayer p)||event.getHand()!=InteractionHand.MAIN_HAND
            ||!p.isShiftKeyDown()||!p.getMainHandItem().is(SaoItems.STEEL_INGOT.get())||!eligible(p)
            ||!p.serverLevel().getBlockState(event.getPos()).is(BlockTags.ANVIL))return;
        event.setCanceled(true);event.setCancellationResult(InteractionResult.CONSUME);
        var result=selfForge(p,event.getPos());
        if(result!=Outcome.COMPLETED)feedback(p,result);
    }
    public static Outcome selfForge(ServerPlayer p,BlockPos anvil){
        if(!eligible(p)||p.distanceToSqr(anvil.getX()+.5,anvil.getY()+.5,anvil.getZ()+.5)>36
            ||!p.serverLevel().getBlockState(anvil).is(BlockTags.ANVIL)||!furnaceNear(p.serverLevel(),anvil))
            return Outcome.UNAVAILABLE;
        return produce(p,new CharacterProgressData.Owner(CharacterProgressData.Kind.PLAYER,p.getUUID()),p.getName().getString(),anvil);
    }
    /** A real, present smith must be at work, within reach of forge equipment. Player supplies material/fuel. */
    public static Outcome commission(ServerPlayer p,CityNpc smith){
        var level=p.serverLevel();var profile=smith.boundProfile();
        if(!eligible(p)||smith.level()!=level||!smith.isAlive()||profile==null||profile.occupation()!=Occupation.BLACKSMITH
            ||smith.distanceToSqr(p)>36||!smith.hasLineOfSight(p)||smith.originFloor()!=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ())){
            feedback(p,Outcome.UNAVAILABLE);return Outcome.UNAVAILABLE;
        }
        var state=NpcPopulationData.get(level.getServer()).state(smith.originFloor(),smith.getUUID());
        var current=state==null?null:NpcDailyPlan.at(state,level.getDayTime());
        var anvil=anvilNear(level,smith.blockPosition());
        if(current==null||current.activity()!=NpcDailyPlan.Activity.WORK||!smith.matchesPlan(current)||!smith.atScheduledDestination()
            ||anvil==null||!furnaceNear(level,anvil)){
            feedback(p,Outcome.UNAVAILABLE);return Outcome.UNAVAILABLE;
        }
        var result=produce(p,new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,profile.id()),profile.name(),anvil);
        if(result==Outcome.COMPLETED)smith.swing(InteractionHand.MAIN_HAND);
        else feedback(p,result);
        return result;
    }
    private static Outcome produce(ServerPlayer p,CharacterProgressData.Owner maker,String name,BlockPos anvil){
        var server=p.serverLevel().getServer();var progress=CharacterProgressData.get(server);
        var quality=MaterialRules.steelQuality(progress.state(maker).mastery(Mastery.BLACKSMITHING));
        String display=name.length()>80?name.substring(0,80):name;
        var sword=NativeSteelSword.forged(quality,maker.id(),display);
        var inventory=p.getInventory();var before=new ArrayList<ItemStack>(MAIN_SLOTS);
        for(int i=0;i<MAIN_SLOTS;i++)before.add(inventory.getItem(i));
        var planned=plan(before,sword);
        if(planned.isEmpty())return Outcome.MATERIALS_OR_SPACE;
        // Server thread: one commit, no await and no input/output drops.
        var after=planned.get();
        for(int i=0;i<MAIN_SLOTS;i++)if(!ItemStack.matches(before.get(i),after.get(i)))inventory.setItem(i,after.get(i));
        inventory.setChanged();p.containerMenu.broadcastChanges();
        progress.craft(maker,Math.max(0,server.overworld().getGameTime()/24000),Mastery.BLACKSMITHING);
        p.serverLevel().playSound(null,anvil,SoundEvents.ANVIL_USE,SoundSource.BLOCKS,.6f,1f);
        p.sendSystemMessage(Component.translatable("message.sao.forged",Component.translatable("quality.sao."+quality.name().toLowerCase(Locale.ROOT)),display));
        return Outcome.COMPLETED;
    }
    private static void feedback(ServerPlayer p,Outcome result){
        p.sendSystemMessage(Component.translatable(result==Outcome.MATERIALS_OR_SPACE?"message.sao.forge_materials":"message.sao.forge_unavailable"));
    }
}
