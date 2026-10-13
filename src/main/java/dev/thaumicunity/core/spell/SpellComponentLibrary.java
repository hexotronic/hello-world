package dev.thaumicunity.core.spell;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** All loaded spell components, indexed by id and by the compound aspect that triggers synergy effects. */
public final class SpellComponentLibrary {
    private final Map<String, SpellComponentSpec> byId;
    private final Map<String, List<String>> synergiesByAspect;

    public SpellComponentLibrary(Collection<SpellComponentSpec> specs) {
        Map<String, SpellComponentSpec> ids = new TreeMap<>();
        Map<String, List<String>> synergies = new HashMap<>();
        for (SpellComponentSpec spec : specs) {
            ids.put(spec.id(), spec);
            spec.synergyOf().ifPresent(aspect -> synergies.computeIfAbsent(aspect, k -> new ArrayList<>()).add(spec.id()));
        }
        synergies.replaceAll((k, v) -> v.stream().sorted().toList());
        this.byId = Map.copyOf(ids);
        this.synergiesByAspect = Map.copyOf(synergies);
    }

    public Optional<SpellComponentSpec> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    /** Synergy effects triggered when components of {@code compoundAspect} are carried together. */
    public List<String> synergiesFor(String compoundAspect) {
        return synergiesByAspect.getOrDefault(compoundAspect, List.of());
    }

    public Collection<SpellComponentSpec> all() {
        return byId.values();
    }

    public int size() {
        return byId.size();
    }
}
