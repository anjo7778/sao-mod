package dev.anjo7778.sao.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.anjo7778.sao.simulation.FloorLayout;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** No mutable state; safe for concurrent chunk generation. */
public record FloorRegionDensity(int part) implements DensityFunction.SimpleFunction {
    public static final MapCodec<FloorRegionDensity> MAP_CODEC =
        Codec.intRange(0, 1).fieldOf("part").xmap(FloorRegionDensity::new, FloorRegionDensity::part);
    private static final KeyDispatchDataCodec<FloorRegionDensity> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);

    public FloorRegionDensity {
        if (part < 0 || part > 1) throw new IllegalArgumentException("Unknown floor region density part");
    }

    @Override public double compute(FunctionContext ctx) {
        return part == 0 ? FloorLayout.radialDensity(ctx.blockX(), ctx.blockZ())
            : FloorLayout.heightDensity(ctx.blockX(), ctx.blockY(), ctx.blockZ());
    }
    @Override public double minValue() { return -4.0; }
    @Override public double maxValue() { return 4.0; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
