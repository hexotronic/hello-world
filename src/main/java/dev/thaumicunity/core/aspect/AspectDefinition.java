package dev.thaumicunity.core.aspect;

import java.util.List;
import java.util.Objects;

/**
 * Minecraft-independent description of one aspect: its identifier ({@code namespace:path}), display colour and
 * the two aspects it is made of. Primal aspects have no components.
 *
 * @param id         namespaced identifier, e.g. {@code thaumicunity:ignis}
 * @param color      RGB colour (0xRRGGBB)
 * @param components empty for primal aspects, exactly two identifiers for compounds
 */
public record AspectDefinition(String id, int color, List<String> components) {
    public AspectDefinition {
        Objects.requireNonNull(id, "id");
        components = List.copyOf(components);
    }

    public static AspectDefinition primal(String id, int color) {
        return new AspectDefinition(id, color, List.of());
    }

    public static AspectDefinition compound(String id, int color, String first, String second) {
        return new AspectDefinition(id, color, List.of(first, second));
    }

    public boolean isPrimal() {
        return components.isEmpty();
    }
}
