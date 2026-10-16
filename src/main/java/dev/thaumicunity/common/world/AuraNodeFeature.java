package dev.thaumicunity.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.common.aura.node.NodeType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * An aura node hanging in the air {@code min_height} to {@code max_height} blocks above the placement origin (the
 * surface), of a type that suits the biome. Places nothing in the "chunk" aura mode (docs/DESIGN_DECISIONS.md, D2).
 */
public record AuraNodeFeature(int minHeight, int maxHeight) implements Feature {
    public static final MapCodec<AuraNodeFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(0, 64).fieldOf("min_height").forGetter(AuraNodeFeature::minHeight),
            Codec.intRange(0, 64).fieldOf("max_height").forGetter(AuraNodeFeature::maxHeight)
    ).apply(i, AuraNodeFeature::new));

    @Override
    public MapCodec<? extends Feature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!AuraNodes.nodesGenerate()) {
            return false;
        }
        BlockPos pos = origin.above(minHeight + random.nextInt(Math.max(0, maxHeight - minHeight) + 1));
        if (!level.getBlockState(pos).isAir() || !level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        return AuraNodes.place(level, pos, NodeType.forBiome(level.getBiome(pos), random), random);
    }
}
