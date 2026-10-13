package dev.thaumicunity.common.aura;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.core.aura.AuraState;
import dev.thaumicunity.core.aura.PrimalAttunement;

import java.util.List;

/**
 * Aura of one chunk, stored as a chunk attachment. {@code initialized} is false until the chunk's base level and
 * attunement have been read from its biome ({@link AuraManager#ensureInitialized}).
 */
public final class ChunkAura {
    private double vis;
    private double flux;
    private double base;
    private double[] attunement = {1, 1, 1, 1, 1, 1};
    private long lastUpdate;
    private boolean initialized;

    public static final MapCodec<ChunkAura> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("vis", 0d).forGetter(a -> a.vis),
            Codec.DOUBLE.optionalFieldOf("flux", 0d).forGetter(a -> a.flux),
            Codec.DOUBLE.optionalFieldOf("base", 0d).forGetter(a -> a.base),
            Codec.DOUBLE.listOf().optionalFieldOf("attunement", List.of(1d, 1d, 1d, 1d, 1d, 1d)).forGetter(a ->
                    List.of(a.attunement[0], a.attunement[1], a.attunement[2], a.attunement[3], a.attunement[4], a.attunement[5])),
            Codec.LONG.optionalFieldOf("last_update", 0L).forGetter(a -> a.lastUpdate),
            Codec.BOOL.optionalFieldOf("initialized", false).forGetter(a -> a.initialized)
    ).apply(i, (vis, flux, base, att, last, init) -> {
        ChunkAura aura = new ChunkAura();
        aura.vis = vis;
        aura.flux = flux;
        aura.base = base;
        if (att.size() == 6) {
            for (int k = 0; k < 6; k++) {
                aura.attunement[k] = att.get(k);
            }
        }
        aura.lastUpdate = last;
        aura.initialized = init;
        return aura;
    }));

    public AuraState state() {
        return new AuraState(Math.max(0, vis), Math.max(0, flux), Math.max(0, base));
    }

    public void apply(AuraState state) {
        vis = state.vis();
        flux = state.flux();
        base = state.base();
    }

    public double vis() {
        return vis;
    }

    public double flux() {
        return flux;
    }

    public double base() {
        return base;
    }

    public void setVis(double value) {
        vis = Math.max(0, value);
    }

    public void setFlux(double value) {
        flux = Math.max(0, value);
    }

    public PrimalAttunement attunement() {
        return new PrimalAttunement(attunement.clone());
    }

    public long lastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(long gameTime) {
        lastUpdate = gameTime;
    }

    public boolean initialized() {
        return initialized;
    }

    public void initialize(double baseVis, PrimalAttunement profile, long gameTime) {
        base = baseVis;
        vis = baseVis;
        flux = 0;
        attunement = profile.weights();
        lastUpdate = gameTime;
        initialized = true;
    }
}
