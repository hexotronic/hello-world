package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.essentia.EssentiaNode;
import dev.thaumicunity.common.essentia.EssentiaTransfer;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Warded jar: holds up to {@link #CAPACITY} essentia of a single aspect. A label ({@code filter}) locks it to one
 * aspect even when empty. Contents travel with the item when the jar is broken.
 */
public class WardedJarBlockEntity extends SyncedBlockEntity implements EssentiaNode {
    public static final int CAPACITY = 250;

    private @Nullable Identifier aspect;
    private int amount;
    private @Nullable Identifier filter;

    public WardedJarBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.WARDED_JAR.get(), pos, state);
    }

    public @Nullable Identifier aspect() {
        return amount > 0 ? aspect : filter;
    }

    public int amount() {
        return amount;
    }

    public @Nullable Identifier filter() {
        return filter;
    }

    public void setFilter(@Nullable Identifier value) {
        filter = value;
        markForSync();
    }

    public boolean canAccept(Identifier incoming) {
        if (filter != null && !filter.equals(incoming)) {
            return false;
        }
        return amount == 0 || incoming.equals(aspect);
    }

    /** Adds essentia; returns how much was accepted. */
    public int add(Identifier incoming, int count) {
        if (count <= 0 || !canAccept(incoming)) {
            return 0;
        }
        int accepted = Math.min(count, CAPACITY - amount);
        if (accepted > 0) {
            aspect = incoming;
            amount += accepted;
            markForSync();
        }
        return accepted;
    }

    /** Removes essentia of the given aspect; returns how much was taken. */
    public int take(Identifier wanted, int count) {
        if (amount == 0 || !wanted.equals(aspect) || count <= 0) {
            return 0;
        }
        int taken = Math.min(count, amount);
        amount -= taken;
        if (amount == 0) {
            aspect = null;
        }
        markForSync();
        return taken;
    }

    // --- essentia transport: tubes connect to the lid only

    @Override
    public boolean connectsTo(Direction side) {
        return side == Direction.UP;
    }

    @Override
    public int suction(Direction side) {
        return side == Direction.UP && amount < CAPACITY ? retention(side) : 0;
    }

    @Override
    public int retention(Direction side) {
        return filter != null ? LABELLED_JAR : JAR;
    }

    @Override
    public @Nullable Identifier suctionAspect(Direction side) {
        return filter != null ? filter : amount > 0 ? aspect : null;
    }

    @Override
    public @Nullable Identifier offered(Direction side) {
        return side == Direction.UP && amount > 0 ? aspect : null;
    }

    @Override
    public int take(Identifier wanted, int count, Direction side) {
        return take(wanted, count);
    }

    @Override
    public int give(Identifier incoming, int count, Direction side) {
        return add(incoming, count);
    }

    /** Pulls essentia from a tube on the lid. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, WardedJarBlockEntity jar) {
        int suction = jar.suction(Direction.UP);
        if (suction > 0) {
            EssentiaTransfer.pull(level, pos, jar, Direction.UP, suction, jar.suctionAspect(Direction.UP));
        }
    }

    public AspectList contents() {
        return amount > 0 && aspect != null ? AspectList.of(aspect, amount) : AspectList.EMPTY;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        aspect = input.getString("aspect").map(Identifier::tryParse).orElse(null);
        amount = input.getIntOr("amount", 0);
        filter = input.getString("filter").map(Identifier::tryParse).orElse(null);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (aspect != null && amount > 0) {
            output.putString("aspect", aspect.toString());
            output.putInt("amount", amount);
        }
        if (filter != null) {
            output.putString("filter", filter.toString());
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        AspectList stored = components.get(TUDataComponents.ESSENTIA.get());
        if (stored != null && !stored.isEmpty()) {
            aspect = stored.dominant();
            amount = Math.min(CAPACITY, stored.get(aspect));
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        if (amount > 0 && aspect != null) {
            builder.set(TUDataComponents.ESSENTIA.get(), contents());
        }
    }
}
