package dev.thaumicunity.core.spell;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Minecraft-independent numbers of one spell component, as loaded from the {@code thaumicunity:spell_component}
 * datapack registry.
 *
 * @param id          namespaced id
 * @param kind        medium, effect or modifier
 * @param aspect      the aspect the component channels (used for synergies and attunement), may be empty
 * @param cost        base vis cost as aspect amounts; compounds are paid in their primal breakdown
 * @param complexity  complexity points this component adds to a focus
 * @param cooldown    cooldown ticks this component adds
 * @param tags        free-form tags (e.g. {@code projectile}, {@code area}, {@code self}); modifiers match on them
 * @param appliesTo   for modifiers: tags of the node medium it may be attached to; empty means any medium
 * @param ops         for modifiers: numeric effect; {@link ModifierOps#IDENTITY} otherwise
 * @param costMultiplier for mediums: multiplier applied to the effects carried by this medium
 * @param warp        warp gained per cast (forbidden components)
 * @param synergyOf   for synergy effects: the compound aspect whose components, carried together, trigger it
 */
public record SpellComponentSpec(String id, ComponentKind kind, Optional<String> aspect, Map<String, Integer> cost,
                                 int complexity, int cooldown, Set<String> tags, Set<String> appliesTo, ModifierOps ops,
                                 double costMultiplier, int warp, Optional<String> synergyOf) {
    public SpellComponentSpec {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        cost = Map.copyOf(cost);
        tags = Set.copyOf(tags);
        appliesTo = Set.copyOf(appliesTo);
        if (complexity < 0 || cooldown < 0 || warp < 0 || costMultiplier <= 0) {
            throw new IllegalArgumentException("Invalid numbers for spell component " + id);
        }
    }

    public static SpellComponentSpec medium(String id, String aspect, int complexity, int cooldown, double costMultiplier,
                                            Set<String> tags) {
        return new SpellComponentSpec(id, ComponentKind.MEDIUM, Optional.ofNullable(aspect), Map.of(), complexity,
                cooldown, tags, Set.of(), ModifierOps.IDENTITY, costMultiplier, 0, Optional.empty());
    }

    public static SpellComponentSpec effect(String id, String aspect, Map<String, Integer> cost, int complexity,
                                            int cooldown) {
        return new SpellComponentSpec(id, ComponentKind.EFFECT, Optional.ofNullable(aspect), cost, complexity, cooldown,
                Set.of(), Set.of(), ModifierOps.IDENTITY, 1.0, 0, Optional.empty());
    }

    public static SpellComponentSpec modifier(String id, int complexity, ModifierOps ops, Set<String> appliesTo) {
        return new SpellComponentSpec(id, ComponentKind.MODIFIER, Optional.empty(), Map.of(), complexity, 0, Set.of(),
                appliesTo, ops, 1.0, 0, Optional.empty());
    }

    public SpellComponentSpec withWarp(int warpPerCast) {
        return new SpellComponentSpec(id, kind, aspect, cost, complexity, cooldown, tags, appliesTo, ops, costMultiplier,
                warpPerCast, synergyOf);
    }

    public SpellComponentSpec asSynergyOf(String compoundAspect) {
        return new SpellComponentSpec(id, kind, aspect, cost, complexity, cooldown, tags, appliesTo, ops, costMultiplier,
                warp, Optional.of(compoundAspect));
    }
}
