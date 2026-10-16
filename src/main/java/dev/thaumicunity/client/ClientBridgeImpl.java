package dev.thaumicunity.client;

import dev.thaumicunity.client.screen.ThaumonomiconScreen;
import dev.thaumicunity.common.item.ClientBridge;
import net.minecraft.client.Minecraft;

/** Client implementation of {@link ClientBridge}, installed by {@link ThaumicUnityClient}. */
final class ClientBridgeImpl implements ClientBridge {
    @Override
    public void openThaumonomicon() {
        Minecraft.getInstance().setScreenAndShow(new ThaumonomiconScreen());
    }
}
