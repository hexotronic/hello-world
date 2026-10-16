package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.Aspect;
import dev.thaumicunity.common.research.ResearchCategory;
import dev.thaumicunity.common.research.ResearchEntry;
import dev.thaumicunity.common.spell.SpellComponent;
import dev.thaumicunity.common.wand.WandCap;
import dev.thaumicunity.common.wand.WandRod;
import dev.thaumicunity.api.spell.SpellPrimitive;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.NewDatapackRegistryEvent;
import net.neoforged.neoforge.registries.NewRegistryEvent;
import net.neoforged.neoforge.registries.RegistryBuilder;

/**
 * Registry keys owned by Thaumic Unity. Datapack registries load JSON from
 * {@code data/<namespace>/thaumicunity/<registry>/<id>.json} and are synced to clients on login.
 */
public final class TURegistries {
    public static final ResourceKey<Registry<Aspect>> ASPECT = ResourceKey.createRegistryKey(ThaumicUnity.id("aspect"));
    public static final ResourceKey<Registry<ResearchCategory>> RESEARCH_CATEGORY =
            ResourceKey.createRegistryKey(ThaumicUnity.id("research_category"));
    public static final ResourceKey<Registry<ResearchEntry>> RESEARCH = ResourceKey.createRegistryKey(ThaumicUnity.id("research"));
    public static final ResourceKey<Registry<SpellComponent>> SPELL_COMPONENT =
            ResourceKey.createRegistryKey(ThaumicUnity.id("spell_component"));
    public static final ResourceKey<Registry<WandRod>> WAND_ROD = ResourceKey.createRegistryKey(ThaumicUnity.id("wand_rod"));
    public static final ResourceKey<Registry<WandCap>> WAND_CAP = ResourceKey.createRegistryKey(ThaumicUnity.id("wand_cap"));

    /** Static registry mapping primitive ids used by spell component JSON to Java behaviour. */
    public static final ResourceKey<Registry<SpellPrimitive>> SPELL_PRIMITIVE_KEY =
            ResourceKey.createRegistryKey(ThaumicUnity.id("spell_primitive"));
    public static final Registry<SpellPrimitive> SPELL_PRIMITIVES = new RegistryBuilder<>(SPELL_PRIMITIVE_KEY).sync(true).create();

    private TURegistries() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(TURegistries::onNewRegistry);
        modBus.addListener(TURegistries::onNewDatapackRegistry);
    }

    private static void onNewRegistry(NewRegistryEvent event) {
        event.register(SPELL_PRIMITIVES);
    }

    private static void onNewDatapackRegistry(NewDatapackRegistryEvent event) {
        event.worldRegistry(ASPECT, Aspect.CODEC, Aspect.CODEC);
        event.worldRegistry(RESEARCH_CATEGORY, ResearchCategory.CODEC, ResearchCategory.CODEC);
        event.worldRegistry(RESEARCH, ResearchEntry.CODEC, ResearchEntry.CODEC);
        event.worldRegistry(SPELL_COMPONENT, SpellComponent.CODEC, SpellComponent.CODEC);
        event.worldRegistry(WAND_ROD, WandRod.CODEC, WandRod.CODEC);
        event.worldRegistry(WAND_CAP, WandCap.CODEC, WandCap.CODEC);
    }
}
