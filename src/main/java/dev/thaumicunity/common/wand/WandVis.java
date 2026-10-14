package dev.thaumicunity.common.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Data component {@code thaumicunity:wand_vis}: vis stored in a wand, per primal in the order of
 * {@link dev.thaumicunity.common.aspect.Aspects#PRIMALS}, in hundredths of a point so small costs add up exactly.
 * The capacity (also in hundredths, per primal) is kept alongside so the item bar can be drawn without registry
 * access.
 */
public record WandVis(List<Integer> centivis, int capacity) {
    public static final int SCALE = 100;
    public static final WandVis EMPTY = new WandVis(List.of(0, 0, 0, 0, 0, 0), 0);

    public static final Codec<WandVis> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, Integer.MAX_VALUE).listOf(6, 6).fieldOf("vis").forGetter(WandVis::centivis),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("capacity", 0).forGetter(WandVis::capacity)
    ).apply(i, WandVis::new));
    public static final StreamCodec<ByteBuf, WandVis> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6)), WandVis::centivis,
            ByteBufCodecs.VAR_INT, WandVis::capacity,
            WandVis::new);

    public WandVis {
        if (centivis.size() != 6) {
            throw new IllegalArgumentException("A wand stores six primals, got " + centivis.size());
        }
        centivis = List.copyOf(centivis);
    }

    /** Stored hundredths of primal {@code index}. */
    public int get(int index) {
        return centivis.get(index);
    }

    /** A copy with primal {@code index} set to {@code value}, kept within 0 and the capacity. */
    public WandVis with(int index, int value) {
        List<Integer> copy = new ArrayList<>(centivis);
        copy.set(index, Math.max(0, capacity > 0 ? Math.min(capacity, value) : value));
        return new WandVis(copy, capacity);
    }

    /** A copy with a new capacity; stored vis above it is lost. */
    public WandVis withCapacity(int newCapacity) {
        List<Integer> copy = new ArrayList<>(centivis);
        copy.replaceAll(v -> Math.min(v, newCapacity));
        return new WandVis(copy, newCapacity);
    }

    public boolean isFull(int index) {
        return capacity > 0 && centivis.get(index) >= capacity;
    }

    public int total() {
        int sum = 0;
        for (int v : centivis) {
            sum += v;
        }
        return sum;
    }

    /** All six primals at the given amount (hundredths). */
    public static WandVis filled(int centivisEach, int capacity) {
        return new WandVis(List.of(centivisEach, centivisEach, centivisEach, centivisEach, centivisEach, centivisEach), capacity);
    }
}
