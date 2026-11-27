package dev.anjo7778.sao.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.anjo7778.sao.simulation.FloorBiomes;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import java.util.List;
import java.util.Optional;
import dev.anjo7778.sao.simulation.CityPlan;
import dev.anjo7778.sao.simulation.FloorLayout;
import java.util.stream.Stream;

/** A finite, serialized palette; coordinates select floor regions without allocating dimensions. */
public final class FloorBiomeSource extends BiomeSource {
    public static final MapCodec<FloorBiomeSource> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Codec.BOOL.fieldOf("upper").forGetter(s->s.upper),
        Biome.CODEC.listOf().fieldOf("palette").forGetter(s->s.palette),
        Biome.CODEC.optionalFieldOf("capital").forGetter(s->s.capital)
    ).apply(i,FloorBiomeSource::new));
    private final boolean upper;
    private final List<Holder<Biome>> palette;
    private final Optional<Holder<Biome>> capital;
    public FloorBiomeSource(boolean upper,List<Holder<Biome>> palette,Optional<Holder<Biome>> capital){
        if(palette.size()!=FloorBiomes.Kind.values().length) throw new IllegalArgumentException("Invalid SAO biome palette size");
        this.upper=upper;this.palette=List.copyOf(palette);this.capital=capital;
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){return Stream.concat(palette.stream(),capital.stream());}
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        int bx=x*4,bz=z*4;
        if(capital.isPresent()&&CityPlan.inside(upper?FloorLayout.floorAt(bx,bz):1,upper?FloorLayout.local(bx):bx,upper?FloorLayout.local(bz):bz))return capital.get();
        return palette.get(FloorBiomes.at(upper,bx,bz).ordinal());
    }
}
