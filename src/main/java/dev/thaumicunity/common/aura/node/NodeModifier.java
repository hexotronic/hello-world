package dev.thaumicunity.common.aura.node;

import com.mojang.serialization.Codec;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/** How lively an aura node is: how big it is and how fast it refills itself. Fading nodes do not refill at all. */
public enum NodeModifier implements StringRepresentable {
    NONE(1.0, 0.01),
    BRIGHT(1.4, 0.02),
    PALE(0.6, 0.005),
    FADING(1.0, 0.0);

    public static final Codec<NodeModifier> CODEC = StringRepresentable.fromEnum(NodeModifier::values);

    private final double size;
    private final double regen;

    NodeModifier(double size, double regen) {
        this.size = size;
        this.regen = regen;
    }

    /** Factor on the node's capacity when it forms. */
    public double size() {
        return size;
    }

    /** Share of its capacity the node refills every second. */
    public double regen() {
        return regen;
    }

    public String translationKey() {
        return "node.thaumicunity.modifier." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static NodeModifier byName(String name) {
        for (NodeModifier modifier : values()) {
            if (modifier.getSerializedName().equals(name)) {
                return modifier;
            }
        }
        return NONE;
    }

    public static NodeModifier random(RandomSource random) {
        float roll = random.nextFloat();
        return roll < 0.75f ? NONE : roll < 0.85f ? BRIGHT : roll < 0.95f ? PALE : FADING;
    }
}
