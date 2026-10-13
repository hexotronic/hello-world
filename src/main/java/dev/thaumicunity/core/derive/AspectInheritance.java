package dev.thaumicunity.core.derive;

import dev.thaumicunity.core.aspect.AspectAmounts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Derives aspects for items that have no explicit value, from the recipes that produce them
 * (docs/DESIGN_DECISIONS.md, D13, step 3).
 *
 * <p>For each recipe the ingredient aspects are summed (for an ingredient that accepts several items, the item with
 * the smallest known aspect total is used), multiplied by the loss factor, divided by the output count and capped.
 * Among all recipes of an item the one with the smallest positive total wins; ties break on recipe id. Recursion is
 * memoised; an item reached again while it is being resolved contributes nothing, which makes cycles
 * (block ↔ ingots) safe. Cycle members are resolved from whichever member is queried first; {@link #resolveAll}
 * queries in sorted order, so results are deterministic.</p>
 */
public final class AspectInheritance {
    /**
     * A recipe reduced to what matters for aspect inheritance.
     *
     * @param id          recipe id, for deterministic tie breaking
     * @param output      produced item id
     * @param outputCount number of items produced
     * @param ingredients each ingredient is a list of acceptable item ids
     */
    public record RecipeView(String id, String output, int outputCount, List<List<String>> ingredients) {
        public RecipeView {
            ingredients = ingredients.stream().map(List::copyOf).toList();
            if (outputCount < 1) {
                throw new IllegalArgumentException("Recipe " + id + " has an output count below 1");
            }
        }
    }

    private final Map<String, AspectAmounts> explicit;
    private final Map<String, List<RecipeView>> recipesByOutput;
    private final double lossFactor;
    private final int cap;
    private final Map<String, AspectAmounts> resolved = new HashMap<>();
    private final Map<String, Integer> stackIndex = new HashMap<>();
    /** Lowest stack index of an in-progress item that the current computation ran into (Tarjan-style lowlink). */
    private int lowestCycleRef = Integer.MAX_VALUE;

    public AspectInheritance(Map<String, AspectAmounts> explicit, Collection<RecipeView> recipes, double lossFactor, int cap) {
        if (lossFactor <= 0 || lossFactor > 1) {
            throw new IllegalArgumentException("lossFactor must be in (0, 1]");
        }
        this.explicit = Map.copyOf(explicit);
        Map<String, List<RecipeView>> byOutput = new TreeMap<>();
        for (RecipeView recipe : recipes) {
            byOutput.computeIfAbsent(recipe.output(), k -> new ArrayList<>()).add(recipe);
        }
        byOutput.values().forEach(list -> list.sort(Comparator.comparing(RecipeView::id)));
        this.recipesByOutput = byOutput;
        this.lossFactor = lossFactor;
        this.cap = cap;
    }

    /** Aspects of an item: explicit if known, otherwise inherited from recipes, otherwise empty. */
    public AspectAmounts aspectsOf(String item) {
        AspectAmounts known = explicit.get(item);
        if (known != null) {
            return known;
        }
        AspectAmounts cached = resolved.get(item);
        if (cached != null) {
            return cached;
        }
        Integer onStack = stackIndex.get(item);
        if (onStack != null) {
            lowestCycleRef = Math.min(lowestCycleRef, onStack);
            return AspectAmounts.EMPTY;
        }
        int myIndex = stackIndex.size();
        stackIndex.put(item, myIndex);
        int outerLowest = lowestCycleRef;
        lowestCycleRef = Integer.MAX_VALUE;
        AspectAmounts best = null;
        for (RecipeView recipe : recipesByOutput.getOrDefault(item, List.of())) {
            AspectAmounts candidate = evaluate(recipe);
            if (candidate.isEmpty()) {
                continue;
            }
            if (best == null || candidate.total() < best.total()) {
                best = candidate;
            }
        }
        stackIndex.remove(item);
        AspectAmounts result = best == null ? AspectAmounts.EMPTY : best;
        int myLowest = lowestCycleRef;
        // A result is exact unless it relied on an item further up the stack that was still unresolved; only exact
        // results are memoised (this keeps the resolver linear on DAGs and correct on cycles).
        if (myLowest >= myIndex) {
            resolved.put(item, result);
        }
        lowestCycleRef = Math.min(outerLowest, myLowest < myIndex ? myLowest : Integer.MAX_VALUE);
        return result;
    }

    /** Resolves every item that appears as an output, returning only non-empty results. */
    public Map<String, AspectAmounts> resolveAll() {
        Map<String, AspectAmounts> result = new TreeMap<>();
        for (String output : recipesByOutput.keySet()) {
            if (explicit.containsKey(output)) {
                continue;
            }
            AspectAmounts aspects = aspectsOf(output);
            if (!aspects.isEmpty()) {
                result.put(output, aspects);
            }
        }
        return result;
    }

    private AspectAmounts evaluate(RecipeView recipe) {
        AspectAmounts.Builder sum = AspectAmounts.builder();
        for (List<String> options : recipe.ingredients()) {
            cheapest(options).ifPresent(sum::add);
        }
        return sum.build().scale(lossFactor / recipe.outputCount()).cap(cap);
    }

    private Optional<AspectAmounts> cheapest(List<String> options) {
        AspectAmounts best = null;
        for (String option : options) {
            AspectAmounts value = aspectsOf(option);
            if (value.isEmpty()) {
                continue;
            }
            if (best == null || value.total() < best.total()) {
                best = value;
            }
        }
        return Optional.ofNullable(best);
    }
}
