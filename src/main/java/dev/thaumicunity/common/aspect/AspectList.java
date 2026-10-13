package dev.thaumicunity.common.aspect;

import com.mojang.serialization.Codec;
import dev.thaumicunity.core.aspect.AspectAmounts;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

/**
 * Immutable aspect → amount map keyed by aspect id, used for item/entity aspects, essentia contents, costs and
 * recipe requirements. JSON form: {@code {"thaumicunity:ignis": 4, "thaumicunity:aer": 2}}.
 */
public final class AspectList {
    public static final AspectList EMPTY = new AspectList(new TreeMap<>());

    public static final Codec<AspectList> CODEC = Codec.unboundedMap(Identifier.CODEC, ExtraCodecs.POSITIVE_INT)
            .xmap(AspectList::of, AspectList::asMap);

    public static final StreamCodec<ByteBuf, AspectList> STREAM_CODEC = ByteBufCodecs
            .<ByteBuf, Identifier, Integer, Map<Identifier, Integer>>map(LinkedHashMap::new, Identifier.STREAM_CODEC, ByteBufCodecs.VAR_INT)
            .map(AspectList::of, AspectList::asMap);

    private final TreeMap<Identifier, Integer> amounts;

    private AspectList(TreeMap<Identifier, Integer> amounts) {
        this.amounts = amounts;
    }

    public static AspectList of(Map<Identifier, Integer> values) {
        TreeMap<Identifier, Integer> copy = new TreeMap<>();
        values.forEach((k, v) -> {
            if (v != null && v > 0) {
                copy.merge(k, v, Integer::sum);
            }
        });
        return copy.isEmpty() ? EMPTY : new AspectList(copy);
    }

    public static AspectList of(Identifier aspect, int amount) {
        return of(Map.of(aspect, amount));
    }

    /** Converts from the core representation, whose keys are aspect id strings. */
    public static AspectList fromCore(AspectAmounts amounts) {
        TreeMap<Identifier, Integer> copy = new TreeMap<>();
        amounts.forEach((k, v) -> {
            Identifier id = Identifier.tryParse(k);
            if (id != null) {
                copy.put(id, v);
            }
        });
        return copy.isEmpty() ? EMPTY : new AspectList(copy);
    }

    public AspectAmounts toCore() {
        AspectAmounts.Builder builder = AspectAmounts.builder();
        amounts.forEach((k, v) -> builder.add(k.toString(), v));
        return builder.build();
    }

    public int get(Identifier aspect) {
        return amounts.getOrDefault(aspect, 0);
    }

    public boolean isEmpty() {
        return amounts.isEmpty();
    }

    public int size() {
        return amounts.size();
    }

    public int total() {
        int sum = 0;
        for (int v : amounts.values()) {
            sum += v;
        }
        return sum;
    }

    public Map<Identifier, Integer> asMap() {
        return Collections.unmodifiableMap(amounts);
    }

    public void forEach(BiConsumer<Identifier, Integer> action) {
        amounts.forEach(action);
    }

    public AspectList add(AspectList other) {
        if (other.isEmpty()) {
            return this;
        }
        TreeMap<Identifier, Integer> copy = new TreeMap<>(amounts);
        other.amounts.forEach((k, v) -> copy.merge(k, v, Integer::sum));
        return new AspectList(copy);
    }

    public AspectList add(Identifier aspect, int amount) {
        return add(of(aspect, amount));
    }

    public AspectList subtract(AspectList other) {
        return fromCore(toCore().subtract(other.toCore()));
    }

    public boolean containsAll(AspectList other) {
        for (Map.Entry<Identifier, Integer> e : other.amounts.entrySet()) {
            if (get(e.getKey()) < e.getValue()) {
                return false;
            }
        }
        return true;
    }

    public AspectList scale(double factor) {
        return fromCore(toCore().scale(factor));
    }

    /** The single aspect with the largest amount (ties: smallest id), or null when empty. */
    public Identifier dominant() {
        Identifier best = null;
        int bestAmount = 0;
        for (Map.Entry<Identifier, Integer> e : amounts.entrySet()) {
            if (e.getValue() > bestAmount) {
                best = e.getKey();
                bestAmount = e.getValue();
            }
        }
        return best;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AspectList other && amounts.equals(other.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    @Override
    public String toString() {
        return amounts.toString();
    }
}
