package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.CityNpc;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.GateProspects.Resource;
import dev.anjo7778.sao.simulation.NpcRoster.State;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;
import java.util.*;

/** Local, represented oak harvesting. Seed first, cut top-down, replant using one actual sapling.
 * Refuse tall/branched trees, occupied plots, protected areas and absent chunks. */
final class NpcWoodlandService {
    private static final int HEIGHT=6,RADIUS=3;
    private static final double REACH=4.5;
    private static final Map<CityNpc,Run> RUNS=new WeakHashMap<>();
    private static final Map<CityNpc,Long> TRIED=new WeakHashMap<>();
    private record Work(BlockPos pos,boolean leaf,long finish){}
    private static final class Run{
        final BlockPos root,approach;final UUID smith;final long since,started;
        long lastPath;Work work;
        Run(BlockPos root,BlockPos approach,UUID smith,long since,long started){
            this.root=root;this.approach=approach;this.smith=smith;this.since=since;this.started=started;
        }
    }
    private NpcWoodlandService(){}
    static boolean holding(CityNpc npc){return RUNS.containsKey(npc);}
    private static boolean allowed(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,State s){
        return trip!=null&&trip.phase()==ScoutingTrip.Phase.SURVEY
            &&s.needs().health()>=70&&s.needs().supplies()>=25&&s.needs().equipment()>=40
            &&level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
            &&!CityRules.isSafeZone(level,npc.getBlockX(),npc.getBlockZ())
            &&level.getServer().overworld().getGameTime()/AdventureProgress.EPOCH_TICKS-trip.since()<3;
    }
    private static boolean marked(ServerLevel level,BlockPos p){
        return level.hasChunkAt(p)&&level.getBlockState(p.below(2)).is(Blocks.ROOTED_DIRT)
            &&level.getFluidState(p).isEmpty()&&!CityRules.isSafeZone(level,p.getX(),p.getZ());
    }
    private static BlockPos root(ServerLevel level,GateWoodlandPlan.Site site){
        for(int y=CityPlan.GROUND+18;y>=CityPlan.GROUND-18;y--){
            var p=new BlockPos(site.x(),y,site.z());
            if(marked(level,p))return p;
        }
        return null;
    }
    private static boolean trunk(ServerLevel level,BlockPos p){
        var s=level.getBlockState(p);
        return s.is(Blocks.OAK_LOG)&&s.getValue(RotatedPillarBlock.AXIS)==Direction.Axis.Y;
    }
    private static int trunkHeight(ServerLevel level,BlockPos root){
        int n=0;while(n<=HEIGHT&&trunk(level,root.above(n)))n++;
        if(n==0||n>HEIGHT)return 0;
        // A managed small oak has a single vertical trunk. Leave large or branched trees intact.
        for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dz=-RADIUS;dz<=RADIUS;dz++){
            if(dx==0&&dz==0)continue;
            var column=root.offset(dx,0,dz);
            if(!level.hasChunkAt(column))return 0;
            for(int dy=0;dy<=HEIGHT+1;dy++)if(level.getBlockState(column.above(dy)).is(Blocks.OAK_LOG))return 0;
        }
        return n;
    }
    private static boolean canPlant(ServerLevel level,BlockPos p){
        if(!marked(level,p)||!level.getBlockState(p).isAir()||!Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level,p))return false;
        for(int y=1;y<=HEIGHT;y++)if(!level.getBlockState(p.above(y)).isAir()&&!level.getBlockState(p.above(y)).is(Blocks.OAK_LEAVES))return false;
        return true;
    }
    static boolean start(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,State state,NpcSupplyData.Cargo cargo){
        if(!allowed(npc,level,trip,state)||TRIED.getOrDefault(npc,-1L)==trip.since()||cargo.axeUses()<=0)return false;
        var bank=NpcWoodlandData.get(level.getServer());
        for(var site:GateWoodlandPlan.sites(npc.originFloor(),trip.gate())){
            var root=root(level,site);
            if(root==null||RUNS.values().stream().anyMatch(run->run.root.equals(root)))continue;
            int height=trunkHeight(level,root);
            boolean replant=bank.seeds(npc.originFloor(),root)>0&&canPlant(level,root);
            if(!replant&&(height==0||cargo.axeUses()<height||NpcSupplyData.CAPACITY-cargo.size()<height))continue;
            var at=ExpeditionGround.walkable(level,npc.originFloor(),site.approachX(),site.approachZ());
            if(at==null||Math.abs(at.getY()-root.getY())>1)continue;
            var path=npc.getNavigation().createPath(at,0);
            if(path==null||!path.canReach())continue;
            RUNS.put(npc,new Run(root,at,cargo.smith(),trip.since(),level.getGameTime()));
            TRIED.put(npc,trip.since());npc.getNavigation().moveTo(path,.9);return true;
        }
        return false;
    }
    private static boolean reached(CityNpc npc,ServerLevel level,BlockPos pos){
        if(!level.hasChunkAt(pos)||CityRules.isSafeZone(level,pos.getX(),pos.getZ()))return false;
        Vec3 eye=npc.getEyePosition(),at=Vec3.atCenterOf(pos);
        if(eye.distanceToSqr(at)>REACH*REACH)return false;
        var hit=level.clip(new ClipContext(eye,at,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,npc));
        return hit.getType()==HitResult.Type.BLOCK&&hit.getBlockPos().equals(pos);
    }
    private static boolean leaf(ServerLevel level,BlockPos p){
        var s=level.getBlockState(p);
        return s.is(Blocks.OAK_LEAVES)&&!s.getValue(LeavesBlock.PERSISTENT);
    }
    private static BlockPos findLeaf(CityNpc npc,ServerLevel level,BlockPos root){
        BlockPos best=null;double nearest=Double.MAX_VALUE;
        for(int dx=-RADIUS;dx<=RADIUS;dx++)for(int dz=-RADIUS;dz<=RADIUS;dz++)for(int y=1;y<=HEIGHT;y++){
            var p=root.offset(dx,y,dz);if(!level.hasChunkAt(p)||!leaf(level,p)||!reached(npc,level,p))continue;
            double distance=npc.getEyePosition().distanceToSqr(Vec3.atCenterOf(p));
            if(distance<nearest){nearest=distance;best=p;}
        }
        return best;
    }
    private static boolean stop(CityNpc npc){RUNS.remove(npc);npc.getNavigation().stop();npc.updateDisplayItem();return false;}
    static boolean tick(CityNpc npc,ServerLevel level,ScoutingTrip.Trip trip,State state,NpcSupplyData supplies){
        var run=RUNS.get(npc);if(run==null)return false;
        long now=level.getGameTime();long floor=npc.originFloor();
        if(!allowed(npc,level,trip,state)||run.since!=trip.since()||now-run.started>700||!marked(level,run.root))return stop(npc);
        var cargo=supplies.candidate(npc.getUUID(),floor,run.smith);
        if(cargo==null||!cargo.smith().equals(run.smith))return stop(npc);
        if(npc.distanceToSqr(run.approach.getX()+.5,run.approach.getY(),run.approach.getZ()+.5)>1.8*1.8){
            if(now-run.lastPath>=20&&npc.getNavigation().isDone()){
                var path=npc.getNavigation().createPath(run.approach,0);
                if(path==null||!path.canReach())return stop(npc);
                npc.getNavigation().moveTo(path,.9);run.lastPath=now;
            }
            return true;
        }
        npc.getNavigation().stop();
        var bank=NpcWoodlandData.get(level.getServer());
        if(canPlant(level,run.root)&&bank.seeds(floor,run.root)>0){
            // Empty root plus actual seed. Never force a tree or replace existing blocks.
            if(level.setBlockAndUpdate(run.root,Blocks.OAK_SAPLING.defaultBlockState())){
                bank.use(floor,run.root);npc.swing(InteractionHand.MAIN_HAND);
            }
            return stop(npc);
        }
        int height=trunkHeight(level,run.root);
        if(height==0||cargo.axeUses()<height||NpcSupplyData.CAPACITY-cargo.size()<height)return stop(npc);
        if(run.work==null){
            BlockPos target;
            boolean harvestLeaf=bank.seeds(floor,run.root)==0;
            if(harvestLeaf)target=findLeaf(npc,level,run.root);
            else{
                target=run.root.above(height-1);
                // Clear only real reachable leaves obstructing the top-down cut.
                if(!reached(npc,level,target)){target=findLeaf(npc,level,run.root);harvestLeaf=true;}
            }
            if(target==null||!reached(npc,level,target))return stop(npc);
            run.work=new Work(target,harvestLeaf,now+(harvestLeaf?12:30));
            if(!harvestLeaf){
                var axe=new ItemStack(Items.IRON_AXE);
                axe.setDamageValue(Math.max(0,axe.getMaxDamage()-cargo.axeUses()));
                npc.setItemSlot(EquipmentSlot.MAINHAND,axe);npc.setDropChance(EquipmentSlot.MAINHAND,0);
            }
        }
        var work=run.work;
        if(!reached(npc,level,work.pos())||work.leaf()&&!leaf(level,work.pos())
            ||!work.leaf()&&(!trunk(level,work.pos())||bank.seeds(floor,run.root)==0))return stop(npc);
        npc.getLookControl().setLookAt(work.pos().getX()+.5,work.pos().getY()+.5,work.pos().getZ()+.5,30,30);
        if(npc.tickCount%10==0)npc.swing(InteractionHand.MAIN_HAND);
        if(now<work.finish())return true;
        var block=level.getBlockState(work.pos());
        var tool=work.leaf()?ItemStack.EMPTY:npc.getMainHandItem();
        var drops=Block.getDrops(block,level,work.pos(),null,npc,tool);
        if(work.leaf()){
            if(!level.destroyBlock(work.pos(),false,npc))return stop(npc);
            for(var drop:drops){
                var remaining=drop.copy();
                if(drop.is(Items.OAK_SAPLING))remaining.shrink(bank.store(floor,run.root,drop.getCount()));
                if(!remaining.isEmpty())Block.popResource(level,work.pos(),remaining);
            }
        }else{
            if(drops.isEmpty()||drops.stream().anyMatch(stack->!stack.is(Items.OAK_LOG)))return stop(npc);
            int amount=drops.stream().mapToInt(ItemStack::getCount).sum();
            if(!cargo.canGather(Resource.DEADWOOD,amount)||!level.destroyBlock(work.pos(),false,npc))return stop(npc);
            supplies.store(npc.getUUID(),cargo.gather(Resource.DEADWOOD,amount));
            var progress=CharacterProgressData.get(level.getServer());
            progress.awardNext(new CharacterProgressData.Owner(CharacterProgressData.Kind.NPC,npc.getUUID()),
                CharacterProgress.Source.GATHER,2,CharacterProgress.Mastery.GATHERING,4);
        }
        run.work=null;return true;
    }
}
