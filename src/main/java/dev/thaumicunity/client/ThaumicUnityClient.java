package dev.thaumicunity.client;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.client.hud.AuraHud;
import dev.thaumicunity.client.particle.MagicParticle;
import dev.thaumicunity.client.render.ArcanePedestalRenderer;
import dev.thaumicunity.client.render.CrucibleRenderer;
import dev.thaumicunity.client.render.WardedJarRenderer;
import dev.thaumicunity.client.screen.AlchemicalFurnaceScreen;
import dev.thaumicunity.client.screen.ArcaneWorkbenchScreen;
import dev.thaumicunity.client.screen.GuiUtil;
import dev.thaumicunity.client.screen.ResearchTableScreen;
import dev.thaumicunity.client.smoke.ClientSmokeTest;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.item.ClientBridge;
import dev.thaumicunity.common.network.AuraSnapshotPayload;
import dev.thaumicunity.common.network.ItemAspectsPayload;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUEntities;
import dev.thaumicunity.common.registry.TUMenus;
import dev.thaumicunity.common.registry.TUParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RecipesReceivedEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;

/** Client entry point: renderers, screens, particles, HUD, tooltips and client-bound packet handlers. */
@Mod(value = ThaumicUnity.MOD_ID, dist = Dist.CLIENT)
public final class ThaumicUnityClient {
    public ThaumicUnityClient(IEventBus modBus, ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
        ClientBridge.Holder.instance = new ClientBridgeImpl();

        modBus.addListener(ThaumicUnityClient::registerParticles);
        modBus.addListener(ThaumicUnityClient::registerRenderers);
        modBus.addListener(ThaumicUnityClient::registerScreens);
        modBus.addListener(ThaumicUnityClient::registerPayloadHandlers);
        modBus.addListener(ThaumicUnityClient::registerGuiLayers);
        modBus.addListener(ThaumicUnityClient::registerReloadListeners);

        NeoForge.EVENT_BUS.addListener(AspectTooltips::onTooltip);
        NeoForge.EVENT_BUS.addListener((RecipesReceivedEvent event) -> ClientRecipes.set(event.getRecipeMap()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientRecipes.clear();
            ItemAspects.setClientTable(Map.of());
        });
        if (ClientSmokeTest.isEnabled()) {
            ClientSmokeTest.register();
        }
    }

    private static void registerParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(TUParticles.SPARK.get(), sprites -> new MagicParticle.Provider(sprites, MagicParticle.Style.SPARK));
        event.registerSpriteSet(TUParticles.WISP.get(), sprites -> new MagicParticle.Provider(sprites, MagicParticle.Style.WISP));
        event.registerSpriteSet(TUParticles.ESSENTIA.get(), sprites -> new MagicParticle.Provider(sprites, MagicParticle.Style.ESSENTIA));
        event.registerSpriteSet(TUParticles.RUNE.get(), sprites -> new MagicParticle.Provider(sprites, MagicParticle.Style.RUNE));
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Spell entities are drawn entirely with particles.
        event.registerEntityRenderer(TUEntities.SPELL_PROJECTILE.get(), NoopRenderer::new);
        event.registerEntityRenderer(TUEntities.SPELL_ZONE.get(), NoopRenderer::new);
        event.registerBlockEntityRenderer(TUBlockEntities.ARCANE_PEDESTAL.get(), ArcanePedestalRenderer::new);
        event.registerBlockEntityRenderer(TUBlockEntities.WAND_PEDESTAL.get(), ArcanePedestalRenderer::new);
        event.registerBlockEntityRenderer(TUBlockEntities.CRUCIBLE.get(), CrucibleRenderer::new);
        event.registerBlockEntityRenderer(TUBlockEntities.WARDED_JAR.get(), WardedJarRenderer::new);
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(TUMenus.ARCANE_WORKBENCH.get(), ArcaneWorkbenchScreen::new);
        event.register(TUMenus.RESEARCH_TABLE.get(), ResearchTableScreen::new);
        event.register(TUMenus.ALCHEMICAL_FURNACE.get(), AlchemicalFurnaceScreen::new);
    }

    private static void registerPayloadHandlers(RegisterClientPayloadHandlersEvent event) {
        event.register(ItemAspectsPayload.TYPE, (payload, context) -> ItemAspects.setClientTable(payload.table()));
        event.register(AuraSnapshotPayload.TYPE, (payload, context) -> {
            var level = Minecraft.getInstance().level;
            ClientAura.set(payload, level == null ? 0 : level.getGameTime());
        });
    }

    private static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(ThaumicUnity.id("aura"), AuraHud::render);
    }

    private static void registerReloadListeners(AddClientReloadListenersEvent event) {
        event.addListener(ThaumicUnity.id("texture_cache"), (ResourceManagerReloadListener) manager -> GuiUtil.clearTextureCache());
    }
}
