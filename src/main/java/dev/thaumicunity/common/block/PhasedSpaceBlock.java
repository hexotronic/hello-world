package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.PhasedSpaceBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Empty space left by a portable hole; invisible and walk-through until it closes again. */
public class PhasedSpaceBlock extends TUEntityBlock<PhasedSpaceBlockEntity> {
    public PhasedSpaceBlock(Properties properties) {
        super(properties, TUBlockEntities.PHASED_SPACE, PhasedSpaceBlockEntity::new, PhasedSpaceBlockEntity::serverTick);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state) {
        return true;
    }
}
