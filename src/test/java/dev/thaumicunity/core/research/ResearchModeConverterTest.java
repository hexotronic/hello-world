package dev.thaumicunity.core.research;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResearchModeConverterTest {
    final List<String> aspects = List.of("ignis", "aqua", "ordo");

    @Test
    void unifiedKeepsEverything() {
        List<RequirementSpec> reqs = List.of(new RequirementSpec.Knowledge("theory", "alchemy", 2),
                new RequirementSpec.Insight(List.of("ignis", "aqua")));
        assertEquals(reqs, ResearchModeConverter.convert(reqs, ResearchMode.UNIFIED, aspects, "alchemy"));
    }

    @Test
    void classicTurnsKnowledgeIntoPuzzles() {
        List<RequirementSpec> out = ResearchModeConverter.convert(
                List.of(new RequirementSpec.Knowledge("observation", "alchemy", 3), new RequirementSpec.Other("scan", "x")),
                ResearchMode.CLASSIC, aspects, "alchemy");
        assertEquals(new RequirementSpec.Insight(List.of("ignis", "aqua", "ordo", "ignis")), out.get(0));
        assertEquals(new RequirementSpec.Other("scan", "x"), out.get(1));
    }

    @Test
    void modernTurnsPuzzlesIntoTheory() {
        List<RequirementSpec> out = ResearchModeConverter.convert(
                List.of(new RequirementSpec.Insight(List.of("ignis", "aqua", "ordo"))), ResearchMode.MODERN, aspects, "alchemy");
        assertEquals(List.of(new RequirementSpec.Knowledge("theory", "alchemy", 2)), out);
    }

    @Test
    void conversionsAreInverseForPuzzleSizesTwoToSix() {
        for (int anchors = 2; anchors <= 6; anchors++) {
            List<String> puzzleAspects = aspects.stream().limit(1).toList();
            RequirementSpec.Insight insight = new RequirementSpec.Insight(java.util.Collections.nCopies(anchors, "ignis"));
            RequirementSpec modern = ResearchModeConverter.convert(List.of(insight), ResearchMode.MODERN, puzzleAspects, "c").get(0);
            RequirementSpec back = ResearchModeConverter.convert(List.of(modern), ResearchMode.CLASSIC, puzzleAspects, "c").get(0);
            assertEquals(insight, back);
        }
    }
}
