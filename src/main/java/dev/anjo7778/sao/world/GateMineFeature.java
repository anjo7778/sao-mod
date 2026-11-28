package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** New chunks only: a three-wide descending mine corridor with exposed finite ore pockets.
 * Every write stays in its own decorated chunk. Never changes city blocks or arbitrary old terrain. */
public final class GateMineFeature {
    private GateMineFeature(){}
    private static boolean own(int x,int z,int ox,int oz){return x>=ox&&x<ox+16&&z>=oz&&z<oz+16;}
    private static boolean natural(BlockState s){
        return s.isAir()||s.is(BlockTags.BASE_STONE_OVERWORLD)||s.is(Blocks.DIRT)||s.is(Blocks.GRASS_BLOCK)
            ||s.is(Blocks.COARSE_DIRT)||s.is(Blocks.GRAVEL)||s.is(Blocks.SAND)||s.is(Blocks.RED_SAND)
            ||s.is(Blocks.SANDSTONE)||s.is(Blocks.RED_SANDSTONE)||s.is(Blocks.PODZOL)
            ||s.is(Blocks.IRON_ORE)||s.is(Blocks.DEEPSLATE_IRON_ORE)||s.is(Blocks.COAL_ORE)||s.is(Blocks.DEEPSLATE_COAL_ORE)
            ||s.is(Blocks.COPPER_ORE)||s.is(Blocks.DEEPSLATE_COPPER_ORE);
    }
    public static int place(WorldGenLevel level,long floor,int ox,int oz){
        if(!GateMinePlan.supported(floor))return 0;
        var center=FloorLayout.center(floor);int changed=0;
        for(var gate:ScoutingTrip.Gate.values()){
            var start=GateMinePlan.point(floor,gate,0,0);var end=GateMinePlan.point(floor,gate,GateMinePlan.STEPS-1,0);
            if(Math.max(start.x(),end.x())+2<ox||Math.min(start.x(),end.x())-2>=ox+16
                ||Math.max(start.z(),end.z())+2<oz||Math.min(start.z(),end.z())-2>=oz+16)continue;
            for(int i=0;i<GateMinePlan.STEPS;i++)for(int side=-1;side<=1;side++){
                var p=GateMinePlan.point(floor,gate,i,side);
                if(!own(p.x(),p.z(),ox,oz)||CityPlan.inside(floor,p.x()-center.x(),p.z()-center.z())
                    ||RegionalSettlements.inDecorationBuffer(floor,p.x()-center.x(),p.z()-center.z()))continue;
                var feet=new BlockPos(p.x(),p.y(),p.z());
                int head=i<5?Math.max(p.y()+3,CityPlan.GROUND+20):p.y()+3;
                // Only cut natural material. Any unusual feature stays untouched.
                boolean clear=true;
                for(int y=p.y()-1;y<=head;y++){
                    var pos=new BlockPos(p.x(),y,p.z());
                    if(!natural(level.getBlockState(pos))||!level.getFluidState(pos).isEmpty()){clear=false;break;}
                }
                if(!clear)continue;
                if(level.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),2))changed++;
                for(int y=p.y();y<=head;y++){
                    var pos=new BlockPos(p.x(),y,p.z());
                    if(!level.getBlockState(pos).isAir()&&level.setBlock(pos,Blocks.AIR.defaultBlockState(),2))changed++;
                }
            }
            for(var v:GateMinePlan.veins(floor,gate)){
                var p=v.point();if(!own(p.x(),p.z(),ox,oz))continue;
                var pos=new BlockPos(p.x(),p.y(),p.z());var old=level.getBlockState(pos);
                if(!old.is(Blocks.STONE)&&!old.is(Blocks.DEEPSLATE))continue;
                boolean deep=old.is(Blocks.DEEPSLATE);
                var block=v.resource()==GateProspects.Resource.IRON?(deep?Blocks.DEEPSLATE_IRON_ORE:Blocks.IRON_ORE)
                    :(deep?Blocks.DEEPSLATE_COAL_ORE:Blocks.COAL_ORE);
                if(level.setBlock(pos,block.defaultBlockState(),2))changed++;
            }
        }
        return changed+placeCrosscut(level,floor,ox,oz);
    }
    private static int placeCrosscut(WorldGenLevel level,long floor,int ox,int oz){
        int changed=0;var center=FloorLayout.center(floor);
        for(var gate:ScoutingTrip.Gate.values()){
            for(var p:GateMineExtension.crosscut(floor,gate)){
                if(!own(p.x(),p.z(),ox,oz)||CityPlan.inside(floor,p.x()-center.x(),p.z()-center.z())
                    ||RegionalSettlements.inDecorationBuffer(floor,p.x()-center.x(),p.z()-center.z()))continue;
                var feet=new BlockPos(p.x(),p.y(),p.z());
                boolean safe=true;
                for(int y=-1;y<=2;y++){
                    var at=feet.above(y);
                    if(!natural(level.getBlockState(at))||!level.getFluidState(at).isEmpty()||level.getBlockEntity(at)!=null){safe=false;break;}
                }
                if(!safe)continue;
                if(level.setBlock(feet.below(),Blocks.STONE.defaultBlockState(),2))changed++;
                for(int y=0;y<=2;y++)if(!level.getBlockState(feet.above(y)).isAir()
                    &&level.setBlock(feet.above(y),Blocks.AIR.defaultBlockState(),2))changed++;
            }
            for(var v:GateMineExtension.veins(floor,gate)){
                var p=v.point();
                if(!own(p.x(),p.z(),ox,oz)||CityPlan.inside(floor,p.x()-center.x(),p.z()-center.z())
                    ||RegionalSettlements.inDecorationBuffer(floor,p.x()-center.x(),p.z()-center.z()))continue;
                var at=new BlockPos(p.x(),p.y(),p.z());var old=level.getBlockState(at);
                if(!old.is(Blocks.STONE)&&!old.is(Blocks.DEEPSLATE))continue;
                boolean deep=old.is(Blocks.DEEPSLATE);
                var ore=v.resource()==GateProspects.Resource.IRON?(deep?Blocks.DEEPSLATE_IRON_ORE:Blocks.IRON_ORE)
                    :(deep?Blocks.DEEPSLATE_COAL_ORE:Blocks.COAL_ORE);
                if(level.setBlock(at,ore.defaultBlockState(),2))changed++;
            }
        }
        return changed;
    }
}
