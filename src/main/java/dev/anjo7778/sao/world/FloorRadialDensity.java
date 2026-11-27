package dev.anjo7778.sao.world;

import com.mojang.serialization.MapCodec;
import dev.anjo7778.sao.simulation.FloorGeometry;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Immutable, thread-safe density function registered for data-driven world generation. */
public final class FloorRadialDensity implements DensityFunction.SimpleFunction {
    public static final MapCodec<FloorRadialDensity> MAP_CODEC = MapCodec.unit(new FloorRadialDensity());
    private static final KeyDispatchDataCodec<FloorRadialDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    @Override
    public double compute(FunctionContext context) {
        return FloorGeometry.radialDensity(FloorGeometry.FIRST_RADIUS, context.blockX(), context.blockZ());
    }

    @Override public double minValue() { return -4.0; }
    @Override public double maxValue() { return 4.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
