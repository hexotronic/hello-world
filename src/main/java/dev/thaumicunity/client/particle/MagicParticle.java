package dev.thaumicunity.client.particle;

import dev.thaumicunity.common.config.TUClientConfig;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.util.RandomSource;

/**
 * Glowing, coloured, fading particle used for sparks, wisps, essentia bubbles and runes. Rendered full-bright and
 * translucent; animates through its sprite set over its lifetime.
 */
public class MagicParticle extends SingleQuadParticle {
    private final SpriteSet sprites;
    private final float startAlpha;
    private final float spin;

    protected MagicParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites,
                            ColorParticleOption color, Style style) {
        super(level, x, y, z, dx, dy, dz, sprites.first());
        this.sprites = sprites;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.hasPhysics = false;
        this.gravity = style.gravity;
        this.friction = style.friction;
        this.quadSize = style.size * (0.75f + random.nextFloat() * 0.5f);
        this.lifetime = style.minLife + random.nextInt(Math.max(1, style.extraLife));
        this.spin = style.spin ? (random.nextFloat() - 0.5f) * 0.3f : 0f;
        setColor(color.getRed(), color.getGreen(), color.getBlue());
        this.startAlpha = style.alpha;
        this.alpha = style.alpha;
        setSpriteFromAge(sprites);
    }

    @Override
    protected Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!isAlive()) {
            return;
        }
        setSpriteFromAge(sprites);
        float life = age / (float) lifetime;
        alpha = startAlpha * (life < 0.2f ? life / 0.2f : 1.0f - Math.max(0, (life - 0.6f) / 0.4f));
        oRoll = roll;
        roll += spin;
    }

    /** Behaviour presets for the four particle types. */
    public enum Style {
        SPARK(0.08f, 0.0f, 0.96f, 6, 8, 0.9f, false),
        WISP(0.16f, -0.002f, 0.98f, 20, 20, 0.6f, false),
        ESSENTIA(0.10f, -0.01f, 0.95f, 12, 10, 0.85f, false),
        RUNE(0.18f, 0.0f, 0.9f, 16, 8, 0.9f, true);

        final float size;
        final float gravity;
        final float friction;
        final int minLife;
        final int extraLife;
        final float alpha;
        final boolean spin;

        Style(float size, float gravity, float friction, int minLife, int extraLife, float alpha, boolean spin) {
            this.size = size;
            this.gravity = gravity;
            this.friction = friction;
            this.minLife = minLife;
            this.extraLife = extraLife;
            this.alpha = alpha;
            this.spin = spin;
        }
    }

    /** Provider honouring the client particle quality setting. */
    public record Provider(SpriteSet sprites, Style style) implements ParticleProvider<ColorParticleOption> {
        @Override
        public Particle createParticle(ColorParticleOption options, ClientLevel level, double x, double y, double z, double dx,
                                       double dy, double dz, RandomSource random) {
            if (random.nextDouble() > TUClientConfig.PARTICLE_QUALITY.get().multiplier) {
                return null;
            }
            return new MagicParticle(level, x, y, z, dx, dy, dz, sprites, options, style);
        }
    }
}
