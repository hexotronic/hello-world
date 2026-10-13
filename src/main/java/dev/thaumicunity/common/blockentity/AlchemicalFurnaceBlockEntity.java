package dev.thaumicunity.common.blockentity;

import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.common.spell.SpellFx;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Alchemical furnace: burns fuel to distil items into essentia and pushes it into a warded jar placed directly on
 * top (alembics and tubes extend this in phase 4).
 */
public class AlchemicalFurnaceBlockEntity extends InventoryBlockEntity {
    public static final int INPUT = 0;
    public static final int FUEL = 1;
    public static final int CAPACITY = 64;
    public static final int COOK_TIME = 80;

    private AspectList buffer = AspectList.EMPTY;
    private int burnTime;
    private int burnTotal;
    private int progress;

    /** Synced to the menu: burn time, burn total, progress, buffered essentia. */
    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnTotal;
                case 2 -> progress;
                case 3 -> buffer.total();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> burnTotal = value;
                case 2 -> progress = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return 4;
        }
    };

    public AlchemicalFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(TUBlockEntities.ALCHEMICAL_FURNACE.get(), pos, state, 2);
    }

    public AspectList buffer() {
        return buffer;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AlchemicalFurnaceBlockEntity furnace) {
        boolean changed = false;
        ItemStack input = furnace.inventory.getItem(INPUT);
        AspectList aspects = ItemAspects.of(input, false);
        boolean canWork = !input.isEmpty() && !aspects.isEmpty() && furnace.buffer.total() + aspects.total() <= CAPACITY;
        if (furnace.burnTime > 0) {
            furnace.burnTime--;
        } else if (canWork) {
            ItemStack fuel = furnace.inventory.getItem(FUEL);
            Integer fuelTicks = fuel.isEmpty() ? null : fuel.getItem().builtInRegistryHolder().getData(TUDataMaps.ALCHEMICAL_FUEL);
            int duration = fuelTicks == null ? 0 : fuelTicks;
            if (duration > 0) {
                furnace.burnTime = furnace.burnTotal = duration;
                ItemStack remainder = fuel.getItem().getCraftingRemainder() != null
                        ? fuel.getItem().getCraftingRemainder().create() : ItemStack.EMPTY;
                fuel.shrink(1);
                if (fuel.isEmpty() && !remainder.isEmpty()) {
                    furnace.inventory.setItem(FUEL, remainder);
                }
                changed = true;
            }
        }
        if (furnace.burnTime > 0 && canWork) {
            if (++furnace.progress >= COOK_TIME) {
                furnace.progress = 0;
                furnace.buffer = furnace.buffer.add(aspects);
                input.shrink(1);
                changed = true;
            }
        } else if (furnace.progress > 0) {
            furnace.progress = Math.max(0, furnace.progress - 2);
        }
        if (level.getGameTime() % 5 == 0 && !furnace.buffer.isEmpty()
                && level.getBlockEntity(pos.above()) instanceof WardedJarBlockEntity jar) {
            for (Identifier aspect : furnace.buffer.asMap().keySet()) {
                if (jar.add(aspect, 1) > 0) {
                    furnace.buffer = furnace.buffer.subtract(AspectList.of(aspect, 1));
                    changed = true;
                    if (level instanceof ServerLevel server) {
                        server.sendParticles(SpellFx.spark(dev.thaumicunity.common.aspect.Aspects.color(level.registryAccess(), aspect)),
                                pos.getX() + 0.5, pos.getY() + 1.05, pos.getZ() + 0.5, 2, 0.1, 0.05, 0.1, 0.0);
                    }
                    break;
                }
            }
        }
        if (changed) {
            furnace.setChanged();
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        buffer = input.read("buffer", AspectList.CODEC).orElse(AspectList.EMPTY);
        burnTime = input.getIntOr("burn_time", 0);
        burnTotal = input.getIntOr("burn_total", 0);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!buffer.isEmpty()) {
            output.store("buffer", AspectList.CODEC, buffer);
        }
        output.putInt("burn_time", burnTime);
        output.putInt("burn_total", burnTotal);
        output.putInt("progress", progress);
    }
}
