package dev.thaumicunity.core.spell;

import dev.thaumicunity.core.aspect.AspectAmounts;
import dev.thaumicunity.core.aspect.AspectGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turns a {@link SpellNode} tree into a {@link CompiledSpell}. The balancing rules (docs/SPELLS.md, "Cost model"):
 *
 * <ol>
 *   <li>An effect's base cost is a set of aspect amounts, paid in the primal breakdown of those aspects.</li>
 *   <li>A node costs the sum of its effects × the medium's cost multiplier × the product of its modifiers' cost
 *   multipliers. Synergy effects that emerge from a node are free but add one complexity each.</li>
 *   <li>Complexity is the sum of every component's complexity, plus one per child link.</li>
 *   <li>Cooldown is the sum of medium and effect cooldowns of every node, scaled by that node's modifiers, clamped
 *   to [{@value #MIN_COOLDOWN}, {@value #MAX_COOLDOWN}] ticks.</li>
 *   <li>Warp is the sum of the warp of every component used.</li>
 * </ol>
 * Costs are finally multiplied by a global {@code costScale} (config) and rounded up per primal.
 */
public final class SpellCompiler {
    public static final int MIN_COOLDOWN = 2;
    public static final int MAX_COOLDOWN = 1200;

    private final SpellComponentLibrary library;
    private final AspectGraph aspects;
    private final double costScale;

    public SpellCompiler(SpellComponentLibrary library, AspectGraph aspects, double costScale) {
        if (costScale <= 0) {
            throw new IllegalArgumentException("costScale must be positive");
        }
        this.library = library;
        this.aspects = aspects;
        this.costScale = costScale;
    }

    public CompiledSpell compile(SpellNode root, SpellLimits limits) {
        Accumulator acc = new Accumulator();
        CompiledSpell.CompiledNode compiled = compileNode(root, 1, limits, acc);
        if (acc.complexity > limits.maxComplexity()) {
            acc.errors.add("Complexity " + acc.complexity + " exceeds the focus limit of " + limits.maxComplexity());
        }
        AspectAmounts.Builder cost = AspectAmounts.builder();
        acc.cost.forEach((primal, amount) -> cost.add(primal, (int) Math.ceil(amount * costScale - 1.0e-9)));
        int cooldown = Math.max(MIN_COOLDOWN, Math.min(MAX_COOLDOWN, (int) Math.round(acc.cooldown)));
        return new CompiledSpell(compiled, cost.build(), acc.complexity, cooldown, acc.warp, acc.errors);
    }

    private CompiledSpell.CompiledNode compileNode(SpellNode node, int depth, SpellLimits limits, Accumulator acc) {
        if (depth > limits.maxDepth()) {
            acc.errors.add("Spell is nested " + depth + " levels deep; this focus allows " + limits.maxDepth());
        }
        Optional<SpellComponentSpec> mediumSpec = expect(node.medium(), ComponentKind.MEDIUM, acc);
        if (node.effects().isEmpty() && node.children().isEmpty()) {
            acc.errors.add("Medium " + node.medium() + " carries no effect");
        }
        if (node.effects().size() > limits.maxEffectsPerNode()) {
            acc.errors.add("Medium " + node.medium() + " carries " + node.effects().size() + " effects; the limit is "
                    + limits.maxEffectsPerNode());
        }
        if (node.modifiers().size() > limits.maxModifiersPerNode()) {
            acc.errors.add("Medium " + node.medium() + " has " + node.modifiers().size() + " modifiers; the limit is "
                    + limits.maxModifiersPerNode());
        }
        if (node.children().size() > limits.maxChildrenPerNode()) {
            acc.errors.add("Medium " + node.medium() + " releases " + node.children().size()
                    + " follow-up mediums; the limit is " + limits.maxChildrenPerNode());
        }

        double nodeCooldown = 0;
        Map<String, Double> nodeCost = new TreeMap<>();
        List<String> effectAspects = new ArrayList<>();
        for (String effectId : node.effects()) {
            Optional<SpellComponentSpec> effect = expect(effectId, ComponentKind.EFFECT, acc);
            if (effect.isEmpty()) {
                continue;
            }
            SpellComponentSpec spec = effect.get();
            if (spec.synergyOf().isPresent()) {
                acc.errors.add("Effect " + effectId + " only emerges from combining "
                        + spec.synergyOf().get() + "'s components and cannot be placed directly");
            }
            acc.add(spec);
            nodeCooldown += spec.cooldown();
            AspectAmounts.of(spec.cost()).toPrimals(aspects).forEach((primal, amount) -> nodeCost.merge(primal, (double) amount, Double::sum));
            spec.aspect().ifPresent(effectAspects::add);
        }

        TreeSet<String> emergent = new TreeSet<>();
        for (int i = 0; i < effectAspects.size(); i++) {
            for (int j = i + 1; j < effectAspects.size(); j++) {
                aspects.combine(effectAspects.get(i), effectAspects.get(j))
                        .ifPresent(compound -> emergent.addAll(library.synergiesFor(compound)));
            }
        }
        for (String synergy : emergent) {
            library.find(synergy).ifPresent(spec -> acc.warp += spec.warp());
            acc.complexity += 1;
        }

        ModifierOps ops = ModifierOps.IDENTITY;
        for (String modifierId : node.modifiers()) {
            Optional<SpellComponentSpec> modifier = expect(modifierId, ComponentKind.MODIFIER, acc);
            if (modifier.isEmpty()) {
                continue;
            }
            SpellComponentSpec spec = modifier.get();
            if (mediumSpec.isPresent() && !spec.appliesTo().isEmpty()
                    && Collections.disjoint(spec.appliesTo(), mediumSpec.get().tags())) {
                acc.errors.add("Modifier " + modifierId + " cannot be attached to medium " + node.medium());
            }
            acc.add(spec);
            ops = ops.compose(spec.ops());
        }

        double mediumMultiplier = 1.0;
        if (mediumSpec.isPresent()) {
            acc.add(mediumSpec.get());
            nodeCooldown += mediumSpec.get().cooldown();
            mediumMultiplier = mediumSpec.get().costMultiplier();
        }
        double multiplier = mediumMultiplier * ops.cost();
        nodeCost.forEach((primal, amount) -> acc.cost.merge(primal, amount * multiplier, Double::sum));
        acc.cooldown += nodeCooldown * ops.cooldown();

        List<CompiledSpell.CompiledNode> children = new ArrayList<>();
        for (SpellNode child : node.children()) {
            acc.complexity += 1;
            children.add(compileNode(child, depth + 1, limits, acc));
        }
        return new CompiledSpell.CompiledNode(node.medium(), node.effects(), List.copyOf(emergent), node.modifiers(), ops,
                children);
    }

    private Optional<SpellComponentSpec> expect(String id, ComponentKind kind, Accumulator acc) {
        Optional<SpellComponentSpec> spec = library.find(id);
        if (spec.isEmpty()) {
            acc.errors.add("Unknown spell component " + id);
            return Optional.empty();
        }
        if (spec.get().kind() != kind) {
            acc.errors.add("Component " + id + " is a " + spec.get().kind().serializedName() + ", expected a "
                    + kind.serializedName());
            return Optional.empty();
        }
        return spec;
    }

    private static final class Accumulator {
        final Map<String, Double> cost = new TreeMap<>();
        final List<String> errors = new ArrayList<>();
        int complexity;
        double cooldown;
        int warp;

        void add(SpellComponentSpec spec) {
            complexity += spec.complexity();
            warp += spec.warp();
        }
    }
}
