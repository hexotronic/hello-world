package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.recipe.ArcaneCraftingRecipe;
import dev.thaumicunity.common.recipe.CrucibleRecipe;
import dev.thaumicunity.common.recipe.InfusionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Recipe types, serializers and the (unused by vanilla books) recipe book category of magic recipes. */
public final class TURecipes {
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ThaumicUnity.MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, ThaumicUnity.MOD_ID);
    private static final DeferredRegister<RecipeBookCategory> CATEGORIES =
            DeferredRegister.create(Registries.RECIPE_BOOK_CATEGORY, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<RecipeType<?>, RecipeType<CrucibleRecipe>> CRUCIBLE = type("crucible");
    public static final DeferredHolder<RecipeType<?>, RecipeType<ArcaneCraftingRecipe>> ARCANE_CRAFTING = type("arcane_crafting");
    public static final DeferredHolder<RecipeType<?>, RecipeType<InfusionRecipe>> INFUSION = type("infusion");

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<CrucibleRecipe>> CRUCIBLE_SERIALIZER =
            SERIALIZERS.register("crucible", () -> new RecipeSerializer<>(CrucibleRecipe.MAP_CODEC, CrucibleRecipe.STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ArcaneCraftingRecipe>> ARCANE_SHAPED_SERIALIZER =
            SERIALIZERS.register("arcane_shaped", () -> new RecipeSerializer<>(ArcaneCraftingRecipe.SHAPED_CODEC,
                    ArcaneCraftingRecipe.SHAPED_STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<ArcaneCraftingRecipe>> ARCANE_SHAPELESS_SERIALIZER =
            SERIALIZERS.register("arcane_shapeless", () -> new RecipeSerializer<>(ArcaneCraftingRecipe.SHAPELESS_CODEC,
                    ArcaneCraftingRecipe.SHAPELESS_STREAM_CODEC));
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<InfusionRecipe>> INFUSION_SERIALIZER =
            SERIALIZERS.register("infusion", () -> new RecipeSerializer<>(InfusionRecipe.MAP_CODEC, InfusionRecipe.STREAM_CODEC));

    public static final DeferredHolder<RecipeBookCategory, RecipeBookCategory> MAGIC_CATEGORY =
            CATEGORIES.register("magic", RecipeBookCategory::new);

    private TURecipes() {
    }

    private static <T extends net.minecraft.world.item.crafting.Recipe<?>> DeferredHolder<RecipeType<?>, RecipeType<T>> type(String name) {
        return TYPES.register(name, () -> new RecipeType<>() {
            @Override
            public String toString() {
                return ThaumicUnity.MOD_ID + ":" + name;
            }
        });
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
        SERIALIZERS.register(modBus);
        CATEGORIES.register(modBus);
    }
}
