package dev.thaumicunity.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Fired on {@code NeoForge.EVENT_BUS} when a player completes the last stage of a research entry. */
public class ResearchCompletedEvent extends Event {
    private final ServerPlayer player;
    private final Identifier research;

    public ResearchCompletedEvent(ServerPlayer player, Identifier research) {
        this.player = player;
        this.research = research;
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public Identifier getResearch() {
        return research;
    }
}
