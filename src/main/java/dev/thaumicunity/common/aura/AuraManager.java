package dev.thaumicunity.common.aura;

import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.core.aura.AuraSettings;
import dev.thaumicunity.core.aura.AuraSimulator;
import dev.thaumicunity.core.aura.AuraState;
import dev.thaumicunity.core.aura.PrimalAttunement;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Server-side aura: initialises chunk auras from their biome, simulates a bounded number of loaded chunks per update
 * (round-robin) and catches unloaded chunks up in closed form when they come back (docs/ARCHITECTURE.md §6).
 */
public final class AuraManager {
    private static final Map<ResourceKey<Level>, LongLinkedOpenHashSet> LOADED = new HashMap<>();
    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private AuraManager() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(AuraManager::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(AuraManager::onChunkUnload);
        NeoForge.EVENT_BUS.addListener(AuraManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> LOADED.clear());
    }

    public static AuraSettings settings() {
        return new AuraSettings(TUConfig.AURA_REGEN_RATE.get(), 0.01, TUConfig.AURA_DIFFUSION_RATE.get(),
                TUConfig.AURA_FLUX_DECAY.get(), 0.75, TUConfig.AURA_RIFT_PRESSURE.get(), 4.0);
    }

    private static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LevelChunk chunk = event.getChunk();
            LOADED.computeIfAbsent(level.dimension(), k -> new LongLinkedOpenHashSet()).add(chunk.getPos().pack());
        }
    }

    private static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            LongLinkedOpenHashSet set = LOADED.get(level.dimension());
            if (set != null) {
                set.remove(event.getChunk().getPos().pack());
            }
        }
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        int interval = TUConfig.AURA_TICK_INTERVAL.get();
        if (level.getGameTime() % interval != 0) {
            return;
        }
        LongLinkedOpenHashSet loaded = LOADED.get(level.dimension());
        if (loaded == null || loaded.isEmpty()) {
            return;
        }
        int budget = Math.min(loaded.size(), TUConfig.AURA_CHUNKS_PER_TICK.get());
        LongArrayList batch = new LongArrayList(budget);
        for (int i = 0; i < budget; i++) {
            long first = loaded.removeFirstLong();
            loaded.add(first); // rotate to the back
            batch.add(first);
        }
        AuraSettings settings = settings();
        for (int i = 0; i < batch.size(); i++) {
            long packed = batch.getLong(i);
            int cx = ChunkPos.getX(packed);
            int cz = ChunkPos.getZ(packed);
            if (!level.hasChunk(cx, cz)) {
                loaded.remove(packed);
                continue;
            }
            simulate(level, level.getChunk(cx, cz), settings, interval);
        }
    }

    private static void simulate(ServerLevel level, LevelChunk chunk, AuraSettings settings, int interval) {
        ChunkAura aura = ensureInitialized(level, chunk);
        long now = level.getGameTime();
        long steps = Math.max(1, (now - aura.lastUpdate()) / interval);
        AuraState state = AuraSimulator.catchUp(aura.state(), steps, settings);
        aura.setLastUpdate(now);

        double[] neighbourVis = new double[4];
        LevelChunk[] neighbours = new LevelChunk[4];
        ChunkPos pos = chunk.getPos();
        for (int k = 0; k < 4; k++) {
            int nx = pos.x() + NEIGHBOURS[k][0];
            int nz = pos.z() + NEIGHBOURS[k][1];
            if (level.hasChunk(nx, nz)) {
                neighbours[k] = level.getChunk(nx, nz);
                neighbourVis[k] = ensureInitialized(level, neighbours[k]).vis();
            } else {
                neighbourVis[k] = Double.NaN;
            }
        }
        double[] flows = AuraSimulator.diffusion(state.vis(), neighbourVis, settings);
        double sent = 0;
        for (int k = 0; k < 4; k++) {
            if (flows[k] > 0 && neighbours[k] != null) {
                ChunkAura other = neighbours[k].getData(TUAttachments.CHUNK_AURA);
                other.setVis(other.vis() + flows[k]);
                neighbours[k].markUnsaved();
                sent += flows[k];
            }
        }
        aura.apply(state.withVis(state.vis() - sent));
        chunk.markUnsaved();
        FluxEffects.onAuraUpdated(level, chunk, aura, settings);
    }

    /** Gives a chunk its biome's base vis and attunement the first time its aura is touched. */
    public static ChunkAura ensureInitialized(Level level, LevelChunk chunk) {
        ChunkAura aura = chunk.getData(TUAttachments.CHUNK_AURA);
        if (!aura.initialized()) {
            ChunkPos pos = chunk.getPos();
            BlockPos center = new BlockPos(pos.getMiddleBlockX(), level.getSeaLevel(), pos.getMiddleBlockZ());
            Holder<Biome> biome = level.getBiome(center);
            AuraProfile profile = profileOf(biome);
            // Deterministic per-chunk variation of ±25 % so the aura is not perfectly flat.
            long seed = pos.pack() * 0x9E3779B97F4A7C15L;
            double variation = 0.75 + ((seed >>> 11) % 1000) / 1000.0 * 0.5;
            aura.initialize(profile.base() * variation, profile.toAttunement(), level.getGameTime());
            chunk.markUnsaved();
        }
        return aura;
    }

    /** The biome's aura profile, falling back to a profile derived from common biome tags. */
    public static AuraProfile profileOf(Holder<Biome> biome) {
        AuraProfile explicit = biome.getData(TUDataMaps.AURA_PROFILE);
        if (explicit != null) {
            return explicit;
        }
        int base = TUConfig.AURA_DEFAULT_BASE.get();
        // aer, terra, ignis, aqua, ordo, perditio
        if (biome.is(BiomeTags.IS_NETHER)) {
            return new AuraProfile((int) (base * 0.8), java.util.List.of(0.6, 1.0, 2.6, 0.2, 0.6, 1.6));
        }
        if (biome.is(BiomeTags.IS_END)) {
            return new AuraProfile((int) (base * 1.2), java.util.List.of(1.2, 0.6, 0.6, 0.4, 1.4, 1.8));
        }
        if (biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER)) {
            return new AuraProfile(base, java.util.List.of(1.0, 0.6, 0.4, 2.4, 1.0, 0.6));
        }
        if (biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(BiomeTags.IS_HILL)) {
            return new AuraProfile(base, java.util.List.of(1.6, 1.6, 0.8, 0.6, 1.0, 0.4));
        }
        if (biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE)) {
            return new AuraProfile((int) (base * 1.1), java.util.List.of(1.0, 1.4, 0.7, 1.2, 1.0, 0.7));
        }
        if (biome.is(BiomeTags.IS_BADLANDS) || biome.is(BiomeTags.IS_SAVANNA)) {
            return new AuraProfile((int) (base * 0.9), java.util.List.of(1.0, 1.2, 1.8, 0.4, 0.8, 0.8));
        }
        return new AuraProfile(base, java.util.List.of(1d, 1d, 1d, 1d, 1d, 1d));
    }

    // --- public access used by devices, casting and the API

    public static ChunkAura auraAt(Level level, BlockPos pos) {
        return ensureInitialized(level, level.getChunkAt(pos));
    }

    /** Drains up to {@code amount} vis at a position; returns how much was drained. */
    public static double drainVis(Level level, BlockPos pos, double amount) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkAura aura = ensureInitialized(level, chunk);
        double drained = Math.min(Math.max(0, amount), aura.vis());
        aura.setVis(aura.vis() - drained);
        chunk.markUnsaved();
        return drained;
    }

    /**
     * Drains vis for the given primal, applying the chunk's attunement cost factor. Returns true and drains only if
     * the full amount is available.
     */
    public static boolean drainPrimal(Level level, BlockPos pos, int primalIndex, double amount, boolean simulate) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkAura aura = ensureInitialized(level, chunk);
        double needed = amount * aura.attunement().costFactor(primalIndex);
        if (aura.vis() + 1.0e-9 < needed) {
            return false;
        }
        if (!simulate) {
            aura.setVis(aura.vis() - needed);
            chunk.markUnsaved();
        }
        return true;
    }

    public static void addVis(Level level, BlockPos pos, double amount) {
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkAura aura = ensureInitialized(level, chunk);
        aura.setVis(aura.vis() + amount);
        chunk.markUnsaved();
    }

    public static void addFlux(Level level, BlockPos pos, double amount) {
        if (!TUConfig.FLUX_ENABLED.get() || amount <= 0) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        ChunkAura aura = ensureInitialized(level, chunk);
        aura.setFlux(aura.flux() + amount);
        chunk.markUnsaved();
    }

    public static PrimalAttunement attunementAt(Level level, BlockPos pos) {
        return auraAt(level, pos).attunement();
    }
}
