package dev.thaumicunity.core.spell;

import java.util.Locale;

/** The three kinds of spell component (docs/SPELLS.md). */
public enum ComponentKind {
    /** How the spell travels or where it resolves: touch, bolt, projectile, beam, cloud, rune, zone, … */
    MEDIUM,
    /** What happens where the medium resolves: fire, frost, heal, break, teleport, … */
    EFFECT,
    /** How a node is changed: split, amplify, linger, echo, efficiency, … */
    MODIFIER;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
