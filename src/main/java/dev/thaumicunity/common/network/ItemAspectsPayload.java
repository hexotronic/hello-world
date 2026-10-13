package dev.thaumicunity.common.network;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.AspectList;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/** Server → client: the resolved aspects of every item (sent after login and every data reload). */
public record ItemAspectsPayload(Map<Identifier, AspectList> table) implements CustomPacketPayload {
    public static final Type<ItemAspectsPayload> TYPE = new Type<>(ThaumicUnity.id("item_aspects"));
    public static final StreamCodec<ByteBuf, ItemAspectsPayload> STREAM_CODEC = ByteBufCodecs
            .<ByteBuf, Identifier, AspectList, Map<Identifier, AspectList>>map(HashMap::new, Identifier.STREAM_CODEC, AspectList.STREAM_CODEC, 1 << 20)
            .map(ItemAspectsPayload::new, ItemAspectsPayload::table);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
