package dev.thaumicunity.common.spell.primitive;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Small server-thread scheduler for delayed spell work (echoes, summon lifetimes). Tasks do not survive a server
 * restart, which is intended: they are all short-lived effects.
 */
public final class SpellScheduler {
    private record Task(ResourceKey<Level> dimension, long runAt, Consumer<ServerLevel> action) {
    }

    private static final List<Task> TASKS = new ArrayList<>();
    private static final int MAX_TASKS = 20000;

    private SpellScheduler() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(SpellScheduler::onServerTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TASKS.clear());
    }

    /** Runs {@code action} in {@code level} after {@code ticks} ticks. */
    public static void later(ServerLevel level, int ticks, Consumer<ServerLevel> action) {
        if (TASKS.size() < MAX_TASKS) {
            TASKS.add(new Task(level.dimension(), level.getGameTime() + Math.max(1, ticks), action));
        }
    }

    /** Discards an entity after {@code ticks} ticks (summoned creatures). */
    public static void expireLater(ServerLevel level, Entity entity, int ticks) {
        UUID id = entity.getUUID();
        later(level, ticks, l -> {
            Entity e = l.getEntity(id);
            if (e != null) {
                e.discard();
            }
        });
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (TASKS.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        List<Task> due = new ArrayList<>();
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            ServerLevel level = server.getLevel(task.dimension());
            if (level == null) {
                it.remove();
            } else if (level.getGameTime() >= task.runAt()) {
                due.add(task);
                it.remove();
            }
        }
        for (Task task : due) {
            ServerLevel level = server.getLevel(task.dimension());
            if (level != null) {
                task.action().accept(level);
            }
        }
    }
}
