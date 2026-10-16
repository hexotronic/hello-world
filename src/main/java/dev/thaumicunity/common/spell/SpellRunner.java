package dev.thaumicunity.common.spell;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.api.spell.SpellContext;
import dev.thaumicunity.api.spell.SpellDelivery;
import dev.thaumicunity.api.spell.SpellPrimitive;
import dev.thaumicunity.api.spell.SpellTarget;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.spell.primitive.ModifierPrimitives;
import dev.thaumicunity.common.spell.primitive.SpellScheduler;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellLimits;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Executes compiled spells on the server: hands each node to its medium primitive and, wherever the medium
 * resolves, applies the node's effects and releases its child nodes.
 */
public final class SpellRunner {
    /** Hard cap on child nesting at runtime (protects against hand-edited foci). */
    private static final int MAX_RUNTIME_DEPTH = 8;

    private SpellRunner() {
    }

    /** Casts a whole spell from a living caster's eyes along its look direction. */
    public static void cast(ServerLevel level, LivingEntity caster, CompiledSpell spell, double instrumentPower) {
        if (spell.root() == null || !spell.isValid()) {
            return;
        }
        double power = instrumentPower * TUConfig.SPELL_POWER_SCALE.get();
        runNode(level, caster, spell.root(), caster.getEyePosition(), caster.getLookAngle(), caster, power, 0);
    }

    /** Delivers one node (its medium decides where it resolves). */
    public static void runNode(ServerLevel level, @Nullable LivingEntity caster, CompiledSpell.CompiledNode node, Vec3 origin,
                               Vec3 direction, @Nullable Entity source, double power, int depth) {
        if (depth > MAX_RUNTIME_DEPTH) {
            return;
        }
        SpellLibrary library = SpellLibrary.get(level.registryAccess());
        SpellComponent medium = library.component(node.medium()).orElse(null);
        if (medium == null) {
            return;
        }
        SpellPrimitive primitive = TURegistries.SPELL_PRIMITIVES.getValue(medium.primitive());
        if (!(primitive instanceof SpellPrimitive.Medium mediumPrimitive)) {
            ThaumicUnity.LOGGER.warn("Spell component {} uses {} which is not a medium primitive", node.medium(), medium.primitive());
            return;
        }
        Identifier mediumId = Identifier.parse(node.medium());
        Context context = new Context(level, caster, power, colorOf(level, medium), mediumId);
        Delivery delivery = new Delivery(level, caster, node, origin, direction.normalize(), source, power, depth);
        mediumPrimitive.deliver(context, medium.spellParams(), delivery);
        playSound(level, medium, origin);
    }

    /** Applies a node's effects at a target and releases its children (used by mediums and spell entities). */
    public static void resolve(ServerLevel level, @Nullable LivingEntity caster, CompiledSpell.CompiledNode node, SpellTarget target,
                               Vec3 direction, double power, int depth) {
        SpellLibrary library = SpellLibrary.get(level.registryAccess());
        double nodePower = power * node.ops().power();
        List<String> effects = new ArrayList<>(node.effects());
        effects.addAll(node.emergentEffects());
        for (String effectId : effects) {
            SpellComponent effect = library.component(effectId).orElse(null);
            if (effect == null) {
                continue;
            }
            SpellPrimitive primitive = TURegistries.SPELL_PRIMITIVES.getValue(effect.primitive());
            if (primitive instanceof SpellPrimitive.Effect effectPrimitive) {
                int color = colorOf(level, effect);
                Context context = new Context(level, caster, nodePower, color, Identifier.parse(effectId));
                effectPrimitive.apply(context, target, effect.spellParams(), nodePower);
                SpellFx.impact(level, target.position(), color, effect.visual(), effect.visual().density());
                playSound(level, effect, target.position());
            }
        }
        for (String modifierId : node.modifiers()) {
            SpellComponent modifier = library.component(modifierId).orElse(null);
            if (modifier == null) {
                continue;
            }
            SpellPrimitive primitive = TURegistries.SPELL_PRIMITIVES.getValue(modifier.primitive());
            if (primitive == ModifierPrimitives.ECHO) {
                scheduleEcho(level, caster, node, modifierId, modifier, target, direction, power, depth);
            } else if (primitive instanceof SpellPrimitive.Modifier modifierPrimitive && primitive != SpellPrimitive.NUMERIC) {
                Context context = new Context(level, caster, nodePower, colorOf(level, modifier), Identifier.parse(modifierId));
                modifierPrimitive.afterResolve(context, target, modifier.spellParams(), nodePower);
            }
        }
        for (CompiledSpell.CompiledNode child : node.children()) {
            Vec3 childDirection = direction;
            if (target.face() != null) {
                // Reflect the travel direction off the face that was hit so follow-ups bounce naturally.
                Vec3 normal = new Vec3(target.face().getStepX(), target.face().getStepY(), target.face().getStepZ());
                childDirection = direction.subtract(normal.scale(2 * direction.dot(normal)));
            }
            Vec3 childOrigin = target.position().add(childDirection.scale(0.05));
            runNode(level, caster, child, childOrigin, childDirection, target.entity(), power, depth + 1);
        }
    }

    private static void scheduleEcho(ServerLevel level, @Nullable LivingEntity caster, CompiledSpell.CompiledNode node,
                                     String echoId, SpellComponent echo, SpellTarget target, Vec3 direction, double power,
                                     int depth) {
        int delay = Math.max(1, echo.spellParams().getInt("delay", 20));
        int times = Math.max(1, Math.min(8, echo.spellParams().getInt("times", 1)));
        double decay = echo.spellParams().getDouble("decay", 0.75);
        List<String> remaining = new ArrayList<>(node.modifiers());
        remaining.remove(echoId);
        // Echoes repeat effects only: no children (so follow-up mediums are not multiplied) and no further echoes.
        CompiledSpell.CompiledNode replay = new CompiledSpell.CompiledNode(node.medium(), node.effects(), node.emergentEffects(),
                remaining, node.ops(), List.of());
        for (int i = 1; i <= times; i++) {
            double echoPower = power * Math.pow(decay, i);
            SpellScheduler.later(level, delay * i, l -> {
                if (caster == null || !caster.isRemoved()) {
                    resolve(l, caster, replay, target, direction, echoPower, depth + 1);
                }
            });
        }
    }

    /** Recompiles a stored node (spell entities keep the raw node so they survive data reloads). */
    public static CompiledSpell.@Nullable CompiledNode compileStored(ServerLevel level, SpellNode node) {
        CompiledSpell compiled = SpellLibrary.get(level.registryAccess()).compile(node, SpellLimits.UNBOUNDED);
        return compiled.root();
    }

    static int colorOf(ServerLevel level, SpellComponent component) {
        return component.visual().color().orElseGet(() ->
                component.aspect().map(a -> Aspects.color(level.registryAccess(), a)).orElse(0xB78CFF));
    }

    private static void playSound(ServerLevel level, SpellComponent component, Vec3 at) {
        component.visual().sound().ifPresent(id -> {
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getValue(id);
            if (sound != null) {
                level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, 0.8f, 0.9f + level.getRandom().nextFloat() * 0.2f);
            }
        });
    }

    private record Context(ServerLevel level, @Nullable LivingEntity caster, double powerScale, int color,
                           Identifier componentId) implements SpellContext {
        @Override
        public RandomSource random() {
            return level.getRandom();
        }

        @Override
        public boolean mayAlterWorld() {
            return TUConfig.SPELL_GRIEFING.get();
        }
    }

    private static final class Delivery implements SpellDelivery {
        private final ServerLevel level;
        private final @Nullable LivingEntity caster;
        private final CompiledSpell.CompiledNode node;
        private final Vec3 origin;
        private final Vec3 direction;
        private final @Nullable Entity source;
        private final double power;
        private final int depth;

        Delivery(ServerLevel level, @Nullable LivingEntity caster, CompiledSpell.CompiledNode node, Vec3 origin, Vec3 direction,
                 @Nullable Entity source, double power, int depth) {
            this.level = level;
            this.caster = caster;
            this.node = node;
            this.origin = origin;
            this.direction = direction;
            this.source = source;
            this.power = power;
            this.depth = depth;
        }

        @Override
        public Vec3 origin() {
            return origin;
        }

        @Override
        public Vec3 direction() {
            return direction;
        }

        @Override
        public @Nullable Entity source() {
            return source;
        }

        @Override
        public int branches() {
            return node.ops().branches();
        }

        @Override
        public double areaBonus() {
            return node.ops().area();
        }

        @Override
        public double durationMultiplier() {
            return node.ops().duration();
        }

        @Override
        public void resolve(SpellTarget target) {
            SpellRunner.resolve(level, caster, node, target, direction, power, depth);
        }

        /** Spell entities need the raw node and power to resolve later. */
        CompiledSpell.CompiledNode node() {
            return node;
        }

        double power() {
            return power;
        }

        int depth() {
            return depth;
        }
    }

    /** Access for medium primitives that spawn entities which resolve later. */
    public static @Nullable PendingResolution pending(SpellDelivery delivery) {
        if (delivery instanceof Delivery d) {
            return new PendingResolution(d.node(), d.power(), d.depth(), d.direction());
        }
        return null;
    }

    /** Everything a spell entity needs to resolve a node later. */
    public record PendingResolution(CompiledSpell.CompiledNode node, double power, int depth, Vec3 direction) {
    }
}
