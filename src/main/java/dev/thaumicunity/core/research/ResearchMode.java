package dev.thaumicunity.core.research;

import java.util.Locale;

/** Research progression style, see docs/DESIGN_DECISIONS.md, D3. */
public enum ResearchMode {
    /** Requirements are used as written; puzzles and theorycraft both award progress. */
    UNIFIED,
    /** Knowledge costs become insight puzzles (Thaumcraft 3/4 style). */
    CLASSIC,
    /** Insight puzzles become knowledge costs (Thaumcraft 6 style). */
    MODERN;

    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
