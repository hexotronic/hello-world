package dev.thaumicunity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.thaumicunity.common.blockentity.CrucibleBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
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
        int total = crucible.essentia().total();
        float strength = total == 0 ? 0f : Math.min(1f, 0.35f + total / 200f);
        state.color = ARGB.srgbLerp(strength, ARGB.opaque(WATER), ARGB.opaque(crucible.color()));
        state.time = crucible.getLevel() == null ? 0 : crucible.getLevel().getGameTime() + partialTick;
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
