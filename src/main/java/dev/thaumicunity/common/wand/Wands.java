package dev.thaumicunity.common.wand;

import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.aura.ChunkAura;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.core.aspect.AspectAmounts;
import dev.thaumicunity.core.aura.PrimalAttunement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/**
 * Wand rules shared by the wand item, the recharge pedestal and the arcane workbench (docs/DESIGN_DECISIONS.md, D4):
 * looking up rods and caps, what a spell costs a given wand, paying it (eating vis crystals for any shortfall), and
 * recharging from the aura.
 */
public final class Wands {
    /** Share of a spell's cost a rod's favoured primal is spared. */
    public static final double AFFINITY_DISCOUNT = 0.9;
    /** Hundredths of its favoured primal a rod regains on its own every five seconds. */
    public static final int AFFINITY_REGEN = 25;

    private Wands() {
    }

    // --- registries

    public static Optional<WandRod> rod(HolderLookup.Provider registries, Identifier id) {
        return lookup(registries, TURegistries.WAND_ROD, id);
    }

    public static Optional<WandCap> cap(HolderLookup.Provider registries, Identifier id) {
        return lookup(registries, TURegistries.WAND_CAP, id);
    }

    private static <T> Optional<T> lookup(HolderLookup.Provider registries, ResourceKey<Registry<T>> key, Identifier id) {
        return registries.lookup(key).flatMap(l -> l.get(ResourceKey.create(key, id))).map(h -> h.value());
    }

    // --- stack data

    public static WandParts parts(ItemStack wand) {
        return wand.getOrDefault(TUDataComponents.WAND_PARTS.get(), WandParts.DEFAULT);
    }

    public static WandVis vis(ItemStack wand) {
        return wand.getOrDefault(TUDataComponents.WAND_VIS.get(), WandVis.EMPTY);
    }

    /** Vis of each primal the wand holds, in hundredths (0 for an unknown rod). */
    public static int capacity(HolderLookup.Provider registries, ItemStack wand) {
        return rod(registries, parts(wand).rod()).map(r -> r.capacity() * WandVis.SCALE).orElse(0);
    }

    /** The wand's vis with its capacity brought up to date with the rod. */
    public static WandVis currentVis(HolderLookup.Provider registries, ItemStack wand) {
        WandVis vis = vis(wand);
        int capacity = capacity(registries, wand);
        return vis.capacity() == capacity ? vis : vis.withCapacity(capacity);
    }

    /** A wand made of the given rod and caps, named and coloured after them, holding no vis. */
    public static ItemStack create(HolderLookup.Provider registries, Identifier rodId, Identifier capId) {
        ItemStack stack = new ItemStack(TUItems.WAND.get());
        stack.set(TUDataComponents.WAND_PARTS.get(), new WandParts(rodId, capId));
        int rodColor = rod(registries, rodId).map(WandRod::color).orElse(0x8B6A3E);
        int capColor = cap(registries, capId).map(WandCap::color).orElse(0xC4C4C4);
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(), List.of(), List.of(), List.of(rodColor, capColor)));
        stack.set(DataComponents.ITEM_NAME, Component.translatable("item.thaumicunity.wand.parts",
                Component.translatable(WandCap.translationKey(capId)), Component.translatable(WandRod.translationKey(rodId))));
        stack.set(TUDataComponents.WAND_VIS.get(), WandVis.EMPTY.withCapacity(rod(registries, rodId).map(r -> r.capacity() * WandVis.SCALE).orElse(0)));
        return stack;
    }

    // --- costs

    /** Factor on what primal {@code index} costs with this wand: the cap's factor, less for the rod's favoured primal. */
    public static double costFactor(HolderLookup.Provider registries, ItemStack wand, int index) {
        WandParts parts = parts(wand);
        Identifier primal = Aspects.PRIMALS.get(index);
        double factor = cap(registries, parts.cap()).map(c -> c.costFactor(primal)).orElse(1.0);
        if (rod(registries, parts.rod()).flatMap(WandRod::affinity).filter(primal::equals).isPresent()) {
            factor *= AFFINITY_DISCOUNT;
        }
        return factor;
    }

    /** What a cost in primal vis comes to with this wand, in hundredths per primal. */
    public static int[] cost(HolderLookup.Provider registries, ItemStack wand, AspectAmounts primalCost) {
        int[] out = new int[6];
        for (int i = 0; i < 6; i++) {
            double plain = primalCost.get(Aspects.PRIMALS.get(i).toString());
            out[i] = plain <= 0 ? 0 : (int) Math.ceil(plain * WandVis.SCALE * costFactor(registries, wand, i) - 1.0e-6);
        }
        return out;
    }

    /** Hundredths of vis one crystal gives its primal. */
    public static int crystalValue() {
        return TUConfig.VIS_CRYSTAL_VALUE.get() * WandVis.SCALE;
    }

    /**
     * Pays a cost (hundredths per primal) from the wand. Where the wand holds too little, vis crystals of that primal
     * are taken from the player's inventory and dissolved into it. All or nothing: returns false and changes nothing
     * if the cost cannot be met. Players in creative mode pay nothing.
     */
    public static boolean pay(Player player, ItemStack wand, int[] cost) {
        if (player.isCreative()) {
            return true;
        }
        WandVis vis = currentVis(player.level().registryAccess(), wand);
        int perCrystal = crystalValue();
        int[] crystals = new int[6];
        for (int i = 0; i < 6; i++) {
            int missing = cost[i] - vis.get(i);
            if (missing > 0) {
                if (perCrystal <= 0) {
                    return false;
                }
                crystals[i] = (missing + perCrystal - 1) / perCrystal;
                if (countCrystals(player.getInventory(), i) < crystals[i]) {
                    return false;
                }
            }
        }
        // Crystal vis that does not fit after paying is lost, like overfilling a jar.
        for (int i = 0; i < 6; i++) {
            if (crystals[i] > 0) {
                takeCrystals(player.getInventory(), i, crystals[i]);
            }
            int after = vis.get(i) + crystals[i] * perCrystal - cost[i];
            vis = vis.with(i, after);
        }
        wand.set(TUDataComponents.WAND_VIS.get(), vis);
        return true;
    }

    private static Item crystal(int index) {
        return TUItems.VIS_CRYSTALS.get(Aspects.PRIMALS.get(index).getPath()).get();
    }

    private static int countCrystals(Inventory inventory, int index) {
        Item crystal = crystal(index);
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(crystal)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void takeCrystals(Inventory inventory, int index, int amount) {
        Item crystal = crystal(index);
        for (int slot = 0; slot < inventory.getContainerSize() && amount > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(crystal)) {
                int taken = Math.min(amount, stack.getCount());
                stack.shrink(taken);
                amount -= taken;
            }
        }
    }

    // --- recharging

    /**
     * Moves up to {@code budget} vis from the aura at {@code pos} into the wand's primals that are not full, shared
     * out by how strongly the place resonates with each primal. The aura is not taken below {@code floor} times its
     * base level. Returns the vis moved.
     */
    public static double rechargeFromAura(ServerLevel level, BlockPos pos, ItemStack wand, double budget, double floor) {
        WandVis vis = currentVis(level.registryAccess(), wand);
        if (vis.capacity() <= 0 || budget <= 0) {
            return 0;
        }
        ChunkAura aura = AuraManager.auraAt(level, pos);
        double available = Math.min(budget, aura.vis() - aura.base() * floor);
        if (available <= 0) {
            return 0;
        }
        PrimalAttunement attunement = aura.attunement();
        double weights = 0;
        for (int i = 0; i < 6; i++) {
            if (!vis.isFull(i)) {
                weights += attunement.weight(i);
            }
        }
        if (weights <= 0) {
            return 0;
        }
        int moved = 0;
        for (int i = 0; i < 6; i++) {
            if (vis.isFull(i)) {
                continue;
            }
            int share = (int) (available * attunement.weight(i) / weights * WandVis.SCALE);
            int added = Math.min(share, vis.capacity() - vis.get(i));
            if (added > 0) {
                vis = vis.with(i, vis.get(i) + added);
                moved += added;
            }
        }
        if (moved > 0) {
            AuraManager.drainVis(level, pos, moved / (double) WandVis.SCALE);
            wand.set(TUDataComponents.WAND_VIS.get(), vis);
        }
        return moved / (double) WandVis.SCALE;
    }

    /** A rod with an affinity slowly refills its favoured primal by itself. */
    public static void regenerateAffinity(Level level, ItemStack wand) {
        WandParts parts = parts(wand);
        Optional<Identifier> affinity = rod(level.registryAccess(), parts.rod()).flatMap(WandRod::affinity);
        if (affinity.isEmpty()) {
            return;
        }
        int index = Aspects.PRIMALS.indexOf(affinity.get());
        WandVis vis = currentVis(level.registryAccess(), wand);
        if (index < 0 || vis.isFull(index) || vis.capacity() <= 0) {
            return;
        }
        wand.set(TUDataComponents.WAND_VIS.get(), vis.with(index, vis.get(index) + AFFINITY_REGEN));
    }
}
