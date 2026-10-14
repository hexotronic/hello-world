package dev.thaumicunity.common.essentia;

import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A block entity that takes part in essentia transport through tubes (docs/ARCHITECTURE.md, "Essentia").
 *
 * <p>Transport follows suction: every node says how strongly it pulls essentia in through each side, and essentia
 * moves one point at a time from a node to a neighbour pulling harder than the node holds on to it. Tubes take the
 * strongest suction of their neighbours minus one, so essentia finds its way along a line of tubes to the device
 * pulling hardest: an unlabelled jar pulls with {@value #JAR}, a labelled jar with {@value #LABELLED_JAR}, an
 * alchemical furnace not at all.</p>
 */
public interface EssentiaNode {
    int JAR = 32;
    int LABELLED_JAR = 64;

    /** Whether tubes connect to this side. */
    boolean connectsTo(Direction side);

    /** How strongly this node pulls essentia in through {@code side}; 0 when it takes none there. */
    int suction(Direction side);

    /** The aspect this node wants through {@code side}, or null for any. */
    @Nullable Identifier suctionAspect(Direction side);

    /** How strongly this node holds on to what it offers: a neighbour must pull harder to take it. */
    default int retention(Direction side) {
        return suction(side);
    }

    /** The aspect this node offers through {@code side}, or null when it offers nothing there. */
    @Nullable Identifier offered(Direction side);

    /** Removes up to {@code amount} of {@code aspect} through {@code side}; returns how much was removed. */
    int take(Identifier aspect, int amount, Direction side);

    /** Adds up to {@code amount} of {@code aspect} through {@code side}; returns how much was accepted. */
    int give(Identifier aspect, int amount, Direction side);
}
