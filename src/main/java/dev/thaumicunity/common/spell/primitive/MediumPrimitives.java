package dev.thaumicunity.common.spell.primitive;

import dev.thaumicunity.api.spell.SpellContext;
import dev.thaumicunity.api.spell.SpellDelivery;
import dev.thaumicunity.api.spell.SpellPrimitive;
import dev.thaumicunity.api.spell.SpellTarget;
import dev.thaumicunity.common.entity.SpellProjectile;
import dev.thaumicunity.common.entity.SpellZoneEntity;
import dev.thaumicunity.common.spell.SpellFx;
import dev.thaumicunity.common.spell.SpellRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in medium primitives. Each is configured through its component's {@code params}; see docs/SPELLS.md for the
 * parameter reference.
 */
public final class MediumPrimitives {
    private MediumPrimitives() {
    }

    /** Resolves on what the caster touches (block or entity within a few blocks). Params: range. */
    public static final SpellPrimitive.Medium TOUCH = (ctx, params, d) -> {
        double range = params.getDouble("range", 4.0) + d.areaBonus();
        SpellTarget target = raycast(ctx, d.source(), d.origin(), d.direction(), range);
        if (target != null) {
            for (int i = 0; i < d.branches(); i++) {
                d.resolve(target);
            }
        }
    };

    /** Instant ray. Params: range, spread (degrees between branches). */
    public static final SpellPrimitive.Medium BOLT = (ctx, params, d) -> {
        double range = params.getDouble("range", 32.0) + d.areaBonus() * 4;
        for (Vec3 dir : spread(d.direction(), d.branches(), params.getDouble("spread", 6.0), ctx)) {
            SpellTarget target = raycast(ctx, d.source(), d.origin(), dir, range);
            Vec3 end = target != null ? target.position() : d.origin().add(dir.scale(range));
            SpellFx.line(ctx.level(), d.origin().add(dir.scale(0.6)), end, ctx.color());
            if (target != null) {
                d.resolve(target);
            }
        }
    };

    /** Ray that passes through creatures. Params: range, pierce (max entities). */
    public static final SpellPrimitive.Medium BEAM = (ctx, params, d) -> {
        double range = params.getDouble("range", 24.0) + d.areaBonus() * 4;
        int pierce = params.getInt("pierce", 5);
        for (Vec3 dir : spread(d.direction(), d.branches(), params.getDouble("spread", 8.0), ctx)) {
            Vec3 end = d.origin().add(dir.scale(range));
            BlockHitResult block = ctx.level().clip(new ClipContext(d.origin(), end, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, sourceOrCaster(ctx, d)));
            Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            List<Entity> hits = entitiesAlong(ctx, d.source(), d.origin(), stop, 0.6);
            int count = 0;
            for (Entity e : hits) {
                if (count++ >= pierce) {
                    break;
                }
                d.resolve(SpellTarget.ofEntity(e));
            }
            if (block.getType() == HitResult.Type.BLOCK) {
                d.resolve(SpellTarget.ofBlock(block.getBlockPos(), block.getDirection(), block.getLocation()));
            }
            SpellFx.line(ctx.level(), d.origin().add(dir.scale(0.6)), stop, ctx.color());
        }
    };

    /** Hits the first creature in sight, then arcs to the nearest others. Params: range, jumps, jump_range. */
    public static final SpellPrimitive.Medium CHAIN = (ctx, params, d) -> {
        double range = params.getDouble("range", 16.0);
        int jumps = params.getInt("jumps", 3) + (d.branches() - 1) * 2;
        double jumpRange = params.getDouble("jump_range", 6.0) + d.areaBonus();
        SpellTarget first = raycast(ctx, d.source(), d.origin(), d.direction(), range);
        if (first == null) {
            return;
        }
        d.resolve(first);
        if (!(first.entity() instanceof LivingEntity current)) {
            return;
        }
        Set<Integer> visited = new HashSet<>();
        visited.add(current.getId());
        Vec3 from = first.position();
        for (int i = 0; i < jumps; i++) {
            Vec3 center = current.position();
            LivingEntity next = ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(jumpRange),
                            e -> e.isAlive() && !visited.contains(e.getId()) && e != ctx.caster())
                    .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(center))).orElse(null);
            if (next == null) {
                break;
            }
            visited.add(next.getId());
            SpellTarget target = SpellTarget.ofEntity(next);
            SpellFx.line(ctx.level(), from, target.position(), ctx.color());
            d.resolve(target);
            from = target.position();
            current = next;
        }
    };

    /** Resolves on the caster (or the entity the previous node hit). */
    public static final SpellPrimitive.Medium SELF = (ctx, params, d) -> {
        Entity self = d.source() != null ? d.source() : ctx.caster();
        if (self != null) {
            for (int i = 0; i < d.branches(); i++) {
                d.resolve(SpellTarget.ofEntity(self));
            }
        }
    };

    /** Instant sphere around the origin. Params: radius, include_center, affect_caster. */
    public static final SpellPrimitive.Medium BURST = (ctx, params, d) -> {
        double radius = params.getDouble("radius", 3.0) + d.areaBonus();
        Vec3 center = d.origin();
        boolean affectCaster = params.getBoolean("affect_caster", false);
        for (Entity e : ctx.level().getEntities((Entity) null, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e.position().distanceTo(center) <= radius && (affectCaster || e != ctx.caster()))) {
            d.resolve(SpellTarget.ofEntity(e));
        }
        if (params.getBoolean("include_center", true)) {
            d.resolve(SpellTarget.ofPosition(center));
        }
        SpellFx.ring(ctx.level(), center, radius, ctx.color());
    };

    /** Cone in front of the origin. Params: range, angle (degrees). */
    public static final SpellPrimitive.Medium WAVE = (ctx, params, d) -> {
        double range = params.getDouble("range", 8.0) + d.areaBonus();
        double cos = Math.cos(Math.toRadians(params.getDouble("angle", 35.0)));
        Vec3 origin = d.origin();
        for (Entity e : ctx.level().getEntities((Entity) null, new AABB(origin, origin).inflate(range),
                e -> e.isAlive() && e != ctx.caster())) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(origin);
            double len = to.length();
            if (len <= range && len > 0 && to.scale(1 / len).dot(d.direction()) >= cos) {
                d.resolve(SpellTarget.ofEntity(e));
            }
        }
        for (int i = 1; i <= 6; i++) {
            SpellFx.ring(ctx.level(), origin.add(d.direction().scale(range * i / 6.0)), range * i / 6.0 * Math.tan(Math.acos(cos)) * 0.5, ctx.color());
        }
    };

    /** Shockwave rolling along the ground. Params: length, width. */
    public static final SpellPrimitive.Medium GROUND_WAVE = (ctx, params, d) -> {
        double length = params.getDouble("length", 10.0) + d.areaBonus() * 2;
        double width = params.getDouble("width", 1.5);
        Vec3 flat = new Vec3(d.direction().x, 0, d.direction().z);
        if (flat.lengthSqr() < 1.0e-4) {
            return;
        }
        flat = flat.normalize();
        Set<Integer> hit = new HashSet<>();
        Vec3 start = d.origin();
        for (double s = 1; s <= length; s += 1) {
            Vec3 p = start.add(flat.scale(s));
            BlockPos ground = BlockPos.containing(p.x, p.y - 1.5, p.z);
            for (int k = 0; k < 4 && ctx.level().getBlockState(ground).isAir(); k++) {
                ground = ground.below();
            }
            SpellFx.impact(ctx.level(), Vec3.atCenterOf(ground).add(0, 0.6, 0), ctx.color(),
                    dev.thaumicunity.common.spell.SpellVisual.DEFAULT, 0.3f);
            for (Entity e : ctx.level().getEntities((Entity) null, new AABB(p, p).inflate(width, 1.5, width),
                    e -> e.isAlive() && e != ctx.caster())) {
                if (hit.add(e.getId())) {
                    d.resolve(SpellTarget.ofEntity(e));
                }
            }
        }
    };

    /** Square of blocks on the face that was looked at (TC6 "plan"). Params: range, size (odd). */
    public static final SpellPrimitive.Medium PLAN = (ctx, params, d) -> {
        double range = params.getDouble("range", 16.0);
        int size = Math.max(1, params.getInt("size", 3) + (int) Math.round(d.areaBonus()) * 2);
        int half = size / 2;
        BlockHitResult hit = ctx.level().clip(new ClipContext(d.origin(), d.origin().add(d.direction().scale(range)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, sourceOrCaster(ctx, d)));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        Direction face = hit.getDirection();
        BlockPos center = hit.getBlockPos();
        for (int a = -half; a <= half; a++) {
            for (int b = -half; b <= half; b++) {
                BlockPos pos = switch (face.getAxis()) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    case Z -> center.offset(a, b, 0);
                };
                if (!ctx.level().getBlockState(pos).isAir()) {
                    d.resolve(SpellTarget.ofBlock(pos, face, Vec3.atCenterOf(pos)));
                }
            }
        }
    };

    /** Thrown magic projectile. Params: speed, gravity, spread, homing (0-1), lifetime, pierce. */
    public static final SpellPrimitive.Medium PROJECTILE = (ctx, params, d) -> {
        SpellRunner.PendingResolution pending = SpellRunner.pending(d);
        if (pending == null) {
            return;
        }
        double speed = params.getDouble("speed", 1.6);
        for (Vec3 dir : spread(d.direction(), d.branches(), params.getDouble("spread", 10.0), ctx)) {
            SpellProjectile projectile = SpellProjectile.create(ctx.level(), ctx.caster(), pending, ctx.color(),
                    params.getDouble("gravity", 0.0), params.getDouble("homing", 0.0), params.getInt("lifetime", 100),
                    params.getInt("pierce", 0));
            projectile.setPos(d.origin().add(dir.scale(0.5)));
            projectile.shoot(dir.x, dir.y, dir.z, (float) speed, 0.0f);
            ctx.level().addFreshEntity(projectile);
        }
    };

    /**
     * Lingering magic at the origin: clouds, zones, runes (proximity traps), mines, delayed blasts and caster auras.
     * Params: radius, duration (ticks), interval (ticks), trigger ("pulse", "proximity", "delay"), follow_source,
     * ground (snap to the block below).
     */
    public static final SpellPrimitive.Medium ZONE = (ctx, params, d) -> {
        SpellRunner.PendingResolution pending = SpellRunner.pending(d);
        if (pending == null) {
            return;
        }
        Vec3 at = d.origin();
        if (params.getBoolean("ground", false)) {
            BlockHitResult down = ctx.level().clip(new ClipContext(at, at.add(0, -8, 0), ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, sourceOrCaster(ctx, d)));
            if (down.getType() == HitResult.Type.BLOCK) {
                at = down.getLocation().add(0, 0.05, 0);
            }
        }
        SpellZoneEntity zone = SpellZoneEntity.create(ctx.level(), ctx.caster(), pending, ctx.color(),
                (float) (params.getDouble("radius", 2.5) + d.areaBonus()),
                (int) Math.round(params.getInt("duration", 100) * d.durationMultiplier()),
                Math.max(1, params.getInt("interval", 20)),
                SpellZoneEntity.Trigger.byName(params.getString("trigger", "pulse")),
                params.getBoolean("follow_source", false) ? d.source() : null);
        zone.setPos(at);
        ctx.level().addFreshEntity(zone);
    };

    /**
     * Ranged variant of {@link #ZONE}: first finds where the caster is looking (like a bolt), then places the zone
     * there. Params: range plus all zone params.
     */
    public static final SpellPrimitive.Medium TARGETED_ZONE = (ctx, params, d) -> {
        double range = params.getDouble("range", 24.0);
        SpellTarget target = raycast(ctx, d.source(), d.origin(), d.direction(), range);
        Vec3 at = target != null ? target.position() : d.origin().add(d.direction().scale(range));
        ZONE.deliver(ctx, params, new ForwardedDelivery(d, at));
    };

    // --- helpers

    static Entity sourceOrCaster(SpellContext ctx, SpellDelivery d) {
        return d.source() != null ? d.source() : ctx.caster();
    }

    /** First entity or block along a ray, or null. */
    public static SpellTarget raycast(SpellContext ctx, Entity source, Vec3 from, Vec3 dir, double range) {
        ServerLevel level = ctx.level();
        Vec3 to = from.add(dir.scale(range));
        Entity ignore = source != null ? source : ctx.caster();
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, ignore));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        List<Entity> entities = entitiesAlong(ctx, ignore, from, stop, 0.3);
        if (!entities.isEmpty()) {
            return SpellTarget.ofEntity(entities.get(0));
        }
        if (block.getType() == HitResult.Type.BLOCK) {
            return SpellTarget.ofBlock(block.getBlockPos(), block.getDirection(), block.getLocation());
        }
        return null;
    }

    /** Entities whose inflated bounding box the segment passes through, nearest first. */
    static List<Entity> entitiesAlong(SpellContext ctx, Entity ignore, Vec3 from, Vec3 to, double margin) {
        AABB sweep = new AABB(from, to).inflate(margin + 1.0);
        List<Entity> result = new ArrayList<>();
        for (Entity e : ctx.level().getEntities(ignore, sweep, e -> e.isAlive() && e.isPickable() && e != ctx.caster())) {
            if (e.getBoundingBox().inflate(margin).clip(from, to).isPresent()) {
                result.add(e);
            }
        }
        result.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        return result;
    }

    /** Directions for split/scatter: the first is the original, the rest are spread around it. */
    static List<Vec3> spread(Vec3 dir, int branches, double degrees, SpellContext ctx) {
        List<Vec3> result = new ArrayList<>(branches);
        result.add(dir);
        for (int i = 1; i < branches; i++) {
            double yaw = Math.toRadians((ctx.random().nextDouble() - 0.5) * 2 * degrees);
            double pitch = Math.toRadians((ctx.random().nextDouble() - 0.5) * 2 * degrees);
            result.add(dir.yRot((float) yaw).xRot((float) pitch).normalize());
        }
        return result;
    }

    /** Delivery wrapper that moves the origin (used by targeted zones). */
    record ForwardedDelivery(SpellDelivery inner, Vec3 origin) implements SpellDelivery {
        @Override
        public Vec3 direction() {
            return inner.direction();
        }

        @Override
        public Entity source() {
            return inner.source();
        }

        @Override
        public int branches() {
            return inner.branches();
        }

        @Override
        public double areaBonus() {
            return inner.areaBonus();
        }

        @Override
        public double durationMultiplier() {
            return inner.durationMultiplier();
        }

        @Override
        public void resolve(SpellTarget target) {
            inner.resolve(target);
        }
    }
}
