package dev.anjo7778.sao.world;

import dev.anjo7778.sao.entity.FrenzyBoar;
import dev.anjo7778.sao.entity.SaoEntities;
import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Loaded-only wilderness spawning. No biome-list pollution, spawn eggs, forced chunks or distant mob simulation. */
public final class BoarSpawns {
    public static final int PERIOD=200, LOCAL_CAP=6, ATTEMPTS=8;
    private BoarSpawns(){}
    public static boolean habitat(long floor,int x,int z){
        if(!FloorLayout.supported(floor))return false;
        var theme=FloorCatalog.themeFor(floor);
        if(theme!=FloorCatalog.Theme.PLAINS&&theme!=FloorCatalog.Theme.ANCIENT_FOREST)return false;
        long radius=FloorCatalog.radiusFor(floor)-64L;
        return (long)x*x+(long)z*z<radius*radius&&!CityPlan.inside(floor,x,z)
            &&RegionalSettlements.at(floor,x,z)==null;
    }
    public static boolean allowedArea(ServerLevel level,int x,int z){
        if(!CityRules.hasRegionalSettlements(level)||CityRules.isSafeZone(level,x,z))return false;
        long floor=FloorTravel.currentFloor(level,x,z);
        if(!FloorLayout.supported(floor)||!SaoWorldData.get(level.getServer()).isUnlocked(floor))return false;
        var c=FloorLayout.center(floor);
        return habitat(floor,x-c.x(),z-c.z());
    }
    public static void tick(PlayerTickEvent.Post event){
        if(event.getEntity() instanceof ServerPlayer p&&p.tickCount%PERIOD==0)attempt(p);
    }
    public static FrenzyBoar attempt(ServerPlayer p){
        var level=p.serverLevel();
        if(!p.isAlive()||p.isSpectator()||level.getDifficulty()==Difficulty.PEACEFUL
            ||!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)||!allowedArea(level,p.getBlockX(),p.getBlockZ()))return null;
        if(level.getEntitiesOfClass(FrenzyBoar.class,p.getBoundingBox().inflate(96),FrenzyBoar::isAlive).size()>=LOCAL_CAP)return null;
        for(int i=0;i<ATTEMPTS;i++){
            double angle=p.getRandom().nextDouble()*Math.PI*2;int r=24+p.getRandom().nextInt(25);
            int x=p.getBlockX()+(int)Math.round(Math.cos(angle)*r),z=p.getBlockZ()+(int)Math.round(Math.sin(angle)*r);
            var pos=surface(level,x,z);
            if(pos==null||Math.abs(pos.getY()-p.getY())>20)continue;
            if(level.players().stream().anyMatch(q->!q.isSpectator()&&q.distanceToSqr(x+.5,pos.getY(),z+.5)<24*24))continue;
            var boar=SaoEntities.FRENZY_BOAR.get().create(level);
            if(boar==null)return null;
            boar.moveTo(x+.5,pos.getY(),z+.5,p.getRandom().nextFloat()*360,0);
            if(!level.noCollision(boar))continue;
            if(level.addFreshEntity(boar))return boar;
        }
        return null;
    }
    public static BlockPos surface(ServerLevel level,int x,int z){
        if(!allowedArea(level,x,z))return null;
        // Check surrounding chunks before any heightmap/block/collision lookup.
        for(int dx:new int[]{-2,2})for(int dz:new int[]{-2,2})
            if(!level.hasChunkAt(new BlockPos(x+dx,96,z+dz)))return null;
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        if(y<48||y>205)return null;
        var p=new BlockPos(x,y,z);var below=p.below();
        if(!level.getWorldBorder().isWithinBounds(p)||!level.canSeeSky(p.above())
            ||!level.getBlockState(below).is(BlockTags.DIRT)
            ||!level.getBlockState(below).isFaceSturdy(level,below,Direction.UP)
            ||!level.getBlockState(p).getCollisionShape(level,p).isEmpty()
            ||!level.getBlockState(p.above()).getCollisionShape(level,p.above()).isEmpty()
            ||!level.getFluidState(p).isEmpty()||!level.getFluidState(p.above()).isEmpty()||!level.getFluidState(below).isEmpty())return null;
        return p;
    }
}
