package dev.thaumicunity.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shimmerleaf, cinderpearl and vishroom: flowers that grow on whatever ground their block tag names
 * ({@code thaumicunity:<plant>_soil}), so cinderpearl can root in desert sand and vishroom on stone.
 */
public class MagicPlantBlock extends FlowerBlock {
    private final TagKey<Block> soil;

    public MagicPlantBlock(Holder<MobEffect> stewEffect, float stewSeconds, TagKey<Block> soil, Properties properties) {
        super(stewEffect, stewSeconds, properties);
        this.soil = soil;
    }

    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return state.is(soil);
    }
}
