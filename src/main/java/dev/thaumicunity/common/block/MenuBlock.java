package dev.thaumicunity.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Block entity block that opens a menu when used; the menu receives the block position as extra data. */
public abstract class MenuBlock<E extends BlockEntity> extends TUEntityBlock<E> {
    protected MenuBlock(Properties properties, Supplier<BlockEntityType<E>> type, BiFunction<BlockPos, BlockState, E> factory,
                        @Nullable BlockEntityTicker<E> ticker) {
        super(properties, type, factory, ticker);
    }

    /** Creates the server-side menu. */
    protected abstract AbstractContainerMenu createMenu(int id, Player player, E blockEntity);

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        E be = blockEntity(level, pos);
        if (be == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            MenuProvider provider = new SimpleMenuProvider((id, inventory, p) -> createMenu(id, p, be), getName());
            player.openMenu(provider, pos);
        }
        return InteractionResult.SUCCESS;
    }
}
