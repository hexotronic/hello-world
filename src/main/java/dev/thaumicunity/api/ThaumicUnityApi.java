package dev.thaumicunity.api;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.research.ResearchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Optional;

/**
 * Stable entry point for other mods (docs/API.md). Content (aspects, research, recipes, spell components) is added
 * with datapacks; this class exposes the runtime queries and actions. Registry keys live in {@link TURegistries}.
 */
public final class ThaumicUnityApi {
    private ThaumicUnityApi() {
    }

    // --- aspects

    /** Aspects of an item stack's item, as resolved for this side. */
    public static AspectList aspectsOf(ItemStack stack, boolean clientSide) {
        return ItemAspects.of(stack, clientSide);
    }

    /** Aspects of a creature type (scanning). */
    public static AspectList aspectsOf(EntityType<?> type) {
        return ItemAspects.ofEntity(type);
    }

    public static Optional<Aspect> aspect(RegistryAccess access, Identifier id) {
        return Aspects.get(access, id);
    }

    // --- research

    public static boolean isResearchComplete(Player player, Identifier research) {
        return ResearchManager.isComplete(player, research);
    }

    public static int researchStage(Player player, Identifier research) {
        return ResearchManager.knowledge(player).stage(research);
    }

    public static boolean hasScanned(Player player, String scanKey) {
        return ResearchManager.knowledge(player).hasScanned(scanKey);
    }

    // --- aura (server side)

    public static double vis(Level level, BlockPos pos) {
        return AuraManager.auraAt(level, pos).vis();
    }

    public static double flux(Level level, BlockPos pos) {
        return AuraManager.auraAt(level, pos).flux();
    }

    /** Drains up to {@code amount} vis; returns what was drained. */
    public static double drainVis(Level level, BlockPos pos, double amount) {
        return AuraManager.drainVis(level, pos, amount);
    }

    public static void addFlux(Level level, BlockPos pos, double amount) {
        AuraManager.addFlux(level, pos, amount);
    }

    // --- warp

    public static int warp(Player player) {
        return ResearchManager.knowledge(player).totalWarp();
    }
}
