package dev.thaumicunity.core.derive;

import dev.thaumicunity.core.aspect.AspectAmounts;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AspectInheritanceTest {
    final Map<String, AspectAmounts> explicit = Map.of(
            "iron_ingot", AspectAmounts.of(Map.of("metallum", 4)),
            "stick", AspectAmounts.of(Map.of("arbor", 1)),
            "oak_planks", AspectAmounts.of(Map.of("arbor", 2)),
            "birch_planks", AspectAmounts.of(Map.of("arbor", 3)));

    @Test
    void sumsIngredientsAppliesLossAndDividesByCount() {
        var inheritance = new AspectInheritance(explicit, List.of(
                new AspectInheritance.RecipeView("iron_pickaxe", "iron_pickaxe", 1,
                        List.of(List.of("iron_ingot"), List.of("iron_ingot"), List.of("iron_ingot"), List.of("stick"), List.of("stick")))),
                0.75, 64);
        assertEquals(Map.of("metallum", 9, "arbor", 1), inheritance.aspectsOf("iron_pickaxe").asMap());
    }

    @Test
    void picksCheapestOptionAndCheapestRecipe() {
        var inheritance = new AspectInheritance(explicit, List.of(
                new AspectInheritance.RecipeView("a_chest", "chest", 1, List.of(
                        List.of("birch_planks", "oak_planks"), List.of("oak_planks"), List.of("oak_planks"), List.of("oak_planks"))),
                new AspectInheritance.RecipeView("b_chest", "chest", 1, List.of(List.of("iron_ingot"), List.of("iron_ingot")))),
                1.0, 64);
        assertEquals(Map.of("arbor", 8), inheritance.aspectsOf("chest").asMap());
    }

    @Test
    void cyclesTerminate() {
        var inheritance = new AspectInheritance(explicit, List.of(
                new AspectInheritance.RecipeView("block", "iron_block", 1, java.util.Collections.nCopies(9, List.of("iron_ingot"))),
                new AspectInheritance.RecipeView("nugget_from_block", "iron_nugget", 81, List.of(List.of("iron_block"))),
                new AspectInheritance.RecipeView("x", "loop_a", 1, List.of(List.of("loop_b"))),
                new AspectInheritance.RecipeView("y", "loop_b", 1, List.of(List.of("loop_a")))),
                0.75, 64);
        assertEquals(Map.of("metallum", 27), inheritance.aspectsOf("iron_block").asMap());
        assertTrue(inheritance.aspectsOf("iron_nugget").isEmpty()); // 27 * 0.75 / 81 rounds to zero
        assertTrue(inheritance.aspectsOf("loop_a").isEmpty());
        Map<String, AspectAmounts> all = inheritance.resolveAll();
        assertTrue(all.containsKey("iron_block"));
        assertTrue(!all.containsKey("loop_b"));
    }

    @Test
    void capsLargeValues() {
        var inheritance = new AspectInheritance(explicit, List.of(
                new AspectInheritance.RecipeView("huge", "huge", 1, java.util.Collections.nCopies(40, List.of("iron_ingot")))),
                1.0, 64);
        assertEquals(64, inheritance.aspectsOf("huge").get("metallum"));
    }

    @Test
    void cycleFedFromOutsideResolvesEveryMember() {
        // a is made from iron or from b; b only from a. Whichever is asked first, both get values.
        var recipes = List.of(
                new AspectInheritance.RecipeView("a_from_iron", "a", 1, List.of(List.of("iron_ingot"))),
                new AspectInheritance.RecipeView("a_from_b", "a", 1, List.of(List.of("b"))),
                new AspectInheritance.RecipeView("b_from_a", "b", 1, List.of(List.of("a"), List.of("stick"))));
        var askB = new AspectInheritance(explicit, recipes, 1.0, 64);
        assertEquals(Map.of("metallum", 4, "arbor", 1), askB.aspectsOf("b").asMap());
        var askA = new AspectInheritance(explicit, recipes, 1.0, 64);
        assertEquals(Map.of("metallum", 4), askA.aspectsOf("a").asMap());
        assertEquals(Map.of("metallum", 4, "arbor", 1), askA.aspectsOf("b").asMap());
    }

    @Test
    void denseRedyeingCyclesResolveQuickly() {
        // Sixteen colours, each craftable from any colour plus its dye (like wool, beds, candles); one colour also
        // from plain planks. A resolver that re-explores cycles takes exponential time here.
        java.util.List<AspectInheritance.RecipeView> recipes = new java.util.ArrayList<>();
        java.util.List<String> colours = java.util.stream.IntStream.range(0, 16).mapToObj(i -> "colour_" + i).toList();
        for (String colour : colours) {
            recipes.add(new AspectInheritance.RecipeView("dye_" + colour, colour, 1, List.of(colours, List.of("stick"))));
        }
        recipes.add(new AspectInheritance.RecipeView("plain", "colour_0", 1, List.of(List.of("oak_planks"))));
        var inheritance = new AspectInheritance(explicit, recipes, 1.0, 64);
        Map<String, AspectAmounts> all = org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                java.time.Duration.ofSeconds(2), inheritance::resolveAll);
        assertEquals(16, all.size());
        assertEquals(Map.of("arbor", 2), all.get("colour_0").asMap());
        assertEquals(Map.of("arbor", 3), all.get("colour_7").asMap());
    }
}
