package dev.thaumicunity.common.menu;

import dev.thaumicunity.common.blockentity.AlchemicalFurnaceBlockEntity;
import dev.thaumicunity.common.registry.TUMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Alchemical furnace menu: input and fuel slots, burn/progress/essentia bars. */
public class AlchemicalFurnaceMenu extends AbstractContainerMenu {
    private final Container container;
    private final ContainerData data;

    public AlchemicalFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, new SimpleContainer(2), new SimpleContainerData(4));
        extra.readBlockPos();
    }

    public AlchemicalFurnaceMenu(int id, Inventory inventory, AlchemicalFurnaceBlockEntity furnace) {
        this(id, inventory, furnace.inventory(), furnace.data);
    }

    private AlchemicalFurnaceMenu(int id, Inventory inventory, Container container, ContainerData data) {
        super(TUMenus.ALCHEMICAL_FURNACE.get(), id);
        this.container = container;
        this.data = data;
        addSlot(new Slot(container, AlchemicalFurnaceBlockEntity.INPUT, 56, 17));
        addSlot(new Slot(container, AlchemicalFurnaceBlockEntity.FUEL, 56, 53));
        MenuUtil.addPlayerInventory(this::addSlot, inventory, 8, 84);
        addDataSlots(data);
    }

    public float burnFraction() {
        int total = data.get(1);
        return total <= 0 ? 0 : data.get(0) / (float) total;
    }

    public float progressFraction() {
        return data.get(2) / (float) AlchemicalFurnaceBlockEntity.COOK_TIME;
    }

    public float essentiaFraction() {
        return data.get(3) / (float) AlchemicalFurnaceBlockEntity.CAPACITY;
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
