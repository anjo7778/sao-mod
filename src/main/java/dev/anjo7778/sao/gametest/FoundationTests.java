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
}
