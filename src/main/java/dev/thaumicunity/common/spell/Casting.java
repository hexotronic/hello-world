package dev.thaumicunity.common.spell;

import dev.thaumicunity.api.event.SpellCastEvent;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.item.FocusItem;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import org.jspecify.annotations.Nullable;

/**
 * Steps shared by every casting instrument (gauntlets, wands): compiling the focus's spell, firing
 * {@link SpellCastEvent}, and releasing a paid spell with its warp, flux, sound and cooldown. Instruments only differ
 * in where the vis comes from (docs/DESIGN_DECISIONS.md, D4).
 */
public final class Casting {
    private Casting() {
    }

    /**
     * The spell set into {@code focus}, compiled with the focus's limits, or null (after telling the player why) when
     * there is none, it is invalid, or a listener cancelled the cast.
     */
    public static @Nullable CompiledSpell prepare(ServerLevel level, Player player, ItemStack focus, String noFocusMessage) {
        if (!(focus.getItem() instanceof FocusItem focusItem) || !focus.has(TUDataComponents.SPELL.get())) {
            player.sendOverlayMessage(Component.translatable(noFocusMessage));
            return null;
        }
        SpellNode node = focus.get(TUDataComponents.SPELL.get());
        CompiledSpell spell = SpellLibrary.get(level.registryAccess()).compile(node, focusItem.limits());
        if (!spell.isValid()) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.caster.invalid", spell.errors().get(0))
                    .withStyle(ChatFormatting.RED));
            return null;
        }
        if (NeoForge.EVENT_BUS.post(new SpellCastEvent(player, spell)).isCanceled()) {
            return null;
        }
        return spell;
    }

    /** Runs a spell that has been paid for: effects, warp, flux where it was cast, sound and the instrument's cooldown. */
    public static void release(ServerLevel level, ServerPlayer player, ItemStack instrument, CompiledSpell spell, double power) {
        BlockPos at = player.blockPosition();
        SpellRunner.cast(level, player, spell, power);
        if (spell.warp() > 0 && TUConfig.WARP_ENABLED.get()) {
            player.getData(TUAttachments.KNOWLEDGE).addWarp(0, 0, spell.warp());
            player.syncData(TUAttachments.KNOWLEDGE);
        }
        AuraManager.addFlux(level, at, spell.cost().total() * 0.02);
        level.playSound(null, at, TUSounds.CAST.get(), SoundSource.PLAYERS, 0.7f, 0.9f + level.getRandom().nextFloat() * 0.2f);
        player.getCooldowns().addCooldown(instrument, spell.cooldown());
    }

    /** A cast that could not be paid for. */
    public static void fizzle(ServerLevel level, Player player, ItemStack instrument, String message) {
        level.playSound(null, player.blockPosition(), TUSounds.FIZZLE.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
        player.sendOverlayMessage(Component.translatable(message));
        player.getCooldowns().addCooldown(instrument, 10);
    }
}
