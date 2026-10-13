package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Tags owned by Thaumic Unity (behaviour tags; materials use the common {@code c:} namespace). */
public final class TUTags {
    private TUTags() {
    }

    public static final class Blocks {
        /** Blocks that heat a crucible placed on top of them. */
        public static final TagKey<Block> CRUCIBLE_HEAT_SOURCES = tag("crucible_heat_sources");
        /** Blocks counted by the infusion stability model (their value comes from the infusion_stabilizer data map). */
        public static final TagKey<Block> INFUSION_STABILIZERS = tag("infusion_stabilizers");
        /** Blocks spells and taint never change. */
        public static final TagKey<Block> MAGIC_IMMUNE = tag("magic_immune");
        /** Blocks Salis Mundus can transform (bookshelf, crafting table, cauldron…). */
        public static final TagKey<Block> SALIS_TRANSMUTABLE = tag("salis_transmutable");

        private Blocks() {
        }

        private static TagKey<Block> tag(String name) {
            return TagKey.create(Registries.BLOCK, ThaumicUnity.id(name));
        }
    }

    public static final class Items {
        /** Items that can cast spells (wands, staves, casters). */
        public static final TagKey<Item> CASTING_INSTRUMENTS = tag("casting_instruments");
        /** Items that can be imprinted with a spell. */
        public static final TagKey<Item> FOCI = tag("foci");
        /** Items that let the wearer see aspects and aura (goggles of revealing, revealing helmets). */
        public static final TagKey<Item> REVEALING = tag("revealing");
        /** Paper that can be written on at the research table. */
        public static final TagKey<Item> RESEARCH_PAPER = tag("research_paper");

        private Items() {
        }

        private static TagKey<Item> tag(String name) {
            return TagKey.create(Registries.ITEM, ThaumicUnity.id(name));
        }
    }
}
