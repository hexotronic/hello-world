package dev.thaumicunity.core.spell;

/**
 * Structural limits of a focus. Focus blanks of higher tiers raise them (data-driven, see focus items).
 */
public record SpellLimits(int maxComplexity, int maxDepth, int maxEffectsPerNode, int maxModifiersPerNode,
                          int maxChildrenPerNode) {
    /** Limits of the three focus tiers: apprentice, adept, archmage. */
    public static final SpellLimits TIER_1 = new SpellLimits(20, 1, 2, 1, 1);
    public static final SpellLimits TIER_2 = new SpellLimits(50, 2, 3, 3, 2);
    public static final SpellLimits TIER_3 = new SpellLimits(100, 4, 4, 5, 3);
    /** Used for legendary foci and admin testing. */
    public static final SpellLimits UNBOUNDED = new SpellLimits(Integer.MAX_VALUE, 8, 8, 8, 4);
}
