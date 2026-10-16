package dev.thaumicunity.core.spell;

import java.util.List;
import java.util.Objects;

/**
 * One delivery step of a spell, as arranged in the focus workbench: a medium carrying effects, changed by
 * modifiers. {@code children} are further mediums released where this one resolves (for example a projectile that
 * bursts into a cloud on impact).
 */
public record SpellNode(String medium, List<String> effects, List<String> modifiers, List<SpellNode> children) {
    public SpellNode {
        Objects.requireNonNull(medium, "medium");
        effects = List.copyOf(effects);
        modifiers = List.copyOf(modifiers);
        children = List.copyOf(children);
    }

    public static SpellNode of(String medium, List<String> effects) {
        return new SpellNode(medium, effects, List.of(), List.of());
    }

    public SpellNode withModifiers(List<String> mods) {
        return new SpellNode(medium, effects, mods, children);
    }

    public SpellNode withChildren(List<SpellNode> next) {
        return new SpellNode(medium, effects, modifiers, next);
    }

    /** Number of nodes in this subtree. */
    public int size() {
        int n = 1;
        for (SpellNode child : children) {
            n += child.size();
        }
        return n;
    }
}
