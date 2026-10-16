package dev.thaumicunity.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.grower.TreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;

import java.util.Optional;

/**
 * Sapling that grows into a tree feature from the data (worldgen/feature), the same one that generates in the world,
 * so a pack can reshape greatwood and silverwood trees without code.
 */
public class MagicSaplingBlock extends SaplingBlock {
    /** Block update flags vanilla saplings use while growing: no neighbour updates, no re-render. */
    private static final int QUIET = 4;

    private final ResourceKey<Feature> tree;

    public MagicSaplingBlock(ResourceKey<Feature> tree, Properties properties) {
        // The vanilla grower is never used: advanceTree below grows our own feature.
        super(TreeGrower.OAK, properties);
        this.tree = tree;
    }

    public ResourceKey<Feature> tree() {
        return tree;
    }

    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), QUIET);
        } else {
            growTree(level, pos, state, random);
        }
    }

    /** Replaces the sapling with its tree; puts the sapling back and returns false if the tree does not fit. */
    public boolean growTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        Optional<? extends Holder<Feature>> feature = level.registryAccess().lookupOrThrow(Registries.FEATURE).get(tree);
        if (feature.isEmpty()) {
            return false;
        }
        level.setBlock(pos, level.getFluidState(pos).createLegacyBlock(), QUIET);
        if (feature.get().value().place(level, level.getChunkSource().getGenerator(), random, pos)) {
            return true;
        }
        level.setBlock(pos, state, QUIET);
        return false;
    }
}
