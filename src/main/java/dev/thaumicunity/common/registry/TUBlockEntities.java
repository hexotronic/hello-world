package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.blockentity.AlchemicalFurnaceBlockEntity;
import dev.thaumicunity.common.blockentity.ArcanePedestalBlockEntity;
import dev.thaumicunity.common.blockentity.ArcaneWorkbenchBlockEntity;
import dev.thaumicunity.common.blockentity.CrucibleBlockEntity;
import dev.thaumicunity.common.blockentity.ResearchTableBlockEntity;
import dev.thaumicunity.common.blockentity.RunicMatrixBlockEntity;
import dev.thaumicunity.common.blockentity.WardedJarBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/** Block entity types. */
public final class TUBlockEntities {
    private static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrucibleBlockEntity>> CRUCIBLE = TYPES.register("crucible",
            () -> new BlockEntityType<>(CrucibleBlockEntity::new, Set.of(TUBlocks.CRUCIBLE.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcaneWorkbenchBlockEntity>> ARCANE_WORKBENCH =
            TYPES.register("arcane_workbench", () -> new BlockEntityType<>(ArcaneWorkbenchBlockEntity::new, Set.of(TUBlocks.ARCANE_WORKBENCH.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ResearchTableBlockEntity>> RESEARCH_TABLE =
            TYPES.register("research_table", () -> new BlockEntityType<>(ResearchTableBlockEntity::new, Set.of(TUBlocks.RESEARCH_TABLE.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RunicMatrixBlockEntity>> RUNIC_MATRIX =
            TYPES.register("runic_matrix", () -> new BlockEntityType<>(RunicMatrixBlockEntity::new, Set.of(TUBlocks.RUNIC_MATRIX.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ArcanePedestalBlockEntity>> ARCANE_PEDESTAL =
            TYPES.register("arcane_pedestal", () -> new BlockEntityType<>(ArcanePedestalBlockEntity::new, Set.of(TUBlocks.ARCANE_PEDESTAL.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WardedJarBlockEntity>> WARDED_JAR =
            TYPES.register("warded_jar", () -> new BlockEntityType<>(WardedJarBlockEntity::new, Set.of(TUBlocks.WARDED_JAR.get())));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AlchemicalFurnaceBlockEntity>> ALCHEMICAL_FURNACE =
            TYPES.register("alchemical_furnace", () -> new BlockEntityType<>(AlchemicalFurnaceBlockEntity::new,
                    Set.of(TUBlocks.ALCHEMICAL_FURNACE.get())));

    private TUBlockEntities() {
    }

    public static void init(IEventBus modBus) {
        TYPES.register(modBus);
    }
}
