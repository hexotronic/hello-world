package dev.thaumicunity.common.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Block entity with a small persistent inventory shared with its menu. */
public abstract class InventoryBlockEntity extends SyncedBlockEntity {
    protected final SimpleContainer inventory;

    protected InventoryBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        this.inventory = new SimpleContainer(size) {
            @Override
            public void setChanged() {
                super.setChanged();
                InventoryBlockEntity.this.setChanged();
            }

            @Override
            public boolean stillValid(Player player) {
                return InventoryBlockEntity.this.stillValid(player);
            }
        };
    }

    public SimpleContainer inventory() {
        return inventory;
    }

    /** Drops the inventory when the block is broken or replaced. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null) {
            Containers.dropContents(level, pos, inventory);
        }
    }

    public boolean stillValid(Player player) {
        return level != null && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5) <= 64;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        inventory.clearContent();
        input.list("items", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC).ifPresent(list -> {
            int i = 0;
            for (var stack : list) {
                if (i < inventory.getContainerSize()) {
                    inventory.setItem(i, stack);
                }
                i++;
            }
        });
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        var list = output.list("items", net.minecraft.world.item.ItemStack.OPTIONAL_CODEC);
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            list.add(inventory.getItem(i));
        }
    }
}
