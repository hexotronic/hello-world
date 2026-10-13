package dev.thaumicunity.common.item;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.registry.TUDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Glass phial: carries ten essentia of one aspect between jars. */
public class PhialItem extends Item {
    public PhialItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        AspectList essentia = stack.getOrDefault(TUDataComponents.ESSENTIA.get(), AspectList.EMPTY);
        if (essentia.isEmpty()) {
            tooltip.accept(Component.translatable("tooltip.thaumicunity.phial.empty").withStyle(ChatFormatting.GRAY));
        } else {
            essentia.forEach((aspect, amount) -> tooltip.accept(Component.literal(amount + " × ")
                    .append(Component.translatable(Aspect.translationKey(aspect)))));
        }
    }
}
