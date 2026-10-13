package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aura.ChunkAura;
import dev.thaumicunity.common.research.PlayerKnowledge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

/** Data attachments: per-player knowledge and per-chunk aura. */
public final class TUAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, ThaumicUnity.MOD_ID);

    /** Research, scans, aspect points, knowledge and warp. Survives death; synced to its owner only. */
    public static final Supplier<AttachmentType<PlayerKnowledge>> KNOWLEDGE = TYPES.register("knowledge",
            () -> AttachmentType.builder(PlayerKnowledge::new)
                    .serialize(PlayerKnowledge.CODEC.fieldOf("knowledge"))
                    .copyOnDeath()
                    .sync((holder, player) -> holder == player, PlayerKnowledge.STREAM_CODEC)
                    .build());

    /** Vis, flux and attunement of a chunk. Not synced automatically; see AuraSnapshotPayload. */
    public static final Supplier<AttachmentType<ChunkAura>> CHUNK_AURA = TYPES.register("chunk_aura",
            () -> AttachmentType.builder(ChunkAura::new).serialize(ChunkAura.MAP_CODEC).build());

    private TUAttachments() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
