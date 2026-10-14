package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Arcane pedestal: holds a single item for infusion (and display). */
public class ArcanePedestalBlockEntity extends SyncedBlockEntity implements DisplaysItem {
    private ItemStack item = ItemStack.EMPTY;

    public ArcanePedestalBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.ARCANE_PEDESTAL.get(), pos, state);
    }

    @Override
    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack.copyWithCount(Math.min(1, stack.getCount()));
        if (stack.isEmpty()) {
            item = ItemStack.EMPTY;
        }
        markForSync();
    }

    /** Drops the held item when the pedestal is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !item.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, item);
            item = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        item = input.read("item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!item.isEmpty()) {
            output.store("item", ItemStack.OPTIONAL_CODEC, item);
        }
    }
}
