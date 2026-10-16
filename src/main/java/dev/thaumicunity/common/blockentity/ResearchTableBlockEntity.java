package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Research table: slot 0 holds scribing tools, slot 1 holds paper or research notes. */
public class ResearchTableBlockEntity extends InventoryBlockEntity {
    public static final int TOOLS_SLOT = 0;
    public static final int PAPER_SLOT = 1;

    public ResearchTableBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.RESEARCH_TABLE.get(), pos, state, 2);
    }
}
