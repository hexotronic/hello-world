package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.block.AlchemicalFurnaceBlock;
import dev.thaumicunity.common.block.ArcanePedestalBlock;
import dev.thaumicunity.common.block.ArcaneWorkbenchBlock;
import dev.thaumicunity.common.block.CrucibleBlock;
import dev.thaumicunity.common.block.ResearchTableBlock;
import dev.thaumicunity.common.block.RunicMatrixBlock;
import dev.thaumicunity.common.block.WandPedestalBlock;
import dev.thaumicunity.common.block.WardedJarBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Blocks (phase 3 set; docs/PLAN.md lists what later phases add). */
public final class TUBlocks {
    static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ThaumicUnity.MOD_ID);

    // --- devices
    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = BLOCKS.registerBlock("crucible", CrucibleBlock::new,
            p -> p.mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<ArcaneWorkbenchBlock> ARCANE_WORKBENCH = BLOCKS.registerBlock("arcane_workbench",
            ArcaneWorkbenchBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD));
    public static final DeferredBlock<ResearchTableBlock> RESEARCH_TABLE = BLOCKS.registerBlock("research_table",
            ResearchTableBlock::new, p -> p.mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion());
    public static final DeferredBlock<RunicMatrixBlock> RUNIC_MATRIX = BLOCKS.registerBlock("runic_matrix", RunicMatrixBlock::new,
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(3.0f, 12.0f).sound(SoundType.AMETHYST).requiresCorrectToolForDrops()
                    .noOcclusion().lightLevel(s -> 7));
    public static final DeferredBlock<ArcanePedestalBlock> ARCANE_PEDESTAL = BLOCKS.registerBlock("arcane_pedestal",
            ArcanePedestalBlock::new, p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<WandPedestalBlock> WAND_PEDESTAL = BLOCKS.registerBlock("wand_pedestal",
            WandPedestalBlock::new, p -> p.mapColor(MapColor.STONE).strength(2.0f, 6.0f).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops().noOcclusion());
    public static final DeferredBlock<WardedJarBlock> WARDED_JAR = BLOCKS.registerBlock("warded_jar", WardedJarBlock::new,
            p -> p.mapColor(MapColor.NONE).strength(0.6f).sound(SoundType.GLASS).noOcclusion());
    public static final DeferredBlock<AlchemicalFurnaceBlock> ALCHEMICAL_FURNACE = BLOCKS.registerBlock("alchemical_furnace",
            AlchemicalFurnaceBlock::new, p -> p.mapColor(MapColor.STONE).strength(3.0f, 6.0f).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops().lightLevel(s -> 10));

    // --- building and storage blocks
    public static final DeferredBlock<Block> ARCANE_STONE = BLOCKS.registerSimpleBlock("arcane_stone",
            p -> p.mapColor(MapColor.STONE).strength(2.0f, 10.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> ARCANE_STONE_BRICKS = BLOCKS.registerSimpleBlock("arcane_stone_bricks",
            p -> p.mapColor(MapColor.STONE).strength(2.0f, 10.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> THAUMIUM_BLOCK = BLOCKS.registerSimpleBlock("thaumium_block",
            p -> p.mapColor(MapColor.COLOR_PURPLE).strength(5.0f, 8.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> BRASS_BLOCK = BLOCKS.registerSimpleBlock("brass_block",
            p -> p.mapColor(MapColor.GOLD).strength(5.0f, 6.0f).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> AMBER_BLOCK = BLOCKS.registerSimpleBlock("amber_block",
            p -> p.mapColor(MapColor.COLOR_ORANGE).strength(1.5f).sound(SoundType.GLASS).noOcclusion().lightLevel(s -> 4));
    public static final DeferredBlock<Block> CINNABAR_ORE = BLOCKS.registerSimpleBlock("cinnabar_ore",
            p -> p.mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops());
    public static final DeferredBlock<Block> AMBER_ORE = BLOCKS.registerSimpleBlock("amber_ore",
            p -> p.mapColor(MapColor.STONE).strength(3.0f, 3.0f).requiresCorrectToolForDrops());

    private TUBlocks() {
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
