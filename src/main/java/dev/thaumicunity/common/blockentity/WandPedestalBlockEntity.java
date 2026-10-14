package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.item.WandItem;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.spell.SpellFx;
import dev.thaumicunity.common.wand.Wands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Wand recharge pedestal: a wand placed on it fills from the chunk's aura every second, faster than one carried
 * around, and may take the aura lower (down to a quarter of its base level).
 */
public class WandPedestalBlockEntity extends SyncedBlockEntity implements DisplaysItem {
    private static final double FLOOR = 0.25;
    private ItemStack item = ItemStack.EMPTY;

    public WandPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.WAND_PEDESTAL.get(), pos, state);
    }

    @Override
    public ItemStack item() {
        return item;
    }

    public void setItem(ItemStack stack) {
        item = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        markForSync();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, WandPedestalBlockEntity pedestal) {
        if (!(level instanceof ServerLevel server) || !(pedestal.item.getItem() instanceof WandItem)
                || (level.getGameTime() + pos.asLong()) % 20 != 0) {
            return;
        }
        Wands.regenerateAffinity(server, pedestal.item);
        double moved = Wands.rechargeFromAura(server, pos, pedestal.item, TUConfig.WAND_PEDESTAL_RECHARGE.get(), FLOOR);
        if (moved > 0) {
            pedestal.markForSync();
            server.sendParticles(SpellFx.spark(0xB98AF0), pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, 3, 0.25, 0.2, 0.25, 0.01);
        }
    }

    /** Drops the held wand when the pedestal is broken. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level != null && !item.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, item);
            item = ItemStack.EMPTY;
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        item = input.read("item", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!item.isEmpty()) {
            output.store("item", ItemStack.OPTIONAL_CODEC, item);
        }
    }
}
