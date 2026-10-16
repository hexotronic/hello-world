package dev.thaumicunity.common.equipment;

import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.common.registry.TUTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What tools, weapons and armour do beyond vanilla's numbers (phase 4.4), driven by data so packs can extend it:
 * <ul>
 *   <li>data map {@code thaumicunity:vis_discount}: worn items spare part of every spell's vis cost;</li>
 *   <li>tag {@code thaumicunity:self_repairing}: carried or worn items mend a point of durability now and then;</li>
 *   <li>tag {@code thaumicunity:weakening}: melee hits with these weapons leave the target weakened.</li>
 * </ul>
 */
public final class EquipmentEffects {
    private static final EquipmentSlot[] WORN = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private EquipmentEffects() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EquipmentEffects::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(EquipmentEffects::onDamage);
    }

    // --- vis discount

    /** Share of spell vis cost (percent) one item spares its wearer. */
    public static int discountOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        Integer percent = stack.getItem().builtInRegistryHolder().getData(TUDataMaps.VIS_DISCOUNT);
        return percent == null ? 0 : percent;
    }

    /** Share of spell vis cost (percent) everything the entity wears spares it, up to the configured maximum. */
    public static int visDiscount(LivingEntity entity) {
        int total = 0;
        for (EquipmentSlot slot : WORN) {
            total += discountOf(entity.getItemBySlot(slot));
        }
        return Math.max(0, Math.min(total, TUConfig.MAX_VIS_DISCOUNT.get()));
    }

    /** Multiplier on what a spell costs this entity. */
    public static double costFactor(LivingEntity entity) {
        return 1.0 - visDiscount(entity) / 100.0;
    }

    // --- self-repair

    /** Mends one point of every damaged self-repairing item the player carries or wears; returns how many. */
    public static int selfRepair(Player player) {
        Inventory inventory = player.getInventory();
        int mended = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isDamaged() && stack.is(TUTags.Items.SELF_REPAIRING)) {
                stack.setDamageValue(stack.getDamageValue() - 1);
                mended++;
            }
        }
        return mended;
    }

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        int interval = TUConfig.SELF_REPAIR_INTERVAL.get();
        if (interval > 0 && !player.level().isClientSide() && player.tickCount % interval == 0) {
            selfRepair(player);
        }
    }

    // --- weakening blades

    /** Weakens {@code target} if {@code attacker} struck it with a weakening weapon; returns whether it did. */
    public static boolean weaken(LivingEntity target, LivingEntity attacker) {
        int ticks = TUConfig.WEAKNESS_DURATION.get();
        if (ticks <= 0 || target == attacker || !attacker.getMainHandItem().is(TUTags.Items.WEAKENING)) {
            return false;
        }
        return target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0), attacker);
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        // Only melee: the attacker itself dealt the blow, not an arrow or a spell it sent.
        if (event.getEntity().level().isClientSide() || event.getInflictedDamage() <= 0
                || !(event.getSource().getDirectEntity() instanceof LivingEntity attacker)) {
            return;
        }
        weaken(event.getEntity(), attacker);
    }
}
