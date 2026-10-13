package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The Thaumic Unity creative tab, listing every registered item in registration order. */
public final class TUCreativeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.thaumicunity"))
            .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
            .icon(() -> TUItems.THAUMONOMICON.get().getDefaultInstance())
            .displayItems((parameters, output) -> TUItems.ITEMS.getEntries().forEach(item -> output.accept(item.get())))
            .build());

    private TUCreativeTabs() {
    }

    public static void init(IEventBus modBus) {
        TABS.register(modBus);
    }
}
