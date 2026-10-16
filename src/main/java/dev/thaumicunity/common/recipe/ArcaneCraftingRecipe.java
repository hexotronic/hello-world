package dev.thaumicunity.common.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.registry.TURecipes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Arcane workbench recipe: a normal 3×3 pattern (shaped) or ingredient list (shapeless) that also costs primal vis,
 * drawn from the aura or a wand. JSON types {@code thaumicunity:arcane_shaped} and
 * {@code thaumicunity:arcane_shapeless}; field {@code vis} lists primal amounts.
 */
public final class ArcaneCraftingRecipe implements Recipe<CraftingInput>, ResearchGated {
    private final Optional<ShapedRecipePattern> pattern;
    private final List<Ingredient> ingredients;
    private final AspectList vis;
    private final ItemStackTemplate result;
    private final Optional<Identifier> research;

    private ArcaneCraftingRecipe(Optional<ShapedRecipePattern> pattern, List<Ingredient> ingredients, AspectList vis,
                                 ItemStackTemplate result, Optional<Identifier> research) {
        this.pattern = pattern;
        this.ingredients = List.copyOf(ingredients);
        this.vis = vis;
        this.result = result;
        this.research = research;
    }

    public static ArcaneCraftingRecipe shaped(ShapedRecipePattern pattern, AspectList vis, ItemStackTemplate result,
                                              Optional<Identifier> research) {
        return new ArcaneCraftingRecipe(Optional.of(pattern), List.of(), vis, result, research);
    }

    public static ArcaneCraftingRecipe shapeless(List<Ingredient> ingredients, AspectList vis, ItemStackTemplate result,
                                                 Optional<Identifier> research) {
        return new ArcaneCraftingRecipe(Optional.empty(), ingredients, vis, result, research);
    }

    public static final MapCodec<ArcaneCraftingRecipe> SHAPED_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ShapedRecipePattern.MAP_CODEC.forGetter(r -> r.pattern.orElseThrow()),
            AspectList.CODEC.optionalFieldOf("vis", AspectList.EMPTY).forGetter(ArcaneCraftingRecipe::vis),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ArcaneCraftingRecipe::result),
            Identifier.CODEC.optionalFieldOf("research").forGetter(ArcaneCraftingRecipe::research)
    ).apply(i, ArcaneCraftingRecipe::shaped));

    public static final MapCodec<ArcaneCraftingRecipe> SHAPELESS_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.listOf(1, 9).fieldOf("ingredients").forGetter(ArcaneCraftingRecipe::ingredients),
            AspectList.CODEC.optionalFieldOf("vis", AspectList.EMPTY).forGetter(ArcaneCraftingRecipe::vis),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(ArcaneCraftingRecipe::result),
            Identifier.CODEC.optionalFieldOf("research").forGetter(ArcaneCraftingRecipe::research)
    ).apply(i, ArcaneCraftingRecipe::shapeless));

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneCraftingRecipe> SHAPED_STREAM_CODEC = StreamCodec.composite(
            ShapedRecipePattern.STREAM_CODEC, r -> r.pattern.orElseThrow(),
            AspectList.STREAM_CODEC, ArcaneCraftingRecipe::vis,
            ItemStackTemplate.STREAM_CODEC, ArcaneCraftingRecipe::result,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), ArcaneCraftingRecipe::research,
            ArcaneCraftingRecipe::shaped);

    public static final StreamCodec<RegistryFriendlyByteBuf, ArcaneCraftingRecipe> SHAPELESS_STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(9)), ArcaneCraftingRecipe::ingredients,
            AspectList.STREAM_CODEC, ArcaneCraftingRecipe::vis,
            ItemStackTemplate.STREAM_CODEC, ArcaneCraftingRecipe::result,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), ArcaneCraftingRecipe::research,
            ArcaneCraftingRecipe::shapeless);

    public boolean isShaped() {
        return pattern.isPresent();
    }

    public Optional<ShapedRecipePattern> pattern() {
        return pattern;
    }

    public List<Ingredient> ingredients() {
        return ingredients;
    }

    /** Primal vis cost. */
    public AspectList vis() {
        return vis;
    }

    public ItemStackTemplate result() {
        return result;
    }

    @Override
    public Optional<Identifier> research() {
        return research;
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (pattern.isPresent()) {
            return pattern.get().matches(input);
        }
        if (input.ingredientCount() != ingredients.size()) {
            return false;
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack stack : input.items()) {
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        // Greedy bipartite matching is enough for 9 slots with distinct-ish ingredients; fall back to backtracking.
        return matchShapeless(stacks, 0, new boolean[ingredients.size()]);
    }

    private boolean matchShapeless(List<ItemStack> stacks, int index, boolean[] used) {
        if (index == stacks.size()) {
            return true;
        }
        for (int k = 0; k < ingredients.size(); k++) {
            if (!used[k] && ingredients.get(k).test(stacks.get(index))) {
                used[k] = true;
                if (matchShapeless(stacks, index + 1, used)) {
                    return true;
                }
                used[k] = false;
            }
        }
        return false;
    }

    @Override
    public ItemStack assemble(CraftingInput input) {
        return result.create();
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<? extends Recipe<CraftingInput>> getSerializer() {
        return pattern.isPresent() ? TURecipes.ARCANE_SHAPED_SERIALIZER.get() : TURecipes.ARCANE_SHAPELESS_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<CraftingInput>> getType() {
        return TURecipes.ARCANE_CRAFTING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.NOT_PLACEABLE;
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return TURecipes.MAGIC_CATEGORY.get();
    }
}
