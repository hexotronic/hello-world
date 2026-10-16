package dev.thaumicunity.common.recipe;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

import java.util.List;

/** Input of an infusion: the item on the central pedestal and the items on the surrounding pedestals. */
public record InfusionInput(ItemStack central, List<ItemStack> components) implements RecipeInput {
    public InfusionInput {
        components = List.copyOf(components);
    }

    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? central : components.get(index - 1);
    }

    @Override
    public int size() {
        return 1 + components.size();
    }
}
