package dev.thaumicunity.client;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;
import org.jspecify.annotations.Nullable;

/**
 * Recipes the server sent for the Thaumonomicon (crucible, arcane and infusion types, see
 * {@code TUEvents#onDatapackSync}). Replaced on every datapack sync and cleared on logout.
 */
public final class ClientRecipes {
    private static RecipeMap recipes = RecipeMap.EMPTY;

    private ClientRecipes() {
    }

    public static void set(RecipeMap map) {
        recipes = map;
    }

    public static void clear() {
        recipes = RecipeMap.EMPTY;
    }

    public static @Nullable RecipeHolder<?> get(Identifier id) {
        return recipes.byKey(ResourceKey.create(Registries.RECIPE, id));
    }
}
