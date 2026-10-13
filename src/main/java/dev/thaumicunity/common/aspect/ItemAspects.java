package dev.thaumicunity.common.aspect;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.core.aspect.AspectAmounts;
import dev.thaumicunity.core.derive.AspectInheritance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Final aspect table of every item (docs/DESIGN_DECISIONS.md, D13): explicit data map values, then tag rules, then
 * recipe inheritance, then a class-based fallback. Computed on the server after every data reload and sent to
 * clients; each side keeps its own copy.
 */
public final class ItemAspects {
    private static volatile Map<Identifier, AspectList> server = Map.of();
    private static volatile Map<Identifier, AspectList> client = Map.of();

    private ItemAspects() {
    }

    /** Aspects of a stack's item (stack size is ignored). */
    public static AspectList of(ItemStack stack, boolean clientSide) {
        if (stack.isEmpty()) {
            return AspectList.EMPTY;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return (clientSide ? client : server).getOrDefault(id, AspectList.EMPTY);
    }

    public static AspectList of(Item item, boolean clientSide) {
        return (clientSide ? client : server).getOrDefault(BuiltInRegistries.ITEM.getKey(item), AspectList.EMPTY);
    }

    /** Aspects revealed by scanning a living thing of this type. */
    public static AspectList ofEntity(EntityType<?> type) {
        AspectList explicit = type.builtInRegistryHolder().getData(TUDataMaps.ENTITY_ASPECTS);
        return explicit != null ? explicit : AspectList.EMPTY;
    }

    public static Map<Identifier, AspectList> serverTable() {
        return server;
    }

    public static void setClientTable(Map<Identifier, AspectList> table) {
        client = Map.copyOf(table);
    }

    /** Recomputes the server table; called after tags and recipes are (re)loaded. */
    public static void recompute(MinecraftServer minecraftServer) {
        long start = System.nanoTime();
        Map<String, AspectAmounts> explicit = new HashMap<>();
        Map<Identifier, AspectList> result = new HashMap<>();

        // 1. explicit data map values
        for (Holder<Item> holder : BuiltInRegistries.ITEM.asHolderIdMap()) {
            AspectList value = holder.getData(TUDataMaps.ITEM_ASPECTS);
            if (value != null && !value.isEmpty()) {
                Identifier id = holder.unwrapKey().orElseThrow().identifier();
                explicit.put(id.toString(), value.toCore());
                result.put(id, value);
            }
        }

        // 2. tag rules
        List<AspectRule> rules = new ArrayList<>(AspectRuleLoader.rules());
        rules.sort((a, b) -> Integer.compare(b.priority(), a.priority()));
        for (Holder<Item> holder : BuiltInRegistries.ITEM.asHolderIdMap()) {
            Identifier id = holder.unwrapKey().orElseThrow().identifier();
            if (explicit.containsKey(id.toString())) {
                continue;
            }
            AspectList combined = null;
            AspectList additive = AspectList.EMPTY;
            for (AspectRule rule : rules) {
                if (!holder.is(rule.tag())) {
                    continue;
                }
                if (rule.additive()) {
                    additive = additive.add(rule.aspects());
                } else if (combined == null) {
                    combined = rule.aspects();
                }
            }
            AspectList value = combined == null ? additive : combined.add(additive);
            if (!value.isEmpty()) {
                explicit.put(id.toString(), value.toCore());
                result.put(id, value);
            }
        }

        // 3. recipe inheritance
        if (TUConfig.DERIVE_ASPECTS_FROM_RECIPES.get()) {
            List<AspectInheritance.RecipeView> views = new ArrayList<>();
            for (RecipeHolder<?> holder : minecraftServer.getRecipeManager().getRecipes()) {
                toView(holder).ifPresent(views::add);
            }
            AspectInheritance inheritance = new AspectInheritance(explicit, views, TUConfig.DERIVATION_LOSS_FACTOR.get(),
                    TUConfig.DERIVATION_CAP.get());
            inheritance.resolveAll().forEach((item, aspects) -> {
                Identifier id = Identifier.tryParse(item);
                if (id != null && !result.containsKey(id)) {
                    result.put(id, AspectList.fromCore(aspects));
                }
            });
        }

        // 4. fallback by kind of item
        for (Holder<Item> holder : BuiltInRegistries.ITEM.asHolderIdMap()) {
            Identifier id = holder.unwrapKey().orElseThrow().identifier();
            if (!result.containsKey(id)) {
                AspectList fallback = fallback(holder.value());
                if (!fallback.isEmpty()) {
                    result.put(id, fallback);
                }
            }
        }
        server = Collections.unmodifiableMap(result);
        ThaumicUnity.LOGGER.info("Assigned aspects to {} items in {} ms", result.size(), (System.nanoTime() - start) / 1_000_000);
    }

    private static Optional<AspectInheritance.RecipeView> toView(RecipeHolder<?> holder) {
        Recipe<?> recipe = holder.value();
        try {
            ItemStack output = RecipeOutputs.resultOf(recipe);
            if (output.isEmpty()) {
                return Optional.empty();
            }
            List<List<String>> ingredients = new ArrayList<>();
            for (Ingredient ingredient : recipe.placementInfo().ingredients()) {
                List<String> options = ingredient.items().limit(16)
                        .map(h -> h.unwrapKey().map(k -> k.identifier().toString()).orElse(""))
                        .filter(s -> !s.isEmpty()).toList();
                if (!options.isEmpty()) {
                    ingredients.add(options);
                }
            }
            if (ingredients.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new AspectInheritance.RecipeView(holder.id().identifier().toString(),
                    BuiltInRegistries.ITEM.getKey(output.getItem()).toString(), output.getCount(), ingredients));
        } catch (RuntimeException e) {
            // Some modded recipes cannot produce a result without a real input; they are simply skipped.
            return Optional.empty();
        }
    }

    private static AspectList fallback(Item item) {
        Map<Identifier, Integer> values = new HashMap<>();
        var components = item.components();
        if (components.has(DataComponents.FOOD)) {
            values.merge(ThaumicUnity.id("victus"), 1, Integer::sum);
            values.merge(ThaumicUnity.id("fames"), 1, Integer::sum);
        }
        if (components.has(DataComponents.TOOL)) {
            values.merge(ThaumicUnity.id("instrumentum"), 2, Integer::sum);
        }
        if (components.has(DataComponents.EQUIPPABLE)) {
            values.merge(ThaumicUnity.id("tutamen"), 2, Integer::sum);
        }
        if (item instanceof BlockItem) {
            values.merge(ThaumicUnity.id("terra"), 1, Integer::sum);
        }
        return AspectList.of(values);
    }
}
