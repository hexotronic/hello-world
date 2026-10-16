package dev.thaumicunity.common.block;

import dev.thaumicunity.common.util.TUUtil;
import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.blockentity.WardedJarBlockEntity;
import dev.thaumicunity.common.registry.TUBlockEntities;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Warded jar. Phials fill from or pour into it; paper labels it with its current aspect (sneak with an empty hand to
 * clear the label); an empty hand shows the contents.
 */
public class WardedJarBlock extends TUEntityBlock<WardedJarBlockEntity> {
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 12, 13);
    public static final int PHIAL_AMOUNT = 10;

    public WardedJarBlock(Properties properties) {
        super(properties, TUBlockEntities.WARDED_JAR, WardedJarBlockEntity::new, WardedJarBlockEntity::serverTick);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        WardedJarBlockEntity jar = blockEntity(level, pos);
        if (jar == null) {
            return InteractionResult.PASS;
        }
        if (stack.is(TUItems.PHIAL.get())) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            AspectList held = stack.getOrDefault(TUDataComponents.ESSENTIA.get(), AspectList.EMPTY);
            if (held.isEmpty()) {
                Identifier aspect = jar.aspect();
                if (aspect != null && jar.amount() >= PHIAL_AMOUNT) {
                    jar.take(aspect, PHIAL_AMOUNT);
                    ItemStack filled = new ItemStack(TUItems.PHIAL.get());
                    filled.set(TUDataComponents.ESSENTIA.get(), AspectList.of(aspect, PHIAL_AMOUNT));
                    giveOrDrop(player, stack, filled);
                    level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.8f, 1.0f);
                }
            } else {
                Identifier aspect = held.dominant();
                if (jar.canAccept(aspect) && WardedJarBlockEntity.CAPACITY - jar.amount() >= held.get(aspect)) {
                    jar.add(aspect, held.get(aspect));
                    giveOrDrop(player, stack, new ItemStack(TUItems.PHIAL.get()));
                    level.playSound(null, pos, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.8f, 1.0f);
                }
            }
            return InteractionResult.SUCCESS;
        }
        if (stack.is(Items.PAPER) && jar.aspect() != null && jar.filter() == null) {
            if (!level.isClientSide()) {
                jar.setFilter(jar.aspect());
                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        WardedJarBlockEntity jar = blockEntity(level, pos);
        if (jar == null || level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isShiftKeyDown() && jar.filter() != null) {
            jar.setFilter(null);
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.jar.label_removed"));
            return InteractionResult.SUCCESS;
        }
        Identifier aspect = jar.aspect();
        if (aspect == null || jar.amount() == 0) {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.jar.empty"));
        } else {
            player.sendOverlayMessage(Component.translatable("message.thaumicunity.jar.contents", jar.amount(),
                    Aspect.displayName(aspect, Aspects.color(level.registryAccess(), aspect))));
        }
        return InteractionResult.SUCCESS;
    }

    private static void giveOrDrop(Player player, ItemStack used, ItemStack result) {
        if (!player.isCreative()) {
            used.shrink(1);
        }
        TUUtil.giveOrDrop(player, result);
    }
}
