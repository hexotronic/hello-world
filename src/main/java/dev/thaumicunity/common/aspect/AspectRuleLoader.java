package dev.thaumicunity.common.aspect;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.List;
import java.util.Map;

/** Loads {@link AspectRule}s on every data reload. */
public final class AspectRuleLoader extends SimpleJsonResourceReloadListener<AspectRule> {
    private static volatile List<AspectRule> rules = List.of();

    public AspectRuleLoader() {
        super(AspectRule.CODEC, FileToIdConverter.json("thaumicunity/aspect_rule"));
    }

    @Override
    protected void apply(Map<Identifier, AspectRule> loaded, ResourceManager manager, ProfilerFiller profiler) {
        rules = List.copyOf(loaded.values());
    }

    public static List<AspectRule> rules() {
        return rules;
    }
}
