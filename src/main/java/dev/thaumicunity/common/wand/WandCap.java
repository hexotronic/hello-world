package dev.thaumicunity.common.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;

/**
 * A wand cap, datapack registry {@code thaumicunity:wand_cap} (docs/DESIGN_DECISIONS.md, D4).
 *
 * @param item       the cap item
 * @param cost       factor on the vis a spell costs (1.1 = a tenth dearer)
 * @param primalCost factors for single primals, overriding {@code cost}
 * @param color      tint of the caps in the wand's item model
 */
public record WandCap(Identifier item, double cost, Map<Identifier, Double> primalCost, int color) {
    public static final Codec<WandCap> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("item").forGetter(WandCap::item),
            Codec.doubleRange(0.05, 20.0).fieldOf("cost").forGetter(WandCap::cost),
            Codec.unboundedMap(Identifier.CODEC, Codec.doubleRange(0.05, 20.0)).optionalFieldOf("primal_cost", Map.of())
                    .forGetter(WandCap::primalCost),
            ExtraCodecs.STRING_RGB_COLOR.fieldOf("color").forGetter(WandCap::color)
    ).apply(i, WandCap::new));

    public WandCap {
        primalCost = Map.copyOf(primalCost);
    }

    /** Cost factor for one primal. */
    public double costFactor(Identifier primal) {
        return primalCost.getOrDefault(primal, cost);
    }

    public static String translationKey(Identifier id) {
        return "wand_cap." + id.getNamespace() + "." + id.getPath();
    }
}
