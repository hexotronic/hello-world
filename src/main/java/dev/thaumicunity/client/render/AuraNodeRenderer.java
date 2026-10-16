package dev.thaumicunity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.blockentity.AuraNodeBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Draws an aura node as a glowing orb that always faces the camera: two slowly turning halos in the node's colour
 * around a bright core, sized by the vis it holds and gently pulsing.
 */
public class AuraNodeRenderer implements BlockEntityRenderer<AuraNodeBlockEntity, AuraNodeRenderer.State> {
    private static final Identifier GLOW = ThaumicUnity.id("textures/misc/node_glow.png");
    /** Packed light coordinates for full brightness: nodes glow in the dark. */
    private static final int FULL_BRIGHT = 0xF000F0;
    /** Vis at which a node is drawn at its largest. */
    private static final float LARGE = 120f;

    public AuraNodeRenderer(BlockEntityRendererProvider.Context context) {
    }

    public static class State extends BlockEntityRenderState {
        float size;
        int color;
        float time;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(AuraNodeBlockEntity node, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(node, state, partialTick, camera, crumbling);
        float fill = Mth.clamp((float) node.totalVis() / LARGE, 0f, 1f);
        state.size = 0.22f + 0.45f * Mth.sqrt(fill);
        if (node.getLevel() != null) {
            state.color = node.color(node.getLevel().registryAccess());
            state.time = (node.getLevel().getGameTime() + partialTick) + (node.getBlockPos().asLong() & 0xFF);
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        float pulse = 1f + 0.06f * Mth.sin(state.time * 0.08f);
        float size = state.size * pulse;
        pose.pushPose();
        pose.translate(0.5f, 0.5f, 0.5f);
        // Face the camera: the same rotation the camera's orientation is made of (yaw, then pitch).
        pose.rotateDegrees(Axis.YP, 180f - camera.yRot);
        pose.rotateDegrees(Axis.XP, -camera.xRot);
        int halo = ARGB.color(150, state.color);
        int inner = ARGB.color(130, ARGB.srgbLerp(0.35f, state.color, 0xFFFFFF));
        int core = ARGB.color(235, ARGB.srgbLerp(0.7f, state.color, 0xFFFFFF));
        quad(pose, collector, size * 1.6f, state.time * 0.6f, halo);
        quad(pose, collector, size * 1.1f, -state.time * 0.9f, inner);
        quad(pose, collector, size * 0.5f, 0f, core);
        pose.popPose();
    }

    /** One camera-facing glow quad, turned by {@code degrees} about the view axis. */
    private static void quad(PoseStack pose, SubmitNodeCollector collector, float half, float degrees, int argb) {
        pose.pushPose();
        pose.rotateDegrees(Axis.ZP, degrees);
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(GLOW), (p, buffer) -> {
            // Both windings, so the quad shows whichever way the renderer culls.
            vertex(p, buffer, -half, -half, 0, 1, argb);
            vertex(p, buffer, half, -half, 1, 1, argb);
            vertex(p, buffer, half, half, 1, 0, argb);
            vertex(p, buffer, -half, half, 0, 0, argb);
            vertex(p, buffer, -half, half, 0, 0, argb);
            vertex(p, buffer, half, half, 1, 0, argb);
            vertex(p, buffer, half, -half, 1, 1, argb);
            vertex(p, buffer, -half, -half, 0, 1, argb);
        });
        pose.popPose();
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float u, float v, int argb) {
        buffer.addVertex(pose, x, y, 0)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(pose, 0, 0, 1);
    }
}
