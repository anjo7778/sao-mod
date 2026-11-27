package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.*;
import dev.anjo7778.sao.simulation.*;
import dev.anjo7778.sao.simulation.NpcRoster.State;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.*;

/** Server-thread projection budget; only real, already loaded city chunks may receive actors. */
public final class NpcPresence {
    public static final int CAP=32, BATCH=4, RADIUS=80;
    private NpcPresence(){}
    public static void tick(PlayerTickEvent.Post event){
        if(!(event.getEntity() instanceof ServerPlayer p)||p.isSpectator()||!p.isAlive()||p.tickCount%40!=0)return;
        reconcile(p);
    }
    public static void reconcile(ServerPlayer p){
        var level=p.serverLevel();
        if(!CityRules.isCity(level,p.getBlockX(),p.getBlockZ()))return;
        long floor=FloorTravel.currentFloor(level,p.getBlockX(),p.getBlockZ());
        if(!FloorLayout.supported(floor)||!SaoWorldData.get(level.getServer()).isUnlocked(floor))return;
        var data=NpcPopulationData.get(level.getServer());
        data.discover(floor,level.getServer().overworld().getSeed());
        var visible=level.getEntitiesOfClass(CityNpc.class,p.getBoundingBox().inflate(RADIUS+24),e->e.originFloor()==floor&&e.isAlive());
        int allowance=Math.min(BATCH,CAP-visible.size());if(allowance<=0)return;
        var c=FloorLayout.center(floor);long time=level.getDayTime();
        List<State> roster=data.roster(floor);
        // Stable but rotating scan avoids permanently favoring the first roles in the roster.
        int start=Math.floorMod((int)(level.getGameTime()/40)*BATCH,roster.size());
        for(int i=0;i<roster.size()&&allowance>0;i++){
            State s=roster.get((start+i)%roster.size());
            if(level.getEntity(s.profile().id())!=null)continue;
            var dest=NpcDailyPlan.at(s,time).destination();
            double d=p.distanceToSqr(c.x()+dest.x()+.5,CityPlan.GROUND+1,c.z()+dest.z()+.5);
            if(d>RADIUS*RADIUS||d<10*10)continue;
            if(spawn(level,s,c,dest))allowance--;
        }
    }
    private static boolean spawn(ServerLevel level,State s,FloorLayout.Center center,NpcDailyPlan.Point dest){
        int[] offsets={0,2,-2,4,-4,6,-6};
        var base=CityPlan.column(s.profile().originFloor(),dest.x(),dest.z());
        for(int dx:offsets)for(int dz:offsets){
            int lx=dest.x()+dx,lz=dest.z()+dz;
            var col=CityPlan.column(s.profile().originFloor(),lx,lz);
            if(base.zone()==CityPlan.Zone.LOT&&!Objects.equals(base.plot(),col.plot())
                ||base.zone()==CityPlan.Zone.PLAZA&&col.zone()!=CityPlan.Zone.PLAZA)continue;
            var pos=new BlockPos(center.x()+lx,CityPlan.GROUND+1,center.z()+lz);
            if(!level.hasChunkAt(pos)||!CityRules.isCity(level,pos.getX(),pos.getZ()))continue;
            if(!level.getBlockState(pos).getCollisionShape(level,pos).isEmpty()
                ||!level.getBlockState(pos.above()).getCollisionShape(level,pos.above()).isEmpty()
                ||level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty())continue;
            AABB box=new AABB(pos.getX()+.1,pos.getY(),pos.getZ()+.1,pos.getX()+.9,pos.getY()+1.8,pos.getZ()+.9);
            if(!level.getEntitiesOfClass(CityNpc.class,box.inflate(.35)).isEmpty())continue;
            var npc=SaoEntities.CITY_NPC.get().create(level);if(npc==null)return false;
            npc.bind(s);npc.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,0,0);
            if(!level.noCollision(npc))continue;
            return level.addFreshEntity(npc);
        }
        return false;
    }
}
