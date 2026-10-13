package dev.thaumicunity.common.registry;

import com.mojang.serialization.Codec;
import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aura.AuraProfile;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

/** Data maps (data/&lt;ns&gt;/data_maps/&lt;registry&gt;/thaumicunity/&lt;name&gt;.json). */
public final class TUDataMaps {
    /** Explicit aspects of an item (highest priority source, docs/DESIGN_DECISIONS.md D13). */
    public static final DataMapType<Item, AspectList> ITEM_ASPECTS = DataMapType
            .builder(ThaumicUnity.id("aspects"), Registries.ITEM, AspectList.CODEC)
            .synced(AspectList.CODEC, false)
            .build();

    /** Aspects revealed by scanning a living thing. */
    public static final DataMapType<EntityType<?>, AspectList> ENTITY_ASPECTS = DataMapType
            .builder(ThaumicUnity.id("entity_aspects"), Registries.ENTITY_TYPE, AspectList.CODEC)
            .synced(AspectList.CODEC, false)
            .build();

    /** Base vis and primal attunement of a biome. */
    public static final DataMapType<Biome, AuraProfile> AURA_PROFILE = DataMapType
            .builder(ThaumicUnity.id("aura_profile"), Registries.BIOME, AuraProfile.CODEC)
            .synced(AuraProfile.CODEC, false)
            .build();

    /** Stability a block adds when placed around a runic matrix. */
    public static final DataMapType<Block, Float> INFUSION_STABILIZER = DataMapType
            .builder(ThaumicUnity.id("infusion_stabilizer"), Registries.BLOCK, Codec.floatRange(-20f, 20f))
            .build();

    /** Burn time in ticks of an item in the alchemical furnace (and other arcane burners). */
    public static final DataMapType<Item, Integer> ALCHEMICAL_FUEL = DataMapType
            .builder(ThaumicUnity.id("alchemical_fuel"), Registries.ITEM, Codec.intRange(1, 1_000_000))
            .build();

    private TUDataMaps() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TUDataMaps::register);
    }

    private static void register(RegisterDataMapTypesEvent event) {
        event.register(ITEM_ASPECTS);
        event.register(ENTITY_ASPECTS);
        event.register(AURA_PROFILE);
        event.register(INFUSION_STABILIZER);
        event.register(ALCHEMICAL_FUEL);
    }
}
