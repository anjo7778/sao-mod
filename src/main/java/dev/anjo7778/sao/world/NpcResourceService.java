package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import dev.anjo7778.sao.simulation.GateProspects.Resource;
import dev.anjo7778.sao.simulation.CharacterProgress.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.phys.*;
import java.util.*;

/** Gather only physically exposed, assigned starter prospect blocks during actual surveys.
 * Bounded selection, real loot and block destruction, finite tools and durable cargo.
 * No mining through walls, no arbitrary trees/builds, no offscreen material grants. */
public final class NpcResourceService {
    private NpcResourceService(){}
    private record Attempt(BlockPos pos,Resource resource,UUID smith,long surveySince,long finish){}
    private record Target(BlockPos pos,Resource resource,double distance){}
    private static final Map<CityNpc,Attempt> ACTIVE=new WeakHashMap<>();
    private static final double REACH=4.5;
    private static long epoch(ServerLevel level){return Math.max(0,level.getServer().overworld().getGameTime()/AdventureProgress.EPOCH_TICKS);}
    private static boolean matches(BlockState s,Resource r){
        return switch(r){
            case IRON->s.is(Blocks.IRON_ORE)||s.is(Blocks.DEEPSLATE_IRON_ORE);
            case COAL->s.is(Blocks.COAL_ORE)||s.is(Blocks.DEEPSLATE_COAL_ORE);
            case DEADWOOD->s.is(Blocks.OAK_LOG);
        };
    }
    private static Item drop(Resource r){return switch(r){case IRON->Items.RAW_IRON;case COAL->Items.COAL;case DEADWOOD->Items.OAK_LOG;};}
    private static ItemStack tool(NpcSupplyData.Cargo c,Resource r){
        int uses=r==Resource.DEADWOOD?c.axeUses():c.pickUses();
        if(uses<=0)return ItemStack.EMPTY;
        var tool=new ItemStack(r==Resource.DEADWOOD?Items.IRON_AXE:Items.IRON_PICKAXE);
        tool.setDamageValue(Math.max(0,tool.getMaxDamage()-uses));return tool;
    }
    private static Profile smith(ServerLevel level,CityNpc npc,ScoutingTrip.Trip trip){
        var point=ScoutingTrip.gatePoint(npc.originFloor(),trip.gate(),-16);
        return NpcPopulationData.get(level.getServer()).roster(npc.originFloor()).stream().map(State::profile)
            .filter(p->p.occupation()==Occupation.BLACKSMITH)
            .min(Comparator.<Profile>comparingLong(p->{long x=p.workX()-point.x(),z=p.workZ()-point.z();return x*x+z*z;})
                .thenComparing(Profile::id)).orElse(null);
    }
    private static boolean eligible(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,State state){
        if(!npc.boundAdventurer()||!npc.isAlive()||!CityRules.hasRegionalSettlements(level)||state==null
            ||trip==null||trip.phase()!=ScoutingTrip.Phase.SURVEY||epoch(level)-trip.since()>=HuntContract.WINDOW_EPOCHS
            ||state.needs().health()<70||state.needs().equipment()<40||state.needs().supplies()<25
            ||CityRules.isSafeZone(level,npc.getBlockX(),npc.getBlockZ())
            ||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))return false;
        var hunt=NpcPopulationData.get(level.getServer()).hunt(npc.getUUID());
        if(hunt!=null&&hunt.matches(npc.originFloor(),trip)&&hunt.active())return false;
        var center=FloorLayout.center(npc.originFloor());
        var p=ScoutingTrip.gatePoint(npc.originFloor(),trip.gate(),36);
        return Math.hypot(npc.getX()-center.x()-p.x(),npc.getZ()-center.z()-p.z())<=9;
    }
    private static boolean reachable(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,BlockPos pos,Resource resource){
        if(!level.hasChunkAt(pos)||CityRules.isSafeZone(level,pos.getX(),pos.getZ())
            ||FloorTravel.currentFloor(level,pos.getX(),pos.getZ())!=npc.originFloor()
            ||GateProspects.resourceAt(npc.originFloor(),trip.gate(),pos.getX(),pos.getZ()).orElse(null)!=resource
            ||pos.getY()<CityPlan.GROUND-18||pos.getY()>CityPlan.GROUND+18
            ||!matches(level.getBlockState(pos),resource)||level.getBlockEntity(pos)!=null)return false;
        Vec3 eye=npc.getEyePosition(),at=Vec3.atCenterOf(pos);
        if(eye.distanceToSqr(at)>REACH*REACH)return false;
        var hit=level.clip(new ClipContext(eye,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos);
    }
    private static Target find(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,NpcSupplyData.Cargo cargo){
        Target best=null;
        for(var c:GateProspects.columns(npc.originFloor(),trip.gate())){
            if(!cargo.canGather(c.resource(),1)||Math.hypot(npc.getX()-c.x()-.5,npc.getZ()-c.z()-.5)>REACH)continue;
            for(int y=Math.max(CityPlan.GROUND-18,npc.getBlockY()-3);y<=Math.min(CityPlan.GROUND+18,npc.getBlockY()+4);y++){
                var pos=new BlockPos(c.x(),y,c.z());
                if(!reachable(npc,level,trip,pos,c.resource()))continue;
                double distance=npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos));
                if(best==null||distance<best.distance())best=new Target(pos,c.resource(),distance);
            }
        }
        return best;
    }
    public static boolean holding(CityNpc npc){return ACTIVE.containsKey(npc);}
    private static void clear(CityNpc npc){if(ACTIVE.remove(npc)!=null)npc.updateDisplayItem();}
    public static boolean tick(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip){
        if(!npc.boundAdventurer())return false;
        var state=NpcPopulationData.get(level.getServer()).state(npc.originFloor(),npc.getUUID());
        if(state==null){clear(npc);return false;}
        var supplies=NpcSupplyData.get(level.getServer());
        if(trip==null||!trip.active()){
            clear(npc);return deliver(npc,level,state);
        }
        if(!eligible(npc,level,trip,state)){clear(npc);return false;}
        var attempt=ACTIVE.get(npc);
        if(attempt!=null){
            var cargo=supplies.candidate(npc.getUUID(),npc.originFloor(),attempt.smith());
            if(attempt.surveySince()!=trip.since()||cargo==null||!cargo.smith().equals(attempt.smith())
                ||!cargo.canGather(attempt.resource(),1)||!reachable(npc,level,trip,attempt.pos(),attempt.resource())){
                clear(npc);return false;
            }
            npc.getNavigation().stop();
            npc.getLookControl().setLookAt(attempt.pos().getX()+.5,attempt.pos().getY()+.5,attempt.pos().getZ()+.5,30,30);
            if(npc.tickCount%10==0)npc.swing(InteractionHand.MAIN_HAND);
            if(level.getGameTime()>=attempt.finish()){
                harvest(npc,level,trip,attempt,cargo,supplies);
                clear(npc);
            }
            return true;
        }
        if(npc.tickCount%20!=Math.floorMod(npc.getUUID().hashCode(),20)||!npc.atScheduledDestination())return false;
        var assigned=smith(level,npc,trip);if(assigned==null)return false;
        var cargo=supplies.candidate(npc.getUUID(),npc.originFloor(),assigned.id());
        if(cargo==null||cargo.size()>=NpcSupplyData.CAPACITY)return false;
        var target=find(npc,level,trip,cargo);if(target==null)return false;
        ACTIVE.put(npc,new Attempt(target.pos(),target.resource(),cargo.smith(),trip.since(),level.getGameTime()+30));
        npc.getNavigation().stop();npc.setItemSlot(EquipmentSlot.MAINHAND,tool(cargo,target.resource()));
        npc.setDropChance(EquipmentSlot.MAINHAND,0);npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    private static void harvest(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,Attempt attempt,NpcSupplyData.Cargo cargo,NpcSupplyData supplies){
        var pos=attempt.pos();var r=attempt.resource();var expected=drop(r);var tool=tool(cargo,r);
        if(tool.isEmpty()||!reachable(npc,level,trip,pos,r))return;
        var state=level.getBlockState(pos);
        // Native loot table is evaluated once, actual block removed without a second spawned drop.
        var drops=Block.getDrops(state,level,pos,null,npc,tool);
        if(drops.isEmpty()||drops.stream().anyMatch(s->!s.is(expected)))return;
        int amount=drops.stream().mapToInt(ItemStack::getCount).sum();
        if(!cargo.canGather(r,amount))return;
        var next=cargo.gather(r,amount);
        if(!level.destroyBlock(pos,false,npc))return;
        supplies.store(npc.getUUID(),next);
        var progress=CharacterProgressData.get(level.getServer());
        var owner=new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,npc.getUUID());
        if(r==Resource.DEADWOOD)progress.awardNext(owner,Source.GATHER,2,Mastery.GATHERING,4);
        else progress.profession(owner,Math.max(0,level.getServer().overworld().getGameTime()/24000),Mastery.MINING);
    }
    public static NpcDailyPlan.Plan deliveryPlan(ServerLevel level,State state){
        if(state.profile().occupation()!=Occupation.ADVENTURER||state.needs().health()<35
            ||NpcRoutine.resting(state.profile(),level.getDayTime()))return null;
        var c=NpcSupplyData.get(level.getServer()).cargo(state.profile().id());
        if(c==null||c.size()==0||c.floor()!=state.profile().currentFloor())return null;
        var smith=NpcPopulationData.get(level.getServer()).state(c.floor(),c.smith());
        if(smith==null||smith.profile().occupation()!=Occupation.BLACKSMITH)return null;
        var p=smith.profile();
        return new NpcDailyPlan.Plan(NpcDailyPlan.Activity.PREPARE,new NpcDailyPlan.Point(p.workX()+3,p.workZ()-3));
    }
    private static boolean deliver(CityNpc npc,ServerLevel level,State state){
        var data=NpcSupplyData.get(level.getServer());var cargo=data.cargo(npc.getUUID());
        if(cargo==null||cargo.size()==0||cargo.floor()!=npc.originFloor())return false;
        if(AdventureSimulationData.get(level.getServer()).journey(state).active())return false;
        var plan=deliveryPlan(level,state);
        if(plan==null||!npc.matchesPlan(plan)||!npc.atScheduledDestination())return false;
        var smith=NpcPopulationData.get(level.getServer()).state(cargo.floor(),cargo.smith());
        if(smith==null)return false;
        var pos=NpcForgeWorkshop.fixtures(smith.profile()).stock();
        if(!level.hasChunkAt(pos)||!CityRules.isSafeZone(level,pos.getX(),pos.getZ())
            ||npc.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)>100
            ||!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)
            ||level.players().stream().anyMatch(p->p.containerMenu!=p.inventoryMenu&&p.distanceToSqr(npc)<20*20))return false;
        var contents=NpcForgeWorkshop.copy(barrel);
        if(!deposit(contents,cargo))return false;
        // Single server-thread transfer: never create cargo offscreen or deliver it twice.
        NpcForgeWorkshop.apply(barrel,contents);data.store(npc.getUUID(),cargo.emptied());
        npc.getNavigation().stop();npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    static boolean deposit(List<ItemStack> contents,NpcSupplyData.Cargo cargo){
        if(cargo.size()==0||cargo.delivered()==Long.MAX_VALUE)return false;
        if(cargo.iron()>0&&!NpcForgeWorkshop.insert(contents,new ItemStack(Items.RAW_IRON,cargo.iron())))return false;
        if(cargo.coal()>0&&!NpcForgeWorkshop.insert(contents,new ItemStack(Items.COAL,cargo.coal())))return false;
        return cargo.logs()==0||NpcForgeWorkshop.insert(contents,new ItemStack(Items.OAK_LOG,cargo.logs()));
    }
}
