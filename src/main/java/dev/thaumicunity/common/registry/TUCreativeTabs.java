package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.wand.Wands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Thaumic Unity creative tab: every registered item in registration order, then every kind of wand. */
public final class TUCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thaumicunity"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> TUItems.THAUMONOMICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> {
                TUItems.ITEMS.getEntries().forEach(item -> {
                    if (item.get() != TUItems.WAND.get()) {
                        output.accept(item.get());
                    }
                });
                // Every rod and cap combination the loaded data defines.
                parameters.holders().lookup(TURegistries.WAND_ROD).ifPresent(rods -> parameters.holders().lookup(TURegistries.WAND_CAP)
                        .ifPresent(caps -> rods.listElementIds().forEach(rod -> caps.listElementIds().forEach(cap ->
                                output.accept(Wands.create(parameters.holders(), rod.identifier(), cap.identifier()))))));
            })
            .build());

    private TUCreativeTabs() {
    }

    public static void init(IEventBus modBus) {
        TABS.register(modBus);
    }
}
