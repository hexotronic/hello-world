package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.essentia.EssentiaNode;
import dev.thaumicunity.common.essentia.EssentiaTransfer;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.spell.SpellFx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Essentia centrifuge: pulls essentia in from above ({@value #SUCTION}, enough to draw from jars) and spins each
 * compound point apart into one of its two components, taking them in turn. Primal essentia passes through
 * unchanged. The results are offered on every other side to anything pulling at all.
 */
public class CentrifugeBlockEntity extends SyncedBlockEntity implements EssentiaNode {
    public static final int SUCTION = 48;
    public static final int OUTPUT_CAPACITY = 16;
    public static final int SPIN_TICKS = 20;

    private @Nullable Identifier input;
    private AspectList output = AspectList.EMPTY;
    private int progress;
    /** Which component the next compound point becomes (alternates). */
    private boolean second;

    public CentrifugeBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.CENTRIFUGE.get(), pos, state);
    }

    public AspectList output() {
        return output;
    }

    public @Nullable Identifier input() {
        return input;
    }

    @Override
    public boolean connectsTo(Direction side) {
        return true;
    }

    @Override
    public int suction(Direction side) {
        return side == Direction.UP && input == null && output.total() < OUTPUT_CAPACITY ? SUCTION : 0;
    }

    @Override
    public int retention(Direction side) {
        return side == Direction.UP ? SUCTION : 0;
    }

    @Override
    public @Nullable Identifier suctionAspect(Direction side) {
        return null;
    }

    @Override
    public @Nullable Identifier offered(Direction side) {
        return side == Direction.UP || output.isEmpty() ? null : output.dominant();
    }

    @Override
    public int take(Identifier aspect, int count, Direction side) {
        if (side == Direction.UP) {
            return 0;
        }
        int taken = Math.min(count, output.get(aspect));
        if (taken > 0) {
            output = output.subtract(AspectList.of(aspect, taken));
            setChanged();
        }
        return taken;
    }

    @Override
    public int give(Identifier aspect, int count, Direction side) {
        if (side != Direction.UP || input != null || count <= 0) {
            return 0;
        }
        input = aspect;
        setChanged();
        return 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CentrifugeBlockEntity centrifuge) {
        if (centrifuge.input == null) {
            if (centrifuge.output.total() < OUTPUT_CAPACITY) {
                EssentiaTransfer.pull(level, pos, centrifuge, Direction.UP, SUCTION, null);
            }
            return;
        }
        if (++centrifuge.progress < SPIN_TICKS) {
            return;
        }
        centrifuge.progress = 0;
        Identifier result = centrifuge.input;
        Optional<Aspect> aspect = Aspects.get(level.registryAccess(), result);
        List<Identifier> parts = aspect.map(Aspect::components).orElse(List.of());
        if (parts.size() == 2) {
            result = parts.get(centrifuge.second ? 1 : 0);
            centrifuge.second = !centrifuge.second;
        }
        centrifuge.output = centrifuge.output.add(result, 1);
        centrifuge.input = null;
        centrifuge.setChanged();
        if (level instanceof ServerLevel server) {
            server.sendParticles(SpellFx.spark(Aspects.color(level.registryAccess(), result)), pos.getX() + 0.5, pos.getY() + 1.0,
                    pos.getZ() + 0.5, 3, 0.2, 0.05, 0.2, 0.02);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.input = input.getString("input").map(Identifier::tryParse).orElse(null);
        output = input.read("output", AspectList.CODEC).orElse(AspectList.EMPTY);
        progress = input.getIntOr("progress", 0);
        second = input.getBooleanOr("second", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (input != null) {
            output.putString("input", input.toString());
        }
        if (!this.output.isEmpty()) {
            output.store("output", AspectList.CODEC, this.output);
        }
        output.putInt("progress", progress);
        output.putBoolean("second", second);
    }
}
