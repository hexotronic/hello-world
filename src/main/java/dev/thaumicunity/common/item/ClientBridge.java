package dev.thaumicunity.common.item;

/**
 * Calls from common code into the client (opening screens) without class-loading client code on a dedicated
 * server. The client entry point replaces {@link Holder#instance} with a real implementation.
 */
public interface ClientBridge {
    void openThaumonomicon();

    ClientBridge NOOP = () -> {
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
