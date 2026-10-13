package dev.thaumicunity.common.item;

import dev.thaumicunity.common.registry.TUSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** The research book. */
public class ThaumonomiconItem extends Item {
    public ThaumonomiconItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientBridge.get().openThaumonomicon();
        } else {
            level.playSound(null, player.blockPosition(), TUSounds.PAGE.get(), SoundSource.PLAYERS, 0.7f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }
}
