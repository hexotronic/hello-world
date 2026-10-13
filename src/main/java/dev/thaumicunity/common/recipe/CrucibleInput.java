package dev.thaumicunity.common.recipe;

import dev.thaumicunity.common.aspect.AspectList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeInput;

/** Input of a crucible recipe: the catalyst thrown in and the essentia dissolved in the water. */
public record CrucibleInput(ItemStack catalyst, AspectList essentia) implements RecipeInput {
    @Override
    public ItemStack getItem(int index) {
        return index == 0 ? catalyst : ItemStack.EMPTY;
    }

    @Override
    public int size() {
        return 1;
    }
}
