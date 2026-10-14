package dev.thaumicunity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.blockentity.CrucibleBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws the crucible's liquid: plain water is blue, dissolved essentia tints it towards the average aspect colour.
 * The level follows the water amount and the surface drifts slowly; boiling makes it heave.
 */
public class CrucibleRenderer implements BlockEntityRenderer<CrucibleBlockEntity, CrucibleRenderer.State> {
    private static final int WATER = 0x3F76E4;
    /** Inside of the crucible model: walls 2..14, floor top at 4 (in sixteenths). */
    private static final float MIN = 2.01f / 16f;
    private static final float MAX = 13.99f / 16f;
    private static final float FLOOR = 4.2f / 16f;
    private static final float BRIM = 15f / 16f;

    public CrucibleRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static class State extends BlockEntityRenderState {
        float level;
        int color;
        float time;
        boolean boiling;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(CrucibleBlockEntity crucible, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(crucible, state, partialTick, camera, crumbling);
        state.level = (float) crucible.water() / CrucibleBlockEntity.MAX_WATER;
        state.boiling = crucible.isBoiling();
        // Essentia colours the water more strongly the more of it there is.
        AspectList essentia = crucible.essentia();
        int total = essentia.total();
        if (total == 0 || crucible.getLevel() == null) {
            state.color = ARGB.opaque(WATER);
        } else {
            float strength = Math.min(1f, 0.45f + total / 150f);
            state.color = ARGB.srgbLerp(strength, ARGB.opaque(WATER), ARGB.opaque(tone(essentia, crucible.getLevel().registryAccess())));
        }
        state.time = crucible.getLevel() == null ? 0 : crucible.getLevel().getGameTime() + partialTick;
    }

    /**
     * Colour of a mix of essentia. Amounts are weighted by their square so the main ingredient sets the tone, and
     * the result is saturated, since a plain average of many aspect colours is a muddy grey.
     */
    static int tone(AspectList essentia, RegistryAccess access) {
        double r = 0, g = 0, b = 0, weight = 0;
        for (var e : essentia.asMap().entrySet()) {
            int c = Aspects.color(access, e.getKey());
            double w = (double) e.getValue() * e.getValue();
            r += ((c >> 16) & 0xFF) * w;
            g += ((c >> 8) & 0xFF) * w;
            b += (c & 0xFF) * w;
            weight += w;
        }
        r /= weight;
        g /= weight;
        b /= weight;
        double grey = (r + g + b) / 3;
        double k = 1.8;
        return (channel(grey + (r - grey) * k) << 16) | (channel(grey + (g - grey) * k) << 8) | channel(grey + (b - grey) * k);
    }

    private static int channel(double value) {
        return (int) Math.max(0, Math.min(255, Math.round(value)));
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.level <= 0) {
            return;
        }
        float wave = state.boiling ? (float) Math.sin(state.time / 3.0) * 0.006f : 0f;
        float y = FLOOR + (BRIM - FLOOR) * state.level + wave;
        int color = ARGB.color(state.boiling ? 235 : 215, state.color);
        int light = state.lightCoords;
        float drift = state.time / 400f;
        LiquidQuads.submitTop(pose, collector, MIN, MAX, MIN, MAX, y, drift, color, light);
    }
}
