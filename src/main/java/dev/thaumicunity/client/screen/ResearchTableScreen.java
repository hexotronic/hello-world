package dev.thaumicunity.client.screen;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.menu.ResearchTableMenu;
import dev.thaumicunity.common.network.ResearchTablePayload;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.registry.TUTags;
import dev.thaumicunity.common.research.InsightNote;
import dev.thaumicunity.common.research.PlayerKnowledge;
import dev.thaumicunity.common.research.ResearchCategory;
import dev.thaumicunity.common.research.ResearchEntry;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.research.ResearchRequirement;
import dev.thaumicunity.core.aspect.AspectGraph;
import dev.thaumicunity.core.research.HexCoord;
import dev.thaumicunity.core.research.InsightPuzzle;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Research table. With research notes in the paper slot it shows the insight puzzle on a hex board and a palette
 * of the player's aspect points: pick an aspect, click an empty cell to place it, right-click to take it back.
 * With blank paper it lists the insight puzzles the player can start and the categories they can theorize in.
 */
public class ResearchTableScreen extends AbstractContainerScreen<ResearchTableMenu> {
    private static final int BOARD_X = 40;
    private static final int BOARD_Y = 16;
    private static final int BOARD_W = 134;
    private static final int BOARD_H = 142;
    private static final int PALETTE_X = 180;
    private static final int PALETTE_Y = 28;
    private static final int PALETTE_COLS = 4;
    private static final int PALETTE_ROWS = 7;
    private static final int ROW_H = 12;

    private @Nullable Identifier selectedAspect;
    private int paletteScroll;
    private int listScroll;

    public ResearchTableScreen(ResearchTableMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 256, 256);
        this.inventoryLabelX = 48;
        this.inventoryLabelY = 163;
    }

    // --- state

    private RegistryAccess access() {
        return minecraft.level != null ? minecraft.level.registryAccess() : RegistryAccess.EMPTY;
    }

    private Player player() {
        return minecraft.player;
    }

    private @Nullable InsightNote note() {
        return menu.paper().get(TUDataComponents.INSIGHT.get());
    }

    private int cellSize(int radius) {
        // Pointy-top hexes: the board is (2r + 1) cells wide at the middle row.
        return Math.max(5, Math.min(10, (int) (BOARD_W / ((2 * radius + 1) * Math.sqrt(3)))));
    }

    private int[] cellCenter(HexCoord c, int size) {
        double px = size * Math.sqrt(3) * (c.q() + c.r() / 2.0);
        double py = size * 1.5 * c.r();
        return new int[]{leftPos + BOARD_X + BOARD_W / 2 + (int) Math.round(px), topPos + BOARD_Y + BOARD_H / 2 + (int) Math.round(py)};
    }

    private @Nullable HexCoord cellAt(double mx, double my, InsightPuzzle puzzle) {
        int size = cellSize(puzzle.radius());
        HexCoord best = null;
        double bestDistance = size * size;
        for (HexCoord c : HexCoord.board(puzzle.radius())) {
            int[] center = cellCenter(c, size);
            double d = (mx - center[0]) * (mx - center[0]) + (my - center[1]) * (my - center[1]);
            if (d < bestDistance) {
                bestDistance = d;
                best = c;
            }
        }
        return best;
    }

    /** Aspects shown in the palette: everything the player holds points of, cheapest tiers first. */
    private List<Identifier> palette() {
        PlayerKnowledge knowledge = ResearchManager.knowledge(player());
        RegistryAccess access = access();
        List<Identifier> result = new ArrayList<>();
        knowledge.aspectPoints().forEach((aspect, points) -> {
            if (points > 0 && Aspects.get(access, aspect).isPresent()) {
                result.add(aspect);
            }
        });
        result.sort(Comparator.<Identifier>comparingInt(a -> Aspects.tier(access, a)).thenComparing(Identifier::toString));
        return result;
    }

    /** A row of the start/theorize list. */
    private record Action(Component label, ResearchTablePayload payload) {
    }

    private List<Action> actions() {
        List<Action> result = new ArrayList<>();
        Player player = player();
        PlayerKnowledge knowledge = ResearchManager.knowledge(player);
        RegistryAccess access = access();
        ResearchManager.entries(access).ifPresent(registry -> registry.entrySet().stream()
                .sorted(Comparator.comparing(e -> e.getKey().identifier().toString()))
                .forEach(e -> {
                    Identifier id = e.getKey().identifier();
                    ResearchEntry entry = e.getValue();
                    int stage = knowledge.stage(id);
                    if (stage >= entry.stageCount() || !ResearchManager.isAvailable(player, entry)) {
                        return;
                    }
                    List<ResearchRequirement> requirements = ResearchManager.currentRequirements(player, id, entry);
                    for (int i = 0; i < requirements.size(); i++) {
                        if (requirements.get(i) instanceof ResearchRequirement.Insight
                                && !knowledge.hasSolvedInsight(PlayerKnowledge.insightKey(id, stage, i))) {
                            result.add(new Action(Component.translatable("gui.thaumicunity.research_table.start",
                                    Component.translatable(ResearchEntry.titleKey(id))),
                                    new ResearchTablePayload(ResearchTablePayload.START, id, stage, i, 0, 0)));
                        }
                    }
                }));
        access.lookup(TURegistries.RESEARCH_CATEGORY).ifPresent(categories -> categories.entrySet().stream()
                .sorted(Comparator.comparingInt(e -> e.getValue().order()))
                .forEach(e -> {
                    Identifier id = e.getKey().identifier();
                    int observations = knowledge.knowledge(ResearchRequirement.KnowledgeKind.OBSERVATION, id);
                    if (observations >= 2) {
                        result.add(new Action(Component.translatable("gui.thaumicunity.research_table.theorize",
                                Component.translatable(ResearchCategory.translationKey(id)), observations),
                                new ResearchTablePayload(ResearchTablePayload.THEORIZE, id, 0, 0, 0, 0)));
                    }
                }));
        return result;
    }

    private Component actionsHeader() {
        return menu.paper().is(TUTags.Items.RESEARCH_PAPER) ? Component.translatable("gui.thaumicunity.research_table.choose")
                : Component.translatable("gui.thaumicunity.research_table.need_paper");
    }

    /** Top of the action list, below the (wrapped) header. */
    private int rowsTop() {
        return topPos + BOARD_Y + 4 + font.split(actionsHeader(), BOARD_W - 8).size() * font.lineHeight + 4;
    }

    private int listRows() {
        return Math.max(1, (topPos + BOARD_Y + BOARD_H - 2 - rowsTop()) / ROW_H);
    }

    // --- drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos;
        int y = topPos;
        GuiUtil.panel(g, x, y, imageWidth, imageHeight);
        GuiUtil.slots(g, menu, x, y);
        g.fill(x + BOARD_X, y + BOARD_Y, x + BOARD_X + BOARD_W, y + BOARD_Y + BOARD_H, GuiUtil.PARCHMENT_SHADE);
        g.fill(x + BOARD_X + 1, y + BOARD_Y + 1, x + BOARD_X + BOARD_W - 1, y + BOARD_Y + BOARD_H - 1, 0xFFF3EAD3);

        InsightNote note = note();
        if (note != null) {
            drawBoard(g, note, mouseX, mouseY);
            drawPalette(g, mouseX, mouseY);
        } else {
            drawActions(g, mouseX, mouseY);
        }
        drawKnowledge(g);
    }

    private void drawBoard(GuiGraphicsExtractor g, InsightNote note, int mouseX, int mouseY) {
        RegistryAccess access = access();
        AspectGraph graph = Aspects.graph(access);
        InsightPuzzle puzzle = note.toPuzzle();
        int size = cellSize(puzzle.radius());
        Set<HexCoord> connected = puzzle.anchors().isEmpty() ? Set.of()
                : puzzle.connectedFrom(puzzle.anchors().keySet().iterator().next(), graph);
        HexCoord hovered = GuiUtil.inside(mouseX, mouseY, leftPos + BOARD_X, topPos + BOARD_Y, BOARD_W, BOARD_H)
                ? cellAt(mouseX, mouseY, puzzle) : null;
        for (HexCoord c : HexCoord.board(puzzle.radius())) {
            int[] center = cellCenter(c, size);
            int fill;
            if (puzzle.blocked().contains(c)) {
                fill = 0xFF5A4A3A;
            } else if (puzzle.anchors().containsKey(c)) {
                fill = connected.contains(c) && note.solved() ? 0xFF9FD08A : GuiUtil.BRASS_LIGHT;
            } else if (puzzle.placed().containsKey(c)) {
                fill = connected.contains(c) ? 0xFFC8E6B5 : 0xFFE6D3AE;
            } else {
                fill = 0xFFDCCBA6;
            }
            if (c.equals(hovered) && !puzzle.blocked().contains(c)) {
                fill = GuiUtil.lighten(fill);
            }
            hex(g, center[0], center[1], size, fill);
            Optional<String> aspect = puzzle.aspectAt(c);
            if (aspect.isPresent()) {
                Identifier id = Identifier.tryParse(aspect.get());
                if (id != null) {
                    float scale = Math.min(1f, size * 1.5f / 16f);
                    g.pose().pushMatrix();
                    g.pose().translate(center[0], center[1]);
                    g.pose().scale(scale, scale);
                    GuiUtil.aspectIcon(g, font, access, id, -8, -8);
                    g.pose().popMatrix();
                }
            }
        }
        Component status = note.solved()
                ? Component.translatable("gui.thaumicunity.research_table.solved").withStyle(ChatFormatting.DARK_GREEN)
                : Component.translatable(ResearchEntry.titleKey(note.research()));
        g.text(font, font.plainSubstrByWidth(status.getString(), BOARD_W - 8), leftPos + BOARD_X + 4, topPos + BOARD_Y + 4,
                note.solved() ? GuiUtil.GOOD : GuiUtil.INK, false);
    }

    /** Hexagon-like cell built from three rectangles. */
    private static void hex(GuiGraphicsExtractor g, int cx, int cy, int size, int color) {
        int halfW = (int) Math.round(size * Math.sqrt(3) / 2.0);
        int halfH = size / 2;
        g.fill(cx - halfW, cy - halfH, cx + halfW, cy + halfH + 1, GuiUtil.darken(color, 0.7f));
        g.fill(cx - halfW / 2, cy - size, cx + halfW / 2 + 1, cy + size + 1, GuiUtil.darken(color, 0.7f));
        g.fill(cx - halfW + 1, cy - halfH + 1, cx + halfW - 1, cy + halfH, color);
        g.fill(cx - halfW / 2 + 1, cy - size + 1, cx + halfW / 2, cy + size, color);
    }

    private void drawPalette(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftPos + PALETTE_X;
        int y = topPos + PALETTE_Y;
        g.text(font, Component.translatable("gui.thaumicunity.research_table.aspects"), x, topPos + 16, GuiUtil.INK, false);
        List<Identifier> palette = palette();
        PlayerKnowledge knowledge = ResearchManager.knowledge(player());
        RegistryAccess access = access();
        int first = paletteScroll * PALETTE_COLS;
        for (int i = 0; i < PALETTE_COLS * PALETTE_ROWS && first + i < palette.size(); i++) {
            Identifier aspect = palette.get(first + i);
            int ax = x + (i % PALETTE_COLS) * 18;
            int ay = y + (i / PALETTE_COLS) * 18;
            if (aspect.equals(selectedAspect)) {
                g.fill(ax - 1, ay - 1, ax + 17, ay + 17, GuiUtil.BRASS);
            }
            GuiUtil.aspectStack(g, font, access, aspect, knowledge.aspectPoints(aspect), ax, ay);
        }
        int pages = Math.max(0, (palette.size() - 1) / PALETTE_COLS - PALETTE_ROWS + 1);
        if (pages > 0) {
            int trackY = y;
            int trackH = PALETTE_ROWS * 18;
            g.fill(x + PALETTE_COLS * 18 + 1, trackY, x + PALETTE_COLS * 18 + 3, trackY + trackH, GuiUtil.PARCHMENT_SHADE);
            int knob = trackY + (trackH - 10) * paletteScroll / pages;
            g.fill(x + PALETTE_COLS * 18 + 1, knob, x + PALETTE_COLS * 18 + 3, knob + 10, GuiUtil.BRASS);
        }
        if (palette.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.thaumicunity.research_table.no_aspects"), x, y, 72, GuiUtil.INK_FADED);
        }
    }

    private void drawActions(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int x = leftPos + BOARD_X + 4;
        int y = topPos + BOARD_Y + 4;
        boolean hasPaper = menu.paper().is(TUTags.Items.RESEARCH_PAPER);
        g.textWithWordWrap(font, actionsHeader(), x, y, BOARD_W - 8, hasPaper ? GuiUtil.INK : GuiUtil.INK_FADED);
        List<Action> actions = actions();
        int rowsY = rowsTop();
        int rows = listRows();
        listScroll = Math.max(0, Math.min(listScroll, Math.max(0, actions.size() - rows)));
        for (int i = 0; i < rows && listScroll + i < actions.size(); i++) {
            Action action = actions.get(listScroll + i);
            int ry = rowsY + i * ROW_H;
            boolean hover = hasPaper && GuiUtil.inside(mouseX, mouseY, x - 2, ry - 1, BOARD_W - 4, ROW_H);
            if (hover) {
                g.fill(x - 2, ry - 1, x + BOARD_W - 6, ry + ROW_H - 1, 0x40B08D3C);
            }
            String text = font.plainSubstrByWidth(action.label().getString(), BOARD_W - 10);
            g.text(font, text, x, ry + 1, hasPaper ? GuiUtil.INK : GuiUtil.INK_FADED, false);
        }
        if (actions.isEmpty()) {
            g.textWithWordWrap(font, Component.translatable("gui.thaumicunity.research_table.nothing"), x, rowsY, BOARD_W - 8,
                    GuiUtil.INK_FADED);
        }
    }

    /** Observation and theory points per category, on the right while no notes are on the table. */
    private void drawKnowledge(GuiGraphicsExtractor g) {
        if (note() != null) {
            return;
        }
        int x = leftPos + PALETTE_X;
        int y = topPos + 16;
        g.text(font, Component.translatable("gui.thaumicunity.research_table.knowledge"), x, y, GuiUtil.INK, false);
        PlayerKnowledge knowledge = ResearchManager.knowledge(player());
        Optional<Registry<ResearchCategory>> categories = access().lookup(TURegistries.RESEARCH_CATEGORY);
        if (categories.isEmpty()) {
            return;
        }
        int row = 0;
        for (Map.Entry<net.minecraft.resources.ResourceKey<ResearchCategory>, ResearchCategory> e : categories.get().entrySet().stream()
                .sorted(Comparator.comparingInt(en -> en.getValue().order())).toList()) {
            Identifier id = e.getKey().identifier();
            int obs = knowledge.knowledge(ResearchRequirement.KnowledgeKind.OBSERVATION, id);
            int theory = knowledge.knowledge(ResearchRequirement.KnowledgeKind.THEORY, id);
            if (obs == 0 && theory == 0) {
                continue;
            }
            int ry = y + 12 + row * 20;
            if (ry > topPos + BOARD_Y + BOARD_H - 18) {
                break;
            }
            String name = font.plainSubstrByWidth(Component.translatable(ResearchCategory.translationKey(id)).getString(), 70);
            g.text(font, name, x, ry, GuiUtil.opaque(e.getValue().color()), false);
            g.text(font, Component.translatable("gui.thaumicunity.research_table.points", obs, theory), x, ry + 9, GuiUtil.INK_FADED, false);
            row++;
        }
        if (row == 0) {
            g.textWithWordWrap(font, Component.translatable("gui.thaumicunity.research_table.no_knowledge"), x, y + 12,
                    imageWidth - PALETTE_X - 8, GuiUtil.INK_FADED);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        InsightNote note = note();
        if (note == null) {
            return;
        }
        Identifier hoveredAspect = null;
        List<Identifier> palette = palette();
        int index = paletteIndexAt(mouseX, mouseY);
        if (index >= 0 && index < palette.size()) {
            hoveredAspect = palette.get(index);
        } else if (GuiUtil.inside(mouseX, mouseY, leftPos + BOARD_X, topPos + BOARD_Y, BOARD_W, BOARD_H)) {
            InsightPuzzle puzzle = note.toPuzzle();
            HexCoord cell = cellAt(mouseX, mouseY, puzzle);
            if (cell != null) {
                hoveredAspect = puzzle.aspectAt(cell).map(Identifier::tryParse).orElse(null);
            }
        }
        if (hoveredAspect != null) {
            g.setComponentTooltipForNextFrame(font, GuiUtil.aspectTooltip(access(), hoveredAspect), mouseX, mouseY);
        }
    }

    // --- input

    private int paletteIndexAt(double mx, double my) {
        int x = leftPos + PALETTE_X;
        int y = topPos + PALETTE_Y;
        if (!GuiUtil.inside(mx, my, x, y, PALETTE_COLS * 18, PALETTE_ROWS * 18)) {
            return -1;
        }
        int col = (int) (mx - x) / 18;
        int row = (int) (my - y) / 18;
        return (paletteScroll + row) * PALETTE_COLS + col;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();
        InsightNote note = note();
        if (note != null) {
            List<Identifier> palette = palette();
            int index = paletteIndexAt(mx, my);
            if (index >= 0 && index < palette.size()) {
                selectedAspect = palette.get(index).equals(selectedAspect) ? null : palette.get(index);
                GuiUtil.playUi(TUSounds.PAGE, 1.6f);
                return true;
            }
            if (!note.solved() && GuiUtil.inside(mx, my, leftPos + BOARD_X, topPos + BOARD_Y, BOARD_W, BOARD_H)) {
                InsightPuzzle puzzle = note.toPuzzle();
                HexCoord cell = cellAt(mx, my, puzzle);
                if (cell != null) {
                    if (event.button() == 1 && puzzle.placed().containsKey(cell)) {
                        send(new ResearchTablePayload(ResearchTablePayload.CLEAR, ThaumicUnity.id("none"), 0, 0, cell.q(), cell.r()));
                        return true;
                    }
                    if (event.button() == 0 && selectedAspect != null && puzzle.isFree(cell)) {
                        send(new ResearchTablePayload(ResearchTablePayload.PLACE, selectedAspect, 0, 0, cell.q(), cell.r()));
                        return true;
                    }
                }
            }
        } else if (event.button() == 0 && menu.paper().is(TUTags.Items.RESEARCH_PAPER)) {
            int x = leftPos + BOARD_X + 4;
            int rowsY = rowsTop();
            if (GuiUtil.inside(mx, my, x - 2, rowsY - 1, BOARD_W - 4, listRows() * ROW_H)) {
                int row = (int) (my - rowsY + 1) / ROW_H;
                List<Action> actions = actions();
                if (listScroll + row < actions.size()) {
                    send(actions.get(listScroll + row).payload());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        int step = scrollY > 0 ? -1 : scrollY < 0 ? 1 : 0;
        if (note() != null && GuiUtil.inside(mx, my, leftPos + PALETTE_X, topPos + PALETTE_Y, PALETTE_COLS * 18 + 4, PALETTE_ROWS * 18)) {
            int maxScroll = Math.max(0, (palette().size() - 1) / PALETTE_COLS - PALETTE_ROWS + 1);
            paletteScroll = Math.max(0, Math.min(maxScroll, paletteScroll + step));
            return true;
        }
        if (note() == null && GuiUtil.inside(mx, my, leftPos + BOARD_X, topPos + BOARD_Y, BOARD_W, BOARD_H)) {
            listScroll = Math.max(0, listScroll + step);
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    private static void send(ResearchTablePayload payload) {
        ClientPacketDistributor.sendToServer(payload);
    }

    /** Clears the palette selection when the notes are taken off the table. */
    @Override
    protected void containerTick() {
        super.containerTick();
        ItemStack paper = menu.paper();
        if (paper.isEmpty() || paper.get(TUDataComponents.INSIGHT.get()) == null) {
            selectedAspect = null;
            paletteScroll = 0;
        }
    }
}
