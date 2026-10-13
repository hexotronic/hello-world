package dev.thaumicunity.core.spell;

import dev.thaumicunity.core.aspect.AspectDefinition;
import dev.thaumicunity.core.aspect.AspectGraph;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellCompilerTest {
    static final AspectGraph ASPECTS = AspectGraph.build(List.of(
            AspectDefinition.primal("ignis", 1), AspectDefinition.primal("aqua", 2), AspectDefinition.primal("perditio", 3),
            AspectDefinition.primal("aer", 4),
            AspectDefinition.compound("gelum", 5, "ignis", "perditio"),
            AspectDefinition.compound("vapor", 6, "aqua", "ignis")));

    static final SpellComponentLibrary LIBRARY = new SpellComponentLibrary(List.of(
            SpellComponentSpec.medium("touch", null, 1, 0, 0.5, Set.of("touch")),
            SpellComponentSpec.medium("bolt", null, 2, 4, 1.0, Set.of("projectile")),
            SpellComponentSpec.medium("cloud", "aer", 4, 10, 1.5, Set.of("area")),
            SpellComponentSpec.effect("fire", "ignis", Map.of("ignis", 8), 2, 6),
            SpellComponentSpec.effect("frost", "gelum", Map.of("gelum", 4), 2, 6),
            SpellComponentSpec.effect("water", "aqua", Map.of("aqua", 6), 2, 4),
            SpellComponentSpec.effect("steam_burst", "vapor", Map.of("vapor", 5), 3, 0).asSynergyOf("vapor"),
            SpellComponentSpec.effect("void_lance", "perditio", Map.of("perditio", 10), 6, 20).withWarp(2),
            SpellComponentSpec.modifier("split", 3, new ModifierOps(0.8, 1.6, 1.0, 0, 1.0, 2), Set.of("projectile")),
            SpellComponentSpec.modifier("amplify", 4, new ModifierOps(1.5, 1.75, 1.25, 0, 1.0, 1), Set.of()),
            SpellComponentSpec.modifier("efficiency", 2, new ModifierOps(0.9, 0.7, 1.0, 0, 1.0, 1), Set.of())));

    final SpellCompiler compiler = new SpellCompiler(LIBRARY, ASPECTS, 1.0);

    @Test
    void simpleBolt() {
        CompiledSpell spell = compiler.compile(SpellNode.of("bolt", List.of("fire")), SpellLimits.TIER_1);
        assertTrue(spell.isValid(), spell.errors().toString());
        assertEquals(Map.of("ignis", 8), spell.cost().asMap());
        assertEquals(4, spell.complexity());
        assertEquals(10, spell.cooldown());
        assertEquals(0, spell.warp());
    }

    @Test
    void compoundEffectsArePaidInPrimals() {
        CompiledSpell spell = compiler.compile(SpellNode.of("touch", List.of("frost")), SpellLimits.TIER_1);
        assertEquals(Map.of("ignis", 2, "perditio", 2), spell.cost().asMap());
    }

    @Test
    void modifiersScaleCostAndRespectAppliesTo() {
        CompiledSpell split = compiler.compile(SpellNode.of("bolt", List.of("fire")).withModifiers(List.of("split")), SpellLimits.TIER_2);
        assertTrue(split.isValid(), split.errors().toString());
        assertEquals(Map.of("ignis", 13), split.cost().asMap()); // 8 * 1.6 = 12.8 -> 13
        assertEquals(2, split.root().ops().branches());

        CompiledSpell wrong = compiler.compile(SpellNode.of("touch", List.of("fire")).withModifiers(List.of("split")), SpellLimits.TIER_2);
        assertFalse(wrong.isValid());
    }

    @Test
    void synergyEmergesFromCombinedAspects() {
        CompiledSpell spell = compiler.compile(SpellNode.of("bolt", List.of("fire", "water")), SpellLimits.TIER_2);
        assertTrue(spell.isValid(), spell.errors().toString());
        assertEquals(List.of("steam_burst"), spell.root().emergentEffects());
        // fire 2 + water 2 + bolt 2 + one complexity for the synergy
        assertEquals(7, spell.complexity());
        // the synergy is free
        assertEquals(Map.of("ignis", 8, "aqua", 6), spell.cost().asMap());
    }

    @Test
    void synergyEffectsCannotBePlacedDirectly() {
        CompiledSpell spell = compiler.compile(SpellNode.of("bolt", List.of("steam_burst")), SpellLimits.TIER_2);
        assertFalse(spell.isValid());
    }

    @Test
    void childrenAddCostComplexityAndDepth() {
        SpellNode root = SpellNode.of("bolt", List.of("fire")).withChildren(List.of(SpellNode.of("cloud", List.of("frost"))));
        CompiledSpell spell = compiler.compile(root, SpellLimits.TIER_2);
        assertTrue(spell.isValid(), spell.errors().toString());
        // fire: 8 ignis; frost: 4 gelum = 4 ignis + 4 perditio, carried by cloud x1.5 => 6 + 6
        assertEquals(Map.of("ignis", 14, "perditio", 6), spell.cost().asMap());
        assertEquals(2 + 2 + 4 + 2 + 1, spell.complexity());
        assertFalse(compiler.compile(root, SpellLimits.TIER_1).isValid());
    }

    @Test
    void limitsAndUnknownComponents() {
        CompiledSpell tooMany = compiler.compile(SpellNode.of("bolt", List.of("fire", "water", "frost")), SpellLimits.TIER_1);
        assertFalse(tooMany.isValid());
        CompiledSpell unknown = compiler.compile(SpellNode.of("bolt", List.of("nope")), SpellLimits.TIER_3);
        assertFalse(unknown.isValid());
        CompiledSpell wrongKind = compiler.compile(SpellNode.of("fire", List.of("fire")), SpellLimits.TIER_3);
        assertFalse(wrongKind.isValid());
        CompiledSpell empty = compiler.compile(SpellNode.of("bolt", List.of()), SpellLimits.TIER_3);
        assertFalse(empty.isValid());
    }

    @Test
    void forbiddenComponentsCarryWarpAndCostScaleApplies() {
        SpellCompiler expensive = new SpellCompiler(LIBRARY, ASPECTS, 2.0);
        CompiledSpell spell = expensive.compile(SpellNode.of("bolt", List.of("void_lance")), SpellLimits.TIER_3);
        assertEquals(2, spell.warp());
        assertEquals(Map.of("perditio", 20), spell.cost().asMap());
    }

    @Test
    void efficiencyLowersCost() {
        CompiledSpell spell = compiler.compile(SpellNode.of("touch", List.of("fire")).withModifiers(List.of("efficiency")), SpellLimits.TIER_1);
        assertEquals(6, spell.cooldown());
        assertEquals(Map.of("ignis", 3), spell.cost().asMap()); // 8 * 0.5 * 0.7 = 2.8 -> 3
    }
}
