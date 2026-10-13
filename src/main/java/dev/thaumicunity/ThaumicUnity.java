package dev.thaumicunity;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/// Entry point of Thaumic Unity.
@Mod(ThaumicUnity.MOD_ID)
public final class ThaumicUnity {
    public static final String MOD_ID = "thaumicunity";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ThaumicUnity(IEventBus modBus, ModContainer container) {
        LOGGER.info("Thaumic Unity is gathering its notes");
    }

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
