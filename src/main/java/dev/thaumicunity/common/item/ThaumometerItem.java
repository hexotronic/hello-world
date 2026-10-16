package dev.thaumicunity.common.item;

import dev.thaumicunity.common.research.ResearchManager;
import dev.thaumicunity.common.registry.TUSounds;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Thaumometer: scans the creature or block you look at, or the item in your off hand if you look at nothing.
 * Every new scan teaches aspects (aspect points) and knowledge (observations).
 */
public class ThaumometerItem extends Item {
    public static final double RANGE = 8.0;

    public ThaumometerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        Entity target = null;
        double best = Double.MAX_VALUE;
        for (Entity e : level.getEntities(player, new AABB(eye, stop).inflate(1.0), e -> e.isAlive() && e.isPickable())) {
            if (e.getBoundingBox().inflate(0.3).clip(eye, stop).isPresent() && e.distanceToSqr(eye) < best) {
                best = e.distanceToSqr(eye);
                target = e;
            }
        }
        boolean scanned;
        if (target != null) {
            scanned = ResearchManager.scanEntity(serverPlayer, target);
        } else if (block.getType() == HitResult.Type.BLOCK) {
            scanned = ResearchManager.scanBlock(serverPlayer, block.getBlockPos());
        } else {
            ItemStack other = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            scanned = !other.isEmpty() && ResearchManager.scanItem(serverPlayer, other);
        }
        level.playSound(null, player.blockPosition(), TUSounds.SCAN.get(), SoundSource.PLAYERS, 0.6f, scanned ? 1.2f : 0.8f);
        player.getCooldowns().addCooldown(player.getItemInHand(hand), 10);
        return InteractionResult.SUCCESS;
    }
}
