package dev.thaumicunity.client.hud;

import dev.thaumicunity.client.ClientAura;
import dev.thaumicunity.client.screen.GuiUtil;
import dev.thaumicunity.common.config.TUClientConfig;
import dev.thaumicunity.common.network.AuraSnapshotPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Small aura readout in the top-left corner while the server sends aura snapshots (holding a thaumometer or
 * wearing revealing goggles): vis against the chunk's base level, and flux.
 */
public final class AuraHud {
    private static final int VIS_COLOR = 0xFFB98AF0;
    private static final int FLUX_COLOR = 0xFF7A2E8C;

    private AuraHud() {
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!TUClientConfig.SHOW_AURA_HUD.get() || minecraft.level == null) {
            return;
        }
        AuraSnapshotPayload aura = ClientAura.current(minecraft.level.getGameTime());
        if (aura == null) {
            return;
        }
        Font font = minecraft.font;
        int x = 6;
        int y = 6;
        int w = 96;
        g.fill(x - 2, y - 2, x + w + 2, y + 34, 0x90201812);
        g.text(font, Component.translatable("hud.thaumicunity.aura.vis", Math.round(aura.vis()), Math.round(aura.base())), x, y,
                VIS_COLOR, true);
        // The bar shows up to twice the base level; above that it stays full.
        float base = Math.max(1f, aura.base());
        GuiUtil.bar(g, x, y + 10, w, 4, aura.vis() / (2 * base), VIS_COLOR);
        g.fill(x + w / 2, y + 9, x + w / 2 + 1, y + 15, 0xFFFFFFFF);
        g.text(font, Component.translatable("hud.thaumicunity.aura.flux", Math.round(aura.flux())), x, y + 18, FLUX_COLOR | 0x00303030, true);
        GuiUtil.bar(g, x, y + 28, w, 3, aura.flux() / base, FLUX_COLOR);
    }
}
