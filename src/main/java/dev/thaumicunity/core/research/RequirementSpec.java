package dev.thaumicunity.core.research;

import java.util.List;

/**
 * Minecraft-independent view of a research stage requirement, used for the classic/modern/unified research mode
 * conversion (see docs/DESIGN_DECISIONS.md, D3). Requirement types that are the same in every mode are carried
 * through as {@link Other}.
 */
public sealed interface RequirementSpec {
    /** Spend knowledge points of a kind ("observation" or "theory") in a research category. */
    record Knowledge(String kind, String category, int amount) implements RequirementSpec {
    }

    /** Solve an insight puzzle whose anchors are the given aspects. */
    record Insight(List<String> aspects) implements RequirementSpec {
        public Insight {
            aspects = List.copyOf(aspects);
        }
    }

    /** Any other requirement (scan, craft, obtain, research, warp, …), unchanged by mode conversion. */
    record Other(String type, Object payload) implements RequirementSpec {
    }
}
