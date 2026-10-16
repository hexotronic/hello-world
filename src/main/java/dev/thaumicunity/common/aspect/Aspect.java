package dev.thaumicunity.common.aspect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.ThaumicUnity;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.List;

/**
 * Value of the {@code thaumicunity:aspect} datapack registry. The registry entry's id is the aspect's identity;
 * this record only holds what the data file says about it.
 *
 * @param color      display colour (RGB)
 * @param components empty for primal aspects, otherwise the two aspects it is combined from
 * @param origin     provenance label ({@code tc4}, {@code tc6}, {@code addon:<mod>}, {@code tu}, {@code legacy});
 *                   informational, shown in advanced tooltips
 */
public record Aspect(int color, List<Identifier> components, String origin) {
    public static final Codec<Aspect> CODEC = RecordCodecBuilder.create(i -> i.group(
            ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(Aspect::color),
            Identifier.CODEC.listOf().optionalFieldOf("components", List.of()).forGetter(Aspect::components),
            Codec.STRING.optionalFieldOf("origin", "custom").forGetter(Aspect::origin)
    ).apply(i, Aspect::new));

    public boolean isPrimal() {
        return components.isEmpty();
    }

    /** Translation key of an aspect's name, e.g. {@code aspect.thaumicunity.ignis}. */
    public static String translationKey(Identifier id) {
        return "aspect." + id.getNamespace() + "." + id.getPath();
    }

    /** Coloured display name of the aspect with the given id. */
    public static MutableComponent displayName(Identifier id, int color) {
        return Component.translatable(translationKey(id)).withColor(color);
    }

    /** Short id used in commands and logs: the path for our namespace, the full id otherwise. */
    public static String shortId(Identifier id) {
        return ThaumicUnity.MOD_ID.equals(id.getNamespace()) ? id.getPath() : id.toString();
    }
}
