package dev.thaumicunity.common.item;

import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.research.InsightNote;
import dev.thaumicunity.common.research.ResearchEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/** Research notes: an insight puzzle worked on at the research table. */
public class ResearchNotesItem extends Item {
    public ResearchNotesItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        InsightNote note = stack.get(TUDataComponents.INSIGHT.get());
        return note != null && note.solved();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        InsightNote note = stack.get(TUDataComponents.INSIGHT.get());
        if (note == null) {
            return;
        }
        tooltip.accept(Component.translatable(ResearchEntry.titleKey(note.research())).withStyle(ChatFormatting.GOLD));
        tooltip.accept(Component.translatable(note.solved() ? "tooltip.thaumicunity.notes.solved" : "tooltip.thaumicunity.notes.unsolved")
                .withStyle(note.solved() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
    }
}
