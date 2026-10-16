package dev.thaumicunity.common.world;

import com.mojang.serialization.MapCodec;
import dev.thaumicunity.ThaumicUnity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** World generation feature types; the features themselves are data (worldgen/feature, from tools/content/flora.py). */
public final class TUFeatures {
    private static final DeferredRegister<MapCodec<? extends Feature>> TYPES =
            DeferredRegister.create(Registries.FEATURE_TYPE, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<CrystalClusterFeature>> CRYSTAL_CLUSTERS =
            TYPES.register("crystal_clusters", () -> CrystalClusterFeature.CODEC);
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<AuraNodeFeature>> AURA_NODE =
            TYPES.register("aura_node", () -> AuraNodeFeature.CODEC);
    public static final DeferredHolder<MapCodec<? extends Feature>, MapCodec<TreeWithNodeFeature>> TREE_WITH_NODE =
            TYPES.register("tree_with_node", () -> TreeWithNodeFeature.CODEC);

    private TUFeatures() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
