package dev.thaumicunity.common.registry;

import com.mojang.serialization.MapCodec;
import dev.thaumicunity.ThaumicUnity;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Coloured particle types used by spells, essentia and the aura. */
public final class TUParticles {
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, ThaumicUnity.MOD_ID);

    /** Small glowing spark, the default spell particle. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> SPARK = PARTICLES.register("spark", TUParticles::colored);
    /** Soft drifting mote used by wisps, nodes and the aura. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> WISP = PARTICLES.register("wisp", TUParticles::colored);
    /** Bubble of essentia rising from crucibles and jars. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> ESSENTIA = PARTICLES.register("essentia", TUParticles::colored);
    /** Rotating rune glyph used by infusion, runes and research. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> RUNE = PARTICLES.register("rune", TUParticles::colored);

    private TUParticles() {
    }

    private static ParticleType<ColorParticleOption> colored() {
        return new ParticleType<>(false) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        };
    }

    public static void init(IEventBus modBus) {
        PARTICLES.register(modBus);
    }
}
