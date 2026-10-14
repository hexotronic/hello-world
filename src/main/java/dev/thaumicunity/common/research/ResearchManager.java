package dev.thaumicunity.common.research;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.api.event.ResearchCompletedEvent;
import dev.thaumicunity.api.event.ScanEvent;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.core.research.RequirementSpec;
import dev.thaumicunity.core.research.ResearchMode;
import dev.thaumicunity.core.research.ResearchModeConverter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Server-side research logic: scanning, knowledge points, checking and completing research stages, and research
 * mode conversion (docs/DESIGN_DECISIONS.md, D3). Every change is synced to the player through the knowledge
 * attachment.
 */
public final class ResearchManager {
    private ResearchManager() {
    }

    public static PlayerKnowledge knowledge(Player player) {
        return player.getData(TUAttachments.KNOWLEDGE);
    }

    private static void sync(ServerPlayer player) {
        player.syncData(TUAttachments.KNOWLEDGE);
    }

    public static Optional<Registry<ResearchEntry>> entries(RegistryAccess access) {
        return access.lookup(TURegistries.RESEARCH);
    }

    public static Optional<ResearchEntry> entry(RegistryAccess access, Identifier id) {
        return entries(access).flatMap(r -> r.getOptional(id));
    }

    /** True when the entry is complete (all stages done). Unknown entries count as known. */
    public static boolean isComplete(Player player, Identifier id) {
        Optional<ResearchEntry> entry = entry(player.level().registryAccess(), id);
        return entry.isEmpty() || knowledge(player).stage(id) >= entry.get().stageCount();
    }

    /** Recipe gate: no research means always known. */
    public static boolean knows(Player player, Optional<Identifier> research) {
        return research.isEmpty() || player.isCreative() || isComplete(player, research.get());
    }

    // --- scanning

    public static boolean scanItem(ServerPlayer player, ItemStack stack) {
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return scan(player, ScanTarget.Kind.ITEM, id, ItemAspects.of(stack, false), stack.getHoverName());
    }

    public static boolean scanBlock(ServerPlayer player, BlockPos pos) {
        BlockState state = player.level().getBlockState(pos);
        Identifier id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        AspectList aspects = ItemAspects.of(state.getBlock().asItem(), false);
        boolean result = scan(player, ScanTarget.Kind.BLOCK, id, aspects, state.getBlock().getName());
        // A scanned block also counts as its item (one fewer step for item-based research).
        knowledge(player).markScanned(ScanTarget.scanKey(ScanTarget.Kind.ITEM, BuiltInRegistries.ITEM.getKey(state.getBlock().asItem())));
        return result;
    }

    public static boolean scanEntity(ServerPlayer player, Entity entity) {
        Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return scan(player, ScanTarget.Kind.ENTITY, id, ItemAspects.ofEntity(entity.getType()), entity.getName());
    }

    private static boolean scan(ServerPlayer player, ScanTarget.Kind kind, Identifier id, AspectList aspects, Component name) {
        PlayerKnowledge knowledge = knowledge(player);
        String key = ScanTarget.scanKey(kind, id);
        if (!knowledge.markScanned(key)) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.scan.known", name).withStyle(ChatFormatting.GRAY));
            return false;
        }
        int points = TUConfig.SCAN_POINTS_PER_ASPECT.get();
        aspects.forEach((aspect, amount) -> {
            knowledge.addAspectPoints(aspect, Math.max(1, Math.min(points, amount)));
            knowledge.markScanned(ScanTarget.scanKey(ScanTarget.Kind.ASPECT, aspect));
        });
        grantObservations(player, knowledge, aspects);
        NeoForge.EVENT_BUS.post(new ScanEvent(player, key, aspects.asMap()));
        player.sendOverlayMessage(Component.translatable("message.thaumicunity.scan.new", name).withStyle(ChatFormatting.LIGHT_PURPLE));
        sync(player);
        return true;
    }

    /** Scanning something whose aspects match a category's observation aspects grants one observation there. */
    private static void grantObservations(ServerPlayer player, PlayerKnowledge knowledge, AspectList aspects) {
        RegistryAccess access = player.level().registryAccess();
        access.lookup(TURegistries.RESEARCH_CATEGORY).ifPresent(categories -> categories.entrySet().forEach(e -> {
            Identifier category = e.getKey().identifier();
            boolean relevant = aspects.isEmpty() ? false : aspects.asMap().keySet().stream()
                    .anyMatch(a -> CategoryAspects.of(category).contains(a));
            if (relevant) {
                knowledge.addKnowledge(ResearchRequirement.KnowledgeKind.OBSERVATION, category, 1);
            }
        }));
    }

    // --- research stages

    /** Requirements of the entry's current stage, after research mode conversion. */
    public static List<ResearchRequirement> currentRequirements(Player player, Identifier id, ResearchEntry entry) {
        int stage = knowledge(player).stage(id);
        if (stage >= entry.stageCount()) {
            return List.of();
        }
        return effectiveRequirements(entry, entry.stages().get(stage));
    }

    public static List<ResearchRequirement> effectiveRequirements(ResearchEntry entry, ResearchStage stage) {
        ResearchMode mode = switch (TUConfig.RESEARCH_MODE.get()) {
            case CLASSIC -> ResearchMode.CLASSIC;
            case MODERN -> ResearchMode.MODERN;
            case UNIFIED -> ResearchMode.UNIFIED;
        };
        if (mode == ResearchMode.UNIFIED) {
            return stage.requirements();
        }
        List<RequirementSpec> specs = stage.requirements().stream().map(ResearchRequirement::toSpec).toList();
        List<String> aspects = entry.aspects().stream().map(Identifier::toString).toList();
        return ResearchModeConverter.convert(specs, mode, aspects, entry.category().toString()).stream()
                .map(ResearchRequirement::fromSpec).toList();
    }

    /** Whether the parents of an entry are complete (so it shows up as available). */
    public static boolean isAvailable(Player player, ResearchEntry entry) {
        for (Identifier parent : entry.parents()) {
            if (!isComplete(player, parent)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isMet(Player player, Identifier entryId, int stage, int index, ResearchRequirement requirement) {
        PlayerKnowledge k = knowledge(player);
        return switch (requirement) {
            case ResearchRequirement.Scan scan -> isScanMet(k, scan.target());
            case ResearchRequirement.Craft craft -> k.hasCrafted(craft.item());
            case ResearchRequirement.Obtain obtain -> countItem(player, obtain.item()) >= obtain.count();
            case ResearchRequirement.Knowledge kn -> k.knowledge(kn.kind(), kn.category()) >= kn.amount();
            case ResearchRequirement.Insight insight -> k.hasSolvedInsight(PlayerKnowledge.insightKey(entryId, stage, index));
            case ResearchRequirement.Research research -> isComplete(player, research.id());
            case ResearchRequirement.Warp warp -> k.totalWarp() >= warp.minimum();
            case ResearchRequirement.Dimension dimension -> player.level().dimension().identifier().equals(dimension.dimension());
            case ResearchRequirement.Kill kill -> k.kills(kill.entity()) >= kill.count();
        };
    }

    private static boolean isScanMet(PlayerKnowledge k, ScanTarget target) {
        if (target.kind() != ScanTarget.Kind.ITEM_TAG) {
            return k.hasScanned(target.serialize());
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, target.id());
        for (var holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
            Identifier item = holder.unwrapKey().map(key -> key.identifier()).orElse(null);
            if (item != null && k.hasScanned(ScanTarget.scanKey(ScanTarget.Kind.ITEM, item))) {
                return true;
            }
        }
        return false;
    }

    private static int countItem(Player player, Identifier item) {
        Item target = BuiltInRegistries.ITEM.getValue(item);
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(target)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** Tries to complete the current stage of an entry. Returns true on success. */
    public static boolean tryAdvance(ServerPlayer player, Identifier id) {
        Optional<ResearchEntry> found = entry(player.level().registryAccess(), id);
        if (found.isEmpty()) {
            return false;
        }
        ResearchEntry entry = found.get();
        PlayerKnowledge k = knowledge(player);
        int stage = k.stage(id);
        if (stage >= entry.stageCount() || !isAvailable(player, entry)) {
            return false;
        }
        List<ResearchRequirement> requirements = currentRequirements(player, id, entry);
        for (int i = 0; i < requirements.size(); i++) {
            if (!player.isCreative() && !isMet(player, id, stage, i, requirements.get(i))) {
                player.sendOverlayMessage(Component.translatable("message.thaumicunity.research.not_ready")
                        .withStyle(ChatFormatting.RED));
                return false;
            }
        }
        if (!player.isCreative()) {
            consume(player, k, requirements);
        }
        int next = stage + 1;
        // A last stage without requirements is the entry's closing text: reaching it completes the entry, so the
        // recipes it reveals become craftable at once.
        if (next == entry.stageCount() - 1 && effectiveRequirements(entry, entry.stages().get(next)).isEmpty()) {
            next = entry.stageCount();
        }
        k.setStage(id, next);
        sync(player);
        player.level().playSound(null, player.blockPosition(), TUSounds.RESEARCH.get(), SoundSource.PLAYERS, 0.8f, 1.0f);
        if (next >= entry.stageCount()) {
            player.sendSystemMessage(Component.translatable("message.thaumicunity.research.complete",
                    Component.translatable(ResearchEntry.titleKey(id))).withStyle(ChatFormatting.GOLD));
            NeoForge.EVENT_BUS.post(new ResearchCompletedEvent(player, id));
        }
        return true;
    }

    private static void consume(ServerPlayer player, PlayerKnowledge k, List<ResearchRequirement> requirements) {
        for (ResearchRequirement requirement : requirements) {
            if (requirement instanceof ResearchRequirement.Knowledge kn) {
                k.addKnowledge(kn.kind(), kn.category(), -kn.amount());
            } else if (requirement instanceof ResearchRequirement.Obtain obtain && obtain.consume()) {
                Item target = BuiltInRegistries.ITEM.getValue(obtain.item());
                int remaining = obtain.count();
                for (int i = 0; i < player.getInventory().getContainerSize() && remaining > 0; i++) {
                    ItemStack stack = player.getInventory().getItem(i);
                    if (stack.is(target)) {
                        int take = Math.min(remaining, stack.getCount());
                        stack.shrink(take);
                        remaining -= take;
                    }
                }
            }
        }
    }

    /** Grants (creative/commands) or revokes research. */
    public static void setComplete(ServerPlayer player, Identifier id, boolean complete) {
        entry(player.level().registryAccess(), id).ifPresent(entry -> {
            knowledge(player).setStage(id, complete ? entry.stageCount() : 0);
            sync(player);
        });
    }

    /** Grants every auto-unlocking entry (called on login). */
    public static void grantAutoUnlocks(ServerPlayer player) {
        entries(player.level().registryAccess()).ifPresent(registry -> {
            boolean changed = false;
            for (var e : registry.entrySet()) {
                if (e.getValue().autoUnlock() && knowledge(player).stage(e.getKey().identifier()) < e.getValue().stageCount()) {
                    knowledge(player).setStage(e.getKey().identifier(), e.getValue().stageCount());
                    changed = true;
                }
            }
            if (changed) {
                sync(player);
            }
        });
    }

    // --- crafting and kills feed "craft" and "kill" requirements

    public static void onCrafted(ServerPlayer player, ItemStack result) {
        if (!result.isEmpty() && knowledge(player).markCrafted(BuiltInRegistries.ITEM.getKey(result.getItem()))) {
            sync(player);
        }
    }

    public static void onKill(ServerPlayer player, Entity victim) {
        knowledge(player).addKill(BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType()));
        sync(player);
    }

    /** Research ids, used by command suggestions. */
    public static List<Identifier> allIds(RegistryAccess access) {
        List<Identifier> ids = new ArrayList<>();
        entries(access).ifPresent(r -> r.keySet().forEach(ids::add));
        ids.sort(null);
        return ids;
    }

    static void log(String message, Object... args) {
        ThaumicUnity.LOGGER.debug(message, args);
    }
}
