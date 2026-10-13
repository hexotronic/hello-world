package dev.thaumicunity.api.spell;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * State shared by every primitive taking part in one cast.
 */
public interface SpellContext {
    ServerLevel level();

    /** The caster; may be null for spells released by devices or runes whose owner is gone. */
    @Nullable
    LivingEntity caster();

    RandomSource random();

    /** Global power multiplier from the config times instrument bonuses. */
    double powerScale();

    /** False when the config or a protection mod forbids changing blocks. */
    boolean mayAlterWorld();

    /** Colour of the component currently resolving, for primitives that spawn their own visuals. */
    int color();

    /** Id of the component currently resolving. */
    Identifier componentId();
}
