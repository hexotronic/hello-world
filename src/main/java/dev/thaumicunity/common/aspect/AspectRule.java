package dev.thaumicunity.common.aspect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * Tag-based aspect rule, loaded from {@code data/<ns>/thaumicunity/aspect_rule/<name>.json}. Items in the tag that
 * have no explicit aspects get these aspects (docs/DESIGN_DECISIONS.md, D13, step 2). When several rules match, the
 * one with the highest priority wins; with {@code additive: true} a rule is added on top of the winner instead.
 */
public record AspectRule(TagKey<Item> tag, AspectList aspects, int priority, boolean additive) {
    public static final Codec<AspectRule> CODEC = RecordCodecBuilder.create(i -> i.group(
            TagKey.hashedCodec(Registries.ITEM).fieldOf("tag").forGetter(AspectRule::tag),
            AspectList.CODEC.fieldOf("aspects").forGetter(AspectRule::aspects),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(AspectRule::priority),
            Codec.BOOL.optionalFieldOf("additive", false).forGetter(AspectRule::additive)
    ).apply(i, AspectRule::new));
}
