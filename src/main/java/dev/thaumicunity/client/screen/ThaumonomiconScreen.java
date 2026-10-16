package dev.thaumicunity.client.screen;

import dev.thaumicunity.client.ClientRecipes;
import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.config.TUClientConfig;
import dev.thaumicunity.common.network.ResearchAdvancePayload;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.research.PlayerKnowledge;
import dev.thaumicunity.common.research.ResearchCategory;
import dev.thaumicunity.common.research.ResearchEntry;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.research.ResearchRequirement;
import dev.thaumicunity.common.research.ScanTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The Thaumonomicon: category tabs, a pannable research map per category, and entry pages with the text of every
 * reached stage, the current stage's requirements, a button to complete the stage, and recipe pages.
 */
public class ThaumonomiconScreen extends Screen {
    private static final int GRID = 30;
    private static final int NODE = 24;
    private static final int TAB_W = 26;
    private static final int TAB_H = 24;

    /** Remembered between openings, like a bookmark. */
    private static @Nullable Identifier lastCategory;
    private static final Map<Identifier, double[]> PAN = new LinkedHashMap<>();
    private static @Nullable Identifier lastEntry;

    private int bookX;
    private int bookY;
    private int bookW;
    private int bookH;
    private List<Map.Entry<Identifier, ResearchCategory>> categories = List.of();
    private @Nullable Identifier category;
    private @Nullable Identifier entry;
    private int textScroll;
    private int recipeIndex;
    /** Stage of the open entry when its widgets were built. */
    private int shownStage = -1;
    private boolean dragging;

    public ThaumonomiconScreen() {
        super(Component.translatable("item.thaumicunity.thaumonomicon"));
        this.entry = lastEntry;
    }

    /** Makes the next Thaumonomicon opened show this category and entry (null for the category's map). */
    public static void bookmark(@Nullable Identifier categoryId, @Nullable Identifier entryId) {
        lastCategory = categoryId;
        lastEntry = entryId;
    }

    // --- data

    private RegistryAccess access() {
        return minecraft.level != null ? minecraft.level.registryAccess() : RegistryAccess.EMPTY;
    }

    private Player player() {
        return minecraft.player;
    }

    private List<Map.Entry<Identifier, ResearchCategory>> visibleCategories() {
        List<Map.Entry<Identifier, ResearchCategory>> result = new ArrayList<>();
        access().lookup(TURegistries.RESEARCH_CATEGORY).ifPresent(registry -> registry.entrySet().forEach(e -> {
            ResearchCategory value = e.getValue();
            if (value.requires().isEmpty() || ResearchManager.isComplete(player(), value.requires().get())) {
                result.add(Map.entry(e.getKey().identifier(), value));
            }
        }));
        result.sort(Comparator.<Map.Entry<Identifier, ResearchCategory>>comparingInt(e -> e.getValue().order())
                .thenComparing(e -> e.getKey().toString()));
        return result;
    }

    private Optional<ResearchCategory> currentCategory() {
        return categories.stream().filter(e -> e.getKey().equals(category)).map(Map.Entry::getValue).findFirst();
    }

    private Map<Identifier, ResearchEntry> entriesOf(Identifier categoryId) {
        Map<Identifier, ResearchEntry> result = new LinkedHashMap<>();
        ResearchManager.entries(access()).ifPresent(registry -> registry.entrySet().forEach(e -> {
            if (e.getValue().category().equals(categoryId)) {
                result.put(e.getKey().identifier(), e.getValue());
            }
        }));
        return result;
    }

    private enum NodeState { COMPLETE, IN_PROGRESS, AVAILABLE, LOCKED }

    private NodeState state(Identifier id, ResearchEntry value) {
        int stage = ResearchManager.knowledge(player()).stage(id);
        if (stage >= value.stageCount()) {
            return NodeState.COMPLETE;
        }
        if (!ResearchManager.isAvailable(player(), value)) {
            return NodeState.LOCKED;
        }
        return stage > 0 ? NodeState.IN_PROGRESS : NodeState.AVAILABLE;
    }

    private static boolean visible(NodeState state, ResearchEntry value) {
        return state != NodeState.LOCKED || !value.hidden();
    }

    private static ItemStack icon(Identifier itemId) {
        return BuiltInRegistries.ITEM.getOptional(itemId).map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    // --- layout

    @Override
    protected void init() {
        bookW = Math.min(width - 2 * TAB_W - 16, 380);
        bookH = Math.min(height - 16, 250);
        bookX = (width - bookW) / 2;
        bookY = (height - bookH) / 2;
        categories = visibleCategories();
        if (category == null || currentCategory().isEmpty()) {
            category = lastCategory != null && categories.stream().anyMatch(e -> e.getKey().equals(lastCategory))
                    ? lastCategory : categories.isEmpty() ? null : categories.getFirst().getKey();
        }
        if (entry != null && ResearchManager.entry(access(), entry).isEmpty()) {
            entry = null;
        }
        if (entry != null) {
            initEntryWidgets();
        }
    }

    private void initEntryWidgets() {
        addRenderableWidget(Button.builder(Component.translatable("gui.thaumicunity.book.back"), b -> closeEntry())
                .bounds(bookX + 10, bookY + bookH - 26, 50, 18).build());
        Identifier id = entry;
        ResearchEntry value = ResearchManager.entry(access(), id).orElseThrow();
        int stage = ResearchManager.knowledge(player()).stage(id);
        shownStage = stage;
        int rightX = bookX + bookW / 2 + 8;
        if (stage < value.stageCount() && ResearchManager.isAvailable(player(), value)) {
            Button advance = Button.builder(Component.translatable("gui.thaumicunity.book.advance"), b -> {
                ClientPacketDistributor.sendToServer(new ResearchAdvancePayload(id));
                GuiUtil.playUi(TUSounds.PAGE, 1.2f);
            }).bounds(rightX, requirementsBottom(value, stage) + 4, bookW / 2 - 24, 18).build();
            advance.active = allMet(id, value, stage) || player().isCreative();
            addRenderableWidget(advance);
        }
        List<Identifier> recipes = reachedRecipes(value, stage);
        if (recipes.size() > 1) {
            int y = bookY + bookH - 26;
            addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                recipeIndex = Math.floorMod(recipeIndex - 1, recipes.size());
                GuiUtil.playUi(TUSounds.PAGE, 1.4f);
            }).bounds(rightX, y, 18, 18).build());
            addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                recipeIndex = Math.floorMod(recipeIndex + 1, recipes.size());
                GuiUtil.playUi(TUSounds.PAGE, 1.4f);
            }).bounds(bookX + bookW - 30, y, 18, 18).build());
        }
    }

    private int mapX() {
        return bookX + 8;
    }

    private int mapY() {
        return bookY + 8;
    }

    private int mapW() {
        return bookW - 16;
    }

    private int mapH() {
        return bookH - 16;
    }

    private double[] pan() {
        return PAN.computeIfAbsent(category, c -> {
            // Centre the map on its entries the first time a category is opened.
            Map<Identifier, ResearchEntry> entries = entriesOf(c);
            if (entries.isEmpty()) {
                return new double[]{0, 0};
            }
            double sx = 0;
            double sy = 0;
            for (ResearchEntry e : entries.values()) {
                sx += e.x();
                sy += e.y();
            }
            return new double[]{sx / entries.size() * GRID, sy / entries.size() * GRID};
        });
    }

    private int nodeX(ResearchEntry value) {
        return mapX() + mapW() / 2 + value.x() * GRID - (int) pan()[0] - NODE / 2;
    }

    private int nodeY(ResearchEntry value) {
        return mapY() + mapH() / 2 + value.y() * GRID - (int) pan()[1] - NODE / 2;
    }

    private int tabX(int index) {
        return index < tabsPerSide() ? bookX - TAB_W + 2 : bookX + bookW - 2;
    }

    private int tabY(int index) {
        return bookY + 8 + (index % tabsPerSide()) * (TAB_H + 2);
    }

    private int tabsPerSide() {
        return Math.max(1, (bookH - 16) / (TAB_H + 2));
    }

    // --- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        for (int i = 0; i < categories.size(); i++) {
            boolean selected = categories.get(i).getKey().equals(category);
            int tx = tabX(i);
            int ty = tabY(i);
            int color = GuiUtil.opaque(categories.get(i).getValue().color());
            g.fill(tx, ty, tx + TAB_W, ty + TAB_H, selected ? GuiUtil.BRASS_LIGHT : GuiUtil.BRASS_DARK);
            g.fill(tx + 1, ty + 1, tx + TAB_W - 1, ty + TAB_H - 1, selected ? color : GuiUtil.darken(color, 0.6f));
            g.item(icon(categories.get(i).getValue().icon()), tx + 5, ty + 4);
        }
        GuiUtil.panel(g, bookX, bookY, bookW, bookH);
        if (entry == null) {
            drawMap(g, mouseX, mouseY);
        } else {
            g.fill(bookX + bookW / 2 - 1, bookY + 6, bookX + bookW / 2 + 1, bookY + bookH - 6, GuiUtil.PARCHMENT_SHADE);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (entry != null) {
            drawEntry(g, mouseX, mouseY);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        if (entry == null) {
            mapTooltip(g, mouseX, mouseY);
        }
        for (int i = 0; i < categories.size(); i++) {
            if (GuiUtil.inside(mouseX, mouseY, tabX(i), tabY(i), TAB_W, TAB_H)) {
                Identifier id = categories.get(i).getKey();
                PlayerKnowledge knowledge = ResearchManager.knowledge(player());
                g.setComponentTooltipForNextFrame(font, List.of(
                        Component.translatable(ResearchCategory.translationKey(id)),
                        Component.translatable("gui.thaumicunity.book.knowledge",
                                knowledge.knowledge(ResearchRequirement.KnowledgeKind.OBSERVATION, id),
                                knowledge.knowledge(ResearchRequirement.KnowledgeKind.THEORY, id)).withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
            }
        }
    }

    private void drawMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Optional<ResearchCategory> current = currentCategory();
        if (current.isEmpty()) {
            g.centeredText(font, Component.translatable("gui.thaumicunity.book.empty"), bookX + bookW / 2, bookY + bookH / 2,
                    GuiUtil.INK_FADED);
            return;
        }
        int mx = mapX();
        int my = mapY();
        g.enableScissor(mx, my, mx + mapW(), my + mapH());
        Identifier background = current.get().background();
        Identifier texture = Identifier.fromNamespaceAndPath(background.getNamespace(), "textures/gui/research/" + background.getPath() + ".png");
        if (GuiUtil.textureExists(texture)) {
            // Parallax: the background moves at half the speed of the nodes.
            int ox = Math.floorMod((int) (pan()[0] / 2), 256);
            int oy = Math.floorMod((int) (pan()[1] / 2), 256);
            for (int tx = mx - ox; tx < mx + mapW(); tx += 256) {
                for (int ty = my - oy; ty < my + mapH(); ty += 256) {
                    g.blit(RenderPipelines.GUI_TEXTURED, texture, tx, ty, 0f, 0f, 256, 256, 256, 256);
                }
            }
        } else {
            g.fill(mx, my, mx + mapW(), my + mapH(), 0xFFE2D4B2);
        }

        Map<Identifier, ResearchEntry> entries = entriesOf(category);
        int accent = GuiUtil.opaque(current.get().color());
        for (Map.Entry<Identifier, ResearchEntry> e : entries.entrySet()) {
            NodeState childState = state(e.getKey(), e.getValue());
            if (!visible(childState, e.getValue())) {
                continue;
            }
            for (Identifier parent : e.getValue().parents()) {
                ResearchEntry parentEntry = entries.get(parent);
                if (parentEntry == null || !visible(state(parent, parentEntry), parentEntry)) {
                    continue;
                }
                boolean lit = ResearchManager.isComplete(player(), parent);
                connect(g, parentEntry, e.getValue(), lit ? accent : 0xFF8C7B60);
            }
        }
        for (Map.Entry<Identifier, ResearchEntry> e : entries.entrySet()) {
            NodeState state = state(e.getKey(), e.getValue());
            if (visible(state, e.getValue())) {
                drawNode(g, e.getKey(), e.getValue(), state, accent, mouseX, mouseY);
            }
        }
        if (TUClientConfig.BOOK_ANIMATIONS.get()) {
            drawMotes(g, accent);
        }
        g.disableScissor();
    }

    /** Slowly rising, flickering motes in the category colour; positions derive from time, so nothing is stored. */
    private void drawMotes(GuiGraphicsExtractor g, int accent) {
        long ticks = minecraft.level != null ? minecraft.level.getGameTime() : 0;
        float time = ticks + minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (int i = 0; i < 28; i++) {
            long seed = i * 0x9E3779B97F4A7C15L;
            float speed = 0.15f + ((seed >>> 40) & 0xFF) / 255f * 0.35f;
            float px = ((seed >>> 8) & 0xFFFF) / 65535f * mapW() + (float) Math.sin(time / 30f + i) * 6f;
            float py = mapH() - (((seed >>> 24) & 0xFFFF) / 65535f * mapH() + time * speed) % mapH();
            float flicker = 0.5f + 0.5f * (float) Math.sin(time / 7f + i * 1.7f);
            int alpha = (int) (40 + 120 * flicker);
            int x = mapX() + (int) px;
            int y = mapY() + (int) py;
            g.fill(x, y, x + 2, y + 2, (alpha << 24) | (GuiUtil.lighten(accent) & 0xFFFFFF));
        }
    }

    /** L-shaped connection: horizontal from the parent, then vertical into the child. */
    private void connect(GuiGraphicsExtractor g, ResearchEntry from, ResearchEntry to, int color) {
        int x1 = nodeX(from) + NODE / 2;
        int y1 = nodeY(from) + NODE / 2;
        int x2 = nodeX(to) + NODE / 2;
        int y2 = nodeY(to) + NODE / 2;
        g.fill(Math.min(x1, x2) - 1, y1 - 1, Math.max(x1, x2) + 1, y1 + 1, color);
        g.fill(x2 - 1, Math.min(y1, y2) - 1, x2 + 1, Math.max(y1, y2) + 1, color);
    }

    private void drawNode(GuiGraphicsExtractor g, Identifier id, ResearchEntry value, NodeState state, int accent, int mouseX,
                          int mouseY) {
        int x = nodeX(value);
        int y = nodeY(value);
        int frame = switch (state) {
            case COMPLETE -> accent;
            case IN_PROGRESS -> GuiUtil.BRASS_LIGHT;
            case AVAILABLE -> GuiUtil.BRASS;
            case LOCKED -> 0xFF6E6457;
        };
        if (value.special()) {
            g.fill(x - 3, y - 3, x + NODE + 3, y + NODE + 3, GuiUtil.darken(frame, 0.6f));
        }
        g.fill(x - 1, y - 1, x + NODE + 1, y + NODE + 1, GuiUtil.BRASS_DARK);
        g.fill(x, y, x + NODE, y + NODE, frame);
        g.fill(x + 2, y + 2, x + NODE - 2, y + NODE - 2, state == NodeState.LOCKED ? 0xFFB5AA93 : GuiUtil.PARCHMENT);
        if (GuiUtil.inside(mouseX, mouseY, x, y, NODE, NODE) && state != NodeState.LOCKED) {
            g.fill(x + 2, y + 2, x + NODE - 2, y + NODE - 2, 0xFFFFF4D6);
        }
        g.item(icon(value.icon()), x + 4, y + 4);
        if (state == NodeState.LOCKED) {
            g.fill(x + 2, y + 2, x + NODE - 2, y + NODE - 2, 0x99706656);
        } else if (state == NodeState.IN_PROGRESS) {
            int stage = ResearchManager.knowledge(player()).stage(id);
            int filled = (NODE - 4) * stage / value.stageCount();
            g.fill(x + 2, y + NODE - 4, x + NODE - 2, y + NODE - 2, 0xFF4A3A28);
            g.fill(x + 2, y + NODE - 4, x + 2 + filled, y + NODE - 2, accent);
        }
    }

    private void mapTooltip(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        if (category == null || !GuiUtil.inside(mouseX, mouseY, mapX(), mapY(), mapW(), mapH())) {
            return;
        }
        for (Map.Entry<Identifier, ResearchEntry> e : entriesOf(category).entrySet()) {
            ResearchEntry value = e.getValue();
            NodeState state = state(e.getKey(), value);
            if (!visible(state, value) || !GuiUtil.inside(mouseX, mouseY, nodeX(value), nodeY(value), NODE, NODE)) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(ResearchEntry.titleKey(e.getKey())));
            int stage = ResearchManager.knowledge(player()).stage(e.getKey());
            lines.add(switch (state) {
                case COMPLETE -> Component.translatable("gui.thaumicunity.book.complete").withStyle(ChatFormatting.GOLD);
                case IN_PROGRESS -> Component.translatable("gui.thaumicunity.book.stage", stage + 1, value.stageCount())
                        .withStyle(ChatFormatting.YELLOW);
                case AVAILABLE -> Component.translatable("gui.thaumicunity.book.available").withStyle(ChatFormatting.GREEN);
                case LOCKED -> Component.translatable("gui.thaumicunity.book.locked").withStyle(ChatFormatting.DARK_GRAY);
            });
            if (state == NodeState.LOCKED) {
                for (Identifier parent : value.parents()) {
                    if (!ResearchManager.isComplete(player(), parent)) {
                        lines.add(Component.literal(" - ").append(Component.translatable(ResearchEntry.titleKey(parent)))
                                .withStyle(ChatFormatting.GRAY));
                    }
                }
            }
            g.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
            return;
        }
    }

    private void drawEntry(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Optional<ResearchEntry> found = ResearchManager.entry(access(), entry);
        if (found.isEmpty() || minecraft.level == null) {
            return;
        }
        ResearchEntry value = found.get();
        int stage = ResearchManager.knowledge(player()).stage(entry);
        int accent = currentCategory().map(c -> GuiUtil.opaque(c.color())).orElse(GuiUtil.BRASS);

        // Left page: title and the text of every reached stage.
        int leftX = bookX + 12;
        int pageW = bookW / 2 - 22;
        int top = bookY + 12;
        g.item(icon(value.icon()), leftX, top - 2);
        g.text(font, Component.translatable(ResearchEntry.titleKey(entry)).withStyle(ChatFormatting.BOLD), leftX + 20, top + 2,
                GuiUtil.darken(accent, 0.7f), false);
        List<FormattedCharSequence> lines = new ArrayList<>();
        int shown = Math.min(stage, value.stageCount() - 1);
        for (int s = 0; s <= shown; s++) {
            if (s > 0) {
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(font.split(Component.translatable(value.stages().get(s).text()), pageW));
        }
        int textTop = top + 20;
        int textBottom = bookY + bookH - 32;
        int visibleLines = (textBottom - textTop) / font.lineHeight;
        textScroll = Math.max(0, Math.min(textScroll, Math.max(0, lines.size() - visibleLines)));
        for (int i = 0; i < visibleLines && textScroll + i < lines.size(); i++) {
            g.text(font, lines.get(textScroll + i), leftX, textTop + i * font.lineHeight, GuiUtil.INK, false);
        }
        if (lines.size() > visibleLines) {
            int trackX = leftX + pageW + 3;
            g.fill(trackX, textTop, trackX + 2, textBottom, GuiUtil.PARCHMENT_SHADE);
            int knob = textTop + (textBottom - textTop - 12) * textScroll / Math.max(1, lines.size() - visibleLines);
            g.fill(trackX, knob, trackX + 2, knob + 12, GuiUtil.BRASS);
        }

        // Right page: requirements of the current stage, then recipes.
        int rightX = bookX + bookW / 2 + 8;
        int y = top;
        ItemStack hoveredStack = ItemStack.EMPTY;
        Identifier hoveredAspect = null;
        if (stage < value.stageCount()) {
            g.text(font, Component.translatable("gui.thaumicunity.book.requirements"), rightX, y, GuiUtil.INK, false);
            y += 12;
            List<ResearchRequirement> requirements = ResearchManager.currentRequirements(player(), entry, value);
            for (int i = 0; i < requirements.size(); i++) {
                ResearchRequirement requirement = requirements.get(i);
                boolean met = ResearchManager.isMet(player(), entry, stage, i, requirement);
                g.text(font, met ? "✔" : "✘", rightX, y, met ? GuiUtil.GOOD : GuiUtil.BAD, false);
                for (FormattedCharSequence line : font.split(describe(requirement), bookW / 2 - 34)) {
                    g.text(font, line, rightX + 10, y, GuiUtil.INK, false);
                    y += font.lineHeight;
                }
                y += 2;
            }
            if (requirements.isEmpty()) {
                g.text(font, Component.translatable("gui.thaumicunity.book.no_requirements"), rightX, y, GuiUtil.INK_FADED, false);
            }
        } else {
            g.text(font, Component.translatable("gui.thaumicunity.book.complete"), rightX, y, GuiUtil.darken(accent, 0.7f), false);
        }

        List<Identifier> recipes = reachedRecipes(value, stage);
        if (!recipes.isEmpty()) {
            recipeIndex = Math.floorMod(recipeIndex, recipes.size());
            // Below the requirements while the entry is in progress; right under "Complete" once it is done.
            int recipeY = stage < value.stageCount() ? bookY + bookH - 30 - RecipePageRenderer.HEIGHT
                    : Math.min(top + 20, bookY + bookH - 30 - RecipePageRenderer.HEIGHT);
            RecipeHolder<?> holder = ClientRecipes.get(recipes.get(recipeIndex));
            RecipePageRenderer renderer = new RecipePageRenderer(font, access(), SlotDisplayContext.fromLevel(minecraft.level),
                    minecraft.level.getGameTime(), mouseX, mouseY);
            if (holder == null || !renderer.draw(g, holder.value(), rightX, recipeY)) {
                g.text(font, Component.translatable("gui.thaumicunity.book.recipe_missing"), rightX, recipeY, GuiUtil.INK_FADED, false);
            }
            hoveredStack = renderer.hoveredStack();
            hoveredAspect = renderer.hoveredAspect();
            if (recipes.size() > 1) {
                String counter = (recipeIndex + 1) + " / " + recipes.size();
                g.centeredText(font, counter, rightX + (bookW / 2 - 24) / 2, bookY + bookH - 21, GuiUtil.INK_FADED);
            }
        }
        if (!hoveredStack.isEmpty()) {
            g.setTooltipForNextFrame(font, hoveredStack, mouseX, mouseY);
        } else if (hoveredAspect != null) {
            g.setComponentTooltipForNextFrame(font, GuiUtil.aspectTooltip(access(), hoveredAspect), mouseX, mouseY);
        }
    }

    /** Recipes of every reached stage (all stages once the entry is complete). */
    private static List<Identifier> reachedRecipes(ResearchEntry value, int stage) {
        List<Identifier> result = new ArrayList<>();
        int shown = Math.min(stage, value.stageCount() - 1);
        for (int s = 0; s <= shown; s++) {
            result.addAll(value.stages().get(s).recipes());
        }
        return result;
    }

    private int requirementsBottom(ResearchEntry value, int stage) {
        int y = bookY + 12 + 12;
        for (ResearchRequirement requirement : ResearchManager.currentRequirements(player(), entry, value)) {
            y += font.split(describe(requirement), bookW / 2 - 34).size() * font.lineHeight + 2;
        }
        return Math.max(y, bookY + 36);
    }

    private boolean allMet(Identifier id, ResearchEntry value, int stage) {
        List<ResearchRequirement> requirements = ResearchManager.currentRequirements(player(), id, value);
        for (int i = 0; i < requirements.size(); i++) {
            if (!ResearchManager.isMet(player(), id, stage, i, requirements.get(i))) {
                return false;
            }
        }
        return true;
    }

    private Component describe(ResearchRequirement requirement) {
        PlayerKnowledge knowledge = ResearchManager.knowledge(player());
        return switch (requirement) {
            case ResearchRequirement.Scan scan -> Component.translatable("requirement.thaumicunity.scan", scanName(scan.target()));
            case ResearchRequirement.Craft craft -> Component.translatable("requirement.thaumicunity.craft", itemName(craft.item()));
            case ResearchRequirement.Obtain obtain -> Component.translatable(
                    obtain.consume() ? "requirement.thaumicunity.obtain.consume" : "requirement.thaumicunity.obtain",
                    obtain.count(), itemName(obtain.item()));
            case ResearchRequirement.Knowledge kn -> Component.translatable("requirement.thaumicunity.knowledge." + kn.kind().key(),
                    kn.amount(), Component.translatable(ResearchCategory.translationKey(kn.category())),
                    knowledge.knowledge(kn.kind(), kn.category()));
            case ResearchRequirement.Insight insight -> {
                MutableComponent aspects = Component.empty();
                for (int i = 0; i < insight.aspects().size(); i++) {
                    Identifier aspect = insight.aspects().get(i);
                    if (i > 0) {
                        aspects.append(", ");
                    }
                    aspects.append(Aspect.displayName(aspect, Aspects.color(access(), aspect)));
                }
                yield Component.translatable("requirement.thaumicunity.insight", aspects);
            }
            case ResearchRequirement.Research research -> Component.translatable("requirement.thaumicunity.research",
                    Component.translatable(ResearchEntry.titleKey(research.id())));
            case ResearchRequirement.Warp warp -> Component.translatable("requirement.thaumicunity.warp", warp.minimum(),
                    knowledge.totalWarp());
            case ResearchRequirement.Dimension dimension -> Component.translatable("requirement.thaumicunity.dimension",
                    Component.translatable(dimension.dimension().toLanguageKey("dimension")));
            case ResearchRequirement.Kill kill -> Component.translatable("requirement.thaumicunity.kill", kill.count(),
                    BuiltInRegistries.ENTITY_TYPE.getOptional(kill.entity()).map(t -> t.getDescription())
                            .orElse(Component.literal(kill.entity().toString())),
                    knowledge.kills(kill.entity()));
        };
    }

    private static Component itemName(Identifier id) {
        return BuiltInRegistries.ITEM.getOptional(id).map(item -> new ItemStack(item).getHoverName())
                .orElse(Component.literal(id.toString()));
    }

    private Component scanName(ScanTarget target) {
        return switch (target.kind()) {
            case ITEM -> itemName(target.id());
            case BLOCK -> BuiltInRegistries.BLOCK.getOptional(target.id()).map(b -> (Component) b.getName())
                    .orElse(Component.literal(target.id().toString()));
            case ENTITY -> BuiltInRegistries.ENTITY_TYPE.getOptional(target.id()).map(t -> t.getDescription())
                    .orElse(Component.literal(target.id().toString()));
            case ASPECT -> Aspect.displayName(target.id(), Aspects.color(access(), target.id()));
            case ITEM_TAG -> Component.literal("#" + target.id());
        };
    }

    // --- input

    private void openEntry(Identifier id) {
        entry = id;
        lastEntry = id;
        textScroll = 0;
        recipeIndex = 0;
        GuiUtil.playUi(TUSounds.PAGE, 1.0f);
        rebuildWidgets();
    }

    private void closeEntry() {
        entry = null;
        lastEntry = null;
        GuiUtil.playUi(TUSounds.PAGE, 0.9f);
        rebuildWidgets();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        for (int i = 0; i < categories.size(); i++) {
            if (GuiUtil.inside(mx, my, tabX(i), tabY(i), TAB_W, TAB_H)) {
                category = categories.get(i).getKey();
                lastCategory = category;
                if (entry != null) {
                    entry = null;
                    lastEntry = null;
                    rebuildWidgets();
                }
                GuiUtil.playUi(TUSounds.PAGE, 1.1f);
                return true;
            }
        }
        if (entry != null) {
            if (event.button() == 1) {
                closeEntry();
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (category != null && event.button() == 0 && GuiUtil.inside(mx, my, mapX(), mapY(), mapW(), mapH())) {
            for (Map.Entry<Identifier, ResearchEntry> e : entriesOf(category).entrySet()) {
                ResearchEntry value = e.getValue();
                NodeState state = state(e.getKey(), value);
                if (state != NodeState.LOCKED && GuiUtil.inside(mx, my, nodeX(value), nodeY(value), NODE, NODE)) {
                    openEntry(e.getKey());
                    return true;
                }
            }
            dragging = true;
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dragging && entry == null && category != null) {
            double[] pan = pan();
            pan[0] -= dragX;
            pan[1] -= dragY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (entry != null) {
            textScroll = Math.max(0, textScroll - (int) Math.signum(scrollY) * 3);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == 256 && entry != null) {
            closeEntry();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void tick() {
        super.tick();
        // Rebuild the entry page when the stage changes (after the server confirms an advance).
        if (entry != null && shownStage != ResearchManager.knowledge(player()).stage(entry)) {
            rebuildWidgets();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
