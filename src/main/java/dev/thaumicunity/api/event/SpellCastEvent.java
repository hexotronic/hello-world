package dev.thaumicunity.api.event;

import dev.thaumicunity.core.spell.CompiledSpell;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** Fired before a spell is cast; cancel it to prevent the cast (the vis is not spent). */
public class SpellCastEvent extends Event implements ICancellableEvent {
    private final LivingEntity caster;
    private final CompiledSpell spell;

    public SpellCastEvent(LivingEntity caster, CompiledSpell spell) {
        this.caster = caster;
        this.spell = spell;
    }

    public LivingEntity getCaster() {
        return caster;
    }

    public CompiledSpell getSpell() {
        return spell;
    }
}
