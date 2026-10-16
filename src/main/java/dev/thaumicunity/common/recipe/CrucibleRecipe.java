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
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Crucible alchemy: throw the catalyst into a boiling crucible holding at least the required essentia.
 * JSON: {@code {"type": "thaumicunity:crucible", "catalyst": <ingredient>, "aspects": {...}, "result": <item>,
 * "research": "<research id>"}}.
 */
public record CrucibleRecipe(Ingredient catalyst, AspectList aspects, ItemStackTemplate result, Optional<Identifier> research)
        implements Recipe<CrucibleInput>, ResearchGated {
    public static final MapCodec<CrucibleRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("catalyst").forGetter(CrucibleRecipe::catalyst),
            AspectList.CODEC.fieldOf("aspects").forGetter(CrucibleRecipe::aspects),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(CrucibleRecipe::result),
            Identifier.CODEC.optionalFieldOf("research").forGetter(CrucibleRecipe::research)
    ).apply(i, CrucibleRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrucibleRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, CrucibleRecipe::catalyst,
            AspectList.STREAM_CODEC, CrucibleRecipe::aspects,
            ItemStackTemplate.STREAM_CODEC, CrucibleRecipe::result,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), CrucibleRecipe::research,
            CrucibleRecipe::new);

    @Override
    public boolean matches(CrucibleInput input, Level level) {
        return catalyst.test(input.catalyst()) && input.essentia().containsAll(aspects);
    }

    @Override
    public ItemStack assemble(CrucibleInput input) {
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
    public RecipeSerializer<? extends Recipe<CrucibleInput>> getSerializer() {
        return TURecipes.CRUCIBLE_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<CrucibleInput>> getType() {
        return TURecipes.CRUCIBLE.get();
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
