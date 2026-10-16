package dev.thaumicunity.common.research;

import dev.thaumicunity.common.util.TUUtil;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.menu.ResearchTableMenu;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.registry.TUTags;
import dev.thaumicunity.core.aspect.AspectGraph;
import dev.thaumicunity.core.research.HexCoord;
import dev.thaumicunity.core.research.InsightPuzzle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Server side of the research table (all methods validate that the player has the table open):
 * <ul>
 *   <li>{@link #startNotes} writes a fresh insight puzzle for one insight requirement onto paper (uses ink);</li>
 *   <li>{@link #place}/{@link #clear} edit the puzzle (placing costs one aspect point);</li>
 *   <li>{@link #theorize} turns two observations of a category into one theory (uses paper and ink).</li>
 * </ul>
 */
public final class ResearchTableActions {
    private ResearchTableActions() {
    }

    private static Optional<ResearchTableMenu> menu(ServerPlayer player) {
        return player.containerMenu instanceof ResearchTableMenu menu && menu.stillValid(player) ? Optional.of(menu) : Optional.empty();
    }

    private static boolean useInk(ResearchTableMenu menu, ServerPlayer player) {
        ItemStack tools = menu.tools();
        if (!tools.is(TUItems.SCRIBING_TOOLS.get())) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.table.no_tools").withStyle(ChatFormatting.RED));
            return false;
        }
        if (!player.isCreative()) {
            tools.hurtAndBreak(1, player.level(), player, item -> { });
        }
        return true;
    }

    public static void startNotes(ServerPlayer player, Identifier research, int stage, int requirement) {
        Optional<ResearchTableMenu> open = menu(player);
        if (open.isEmpty()) {
            return;
        }
        ResearchTableMenu menu = open.get();
        if (!menu.paper().is(TUTags.Items.RESEARCH_PAPER)) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.table.no_paper").withStyle(ChatFormatting.RED));
            return;
        }
        Optional<ResearchEntry> entry = ResearchManager.entry(player.level().registryAccess(), research);
        PlayerKnowledge knowledge = ResearchManager.knowledge(player);
        if (entry.isEmpty() || knowledge.stage(research) != stage || !ResearchManager.isAvailable(player, entry.get())) {
            return;
        }
        List<ResearchRequirement> requirements = ResearchManager.currentRequirements(player, research, entry.get());
        if (requirement < 0 || requirement >= requirements.size()
                || !(requirements.get(requirement) instanceof ResearchRequirement.Insight insight)) {
            return;
        }
        if (!useInk(menu, player)) {
            return;
        }
        long seed = player.getUUID().getMostSignificantBits() ^ research.hashCode() * 31L ^ stage * 977L ^ requirement;
        List<String> anchors = insight.aspects().stream().map(Identifier::toString).toList();
        InsightPuzzle puzzle = InsightPuzzle.generate(anchors, TUConfig.INSIGHT_BOARD_RADIUS.get(), 2, seed);
        ItemStack notes = new ItemStack(TUItems.RESEARCH_NOTES.get());
        notes.set(TUDataComponents.INSIGHT.get(), InsightNote.of(research, stage, requirement, puzzle, false));
        ItemStack paper = menu.paper();
        paper.shrink(1);
        if (paper.isEmpty()) {
            menu.setPaper(notes);
        } else {
            TUUtil.giveOrDrop(player, notes);
        }
        player.level().playSound(null, player.blockPosition(), TUSounds.PAGE.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
    }

    public static void place(ServerPlayer player, int q, int r, Identifier aspect) {
        editNotes(player, (note, puzzle, graph) -> {
            HexCoord cell = new HexCoord(q, r);
            if (!puzzle.isFree(cell) || !graph.contains(aspect.toString())) {
                return null;
            }
            PlayerKnowledge knowledge = ResearchManager.knowledge(player);
            if (!player.isCreative() && !knowledge.spendAspectPoint(aspect)) {
                player.sendOverlayMessage(Component.translatable("message.thaumicunity.table.no_points").withStyle(ChatFormatting.RED));
                return null;
            }
            return puzzle.place(cell, aspect.toString());
        });
    }

    public static void clear(ServerPlayer player, int q, int r) {
        editNotes(player, (note, puzzle, graph) -> {
            HexCoord cell = new HexCoord(q, r);
            if (!puzzle.placed().containsKey(cell)) {
                return null;
            }
            return puzzle.clear(cell);
        });
    }

    @FunctionalInterface
    private interface Edit {
        InsightPuzzle apply(InsightNote note, InsightPuzzle puzzle, AspectGraph graph);
    }

    private static void editNotes(ServerPlayer player, Edit edit) {
        Optional<ResearchTableMenu> open = menu(player);
        if (open.isEmpty()) {
            return;
        }
        ItemStack notes = open.get().paper();
        InsightNote note = notes.get(TUDataComponents.INSIGHT.get());
        if (note == null || note.solved()) {
            return;
        }
        AspectGraph graph = Aspects.graph(player.level().registryAccess());
        InsightPuzzle updated = edit.apply(note, note.toPuzzle(), graph);
        if (updated == null) {
            player.syncData(TUAttachments.KNOWLEDGE);
            return;
        }
        boolean solved = updated.isSolved(graph);
        notes.set(TUDataComponents.INSIGHT.get(), InsightNote.of(note.research(), note.stage(), note.requirement(), updated, solved));
        if (solved) {
            PlayerKnowledge knowledge = ResearchManager.knowledge(player);
            knowledge.markInsightSolved(note.requirementKey());
            // Unified mode: solving a puzzle also yields a theory in the entry's category.
            if (TUConfig.RESEARCH_MODE.get() == TUConfig.ResearchModeOption.UNIFIED) {
                ResearchManager.entry(player.level().registryAccess(), note.research()).ifPresent(entry ->
                        knowledge.addKnowledge(ResearchRequirement.KnowledgeKind.THEORY, entry.category(), 1));
            }
            player.level().playSound(null, player.blockPosition(), TUSounds.RESEARCH.get(), SoundSource.PLAYERS, 0.8f, 1.2f);
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.table.solved").withStyle(ChatFormatting.GOLD));
        }
        open.get().broadcastChanges();
        player.syncData(TUAttachments.KNOWLEDGE);
    }

    /** Theorycraft (simplified): two observations + paper + ink → one theory in the same category. */
    public static void theorize(ServerPlayer player, Identifier category) {
        Optional<ResearchTableMenu> open = menu(player);
        if (open.isEmpty()) {
            return;
        }
        ResearchTableMenu menu = open.get();
        PlayerKnowledge knowledge = ResearchManager.knowledge(player);
        if (knowledge.knowledge(ResearchRequirement.KnowledgeKind.OBSERVATION, category) < 2) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.table.no_observations").withStyle(ChatFormatting.RED));
            return;
        }
        if (!menu.paper().is(TUTags.Items.RESEARCH_PAPER) || !useInk(menu, player)) {
            return;
        }
        menu.paper().shrink(1);
        knowledge.addKnowledge(ResearchRequirement.KnowledgeKind.OBSERVATION, category, -2);
        knowledge.addKnowledge(ResearchRequirement.KnowledgeKind.THEORY, category, 1);
        menu.broadcastChanges();
        player.syncData(TUAttachments.KNOWLEDGE);
        player.level().playSound(null, player.blockPosition(), TUSounds.PAGE.get(), SoundSource.PLAYERS, 0.7f, 1.2f);
    }
}
