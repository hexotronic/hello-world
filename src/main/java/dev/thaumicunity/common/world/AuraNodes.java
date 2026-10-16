package dev.thaumicunity.common.world;

import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.aura.node.NodeModifier;
import dev.thaumicunity.common.aura.node.NodeType;
import dev.thaumicunity.common.blockentity.AuraNodeBlockEntity;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUFlora;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;

/** Placing aura nodes during world generation. */
public final class AuraNodes {
    private AuraNodes() {
    }

    /** Nodes form unless the aura runs in "chunk" mode, which has none (docs/DESIGN_DECISIONS.md, D2). */
    public static boolean nodesGenerate() {
        return TUConfig.AURA_MODE.get() != TUConfig.AuraModeOption.CHUNK;
    }

    /** Places a node of the given type with a random modifier and capacities that suit the biome. */
    public static boolean place(WorldGenLevel level, BlockPos pos, NodeType type, RandomSource random) {
        level.setBlock(pos, TUFlora.AURA_NODE.get().defaultBlockState(), 2);
        if (level.getBlockEntity(pos) instanceof AuraNodeBlockEntity node) {
            NodeModifier modifier = NodeModifier.random(random);
            node.initialize(type, modifier, AuraNodeBlockEntity.randomCapacities(
                    AuraManager.profileOf(level.getBiome(pos)).toAttunement(), modifier, random));
            return true;
        }
        return false;
    }
}
