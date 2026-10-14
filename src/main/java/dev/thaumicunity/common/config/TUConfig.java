package dev.thaumicunity.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

/**
 * Gameplay configuration. Registered as a synced config: the server loads it when a world starts (a copy in the
 * world's {@code syncedconfig} folder overrides the global file) and sends it to every client, so research mode and
 * aspect visibility agree on both sides. Options for systems that later phases add are introduced with them.
 */
public final class TUConfig {
    public static final ModConfigSpec SPEC;

    // --- research
    public static final ModConfigSpec.EnumValue<ResearchModeOption> RESEARCH_MODE;
    public static final ModConfigSpec.IntValue SCAN_POINTS_PER_ASPECT;
    public static final ModConfigSpec.IntValue INSIGHT_BOARD_RADIUS;

    // --- aspects
    public static final ModConfigSpec.BooleanValue DERIVE_ASPECTS_FROM_RECIPES;
    public static final ModConfigSpec.DoubleValue DERIVATION_LOSS_FACTOR;
    public static final ModConfigSpec.IntValue DERIVATION_CAP;
    public static final ModConfigSpec.BooleanValue ALWAYS_SHOW_ASPECTS;

    // --- aura
    public static final ModConfigSpec.EnumValue<AuraModeOption> AURA_MODE;
    public static final ModConfigSpec.IntValue AURA_CHUNKS_PER_TICK;
    public static final ModConfigSpec.IntValue AURA_TICK_INTERVAL;
    public static final ModConfigSpec.DoubleValue AURA_REGEN_RATE;
    public static final ModConfigSpec.DoubleValue AURA_DIFFUSION_RATE;
    public static final ModConfigSpec.DoubleValue AURA_FLUX_DECAY;
    public static final ModConfigSpec.DoubleValue AURA_RIFT_PRESSURE;
    public static final ModConfigSpec.IntValue AURA_DEFAULT_BASE;

    // --- flux
    public static final ModConfigSpec.BooleanValue FLUX_ENABLED;

    // --- casting
    public static final ModConfigSpec.BooleanValue CASTERS_ENABLED;
    public static final ModConfigSpec.BooleanValue WANDS_ENABLED;
    public static final ModConfigSpec.DoubleValue WAND_AURA_RECHARGE;
    public static final ModConfigSpec.DoubleValue WAND_PEDESTAL_RECHARGE;
    public static final ModConfigSpec.IntValue VIS_CRYSTAL_VALUE;
    public static final ModConfigSpec.DoubleValue SPELL_COST_SCALE;
    public static final ModConfigSpec.DoubleValue SPELL_POWER_SCALE;
    public static final ModConfigSpec.BooleanValue SPELL_GRIEFING;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> DISABLED_SPELL_COMPONENTS;

    // --- warp
    public static final ModConfigSpec.BooleanValue WARP_ENABLED;

    // --- crafting
    public static final ModConfigSpec.IntValue CRUCIBLE_CAPACITY;
    public static final ModConfigSpec.DoubleValue INFUSION_INSTABILITY_SCALE;
    public static final ModConfigSpec.IntValue INFUSION_RANGE;

    // --- world
    public static final ModConfigSpec.BooleanValue WORLDGEN_ENABLED;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("Research progression (docs/DESIGN_DECISIONS.md, D3)").push("research");
        RESEARCH_MODE = b.comment("unified: stages as written; classic: knowledge costs become insight puzzles;",
                        "modern: insight puzzles become theory costs")
                .defineEnum("mode", ResearchModeOption.UNIFIED);
        SCAN_POINTS_PER_ASPECT = b.comment("Aspect points granted per aspect type the first time something is scanned")
                .defineInRange("scanPointsPerAspect", 2, 0, 64);
        INSIGHT_BOARD_RADIUS = b.comment("Radius of the insight puzzle board")
                .defineInRange("insightBoardRadius", 3, 2, 5);
        b.pop();

        b.comment("Aspects on items (docs/DESIGN_DECISIONS.md, D13)").push("aspects");
        DERIVE_ASPECTS_FROM_RECIPES = b.comment("Give items without explicit aspects the aspects of their recipe ingredients")
                .define("deriveFromRecipes", true);
        DERIVATION_LOSS_FACTOR = b.comment("Fraction of ingredient aspects an item keeps when derived from a recipe")
                .defineInRange("derivationLossFactor", 0.75, 0.05, 1.0);
        DERIVATION_CAP = b.comment("Maximum amount of any one aspect a derived item can have")
                .defineInRange("derivationCap", 64, 1, 1024);
        ALWAYS_SHOW_ASPECTS = b.comment("Show aspects in tooltips even for things the player has not scanned")
                .define("alwaysShowAspects", false);
        b.pop();

        b.comment("Vis and flux (docs/DESIGN_DECISIONS.md, D2)").push("aura");
        AURA_MODE = b.comment("hybrid: chunk aura plus nodes; chunk: chunk aura only; nodes: casters cannot draw chunk vis")
                .defineEnum("mode", AuraModeOption.HYBRID);
        AURA_CHUNKS_PER_TICK = b.comment("Maximum chunks simulated per level per aura update")
                .defineInRange("chunksPerTick", 64, 1, 4096);
        AURA_TICK_INTERVAL = b.comment("Game ticks between aura updates")
                .defineInRange("tickInterval", 10, 1, 200);
        AURA_REGEN_RATE = b.comment("Fraction of the missing vis a chunk regains per update")
                .defineInRange("regenRate", 0.02, 0.0, 1.0);
        AURA_DIFFUSION_RATE = b.comment("Fraction of the vis difference to lower neighbouring chunks that flows out per update (shared by the four sides)")
                .defineInRange("diffusionRate", 0.08, 0.0, 1.0);
        AURA_FLUX_DECAY = b.comment("Fraction of flux that dissipates per update while flux pressure is below riftPressure")
                .defineInRange("fluxDecay", 0.004, 0.0, 1.0);
        AURA_RIFT_PRESSURE = b.comment("Flux pressure (flux / base) above which flux stops dissipating and rifts may form")
                .defineInRange("riftPressure", 0.75, 0.05, 100.0);
        AURA_DEFAULT_BASE = b.comment("Base vis of biomes without an aura profile")
                .defineInRange("defaultBase", 100, 0, 10000);
        b.pop();

        b.comment("Flux (docs/DESIGN_DECISIONS.md, D6)").push("flux");
        FLUX_ENABLED = b.comment("Magic produces flux; disable to make all magic clean").define("enabled", true);
        b.pop();

        b.comment("Casting (docs/DESIGN_DECISIONS.md, D4)").push("casting");
        CASTERS_ENABLED = b.comment("Allow casting spells with caster's gauntlets").define("casters", true);
        WANDS_ENABLED = b.comment("Allow casting spells with wands").define("wands", true);
        WAND_AURA_RECHARGE = b.comment("Vis a carried wand draws from a strong aura every five seconds")
                .defineInRange("wandAuraRecharge", 1.0, 0.0, 1000.0);
        WAND_PEDESTAL_RECHARGE = b.comment("Vis a wand on a recharge pedestal draws from the aura every second")
                .defineInRange("wandPedestalRecharge", 2.0, 0.0, 1000.0);
        VIS_CRYSTAL_VALUE = b.comment("Vis of its primal a wand gains from eating one vis crystal")
                .defineInRange("visCrystalValue", 10, 0, 1000);
        SPELL_COST_SCALE = b.comment("Multiplier on every spell's vis cost").defineInRange("costScale", 1.0, 0.0, 100.0);
        SPELL_POWER_SCALE = b.comment("Multiplier on every spell's power (damage, healing, duration)")
                .defineInRange("powerScale", 1.0, 0.0, 100.0);
        SPELL_GRIEFING = b.comment("Allow spells to break, place or change blocks").define("griefing", true);
        DISABLED_SPELL_COMPONENTS = b.comment("Spell component ids that cannot be used, e.g. [\"thaumicunity:rift\"]")
                .defineListAllowEmpty("disabledComponents", List.of(), () -> "", o -> o instanceof String);
        b.pop();

        b.comment("Warp (docs/DESIGN_DECISIONS.md, D6)").push("warp");
        WARP_ENABLED = b.comment("Forbidden spells and research give warp").define("enabled", true);
        b.pop();

        b.push("crafting");
        CRUCIBLE_CAPACITY = b.comment("Essentia a crucible holds before the excess turns into flux")
                .defineInRange("crucibleCapacity", 500, 10, 100000);
        INFUSION_INSTABILITY_SCALE = b.comment("Multiplier on the chance of instability events during infusion")
                .defineInRange("infusionInstabilityScale", 1.0, 0.0, 10.0);
        INFUSION_RANGE = b.comment("Horizontal range in blocks in which the runic matrix finds pedestals and jars")
                .defineInRange("infusionRange", 8, 3, 16);
        b.pop();

        b.push("world");
        WORLDGEN_ENABLED = b.comment("Generate Thaumic Unity ores and features in new chunks").define("worldgen", true);
        b.pop();

        SPEC = b.build();
    }

    private TUConfig() {
    }

    public enum ResearchModeOption { UNIFIED, CLASSIC, MODERN }

    public enum AuraModeOption { HYBRID, CHUNK, NODES }
}
