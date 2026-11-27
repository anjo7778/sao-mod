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
import java.util.stream.Stream;

/** A finite, serialized palette; coordinates select floor regions without allocating dimensions. */
public final class FloorBiomeSource extends BiomeSource {
    public static final MapCodec<FloorBiomeSource> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Codec.BOOL.fieldOf("upper").forGetter(s->s.upper),
        Biome.CODEC.listOf().fieldOf("palette").forGetter(s->s.palette)
    ).apply(i,FloorBiomeSource::new));
    private final boolean upper;
    private final List<Holder<Biome>> palette;
    public FloorBiomeSource(boolean upper,List<Holder<Biome>> palette){
        if(palette.size()!=FloorBiomes.Kind.values().length) throw new IllegalArgumentException("Invalid SAO biome palette size");
        this.upper=upper;this.palette=List.copyOf(palette);
    }
    @Override protected MapCodec<? extends BiomeSource> codec(){return CODEC;}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){return palette.stream();}
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        return palette.get(FloorBiomes.at(upper,x*4,z*4).ordinal());
    }
}
