package dev.thaumicunity.common.block;

import dev.thaumicunity.common.blockentity.CrucibleBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Crucible. Fill with a water bucket, heat from below, throw items in. Sneak-use with an empty hand to pour it out
 * (the essentia becomes flux).
 */
public class CrucibleBlock extends TUEntityBlock<CrucibleBlockEntity> {
    private static final VoxelShape SHAPE = Shapes.join(Shapes.block(), box(2, 4, 2, 14, 16, 14), BooleanOp.ONLY_FIRST);

    public CrucibleBlock(Properties properties) {
        super(properties, TUBlockEntities.CRUCIBLE, CrucibleBlockEntity::new, CrucibleBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        CrucibleBlockEntity crucible = blockEntity(level, pos);
        if (crucible == null || !stack.is(Items.WATER_BUCKET)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (!level.isClientSide() && crucible.water() < CrucibleBlockEntity.MAX_WATER) {
            crucible.fillWater();
            if (!player.isCreative()) {
                player.setItemInHand(hand, new ItemStack(Items.BUCKET));
            }
            level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        CrucibleBlockEntity crucible = blockEntity(level, pos);
        if (crucible == null || !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            crucible.dump(server);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity, InsideBlockEffectApplier effects,
                                boolean intersects) {
        if (entity instanceof ItemEntity item && level instanceof ServerLevel server) {
            CrucibleBlockEntity crucible = blockEntity(level, pos);
            if (crucible != null && item.getY() < pos.getY() + 0.9) {
                crucible.onItemEntered(server, item);
            }
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        CrucibleBlockEntity crucible = blockEntity(level, pos);
        if (crucible != null && crucible.isBoiling()) {
            double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
            double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
            level.addParticle(ParticleTypes.BUBBLE_POP, x, pos.getY() + 0.85, z, 0, 0.02, 0);
            if (random.nextInt(8) == 0) {
                level.playLocalSound(pos, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, SoundSource.BLOCKS, 0.3f, 1.2f, false);
            }
        }
    }
}
