package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * One stage of a research entry: the text shown while the stage is current, what must be done to advance, and the
 * recipes revealed once the stage is reached.
 *
 * @param text         translation key of the stage text
 * @param requirements what must be done to complete this stage
 * @param recipes      recipe ids shown on this page of the entry
 */
public record ResearchStage(String text, List<ResearchRequirement> requirements, List<Identifier> recipes) {
    public static final Codec<ResearchStage> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("text").forGetter(ResearchStage::text),
            ResearchRequirement.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(ResearchStage::requirements),
            Identifier.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(ResearchStage::recipes)
    ).apply(i, ResearchStage::new));
}
