package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Arcane workbench: keeps its 3×3 grid between uses, like the original. */
public class ArcaneWorkbenchBlockEntity extends InventoryBlockEntity {
    public static final int GRID = 9;

    public ArcaneWorkbenchBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.ARCANE_WORKBENCH.get(), pos, state, GRID);
    }
}
