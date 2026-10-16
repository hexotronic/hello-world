package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Value of the {@code thaumicunity:research} datapack registry.
 *
 * @param category  research category id
 * @param x         column on the category's research map
 * @param y         row on the category's research map
 * @param icon      item id drawn on the node
 * @param parents   entries that must be complete before this one becomes available
 * @param aspects   aspects associated with the entry (insight puzzles, classic-mode conversion, node glow)
 * @param stages    the stages, in order; the entry is complete when the last stage's requirements are met
 * @param hidden    not shown on the map until available
 * @param special   drawn with an ornate frame (milestones)
 * @param autoUnlock granted to every player at start
 */
public record ResearchEntry(Identifier category, int x, int y, Identifier icon, List<Identifier> parents,
                            List<Identifier> aspects, List<ResearchStage> stages, boolean hidden, boolean special,
                            boolean autoUnlock) {
    public static final Codec<ResearchEntry> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("category").forGetter(ResearchEntry::category),
            Codec.INT.fieldOf("x").forGetter(ResearchEntry::x),
            Codec.INT.fieldOf("y").forGetter(ResearchEntry::y),
            Identifier.CODEC.fieldOf("icon").forGetter(ResearchEntry::icon),
            Identifier.CODEC.listOf().optionalFieldOf("parents", List.of()).forGetter(ResearchEntry::parents),
            Identifier.CODEC.listOf().optionalFieldOf("aspects", List.of()).forGetter(ResearchEntry::aspects),
            ResearchStage.CODEC.listOf(1, 32).fieldOf("stages").forGetter(ResearchEntry::stages),
            Codec.BOOL.optionalFieldOf("hidden", false).forGetter(ResearchEntry::hidden),
            Codec.BOOL.optionalFieldOf("special", false).forGetter(ResearchEntry::special),
            Codec.BOOL.optionalFieldOf("auto_unlock", false).forGetter(ResearchEntry::autoUnlock)
    ).apply(i, ResearchEntry::new));

    public static String titleKey(Identifier id) {
        return "research." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public int stageCount() {
        return stages.size();
    }
}
