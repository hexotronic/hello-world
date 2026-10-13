package dev.thaumicunity.client.screen;

import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.menu.ArcaneWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

import java.util.ArrayList;
import java.util.List;

/**
 * Arcane workbench: a 3×3 grid ringed by the six primal vis costs of the current recipe, with the aura's
 * available vis under the result slot.
 */
public class ArcaneWorkbenchScreen extends AbstractContainerScreen<ArcaneWorkbenchMenu> {
    /** Icon centres of the six primals (order of {@link Aspects#PRIMALS}) on a ring around the grid. */
    private static final int[][] RING = {{75, 20}, {123, 48}, {123, 102}, {75, 130}, {27, 102}, {27, 48}};
    private static final int ARROW_X = 128;
    private static final int ARROW_Y = 61;

    public ArcaneWorkbenchScreen(ArcaneWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 194, 234);
        this.inventoryLabelX = 16;
        this.inventoryLabelY = 141;
    }

    @Override
    protected void init() {
        super.init();
        // The aer icon sits at the top centre, so the title moves to the right.
        this.titleLabelX = imageWidth - 8 - font.width(title);
    }

    private RegistryAccess access() {
        return minecraft.level != null ? minecraft.level.registryAccess() : RegistryAccess.EMPTY;
    }

    private boolean needsVis() {
        for (int i = 0; i < 6; i++) {
            if (menu.requiredVis(i) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        GuiUtil.panel(g, x, y, imageWidth, imageHeight);
        g.fill(x + 35, y + 35, x + 115, y + 115, GuiUtil.PARCHMENT_SHADE);
        g.fill(x + 36, y + 36, x + 114, y + 114, GuiUtil.PARCHMENT);
        GuiUtil.slots(g, menu, x, y);

        g.fill(x + 155, y + 53, x + 181, y + 79, GuiUtil.BRASS_DARK);
        g.fill(x + 156, y + 54, x + 180, y + 78, GuiUtil.BRASS);
        GuiUtil.slot(g, x + 160, y + 58);
        boolean blocked = needsVis() && !menu.affordable();
        int arrowColor = blocked ? GuiUtil.BAD : GuiUtil.BRASS;
        g.fill(x + ARROW_X, y + ARROW_Y + 3, x + ARROW_X + 18, y + ARROW_Y + 7, arrowColor);
        for (int i = 0; i < 5; i++) {
            g.fill(x + ARROW_X + 18 + i, y + ARROW_Y + i, x + ARROW_X + 19 + i, y + ARROW_Y + 10 - i, arrowColor);
        }

        RegistryAccess access = access();
        for (int i = 0; i < RING.length; i++) {
            Identifier primal = Aspects.PRIMALS.get(i);
            int cx = x + RING[i][0];
            int cy = y + RING[i][1];
            int required = menu.requiredVis(i);
            int color = GuiUtil.opaque(Aspects.color(access, primal));
            g.fill(cx - 10, cy - 10, cx + 10, cy + 10, required > 0 ? color : GuiUtil.PARCHMENT_SHADE);
            g.fill(cx - 9, cy - 9, cx + 9, cy + 9, GuiUtil.PARCHMENT);
            GuiUtil.aspectIcon(g, font, access, primal, cx - 8, cy - 8);
            if (required > 0) {
                String text = Integer.toString(required);
                g.text(font, text, cx + 9 - font.width(text), cy + 2, 0xFFFFFFFF, true);
            } else {
                g.fill(cx - 8, cy - 8, cx + 8, cy + 8, 0xA0EADFC4);
            }
        }

        Component aura = Component.translatable("gui.thaumicunity.arcane_workbench.aura", menu.auraVis());
        g.text(font, aura, x + 168 - font.width(aura) / 2, y + 84, blocked ? GuiUtil.BAD : GuiUtil.INK, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < RING.length; i++) {
            int cx = leftPos + RING[i][0];
            int cy = topPos + RING[i][1];
            if (GuiUtil.inside(mouseX, mouseY, cx - 9, cy - 9, 18, 18)) {
                List<Component> lines = new ArrayList<>(GuiUtil.aspectTooltip(access(), Aspects.PRIMALS.get(i)));
                lines.add(Component.translatable("gui.thaumicunity.arcane_workbench.required", menu.requiredVis(i)));
                g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            }
        }
        if (needsVis() && !menu.affordable()
                && GuiUtil.inside(mouseX, mouseY, leftPos + ARROW_X, topPos + ARROW_Y, 24, 10)) {
            g.setTooltipForNextFrame(font, Component.translatable("gui.thaumicunity.arcane_workbench.lacking"), mouseX, mouseY);
        }
    }
}
