package dev.thaumicunity.common.aspect;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;

/**
 * Best-effort extraction of a recipe's result without a real input. Vanilla crafting and cooking recipes return
 * their result regardless of the input passed to {@code assemble}; anything else is skipped.
 */
final class RecipeOutputs {
    private RecipeOutputs() {
    }

    static ItemStack resultOf(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe crafting && !crafting.isSpecial()) {
            return crafting.assemble(CraftingInput.EMPTY);
        }
        if (recipe instanceof AbstractCookingRecipe cooking) {
            return cooking.assemble(new SingleRecipeInput(ItemStack.EMPTY));
        }
        if (recipe instanceof SingleItemRecipe single) {
            return single.assemble(new SingleRecipeInput(ItemStack.EMPTY));
        }
        return ItemStack.EMPTY;
    }
}
