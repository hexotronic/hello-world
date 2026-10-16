package dev.thaumicunity.client;

import dev.thaumicunity.common.network.AuraSnapshotPayload;

/** Last aura snapshot received from the server and when it arrived. */
public final class ClientAura {
    private static AuraSnapshotPayload last;
    private static long receivedAt;

    private ClientAura() {
    }

    public static void set(AuraSnapshotPayload payload, long gameTime) {
        last = payload;
        receivedAt = gameTime;
    }

    /** The snapshot if it is fresh (younger than three seconds), otherwise null. */
    public static AuraSnapshotPayload current(long gameTime) {
        return last != null && gameTime - receivedAt < 60 ? last : null;
    }
}
