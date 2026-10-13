package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Sound events. sounds.json maps them onto vanilla sound files until original recordings are added (each entry can
 * be retargeted by a resource pack without code changes).
 */
public final class TUSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = register("item.thaumometer.scan");
    public static final DeferredHolder<SoundEvent, SoundEvent> RESEARCH = register("research.progress");
    public static final DeferredHolder<SoundEvent, SoundEvent> PAGE = register("book.page");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE = register("block.crucible.bubble");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAFT_SUCCESS = register("craft.success");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRAFT_FAIL = register("craft.fail");
    public static final DeferredHolder<SoundEvent, SoundEvent> INFUSION = register("block.runic_matrix.infuse");
    public static final DeferredHolder<SoundEvent, SoundEvent> CAST = register("item.caster.cast");
    public static final DeferredHolder<SoundEvent, SoundEvent> FIZZLE = register("item.caster.fizzle");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZAP = register("misc.zap");

    private TUSounds() {
    }

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ThaumicUnity.id(name)));
    }

    public static void init(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
