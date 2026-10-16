package dev.thaumicunity.common.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Optional;

/**
 * A wand rod, datapack registry {@code thaumicunity:wand_rod} (docs/DESIGN_DECISIONS.md, D4).
 *
 * @param item     the item the rod is made from (a stick, a wand core, …)
 * @param capacity vis of each primal a wand with this rod holds
 * @param affinity a primal the rod favours: spells pay 10 % less of it and the wand slowly refills it by itself
 * @param color    tint of the rod in the wand's item model
 */
public record WandRod(Identifier item, int capacity, Optional<Identifier> affinity, int color) {
    public static final Codec<WandRod> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("item").forGetter(WandRod::item),
            ExtraCodecs.POSITIVE_INT.fieldOf("capacity").forGetter(WandRod::capacity),
            Identifier.CODEC.optionalFieldOf("affinity").forGetter(WandRod::affinity),
            ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(WandRod::color)
    ).apply(i, WandRod::new));

    public static String translationKey(Identifier id) {
        return "wand_rod." + id.getNamespace() + "." + id.getPath();
    }
}
