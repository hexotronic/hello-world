package dev.thaumicunity.common.block;

import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.blockentity.EssentiaBufferBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Essentia buffer: right-click with an empty hand to see its contents. */
public class EssentiaBufferBlock extends TUEntityBlock<EssentiaBufferBlockEntity> {
    public EssentiaBufferBlock(Properties properties) {
        super(properties, TUBlockEntities.ESSENTIA_BUFFER, EssentiaBufferBlockEntity::new, EssentiaBufferBlockEntity::serverTick);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        EssentiaBufferBlockEntity buffer = blockEntity(level, pos);
        if (buffer == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            player.sendOverlayMessage(describe(buffer.contents()));
        }
        return InteractionResult.SUCCESS;
    }

    /** "Ignis 4, Aqua 2", or "empty". */
    public static Component describe(AspectList contents) {
        if (contents.isEmpty()) {
            return Component.translatable("message.thaumicunity.tube.empty");
        }
        MutableComponent line = Component.empty();
        boolean first = true;
        for (var e : contents.asMap().entrySet()) {
            if (!first) {
                line.append(", ");
            }
            line.append(Component.translatable(Aspect.translationKey(e.getKey()))).append(" " + e.getValue());
            first = false;
        }
        return line;
    }
}
