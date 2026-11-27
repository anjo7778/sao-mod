package dev.anjo7778.sao.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.anjo7778.sao.simulation.CastleGeometry;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record CastleDensity(boolean upper) implements DensityFunction.SimpleFunction {
    public static final MapCodec<CastleDensity> MAP_CODEC =
        Codec.BOOL.fieldOf("upper").xmap(CastleDensity::new,CastleDensity::upper);
    private static final KeyDispatchDataCodec<CastleDensity> CODEC=KeyDispatchDataCodec.of(MAP_CODEC);
    @Override public double compute(FunctionContext ctx) {
        return CastleGeometry.density(upper,ctx.blockX(),ctx.blockY(),ctx.blockZ());
    }
    @Override public double minValue(){return -4.0;}
    @Override public double maxValue(){return 4.0;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return CODEC;}
}
