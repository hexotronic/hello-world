package dev.thaumicunity.common.network;

import dev.thaumicunity.ThaumicUnity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server research table action.
 *
 * @param action      0 start notes, 1 place aspect, 2 clear cell, 3 theorize
 * @param id          research id (start), aspect id (place) or category id (theorize)
 * @param stage       stage index (start)
 * @param requirement requirement index (start)
 * @param q           hex column (place/clear)
 * @param r           hex row (place/clear)
 */
public record ResearchTablePayload(int action, Identifier id, int stage, int requirement, int q, int r) implements CustomPacketPayload {
    public static final int START = 0;
    public static final int PLACE = 1;
    public static final int CLEAR = 2;
    public static final int THEORIZE = 3;

    public static final Type<ResearchTablePayload> TYPE = new Type<>(ThaumicUnity.id("research_table"));
    public static final StreamCodec<ByteBuf, ResearchTablePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ResearchTablePayload::action,
            Identifier.STREAM_CODEC, ResearchTablePayload::id,
            ByteBufCodecs.VAR_INT, ResearchTablePayload::stage,
            ByteBufCodecs.VAR_INT, ResearchTablePayload::requirement,
            ByteBufCodecs.VAR_INT, ResearchTablePayload::q,
            ByteBufCodecs.VAR_INT, ResearchTablePayload::r,
            ResearchTablePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
