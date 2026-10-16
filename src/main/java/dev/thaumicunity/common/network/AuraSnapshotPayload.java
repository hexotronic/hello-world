package dev.thaumicunity.common.network;

import dev.thaumicunity.ThaumicUnity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server → client: aura of the chunk the player stands in (for the aura HUD while holding a scanner). */
public record AuraSnapshotPayload(float vis, float flux, float base) implements CustomPacketPayload {
    public static final Type<AuraSnapshotPayload> TYPE = new Type<>(ThaumicUnity.id("aura_snapshot"));
    public static final StreamCodec<ByteBuf, AuraSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, AuraSnapshotPayload::vis,
            ByteBufCodecs.FLOAT, AuraSnapshotPayload::flux,
            ByteBufCodecs.FLOAT, AuraSnapshotPayload::base,
            AuraSnapshotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
