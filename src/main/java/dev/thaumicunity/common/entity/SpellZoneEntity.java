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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Lingering spell area used by the zone medium: clouds that pulse, runes and mines that wait for a creature, delayed
 * blasts, and auras that follow their source. Not saved to disk.
 */
public class SpellZoneEntity extends Entity {
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> TRIGGER = SynchedEntityData.defineId(SpellZoneEntity.class, EntityDataSerializers.INT);

    public enum Trigger {
        /** Resolves on everything inside every {@code interval} ticks. */
        PULSE,
        /** Waits until a creature (other than the caster) enters, then resolves once and vanishes (rune, mine). */
        PROXIMITY,
        /** Resolves once when its duration runs out (delayed blast). */
        DELAY;

        public static Trigger byName(String name) {
            try {
                return valueOf(name.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                return PULSE;
            }
        }
    }

    private CompiledSpell.@Nullable CompiledNode node;
    private @Nullable LivingEntity caster;
    private @Nullable Entity follow;
    private double power = 1.0;
    private int depth;
    private int duration = 100;
    private int interval = 20;

    public SpellZoneEntity(EntityType<? extends SpellZoneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static SpellZoneEntity create(ServerLevel level, @Nullable LivingEntity caster, SpellRunner.PendingResolution pending,
                                         int color, float radius, int duration, int interval, Trigger trigger,
                                         @Nullable Entity follow) {
        SpellZoneEntity zone = new SpellZoneEntity(TUEntities.SPELL_ZONE.get(), level);
        zone.node = pending.node();
        zone.power = pending.power();
        zone.depth = pending.depth();
        zone.caster = caster;
        zone.follow = follow;
        zone.duration = Math.max(1, duration);
        zone.interval = Math.max(1, interval);
        zone.getEntityData().set(COLOR, color);
        zone.getEntityData().set(RADIUS, Math.max(0.5f, radius));
        zone.getEntityData().set(TRIGGER, trigger.ordinal());
        return zone;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, 0xB78CFF);
        builder.define(RADIUS, 2.5f);
        builder.define(TRIGGER, 0);
    }

    public int color() {
        return getEntityData().get(COLOR);
    }

    public float radius() {
        return getEntityData().get(RADIUS);
    }

    public Trigger trigger() {
        return Trigger.values()[Math.floorMod(getEntityData().get(TRIGGER), Trigger.values().length)];
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide()) {
            spawnClientParticles();
            return;
        }
        if (node == null || tickCount > duration) {
            if (node != null && trigger() == Trigger.DELAY) {
                resolveAll();
            }
            discard();
            return;
        }
        if (follow != null) {
            if (follow.isRemoved()) {
                discard();
                return;
            }
            setPos(follow.position());
        }
        switch (trigger()) {
            case PULSE -> {
                if (tickCount % interval == 0) {
                    resolveAll();
                }
            }
            case PROXIMITY -> {
                if (!targets().isEmpty()) {
                    resolveAll();
                    discard();
                }
            }
            case DELAY -> {
            }
        }
    }

    private List<LivingEntity> targets() {
        Vec3 c = position();
        float r = radius();
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e.isAlive() && e != caster && e.position().distanceTo(c) <= r);
    }

    private void resolveAll() {
        if (!(level() instanceof ServerLevel server) || node == null) {
            return;
        }
        for (LivingEntity e : targets()) {
            SpellRunner.resolve(server, caster, node, SpellTarget.ofEntity(e), new Vec3(0, 1, 0), power, depth);
        }
        SpellRunner.resolve(server, caster, node, SpellTarget.ofPosition(position()), new Vec3(0, 1, 0), power, depth);
    }

    private void spawnClientParticles() {
        float r = radius();
        int points = trigger() == Trigger.PROXIMITY ? 3 : 6;
        for (int i = 0; i < points; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double d = trigger() == Trigger.PROXIMITY ? r * 0.6 : Math.sqrt(random.nextDouble()) * r;
            level().addParticle(ColorParticleOption.create(TUParticles.SPARK.get(), color()),
                    getX() + Math.cos(a) * d, getY() + 0.1 + random.nextDouble() * (trigger() == Trigger.PULSE ? 1.2 : 0.2),
                    getZ() + Math.sin(a) * d, 0, 0.01, 0);
        }
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        // Spell zones are never saved (the entity type is registered with noSave()).
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        // Spell zones are never saved.
    }
}
