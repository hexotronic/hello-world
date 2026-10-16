package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.block.EssentiaTubeBlock;
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
 * Essentia tube (and valve): holds one point of essentia at a time. Each tick it takes the strongest suction of its
 * neighbours minus one, and while empty pulls a point from any neighbour that holds on to it less strongly
 * ({@link EssentiaNode}).
 */
public class EssentiaTubeBlockEntity extends SyncedBlockEntity implements EssentiaNode {
    private static final List<Direction> SIDES = List.of(Direction.values());

    private @Nullable Identifier aspect;
    private int amount;
    private int suction;
    private @Nullable Identifier suctionAspect;

    public EssentiaTubeBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.ESSENTIA_TUBE.get(), pos, state);
    }

    public @Nullable Identifier aspect() {
        return amount > 0 ? aspect : null;
    }

    public int amount() {
        return amount;
    }

    public int currentSuction() {
        return suction;
    }

    public @Nullable Identifier currentSuctionAspect() {
        return suctionAspect;
    }

    private boolean open() {
        return getBlockState().getBlock() instanceof EssentiaTubeBlock tube && tube.isOpen(getBlockState());
    }

    @Override
    public boolean connectsTo(Direction side) {
        return open();
    }

    @Override
    public int suction(Direction side) {
        return open() ? suction : 0;
    }

    @Override
    public @Nullable Identifier suctionAspect(Direction side) {
        return suctionAspect;
    }

    @Override
    public @Nullable Identifier offered(Direction side) {
        return open() ? aspect() : null;
    }

    @Override
    public int take(Identifier wanted, int count, Direction side) {
        if (amount <= 0 || count <= 0 || !wanted.equals(aspect)) {
            return 0;
        }
        amount = 0;
        aspect = null;
        setChanged();
        return 1;
    }

    @Override
    public int give(Identifier incoming, int count, Direction side) {
        if (amount > 0 || count <= 0) {
            return 0;
        }
        aspect = incoming;
        amount = 1;
        setChanged();
        return 1;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EssentiaTubeBlockEntity tube) {
        if (!tube.open()) {
            tube.suction = 0;
            tube.suctionAspect = null;
            return;
        }
        int best = 0;
        Identifier bestAspect = null;
        for (Direction side : SIDES) {
            EssentiaNode other = EssentiaTransfer.neighbour(level, pos, side);
            if (other != null) {
                int s = other.suction(side.getOpposite());
                if (s > best) {
                    best = s;
                    bestAspect = other.suctionAspect(side.getOpposite());
                }
            }
        }
        tube.suction = Math.max(0, best - 1);
        tube.suctionAspect = bestAspect;
        if (tube.amount == 0 && tube.suction > 0) {
            EssentiaTransfer.pullAny(level, pos, tube, tube.suction, tube.suctionAspect, SIDES);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        aspect = input.getString("aspect").map(Identifier::tryParse).orElse(null);
        amount = aspect == null ? 0 : input.getIntOr("amount", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (aspect != null && amount > 0) {
            output.putString("aspect", aspect.toString());
            output.putInt("amount", amount);
        }
    }
}
