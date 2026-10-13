package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.thaumicunity.core.research.HexCoord;
import dev.thaumicunity.core.research.InsightPuzzle;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An insight puzzle written on research notes: which research requirement it answers, and the board state.
 *
 * @param research    research entry id
 * @param stage       stage index the puzzle belongs to
 * @param requirement index of the insight requirement within the stage
 * @param radius      board radius
 * @param cells       every non-empty cell, as {@code "q,r=aspect"} with a {@code !} prefix for anchors and
 *                    {@code #} for blocked cells
 * @param solved      whether the puzzle was solved
 */
public record InsightNote(Identifier research, int stage, int requirement, int radius, List<String> cells, boolean solved) {
    public static final Codec<InsightNote> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("research").forGetter(InsightNote::research),
            Codec.INT.fieldOf("stage").forGetter(InsightNote::stage),
            Codec.INT.fieldOf("requirement").forGetter(InsightNote::requirement),
            Codec.intRange(1, 6).fieldOf("radius").forGetter(InsightNote::radius),
            Codec.STRING.listOf().fieldOf("cells").forGetter(InsightNote::cells),
            Codec.BOOL.optionalFieldOf("solved", false).forGetter(InsightNote::solved)
    ).apply(i, InsightNote::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, InsightNote> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public static InsightNote of(Identifier research, int stage, int requirement, InsightPuzzle puzzle, boolean solved) {
        List<String> cells = new java.util.ArrayList<>();
        puzzle.anchors().forEach((c, a) -> cells.add("!" + c.q() + "," + c.r() + "=" + a));
        puzzle.placed().forEach((c, a) -> cells.add(c.q() + "," + c.r() + "=" + a));
        puzzle.blocked().forEach(c -> cells.add("#" + c.q() + "," + c.r()));
        return new InsightNote(research, stage, requirement, puzzle.radius(), List.copyOf(cells), solved);
    }

    public InsightPuzzle toPuzzle() {
        Map<HexCoord, String> anchors = new LinkedHashMap<>();
        Map<HexCoord, String> placed = new LinkedHashMap<>();
        Set<HexCoord> blocked = new HashSet<>();
        for (String cell : cells) {
            if (cell.startsWith("#")) {
                blocked.add(parseCoord(cell.substring(1)));
                continue;
            }
            boolean anchor = cell.startsWith("!");
            String body = anchor ? cell.substring(1) : cell;
            int eq = body.indexOf('=');
            if (eq < 0) {
                continue;
            }
            HexCoord coord = parseCoord(body.substring(0, eq));
            (anchor ? anchors : placed).put(coord, body.substring(eq + 1));
        }
        return new InsightPuzzle(radius, anchors, placed, blocked);
    }

    private static HexCoord parseCoord(String text) {
        int comma = text.indexOf(',');
        return new HexCoord(Integer.parseInt(text.substring(0, comma).trim()), Integer.parseInt(text.substring(comma + 1).trim()));
    }

    public String requirementKey() {
        return PlayerKnowledge.insightKey(research, stage, requirement);
    }
}
