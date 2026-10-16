package dev.thaumicunity.client.screen;

import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.recipe.ArcaneCraftingRecipe;
import dev.thaumicunity.common.recipe.CrucibleRecipe;
import dev.thaumicunity.common.recipe.InfusionRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipePattern;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws one crucible, arcane or infusion recipe into a Thaumonomicon page and reports what the mouse is over, so
 * the screen can show the matching tooltip. Ingredients with several options cycle once per second.
 */
final class RecipePageRenderer {
    static final int WIDTH = 150;
    static final int HEIGHT = 112;

    private final Font font;
    private final RegistryAccess access;
    private final ContextMap displayContext;
    private final long time;
    private final int mouseX;
    private final int mouseY;
    private ItemStack hoveredStack = ItemStack.EMPTY;
    private @Nullable Identifier hoveredAspect;

    RecipePageRenderer(Font font, RegistryAccess access, ContextMap displayContext, long time, int mouseX, int mouseY) {
        this.font = font;
        this.access = access;
        this.displayContext = displayContext;
        this.time = time;
        this.mouseX = mouseX;
        this.mouseY = mouseY;
    }

    ItemStack hoveredStack() {
        return hoveredStack;
    }

    @Nullable Identifier hoveredAspect() {
        return hoveredAspect;
    }

    /**
     * Draws the recipe with its top-left corner at (x, y). Thaumic Unity recipes get their own layouts; crafting
     * table and furnace recipes are drawn from their vanilla recipe displays. Returns false for anything else.
     */
    boolean draw(GuiGraphicsExtractor g, Recipe<?> recipe, int x, int y) {
        return switch (recipe) {
            case CrucibleRecipe crucible -> {
                drawCrucible(g, crucible, x, y);
                yield true;
            }
            case ArcaneCraftingRecipe arcane -> {
                drawArcane(g, arcane, x, y);
                yield true;
            }
            case InfusionRecipe infusion -> {
                drawInfusion(g, infusion, x, y);
                yield true;
            }
            default -> recipe.display().stream().anyMatch(display -> drawDisplay(g, display, x, y));
        };
    }

    private boolean drawDisplay(GuiGraphicsExtractor g, RecipeDisplay display, int x, int y) {
        switch (display) {
            case ShapedCraftingRecipeDisplay shaped -> {
                header(g, Component.translatable("gui.thaumicunity.recipe.crafting"), x, y);
                drawGrid(g, x + 6, y + 14);
                for (int row = 0; row < shaped.height(); row++) {
                    for (int col = 0; col < shaped.width(); col++) {
                        item(g, cycle(shaped.ingredients().get(col + row * shaped.width())), x + 6 + col * 18, y + 14 + row * 18);
                    }
                }
                arrow(g, x + 66, y + 34);
                result(g, first(shaped.result()), x + 104, y + 30);
                return true;
            }
            case ShapelessCraftingRecipeDisplay shapeless -> {
                header(g, Component.translatable("gui.thaumicunity.recipe.crafting"), x, y);
                drawGrid(g, x + 6, y + 14);
                List<SlotDisplay> ingredients = shapeless.ingredients();
                for (int i = 0; i < ingredients.size() && i < 9; i++) {
                    item(g, cycle(ingredients.get(i)), x + 6 + (i % 3) * 18, y + 14 + (i / 3) * 18);
                }
                g.text(font, Component.translatable("gui.thaumicunity.recipe.shapeless"), x + 6, y + 70, GuiUtil.INK_FADED, false);
                arrow(g, x + 66, y + 34);
                result(g, first(shapeless.result()), x + 104, y + 30);
                return true;
            }
            case FurnaceRecipeDisplay furnace -> {
                header(g, Component.translatable("gui.thaumicunity.recipe.smelting"), x, y);
                GuiUtil.slot(g, x + 30, y + 26);
                item(g, cycle(furnace.ingredient()), x + 30, y + 26);
                g.fill(x + 32, y + 46, x + 44, y + 58, 0xFF4A3A28);
                g.fill(x + 34, y + 50, x + 42, y + 58, 0xFFE07A1F);
                arrow(g, x + 66, y + 34);
                result(g, first(furnace.result()), x + 104, y + 30);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void drawGrid(GuiGraphicsExtractor g, int x, int y) {
        for (int i = 0; i < 9; i++) {
            GuiUtil.slot(g, x + (i % 3) * 18, y + (i / 3) * 18);
        }
    }

    private ItemStack first(SlotDisplay display) {
        return display.resolveForFirstStack(displayContext);
    }

    /** One of the slot's possible items, changing every second. */
    private ItemStack cycle(SlotDisplay display) {
        List<ItemStack> options = display.resolveForStacks(displayContext);
        return options.isEmpty() ? ItemStack.EMPTY : options.get((int) ((time / 20) % options.size()));
    }

    private void drawCrucible(GuiGraphicsExtractor g, CrucibleRecipe recipe, int x, int y) {
        header(g, Component.translatable("gui.thaumicunity.recipe.crucible"), x, y);
        // Cauldron outline under the catalyst.
        g.fill(x + 22, y + 44, x + 58, y + 70, GuiUtil.BRASS_DARK);
        g.fill(x + 24, y + 44, x + 56, y + 68, 0xFF3E5E8C);
        g.fill(x + 24, y + 44, x + 56, y + 47, 0xFF6F95C9);
        item(g, cycle(recipe.catalyst()), x + 32, y + 22);
        arrow(g, x + 68, y + 50);
        result(g, recipe.result().create(), x + 104, y + 46);
        aspects(g, recipe.aspects(), x + 4, y + 82, WIDTH - 8);
    }

    private void drawArcane(GuiGraphicsExtractor g, ArcaneCraftingRecipe recipe, int x, int y) {
        header(g, Component.translatable("gui.thaumicunity.recipe.arcane"), x, y);
        int gx = x + 6;
        int gy = y + 14;
        drawGrid(g, gx, gy);
        Optional<ShapedRecipePattern> pattern = recipe.pattern();
        if (pattern.isPresent()) {
            ShapedRecipePattern shaped = pattern.get();
            List<Optional<Ingredient>> cells = shaped.ingredients();
            for (int row = 0; row < shaped.height(); row++) {
                for (int col = 0; col < shaped.width(); col++) {
                    Optional<Ingredient> cell = cells.get(col + row * shaped.width());
                    if (cell.isPresent()) {
                        item(g, cycle(cell.get()), gx + col * 18, gy + row * 18);
                    }
                }
            }
        } else {
            List<Ingredient> ingredients = recipe.ingredients();
            for (int i = 0; i < ingredients.size() && i < 9; i++) {
                item(g, cycle(ingredients.get(i)), gx + (i % 3) * 18, gy + (i / 3) * 18);
            }
            g.text(font, Component.translatable("gui.thaumicunity.recipe.shapeless"), gx, gy + 56, GuiUtil.INK_FADED, false);
        }
        arrow(g, x + 66, y + 34);
        result(g, recipe.result().create(), x + 104, y + 30);
        aspects(g, recipe.vis(), x + 4, y + 82, WIDTH - 8);
    }

    private void drawInfusion(GuiGraphicsExtractor g, InfusionRecipe recipe, int x, int y) {
        header(g, Component.translatable("gui.thaumicunity.recipe.infusion"), x, y);
        int cx = x + 46;
        int cy = y + 46;
        List<Ingredient> components = recipe.components();
        int count = components.size();
        for (int i = 0; i < count; i++) {
            double angle = -Math.PI / 2 + i * 2 * Math.PI / count;
            int px = cx + (int) Math.round(Math.cos(angle) * 30) - 8;
            int py = cy + (int) Math.round(Math.sin(angle) * 30) - 8;
            g.fill(px - 1, py - 1, px + 17, py + 17, 0x40B08D3C);
            item(g, cycle(components.get(i)), px, py);
        }
        GuiUtil.slot(g, cx - 8, cy - 8);
        item(g, cycle(recipe.central()), cx - 8, cy - 8);
        arrow(g, x + 86, y + 42);
        result(g, recipe.result().create(), x + 116, y + 38);
        Component instability = Component.translatable("gui.thaumicunity.recipe.instability",
                Component.translatable("gui.thaumicunity.recipe.instability." + instabilityLevel(recipe.instability())));
        g.text(font, instability, x + 110 - font.width(instability) / 2 + 12, y + 62, GuiUtil.INK_FADED, false);
        aspects(g, recipe.essentia(), x + 4, y + 82, WIDTH - 8);
    }

    private static String instabilityLevel(int instability) {
        if (instability <= 2) {
            return "low";
        }
        return instability <= 5 ? "medium" : instability <= 8 ? "high" : "extreme";
    }

    private void header(GuiGraphicsExtractor g, Component title, int x, int y) {
        g.text(font, title, x + 4, y, GuiUtil.INK, false);
    }

    private void arrow(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y + 3, x + 18, y + 6, GuiUtil.BRASS);
        for (int i = 0; i < 5; i++) {
            g.fill(x + 18 + i, y + i - 1, x + 19 + i, y + 10 - i, GuiUtil.BRASS);
        }
    }

    private void result(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        g.fill(x - 5, y - 5, x + 21, y + 21, GuiUtil.BRASS_DARK);
        g.fill(x - 4, y - 4, x + 20, y + 20, GuiUtil.BRASS);
        GuiUtil.slot(g, x, y);
        item(g, stack, x, y);
        if (stack.getCount() > 1) {
            g.itemDecorations(font, stack, x, y);
        }
    }

    private void item(GuiGraphicsExtractor g, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        g.item(stack, x, y);
        if (GuiUtil.inside(mouseX, mouseY, x, y, 16, 16)) {
            hoveredStack = stack;
        }
    }

    private void aspects(GuiGraphicsExtractor g, AspectList list, int x, int y, int width) {
        int perRow = Math.max(1, width / 20);
        int i = 0;
        for (var entry : list.asMap().entrySet()) {
            int ax = x + (i % perRow) * 20;
            int ay = y + (i / perRow) * 18;
            GuiUtil.aspectStack(g, font, access, entry.getKey(), entry.getValue(), ax, ay);
            if (GuiUtil.inside(mouseX, mouseY, ax, ay, 16, 16)) {
                hoveredAspect = entry.getKey();
            }
            i++;
        }
    }

    /** One of the ingredient's items, changing every second. */
    private ItemStack cycle(Ingredient ingredient) {
        List<Holder<Item>> options = new ArrayList<>();
        ingredient.items().limit(64).forEach(options::add);
        if (options.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(options.get((int) ((time / 20) % options.size())));
    }
}
