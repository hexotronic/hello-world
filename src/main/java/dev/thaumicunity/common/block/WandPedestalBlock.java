package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.WandPedestalBlockEntity;
import dev.thaumicunity.common.item.WandItem;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.util.TUUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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

/** Wand recharge pedestal: right-click with a wand to set it down, with an empty hand to take it back. */
public class WandPedestalBlock extends TUEntityBlock<WandPedestalBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.or(box(2, 0, 2, 14, 3, 14), box(4, 3, 4, 12, 11, 12), box(2, 11, 2, 14, 14, 14));

    public WandPedestalBlock(Properties properties) {
        super(properties, TUBlockEntities.WAND_PEDESTAL, WandPedestalBlockEntity::new, WandPedestalBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        WandPedestalBlockEntity pedestal = blockEntity(level, pos);
        if (pedestal == null || !pedestal.item().isEmpty() || !(stack.getItem() instanceof WandItem)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide()) {
            pedestal.setItem(stack.copy());
            stack.shrink(1);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.2f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        WandPedestalBlockEntity pedestal = blockEntity(level, pos);
        if (pedestal == null || pedestal.item().isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            ItemStack wand = pedestal.item().copy();
            pedestal.setItem(ItemStack.EMPTY);
            TUUtil.giveOrDrop(player, wand);
            level.playSound(null, pos, SoundEvents.ITEM_FRAME_REMOVE_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
