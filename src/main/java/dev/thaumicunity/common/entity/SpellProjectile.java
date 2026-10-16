package dev.thaumicunity.common.entity;

import dev.thaumicunity.api.spell.SpellTarget;
import dev.thaumicunity.common.registry.TUEntities;
import dev.thaumicunity.common.registry.TUParticles;
import dev.thaumicunity.common.spell.SpellRunner;
import dev.thaumicunity.core.spell.CompiledSpell;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Projectile medium. It carries the compiled node it delivers and resolves it on impact. Not saved to disk: spells in
 * flight simply vanish when their chunk unloads. Drawn entirely with particles (see the client tick).
 */
public class SpellProjectile extends ThrowableProjectile {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SpellProjectile.class, EntityDataSerializers.INT);

    private CompiledSpell.@Nullable CompiledNode node;
    private double power = 1.0;
    private int depth;
    private double gravity;
    private double homing;
    private int lifetime = 100;
    private int pierce;
    private final Set<Integer> pierced = new HashSet<>();

    public SpellProjectile(EntityType<? extends SpellProjectile> type, Level level) {
        super(type, level);
    }

    public static SpellProjectile create(ServerLevel level, @Nullable LivingEntity caster, SpellRunner.PendingResolution pending,
                                         int color, double gravity, double homing, int lifetime, int pierce) {
        SpellProjectile projectile = new SpellProjectile(TUEntities.SPELL_PROJECTILE.get(), level);
        if (caster != null) {
            projectile.setOwner(caster);
        }
        projectile.node = pending.node();
        projectile.power = pending.power();
        projectile.depth = pending.depth();
        projectile.gravity = gravity;
        projectile.homing = Math.max(0, Math.min(1, homing));
        projectile.lifetime = Math.max(1, lifetime);
        projectile.pierce = Math.max(0, pierce);
        projectile.getEntityData().set(COLOR, color);
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xB78CFF);
    }

    public int color() {
        return getEntityData().get(COLOR);
    }

    @Override
    protected double getDefaultGravity() {
        return gravity;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            int c = color();
            Vec3 motion = getDeltaMovement();
            for (int i = 0; i < 3; i++) {
                double f = i / 3.0;
                level().addParticle(ColorParticleOption.create(TUParticles.SPARK.get(), c),
                        getX() - motion.x * f, getY() - motion.y * f, getZ() - motion.z * f,
                        (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02, (random.nextDouble() - 0.5) * 0.02);
            }
            return;
        }
        if (tickCount > lifetime || node == null) {
            discard();
            return;
        }
        if (homing > 0) {
            steer();
        }
    }

    private void steer() {
        Vec3 pos = position();
        Entity owner = getOwner();
        LivingEntity target = level().getEntitiesOfClass(LivingEntity.class, new AABB(pos, pos).inflate(12),
                        e -> e.isAlive() && e != owner && !pierced.contains(e.getId()))
                .stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(pos))).orElse(null);
        if (target != null) {
            Vec3 motion = getDeltaMovement();
            Vec3 wanted = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(pos).normalize().scale(motion.length());
            setDeltaMovement(motion.lerp(wanted, homing * 0.25));
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!(level() instanceof ServerLevel server) || node == null) {
            return;
        }
        Entity hit = result.getEntity();
        if (!pierced.add(hit.getId())) {
            return;
        }
        SpellRunner.resolve(server, ownerAsLiving(), node, SpellTarget.ofEntity(hit), getDeltaMovement().normalize(), power, depth);
        if (pierced.size() > pierce) {
            discard();
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!(level() instanceof ServerLevel server) || node == null) {
            return;
        }
        SpellRunner.resolve(server, ownerAsLiving(), node,
                SpellTarget.ofBlock(result.getBlockPos(), result.getDirection(), result.getLocation()),
                getDeltaMovement().normalize(), power, depth);
        discard();
    }

    private @Nullable LivingEntity ownerAsLiving() {
        return getOwner() instanceof LivingEntity living ? living : null;
    }
}
