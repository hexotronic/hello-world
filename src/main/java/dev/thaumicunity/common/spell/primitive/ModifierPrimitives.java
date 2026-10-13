package dev.thaumicunity.common.spell.primitive;

import dev.thaumicunity.api.spell.SpellPrimitive;
import dev.thaumicunity.common.spell.SpellFx;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Modifier primitives with behaviour beyond their numbers (docs/SPELLS.md, "Modifiers"). */
public final class ModifierPrimitives {
    private ModifierPrimitives() {
    }

    /**
     * Echo: the node's effects resolve again after a delay at the same spot. Params: delay (ticks), times, decay
     * (power multiplier per echo). The scheduling is done by the spell runner, which recognises this primitive.
     */
    public static final SpellPrimitive.Modifier ECHO = (ctx, target, params, power) -> {
    };

    /** Vampiric: heals the caster a little for every creature the node resolves on. Params: amount. */
    public static final SpellPrimitive.Modifier VAMPIRIC = (ctx, target, params, power) -> {
        if (target.entity() instanceof LivingEntity && ctx.caster() != null && target.entity() != ctx.caster()) {
            ctx.caster().heal((float) (params.getDouble("amount", 1.0) * power));
        }
    };

    /** Gravity well: drags nearby creatures towards the resolution point. Params: radius, strength. */
    public static final SpellPrimitive.Modifier GRAVITY_WELL = (ctx, target, params, power) -> {
        double r = params.getDouble("radius", 4.0);
        Vec3 c = target.position();
        ctx.level().getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(c, c).inflate(r),
                e -> e != ctx.caster()).forEach(e -> {
            Vec3 pull = c.subtract(e.position()).normalize().scale(params.getDouble("strength", 0.6));
            e.push(pull);
            e.needsSync = true;
        });
        SpellFx.ring(ctx.level(), c, r, ctx.color());
    };

    /** Marks targets with glowing so allies can see them. Params: duration. */
    public static final SpellPrimitive.Modifier REVEAL = (ctx, target, params, power) -> {
        if (target.entity() instanceof LivingEntity living) {
            living.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,
                    params.getInt("duration", 160)), ctx.caster());
        }
    };
}
