package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.RunicMatrixBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TUTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Runic matrix. Use a casting instrument or Salis Mundus on it to begin an infusion. */
public class RunicMatrixBlock extends TUEntityBlock<RunicMatrixBlockEntity> {
    private static final VoxelShape SHAPE = box(3, 3, 3, 13, 13, 13);

    public RunicMatrixBlock(Properties properties) {
        super(properties, TUBlockEntities.RUNIC_MATRIX, RunicMatrixBlockEntity::new, RunicMatrixBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(TUTags.Items.CASTING_INSTRUMENTS) && !stack.is(TUItems.SALIS_MUNDUS.get())) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        RunicMatrixBlockEntity matrix = blockEntity(level, pos);
        if (matrix != null && level instanceof ServerLevel server && player instanceof ServerPlayer serverPlayer) {
            matrix.tryStart(server, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        RunicMatrixBlockEntity matrix = blockEntity(level, pos);
        if (matrix != null && matrix.isActive()) {
            for (int i = 0; i < 3; i++) {
                level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 2,
                        pos.getY() + 0.5 + random.nextDouble(), pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 2,
                        (random.nextDouble() - 0.5) * 0.5, -0.5, (random.nextDouble() - 0.5) * 0.5);
            }
        } else if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.9, pos.getZ() + 0.5,
                    (random.nextDouble() - 0.5), 0.5, (random.nextDouble() - 0.5));
        }
    }
}
