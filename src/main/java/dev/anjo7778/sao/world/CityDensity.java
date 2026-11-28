package dev.anjo7778.sao.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anjo7778.sao.simulation.CityPlan;
import dev.anjo7778.sao.simulation.RegionalSettlements;
import dev.anjo7778.sao.simulation.FloorLayout;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public record CityDensity(boolean upper,boolean inverse,boolean regional) implements DensityFunction.SimpleFunction {
    public static final MapCodec<CityDensity> MAP_CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Codec.BOOL.fieldOf("upper").forGetter(CityDensity::upper),
        Codec.BOOL.fieldOf("inverse").forGetter(CityDensity::inverse),
        Codec.BOOL.optionalFieldOf("regional",false).forGetter(CityDensity::regional)).apply(i,CityDensity::new));
    public CityDensity(boolean upper,boolean inverse){this(upper,inverse,false);}
    private static final KeyDispatchDataCodec<CityDensity> CODEC=KeyDispatchDataCodec.of(MAP_CODEC);
    @Override public double compute(FunctionContext c){
        long floor=upper?FloorLayout.floorAt(c.blockX(),c.blockZ()):1;
        double m=CityPlan.blend(floor,upper?FloorLayout.local(c.blockX()):c.blockX(),upper?FloorLayout.local(c.blockZ()):c.blockZ());
        if(regional)m=Math.max(m,RegionalSettlements.blend(floor,upper?FloorLayout.local(c.blockX()):c.blockX(),upper?FloorLayout.local(c.blockZ()):c.blockZ()));
        return inverse?1-m:m;
    }
    @Override public double minValue(){return 0;}
    @Override public double maxValue(){return 1;}
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec(){return CODEC;}
}
