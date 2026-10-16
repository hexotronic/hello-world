package dev.thaumicunity.client.screen;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.Aspects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drawing helpers shared by the Thaumic Unity screens. Panels and slots are drawn procedurally in a
 * parchment-and-brass palette, so screen layouts never depend on matching background textures.
 */
public final class GuiUtil {
    public static final int BRASS_DARK = 0xFF5C4317;
    public static final int BRASS = 0xFFB08D3C;
    public static final int BRASS_LIGHT = 0xFFE3C677;
    public static final int PARCHMENT_SHADE = 0xFFCDBB94;
    public static final int PARCHMENT = 0xFFEADFC4;
    public static final int INK = 0xFF3B2A1A;
    public static final int INK_FADED = 0xFF7A6448;
    public static final int GOOD = 0xFF2F7D32;
    public static final int BAD = 0xFFA12A22;

    private static final Map<Identifier, Boolean> TEXTURE_EXISTS = new HashMap<>();

    private GuiUtil() {
    }

    /** Panel with a two-tone brass frame and a parchment fill. */
    public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, BRASS_DARK);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, BRASS);
        g.fill(x + 2, y + 2, x + w - 2, y + 3, BRASS_LIGHT);
        g.fill(x + 2, y + 2, x + 3, y + h - 2, BRASS_LIGHT);
        g.fill(x + 3, y + 3, x + w - 3, y + h - 3, PARCHMENT_SHADE);
        g.fill(x + 4, y + 4, x + w - 4, y + h - 4, PARCHMENT);
    }

    /** Sunken frame behind an 16×16 item position. */
    public static void slot(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF6B5638);
        g.fill(x, y, x + 17, y + 17, 0xFFFBF3DE);
        g.fill(x, y, x + 16, y + 16, 0xFFA8977A);
    }

    /** Slot frames for every slot of a menu. */
    public static void slots(GuiGraphicsExtractor g, AbstractContainerMenu menu, int left, int top) {
        for (Slot slot : menu.slots) {
            slot(g, left + slot.x, top + slot.y);
        }
    }

    /** Horizontal bar filled from the left. */
    public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BRASS_DARK);
        g.fill(x, y, x + w, y + h, 0xFF2A2018);
        int filled = Math.round(w * Math.clamp(fraction, 0f, 1f));
        if (filled > 0) {
            g.fill(x, y, x + filled, y + h, color);
            g.fill(x, y, x + filled, y + 1, lighten(color));
        }
    }

    /** Vertical gauge filled from the bottom. */
    public static void gauge(GuiGraphicsExtractor g, int x, int y, int w, int h, float fraction, int color) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, BRASS_DARK);
        g.fill(x, y, x + w, y + h, 0xFF2A2018);
        int filled = Math.round(h * Math.clamp(fraction, 0f, 1f));
        if (filled > 0) {
            g.fill(x, y + h - filled, x + w, y + h, color);
            g.fill(x, y + h - filled, x + 1, y + h, lighten(color));
        }
    }

    public static int opaque(int rgb) {
        return 0xFF000000 | (rgb & 0xFFFFFF);
    }

    public static int lighten(int argb) {
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 60);
        int gr = Math.min(255, ((argb >> 8) & 0xFF) + 60);
        int b = Math.min(255, (argb & 0xFF) + 60);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    public static int darken(int argb, float factor) {
        int r = (int) (((argb >> 16) & 0xFF) * factor);
        int gr = (int) (((argb >> 8) & 0xFF) * factor);
        int b = (int) ((argb & 0xFF) * factor);
        return (argb & 0xFF000000) | (r << 16) | (gr << 8) | b;
    }

    /** True when a texture exists in the loaded resource packs (cached until the next resource reload). */
    public static boolean textureExists(Identifier texture) {
        return TEXTURE_EXISTS.computeIfAbsent(texture,
                t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    public static void clearTextureCache() {
        TEXTURE_EXISTS.clear();
    }

    /** Texture of an aspect glyph: {@code <namespace>:textures/aspect/<path>.png}. */
    public static Identifier aspectTexture(Identifier aspect) {
        return Identifier.fromNamespaceAndPath(aspect.getNamespace(), "textures/aspect/" + aspect.getPath() + ".png");
    }

    /**
     * Draws a 16×16 aspect glyph. Aspects added by datapacks without a texture get a coloured disc with their
     * initial instead, so they are still recognisable.
     */
    public static void aspectIcon(GuiGraphicsExtractor g, Font font, RegistryAccess access, Identifier aspect, int x, int y) {
        Identifier texture = aspectTexture(aspect);
        if (textureExists(texture)) {
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0f, 0f, 16, 16, 16, 16);
            return;
        }
        int color = opaque(Aspects.color(access, aspect));
        g.fill(x + 3, y + 1, x + 13, y + 15, color);
        g.fill(x + 1, y + 3, x + 15, y + 13, color);
        g.fill(x + 4, y + 2, x + 12, y + 4, lighten(color));
        String initial = aspect.getPath().isEmpty() ? "?" : aspect.getPath().substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        g.centeredText(font, initial, x + 8, y + 4, 0xFF1A1A1A);
    }

    /** Aspect glyph with an amount in the bottom-right corner. */
    public static void aspectStack(GuiGraphicsExtractor g, Font font, RegistryAccess access, Identifier aspect, int amount,
                                   int x, int y) {
        aspectIcon(g, font, access, aspect, x, y);
        if (amount > 1 || amount == 0) {
            String text = Integer.toString(amount);
            g.text(font, text, x + 17 - font.width(text), y + 9, 0xFFFFFFFF, true);
        }
    }

    /** Tooltip lines for an aspect: coloured name, meaning, and its components. */
    public static List<Component> aspectTooltip(RegistryAccess access, Identifier id) {
        List<Component> lines = new ArrayList<>();
        int color = Aspects.color(access, id);
        lines.add(Aspect.displayName(id, color));
        lines.add(Component.translatable(Aspect.translationKey(id) + ".meaning").withStyle(ChatFormatting.GRAY));
        Aspects.get(access, id).ifPresent(aspect -> {
            if (aspect.isPrimal()) {
                lines.add(Component.translatable("tooltip.thaumicunity.aspect.primal").withStyle(ChatFormatting.DARK_AQUA));
            } else if (aspect.components().size() == 2) {
                Identifier a = aspect.components().get(0);
                Identifier b = aspect.components().get(1);
                lines.add(Component.translatable("tooltip.thaumicunity.aspect.components",
                        Aspect.displayName(a, Aspects.color(access, a)), Aspect.displayName(b, Aspects.color(access, b)))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        });
        return lines;
    }

    public static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void playUi(Holder<SoundEvent> sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}
