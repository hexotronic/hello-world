package dev.thaumicunity.common.item;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.spell.Casting;
import dev.thaumicunity.common.util.TUUtil;
import dev.thaumicunity.common.wand.WandCap;
import dev.thaumicunity.common.wand.WandFocus;
import dev.thaumicunity.common.wand.WandParts;
import dev.thaumicunity.common.wand.WandRod;
import dev.thaumicunity.common.wand.WandVis;
import dev.thaumicunity.common.wand.Wands;
import dev.thaumicunity.core.spell.CompiledSpell;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Wand (docs/DESIGN_DECISIONS.md, D4): a rod and two caps ({@link WandParts}) that store vis per primal
 * ({@link WandVis}) and cast the focus fixed to them ({@link WandFocus}).
 * <ul>
 *   <li>use: cast the fixed focus, paying from the wand's own vis (and vis crystals in the inventory)</li>
 *   <li>sneak + use with a focus in the other hand: fix that focus (the old one comes back)</li>
 *   <li>sneak + use with the other hand empty: take the focus off</li>
 *   <li>use on a bookshelf, crafting table, cauldron or lectern: awaken it like Salis Mundus, for a little vis</li>
 * </ul>
 * Carried in the inventory, a wand slowly draws vis from a strong aura; a rod with an affinity also regains its
 * favoured primal by itself.
 */
public class WandItem extends Item {
    /** Vis (hundredths) of Ordo and Perditio that awakening a block costs. */
    private static final int AWAKEN_COST = 200;
    private static final int RECHARGE_INTERVAL = 100;
    /** A carried wand does not take the aura below this share of its base level. */
    private static final double CARRIED_FLOOR = 0.6;
    private static final int BAR_COLOR = 0xB98AF0;

    public WandItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack wand = player.getItemInHand(hand);
        if (!TUConfig.WANDS_ENABLED.get()) {
            return InteractionResult.FAIL;
        }
        InteractionHand otherHand = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        if (player.isSecondaryUseActive()) {
            return swapFocus(level, player, wand, otherHand);
        }
        WandFocus focus = wand.get(TUDataComponents.WAND_FOCUS.get());
        if (focus == null) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.thaumicunity.wand.no_focus"));
            }
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        CompiledSpell spell = Casting.prepare(server, player, focus.stack(), "message.thaumicunity.wand.no_focus");
        if (spell == null) {
            return InteractionResult.FAIL;
        }
        int[] cost = Wands.cost(player, wand, spell.cost());
        if (!Wands.pay(player, wand, cost)) {
            Casting.fizzle(server, player, wand, "message.thaumicunity.wand.no_vis");
            return InteractionResult.FAIL;
        }
        Casting.release(server, serverPlayer, wand, spell, 1.0);
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult swapFocus(Level level, Player player, ItemStack wand, InteractionHand otherHand) {
        ItemStack held = player.getItemInHand(otherHand);
        WandFocus attached = wand.get(TUDataComponents.WAND_FOCUS.get());
        if (held.getItem() instanceof FocusItem) {
            if (!level.isClientSide()) {
                ItemStack newFocus = held.copyWithCount(1);
                held.shrink(1);
                wand.set(TUDataComponents.WAND_FOCUS.get(), new WandFocus(newFocus));
                if (attached != null) {
                    if (held.isEmpty()) {
                        player.setItemInHand(otherHand, attached.stack());
                    } else {
                        TUUtil.giveOrDrop(player, attached.stack());
                    }
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8f, 1.4f);
            }
            return InteractionResult.SUCCESS;
        }
        if (attached != null && held.isEmpty()) {
            if (!level.isClientSide()) {
                wand.remove(TUDataComponents.WAND_FOCUS.get());
                player.setItemInHand(otherHand, attached.stack());
                level.playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), SoundSource.PLAYERS, 0.8f, 0.9f);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    /** A wand awakens blocks like Salis Mundus, paying with vis instead of salt. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isSecondaryUseActive() || !TUConfig.WANDS_ENABLED.get()
                || !SalisMundusItem.canAwaken(context.getLevel().getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (context.getLevel() instanceof ServerLevel server) {
            int[] cost = new int[6];
            cost[Aspects.PRIMALS.indexOf(Aspects.ORDO)] = AWAKEN_COST;
            cost[Aspects.PRIMALS.indexOf(Aspects.PERDITIO)] = AWAKEN_COST;
            if (!Wands.pay(player, context.getItemInHand(), cost)) {
                Casting.fizzle(server, player, context.getItemInHand(), "message.thaumicunity.wand.no_vis");
                return InteractionResult.FAIL;
            }
            SalisMundusItem.awaken(server, context.getClickedPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot slot) {
        if (!(entity instanceof Player) || (level.getGameTime() + entity.getId()) % RECHARGE_INTERVAL != 0) {
            return;
        }
        // A freshly made wand learns its capacity from its rod here, so its bar and tooltip can show it.
        WandVis current = Wands.currentVis(level.registryAccess(), stack);
        if (!current.equals(Wands.vis(stack))) {
            stack.set(TUDataComponents.WAND_VIS.get(), current);
        }
        Wands.regenerateAffinity(level, stack);
        Wands.rechargeFromAura(level, entity.blockPosition(), stack, TUConfig.WAND_AURA_RECHARGE.get(), CARRIED_FLOOR);
    }

    // --- display

    @Override
    public boolean isBarVisible(ItemStack stack) {
        WandVis vis = Wands.vis(stack);
        return vis.capacity() > 0 && vis.total() < vis.capacity() * 6;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        WandVis vis = Wands.vis(stack);
        return vis.capacity() <= 0 ? 0 : Mth.clamp(Math.round(13f * vis.total() / (vis.capacity() * 6f)), 0, 13);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        HolderLookup.Provider registries = context.registries();
        WandVis vis = Wands.vis(stack);
        for (int i = 0; i < 6; i++) {
            var primal = Aspects.PRIMALS.get(i);
            int color = registries instanceof RegistryAccess access ? Aspects.color(access, primal) : 0xFFFFFF;
            tooltip.accept(Aspect.displayName(primal, color).append(Component.literal(" " + amount(vis.get(i))
                    + (vis.capacity() > 0 ? " / " + amount(vis.capacity()) : "")).withStyle(ChatFormatting.GRAY)));
        }
        WandFocus focus = stack.get(TUDataComponents.WAND_FOCUS.get());
        tooltip.accept(focus == null
                ? Component.translatable("tooltip.thaumicunity.wand.no_focus").withStyle(ChatFormatting.DARK_GRAY)
                : Component.translatable("tooltip.thaumicunity.wand.focus", focus.stack().getHoverName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (registries != null) {
            WandParts parts = Wands.parts(stack);
            Wands.cap(registries, parts.cap()).ifPresent(cap -> tooltip.accept(
                    Component.translatable("tooltip.thaumicunity.wand.cost", percent(cap)).withStyle(ChatFormatting.DARK_AQUA)));
            Wands.rod(registries, parts.rod()).flatMap(WandRod::affinity).ifPresent(affinity -> tooltip.accept(
                    Component.translatable("tooltip.thaumicunity.wand.affinity", Component.translatable(Aspect.translationKey(affinity)))
                            .withStyle(ChatFormatting.GOLD)));
        }
        tooltip.accept(Component.translatable("tooltip.thaumicunity.wand.hint").withStyle(ChatFormatting.GRAY));
    }

    private static String amount(int centivis) {
        return centivis % WandVis.SCALE == 0 ? Integer.toString(centivis / WandVis.SCALE)
                : String.format(Locale.ROOT, "%.1f", centivis / (double) WandVis.SCALE);
    }

    private static String percent(WandCap cap) {
        return Math.round(cap.cost() * 100) + "%";
    }
}
