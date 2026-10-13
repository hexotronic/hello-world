package dev.thaumicunity.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.thaumicunity.common.blockentity.ArcanePedestalBlockEntity;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Floats and slowly spins the item placed on an arcane pedestal. */
public class ArcanePedestalRenderer implements BlockEntityRenderer<ArcanePedestalBlockEntity, ArcanePedestalRenderer.State> {
    private final ItemModelResolver itemModels;

    public ArcanePedestalRenderer(BlockEntityRendererProvider.Context context) {
        this.itemModels = context.itemModelResolver();
    }

    public static class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float time;
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ArcanePedestalBlockEntity pedestal, State state, float partialTick, Vec3 camera,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(pedestal, state, partialTick, camera, crumbling);
        state.item.clear();
        if (pedestal.getLevel() != null && !pedestal.item().isEmpty()) {
            itemModels.updateForTopItem(state.item, pedestal.item(), ItemDisplayContext.GROUND, pedestal.getLevel(), null,
                    (int) pedestal.getBlockPos().asLong());
            state.time = pedestal.getLevel().getGameTime() + partialTick;
        }
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 1.05 + Math.sin(state.time / 12.0) * 0.05, 0.5);
        pose.rotateDegrees(Axis.YP, state.time * 2.0f);
        pose.scale(0.9f, 0.9f, 0.9f);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
}
