package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.AlchemicalFurnaceBlockEntity;
import dev.thaumicunity.common.menu.AlchemicalFurnaceMenu;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Alchemical furnace: distils items into essentia for a jar placed on top. */
public class AlchemicalFurnaceBlock extends MenuBlock<AlchemicalFurnaceBlockEntity> {
    public AlchemicalFurnaceBlock(Properties properties) {
        super(properties, TUBlockEntities.ALCHEMICAL_FURNACE, AlchemicalFurnaceBlockEntity::new, AlchemicalFurnaceBlockEntity::serverTick);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Player player, AlchemicalFurnaceBlockEntity blockEntity) {
        return new AlchemicalFurnaceMenu(id, player.getInventory(), blockEntity);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, pos.getX() + 0.3 + random.nextDouble() * 0.4, pos.getY() + 0.2,
                    pos.getZ() + 0.3 + random.nextDouble() * 0.4, 0, 0.01, 0);
        }
    }
}
