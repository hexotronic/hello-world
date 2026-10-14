package dev.thaumicunity.client.smoke;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.client.screen.GuiUtil;
import dev.thaumicunity.common.aspect.Aspects;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Smoke test page showing every Thaumic Unity item with its name, or every aspect glyph, for one screenshot. */
public class SmokeGalleryScreen extends Screen {
    public enum Page { ITEMS, ASPECTS }

    private final Page page;

    public SmokeGalleryScreen(Page page) {
        super(Component.literal("Thaumic Unity " + page.name().toLowerCase(java.util.Locale.ROOT)));
        this.page = page;
    }

    private RegistryAccess access() {
        return minecraft.level != null ? minecraft.level.registryAccess() : RegistryAccess.EMPTY;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        GuiUtil.panel(g, 4, 4, width - 8, height - 8);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        g.text(font, title, 10, 9, GuiUtil.INK, false);
        if (page == Page.ITEMS) {
            drawItems(g);
        } else {
            drawAspects(g);
        }
    }

    private void drawItems(GuiGraphicsExtractor g) {
        List<Item> items = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ThaumicUnity.MOD_ID)) {
                items.add(item);
            }
        }
        int rowHeight = 18;
        int maxRows = Math.max(1, (height - 32) / rowHeight);
        int columns = Math.max(3, (items.size() + maxRows - 1) / maxRows);
        int rows = (items.size() + columns - 1) / columns;
        int columnWidth = (width - 20) / columns;
        for (int i = 0; i < items.size(); i++) {
            int x = 10 + (i / rows) * columnWidth;
            int y = 22 + (i % rows) * rowHeight;
            ItemStack stack = new ItemStack(items.get(i));
            g.item(stack, x, y);
            g.text(font, font.plainSubstrByWidth(stack.getHoverName().getString(), columnWidth - 22), x + 19, y + 4,
                    GuiUtil.INK, false);
        }
    }

    private void drawAspects(GuiGraphicsExtractor g) {
        RegistryAccess access = access();
        List<Identifier> aspects = new ArrayList<>(Aspects.all(access).keySet());
        aspects.sort(Comparator.comparingInt((Identifier id) -> Aspects.tier(access, id)).thenComparing(Identifier::toString));
        int cell = 20;
        int columns = Math.max(1, (width - 20) / cell);
        for (int i = 0; i < aspects.size(); i++) {
            int x = 10 + (i % columns) * cell;
            int y = 22 + (i / columns) * cell;
            GuiUtil.aspectIcon(g, font, access, aspects.get(i), x, y);
        }
        g.text(font, aspects.size() + " aspects", 10, height - 18, GuiUtil.INK_FADED, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
