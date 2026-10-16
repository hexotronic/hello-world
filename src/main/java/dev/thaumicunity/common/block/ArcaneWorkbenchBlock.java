package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.ArcaneWorkbenchBlockEntity;
import dev.thaumicunity.common.menu.ArcaneWorkbenchMenu;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Arcane workbench: crafting with vis (and ordinary crafting too). */
public class ArcaneWorkbenchBlock extends MenuBlock<ArcaneWorkbenchBlockEntity> {
    public ArcaneWorkbenchBlock(Properties properties) {
        super(properties, TUBlockEntities.ARCANE_WORKBENCH, ArcaneWorkbenchBlockEntity::new, null);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Player player, ArcaneWorkbenchBlockEntity blockEntity) {
        return new ArcaneWorkbenchMenu(id, player.getInventory(), blockEntity);
    }
}
