package dev.thaumicunity.common.aura;

import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.core.aura.AuraSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Consequences of heavy flux in a chunk. Phase 3 behaviour: once flux pressure passes the rift threshold the chunk
 * crackles with flux (particles, sounds) and players inside are disoriented; at twice the threshold some flux bleeds
 * into the neighbourhood as a "pulse". Taint seeds and rifts (phase 5) hook in here.
 */
public final class FluxEffects {
    private FluxEffects() {
    }

    static void onAuraUpdated(ServerLevel level, LevelChunk chunk, ChunkAura aura, AuraSettings settings) {
        if (!TUConfig.FLUX_ENABLED.get()) {
            return;
        }
        double pressure = aura.state().fluxPressure();
        if (pressure < settings.riftPressure()) {
            return;
        }
        ChunkPos pos = chunk.getPos();
        int x = pos.getMinBlockX() + level.getRandom().nextInt(16);
        int z = pos.getMinBlockZ() + level.getRandom().nextInt(16);
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        BlockPos at = new BlockPos(x, y, z);
        level.sendParticles(ParticleTypes.WITCH, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, 12, 0.6, 0.8, 0.6, 0.02);
        if (level.getRandom().nextFloat() < 0.15f) {
            level.playSound(null, at, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 0.6f, 0.5f);
        }
        if (pressure >= settings.riftPressure() * 2.0) {
            for (ServerPlayer player : level.players()) {
                if (player.chunkPosition().equals(pos) && !player.isCreative() && !player.isSpectator()) {
                    player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, 120, 0, true, true));
                    player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0, true, true));
                }
            }
            // Bleed a pulse of flux outwards so that one polluted chunk does not stay at extreme values forever.
            double pulse = aura.flux() * 0.05;
            aura.setFlux(aura.flux() - pulse);
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                if (level.hasChunk(pos.x() + d[0], pos.z() + d[1])) {
                    LevelChunk n = level.getChunk(pos.x() + d[0], pos.z() + d[1]);
                    ChunkAura other = AuraManager.ensureInitialized(level, n);
                    other.setFlux(other.flux() + pulse / 4.0);
                    n.markUnsaved();
                }
            }
        }
    }
}
