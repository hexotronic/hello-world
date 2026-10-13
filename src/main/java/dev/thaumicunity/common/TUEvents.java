package dev.thaumicunity.common;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.AspectRuleLoader;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.aura.ChunkAura;
import dev.thaumicunity.common.command.TUCommands;
import dev.thaumicunity.common.network.AuraSnapshotPayload;
import dev.thaumicunity.common.network.ItemAspectsPayload;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TUTags;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.spell.SpellLibrary;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Game and mod bus listeners that glue the systems together. */
public final class TUEvents {
    private TUEvents() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TUEvents::addPacks);
        NeoForge.EVENT_BUS.addListener(TUEvents::addReloadListeners);
        NeoForge.EVENT_BUS.addListener(TUEvents::onTagsUpdated);
        NeoForge.EVENT_BUS.addListener(TUEvents::onDatapackSync);
        NeoForge.EVENT_BUS.addListener(TUEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(TUEvents::onCrafted);
        NeoForge.EVENT_BUS.addListener(TUEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(TUEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent e) -> TUCommands.register(e.getDispatcher()));
    }

    /** The optional "modern combinations" datapack (docs/DESIGN_DECISIONS.md, D1). */
    private static void addPacks(AddPackFindersEvent event) {
        event.addPackFinders(ThaumicUnity.id("datapacks/modern_combinations"), PackType.SERVER_DATA,
                Component.translatable("pack.thaumicunity.modern_combinations"), PackSource.FEATURE, false, Pack.Position.TOP);
    }

    private static void addReloadListeners(AddServerReloadListenersEvent event) {
        event.addListener(ThaumicUnity.id("aspect_rules"), new AspectRuleLoader());
    }

    private static void onTagsUpdated(TagsUpdatedEvent event) {
        if (event instanceof TagsUpdatedEvent.ServerDataLoad) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ItemAspects.recompute(server);
            }
        }
        SpellLibrary.invalidate();
    }

    private static void onDatapackSync(OnDatapackSyncEvent event) {
        if (ItemAspects.serverTable().isEmpty()) {
            var server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                ItemAspects.recompute(server);
            }
        }
        ItemAspectsPayload payload = new ItemAspectsPayload(ItemAspects.serverTable());
        event.getRelevantPlayers().forEach(player -> PacketDistributor.sendToPlayer(player, payload));
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchManager.grantAutoUnlocks(player);
        }
    }

    private static void onCrafted(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchManager.onCrafted(player, event.getCrafting());
        }
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            ResearchManager.onKill(player, event.getEntity());
        }
    }

    /** Sends the aura of the current chunk once a second to players holding a thaumometer or wearing revealing gear. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        boolean scanning = player.getMainHandItem().is(TUItems.THAUMOMETER.get()) || player.getOffhandItem().is(TUItems.THAUMOMETER.get())
                || player.getItemBySlot(EquipmentSlot.HEAD).is(TUTags.Items.REVEALING);
        if (scanning) {
            ChunkAura aura = AuraManager.auraAt(player.level(), player.blockPosition());
            PacketDistributor.sendToPlayer(player, new AuraSnapshotPayload((float) aura.vis(), (float) aura.flux(), (float) aura.base()));
        }
    }
}
