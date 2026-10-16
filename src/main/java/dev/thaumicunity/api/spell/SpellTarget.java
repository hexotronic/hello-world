package dev.thaumicunity.api.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a spell medium resolved: a position, and optionally the entity or block face that was hit. Effects decide
 * which of these they act on (an effect that only works on blocks ignores entity-only targets and vice versa).
 */
public record SpellTarget(Vec3 position, @Nullable Entity entity, @Nullable BlockPos block, @Nullable Direction face) {
    public static SpellTarget ofEntity(Entity entity) {
        return new SpellTarget(entity.position().add(0, entity.getBbHeight() * 0.5, 0), entity, null, null);
    }

    public static SpellTarget ofBlock(BlockPos pos, Direction face, Vec3 hit) {
        return new SpellTarget(hit, null, pos, face);
    }

    public static SpellTarget ofPosition(Vec3 position) {
        return new SpellTarget(position, null, null, null);
    }

    public boolean hasEntity() {
        return entity != null;
    }

    public boolean hasBlock() {
        return block != null;
    }

    /** Block in front of the hit face (where something would be placed), or the block at the position. */
    public BlockPos placementPos() {
        if (block != null && face != null) {
            return block.relative(face);
        }
        return BlockPos.containing(position);
    }
}
