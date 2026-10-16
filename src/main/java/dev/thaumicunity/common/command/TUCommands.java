package dev.thaumicunity.common.command;

import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.JsonOps;
import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.aura.AuraManager;
import dev.thaumicunity.common.aura.ChunkAura;
import dev.thaumicunity.common.item.FocusItem;
import dev.thaumicunity.common.registry.TUAttachments;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.research.PlayerKnowledge;
import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.spell.SpellLibrary;
import dev.thaumicunity.common.spell.SpellNodes;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * {@code /thaumicunity} (alias {@code /tu}): inspection and admin tools.
 * <pre>
 * /tu aspects                               aspects of the held item
 * /tu aura                                  vis, flux and base of the current chunk
 * /tu aura set &lt;vis&gt; &lt;flux&gt;                     (op)
 * /tu research grant|revoke &lt;player&gt; &lt;id&gt;|all      (op)
 * /tu research advance &lt;id&gt;                 try to complete the current stage
 * /tu points &lt;player&gt; &lt;aspect&gt; &lt;amount&gt;           (op) give aspect points
 * /tu warp &lt;player&gt; &lt;amount&gt;                   (op) add temporary warp (negative removes)
 * /tu spell &lt;json&gt;                            (op) imprint a spell graph on the held focus
 * </pre>
 */
public final class TUCommands {
    private TUCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        var root = Commands.literal("thaumicunity")
                .then(Commands.literal("aspects").executes(TUCommands::aspects))
                .then(Commands.literal("aura").executes(TUCommands::aura)
                        .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("vis", IntegerArgumentType.integer(0))
                                        .then(Commands.argument("flux", IntegerArgumentType.integer(0)).executes(TUCommands::auraSet)))))
                .then(Commands.literal("research")
                        .then(Commands.literal("advance").then(Commands.argument("id", IdentifierArgument.id())
                                .executes(c -> {
                                    ResearchManager.tryAdvance(c.getSource().getPlayerOrException(), IdentifierArgument.getId(c, "id"));
                                    return 1;
                                })))
                        .then(Commands.literal("grant").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("all").executes(c -> setAll(c, true)))
                                        .then(Commands.argument("id", IdentifierArgument.id()).executes(c -> set(c, true)))))
                        .then(Commands.literal("revoke").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.literal("all").executes(c -> setAll(c, false)))
                                        .then(Commands.argument("id", IdentifierArgument.id()).executes(c -> set(c, false))))))
                .then(Commands.literal("points").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("aspect", IdentifierArgument.id())
                                        .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(TUCommands::points)))))
                .then(Commands.literal("warp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", IntegerArgumentType.integer()).executes(TUCommands::warp))))
                .then(Commands.literal("spell").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("json", StringArgumentType.greedyString()).executes(TUCommands::spell)));
        var registered = dispatcher.register(root);
        dispatcher.register(Commands.literal("tu").redirect(registered));
    }

    private static int aspects(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        AspectList aspects = ItemAspects.of(stack, false);
        if (aspects.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.thaumicunity.aspects.none", stack.getHoverName()), false);
            return 0;
        }
        MutableComponent line = Component.empty().append(stack.getHoverName()).append(": ");
        aspects.forEach((aspect, amount) -> line.append(Aspect.displayName(aspect, Aspects.color(player.level().registryAccess(), aspect)))
                .append(" " + amount + "  "));
        c.getSource().sendSuccess(() -> line, false);
        return aspects.size();
    }

    private static int aura(CommandContext<CommandSourceStack> c) {
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        ChunkAura aura = AuraManager.auraAt(c.getSource().getLevel(), pos);
        c.getSource().sendSuccess(() -> Component.translatable("command.thaumicunity.aura",
                Math.round(aura.vis()), Math.round(aura.flux()), Math.round(aura.base())), false);
        return (int) aura.vis();
    }

    private static int auraSet(CommandContext<CommandSourceStack> c) {
        BlockPos pos = BlockPos.containing(c.getSource().getPosition());
        ChunkAura aura = AuraManager.auraAt(c.getSource().getLevel(), pos);
        aura.setVis(IntegerArgumentType.getInteger(c, "vis"));
        aura.setFlux(IntegerArgumentType.getInteger(c, "flux"));
        c.getSource().getLevel().getChunkAt(pos).markUnsaved();
        return aura(c);
    }

    private static int set(CommandContext<CommandSourceStack> c, boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Identifier id = IdentifierArgument.getId(c, "id");
        if (ResearchManager.entry(player.level().registryAccess(), id).isEmpty()) {
            c.getSource().sendFailure(Component.translatable("command.thaumicunity.research.unknown", id.toString()));
            return 0;
        }
        ResearchManager.setComplete(player, id, grant);
        c.getSource().sendSuccess(() -> Component.translatable(grant ? "command.thaumicunity.research.granted"
                : "command.thaumicunity.research.revoked", id.toString(), player.getName()), true);
        return 1;
    }

    private static int setAll(CommandContext<CommandSourceStack> c, boolean grant) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        var ids = ResearchManager.allIds(player.level().registryAccess());
        ids.forEach(id -> ResearchManager.setComplete(player, id, grant));
        c.getSource().sendSuccess(() -> Component.translatable(grant ? "command.thaumicunity.research.granted_all"
                : "command.thaumicunity.research.revoked_all", ids.size(), player.getName()), true);
        return ids.size();
    }

    private static int points(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        Identifier aspect = IdentifierArgument.getId(c, "aspect");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        PlayerKnowledge knowledge = player.getData(TUAttachments.KNOWLEDGE);
        knowledge.addAspectPoints(aspect, amount);
        player.syncData(TUAttachments.KNOWLEDGE);
        c.getSource().sendSuccess(() -> Component.translatable("command.thaumicunity.points", amount, aspect.toString(), player.getName()), true);
        return knowledge.aspectPoints(aspect);
    }

    private static int warp(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        int amount = IntegerArgumentType.getInteger(c, "amount");
        PlayerKnowledge knowledge = player.getData(TUAttachments.KNOWLEDGE);
        knowledge.addWarp(0, 0, amount);
        player.syncData(TUAttachments.KNOWLEDGE);
        c.getSource().sendSuccess(() -> Component.translatable("command.thaumicunity.warp", player.getName(), knowledge.totalWarp()), true);
        return knowledge.totalWarp();
    }

    private static int spell(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof FocusItem focus)) {
            c.getSource().sendFailure(Component.translatable("command.thaumicunity.spell.no_focus"));
            return 0;
        }
        SpellNode node;
        try {
            node = SpellNodes.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(StringArgumentType.getString(c, "json")))
                    .getOrThrow(IllegalArgumentException::new);
        } catch (RuntimeException e) {
            c.getSource().sendFailure(Component.literal(e.getMessage()).withStyle(ChatFormatting.RED));
            return 0;
        }
        CompiledSpell compiled = SpellLibrary.get(player.level().registryAccess()).compile(node, focus.limits());
        if (!compiled.isValid()) {
            compiled.errors().forEach(e -> c.getSource().sendFailure(Component.literal(e)));
            return 0;
        }
        held.set(TUDataComponents.SPELL.get(), node);
        c.getSource().sendSuccess(() -> Component.translatable("command.thaumicunity.spell.set", compiled.complexity(),
                compiled.cost().toString(), compiled.cooldown()), false);
        return 1;
    }
}
