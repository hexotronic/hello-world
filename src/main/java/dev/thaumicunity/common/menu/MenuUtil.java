package dev.thaumicunity.common.menu;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Shared slot layout helpers. */
final class MenuUtil {
    private MenuUtil() {
    }

    /** Standard player inventory (27 + 9 hotbar) with its top-left corner at (x, y). */
    static void addPlayerInventory(java.util.function.Function<Slot, Slot> add, Inventory inventory, int x, int y) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                add.apply(new Slot(inventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            add.apply(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    /**
     * Shift-click helper: moves between the machine slots [0, machineSlots) and the player inventory that follows.
     */
    static ItemStack quickMove(AbstractContainerMenu menu, int index, int machineSlots, Mover mover) {
        Slot slot = menu.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int total = menu.slots.size();
        boolean moved = index < machineSlots
                ? mover.move(stack, machineSlots, total, true)
                : mover.move(stack, 0, machineSlots, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @FunctionalInterface
    interface Mover {
        boolean move(ItemStack stack, int from, int to, boolean reverse);
    }
}
