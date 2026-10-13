package dev.thaumicunity.client.screen;

import dev.thaumicunity.common.blockentity.AlchemicalFurnaceBlockEntity;
import dev.thaumicunity.common.menu.AlchemicalFurnaceMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Alchemical furnace: input and fuel slots, burn and progress indicators, and the essentia gauge. */
public class AlchemicalFurnaceScreen extends AbstractContainerScreen<AlchemicalFurnaceMenu> {
    private static final int GAUGE_X = 120;
    private static final int GAUGE_Y = 16;
    private static final int GAUGE_W = 14;
    private static final int GAUGE_H = 54;

    public AlchemicalFurnaceScreen(AlchemicalFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        GuiUtil.panel(g, x, y, imageWidth, imageHeight);
        GuiUtil.slots(g, menu, x, y);

        // Flame: shrinks as the current fuel item burns down.
        int flame = Math.round(13 * menu.burnFraction());
        g.fill(x + 57, y + 37, x + 71, y + 51, 0xFF4A3A28);
        if (flame > 0) {
            g.fill(x + 58, y + 50 - flame, x + 70, y + 50, 0xFFE07A1F);
            g.fill(x + 61, y + 50 - Math.max(1, flame - 4), x + 67, y + 50, 0xFFFFD25A);
        }

        // Progress arrow from input towards the gauge.
        g.fill(x + 80, y + 33, x + 108, y + 41, 0xFF4A3A28);
        int progress = Math.round(28 * menu.progressFraction());
        if (progress > 0) {
            g.fill(x + 80, y + 34, x + 80 + progress, y + 40, 0xFFB46FE0);
        }

        GuiUtil.gauge(g, x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H, menu.essentiaFraction(), 0xFF8E4FC8);
        for (int i = 1; i < 5; i++) {
            int tickY = y + GAUGE_Y + GAUGE_H * i / 5;
            g.fill(x + GAUGE_X, tickY, x + GAUGE_X + 4, tickY + 1, 0x80FFFFFF);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (GuiUtil.inside(mouseX, mouseY, leftPos + GAUGE_X, topPos + GAUGE_Y, GAUGE_W, GAUGE_H)) {
            int amount = Math.round(menu.essentiaFraction() * AlchemicalFurnaceBlockEntity.CAPACITY);
            g.setTooltipForNextFrame(font, Component.translatable("gui.thaumicunity.alchemical_furnace.essentia", amount,
                    AlchemicalFurnaceBlockEntity.CAPACITY), mouseX, mouseY);
        }
    }
}
