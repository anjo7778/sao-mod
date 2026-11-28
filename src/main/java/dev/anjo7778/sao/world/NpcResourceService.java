package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.*;
import dev.anjo7778.sao.simulation.GateProspects.Resource;
import dev.anjo7778.sao.simulation.CharacterProgress.Mastery;
import dev.anjo7778.sao.simulation.CharacterProgress.Source;
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
    private static final Map<CityNpc,MineRun> MINES=new WeakHashMap<>();
    private static final Map<CityNpc,Long> MINE_TRIED=new WeakHashMap<>();
    private static final class MineRun {
        final ScoutingTrip.Gate gate;
        final long since,started;
        final UUID smith;
        int index;
        boolean returning;
        long lastStep,lastPath;
        Attempt work;
        MineRun(ScoutingTrip.Trip trip,UUID smith,long now){
            gate=trip.gate();since=trip.since();this.smith=smith;started=now;lastStep=now;
        }
    }
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
            ||!matches(level.getBlockState(pos),resource)||level.getBlockEntity(pos)!=null)return false;
        boolean surface=pos.getY()>=CityPlan.GROUND-18&&pos.getY()<=CityPlan.GROUND+18
            &&GateProspects.resourceAt(npc.originFloor(),trip.gate(),pos.getX(),pos.getZ()).orElse(null)==resource;
        boolean mine=GateMinePlan.resourceAt(npc.originFloor(),trip.gate(),pos.getX(),pos.getY(),pos.getZ())==resource;
        if(!surface&&!mine)return false;
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
    public static boolean holding(CityNpc npc){return ACTIVE.containsKey(npc)||MINES.containsKey(npc);}
    private static void clear(CityNpc npc){if(ACTIVE.remove(npc)!=null)npc.updateDisplayItem();}
    public static boolean tick(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip){
        if(!npc.boundAdventurer())return false;
        var state=NpcPopulationData.get(level.getServer()).state(npc.originFloor(),npc.getUUID());
        if(state==null){clear(npc);MINES.remove(npc);return false;}
        var supplies=NpcSupplyData.get(level.getServer());
        if(MINES.containsKey(npc))return mineTick(npc,level,trip,state,supplies);
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
        var target=find(npc,level,trip,cargo);
        if(target==null)return startMine(npc,level,trip,cargo);
        ACTIVE.put(npc,new Attempt(target.pos(),target.resource(),cargo.smith(),trip.since(),level.getGameTime()+30));
        npc.getNavigation().stop();npc.setItemSlot(EquipmentSlot.MAINHAND,tool(cargo,target.resource()));
        npc.setDropChance(EquipmentSlot.MAINHAND,0);npc.swing(InteractionHand.MAIN_HAND);
        return true;
    }
    private static BlockPos block(GateMinePlan.Point p){return new BlockPos(p.x(),p.y(),p.z());}
    private static boolean walkable(ServerLevel level,BlockPos p){
        return level.hasChunkAt(p)&&level.getBlockState(p).getCollisionShape(level,p).isEmpty()
            &&level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()
            &&level.getBlockState(p.below()).isFaceSturdy(level,p.below(),net.minecraft.core.Direction.UP)
            &&level.getFluidState(p).isEmpty()&&level.getFluidState(p.above()).isEmpty();
    }
    private static boolean startMine(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,NpcSupplyData.Cargo cargo){
        if(!GateMinePlan.supported(npc.originFloor())||!cargo.canGather(Resource.IRON,1)
            ||MINE_TRIED.getOrDefault(npc,-1L)==trip.since()||epoch(level)-trip.since()>=2)return false;
        var route=GateMinePlan.route(npc.originFloor(),trip.gate());
        // Whole known passage must already exist and be loaded; do not create a tunnel at runtime.
        for(var point:route)if(!walkable(level,block(point)))return false;
        boolean available=GateMinePlan.veins(npc.originFloor(),trip.gate()).stream().anyMatch(v->{
            var p=block(v.point());return level.hasChunkAt(p)&&matches(level.getBlockState(p),v.resource());
        });
        if(!available)return false;
        var entry=block(route.getFirst());var path=npc.getNavigation().createPath(entry,0);
        if(path==null||!path.canReach()){MINE_TRIED.put(npc,trip.since());return false;}
        var run=new MineRun(trip,cargo.smith(),level.getGameTime());MINES.put(npc,run);
        MINE_TRIED.put(npc,trip.since());npc.getNavigation().moveTo(path,.9);
        return true;
    }
    private static boolean mineTick(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,State state,NpcSupplyData supplies){
        var run=MINES.get(npc);var route=GateMinePlan.route(npc.originFloor(),run.gate);
        if(route.isEmpty()){MINES.remove(npc);return false;}
        long now=level.getGameTime();
        var cargo=supplies.candidate(npc.getUUID(),npc.originFloor(),run.smith);
        if(!run.returning&&(trip==null||trip.phase()!=ScoutingTrip.Phase.SURVEY||trip.since()!=run.since
            ||epoch(level)-run.since>=3||now-run.started>=1000||cargo==null||!cargo.canGather(Resource.IRON,1)
            ||state.needs().health()<70||state.needs().equipment()<40||state.needs().supplies()<25
            ||!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING))){
            returnMine(npc,run,route,now);
        }
        if(run.returning&&now-run.lastStep>200){
            // A blocked player-modified route must not hold the simulation forever.
            MINES.remove(npc);npc.getNavigation().stop();npc.updateDisplayItem();return false;
        }
        var dest=block(route.get(run.index));
        // Adjacent ramp steps differ by one block vertically. A broad spherical
        // tolerance falsely reaches the next step while still standing on the previous one.
        double dx=npc.getX()-dest.getX()-.5,dz=npc.getZ()-dest.getZ()-.5;
        boolean arrived=dx*dx+dz*dz<=1.0&&Math.abs(npc.getY()-dest.getY())<.8;
        if(!arrived){
            if(!run.returning&&now-run.lastStep>100){returnMine(npc,run,route,now);return true;}
            if(now-run.lastPath>=10&&walkable(level,dest)
                &&(npc.getNavigation().isDone()||!dest.equals(npc.getNavigation().getTargetPos()))){
                var path=npc.getNavigation().createPath(dest,0);
                if(path!=null&&path.canReach())npc.getNavigation().moveTo(path,.9);
                run.lastPath=now;
            }
            return true;
        }
        if(run.returning){
            if(run.index==0){MINES.remove(npc);npc.getNavigation().stop();npc.updateDisplayItem();return false;}
            run.index=Math.max(0,run.index-1);run.lastStep=now;return true;
        }
        // Search the final four stations, not just the end of the ramp. Each
        // reachable vein still requires the existing line-of-sight and real loot transaction.
        if(run.index<route.size()-4){run.index++;run.lastStep=now;return true;}
        npc.getNavigation().stop();
        if(run.work!=null){
            var work=run.work;
            if(cargo==null||!cargo.canGather(work.resource(),1)||!reachable(npc,level,trip,work.pos(),work.resource())){
                run.work=null;npc.updateDisplayItem();return true;
            }
            npc.getLookControl().setLookAt(work.pos().getX()+.5,work.pos().getY()+.5,work.pos().getZ()+.5,30,30);
            if(npc.tickCount%10==0)npc.swing(InteractionHand.MAIN_HAND);
            if(now>=work.finish()){
                harvest(npc,level,trip,work,cargo,supplies);
                run.work=null;npc.updateDisplayItem();
            }
            return true;
        }
        for(var v:GateMinePlan.veins(npc.originFloor(),run.gate)){
            var pos=block(v.point());
            if(cargo!=null&&cargo.canGather(v.resource(),1)&&reachable(npc,level,trip,pos,v.resource())){
                run.work=new Attempt(pos,v.resource(),run.smith,run.since,now+35);
                npc.setItemSlot(EquipmentSlot.MAINHAND,tool(cargo,v.resource()));npc.setDropChance(EquipmentSlot.MAINHAND,0);
                npc.swing(InteractionHand.MAIN_HAND);return true;
            }
        }
        if(run.index<route.size()-1){run.index++;run.lastStep=now;return true;}
        returnMine(npc,run,route,now);return true;
    }
    private static void returnMine(CityNpc npc,MineRun run,List<GateMinePlan.Point> route,long now){
        run.returning=true;run.work=null;npc.updateDisplayItem();npc.getNavigation().stop();
        double best=Double.MAX_VALUE;int index=0;
        for(int i=0;i<route.size();i++){var p=route.get(i);double d=npc.distanceToSqr(p.x()+.5,p.y(),p.z()+.5);if(d<best){best=d;index=i;}}
        run.index=index;run.lastStep=now;run.lastPath=0;
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
