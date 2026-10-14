package dev.thaumicunity.common.spell;

import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellCompiler;
import dev.thaumicunity.core.spell.SpellComponentLibrary;
import dev.thaumicunity.core.spell.SpellComponentSpec;
import dev.thaumicunity.core.spell.SpellLimits;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spell components of one registry access (server or client) together with a memoising compiler. A new world or
 * server connection has a new registry access and so gets its own library.
 */
public final class SpellLibrary {
    /** One library per registry access (client and integrated server each have their own); weak keys free old worlds. */
    private static final Map<RegistryAccess, SpellLibrary> LIBRARIES = Collections.synchronizedMap(new WeakHashMap<>());

    private final Map<Identifier, SpellComponent> components;
    private final SpellCompiler compiler;
    private final Map<CacheKey, CompiledSpell> compiled = new ConcurrentHashMap<>();

    private record CacheKey(SpellNode node, SpellLimits limits) {
    }

    private SpellLibrary(RegistryAccess access) {
        Map<Identifier, SpellComponent> map = new HashMap<>();
        List<SpellComponentSpec> specs = new ArrayList<>();
        Optional<Registry<SpellComponent>> registry = access.lookup(TURegistries.SPELL_COMPONENT);
        java.util.Set<String> disabled = new java.util.HashSet<>(TUConfig.DISABLED_SPELL_COMPONENTS.get());
        registry.ifPresent(r -> r.entrySet().forEach(e -> {
            Identifier id = e.getKey().identifier();
            if (!disabled.contains(id.toString())) {
                map.put(id, e.getValue());
                specs.add(e.getValue().toSpec(id));
            }
        }));
        this.components = Map.copyOf(map);
        this.compiler = new SpellCompiler(new SpellComponentLibrary(specs), Aspects.graph(access), TUConfig.SPELL_COST_SCALE.get());
    }

    public static SpellLibrary get(RegistryAccess access) {
        return LIBRARIES.computeIfAbsent(access, SpellLibrary::new);
    }

    /** Drops the cache (config or data reload). */
    public static void invalidate() {
        LIBRARIES.clear();
    }

    public Optional<SpellComponent> component(Identifier id) {
        return Optional.ofNullable(components.get(id));
    }

    public Optional<SpellComponent> component(String id) {
        Identifier parsed = Identifier.tryParse(id);
        return parsed == null ? Optional.empty() : component(parsed);
    }

    public Map<Identifier, SpellComponent> all() {
        return components;
    }

    public CompiledSpell compile(SpellNode node, SpellLimits limits) {
        return compiled.computeIfAbsent(new CacheKey(node, limits), k -> compiler.compile(k.node(), k.limits()));
    }
}
