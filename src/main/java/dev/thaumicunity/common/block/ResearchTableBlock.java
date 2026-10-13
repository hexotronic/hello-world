package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.ResearchTableBlockEntity;
import dev.thaumicunity.common.menu.ResearchTableMenu;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Research table: insight puzzles and theorycraft. */
public class ResearchTableBlock extends MenuBlock<ResearchTableBlockEntity> {
    public ResearchTableBlock(Properties properties) {
        super(properties, TUBlockEntities.RESEARCH_TABLE, ResearchTableBlockEntity::new, null);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Player player, ResearchTableBlockEntity blockEntity) {
        return new ResearchTableMenu(id, player.getInventory(), blockEntity);
    }
}
