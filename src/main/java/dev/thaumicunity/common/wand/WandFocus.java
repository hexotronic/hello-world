package dev.thaumicunity.common.wand;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * Data component {@code thaumicunity:wand_focus}: the focus fixed to a wand. Holds its own copy of the stack, and
 * compares by item and components so equal wands stay equal.
 */
public record WandFocus(ItemStack stack) {
    public static final Codec<WandFocus> CODEC = ItemStack.CODEC.xmap(WandFocus::new, WandFocus::stack);
    public static final StreamCodec<RegistryFriendlyByteBuf, WandFocus> STREAM_CODEC = ItemStack.STREAM_CODEC
            .map(WandFocus::new, WandFocus::stack);

    public WandFocus {
        stack = stack.copy();
    }

    /** A copy of the focus. */
    @Override
    public ItemStack stack() {
        return stack.copy();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WandFocus focus && ItemStack.matches(stack, focus.stack);
    }

    @Override
    public int hashCode() {
        return ItemStack.hashItemAndComponents(stack) * 31 + stack.getCount();
    }
}
