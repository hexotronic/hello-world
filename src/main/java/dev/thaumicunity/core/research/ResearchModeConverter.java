package dev.thaumicunity.core.research;

import java.util.ArrayList;
import java.util.List;

/**
 * Converts stage requirements between research modes.
 *
 * <ul>
 *   <li>{@link ResearchMode#CLASSIC}: a knowledge cost of {@code n} points becomes an insight puzzle with
 *   {@code n + 1} anchors (so {@code n} links to make), taken in order from the entry's aspects, cycling if the
 *   entry has fewer aspects. At most 6 anchors are used.</li>
 *   <li>{@link ResearchMode#MODERN}: an insight puzzle with {@code k} anchors becomes a <em>theory</em> cost of
 *   {@code max(1, k - 1)} points in the entry's category.</li>
 *   <li>{@link ResearchMode#UNIFIED}: requirements are unchanged.</li>
 * </ul>
 * The two conversions are inverse for puzzles of up to six anchors, which the unit tests check.
 */
public final class ResearchModeConverter {
    public static final int MAX_ANCHORS = 6;

    private ResearchModeConverter() {
    }

    public static List<RequirementSpec> convert(List<RequirementSpec> requirements, ResearchMode mode,
                                                List<String> entryAspects, String category) {
        if (mode == ResearchMode.UNIFIED) {
            return List.copyOf(requirements);
        }
        List<RequirementSpec> result = new ArrayList<>(requirements.size());
        for (RequirementSpec requirement : requirements) {
            result.add(switch (mode) {
                case CLASSIC -> requirement instanceof RequirementSpec.Knowledge knowledge && !entryAspects.isEmpty()
                        ? toInsight(knowledge, entryAspects)
                        : requirement;
                case MODERN -> requirement instanceof RequirementSpec.Insight insight
                        ? new RequirementSpec.Knowledge("theory", category, Math.max(1, insight.aspects().size() - 1))
                        : requirement;
                case UNIFIED -> requirement;
            });
        }
        return List.copyOf(result);
    }

    private static RequirementSpec.Insight toInsight(RequirementSpec.Knowledge knowledge, List<String> entryAspects) {
        int anchors = Math.min(MAX_ANCHORS, Math.max(2, knowledge.amount() + 1));
        List<String> aspects = new ArrayList<>(anchors);
        for (int i = 0; i < anchors; i++) {
            aspects.add(entryAspects.get(i % entryAspects.size()));
        }
        return new RequirementSpec.Insight(aspects);
    }
}
