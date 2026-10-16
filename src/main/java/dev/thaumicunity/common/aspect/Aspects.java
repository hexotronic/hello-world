package dev.thaumicunity.common.aspect;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.core.aspect.AspectDefinition;
import dev.thaumicunity.core.aspect.AspectGraph;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Aspect registry access with a cached {@link AspectGraph}. The cache is keyed by the {@link RegistryAccess}
 * instance, which changes whenever a world is loaded or a server is joined, so it never serves stale data.
 */
public final class Aspects {
    public static final Identifier AER = ThaumicUnity.id("aer");
    public static final Identifier TERRA = ThaumicUnity.id("terra");
    public static final Identifier IGNIS = ThaumicUnity.id("ignis");
    public static final Identifier AQUA = ThaumicUnity.id("aqua");
    public static final Identifier ORDO = ThaumicUnity.id("ordo");
    public static final Identifier PERDITIO = ThaumicUnity.id("perditio");
    public static final Identifier VITIUM = ThaumicUnity.id("vitium");
    public static final Identifier PRAECANTATIO = ThaumicUnity.id("praecantatio");
    /** Primals in {@link dev.thaumicunity.core.aura.PrimalAttunement#PRIMAL_ORDER} order. */
    public static final List<Identifier> PRIMALS = List.of(AER, TERRA, IGNIS, AQUA, ORDO, PERDITIO);

    /**
     * One snapshot per registry access; in single player the client and the integrated server each have their own,
     * so a single slot would be rebuilt constantly. Weak keys drop snapshots of worlds that were left.
     */
    private static final Map<RegistryAccess, Snapshot> SNAPSHOTS = Collections.synchronizedMap(new WeakHashMap<>());

    private record Snapshot(Map<Identifier, Aspect> aspects, AspectGraph graph) {
    }

    private Aspects() {
    }

    private static Snapshot snapshot(RegistryAccess access) {
        return SNAPSHOTS.computeIfAbsent(access, Aspects::build);
    }

    private static Snapshot build(RegistryAccess access) {
        Map<Identifier, Aspect> map = new LinkedHashMap<>();
        Optional<Registry<Aspect>> registry = access.lookup(TURegistries.ASPECT);
        registry.ifPresent(r -> r.entrySet().forEach(e -> map.put(e.getKey().identifier(), e.getValue())));
        List<AspectDefinition> defs = new ArrayList<>();
        map.forEach((id, aspect) -> defs.add(new AspectDefinition(id.toString(), aspect.color(),
                aspect.components().stream().map(Identifier::toString).toList())));
        AspectGraph graph = AspectGraph.build(defs);
        graph.problems().forEach(p -> ThaumicUnity.LOGGER.warn("Aspect data problem: {}", p));
        return new Snapshot(Collections.unmodifiableMap(map), graph);
    }

    public static AspectGraph graph(RegistryAccess access) {
        return snapshot(access).graph();
    }

    public static Map<Identifier, Aspect> all(RegistryAccess access) {
        return snapshot(access).aspects();
    }

    public static Optional<Aspect> get(RegistryAccess access, Identifier id) {
        return Optional.ofNullable(snapshot(access).aspects().get(id));
    }

    public static int color(RegistryAccess access, Identifier id) {
        Aspect aspect = snapshot(access).aspects().get(id);
        return aspect == null ? 0xFFFFFF : aspect.color();
    }

    /** Tier of an aspect (0 for primals), -1 if unknown. */
    public static int tier(RegistryAccess access, Identifier id) {
        return graph(access).tier(id.toString());
    }

    /** Expands aspects into primals according to the current graph. */
    public static AspectList toPrimals(RegistryAccess access, AspectList list) {
        return AspectList.fromCore(list.toCore().toPrimals(graph(access)));
    }

    /** Index of a primal in {@link #PRIMALS}, or -1. */
    public static int primalIndex(Identifier id) {
        return PRIMALS.indexOf(id);
    }
}
