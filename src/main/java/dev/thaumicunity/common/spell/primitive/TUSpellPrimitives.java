package dev.thaumicunity.common.spell.primitive;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.api.spell.SpellPrimitive;
import dev.thaumicunity.common.registry.TURegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the built-in spell primitives under {@code thaumicunity:<name>}. */
public final class TUSpellPrimitives {
    private static final DeferredRegister<SpellPrimitive> PRIMITIVES =
            DeferredRegister.create(TURegistries.SPELL_PRIMITIVE_KEY, ThaumicUnity.MOD_ID);

    static {
        // mediums
        PRIMITIVES.register("touch", () -> MediumPrimitives.TOUCH);
        PRIMITIVES.register("bolt", () -> MediumPrimitives.BOLT);
        PRIMITIVES.register("beam", () -> MediumPrimitives.BEAM);
        PRIMITIVES.register("chain", () -> MediumPrimitives.CHAIN);
        PRIMITIVES.register("self", () -> MediumPrimitives.SELF);
        PRIMITIVES.register("burst", () -> MediumPrimitives.BURST);
        PRIMITIVES.register("wave", () -> MediumPrimitives.WAVE);
        PRIMITIVES.register("ground_wave", () -> MediumPrimitives.GROUND_WAVE);
        PRIMITIVES.register("plan", () -> MediumPrimitives.PLAN);
        PRIMITIVES.register("projectile", () -> MediumPrimitives.PROJECTILE);
        PRIMITIVES.register("zone", () -> MediumPrimitives.ZONE);
        PRIMITIVES.register("targeted_zone", () -> MediumPrimitives.TARGETED_ZONE);
        // effects
        PRIMITIVES.register("damage", () -> EffectPrimitives.DAMAGE);
        PRIMITIVES.register("drain", () -> EffectPrimitives.DRAIN);
        PRIMITIVES.register("potion", () -> EffectPrimitives.POTION);
        PRIMITIVES.register("heal", () -> EffectPrimitives.HEAL);
        PRIMITIVES.register("shield", () -> EffectPrimitives.SHIELD);
        PRIMITIVES.register("dispel", () -> EffectPrimitives.DISPEL);
        PRIMITIVES.register("ignite", () -> EffectPrimitives.IGNITE);
        PRIMITIVES.register("extinguish", () -> EffectPrimitives.EXTINGUISH);
        PRIMITIVES.register("freeze", () -> EffectPrimitives.FREEZE);
        PRIMITIVES.register("break", () -> EffectPrimitives.BREAK);
        PRIMITIVES.register("place", () -> EffectPrimitives.PLACE);
        PRIMITIVES.register("exchange", () -> EffectPrimitives.EXCHANGE);
        PRIMITIVES.register("phase", () -> EffectPrimitives.PHASE);
        PRIMITIVES.register("push", () -> EffectPrimitives.PUSH);
        PRIMITIVES.register("teleport", () -> EffectPrimitives.TELEPORT);
        PRIMITIVES.register("light", () -> EffectPrimitives.LIGHT);
        PRIMITIVES.register("lightning", () -> EffectPrimitives.LIGHTNING);
        PRIMITIVES.register("explosion", () -> EffectPrimitives.EXPLOSION);
        PRIMITIVES.register("grow", () -> EffectPrimitives.GROW);
        PRIMITIVES.register("smelt", () -> EffectPrimitives.SMELT);
        PRIMITIVES.register("transmute", () -> EffectPrimitives.TRANSMUTE);
        PRIMITIVES.register("summon", () -> EffectPrimitives.SUMMON);
        PRIMITIVES.register("experience", () -> EffectPrimitives.EXPERIENCE);
        PRIMITIVES.register("aura", () -> EffectPrimitives.AURA);
        PRIMITIVES.register("warp", () -> EffectPrimitives.WARP);
        PRIMITIVES.register("attract_items", () -> EffectPrimitives.ATTRACT_ITEMS);
        PRIMITIVES.register("illusion", () -> EffectPrimitives.ILLUSION);
        // modifiers
        PRIMITIVES.register("numeric", () -> SpellPrimitive.NUMERIC);
        PRIMITIVES.register("echo", () -> ModifierPrimitives.ECHO);
        PRIMITIVES.register("vampiric", () -> ModifierPrimitives.VAMPIRIC);
        PRIMITIVES.register("gravity_well", () -> ModifierPrimitives.GRAVITY_WELL);
        PRIMITIVES.register("reveal", () -> ModifierPrimitives.REVEAL);
    }

    private TUSpellPrimitives() {
    }

    public static void init(IEventBus modBus) {
        PRIMITIVES.register(modBus);
    }
}
