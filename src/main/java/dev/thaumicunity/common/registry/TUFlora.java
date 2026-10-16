package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.block.AuraNodeBlock;
import dev.thaumicunity.common.block.MagicPlantBlock;
import dev.thaumicunity.common.block.MagicSaplingBlock;
import dev.thaumicunity.common.block.VisCrystalClusterBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Flora, crystal clusters and aura nodes (phase 4.5). The table in tools/content/flora.py generates their models,
 * loot, tags, aspects, recipes, research and world generation; the game test {@code flora_and_nodes} checks both
 * sides agree.
 */
public final class TUFlora {
    // --- greatwood and silverwood
    public static final DeferredBlock<RotatedPillarBlock> GREATWOOD_LOG = log("greatwood_log", MapColor.COLOR_BROWN);
    public static final DeferredBlock<Block> GREATWOOD_PLANKS = planks("greatwood_planks", MapColor.WOOD);
    public static final DeferredBlock<TintedParticleLeavesBlock> GREATWOOD_LEAVES = leaves("greatwood_leaves", MapColor.PLANT);
    public static final DeferredBlock<MagicSaplingBlock> GREATWOOD_SAPLING = sapling("greatwood_sapling", "greatwood_tree");
    public static final DeferredBlock<RotatedPillarBlock> SILVERWOOD_LOG = log("silverwood_log", MapColor.QUARTZ);
    public static final DeferredBlock<Block> SILVERWOOD_PLANKS = planks("silverwood_planks", MapColor.QUARTZ);
    public static final DeferredBlock<TintedParticleLeavesBlock> SILVERWOOD_LEAVES = leaves("silverwood_leaves", MapColor.COLOR_LIGHT_BLUE);
    public static final DeferredBlock<MagicSaplingBlock> SILVERWOOD_SAPLING = sapling("silverwood_sapling", "silverwood_tree");

    // --- plants: stew effect, ground (block tag), light
    public static final DeferredBlock<MagicPlantBlock> SHIMMERLEAF = TUBlocks.BLOCKS.registerBlock("shimmerleaf",
            p -> new MagicPlantBlock(MobEffects.GLOWING, 8, soil("shimmerleaf_soil"), p), plant(6));
    public static final DeferredBlock<MagicPlantBlock> CINDERPEARL = TUBlocks.BLOCKS.registerBlock("cinderpearl",
            p -> new MagicPlantBlock(MobEffects.FIRE_RESISTANCE, 6, soil("cinderpearl_soil"), p), plant(10));
    public static final DeferredBlock<MagicPlantBlock> VISHROOM = TUBlocks.BLOCKS.registerBlock("vishroom",
            p -> new MagicPlantBlock(MobEffects.POISON, 8, soil("vishroom_soil"), p), plant(4));

    // --- vis crystal clusters, one per primal
    public static final Map<String, DeferredBlock<VisCrystalClusterBlock>> CRYSTAL_CLUSTERS = new LinkedHashMap<>();

    // --- aura node: unbreakable, no item; worldgen places it
    public static final DeferredBlock<AuraNodeBlock> AURA_NODE = TUBlocks.BLOCKS.registerBlock("aura_node", AuraNodeBlock::new,
            p -> p.noCollision().noOcclusion().noLootTable().strength(-1.0f, 3600000.0f).lightLevel(s -> 9)
                    .mapColor(MapColor.NONE));

    /** Block items, by block name. */
    public static final Map<String, DeferredItem<BlockItem>> ITEMS = new LinkedHashMap<>();

    static {
        for (Identifier primal : Aspects.PRIMALS) {
            String name = primal.getPath() + "_crystal_cluster";
            CRYSTAL_CLUSTERS.put(primal.getPath(), TUBlocks.BLOCKS.registerBlock(name, p -> new VisCrystalClusterBlock(primal, p),
                    p -> p.mapColor(MapColor.COLOR_PURPLE).noOcclusion().sound(SoundType.AMETHYST_CLUSTER).strength(1.5f)
                            .lightLevel(s -> 5)));
        }
        for (DeferredBlock<? extends Block> block : java.util.List.of(GREATWOOD_LOG, GREATWOOD_PLANKS, GREATWOOD_LEAVES,
                GREATWOOD_SAPLING, SILVERWOOD_LOG, SILVERWOOD_PLANKS, SILVERWOOD_LEAVES, SILVERWOOD_SAPLING, SHIMMERLEAF,
                CINDERPEARL, VISHROOM)) {
            ITEMS.put(block.getId().getPath(), TUItems.ITEMS.registerSimpleBlockItem(block));
        }
        CRYSTAL_CLUSTERS.values().forEach(block -> ITEMS.put(block.getId().getPath(), TUItems.ITEMS.registerSimpleBlockItem(block)));
    }

    private TUFlora() {
    }

    /** Loads this class so its blocks and items are registered. */
    static void bootstrap() {
    }

    public static ResourceKey<Feature> feature(String name) {
        return ResourceKey.create(Registries.FEATURE, ThaumicUnity.id(name));
    }

    private static TagKey<Block> soil(String name) {
        return TagKey.create(Registries.BLOCK, ThaumicUnity.id(name));
    }

    private static DeferredBlock<RotatedPillarBlock> log(String name, MapColor color) {
        return TUBlocks.BLOCKS.registerBlock(name, RotatedPillarBlock::new,
                p -> p.mapColor(color).strength(2.0f).sound(SoundType.WOOD).ignitedByLava());
    }

    private static DeferredBlock<Block> planks(String name, MapColor color) {
        return TUBlocks.BLOCKS.registerSimpleBlock(name, p -> p.mapColor(color).strength(2.0f, 3.0f).sound(SoundType.WOOD).ignitedByLava());
    }

    private static DeferredBlock<TintedParticleLeavesBlock> leaves(String name, MapColor color) {
        return TUBlocks.BLOCKS.registerBlock(name, p -> new TintedParticleLeavesBlock(0.01f, p),
                p -> p.mapColor(color).strength(0.2f).randomTicks().sound(SoundType.GRASS).noOcclusion().ignitedByLava()
                        .isSuffocating((s, l, pos) -> false));
    }

    private static DeferredBlock<MagicSaplingBlock> sapling(String name, String tree) {
        return TUBlocks.BLOCKS.registerBlock(name, p -> new MagicSaplingBlock(feature(tree), p),
                p -> p.mapColor(MapColor.PLANT).noCollision().randomTicks().instabreak().sound(SoundType.GRASS));
    }

    private static UnaryOperator<BlockBehaviour.Properties> plant(int light) {
        return p -> p.mapColor(MapColor.PLANT).noCollision().instabreak().sound(SoundType.GRASS)
                .offsetType(BlockBehaviour.OffsetType.XZ).lightLevel(s -> light);
    }
}
