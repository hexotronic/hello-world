package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Value of the {@code thaumicunity:research_category} datapack registry: one tab of the Thaumonomicon.
 *
 * @param icon       item id shown on the tab
 * @param background texture drawn behind the research map ({@code textures/gui/research/<path>.png})
 * @param order      tab order, lower first
 * @param requires   research that must be complete before the tab appears
 * @param color      accent colour used for frames and connection lines
 */
public record ResearchCategory(Identifier icon, Identifier background, int order, Optional<Identifier> requires, int color) {
    public static final Codec<ResearchCategory> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("icon").forGetter(ResearchCategory::icon),
            Identifier.CODEC.fieldOf("background").forGetter(ResearchCategory::background),
            Codec.INT.optionalFieldOf("order", 100).forGetter(ResearchCategory::order),
            Identifier.CODEC.optionalFieldOf("requires").forGetter(ResearchCategory::requires),
            net.minecraft.util.ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("color", 0xC9A44C).forGetter(ResearchCategory::color)
    ).apply(i, ResearchCategory::new));

    public static String translationKey(Identifier id) {
        return "research_category." + id.getNamespace() + "." + id.getPath();
    }
}
