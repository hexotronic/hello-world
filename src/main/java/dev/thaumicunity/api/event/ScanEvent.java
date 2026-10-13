package dev.thaumicunity.api.event;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

import java.util.Map;

/**
 * Fired on {@code NeoForge.EVENT_BUS} after a player scans something new. {@code key} has the form
 * {@code kind:namespace:path} (kind is item, block or entity).
 */
public class ScanEvent extends Event {
    private final ServerPlayer player;
    private final String key;
    private final Map<Identifier, Integer> aspects;

    public ScanEvent(ServerPlayer player, String key, Map<Identifier, Integer> aspects) {
        this.player = player;
        this.key = key;
        this.aspects = Map.copyOf(aspects);
    }

    public ServerPlayer getPlayer() {
        return player;
    }

    public String getKey() {
        return key;
    }

    public Map<Identifier, Integer> getAspects() {
        return aspects;
    }
}
