package dev.thaumicunity.common.wand;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.ThaumicUnity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** Data component {@code thaumicunity:wand_parts}: which rod and caps a wand is made of. */
public record WandParts(Identifier rod, Identifier cap) {
    public static final WandParts DEFAULT = new WandParts(ThaumicUnity.id("wood"), ThaumicUnity.id("iron"));

    public static final Codec<WandParts> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("rod").forGetter(WandParts::rod),
            Identifier.CODEC.fieldOf("cap").forGetter(WandParts::cap)
    ).apply(i, WandParts::new));

    public static final StreamCodec<ByteBuf, WandParts> STREAM_CODEC = StreamCodec.composite(
            Identifier.STREAM_CODEC, WandParts::rod,
            Identifier.STREAM_CODEC, WandParts::cap,
            WandParts::new);
}
