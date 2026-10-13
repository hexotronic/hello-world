package dev.thaumicunity.common.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * How a spell component looks and sounds.
 *
 * @param color    main colour; falls back to the component's aspect colour when absent in JSON
 * @param particle particle type id (defaults to the mod's spark particle)
 * @param sound    sound event id played where the component resolves
 * @param density  particle density multiplier (client particle quality scales it further)
 */
public record SpellVisual(Optional<Integer> color, Optional<Identifier> particle, Optional<Identifier> sound, float density) {
    public static final SpellVisual DEFAULT = new SpellVisual(Optional.empty(), Optional.empty(), Optional.empty(), 1.0f);

    public static final Codec<SpellVisual> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("color").forGetter(SpellVisual::color),
            Identifier.CODEC.optionalFieldOf("particle").forGetter(SpellVisual::particle),
            Identifier.CODEC.optionalFieldOf("sound").forGetter(SpellVisual::sound),
            Codec.floatRange(0f, 8f).optionalFieldOf("density", 1.0f).forGetter(SpellVisual::density)
    ).apply(i, SpellVisual::new));
}
