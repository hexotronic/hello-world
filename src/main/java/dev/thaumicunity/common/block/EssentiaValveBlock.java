package dev.thaumicunity.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** Essentia valve: a tube that can be shut by hand (right-click) or by a redstone signal. */
public class EssentiaValveBlock extends EssentiaTubeBlock {
    public EssentiaValveBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected BlockState withDefaults(BlockState state) {
        return state.setValue(BlockStateProperties.OPEN, true).setValue(BlockStateProperties.POWERED, false);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(BlockStateProperties.OPEN, BlockStateProperties.POWERED);
    }

    @Override
    public boolean isOpen(BlockState state) {
        return state.getValue(BlockStateProperties.OPEN) && !state.getValue(BlockStateProperties.POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(BlockStateProperties.POWERED,
                context.getLevel().hasNeighborSignal(context.getClickedPos()));
        return withConnections(state, context.getLevel(), context.getClickedPos());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide()) {
            BlockState toggled = state.cycle(BlockStateProperties.OPEN);
            level.setBlock(pos, withConnections(toggled, level, pos), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f,
                    toggled.getValue(BlockStateProperties.OPEN) ? 0.9f : 0.6f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor, @Nullable Orientation orientation,
                                   boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighbor, orientation, movedByPiston);
        boolean powered = level.hasNeighborSignal(pos);
        if (!level.isClientSide() && powered != state.getValue(BlockStateProperties.POWERED)) {
            level.setBlock(pos, withConnections(state.setValue(BlockStateProperties.POWERED, powered), level, pos), Block.UPDATE_ALL);
        }
    }
}
