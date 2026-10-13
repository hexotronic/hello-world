package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.ArcanePedestalBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Arcane pedestal: right-click with an item to place it, with an empty hand to take it back. */
public class ArcanePedestalBlock extends TUEntityBlock<ArcanePedestalBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 3, 14), box(4, 3, 4, 12, 11, 12), box(2, 11, 2, 14, 14, 14));

    public ArcanePedestalBlock(Properties properties) {
        super(properties, TUBlockEntities.ARCANE_PEDESTAL, ArcanePedestalBlockEntity::new, null);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        ArcanePedestalBlockEntity pedestal = blockEntity(level, pos);
        if (pedestal == null || !pedestal.item().isEmpty() || stack.isEmpty()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            pedestal.setItem(stack.copyWithCount(1));
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        ArcanePedestalBlockEntity pedestal = blockEntity(level, pos);
        if (pedestal == null || pedestal.item().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack item = pedestal.item().copy();
            pedestal.setItem(ItemStack.EMPTY);
            if (!player.getInventory().add(item)) {
                player.drop(item, false);
            }
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        ArcanePedestalBlockEntity pedestal = blockEntity(level, pos);
        if (pedestal != null && !pedestal.item().isEmpty() && !level.isClientSide()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, pedestal.item());
            pedestal.setItem(ItemStack.EMPTY);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }
}
