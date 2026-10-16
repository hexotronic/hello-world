package dev.thaumicunity.core.spell;

import dev.thaumicunity.core.aspect.AspectAmounts;

import java.util.List;

/**
 * Result of compiling a spell graph: what it costs, how complex it is, its cooldown and warp, the effective
 * per-node values the runtime needs, and every validation error. A spell with errors cannot be imprinted on a focus.
 *
 * @param root       compiled root node (null when the root medium itself was invalid)
 * @param cost       vis cost per primal aspect, rounded up
 * @param complexity total complexity
 * @param cooldown   cooldown in ticks
 * @param warp       warp gained per cast
 * @param errors     human-readable validation errors (translation keys with arguments are produced by the caller)
 */
public record CompiledSpell(CompiledNode root, AspectAmounts cost, int complexity, int cooldown, int warp,
                            List<String> errors) {
    public CompiledSpell {
        errors = List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    /**
     * One compiled node.
     *
     * @param medium          medium id
     * @param effects         effects placed by the player
     * @param emergentEffects synergy effects triggered by the combination of effect aspects
     * @param modifiers       modifier ids
     * @param ops             combined modifier numbers (power, cost, cooldown, area, duration, branches)
     * @param children        nodes released where this one resolves
     */
    public record CompiledNode(String medium, List<String> effects, List<String> emergentEffects, List<String> modifiers,
                               ModifierOps ops, List<CompiledNode> children) {
        public CompiledNode {
            effects = List.copyOf(effects);
            emergentEffects = List.copyOf(emergentEffects);
            modifiers = List.copyOf(modifiers);
            children = List.copyOf(children);
        }
    }
}
