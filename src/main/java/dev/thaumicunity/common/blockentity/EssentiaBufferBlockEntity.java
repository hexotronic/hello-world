package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.essentia.EssentiaNode;
import dev.thaumicunity.common.essentia.EssentiaTransfer;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Essentia buffer: a small store for several aspects at once that pulls weakly ({@value #SUCTION}) from every side
 * and gives its essentia to anything pulling harder. Useful between a furnace and many jars.
 */
public class EssentiaBufferBlockEntity extends SyncedBlockEntity implements EssentiaNode {
    public static final int SUCTION = 8;
    public static final int PER_ASPECT = 16;
    public static final int KINDS = 6;
    private static final List<Direction> SIDES = List.of(Direction.values());

    private AspectList contents = AspectList.EMPTY;

    public EssentiaBufferBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.ESSENTIA_BUFFER.get(), pos, state);
    }

    public AspectList contents() {
        return contents;
    }

    @Override
    public boolean connectsTo(Direction side) {
        return true;
    }

    @Override
    public int suction(Direction side) {
        return contents.size() < KINDS || contents.asMap().values().stream().anyMatch(v -> v < PER_ASPECT) ? SUCTION : 0;
    }

    @Override
    public int retention(Direction side) {
        return SUCTION;
    }

    @Override
    public @Nullable Identifier suctionAspect(Direction side) {
        return null;
    }

    @Override
    public @Nullable Identifier offered(Direction side) {
        return contents.isEmpty() ? null : contents.dominant();
    }

    @Override
    public int take(Identifier aspect, int count, Direction side) {
        int taken = Math.min(count, contents.get(aspect));
        if (taken > 0) {
            contents = contents.subtract(AspectList.of(aspect, taken));
            markForSync();
        }
        return taken;
    }

    @Override
    public int give(Identifier aspect, int count, Direction side) {
        int held = contents.get(aspect);
        if (held == 0 && contents.size() >= KINDS) {
            return 0;
        }
        int accepted = Math.min(count, PER_ASPECT - held);
        if (accepted > 0) {
            contents = contents.add(aspect, accepted);
            markForSync();
        }
        return Math.max(0, accepted);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssentiaBufferBlockEntity buffer) {
        if (level.getGameTime() % 2 == 0 && buffer.suction(Direction.UP) > 0) {
            EssentiaTransfer.pullAny(level, pos, buffer, SUCTION, null, SIDES);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        contents = input.read("contents", AspectList.CODEC).orElse(AspectList.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!contents.isEmpty()) {
            output.store("contents", AspectList.CODEC, contents);
        }
    }
}
