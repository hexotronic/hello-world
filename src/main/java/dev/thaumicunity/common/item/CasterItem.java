package dev.thaumicunity.common.item;

import dev.thaumicunity.api.event.SpellCastEvent;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.spell.SpellLibrary;
import dev.thaumicunity.common.spell.SpellRunner;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;

import java.util.function.Consumer;

/**
 * Caster's gauntlet (docs/DESIGN_DECISIONS.md, D4). Casts the focus held in the other hand, paying the spell's primal
 * vis from the aura around the caster (weighted by the place's primal attunement). Higher tier gauntlets cast with
 * more power and draw vis more efficiently.
 */
public class CasterItem extends Item {
    private final double power;
    private final double efficiency;

    public CasterItem(double power, double efficiency, Properties properties) {
        super(properties);
        this.power = power;
        this.efficiency = efficiency;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack caster = player.getItemInHand(hand);
        ItemStack focus = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (!(focus.getItem() instanceof FocusItem focusItem) || !focus.has(TUDataComponents.SPELL.get())) {
            if (!level.isClientSide()) {
                player.sendOverlayMessage(Component.translatable("message.thaumicunity.caster.no_focus"));
            }
            return InteractionResult.FAIL;
        }
        if (!TUConfig.CASTERS_ENABLED.get()) {
            return InteractionResult.FAIL;
        }
        if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        SpellNode node = focus.get(TUDataComponents.SPELL.get());
        CompiledSpell spell = SpellLibrary.get(server.registryAccess()).compile(node, focusItem.limits());
        if (!spell.isValid()) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.caster.invalid", spell.errors().get(0))
                    .withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }
        if (NeoForge.EVENT_BUS.post(new SpellCastEvent(player, spell)).isCanceled()) {
            return InteractionResult.FAIL;
        }
        BlockPos at = player.blockPosition();
        if (!player.isCreative() && !payVis(server, at, spell)) {
            server.playSound(null, at, TUSounds.FIZZLE.get(), SoundSource.PLAYERS, 0.6f, 0.8f);
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.caster.no_vis"));
            player.getCooldowns().addCooldown(caster, 10);
            return InteractionResult.FAIL;
        }
        SpellRunner.cast(server, player, spell, power);
        if (spell.warp() > 0 && TUConfig.WARP_ENABLED.get()) {
            serverPlayer.getData(TUAttachments.KNOWLEDGE).addWarp(0, 0, spell.warp());
            serverPlayer.syncData(TUAttachments.KNOWLEDGE);
        }
        AuraManager.addFlux(server, at, spell.cost().total() * 0.02);
        server.playSound(null, at, TUSounds.CAST.get(), SoundSource.PLAYERS, 0.7f, 0.9f + server.getRandom().nextFloat() * 0.2f);
        player.getCooldowns().addCooldown(caster, spell.cooldown());
        return InteractionResult.SUCCESS;
    }

    /** Pays a spell's primal cost from the aura; all-or-nothing. */
    private boolean payVis(ServerLevel level, BlockPos pos, CompiledSpell spell) {
        if (TUConfig.AURA_MODE.get() == TUConfig.AuraModeOption.NODES) {
            return false;
        }
        double[] cost = new double[6];
        for (int i = 0; i < 6; i++) {
            cost[i] = spell.cost().get(Aspects.PRIMALS.get(i).toString()) * efficiency;
        }
        var attunement = AuraManager.attunementAt(level, pos);
        double total = 0;
        for (int i = 0; i < 6; i++) {
            total += cost[i] * attunement.costFactor(i);
        }
        if (AuraManager.auraAt(level, pos).vis() < total) {
            return false;
        }
        AuraManager.drainVis(level, pos, total);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.accept(Component.translatable("tooltip.thaumicunity.caster.power", Math.round(power * 100))
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltip.accept(Component.translatable("tooltip.thaumicunity.caster.efficiency", Math.round((1 - efficiency) * 100))
                .withStyle(ChatFormatting.DARK_AQUA));
        tooltip.accept(Component.translatable("tooltip.thaumicunity.caster.hint").withStyle(ChatFormatting.GRAY));
    }
}
