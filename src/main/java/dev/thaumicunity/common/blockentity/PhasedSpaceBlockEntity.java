package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;

/**
 * A block opened by a portable hole: remembers what stood there and puts it back when the time is up (waiting while
 * something is in the way). Saved with the chunk, so a hole open when the world is closed still closes.
 */
public class PhasedSpaceBlockEntity extends BlockEntity {
    private BlockState original = Blocks.AIR.defaultBlockState();
    private int remaining;

    public PhasedSpaceBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.PHASED_SPACE.get(), pos, state);
    }

    public void open(BlockState replaced, int ticks) {
        original = replaced;
        remaining = ticks;
        setChanged();
    }

    public BlockState original() {
        return original;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PhasedSpaceBlockEntity space) {
        if (--space.remaining > 0) {
            return;
        }
        if (!level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(pos)).isEmpty()) {
            space.remaining = 20;
            return;
        }
        level.setBlock(pos, space.original, Block.UPDATE_ALL);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        original = input.read("original", BlockState.CODEC).orElse(Blocks.AIR.defaultBlockState());
        remaining = input.getIntOr("remaining", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("original", BlockState.CODEC, original);
        output.putInt("remaining", remaining);
    }
}
