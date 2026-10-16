package dev.thaumicunity.common.menu;

import dev.thaumicunity.common.blockentity.ResearchTableBlockEntity;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TUMenus;
import dev.thaumicunity.common.registry.TUTags;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Research table menu: scribing tools and paper/notes slots plus the player inventory. The insight puzzle and
 * theorycraft actions arrive as packets ({@code ResearchTablePayload}) and are executed by
 * {@link dev.thaumicunity.common.research.ResearchTableActions} after validating that this menu is open.
 */
public class ResearchTableMenu extends AbstractContainerMenu {
    private final Container container;
    private final BlockPos pos;

    public ResearchTableMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, new SimpleContainer(2), extra.readBlockPos());
    }

    public ResearchTableMenu(int id, Inventory inventory, ResearchTableBlockEntity table) {
        this(id, inventory, table.inventory(), table.getBlockPos());
    }

    private ResearchTableMenu(int id, Inventory inventory, Container container, BlockPos pos) {
        super(TUMenus.RESEARCH_TABLE.get(), id);
        this.container = container;
        this.pos = pos;
        addSlot(new Slot(container, ResearchTableBlockEntity.TOOLS_SLOT, 14, 18) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(TUItems.SCRIBING_TOOLS.get());
            }
        });
        addSlot(new Slot(container, ResearchTableBlockEntity.PAPER_SLOT, 14, 40) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(TUTags.Items.RESEARCH_PAPER) || stack.is(TUItems.RESEARCH_NOTES.get());
            }
        });
        MenuUtil.addPlayerInventory(this::addSlot, inventory, 48, 174);
    }

    public BlockPos pos() {
        return pos;
    }

    public ItemStack tools() {
        return container.getItem(ResearchTableBlockEntity.TOOLS_SLOT);
    }

    public ItemStack paper() {
        return container.getItem(ResearchTableBlockEntity.PAPER_SLOT);
    }

    public void setPaper(ItemStack stack) {
        container.setItem(ResearchTableBlockEntity.PAPER_SLOT, stack);
        broadcastChanges();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return MenuUtil.quickMove(this, index, 2, this::moveItemStackTo);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
