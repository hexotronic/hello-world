package dev.thaumicunity.core.spell;

/**
 * Numeric effect of a modifier on the node it is attached to. Multipliers compose by multiplication, additive
 * values by addition. {@code branches > 1} duplicates the node's delivery (split/scatter); the duplicated
 * deliveries share the node's cost multiplier.
 */
public record ModifierOps(double power, double cost, double cooldown, double area, double duration, int branches) {
    public static final ModifierOps IDENTITY = new ModifierOps(1.0, 1.0, 1.0, 0.0, 1.0, 1);

    public ModifierOps {
        if (power <= 0 || cost <= 0 || cooldown <= 0 || duration <= 0) {
            throw new IllegalArgumentException("Modifier multipliers must be positive");
        }
        if (branches < 1 || branches > 16) {
            throw new IllegalArgumentException("Modifier branches must be between 1 and 16");
        }
    }

    public ModifierOps compose(ModifierOps other) {
        return new ModifierOps(power * other.power, cost * other.cost, cooldown * other.cooldown, area + other.area,
                duration * other.duration, Math.min(16, branches * other.branches));
    }
}
