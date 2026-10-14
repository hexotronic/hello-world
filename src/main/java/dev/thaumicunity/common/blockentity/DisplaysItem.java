package dev.thaumicunity.common.blockentity;

import net.minecraft.world.item.ItemStack;

/** A block entity showing one item above itself (pedestals); drawn by the client's pedestal renderer. */
public interface DisplaysItem {
    ItemStack item();
}
