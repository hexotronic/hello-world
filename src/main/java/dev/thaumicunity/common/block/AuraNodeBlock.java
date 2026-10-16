package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.AuraNodeBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Aura node (see {@link AuraNodeBlockEntity}). Nothing collides with it, but it has a small outline in its middle so
 * that thaumometers can scan it and wands can draw from it. Its look is drawn entirely by its renderer.
 */
public class AuraNodeBlock extends TUEntityBlock<AuraNodeBlockEntity> {
    private static final VoxelShape SHAPE = box(4, 4, 4, 12, 12, 12);

    public AuraNodeBlock(Properties properties) {
        super(properties, TUBlockEntities.AURA_NODE, AuraNodeBlockEntity::new, AuraNodeBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
