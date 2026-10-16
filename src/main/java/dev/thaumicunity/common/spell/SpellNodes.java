package dev.thaumicunity.common.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.core.spell.SpellNode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.List;

/** Codecs for the spell graph stored on foci ({@link dev.thaumicunity.core.spell.SpellNode}). */
public final class SpellNodes {
    public static final Codec<SpellNode> CODEC = Codec.recursive("spell_node", self -> RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("medium").forGetter(SpellNode::medium),
            Codec.STRING.listOf().optionalFieldOf("effects", List.of()).forGetter(SpellNode::effects),
            Codec.STRING.listOf().optionalFieldOf("modifiers", List.of()).forGetter(SpellNode::modifiers),
            self.listOf().optionalFieldOf("children", List.of()).forGetter(SpellNode::children)
    ).apply(i, SpellNode::new)));

    public static final StreamCodec<ByteBuf, SpellNode> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    private SpellNodes() {
    }
}
