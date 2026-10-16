package dev.thaumicunity.common.recipe;

import net.minecraft.resources.Identifier;

import java.util.Optional;

/** A recipe that only works for players who have completed (or reached) a research entry. */
public interface ResearchGated {
    Optional<Identifier> research();
}
