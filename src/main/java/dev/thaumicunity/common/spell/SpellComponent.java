package dev.thaumicunity.common.spell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.api.spell.SpellParams;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.core.spell.ComponentKind;
import dev.thaumicunity.core.spell.ModifierOps;
import dev.thaumicunity.core.spell.SpellComponentSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Value of the {@code thaumicunity:spell_component} datapack registry (docs/SPELLS.md).
 *
 * @param kind           medium, effect or modifier
 * @param primitive      id in the {@code thaumicunity:spell_primitive} registry
 * @param aspect         aspect the component channels (synergies, attunement, colour fallback)
 * @param cost           base vis cost as aspect amounts
 * @param complexity     complexity points
 * @param cooldown       cooldown ticks
 * @param costMultiplier for mediums: multiplier on the cost of carried effects
 * @param tags           free-form tags, matched by modifiers' {@code applies_to}
 * @param appliesTo      for modifiers: medium tags it may attach to (empty = any)
 * @param ops            for modifiers: numeric changes
 * @param warp           warp gained per cast
 * @param synergyOf      for synergy effects: compound aspect that triggers them
 * @param params         primitive parameters
 * @param visual         colour, particle, sound
 * @param research       research that unlocks the component
 * @param tier           minimum focus tier (1–3)
 */
public record SpellComponent(ComponentKind kind, Identifier primitive, Optional<Identifier> aspect, AspectList cost,
                             int complexity, int cooldown, double costMultiplier, List<String> tags,
                             List<String> appliesTo, ModifierOps ops, int warp, Optional<Identifier> synergyOf,
                             JsonElement params, SpellVisual visual, Optional<Identifier> research, int tier) {

    static final Codec<ComponentKind> KIND_CODEC = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(ComponentKind.valueOf(s.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown spell component kind " + s);
        }
    }, ComponentKind::serializedName);

    public static final Codec<ModifierOps> OPS_CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.doubleRange(0.01, 100).optionalFieldOf("power", 1.0).forGetter(ModifierOps::power),
            Codec.doubleRange(0.01, 100).optionalFieldOf("cost", 1.0).forGetter(ModifierOps::cost),
            Codec.doubleRange(0.01, 100).optionalFieldOf("cooldown", 1.0).forGetter(ModifierOps::cooldown),
            Codec.doubleRange(-16, 64).optionalFieldOf("area", 0.0).forGetter(ModifierOps::area),
            Codec.doubleRange(0.01, 100).optionalFieldOf("duration", 1.0).forGetter(ModifierOps::duration),
            Codec.intRange(1, 16).optionalFieldOf("branches", 1).forGetter(ModifierOps::branches)
    ).apply(i, ModifierOps::new));

    public static final Codec<SpellComponent> CODEC = RecordCodecBuilder.create(i -> i.group(
            KIND_CODEC.fieldOf("kind").forGetter(SpellComponent::kind),
            Identifier.CODEC.fieldOf("primitive").forGetter(SpellComponent::primitive),
            Identifier.CODEC.optionalFieldOf("aspect").forGetter(SpellComponent::aspect),
            AspectList.CODEC.optionalFieldOf("cost", AspectList.EMPTY).forGetter(SpellComponent::cost),
            Codec.intRange(0, 1000).optionalFieldOf("complexity", 1).forGetter(SpellComponent::complexity),
            Codec.intRange(0, 6000).optionalFieldOf("cooldown", 0).forGetter(SpellComponent::cooldown),
            Codec.doubleRange(0.01, 100).optionalFieldOf("cost_multiplier", 1.0).forGetter(SpellComponent::costMultiplier),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(SpellComponent::tags),
            Codec.STRING.listOf().optionalFieldOf("applies_to", List.of()).forGetter(SpellComponent::appliesTo),
            OPS_CODEC.optionalFieldOf("ops", ModifierOps.IDENTITY).forGetter(SpellComponent::ops),
            Codec.intRange(0, 100).optionalFieldOf("warp", 0).forGetter(SpellComponent::warp),
            Identifier.CODEC.optionalFieldOf("synergy_of").forGetter(SpellComponent::synergyOf),
            ExtraCodecs.JSON.optionalFieldOf("params", new JsonObject()).forGetter(SpellComponent::params),
            SpellVisual.CODEC.optionalFieldOf("visual", SpellVisual.DEFAULT).forGetter(SpellComponent::visual),
            Identifier.CODEC.optionalFieldOf("research").forGetter(SpellComponent::research),
            Codec.intRange(1, 3).optionalFieldOf("tier", 1).forGetter(SpellComponent::tier)
    ).apply(i, SpellComponent::new));

    public SpellParams spellParams() {
        return SpellParams.of(params);
    }

    public boolean isForbidden() {
        return warp > 0 || tags.contains("forbidden");
    }

    /** Converts to the Minecraft-independent spec used by {@link dev.thaumicunity.core.spell.SpellCompiler}. */
    public SpellComponentSpec toSpec(Identifier id) {
        Map<String, Integer> costs = new HashMap<>();
        cost.forEach((k, v) -> costs.put(k.toString(), v));
        return new SpellComponentSpec(id.toString(), kind, aspect.map(Identifier::toString), costs, complexity, cooldown,
                new HashSet<>(tags), new HashSet<>(appliesTo), ops, costMultiplier, warp, synergyOf.map(Identifier::toString));
    }

    public static String translationKey(Identifier id) {
        return "spell." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }
}
