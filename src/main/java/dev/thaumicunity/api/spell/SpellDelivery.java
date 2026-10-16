package dev.thaumicunity.api.spell;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What a medium primitive must deliver: from where, in which direction, how many branches, and the callback that
 * applies the node's effects (and releases its follow-up mediums) wherever the medium resolves.
 */
public interface SpellDelivery {
    Vec3 origin();

    /** Normalised direction of travel. */
    Vec3 direction();

    /** The entity the medium is released from (caster or a previous target); may be null. */
    @Nullable
    Entity source();

    /** Number of parallel deliveries requested by split/scatter modifiers. */
    int branches();

    /** Extra area from modifiers (added to radii). */
    double areaBonus();

    /** Multiplier on durations from modifiers. */
    double durationMultiplier();

    /** Applies the node's effects at the target. Mediums may call this many times (areas, chains, beams). */
    void resolve(SpellTarget target);
}
