package dev.thaumicunity.common.util;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Small helpers shared by blocks, menus and items. */
public final class TUUtil {
    private TUUtil() {
    }

    /** Puts the stack in the player's inventory, or drops it at the player's feet if there is no room. */
    public static void giveOrDrop(Player player, ItemStack stack) {
        if (stack.isEmpty() || player.getInventory().add(stack)) {
            return;
        }
        ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY() + 0.5, player.getZ(), stack);
        entity.setNoPickUpDelay();
        player.level().addFreshEntity(entity);
    }
}
