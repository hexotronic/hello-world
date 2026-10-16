package dev.thaumicunity.common.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client configuration (config/thaumicunity-client.toml): visuals and interface only. */
public final class TUClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.EnumValue<ParticleQuality> PARTICLE_QUALITY;
    public static final ModConfigSpec.BooleanValue SHOW_AURA_HUD;
    public static final ModConfigSpec.BooleanValue ASPECTS_NEED_SHIFT;
    public static final ModConfigSpec.BooleanValue BOOK_ANIMATIONS;
    public static final ModConfigSpec.BooleanValue USE_THAUMCRAFT_TEXTURES;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("visuals");
        PARTICLE_QUALITY = b.comment("How many magic particles to show").defineEnum("particleQuality", ParticleQuality.ALL);
        SHOW_AURA_HUD = b.comment("Show the aura meter while holding a thaumometer or wearing goggles of revealing")
                .define("auraHud", true);
        BOOK_ANIMATIONS = b.comment("Drifting motes over the Thaumonomicon research map").define("bookAnimations", true);
        USE_THAUMCRAFT_TEXTURES = b.comment("Use the textures of a Thaumcraft jar placed in <game dir>/thaumicunity/ (yours to own)")
                .define("useThaumcraftTextures", true);
        b.pop();
        b.push("tooltips");
        ASPECTS_NEED_SHIFT = b.comment("Only show aspects in tooltips while Shift is held").define("aspectsNeedShift", true);
        b.pop();
        SPEC = b.build();
    }

    private TUClientConfig() {
    }

    public enum ParticleQuality {
        MINIMAL(0.25), DECREASED(0.5), ALL(1.0);

        public final double multiplier;

        ParticleQuality(double multiplier) {
            this.multiplier = multiplier;
        }
    }
}
