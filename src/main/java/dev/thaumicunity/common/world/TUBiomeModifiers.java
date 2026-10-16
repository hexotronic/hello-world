package dev.thaumicunity.common.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.config.TUConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.ModifiableBiomeInfo;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Biome modifier types. Thaumic Unity's world generation goes through these so the {@code worldgen} option can switch it off. */
public final class TUBiomeModifiers {
    private static final DeferredRegister<MapCodec<? extends BiomeModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.BIOME_MODIFIER_SERIALIZERS, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends BiomeModifier>, MapCodec<AddFeatures>> ADD_FEATURES =
            SERIALIZERS.register("add_features", () -> RecordCodecBuilder.mapCodec(i -> i.group(
                    Biome.LIST_CODEC.fieldOf("biomes").forGetter(AddFeatures::biomes),
                    PlacedFeature.LIST_CODEC.fieldOf("features").forGetter(AddFeatures::features),
                    GenerationStep.Decoration.CODEC.fieldOf("step").forGetter(AddFeatures::step)
            ).apply(i, AddFeatures::new)));

    private TUBiomeModifiers() {
    }

    public static void init(IEventBus modBus) {
        SERIALIZERS.register(modBus);
    }

    /**
     * Like {@code neoforge:add_features}, but only while the synced config's {@code world.worldgen} option is on.
     * Biome modifiers run after the synced config is loaded for the world, so the option applies per world.
     */
    public record AddFeatures(HolderSet<Biome> biomes, HolderSet<PlacedFeature> features, GenerationStep.Decoration step)
            implements BiomeModifier {
        @Override
        public void modify(RegistryAccess registries, Holder<Biome> biome, Phase phase, ModifiableBiomeInfo.BiomeInfo.Builder builder) {
            if (phase == Phase.ADD && biomes.contains(biome) && TUConfig.WORLDGEN_ENABLED.get()) {
                features.forEach(feature -> builder.getGenerationSettings().addFeature(step, feature));
            }
        }

        @Override
        public MapCodec<? extends BiomeModifier> codec() {
            return ADD_FEATURES.get();
        }
    }
}
