package dev.thaumicunity.common.aura.node;

import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

import java.util.Locale;

/**
 * What kind of aura node it is (Thaumcraft 4's node types), and so what it does to the aura around it besides feeding
 * it vis (docs/DESIGN_DECISIONS.md, D2).
 */
public enum NodeType implements StringRepresentable {
    /** Feeds the aura. */
    NORMAL(1.0, 0.0, 0xB98AF0),
    /** Feeds the aura and washes flux out of it. */
    PURE(1.0, -0.05, 0xF2F2FF),
    /** Feeds the aura a little and adds flux. */
    DARK(0.5, 0.02, 0x3A2A4A),
    /** Drinks vis from the aura instead of feeding it. */
    HUNGRY(0.0, 0.0, 0x8A2A2A),
    /** Feeds the aura in fits and starts, now and then flaring into flux. */
    UNSTABLE(1.5, 0.0, 0xF0A040),
    /** Adds flux steadily. */
    TAINTED(0.5, 0.05, 0x8A3AB0);

    public static final Codec<NodeType> CODEC = StringRepresentable.fromEnum(NodeType::values);

    private final double feed;
    private final double fluxPerSecond;
    private final int tint;

    NodeType(double feed, double fluxPerSecond, int tint) {
        this.feed = feed;
        this.fluxPerSecond = fluxPerSecond;
        this.tint = tint;
    }

    /** How strongly the node feeds the aura (1 = normal). */
    public double feed() {
        return feed;
    }

    /** Flux the node adds (or, if negative, removes) every second. */
    public double fluxPerSecond() {
        return fluxPerSecond;
    }

    /** Colour mixed into the node's glow. */
    public int tint() {
        return tint;
    }

    public String translationKey() {
        return "node.thaumicunity.type." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NodeType byName(String name) {
        for (NodeType type : values()) {
            if (type.getSerializedName().equals(name)) {
                return type;
            }
        }
        return NORMAL;
    }

    /** A type for a node forming in the given biome: dark places favour dark nodes, the Nether unstable ones. */
    public static NodeType forBiome(Holder<Biome> biome, RandomSource random) {
        float roll = random.nextFloat();
        if (biome.is(Biomes.DEEP_DARK) || biome.is(Biomes.DARK_FOREST) || biome.is(Biomes.PALE_GARDEN)) {
            return roll < 0.5f ? DARK : roll < 0.6f ? HUNGRY : NORMAL;
        }
        if (biome.is(BiomeTags.IS_NETHER)) {
            return roll < 0.5f ? UNSTABLE : roll < 0.7f ? HUNGRY : NORMAL;
        }
        if (roll < 0.70f) {
            return NORMAL;
        }
        if (roll < 0.80f) {
            return PURE;
        }
        if (roll < 0.87f) {
            return UNSTABLE;
        }
        if (roll < 0.93f) {
            return HUNGRY;
        }
        return DARK;
    }
}
