package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.CityPlan;
import dev.anjo7778.sao.simulation.RegionalSettlements;
import dev.anjo7778.sao.simulation.FloorLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;

public final class CityRules {
    private CityRules(){}
    public static boolean hasCities(ServerLevel level){
        if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator n))return false;
        return level.dimension().equals(Level.OVERWORLD)&&(n.generatorSettings().is(FloorTravel.FIRST_CITY)||n.generatorSettings().is(FloorTravel.FIRST_CITY_DETAIL)||n.generatorSettings().is(FloorTravel.FIRST_CITY_TEMPLATES)||n.generatorSettings().is(FloorTravel.FIRST_CITY_TEMPLATES_V2)||n.generatorSettings().is(FloorTravel.FIRST_CITY_DISTRICTS)||n.generatorSettings().is(FloorTravel.FIRST_CITY_LANDMARKS)||n.generatorSettings().is(FloorTravel.FIRST_CITY_REGIONAL))
            || level.dimension().equals(FloorTravel.UPPER)&&(n.generatorSettings().is(FloorTravel.UPPER_CITY)||n.generatorSettings().is(FloorTravel.UPPER_CITY_DETAIL)||n.generatorSettings().is(FloorTravel.UPPER_CITY_TEMPLATES)||n.generatorSettings().is(FloorTravel.UPPER_CITY_TEMPLATES_V2)||n.generatorSettings().is(FloorTravel.UPPER_CITY_DISTRICTS)||n.generatorSettings().is(FloorTravel.UPPER_CITY_LANDMARKS)||n.generatorSettings().is(FloorTravel.UPPER_CITY_REGIONAL));
    }
    public static boolean hasLandmarks(ServerLevel level){
        if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator n))return false;
        return level.dimension().equals(Level.OVERWORLD)&&(n.generatorSettings().is(FloorTravel.FIRST_CITY_LANDMARKS)||n.generatorSettings().is(FloorTravel.FIRST_CITY_REGIONAL))
            ||level.dimension().equals(FloorTravel.UPPER)&&(n.generatorSettings().is(FloorTravel.UPPER_CITY_LANDMARKS)||n.generatorSettings().is(FloorTravel.UPPER_CITY_REGIONAL));
    }
    public static boolean hasRegionalSettlements(ServerLevel level){
        if(!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator n))return false;
        return level.dimension().equals(Level.OVERWORLD)&&n.generatorSettings().is(FloorTravel.FIRST_CITY_REGIONAL)
            ||level.dimension().equals(FloorTravel.UPPER)&&n.generatorSettings().is(FloorTravel.UPPER_CITY_REGIONAL);
    }
    public static boolean isSafeZone(ServerLevel level,int x,int z){
        if(isCity(level,x,z))return true;
        if(!hasRegionalSettlements(level))return false;
        boolean upper=level.dimension().equals(FloorTravel.UPPER);
        return RegionalSettlements.at(upper?FloorLayout.floorAt(x,z):1,upper?FloorLayout.local(x):x,upper?FloorLayout.local(z):z)!=null;
    }
    public static boolean isCity(ServerLevel level,int x,int z){
        if(!hasCities(level))return false;
        boolean upper=level.dimension().equals(FloorTravel.UPPER);
        long f=upper?FloorLayout.floorAt(x,z):1;
        return CityPlan.inside(f,upper?FloorLayout.local(x):x,upper?FloorLayout.local(z):z);
    }
    public static void spawn(LevelEvent.CreateSpawnPosition event){
        if(event.getLevel() instanceof ServerLevel level&&level.dimension().equals(Level.OVERWORLD)&&hasCities(level)){
            level.setDefaultSpawnPos(new BlockPos(0,CityPlan.GROUND+1,0),0);
            event.setCanceled(true);
        }
    }
    public static void spawnCheck(MobSpawnEvent.SpawnPlacementCheck event){
        BlockPos p=event.getPos();
        if(event.getEntityType().getCategory()==MobCategory.MONSTER&&isSafeZone(event.getLevel().getLevel(),p.getX(),p.getZ()))
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
    }
    public static void damage(LivingIncomingDamageEvent event){
        if(!(event.getEntity().level() instanceof ServerLevel level))return;
        var a=event.getSource().getEntity();var b=event.getEntity();
        if(!(a instanceof Player||a instanceof Enemy))return;
        if(isSafeZone(level,b.getBlockX(),b.getBlockZ())||
            a.level()==level&&isSafeZone(level,a.getBlockX(),a.getBlockZ()))event.setCanceled(true);
    }
}
