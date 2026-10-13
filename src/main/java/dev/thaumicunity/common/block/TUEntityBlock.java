package dev.thaumicunity.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.function.BiFunction;
import java.util.function.Supplier;

/** Base for Thaumic Unity blocks with a block entity and an optional server ticker. */
public abstract class TUEntityBlock<E extends BlockEntity> extends BaseEntityBlock {
    private final Supplier<BlockEntityType<E>> type;
    private final BiFunction<BlockPos, BlockState, E> factory;
    private final @Nullable BlockEntityTicker<E> serverTicker;

    protected TUEntityBlock(Properties properties, Supplier<BlockEntityType<E>> type, BiFunction<BlockPos, BlockState, E> factory,
                            @Nullable BlockEntityTicker<E> serverTicker) {
        super(properties);
        this.type = type;
        this.factory = factory;
        this.serverTicker = serverTicker;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return factory.apply(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        if (level.isClientSide() || serverTicker == null) {
            return null;
        }
        return createTickerHelper(blockEntityType, type.get(), serverTicker);
    }

    @SuppressWarnings("unchecked")
    protected @Nullable E blockEntity(Level level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        return be != null && be.getType() == type.get() ? (E) be : null;
    }
}
