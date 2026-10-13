package dev.thaumicunity.common.research;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * Something that can be scanned with a thaumometer. Serialised as a short string so research JSON stays readable:
 * {@code "item:minecraft:cauldron"}, {@code "block:minecraft:bookshelf"}, {@code "entity:minecraft:zombie"},
 * {@code "aspect:thaumicunity:ignis"} (any scan revealing that aspect) or {@code "item_tag:c:ingots"}.
 */
public record ScanTarget(Kind kind, Identifier id) {
    public enum Kind {
        ITEM, BLOCK, ENTITY, ASPECT, ITEM_TAG;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<ScanTarget> CODEC = Codec.STRING.comapFlatMap(ScanTarget::parse, ScanTarget::serialize);

    public static DataResult<ScanTarget> parse(String text) {
        int colon = text.indexOf(':');
        if (colon <= 0) {
            return DataResult.error(() -> "Scan target must look like kind:namespace:path, got " + text);
        }
        String kindText = text.substring(0, colon);
        Kind kind = null;
        for (Kind k : Kind.values()) {
            if (k.key().equals(kindText)) {
                kind = k;
            }
        }
        if (kind == null) {
            return DataResult.error(() -> "Unknown scan target kind " + kindText);
        }
        Identifier id = Identifier.tryParse(text.substring(colon + 1));
        if (id == null) {
            return DataResult.error(() -> "Invalid id in scan target " + text);
        }
        return DataResult.success(new ScanTarget(kind, id));
    }

    public String serialize() {
        return kind.key() + ":" + id;
    }

    /** Key stored in a player's scan record. */
    public static String scanKey(Kind kind, Identifier id) {
        return kind.key() + ":" + id;
    }
}
