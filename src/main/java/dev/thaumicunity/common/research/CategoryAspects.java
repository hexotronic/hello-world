package dev.thaumicunity.common.research;

import dev.thaumicunity.ThaumicUnity;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Which aspects count as observations for each built-in research category. Categories added by datapacks without
 * an entry here receive observations from any primal aspect.
 */
public final class CategoryAspects {
    private static final Map<String, Set<String>> BY_CATEGORY = Map.of(
            "fundamentals", set("aer", "terra", "ignis", "aqua", "ordo", "perditio", "praecantatio", "cognitio"),
            "auromancy", set("auram", "praecantatio", "vitium", "potentia", "lux", "aer"),
            "alchemy", set("alkimia", "aqua", "permutatio", "venenum", "sano", "victus", "limus", "herba"),
            "artifice", set("fabrico", "instrumentum", "machina", "metallum", "vitreus", "motus", "potentia"),
            "infusion", set("praecantatio", "ordo", "vitreus", "tutamen", "praemunio", "spiritus"),
            "golemancy", set("humanus", "bestia", "motus", "machina", "spiritus", "corpus"),
            "thaumaturgy", set("ignis", "gelum", "potentia", "telum", "aversio", "iter", "volatus"),
            "eldritch", set("alienis", "tenebrae", "vacuos", "vitium", "mortuus", "exanimis"));

    private CategoryAspects() {
    }

    private static Set<String> set(String... paths) {
        return Stream.of(paths).map(p -> ThaumicUnity.id(p).toString()).collect(Collectors.toUnmodifiableSet());
    }

    public static Set<Identifier> of(Identifier category) {
        Set<String> paths = BY_CATEGORY.get(category.getPath());
        if (paths == null) {
            paths = BY_CATEGORY.get("fundamentals");
        }
        return paths.stream().map(Identifier::parse).collect(Collectors.toUnmodifiableSet());
    }
}
