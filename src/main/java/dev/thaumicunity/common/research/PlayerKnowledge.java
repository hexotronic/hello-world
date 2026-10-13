package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything a player has learned: scans, aspect points, research progress, knowledge points, crafted items, kills,
 * solved insight puzzles and warp. Stored as a player attachment that survives death and is synced to its owner only.
 */
public final class PlayerKnowledge {
    /** Scan keys ({@link ScanTarget#scanKey}). */
    private final Set<String> scanned;
    /** Aspect points available for insight puzzles. */
    private final Map<Identifier, Integer> aspectPoints;
    /** Research id → number of completed stages (complete when equal to the entry's stage count). */
    private final Map<Identifier, Integer> research;
    /** Knowledge points per category: [observation, theory]. */
    private final Map<Identifier, int[]> knowledge;
    private final Set<Identifier> crafted;
    private final Map<Identifier, Integer> kills;
    /** Solved insight requirements: {@code <research id>#<stage>#<requirement index>}. */
    private final Set<String> insights;
    private int warpPermanent;
    private int warpSticky;
    private int warpTemporary;

    public PlayerKnowledge() {
        this(Set.of(), Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), Set.of(), 0, 0, 0);
    }

    private PlayerKnowledge(Set<String> scanned, Map<Identifier, Integer> aspectPoints, Map<Identifier, Integer> research,
                            Map<Identifier, int[]> knowledge, Set<Identifier> crafted, Map<Identifier, Integer> kills,
                            Set<String> insights, int warpPermanent, int warpSticky, int warpTemporary) {
        this.scanned = new HashSet<>(scanned);
        this.aspectPoints = new HashMap<>(aspectPoints);
        this.research = new HashMap<>(research);
        this.knowledge = new HashMap<>();
        knowledge.forEach((k, v) -> this.knowledge.put(k, v.clone()));
        this.crafted = new HashSet<>(crafted);
        this.kills = new HashMap<>(kills);
        this.insights = new HashSet<>(insights);
        this.warpPermanent = warpPermanent;
        this.warpSticky = warpSticky;
        this.warpTemporary = warpTemporary;
    }

    private record KnowledgePoints(int observation, int theory) {
        static final Codec<KnowledgePoints> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("observation", 0).forGetter(KnowledgePoints::observation),
                Codec.INT.optionalFieldOf("theory", 0).forGetter(KnowledgePoints::theory)
        ).apply(i, KnowledgePoints::new));
    }

    public static final Codec<PlayerKnowledge> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.listOf().optionalFieldOf("scanned", List.of()).forGetter(k -> List.copyOf(k.scanned)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("aspect_points", Map.of()).forGetter(k -> k.aspectPoints),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("research", Map.of()).forGetter(k -> k.research),
            Codec.unboundedMap(Identifier.CODEC, KnowledgePoints.CODEC).optionalFieldOf("knowledge", Map.of()).forGetter(k -> {
                Map<Identifier, KnowledgePoints> out = new HashMap<>();
                k.knowledge.forEach((c, v) -> out.put(c, new KnowledgePoints(v[0], v[1])));
                return out;
            }),
            Identifier.CODEC.listOf().optionalFieldOf("crafted", List.of()).forGetter(k -> List.copyOf(k.crafted)),
            Codec.unboundedMap(Identifier.CODEC, Codec.INT).optionalFieldOf("kills", Map.of()).forGetter(k -> k.kills),
            Codec.STRING.listOf().optionalFieldOf("insights", List.of()).forGetter(k -> List.copyOf(k.insights)),
            Codec.INT.optionalFieldOf("warp_permanent", 0).forGetter(k -> k.warpPermanent),
            Codec.INT.optionalFieldOf("warp_sticky", 0).forGetter(k -> k.warpSticky),
            Codec.INT.optionalFieldOf("warp_temporary", 0).forGetter(k -> k.warpTemporary)
    ).apply(i, (scanned, points, research, knowledge, crafted, kills, insights, wp, ws, wt) -> {
        Map<Identifier, int[]> kn = new HashMap<>();
        knowledge.forEach((c, v) -> kn.put(c, new int[]{v.observation(), v.theory()}));
        return new PlayerKnowledge(new HashSet<>(scanned), points, research, kn, new HashSet<>(crafted), kills,
                new HashSet<>(insights), wp, ws, wt);
    }));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerKnowledge> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PlayerKnowledge copy() {
        return new PlayerKnowledge(scanned, aspectPoints, research, knowledge, crafted, kills, insights, warpPermanent,
                warpSticky, warpTemporary);
    }

    // --- scanning

    public boolean hasScanned(String key) {
        return scanned.contains(key);
    }

    /** Records a scan; returns false if it was already known. */
    public boolean markScanned(String key) {
        return scanned.add(key);
    }

    public int scanCount() {
        return scanned.size();
    }

    // --- aspect points

    public int aspectPoints(Identifier aspect) {
        return aspectPoints.getOrDefault(aspect, 0);
    }

    public Map<Identifier, Integer> aspectPoints() {
        return Map.copyOf(aspectPoints);
    }

    public void addAspectPoints(Identifier aspect, int amount) {
        aspectPoints.merge(aspect, amount, Integer::sum);
        if (aspectPoints.get(aspect) <= 0) {
            aspectPoints.remove(aspect);
        }
    }

    public boolean spendAspectPoint(Identifier aspect) {
        if (aspectPoints(aspect) <= 0) {
            return false;
        }
        addAspectPoints(aspect, -1);
        return true;
    }

    // --- research

    public int stage(Identifier entry) {
        return research.getOrDefault(entry, 0);
    }

    public void setStage(Identifier entry, int stage) {
        if (stage <= 0) {
            research.remove(entry);
        } else {
            research.put(entry, stage);
        }
    }

    public Map<Identifier, Integer> researchProgress() {
        return Map.copyOf(research);
    }

    // --- knowledge points

    public int knowledge(ResearchRequirement.KnowledgeKind kind, Identifier category) {
        int[] v = knowledge.get(category);
        return v == null ? 0 : v[kind.ordinal()];
    }

    public void addKnowledge(ResearchRequirement.KnowledgeKind kind, Identifier category, int amount) {
        int[] v = knowledge.computeIfAbsent(category, c -> new int[2]);
        v[kind.ordinal()] = Math.max(0, v[kind.ordinal()] + amount);
    }

    // --- crafting and kills

    public boolean hasCrafted(Identifier item) {
        return crafted.contains(item);
    }

    public boolean markCrafted(Identifier item) {
        return crafted.add(item);
    }

    public int kills(Identifier entityType) {
        return kills.getOrDefault(entityType, 0);
    }

    public void addKill(Identifier entityType) {
        kills.merge(entityType, 1, Integer::sum);
    }

    // --- insight puzzles

    public static String insightKey(Identifier entry, int stage, int requirementIndex) {
        return entry + "#" + stage + "#" + requirementIndex;
    }

    public boolean hasSolvedInsight(String key) {
        return insights.contains(key);
    }

    public void markInsightSolved(String key) {
        insights.add(key);
    }

    // --- warp

    public int warpPermanent() {
        return warpPermanent;
    }

    public int warpSticky() {
        return warpSticky;
    }

    public int warpTemporary() {
        return warpTemporary;
    }

    public int totalWarp() {
        return warpPermanent + warpSticky + warpTemporary;
    }

    public void addWarp(int permanent, int sticky, int temporary) {
        warpPermanent = Math.max(0, warpPermanent + permanent);
        warpSticky = Math.max(0, warpSticky + sticky);
        warpTemporary = Math.max(0, warpTemporary + temporary);
    }
}
