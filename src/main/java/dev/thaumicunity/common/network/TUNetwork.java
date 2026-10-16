package dev.thaumicunity.common.network;

import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.research.ResearchTableActions;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Payload registration. Server-bound handlers run on the main thread and validate everything they are told;
 * client-bound handlers are registered by the client entry point (RegisterClientPayloadHandlersEvent).
 */
public final class TUNetwork {
    public static final String VERSION = "1";

    private TUNetwork() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TUNetwork::register);
    }

    private static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToServer(ResearchAdvancePayload.TYPE, ResearchAdvancePayload.STREAM_CODEC, TUNetwork::onAdvance);
        registrar.playToServer(ResearchTablePayload.TYPE, ResearchTablePayload.STREAM_CODEC, TUNetwork::onTable);
        registrar.playToClient(ItemAspectsPayload.TYPE, ItemAspectsPayload.STREAM_CODEC);
        registrar.playToClient(AuraSnapshotPayload.TYPE, AuraSnapshotPayload.STREAM_CODEC);
    }

    private static void onAdvance(ResearchAdvancePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            ResearchManager.tryAdvance(player, payload.research());
        }
    }

    private static void onTable(ResearchTablePayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        switch (payload.action()) {
            case ResearchTablePayload.START -> ResearchTableActions.startNotes(player, payload.id(), payload.stage(), payload.requirement());
            case ResearchTablePayload.PLACE -> ResearchTableActions.place(player, payload.q(), payload.r(), payload.id());
            case ResearchTablePayload.CLEAR -> ResearchTableActions.clear(player, payload.q(), payload.r());
            case ResearchTablePayload.THEORIZE -> ResearchTableActions.theorize(player, payload.id());
            default -> {
            }
        }
    }
}
