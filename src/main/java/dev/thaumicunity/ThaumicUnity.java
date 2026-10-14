package dev.thaumicunity;

import com.mojang.logging.LogUtils;
import dev.thaumicunity.common.TUEvents;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.network.TUNetwork;
import dev.thaumicunity.common.config.TUClientConfig;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.gametest.TUGameTests;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUBlocks;
import dev.thaumicunity.common.registry.TUCreativeTabs;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TUMenus;
import dev.thaumicunity.common.registry.TURecipes;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.common.registry.TUEntities;
import dev.thaumicunity.common.registry.TUParticles;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.spell.primitive.SpellScheduler;
import dev.thaumicunity.common.spell.primitive.TUSpellPrimitives;
import dev.thaumicunity.common.world.TUBiomeModifiers;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

/// Entry point of Thaumic Unity. Wires every registry and system; see docs/ARCHITECTURE.md for the layout.
@Mod(ThaumicUnity.MOD_ID)
public final class ThaumicUnity {
    public static final String MOD_ID = "thaumicunity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ThaumicUnity(IEventBus modBus, ModContainer container) {
        // Gameplay settings are server-authoritative and synced to clients.
        container.registerConfig(ModConfig.Type.SYNCED, TUConfig.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, TUClientConfig.SPEC);

        TURegistries.init(modBus);
        TUDataMaps.init(modBus);
        TUAttachments.init(modBus);
        TUDataComponents.init(modBus);
        TUBlocks.init(modBus);
        TUItems.init(modBus);
        TUBlockEntities.init(modBus);
        TUMenus.init(modBus);
        TURecipes.init(modBus);
        TUParticles.init(modBus);
        TUSounds.init(modBus);
        TUEntities.init(modBus);
        TUCreativeTabs.init(modBus);
        TUSpellPrimitives.init(modBus);
        TUBiomeModifiers.init(modBus);
        TUGameTests.init(modBus);
        TUNetwork.init(modBus);

        TUEvents.init(modBus);
        AuraManager.init();
        SpellScheduler.init();
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
