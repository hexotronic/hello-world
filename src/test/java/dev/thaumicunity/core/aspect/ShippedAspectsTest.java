package dev.thaumicunity.core.aspect;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validates the aspect files that ship in the mod jar and in the optional modern-combinations datapack. */
class ShippedAspectsTest {
    static final Path CLASSIC = Path.of("src/main/resources/data/thaumicunity/thaumicunity/aspect");
    static final Path MODERN = Path.of("src/main/resources/datapacks/modern_combinations/data/thaumicunity/thaumicunity/aspect");

    static Map<String, AspectDefinition> load(Path dir) throws IOException {
        Map<String, AspectDefinition> result = new HashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = file.getFileName().toString();
                String id = "thaumicunity:" + name.substring(0, name.length() - 5);
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                    int color = Integer.parseInt(json.get("color").getAsString().substring(1), 16);
                    List<String> components = new ArrayList<>();
                    JsonArray array = json.getAsJsonArray("components");
                    array.forEach(e -> components.add(e.getAsString()));
                    result.put(id, new AspectDefinition(id, color, components));
                }
            }
        }
        return result;
    }

    @Test
    void classicSetIsValid() throws IOException {
        Map<String, AspectDefinition> defs = load(CLASSIC);
        AspectGraph graph = AspectGraph.build(defs.values());
        assertTrue(graph.problems().isEmpty(), graph.problems().toString());
        assertEquals(183, graph.size());
        assertEquals(6, graph.primals().size());
        // Every aspect is connected to every primal through related aspects, so any insight puzzle is solvable.
        for (AspectDefinition def : graph.all()) {
            assertTrue(!graph.shortestChain(def.id(), "thaumicunity:ignis").isEmpty(), def.id());
        }
    }

    @Test
    void modernOverridesStayValid() throws IOException {
        Map<String, AspectDefinition> defs = load(CLASSIC);
        defs.putAll(load(MODERN));
        AspectGraph graph = AspectGraph.build(defs.values());
        assertTrue(graph.problems().isEmpty(), graph.problems().toString());
        assertEquals("thaumicunity:vitreus", graph.combine("thaumicunity:terra", "thaumicunity:aer").orElseThrow());
    }
}
