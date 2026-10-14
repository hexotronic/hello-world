package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.research.InsightNote;
import dev.thaumicunity.common.spell.SpellNodes;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Item data components. */
public final class TUDataComponents {
    private static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ThaumicUnity.MOD_ID);

    /** The spell graph imprinted on a focus. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SpellNode>> SPELL = COMPONENTS.registerComponentType(
            "spell", b -> b.persistent(SpellNodes.CODEC).networkSynchronized(SpellNodes.STREAM_CODEC).cacheEncoding());

    /** Essentia held by phials, jars (as items) and other containers. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AspectList>> ESSENTIA = COMPONENTS.registerComponentType(
            "essentia", b -> b.persistent(AspectList.CODEC).networkSynchronized(AspectList.STREAM_CODEC));

    /** An insight puzzle in progress, carried on research notes. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<InsightNote>> INSIGHT = COMPONENTS.registerComponentType(
            "insight", b -> b.persistent(InsightNote.CODEC).networkSynchronized(InsightNote.STREAM_CODEC));

    private TUDataComponents() {
    }

    public static void init(IEventBus modBus) {
        COMPONENTS.register(modBus);
    }
}
