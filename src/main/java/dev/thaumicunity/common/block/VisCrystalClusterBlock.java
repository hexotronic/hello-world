package dev.thaumicunity.common.block;

import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.AmethystClusterBlock;

/** A cluster of one primal's vis crystals growing from a cave wall; breaks into vis crystals of that primal. */
public class VisCrystalClusterBlock extends AmethystClusterBlock {
    private final Identifier primal;

    public VisCrystalClusterBlock(Identifier primal, Properties properties) {
        super(7.0f, 3.0f, properties);
        this.primal = primal;
    }

    public Identifier primal() {
        return primal;
    }
}
