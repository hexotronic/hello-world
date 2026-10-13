package dev.thaumicunity.api.spell;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Read-only view of the {@code params} object of a spell component's JSON. Every getter takes a default so that
 * datapack authors only have to write the values they want to change.
 */
public final class SpellParams {
    public static final SpellParams EMPTY = new SpellParams(new JsonObject());

    private final JsonObject json;

    public SpellParams(JsonObject json) {
        this.json = json;
    }

    public static SpellParams of(JsonElement element) {
        return element != null && element.isJsonObject() ? new SpellParams(element.getAsJsonObject()) : EMPTY;
    }

    public boolean has(String key) {
        return json.has(key);
    }

    public double getDouble(String key, double fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsDouble() : fallback;
    }

    public float getFloat(String key, float fallback) {
        return (float) getDouble(key, fallback);
    }

    public int getInt(String key, int fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber() ? e.getAsInt() : fallback;
    }

    public boolean getBoolean(String key, boolean fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isBoolean() ? e.getAsBoolean() : fallback;
    }

    public String getString(String key, String fallback) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    public Optional<Identifier> getId(String key) {
        JsonElement e = json.get(key);
        return e != null && e.isJsonPrimitive() ? Optional.ofNullable(Identifier.tryParse(e.getAsString())) : Optional.empty();
    }

    public JsonObject raw() {
        return json.deepCopy();
    }
}
