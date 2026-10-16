package dev.thaumicunity.common.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.core.aura.PrimalAttunement;

import java.util.List;

/**
 * Biome data map value {@code thaumicunity:aura_profile}: how much vis a biome's chunks hold at rest and which primal
 * aspects resonate there. JSON: {@code {"base": 140, "attunement": [1.4, 1, 0.8, 1, 1.2, 0.6]}} with weights in the
 * order aer, terra, ignis, aqua, ordo, perditio.
 */
public record AuraProfile(int base, List<Double> attunement) {
    public static final Codec<AuraProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.intRange(0, 100000).fieldOf("base").forGetter(AuraProfile::base),
            Codec.doubleRange(0, 100).listOf(6, 6).optionalFieldOf("attunement", List.of(1d, 1d, 1d, 1d, 1d, 1d))
                    .forGetter(AuraProfile::attunement)
    ).apply(i, AuraProfile::new));

    public PrimalAttunement toAttunement() {
        double[] weights = new double[6];
        for (int k = 0; k < 6; k++) {
            weights[k] = attunement.get(k);
        }
        return new PrimalAttunement(weights);
    }
}
