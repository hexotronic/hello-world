package dev.thaumicunity.common.spell;

import dev.thaumicunity.common.registry.TUParticles;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Server-side particle helpers for spells; the client's particle quality setting scales what is actually drawn. */
public final class SpellFx {
    private SpellFx() {
    }

    public static ParticleOptions spark(int color) {
        return ColorParticleOption.create(TUParticles.SPARK.get(), color);
    }

    /** Burst of the component's particle at an impact point. */
    public static void impact(ServerLevel level, Vec3 at, int color, SpellVisual visual, float density) {
        ParticleOptions options = visual.particle()
                .map(id -> BuiltInRegistries.PARTICLE_TYPE.getValue(id))
                .filter(type -> type instanceof ParticleOptions)
                .map(type -> (ParticleOptions) type)
                .orElseGet(() -> spark(color));
        int count = Math.max(1, Math.round(14 * density));
        level.sendParticles(options, at.x, at.y, at.z, count, 0.25, 0.25, 0.25, 0.05);
    }

    /** Line of sparks, used for bolts, beams and chains. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, int color) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        int steps = (int) Math.min(96, Math.max(2, length * 3));
        ParticleOptions options = spark(color);
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(delta.scale(i / (double) steps));
            level.sendParticles(options, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    /** Ring of sparks around a centre, used for areas. */
    public static void ring(ServerLevel level, Vec3 center, double radius, int color) {
        ParticleOptions options = spark(color);
        int points = (int) Math.min(72, Math.max(8, radius * 10));
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(options, center.x + Math.cos(a) * radius, center.y + 0.1, center.z + Math.sin(a) * radius,
                    1, 0, 0.05, 0, 0.0);
        }
    }
}
