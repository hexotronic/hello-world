package dev.thaumicunity.common.item;

import net.minecraft.core.BlockPos;

/**
 * Calls from common code into the client (opening screens) without class-loading client code on a dedicated
 * server. The client entry point replaces {@link #INSTANCE} with a real implementation.
 */
public interface ClientBridge {
    void openThaumonomicon();

    void showScanResult(String key, BlockPos pos);

    ClientBridge NOOP = new ClientBridge() {
        @Override
        public void openThaumonomicon() {
        }

        @Override
        public void showScanResult(String key, BlockPos pos) {
        }
    };

    /** Replaced by the client entry point. */
    final class Holder {
        public static volatile ClientBridge instance = NOOP;

        private Holder() {
        }
    }

    static ClientBridge get() {
        return Holder.instance;
    }
}
