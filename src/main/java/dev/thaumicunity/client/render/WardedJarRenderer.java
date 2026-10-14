package dev.thaumicunity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.blockentity.WardedJarBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Shows the essentia inside a warded jar as a column of liquid in the aspect's colour. */
public class WardedJarRenderer implements BlockEntityRenderer<WardedJarBlockEntity, WardedJarRenderer.State> {
    /** Inside of the jar's glass body (3..13 wide, 0..11 high, in sixteenths), slightly inset. */
    private static final float MIN = 3.4f / 16f;
    private static final float MAX = 12.6f / 16f;
    private static final float BOTTOM = 0.4f / 16f;
    private static final float TOP = 10.6f / 16f;

    public WardedJarRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static class State extends BlockEntityRenderState {
        float fill;
        int color;
        float time;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(WardedJarBlockEntity jar, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(jar, state, partialTick, camera, crumbling);
        state.fill = (float) jar.amount() / WardedJarBlockEntity.CAPACITY;
        if (state.fill > 0 && jar.aspect() != null && jar.getLevel() != null) {
            state.color = Aspects.color(jar.getLevel().registryAccess(), jar.aspect());
            state.time = jar.getLevel().getGameTime() + partialTick;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.fill <= 0) {
            return;
        }
        float top = BOTTOM + (TOP - BOTTOM) * Math.min(1f, state.fill);
        LiquidQuads.submitBox(pose, collector, MIN, MAX, MIN, MAX, BOTTOM, top, state.time / 600f,
                ARGB.color(225, state.color), state.lightCoords);
    }
}
