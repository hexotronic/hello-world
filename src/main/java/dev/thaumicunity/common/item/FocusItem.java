package dev.thaumicunity.common.item;

import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.spell.SpellComponent;
import dev.thaumicunity.core.spell.SpellLimits;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.function.Consumer;

/**
 * A focus holds one spell graph ({@link TUDataComponents#SPELL}). Its tier sets the structural limits of the spell
 * (complexity, nesting, effects per medium).
 */
public class FocusItem extends Item {
    private final int tier;

    public FocusItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public int tier() {
        return tier;
    }

    public SpellLimits limits() {
        return switch (tier) {
            case 1 -> SpellLimits.TIER_1;
            case 2 -> SpellLimits.TIER_2;
            default -> SpellLimits.TIER_3;
        };
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(TUDataComponents.SPELL.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        SpellNode spell = stack.get(TUDataComponents.SPELL.get());
        if (spell == null) {
            tooltip.accept(Component.translatable("tooltip.thaumicunity.focus.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        describe(spell, 0, tooltip);
    }

    private static void describe(SpellNode node, int depth, Consumer<Component> tooltip) {
        String indent = "  ".repeat(depth);
        tooltip.accept(Component.literal(indent).append(name(node.medium())).withStyle(ChatFormatting.LIGHT_PURPLE));
        for (String effect : node.effects()) {
            tooltip.accept(Component.literal(indent + " + ").append(name(effect)).withStyle(ChatFormatting.AQUA));
        }
        for (String modifier : node.modifiers()) {
            tooltip.accept(Component.literal(indent + " ~ ").append(name(modifier)).withStyle(ChatFormatting.GOLD));
        }
        for (SpellNode child : node.children()) {
            describe(child, depth + 1, tooltip);
        }
    }

    private static Component name(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? Component.literal(id) : Component.translatable(SpellComponent.translationKey(parsed));
    }
}
