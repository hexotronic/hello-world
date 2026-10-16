package dev.thaumicunity.client;

import dev.thaumicunity.common.equipment.EquipmentEffects;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Shows how much vis a piece of robe or the goggles spare (data map thaumicunity:vis_discount). */
public final class EquipmentTooltips {
    private EquipmentTooltips() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        int percent = EquipmentEffects.discountOf(event.getItemStack());
        if (percent > 0) {
            event.getToolTip().add(Component.translatable("tooltip.thaumicunity.vis_discount", percent)
                    .withStyle(ChatFormatting.DARK_PURPLE));
        }
    }
}
