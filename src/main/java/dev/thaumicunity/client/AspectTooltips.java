package dev.thaumicunity.client;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.config.TUClientConfig;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUTags;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.research.ScanTarget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;
import java.util.Map;

/**
 * Adds an item's aspects to its tooltip. Aspects of items the player has not scanned stay hidden (unless the
 * server config says otherwise); with the client option {@code aspectsNeedShift} they show while Shift is held or
 * revealing goggles are worn.
 */
public final class AspectTooltips {
    private static final int PER_LINE = 4;

    private AspectTooltips() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (player == null || stack.isEmpty()) {
            return;
        }
        AspectList aspects = ItemAspects.of(stack, true);
        if (aspects.isEmpty()) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean known = TUConfig.ALWAYS_SHOW_ASPECTS.get() || player.isCreative()
                || ResearchManager.knowledge(player).hasScanned(ScanTarget.scanKey(ScanTarget.Kind.ITEM, itemId));
        if (!known) {
            tooltip.add(Component.translatable("tooltip.thaumicunity.aspects.unknown").withStyle(ChatFormatting.DARK_PURPLE));
            return;
        }
        boolean goggles = player.getItemBySlot(EquipmentSlot.HEAD).is(TUTags.Items.REVEALING);
        if (TUClientConfig.ASPECTS_NEED_SHIFT.get() && !goggles && !Minecraft.getInstance().hasShiftDown()) {
            tooltip.add(Component.translatable("tooltip.thaumicunity.aspects.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        RegistryAccess access = player.level().registryAccess();
        MutableComponent line = null;
        int onLine = 0;
        for (Map.Entry<Identifier, Integer> entry : aspects.asMap().entrySet()) {
            if (line == null) {
                line = Component.literal(" ");
            }
            line.append(Aspect.displayName(entry.getKey(), Aspects.color(access, entry.getKey())))
                    .append(Component.literal(" " + entry.getValue() + "  ").withStyle(ChatFormatting.GRAY));
            if (++onLine == PER_LINE) {
                tooltip.add(line);
                line = null;
                onLine = 0;
            }
        }
        if (line != null) {
            tooltip.add(line);
        }
    }
}
