package dev.thaumicunity.core.derive;

import dev.thaumicunity.core.aspect.AspectAmounts;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Derives aspects for items that have no explicit value, from the recipes that produce them
 * (docs/DESIGN_DECISIONS.md, D13, step 3).
 *
 * <p>For each recipe the ingredient aspects are summed (for an ingredient that accepts several items, the item with
 * the smallest known aspect total is used), multiplied by the loss factor, divided by the output count and capped.
 * Among all recipes of an item the one with the smallest positive total wins; ties break on recipe id.</p>
 *
 * <p>Recipe graphs are full of cycles (blocks ↔ ingots, every dyed variant craftable from every other). Items are
 * therefore grouped into strongly connected components and resolved dependencies first, which is linear in the size
 * of the graph. Inside a cycle, members are filled in a monotone fixed point: each pass gives a value to every member
 * with a recipe in which no ingredient is limited to members still without a value, until a pass adds nothing. A
 * cycle fed by an outside recipe thus gets values for all its members; a cycle with no outside input stays empty.
 * Passes run in sorted order, so results are deterministic.</p>
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
    /** Members of the component being resolved that have no value yet. */
    private Set<String> pending = Set.of();
    private boolean complete;

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
        resolveGraph();
        return resolved.getOrDefault(item, AspectAmounts.EMPTY);
    }

    /** Resolves every item that appears as an output, returning only non-empty results. */
    public Map<String, AspectAmounts> resolveAll() {
        resolveGraph();
        Map<String, AspectAmounts> result = new TreeMap<>();
        resolved.forEach((item, aspects) -> {
            if (!aspects.isEmpty()) {
                result.put(item, aspects);
            }
        });
        return result;
    }

    /** True for items whose value comes from recipes (not explicit, produced by at least one recipe). */
    private boolean derived(String item) {
        return !explicit.containsKey(item) && recipesByOutput.containsKey(item);
    }

    private void resolveGraph() {
        if (complete) {
            return;
        }
        for (List<String> component : components()) {
            resolveComponent(component);
        }
        complete = true;
    }

    /** Derived items each derived item's recipes refer to, in a stable order. */
    private List<String> dependencies(String item) {
        TreeSet<String> deps = new TreeSet<>();
        for (RecipeView recipe : recipesByOutput.get(item)) {
            for (List<String> options : recipe.ingredients()) {
                for (String option : options) {
                    if (derived(option)) {
                        deps.add(option);
                    }
                }
            }
        }
        return List.copyOf(deps);
    }

    /**
     * Strongly connected components of the derived items (iterative Tarjan), emitted dependencies first: every
     * component comes after all components it depends on.
     */
    private List<List<String>> components() {
        Map<String, Integer> index = new HashMap<>();
        Map<String, Integer> lowLink = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> components = new ArrayList<>();
        Map<String, List<String>> deps = new HashMap<>();
        int counter = 0;
        for (String root : recipesByOutput.keySet()) {
            if (!derived(root) || index.containsKey(root)) {
                continue;
            }
            // Each frame: the item and the position of the next dependency to visit.
            Deque<Object[]> frames = new ArrayDeque<>();
            frames.push(new Object[]{root, 0});
            index.put(root, counter);
            lowLink.put(root, counter);
            counter++;
            stack.push(root);
            onStack.add(root);
            while (!frames.isEmpty()) {
                Object[] frame = frames.peek();
                String item = (String) frame[0];
                List<String> itemDeps = deps.computeIfAbsent(item, this::dependencies);
                int next = (Integer) frame[1];
                if (next < itemDeps.size()) {
                    frame[1] = next + 1;
                    String dep = itemDeps.get(next);
                    if (!index.containsKey(dep)) {
                        index.put(dep, counter);
                        lowLink.put(dep, counter);
                        counter++;
                        stack.push(dep);
                        onStack.add(dep);
                        frames.push(new Object[]{dep, 0});
                    } else if (onStack.contains(dep)) {
                        lowLink.put(item, Math.min(lowLink.get(item), index.get(dep)));
                    }
                    continue;
                }
                frames.pop();
                if (!frames.isEmpty()) {
                    String parent = (String) frames.peek()[0];
                    lowLink.put(parent, Math.min(lowLink.get(parent), lowLink.get(item)));
                }
                if (lowLink.get(item).equals(index.get(item))) {
                    List<String> component = new ArrayList<>();
                    String member;
                    do {
                        member = stack.pop();
                        onStack.remove(member);
                        component.add(member);
                    } while (!member.equals(item));
                    component.sort(Comparator.naturalOrder());
                    components.add(component);
                }
            }
        }
        return components;
    }

    private void resolveComponent(List<String> members) {
        TreeSet<String> open = new TreeSet<>(members);
        pending = open;
        boolean changed = true;
        while (changed && !open.isEmpty()) {
            changed = false;
            for (String item : List.copyOf(open)) {
                AspectAmounts value = best(item);
                if (!value.isEmpty()) {
                    resolved.put(item, value);
                    open.remove(item);
                    changed = true;
                }
            }
        }
        for (String item : open) {
            resolved.put(item, AspectAmounts.EMPTY);
        }
        pending = Set.of();
    }

    /** The cheapest non-empty recipe result of an item from the values known so far. */
    private AspectAmounts best(String item) {
        AspectAmounts best = null;
        for (RecipeView recipe : recipesByOutput.getOrDefault(item, List.of())) {
            AspectAmounts candidate = evaluate(recipe);
            if (candidate != null && !candidate.isEmpty() && (best == null || candidate.total() < best.total())) {
                best = candidate;
            }
        }
        return best == null ? AspectAmounts.EMPTY : best;
    }

    /** Value of an ingredient option while resolving: explicit, already resolved, or nothing yet. */
    private AspectAmounts known(String item) {
        AspectAmounts value = explicit.get(item);
        return value != null ? value : resolved.getOrDefault(item, AspectAmounts.EMPTY);
    }

    /**
     * The recipe's value from what is known so far, or null while some ingredient can only be an item of the
     * current cycle that has no value yet (counting it as nothing would undervalue the recipe).
     */
    private AspectAmounts evaluate(RecipeView recipe) {
        AspectAmounts.Builder sum = AspectAmounts.builder();
        for (List<String> options : recipe.ingredients()) {
            if (!options.isEmpty() && pending.containsAll(options)) {
                return null;
            }
            cheapest(options).ifPresent(sum::add);
        }
        return sum.build().scale(lossFactor / recipe.outputCount()).cap(cap);
    }

    private Optional<AspectAmounts> cheapest(List<String> options) {
        AspectAmounts best = null;
        for (String option : options) {
            AspectAmounts value = known(option);
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
