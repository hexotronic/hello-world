package dev.thaumicunity.common.network;

import dev.thaumicunity.ThaumicUnity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client → server: "complete the current stage of this research entry" (Thaumonomicon button). */
public record ResearchAdvancePayload(Identifier research) implements CustomPacketPayload {
    public static final Type<ResearchAdvancePayload> TYPE = new Type<>(ThaumicUnity.id("research_advance"));
    public static final StreamCodec<ByteBuf, ResearchAdvancePayload> STREAM_CODEC =
            Identifier.STREAM_CODEC.map(ResearchAdvancePayload::new, ResearchAdvancePayload::research);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
