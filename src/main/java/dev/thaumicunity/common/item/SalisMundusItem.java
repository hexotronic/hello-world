package dev.thaumicunity.common.item;

import dev.thaumicunity.common.registry.TUBlocks;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TUSounds;
import dev.thaumicunity.common.spell.SpellFx;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Salis Mundus, the "world salt": sprinkled on ordinary blocks it awakens them — a bookshelf yields a
 * Thaumonomicon, a crafting table becomes an arcane workbench, a cauldron becomes a crucible, a lectern becomes a
 * research table. Wands can do the same ({@link #awaken}).
 */
public class SalisMundusItem extends Item {
    public SalisMundusItem(Properties properties) {
        super(properties);
    }

    /** True for the blocks Salis Mundus (or a wand) can awaken. */
    public static boolean canAwaken(BlockState state) {
        return state.is(Blocks.BOOKSHELF) || state.is(Blocks.CRAFTING_TABLE) || state.is(Blocks.CAULDRON)
                || state.is(Blocks.WATER_CAULDRON) || state.is(Blocks.LECTERN);
    }

    /** Awakens the block at {@code pos}; returns false if it is not one that can be awakened. */
    public static boolean awaken(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        Block converted = null;
        ItemStack drop = ItemStack.EMPTY;
        if (state.is(Blocks.BOOKSHELF)) {
            drop = new ItemStack(TUItems.THAUMONOMICON.get());
        } else if (state.is(Blocks.CRAFTING_TABLE)) {
            converted = TUBlocks.ARCANE_WORKBENCH.get();
        } else if (state.is(Blocks.CAULDRON) || state.is(Blocks.WATER_CAULDRON)) {
            converted = TUBlocks.CRUCIBLE.get();
        } else if (state.is(Blocks.LECTERN)) {
            converted = TUBlocks.RESEARCH_TABLE.get();
        } else {
            return false;
        }
        if (converted != null) {
            level.setBlockAndUpdate(pos, converted.defaultBlockState());
        } else {
            level.removeBlock(pos, false);
            Block.popResource(level, pos, drop);
        }
        level.sendParticles(SpellFx.spark(0xE8D9FF), pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, 40, 0.4, 0.4, 0.4, 0.05);
        level.playSound(null, pos, TUSounds.CRAFT_SUCCESS.get(), SoundSource.BLOCKS, 1.0f, 1.0f);
        return true;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!canAwaken(level.getBlockState(context.getClickedPos()))) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server && awaken(server, context.getClickedPos())) {
            Player player = context.getPlayer();
            if (player == null || !player.isCreative()) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
