package dev.anjo7778.sao.gametest;

import dev.anjo7778.sao.simulation.FloorProgress;
import dev.anjo7778.sao.simulation.FloorLayout;
import dev.anjo7778.sao.world.SaoWorldData;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("sao")
@PrefixGameTestTemplate(false)
public final class FoundationTests {
    @GameTest(template = "empty")
    public static void progressionRoundTrip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var data = new SaoWorldData();
        helper.assertTrue(data.isUnlocked(1) && !data.isUnlocked(2), "New world progression");
        helper.assertFalse(data.recordVictory(2, FloorProgress.Victor.PLAYER), "Locked floor victory");
        helper.assertTrue(data.recordVictory(1, FloorProgress.Victor.NPC_PARTY), "NPC unlock");
        helper.assertTrue(data.isDirty(), "Persistence must be marked dirty");
        var saved = data.save(new CompoundTag(), registries);
        var restored = SaoWorldData.load(saved, registries);
        helper.assertTrue(restored.isUnlocked(2) && !restored.isUnlocked(3), "NBT round trip");
        helper.assertFalse(restored.recordVictory(1, FloorProgress.Victor.PLAYER), "Replay victory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void floorShape(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "floor_one"));
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
        var random = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), 42L);
        var density = random.router().initialDensityWithoutJaggedness();
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0, 0, 0)) > 0, "Solid center");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0, -60, 0)) < 0, "Void below");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0, 200, 0)) < 0, "Open sky");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(4100, 0, 0)) < 0, "Void past edge");
        helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(3000, 0, 3000)) < 0, "Circular corner");
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void upperFloorShape(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess();
        var key = ResourceKey.create(Registries.NOISE_SETTINGS, ResourceLocation.fromNamespaceAndPath("sao", "upper_floors"));
        var settings = access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
        var density = RandomState.create(settings, access.lookupOrThrow(Registries.NOISE), 42L).router().initialDensityWithoutJaggedness();
        for (long floor : new long[] {2, 3, 1000, 10000, FloorLayout.MAX_FLOOR}) {
            var c = FloorLayout.center(floor);
            helper.assertTrue(FloorLayout.floorAt(c.x(), c.z()) == floor, "Address round trip");
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x(), 0, c.z())) > 0, "Solid upper floor " + floor);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x(), -60, c.z())) < 0, "Void below");
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x(), 290, c.z())) < 0, "Open sky");
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(c.x()+6000, 0, c.z())) < 0, "Gap outside floor");
        }
        helper.succeed();
    }
    @GameTest(template = "empty")
    public static void castleArchitecture(GameTestHelper helper) {
        var access=helper.getLevel().registryAccess();
        for(String name:new String[]{"floor_one_castle","upper_floors_castle"}) {
            var key=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao",name));
            var settings=access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
            var density=RandomState.create(settings,access.lookupOrThrow(Registries.NOISE),42L).router().finalDensity();
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(384,180,384))>0,"Solid support pillar: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(384,280,192))>0,"Solid roof rib: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(0,280,0))<0,"Clear spawn above: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(576,280,576))<0,"Open daylight court: "+name);
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(384,-60,384))<0,"Void preserved: "+name);
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void guardianIdentity(GameTestHelper helper){
        var data=new SaoWorldData();
        var a=new java.util.UUID(1,2);var b=new java.util.UUID(1,3);
        helper.assertTrue(data.beginGuardian(1,a),"Reserve real guardian identity");
        helper.assertFalse(data.beginGuardian(1,b),"Duplicate encounter rejected");
        helper.assertFalse(data.completeGuardian(1,b),"Unrelated mob cannot unlock");
        var restored=SaoWorldData.load(data.save(new CompoundTag(),helper.getLevel().registryAccess()),helper.getLevel().registryAccess());
        helper.assertTrue(a.equals(restored.activeGuardian()),"Active guardian identity saved");
        helper.assertFalse(restored.completeGuardian(2,a),"Wrong floor rejected");
        helper.assertTrue(restored.completeGuardian(1,a),"Guardian victory unlocks exactly one");
        helper.assertFalse(restored.completeGuardian(1,a),"Replay rejected");
        helper.assertTrue(restored.highestUnlocked()==2 && restored.activeGuardian()==null,"Encounter cleared");
        var legacy=new CompoundTag();legacy.putInt("schema",1);legacy.putLong("highest_unlocked",7);legacy.putBoolean("npc_unlocks",true);
        helper.assertTrue(SaoWorldData.load(legacy,helper.getLevel().registryAccess()).highestUnlocked()==7,"Legacy progression preserved");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void capitalTerrain(GameTestHelper helper){
        var access=helper.getLevel().registryAccess();
        for(String name:new String[]{"floor_one_city","upper_floors_city"}){
            var key=ResourceKey.create(Registries.NOISE_SETTINGS,ResourceLocation.fromNamespaceAndPath("sao",name));
            var settings=access.registryOrThrow(Registries.NOISE_SETTINGS).getOrThrow(key);
            var density=RandomState.create(settings,access.lookupOrThrow(Registries.NOISE),42L).router().finalDensity();
            for(int x:new int[]{0,64,-64,128}){
                helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x,95,0))>0,"Solid city foundation");
                helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x,97,0))<0,"Flat city clearance");
                helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(x,280,0))<0,"No overhead roof over city");
            }
            helper.assertTrue(density.compute(new DensityFunction.SinglePointContext(576,280,384))>0,"Castle outside capital preserved");
        }
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void npcPopulationRoundTrip(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.NpcPopulationData();
        helper.assertTrue(data.discover(1,42),"First discovered capital");
        helper.assertFalse(data.discover(1,43),"No regeneration of existing identities");
        helper.assertTrue(data.discoveredCapitals()==1&&data.roster(2).isEmpty(),"No future capital allocation");
        var npc=data.roster(1).getFirst();var id=npc.profile().id();
        var actor=new java.util.UUID(1,4);
        var memory=new dev.anjo7778.sao.simulation.AdventurerMind.Memory(new java.util.UUID(4,5),actor,3,dev.anjo7778.sao.simulation.AdventurerMind.EventType.RESCUED);
        helper.assertTrue(data.remember(1,id,memory),"Memory stored");
        helper.assertFalse(data.remember(1,id,memory),"Duplicate refused");
        helper.assertTrue(data.updateNeeds(1,id,new dev.anjo7778.sao.simulation.AdventurerMind.Needs(10,100,100,100,0)),"Needs updated");
        var registries=helper.getLevel().registryAccess();
        var loaded=dev.anjo7778.sao.world.NpcPopulationData.load(data.save(new CompoundTag(),registries),registries);
        helper.assertTrue(loaded.roster(1).equals(data.roster(1)),"Full roster persisted");
        helper.assertTrue(loaded.roster(1).getFirst().relationship(actor).trust()==75,"Social memory survived");
        helper.assertTrue(loaded.roster(1).getFirst().choose()==dev.anjo7778.sao.simulation.AdventurerMind.Action.REST,"Needs survived");
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void cityNpcEntity(GameTestHelper helper){
        var level=helper.getLevel();
        var state=dev.anjo7778.sao.simulation.NpcRoster.generate(42,1).getFirst();
        var npc=dev.anjo7778.sao.entity.SaoEntities.CITY_NPC.get().create(level);
        helper.assertTrue(npc!=null,"Entity type registered");
        npc.bind(state);
        helper.assertTrue(npc.getUUID().equals(state.profile().id()),"Entity uses profile identity");
        helper.assertTrue(npc.getCustomName().getString().equals(state.profile().name()),"Entity has persistent profile name");
        helper.assertTrue(npc.getMaxHealth()==20,"Attributes registered");
        helper.assertTrue(!npc.getType().canSerialize(),"Projection does not persist separately");
        npc.discard();
        helper.succeed();
    }
    @GameTest(template="empty")
    public static void reciprocalSocialMemory(GameTestHelper helper){
        var data=new dev.anjo7778.sao.world.NpcPopulationData();
        data.discover(1,42);data.discover(2,42);
        var roster=data.roster(1);
        java.util.UUID first=null,second=null;
        dev.anjo7778.sao.simulation.NpcSocial.Encounter encounter=null;
        outer:for(var a:roster)for(var b:roster){
            var e=data.converse(1,a.profile().id(),b.profile().id(),5);
            if(e.isPresent()){first=a.profile().id();second=b.profile().id();encounter=e.get();break outer;}
        }
        helper.assertTrue(encounter!=null,"A social pair accepted");
        helper.assertTrue(data.state(1,first).memories().contains(encounter.first()),"First memory");
        helper.assertTrue(data.state(1,second).memories().contains(encounter.second()),"Second memory");
        helper.assertTrue(data.converse(1,first,second,5).isEmpty(),"Duplicate rejected");
        helper.assertTrue(data.converse(1,first,data.roster(2).getFirst().profile().id(),5).isEmpty(),"Cross-floor rejected");
        var registries=helper.getLevel().registryAccess();
        var restored=dev.anjo7778.sao.world.NpcPopulationData.load(data.save(new CompoundTag(),registries),registries);
        helper.assertTrue(restored.roster(1).equals(data.roster(1)),"Both perspectives persist");
        helper.assertTrue(restored.converse(1,second,first,5).isEmpty(),"Reload prevents replay");
        helper.succeed();
    }
}
