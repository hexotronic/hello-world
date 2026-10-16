package dev.thaumicunity.common.essentia;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

/** Moving essentia between neighbouring {@link EssentiaNode}s. */
public final class EssentiaTransfer {
    private EssentiaTransfer() {
    }

    /** The node next to {@code pos} on {@code side} that connects back towards {@code pos}, if any. */
    public static @Nullable EssentiaNode neighbour(Level level, BlockPos pos, Direction side) {
        BlockEntity be = level.getBlockEntity(pos.relative(side));
        return be instanceof EssentiaNode node && node.connectsTo(side.getOpposite()) ? node : null;
    }

    /**
     * Pulls one point of essentia into {@code self} from the neighbour on {@code side}, if {@code suction} beats how
     * strongly the neighbour holds on to it and the neighbour offers an acceptable aspect. {@code want} restricts the
     * aspect (null for any). Returns the aspect moved, or null.
     */
    public static @Nullable Identifier pull(Level level, BlockPos pos, EssentiaNode self, Direction side, int suction,
                                            @Nullable Identifier want) {
        EssentiaNode other = neighbour(level, pos, side);
        if (other == null) {
            return null;
        }
        Direction from = side.getOpposite();
        Identifier offered = other.offered(from);
        if (offered == null || other.retention(from) >= suction || (want != null && !want.equals(offered))) {
            return null;
        }
        if (self.give(offered, 1, side) <= 0) {
            return null;
        }
        if (other.take(offered, 1, from) <= 0) {
            // The neighbour changed its mind; undo.
            self.take(offered, 1, side);
            return null;
        }
        return offered;
    }

    /** Tries every side in turn; returns the aspect moved, or null. */
    public static @Nullable Identifier pullAny(Level level, BlockPos pos, EssentiaNode self, int suction, @Nullable Identifier want,
                                               Iterable<Direction> sides) {
        for (Direction side : sides) {
            Identifier moved = pull(level, pos, self, side, suction, want);
            if (moved != null) {
                return moved;
            }
        }
        return null;
    }
}
