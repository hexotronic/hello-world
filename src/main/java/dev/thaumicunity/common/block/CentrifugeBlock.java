package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.CentrifugeBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Essentia centrifuge: right-click with an empty hand to see what it has split. */
public class CentrifugeBlock extends TUEntityBlock<CentrifugeBlockEntity> {
    public CentrifugeBlock(Properties properties) {
        super(properties, TUBlockEntities.CENTRIFUGE, CentrifugeBlockEntity::new, CentrifugeBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        CentrifugeBlockEntity centrifuge = blockEntity(level, pos);
        if (centrifuge == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.sendOverlayMessage(EssentiaBufferBlock.describe(centrifuge.output()));
        }
        return InteractionResult.SUCCESS;
    }
}
