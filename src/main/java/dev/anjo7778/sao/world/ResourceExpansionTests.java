package dev.anjo7778.sao.world;

import dev.anjo7778.sao.simulation.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.*;

@GameTestHolder("sao")
@PrefixGameTestTemplate(false)
public final class ResourceExpansionTests {
    @GameTest(template="empty")
    public static void managedWoodlandSeedPersistence(GameTestHelper helper){
        var bank=new NpcWoodlandData();
        var site=GateWoodlandPlan.sites(1,ScoutingTrip.Gate.EAST).getFirst();
        var root=new BlockPos(site.x(),CityPlan.GROUND+1,site.z());
        helper.assertFalse(bank.use(1,root),"No free sapling");
        helper.assertTrue(bank.store(1,root.offset(1,0,0),1)==0,"Only assigned plot stores seeds");
        helper.assertTrue(bank.store(1,root,2)==2&&bank.seeds(1,root)==2,"Actual seed reserve");
        helper.assertTrue(bank.use(1,root)&&bank.seeds(1,root)==1,"Consume exactly one seed");
        var regs=helper.getLevel().registryAccess();
        var restored=NpcWoodlandData.load(bank.save(new CompoundTag(),regs),regs);
        helper.assertTrue(restored.seeds(1,root)==1&&restored.use(1,root)&&!restored.use(1,root),"Seed use survives reload and cannot replay");
        helper.assertTrue(restored.store(1,root,20)==NpcWoodlandData.CAPACITY,"Bounded seed reserve");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void connectedFiniteCrosscutsAndWoodlands(GameTestHelper helper){
        for(long floor=1;floor<=10;floor++)for(var gate:ScoutingTrip.Gate.values()){
            var center=FloorLayout.center(floor);
            var old=GateMinePlan.route(floor,gate);var extra=GateMineExtension.crosscut(floor,gate);
            var route=GateMineExtension.route(floor,gate);var veins=GateMineExtension.veins(floor,gate);
            helper.assertTrue(route.size()==old.size()+extra.size()&&route.subList(0,old.size()).equals(old),"Preserve original mine");
            helper.assertTrue(new HashSet<>(route).size()==route.size()&&veins.size()==12,"Finite unique crosscut geometry");
            for(int i=1;i<route.size();i++){
                var a=route.get(i-1);var b=route.get(i);
                helper.assertTrue(Math.abs(a.x()-b.x())+Math.abs(a.z()-b.z())==1&&Math.abs(a.y()-b.y())<=1,"Adjacent traversable steps");
            }
            var oldOre=GateMinePlan.veins(floor,gate);
            for(var v:veins){
                var p=v.point();
                helper.assertTrue(!route.contains(new GateMinePlan.Point(p.x(),p.y()-1,p.z())),"Ore not in walking lane");
                helper.assertTrue(oldOre.stream().noneMatch(o->o.point().equals(p)),"No overlap with existing ore");
                helper.assertTrue(ScoutingTrip.inRange(floor,p.x()-center.x(),p.z()-center.z()),"Crosscut stays inside exploration boundary");
            }
            var sites=GateWoodlandPlan.sites(floor,gate);
            helper.assertTrue(sites.size()==2&&!sites.getFirst().equals(sites.getLast()),"Two distinct plots");
            for(var site:sites){
                helper.assertTrue(!CityPlan.inside(floor,site.x()-center.x(),site.z()-center.z())
                    &&ScoutingTrip.inRange(floor,site.x()-center.x(),site.z()-center.z()),"Woodland outside capital");
                helper.assertTrue(GateProspects.columns(floor,gate).stream().noneMatch(p->p.x()==site.x()&&p.z()==site.z()),"No starter prospect overlap");
            }
        }
        helper.assertTrue(GateWoodlandPlan.sites(11,ScoutingTrip.Gate.EAST).isEmpty()
            &&GateMineExtension.route(11,ScoutingTrip.Gate.EAST).isEmpty(),"Starter region scope");
        helper.succeed();
    }
}
