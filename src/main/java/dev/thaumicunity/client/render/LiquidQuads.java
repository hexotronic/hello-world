package dev.thaumicunity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.thaumicunity.ThaumicUnity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Tinted, translucent liquid surfaces for block entity renderers. The texture is light grey and tileable; its UVs are
 * offset by a drift value so the surface appears to move.
 */
final class LiquidQuads {
    static final Identifier TEXTURE = ThaumicUnity.id("textures/misc/essentia_liquid.png");

    private LiquidQuads() {
    }

    private static RenderType renderType() {
        return RenderTypes.entityTranslucent(TEXTURE);
    }

    /** A horizontal surface at height {@code y} spanning the given block-local rectangle. */
    static void submitTop(PoseStack pose, SubmitNodeCollector collector, float x0, float x1, float z0, float z1, float y,
                          float drift, int argb, int light) {
        collector.submitCustomGeometry(pose, renderType(), (p, buffer) -> {
            float u0 = x0 + drift;
            float u1 = x1 + drift;
            float v0 = z0 + drift * 0.6f;
            float v1 = z1 + drift * 0.6f;
            vertex(p, buffer, x0, y, z0, u0, v0, argb, light, 0, 1, 0);
            vertex(p, buffer, x0, y, z1, u0, v1, argb, light, 0, 1, 0);
            vertex(p, buffer, x1, y, z1, u1, v1, argb, light, 0, 1, 0);
            vertex(p, buffer, x1, y, z0, u1, v0, argb, light, 0, 1, 0);
        });
    }

    /** A box of liquid (top and four sides) from {@code y0} to {@code y1}. */
    static void submitBox(PoseStack pose, SubmitNodeCollector collector, float x0, float x1, float z0, float z1, float y0,
                          float y1, float drift, int argb, int light) {
        // Sides are a little darker than the surface, as if lit from above.
        int side = (argb & 0xFF000000) | (scale(argb >> 16) << 16) | (scale(argb >> 8) << 8) | scale(argb);
        collector.submitCustomGeometry(pose, renderType(), (p, buffer) -> {
            float h = y1 - y0;
            // Top.
            vertex(p, buffer, x0, y1, z0, x0 + drift, z0, argb, light, 0, 1, 0);
            vertex(p, buffer, x0, y1, z1, x0 + drift, z1, argb, light, 0, 1, 0);
            vertex(p, buffer, x1, y1, z1, x1 + drift, z1, argb, light, 0, 1, 0);
            vertex(p, buffer, x1, y1, z0, x1 + drift, z0, argb, light, 0, 1, 0);
            // North (z0) and south (z1).
            vertex(p, buffer, x0, y0, z0, x0, drift, side, light, 0, 0, -1);
            vertex(p, buffer, x0, y1, z0, x0, drift + h, side, light, 0, 0, -1);
            vertex(p, buffer, x1, y1, z0, x1, drift + h, side, light, 0, 0, -1);
            vertex(p, buffer, x1, y0, z0, x1, drift, side, light, 0, 0, -1);
            vertex(p, buffer, x1, y0, z1, x1, drift, side, light, 0, 0, 1);
            vertex(p, buffer, x1, y1, z1, x1, drift + h, side, light, 0, 0, 1);
            vertex(p, buffer, x0, y1, z1, x0, drift + h, side, light, 0, 0, 1);
            vertex(p, buffer, x0, y0, z1, x0, drift, side, light, 0, 0, 1);
            // West (x0) and east (x1).
            vertex(p, buffer, x0, y0, z1, z1, drift, side, light, -1, 0, 0);
            vertex(p, buffer, x0, y1, z1, z1, drift + h, side, light, -1, 0, 0);
            vertex(p, buffer, x0, y1, z0, z0, drift + h, side, light, -1, 0, 0);
            vertex(p, buffer, x0, y0, z0, z0, drift, side, light, -1, 0, 0);
            vertex(p, buffer, x1, y0, z0, z0, drift, side, light, 1, 0, 0);
            vertex(p, buffer, x1, y1, z0, z0, drift + h, side, light, 1, 0, 0);
            vertex(p, buffer, x1, y1, z1, z1, drift + h, side, light, 1, 0, 0);
            vertex(p, buffer, x1, y0, z1, z1, drift, side, light, 1, 0, 0);
        });
    }

    private static int scale(int channel) {
        return (int) ((channel & 0xFF) * 0.8f);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer buffer, float x, float y, float z, float u, float v,
                               int argb, int light, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(argb)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
