package dev.thaumicunity.common.menu;

import dev.thaumicunity.common.util.TUUtil;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.blockentity.ArcaneWorkbenchBlockEntity;
import dev.thaumicunity.common.recipe.ArcaneCraftingRecipe;
import dev.thaumicunity.common.registry.TUMenus;
import dev.thaumicunity.common.registry.TURecipes;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.research.ResearchManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Arcane workbench menu: 3×3 grid (kept in the block entity), a result slot and the player inventory. Arcane recipes
 * also need primal vis from the surrounding aura; the six required amounts and whether they are available are
 * synced through {@link #data} for the screen.
 */
public class ArcaneWorkbenchMenu extends AbstractContainerMenu {
    public static final int RESULT_SLOT = 9;
    private static final int DATA_AFFORDABLE = 6;
    private static final int DATA_AURA = 7;

    private final Container grid;
    private final ResultContainer result = new ResultContainer();
    private final ContainerLevelAccess access;
    private final Player player;
    private final ContainerData data;
    private @Nullable ArcaneCraftingRecipe currentArcane;

    /** Client constructor. */
    public ArcaneWorkbenchMenu(int id, Inventory inventory, RegistryFriendlyByteBuf extra) {
        this(id, inventory, new SimpleContainer(ArcaneWorkbenchBlockEntity.GRID), ContainerLevelAccess.NULL, extra.readBlockPos());
    }

    /** Server constructor. */
    public ArcaneWorkbenchMenu(int id, Inventory inventory, ArcaneWorkbenchBlockEntity table) {
        this(id, inventory, table.inventory(), ContainerLevelAccess.create(table.getLevel(), table.getBlockPos()), table.getBlockPos());
    }

    private ArcaneWorkbenchMenu(int id, Inventory inventory, Container grid, ContainerLevelAccess access, BlockPos pos) {
        super(TUMenus.ARCANE_WORKBENCH.get(), id);
        this.grid = grid;
        this.access = access;
        this.player = inventory.player;
        this.data = new SimpleContainerData(8);
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                addSlot(new Slot(grid, col + row * 3, 40 + col * 18, 40 + row * 18) {
                    @Override
                    public void setChanged() {
                        super.setChanged();
                        slotsChanged(grid);
                    }
                });
            }
        }
        addSlot(new Slot(result, 0, 160, 58) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player p) {
                return canCraftNow();
            }

            @Override
            public void onTake(Player p, ItemStack stack) {
                onCrafted(p, stack);
                super.onTake(p, stack);
            }
        });
        MenuUtil.addPlayerInventory(this::addSlot, inventory, 16, 140);
        addDataSlots(data);
        slotsChanged(grid);
    }

    public ContainerData data() {
        return data;
    }

    /** Required vis for primal {@code index} (order of {@link Aspects#PRIMALS}). */
    public int requiredVis(int index) {
        return data.get(index);
    }

    public boolean affordable() {
        return data.get(DATA_AFFORDABLE) != 0;
    }

    public int auraVis() {
        return data.get(DATA_AURA);
    }

    private CraftingInput input() {
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            items.add(grid.getItem(i));
        }
        return CraftingInput.of(3, 3, items);
    }

    @Override
    public void slotsChanged(Container container) {
        super.slotsChanged(container);
        access.execute((level, pos) -> updateResult(level, pos));
    }

    private void updateResult(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        CraftingInput input = input();
        currentArcane = null;
        for (int i = 0; i < 6; i++) {
            data.set(i, 0);
        }
        ItemStack output = ItemStack.EMPTY;
        Optional<RecipeHolder<ArcaneCraftingRecipe>> arcane = server.recipeAccess()
                .getRecipeFor(TURecipes.ARCANE_CRAFTING.get(), input, server);
        if (arcane.isPresent() && player instanceof ServerPlayer sp && ResearchManager.knows(sp, arcane.get().value().research())) {
            currentArcane = arcane.get().value();
            output = currentArcane.assemble(input);
            AspectList primals = Aspects.toPrimals(server.registryAccess(), currentArcane.vis());
            for (int i = 0; i < 6; i++) {
                data.set(i, primals.get(Aspects.PRIMALS.get(i)));
            }
        } else {
            Optional<RecipeHolder<CraftingRecipe>> vanilla = server.recipeAccess().getRecipeFor(RecipeType.CRAFTING, input, server);
            if (vanilla.isPresent()) {
                output = vanilla.get().value().assemble(input);
            }
        }
        data.set(DATA_AFFORDABLE, affordableAt(server, pos) ? 1 : 0);
        data.set(DATA_AURA, (int) AuraManager.auraAt(server, pos).vis());
        result.setItem(0, output);
        broadcastChanges();
    }

    private boolean affordableAt(Level level, BlockPos pos) {
        for (int i = 0; i < 6; i++) {
            int amount = data.get(i);
            if (amount > 0 && !AuraManager.drainPrimal(level, pos, i, amount, true)) {
                return false;
            }
        }
        // All six must be affordable together, not just individually.
        double total = 0;
        var attunement = AuraManager.attunementAt(level, pos);
        for (int i = 0; i < 6; i++) {
            total += data.get(i) * attunement.costFactor(i);
        }
        return AuraManager.auraAt(level, pos).vis() + 1.0e-9 >= total;
    }

    private boolean canCraftNow() {
        return access.evaluate((level, pos) -> currentArcane == null || affordableAt(level, pos), true);
    }

    private void onCrafted(Player crafter, ItemStack stack) {
        access.execute((level, pos) -> {
            if (currentArcane != null) {
                for (int i = 0; i < 6; i++) {
                    int amount = data.get(i);
                    if (amount > 0) {
                        AuraManager.drainPrimal(level, pos, i, amount, false);
                    }
                }
                level.playSound(null, pos, TUSounds.CRAFT_SUCCESS.get(), SoundSource.BLOCKS, 0.6f, 1.2f);
            }
            for (int i = 0; i < 9; i++) {
                ItemStack ingredient = grid.getItem(i);
                if (!ingredient.isEmpty()) {
                    var remainderTemplate = ingredient.getItem().getCraftingRemainder();
                    ingredient.shrink(1);
                    if (remainderTemplate != null) {
                        ItemStack remainder = remainderTemplate.create();
                        if (ingredient.isEmpty()) {
                            grid.setItem(i, remainder);
                        } else {
                            TUUtil.giveOrDrop(crafter, remainder);
                        }
                    }
                }
            }
            if (crafter instanceof ServerPlayer sp) {
                ResearchManager.onCrafted(sp, stack);
            }
            slotsChanged(grid);
        });
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        if (index == RESULT_SLOT) {
            Slot slot = slots.get(index);
            if (!slot.hasItem() || !slot.mayPickup(p)) {
                return ItemStack.EMPTY;
            }
            ItemStack stack = slot.getItem().copy();
            if (!moveItemStackTo(stack, 10, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
            slot.onTake(p, slot.getItem());
            return stack;
        }
        return MenuUtil.quickMove(this, index, 10, this::moveItemStackTo);
    }

    @Override
    public boolean stillValid(Player p) {
        return access.evaluate((level, pos) -> p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64, true);
    }
}
