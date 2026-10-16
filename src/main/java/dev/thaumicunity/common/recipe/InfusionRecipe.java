package dev.thaumicunity.common.recipe;

import com.mojang.serialization.Codec;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Infusion at the runic matrix. {@code keep_components: false} (default) consumes the surrounding items.
 * {@code instability} (0–10) feeds the stability model (docs/DESIGN_DECISIONS.md, D9).
 */
public record InfusionRecipe(Ingredient central, List<Ingredient> components, AspectList essentia, int instability,
                             ItemStackTemplate result, Optional<Identifier> research) implements Recipe<InfusionInput>, ResearchGated {
    public static final MapCodec<InfusionRecipe> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Ingredient.CODEC.fieldOf("central").forGetter(InfusionRecipe::central),
            Ingredient.CODEC.listOf(0, 16).fieldOf("components").forGetter(InfusionRecipe::components),
            AspectList.CODEC.fieldOf("essentia").forGetter(InfusionRecipe::essentia),
            Codec.intRange(0, 10).optionalFieldOf("instability", 1).forGetter(InfusionRecipe::instability),
            ItemStackTemplate.CODEC.fieldOf("result").forGetter(InfusionRecipe::result),
            Identifier.CODEC.optionalFieldOf("research").forGetter(InfusionRecipe::research)
    ).apply(i, InfusionRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionRecipe> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, InfusionRecipe::central,
            Ingredient.CONTENTS_STREAM_CODEC.apply(ByteBufCodecs.list(16)), InfusionRecipe::components,
            AspectList.STREAM_CODEC, InfusionRecipe::essentia,
            ByteBufCodecs.VAR_INT, InfusionRecipe::instability,
            ItemStackTemplate.STREAM_CODEC, InfusionRecipe::result,
            ByteBufCodecs.optional(Identifier.STREAM_CODEC), InfusionRecipe::research,
            InfusionRecipe::new);

    @Override
    public boolean matches(InfusionInput input, Level level) {
        if (!central.test(input.central()) || input.components().size() != components.size()) {
            return false;
        }
        List<ItemStack> remaining = new ArrayList<>(input.components());
        for (Ingredient ingredient : components) {
            int found = -1;
            for (int k = 0; k < remaining.size(); k++) {
                if (ingredient.test(remaining.get(k))) {
                    found = k;
                    break;
                }
            }
            if (found < 0) {
                return false;
            }
            remaining.remove(found);
        }
        return true;
    }

    @Override
    public ItemStack assemble(InfusionInput input) {
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
    public RecipeSerializer<? extends Recipe<InfusionInput>> getSerializer() {
        return TURecipes.INFUSION_SERIALIZER.get();
    }

    @Override
    public RecipeType<? extends Recipe<InfusionInput>> getType() {
        return TURecipes.INFUSION.get();
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
