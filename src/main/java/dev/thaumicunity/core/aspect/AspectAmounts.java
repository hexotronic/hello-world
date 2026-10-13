package dev.thaumicunity.core.aspect;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Immutable multiset of aspects (aspect id → positive amount), ordered by id so that iteration, equality and
 * serialisation are deterministic. Zero and negative amounts are never stored.
 */
public final class AspectAmounts {
    public static final AspectAmounts EMPTY = new AspectAmounts(new TreeMap<>());

    private final TreeMap<String, Integer> amounts;

    private AspectAmounts(TreeMap<String, Integer> amounts) {
        this.amounts = amounts;
    }

    public static AspectAmounts of(Map<String, Integer> values) {
        TreeMap<String, Integer> copy = new TreeMap<>();
        values.forEach((k, v) -> {
            if (v != null && v > 0) {
                copy.merge(k, v, Integer::sum);
            }
        });
        return copy.isEmpty() ? EMPTY : new AspectAmounts(copy);
    }

    public static AspectAmounts of(String aspect, int amount) {
        return of(Map.of(aspect, amount));
    }

    public static Builder builder() {
        return new Builder();
    }

    public int get(String aspect) {
        return amounts.getOrDefault(aspect, 0);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public int size() {
        return amounts.size();
    }

    /** Sum of all amounts. */
    public int total() {
        int sum = 0;
        for (int v : amounts.values()) {
            sum += v;
        }
        return sum;
    }

    public Map<String, Integer> asMap() {
        return Collections.unmodifiableMap(amounts);
    }

    public void forEach(BiConsumer<String, Integer> action) {
        amounts.forEach(action);
    }

    public AspectAmounts add(AspectAmounts other) {
        if (other.isEmpty()) {
            return this;
        }
        TreeMap<String, Integer> copy = new TreeMap<>(amounts);
        other.amounts.forEach((k, v) -> copy.merge(k, v, Integer::sum));
        return new AspectAmounts(copy);
    }

    /** Subtracts, dropping aspects that reach zero or below. */
    public AspectAmounts subtract(AspectAmounts other) {
        TreeMap<String, Integer> copy = new TreeMap<>(amounts);
        other.amounts.forEach((k, v) -> {
            int remaining = copy.getOrDefault(k, 0) - v;
            if (remaining > 0) {
                copy.put(k, remaining);
            } else {
                copy.remove(k);
            }
        });
        return copy.isEmpty() ? EMPTY : new AspectAmounts(copy);
    }

    /** True when this contains at least the given amount of every aspect in {@code other}. */
    public boolean containsAll(AspectAmounts other) {
        for (Map.Entry<String, Integer> e : other.amounts.entrySet()) {
            if (get(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Multiplies every amount by {@code factor}, rounding down and dropping aspects that become zero. */
    public AspectAmounts scale(double factor) {
        TreeMap<String, Integer> copy = new TreeMap<>();
        amounts.forEach((k, v) -> {
            int scaled = (int) Math.floor(v * factor + 1.0e-9);
            if (scaled > 0) {
                copy.put(k, scaled);
            }
        });
        return copy.isEmpty() ? EMPTY : new AspectAmounts(copy);
    }

    /** Caps every amount at {@code max}. */
    public AspectAmounts cap(int max) {
        TreeMap<String, Integer> copy = new TreeMap<>();
        amounts.forEach((k, v) -> copy.put(k, Math.min(v, max)));
        return copy.isEmpty() ? EMPTY : new AspectAmounts(copy);
    }

    /** Expands every aspect into its primal breakdown according to the graph. Unknown aspects are dropped. */
    public AspectAmounts toPrimals(AspectGraph graph) {
        TreeMap<String, Integer> copy = new TreeMap<>();
        amounts.forEach((aspect, amount) ->
                graph.primalBreakdown(aspect).forEach((primal, count) -> copy.merge(primal, count * amount, Integer::sum)));
        return copy.isEmpty() ? EMPTY : new AspectAmounts(copy);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AspectAmounts other && amounts.equals(other.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    @Override
    public String toString() {
        return amounts.toString();
    }

    public static final class Builder {
        private final TreeMap<String, Integer> amounts = new TreeMap<>();

        public Builder add(String aspect, int amount) {
            if (amount > 0) {
                amounts.merge(aspect, amount, Integer::sum);
            }
            return this;
        }

        public Builder add(AspectAmounts other) {
            other.forEach(this::add);
            return this;
        }

        public AspectAmounts build() {
            return amounts.isEmpty() ? EMPTY : new AspectAmounts(new TreeMap<>(amounts));
        }
    }
}
