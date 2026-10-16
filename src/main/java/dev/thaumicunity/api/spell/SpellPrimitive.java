package dev.thaumicunity.api.spell;

/**
 * Java behaviour behind spell components. Registered in the static registry {@code thaumicunity:spell_primitive};
 * spell component JSON names a primitive and supplies its {@link SpellParams}, so one primitive powers many
 * components (docs/SPELLS.md).
 */
public sealed interface SpellPrimitive permits SpellPrimitive.Effect, SpellPrimitive.Medium, SpellPrimitive.Modifier {
    /** Does something at a target. */
    non-sealed interface Effect extends SpellPrimitive {
        void apply(SpellContext context, SpellTarget target, SpellParams params, double power);
    }

    /** Carries a node's effects to where they resolve. */
    non-sealed interface Medium extends SpellPrimitive {
        void deliver(SpellContext context, SpellParams params, SpellDelivery delivery);
    }

    /**
     * A modifier with behaviour beyond the numbers in its JSON (which the compiler applies). The hook is called once
     * per resolved target, after effects; most modifiers use the no-op {@link #NUMERIC} primitive.
     */
    non-sealed interface Modifier extends SpellPrimitive {
        void afterResolve(SpellContext context, SpellTarget target, SpellParams params, double power);
    }

    /** Modifier primitive for purely numeric modifiers. */
    Modifier NUMERIC = (context, target, params, power) -> {
    };
}
