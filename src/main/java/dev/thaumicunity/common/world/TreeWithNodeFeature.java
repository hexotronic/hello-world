package dev.thaumicunity.common.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.common.aura.node.NodeType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

/**
 * Places a tree and, with {@code node_chance}, an aura node of {@code node_type} among its leaves just below the top
 * of the trunk (wild silverwood trees hide pure nodes, as in Thaumcraft 4).
 */
public record TreeWithNodeFeature(Holder<Feature> tree, float nodeChance, NodeType nodeType) implements Feature {
    public static final MapCodec<TreeWithNodeFeature> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Feature.CODEC.fieldOf("tree").forGetter(TreeWithNodeFeature::tree),
            Codec.floatRange(0, 1).fieldOf("node_chance").forGetter(TreeWithNodeFeature::nodeChance),
            NodeType.CODEC.fieldOf("node_type").forGetter(TreeWithNodeFeature::nodeType)
    ).apply(i, TreeWithNodeFeature::new));

    @Override
    public MapCodec<? extends Feature> codec() {
        return CODEC;
    }

    @Override
    public boolean place(WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin) {
        if (!tree.value().place(level, generator, random, origin)) {
            return false;
        }
        if (random.nextFloat() < nodeChance && AuraNodes.nodesGenerate()) {
            BlockPos top = origin;
            while (level.getBlockState(top.above()).is(BlockTags.LOGS) && top.getY() < origin.getY() + 32) {
                top = top.above();
            }
            BlockPos below = top.below();
            for (Direction side : Direction.Plane.HORIZONTAL.shuffledCopy(random)) {
                BlockPos pos = below.relative(side);
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(BlockTags.LEAVES)) {
                    AuraNodes.place(level, pos, nodeType, random);
                    break;
                }
            }
        }
        return true;
    }
}
