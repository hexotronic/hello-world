package dev.thaumicunity.client.thaumcraft;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.config.TUClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Dresses Thaumic Unity in Thaumcraft's own textures for players who own Thaumcraft.
 *
 * <p>Put a Thaumcraft jar (Thaumcraft 4 for 1.7.10 or Thaumcraft 6 for 1.12.2) into {@code <game dir>/thaumicunity/}.
 * At start-up the client converts its textures into a resource pack in {@code <game dir>/thaumicunity/thaumcraft-pack}
 * (rebuilt only when the jar changes) and adds it on top of Thaumic Unity's own art. Nothing is downloaded and no
 * Thaumcraft file is part of Thaumic Unity: the pack is made on the player's computer from the player's jar.</p>
 *
 * <p>Which file dresses which texture comes from {@code assets/thaumicunity/thaumcraft_import.json} (generated from
 * {@code tools/content/thaumcraft_import.py}, shared with {@code tools/import_thaumcraft_textures.py}): known file
 * names first, then any texture of the same kind whose name contains every word of the Thaumic Unity name. Grey
 * aspect glyphs and vis crystals are tinted with their aspect's colour, as Thaumcraft does in code.</p>
 */
public final class ThaumcraftTextures {
    public static final String FOLDER = "thaumicunity";
    private static final String PACK_DIR = "thaumcraft-pack";
    private static final String SOURCE_PREFIX = "assets/thaumcraft/textures/";
    private static final String TARGET_PREFIX = "assets/thaumicunity/textures/";

    private ThaumcraftTextures() {
    }

    /** Mod bus listener: adds the generated pack to the client resource packs when a Thaumcraft jar is present. */
    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !enabled()) {
            return;
        }
        try {
            Path folder = gameDirectory().resolve(FOLDER);
            Files.createDirectories(folder);
            writeReadme(folder);
            Optional<Path> source = findSource(folder);
            if (source.isEmpty()) {
                return;
            }
            Path pack = folder.resolve(PACK_DIR);
            int count = buildIfChanged(source.get(), pack);
            ThaumicUnity.LOGGER.info("Using Thaumcraft textures from {} ({} textures)", source.get().getFileName(), count);
            PackLocationInfo info = new PackLocationInfo("thaumicunity_thaumcraft_textures",
                    Component.translatable("pack.thaumicunity.thaumcraft_textures"), PackSource.BUILT_IN, Optional.empty());
            event.addRepositorySource(consumer -> {
                Pack created = Pack.readMetaAndCreate(info, new PathPackResources.PathResourcesSupplier(pack),
                        PackType.CLIENT_RESOURCES, new PackSelectionConfig(true, Pack.Position.TOP, false));
                if (created != null) {
                    consumer.accept(created);
                }
            });
        } catch (IOException | RuntimeException e) {
            ThaumicUnity.LOGGER.error("Could not build the Thaumcraft texture pack; using Thaumic Unity's own art", e);
        }
    }

    private static boolean enabled() {
        try {
            return TUClientConfig.USE_THAUMCRAFT_TEXTURES.get();
        } catch (IllegalStateException notLoadedYet) {
            return true;
        }
    }

    private static Path gameDirectory() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null ? minecraft.gameDirectory.toPath() : Path.of(".");
    }

    private static void writeReadme(Path folder) throws IOException {
        Path readme = folder.resolve("README.txt");
        if (!Files.exists(readme)) {
            Files.writeString(readme, """
                    Put your own Thaumcraft jar (Thaumcraft 4 for Minecraft 1.7.10 or Thaumcraft 6 for 1.12.2) in this
                    folder and restart the game: Thaumic Unity will use Thaumcraft's textures. They are converted into
                    the thaumcraft-pack folder on this computer only. Thaumcraft's art is All Rights Reserved by its
                    author; keep the jar and the pack to yourself. Turn this off with useThaumcraftTextures in
                    config/thaumicunity-client.toml.
                    """, StandardCharsets.UTF_8);
        }
    }

    /** The first jar or zip in the folder (by name) that contains Thaumcraft textures. */
    static Optional<Path> findSource(Path folder) throws IOException {
        List<Path> candidates;
        try (Stream<Path> files = Files.list(folder)) {
            candidates = files.filter(p -> {
                String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                return Files.isRegularFile(p) && (name.endsWith(".jar") || name.endsWith(".zip"));
            }).sorted(Comparator.comparing(p -> p.getFileName().toString())).toList();
        }
        for (Path candidate : candidates) {
            try (ZipFile zip = new ZipFile(candidate.toFile())) {
                if (zip.stream().anyMatch(e -> e.getName().startsWith(SOURCE_PREFIX) && e.getName().endsWith(".png"))) {
                    return Optional.of(candidate);
                }
            } catch (IOException notAZip) {
                ThaumicUnity.LOGGER.warn("Ignoring {}: not a readable jar", candidate.getFileName());
            }
        }
        return Optional.empty();
    }

    /** Builds the pack unless it was already built from this very jar with this mapping; returns the texture count. */
    static int buildIfChanged(Path source, Path pack) throws IOException {
        JsonObject mapping = loadMapping();
        String stamp = source.getFileName() + "|" + Files.size(source) + "|" + Files.getLastModifiedTime(source).toMillis()
                + "|" + mapping.get("version").getAsInt() + "|" + mapping.hashCode();
        Path marker = pack.resolve("source.txt");
        if (Files.exists(marker) && Files.readString(marker, StandardCharsets.UTF_8).startsWith(stamp)) {
            try (Stream<Path> files = Files.walk(pack)) {
                return (int) files.filter(p -> p.toString().endsWith(".png")).count();
            }
        }
        deleteTree(pack);
        Files.createDirectories(pack);
        int count = build(source, pack, mapping);
        Files.writeString(marker, stamp + "\n" + count + " textures\n", StandardCharsets.UTF_8);
        return count;
    }

    private static JsonObject loadMapping() throws IOException {
        try (InputStream in = ThaumcraftTextures.class.getResourceAsStream("/assets/thaumicunity/thaumcraft_import.json")) {
            if (in == null) {
                throw new IOException("thaumcraft_import.json is missing from the mod");
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static int build(Path source, Path pack, JsonObject mapping) throws IOException {
        Files.writeString(pack.resolve("pack.mcmeta"), """
                {"pack": {"description": {"translate": "pack.thaumicunity.thaumcraft_textures.description"}, \
                "min_format": %d, "max_format": %d}}
                """.formatted(mapping.get("pack_format").getAsInt(), mapping.get("pack_format").getAsInt()),
                StandardCharsets.UTF_8);
        Set<String> noise = new TreeSet<>();
        mapping.getAsJsonArray("noise").forEach(e -> noise.add(e.getAsString()));
        int count = 0;
        try (ZipFile zip = new ZipFile(source.toFile())) {
            List<String> pngs = new ArrayList<>();
            zip.stream().map(ZipEntry::getName)
                    .filter(n -> n.startsWith(SOURCE_PREFIX) && n.endsWith(".png"))
                    .map(n -> n.substring(SOURCE_PREFIX.length()))
                    .sorted()
                    .forEach(pngs::add);
            Set<String> available = new TreeSet<>(pngs);

            Map<String, String> plain = new TreeMap<>();
            for (Map.Entry<String, JsonElement> e : mapping.getAsJsonObject("known").entrySet()) {
                String found = null;
                for (JsonElement candidate : e.getValue().getAsJsonArray()) {
                    if (available.contains(candidate.getAsString())) {
                        found = candidate.getAsString();
                        break;
                    }
                }
                if (found == null) {
                    found = fuzzy(e.getKey(), pngs, noise);
                }
                if (found != null) {
                    plain.put(e.getKey(), found);
                }
            }
            for (Map.Entry<String, String> e : plain.entrySet()) {
                copy(zip, e.getValue(), pack, e.getKey(), null);
                count++;
            }
            for (Map.Entry<String, JsonElement> e : mapping.getAsJsonObject("tinted").entrySet()) {
                JsonObject spec = e.getValue().getAsJsonObject();
                for (JsonElement candidate : spec.getAsJsonArray("candidates")) {
                    if (available.contains(candidate.getAsString())) {
                        copy(zip, candidate.getAsString(), pack, e.getKey(), color(spec.get("color").getAsString()));
                        count++;
                        break;
                    }
                }
            }
            for (Map.Entry<String, JsonElement> e : mapping.getAsJsonObject("aspects").entrySet()) {
                String glyph = "aspects/" + e.getKey() + ".png";
                if (available.contains(glyph)) {
                    copy(zip, glyph, pack, "aspect/" + e.getKey(), color(e.getValue().getAsString()));
                    count++;
                }
            }
        }
        return count;
    }

    private static int color(String hex) {
        return Integer.parseInt(hex.substring(1), 16);
    }

    /**
     * Shortest texture of the same kind (item or block) whose file name contains every word of the target's name,
     * as in {@code tools/import_thaumcraft_textures.py}.
     */
    static @Nullable String fuzzy(String target, List<String> pngs, Set<String> noise) {
        int slash = target.indexOf('/');
        String kind = target.substring(0, slash);
        if (!kind.equals("item") && !kind.equals("block")) {
            return null;
        }
        List<String> words = new ArrayList<>();
        for (String word : target.substring(slash + 1).toLowerCase(Locale.ROOT).split("[_/\\-.]+")) {
            if (!word.isEmpty() && !noise.contains(word)) {
                words.add(word);
            }
        }
        if (words.isEmpty()) {
            return null;
        }
        String best = null;
        for (String path : pngs) {
            if (!path.startsWith(kind + "/") && !path.startsWith(kind + "s/")) {
                continue;
            }
            String base = path.substring(path.lastIndexOf('/') + 1);
            String stem = base.substring(0, base.length() - 4).toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            if (words.stream().allMatch(stem::contains)) {
                String bestBase = best == null ? null : best.substring(best.lastIndexOf('/') + 1);
                if (best == null || base.length() < bestBase.length() || (base.length() == bestBase.length() && path.compareTo(best) < 0)) {
                    best = path;
                }
            }
        }
        return best;
    }

    /** Copies one texture (and its animation file), tinting grey artwork when a colour is given. */
    private static void copy(ZipFile zip, String from, Path pack, String to, @Nullable Integer tint) throws IOException {
        ZipEntry entry = zip.getEntry(SOURCE_PREFIX + from);
        Path out = pack.resolve(TARGET_PREFIX + to + ".png");
        Files.createDirectories(out.getParent());
        try (InputStream in = zip.getInputStream(entry)) {
            byte[] bytes = in.readAllBytes();
            if (tint == null) {
                Files.write(out, bytes);
            } else {
                try (NativeImage image = NativeImage.read(bytes)) {
                    if (isGrey(image)) {
                        tint(image, tint);
                    }
                    image.writeToFile(out);
                }
            }
        }
        ZipEntry meta = zip.getEntry(SOURCE_PREFIX + from + ".mcmeta");
        if (meta != null) {
            try (InputStream in = zip.getInputStream(meta)) {
                Files.write(pack.resolve(TARGET_PREFIX + to + ".png.mcmeta"), in.readAllBytes());
            }
        }
    }

    private static boolean isGrey(NativeImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getPixel(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if ((argb >>> 24) != 0 && (Math.abs(r - g) > 12 || Math.abs(g - b) > 12)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Multiplies grey artwork by a colour, keeping alpha. */
    private static void tint(NativeImage image, int rgb) {
        int tr = (rgb >> 16) & 0xFF;
        int tg = (rgb >> 8) & 0xFF;
        int tb = rgb & 0xFF;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getPixel(x, y);
                double grey = (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF)) / 3.0 / 255.0;
                int a = argb >>> 24;
                image.setPixel(x, y, (a << 24) | ((int) (tr * grey) << 16) | ((int) (tg * grey) << 8) | (int) (tb * grey));
            }
        }
    }

    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> files = Files.walk(root)) {
            for (Path p : files.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
