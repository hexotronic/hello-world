package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.menu.AlchemicalFurnaceMenu;
import dev.thaumicunity.common.menu.ArcaneWorkbenchMenu;
import dev.thaumicunity.common.menu.ResearchTableMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Menu types (each receives the block position as extra data). */
public final class TUMenus {
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ThaumicUnity.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<ArcaneWorkbenchMenu>> ARCANE_WORKBENCH =
            MENUS.register("arcane_workbench", () -> IMenuTypeExtension.create(ArcaneWorkbenchMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<ResearchTableMenu>> RESEARCH_TABLE =
            MENUS.register("research_table", () -> IMenuTypeExtension.create(ResearchTableMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AlchemicalFurnaceMenu>> ALCHEMICAL_FURNACE =
            MENUS.register("alchemical_furnace", () -> IMenuTypeExtension.create(AlchemicalFurnaceMenu::new));

    private TUMenus() {
    }

    public static void init(IEventBus modBus) {
        MENUS.register(modBus);
    }
}
