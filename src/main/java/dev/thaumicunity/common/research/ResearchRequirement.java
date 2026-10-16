package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.core.research.RequirementSpec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * One requirement of a research stage (docs/DESIGN_DECISIONS.md, D3). JSON objects carry a {@code type} field:
 * {@code scan}, {@code craft}, {@code obtain}, {@code knowledge}, {@code insight}, {@code research}, {@code warp},
 * {@code dimension} or {@code kill}.
 */
public sealed interface ResearchRequirement {
    Type type();

    /** Minecraft-independent form used for research mode conversion. */
    default RequirementSpec toSpec() {
        return new RequirementSpec.Other(type().key(), this);
    }

    enum Type {
        SCAN(Scan.CODEC), CRAFT(Craft.CODEC), OBTAIN(Obtain.CODEC), KNOWLEDGE(Knowledge.CODEC), INSIGHT(Insight.CODEC),
        RESEARCH(Research.CODEC), WARP(Warp.CODEC), DIMENSION(Dimension.CODEC), KILL(Kill.CODEC);

        private final MapCodec<? extends ResearchRequirement> codec;

        Type(MapCodec<? extends ResearchRequirement> codec) {
            this.codec = codec;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static final Map<String, Type> BY_KEY = java.util.Arrays.stream(values())
                .collect(Collectors.toMap(Type::key, Function.identity()));
        static final Codec<Type> CODEC = Codec.STRING.comapFlatMap(
                s -> BY_KEY.containsKey(s) ? com.mojang.serialization.DataResult.success(BY_KEY.get(s))
                        : com.mojang.serialization.DataResult.error(() -> "Unknown research requirement type " + s),
                Type::key);
    }

    Codec<ResearchRequirement> CODEC = Type.CODEC.dispatch("type", ResearchRequirement::type, t -> t.codec);

    /** Knowledge point kinds (Thaumcraft 6 observations and theories). */
    enum KnowledgeKind {
        OBSERVATION, THEORY;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        static final Codec<KnowledgeKind> CODEC = Codec.STRING.xmap(s -> valueOf(s.toUpperCase(Locale.ROOT)), KnowledgeKind::key);
    }

    record Scan(ScanTarget target) implements ResearchRequirement {
        static final MapCodec<Scan> CODEC = ScanTarget.CODEC.fieldOf("target").xmap(Scan::new, Scan::target);

        public Type type() {
            return Type.SCAN;
        }
    }

    record Craft(Identifier item) implements ResearchRequirement {
        static final MapCodec<Craft> CODEC = Identifier.CODEC.fieldOf("item").xmap(Craft::new, Craft::item);

        public Type type() {
            return Type.CRAFT;
        }
    }

    record Obtain(Identifier item, int count, boolean consume) implements ResearchRequirement {
        static final MapCodec<Obtain> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("item").forGetter(Obtain::item),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Obtain::count),
                Codec.BOOL.optionalFieldOf("consume", false).forGetter(Obtain::consume)
        ).apply(i, Obtain::new));

        public Type type() {
            return Type.OBTAIN;
        }
    }

    record Knowledge(KnowledgeKind kind, Identifier category, int amount) implements ResearchRequirement {
        static final MapCodec<Knowledge> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                KnowledgeKind.CODEC.fieldOf("kind").forGetter(Knowledge::kind),
                Identifier.CODEC.fieldOf("category").forGetter(Knowledge::category),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("amount", 1).forGetter(Knowledge::amount)
        ).apply(i, Knowledge::new));

        public Type type() {
            return Type.KNOWLEDGE;
        }

        @Override
        public RequirementSpec toSpec() {
            return new RequirementSpec.Knowledge(kind.key(), category.toString(), amount);
        }
    }

    record Insight(List<Identifier> aspects) implements ResearchRequirement {
        static final MapCodec<Insight> CODEC = Identifier.CODEC.listOf(2, 6).fieldOf("aspects").xmap(Insight::new, Insight::aspects);

        public Type type() {
            return Type.INSIGHT;
        }

        @Override
        public RequirementSpec toSpec() {
            return new RequirementSpec.Insight(aspects.stream().map(Identifier::toString).toList());
        }
    }

    record Research(Identifier id) implements ResearchRequirement {
        static final MapCodec<Research> CODEC = Identifier.CODEC.fieldOf("id").xmap(Research::new, Research::id);

        public Type type() {
            return Type.RESEARCH;
        }
    }

    record Warp(int minimum) implements ResearchRequirement {
        static final MapCodec<Warp> CODEC = ExtraCodecs.POSITIVE_INT.fieldOf("minimum").xmap(Warp::new, Warp::minimum);

        public Type type() {
            return Type.WARP;
        }
    }

    record Dimension(Identifier dimension) implements ResearchRequirement {
        static final MapCodec<Dimension> CODEC = Identifier.CODEC.fieldOf("dimension").xmap(Dimension::new, Dimension::dimension);

        public Type type() {
            return Type.DIMENSION;
        }
    }

    record Kill(Identifier entity, int count) implements ResearchRequirement {
        static final MapCodec<Kill> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.fieldOf("entity").forGetter(Kill::entity),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).forGetter(Kill::count)
        ).apply(i, Kill::new));

        public Type type() {
            return Type.KILL;
        }
    }

    /** Converts a core requirement produced by mode conversion back into a research requirement. */
    static ResearchRequirement fromSpec(RequirementSpec spec) {
        return switch (spec) {
            case RequirementSpec.Knowledge k -> new Knowledge(KnowledgeKind.valueOf(k.kind().toUpperCase(Locale.ROOT)),
                    Identifier.parse(k.category()), k.amount());
            case RequirementSpec.Insight in -> new Insight(in.aspects().stream().map(Identifier::parse).toList());
            case RequirementSpec.Other o -> (ResearchRequirement) o.payload();
        };
    }
}
