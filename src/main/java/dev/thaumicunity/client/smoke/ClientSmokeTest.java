package dev.thaumicunity.client.smoke;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.client.screen.ThaumonomiconScreen;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.blockentity.ArcanePedestalBlockEntity;
import dev.thaumicunity.common.blockentity.CrucibleBlockEntity;
import dev.thaumicunity.common.blockentity.WardedJarBlockEntity;
import dev.thaumicunity.common.registry.TUBlocks;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.research.ResearchManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Development aid, off unless the JVM is started with {@code -Dthaumicunity.smokeTest=true} (Gradle task
 * {@code runSmokeClient}, used by CI under a virtual display). It creates a creative flat world, builds a workshop
 * with every device, fills crucible and jars, opens every screen, saves screenshots to {@code screenshots/} in the game
 * directory, writes {@code smoke-report.txt} and closes the game.
 */
public final class ClientSmokeTest {
    public static final String PROPERTY = "thaumicunity.smokeTest";
    private static final int TIMEOUT_TICKS = 20 * 60 * 8;

    private record Step(String name, int delayTicks, Runnable action) {
    }

    private static final List<Step> STEPS = new ArrayList<>();
    private static final List<String> REPORT = new ArrayList<>();
    private static int ticks;
    private static int nextStep;
    private static int waited;
    private static boolean worldRequested;
    private static boolean finished;
    private static BlockPos origin = BlockPos.ZERO;

    private ClientSmokeTest() {
    }

    public static boolean isEnabled() {
        return Boolean.getBoolean(PROPERTY);
    }

    public static void register() {
        ThaumicUnity.LOGGER.info("[smoke] client smoke test enabled");
        buildSteps();
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
    }

    // --- script

    private static void buildSteps() {
        STEPS.add(new Step("build workshop", 20, () -> onServer(ClientSmokeTest::buildWorkshop)));
        STEPS.add(new Step("screenshot workshop", 140, () -> screenshot("01-workshop")));
        STEPS.add(new Step("dissolve items in crucible", 60, () -> onServer(ClientSmokeTest::dissolve)));
        STEPS.add(new Step("look into crucible", 20, () -> onServer(player -> look(player, -4, 2.2, 1.2, 0, 55))));
        STEPS.add(new Step("screenshot crucible", 40, () -> screenshot("02-crucible-and-jars")));
        STEPS.add(new Step("open item gallery", 10, () -> setScreen(new SmokeGalleryScreen(SmokeGalleryScreen.Page.ITEMS))));
        STEPS.add(new Step("screenshot items", 20, () -> screenshot("03-items")));
        STEPS.add(new Step("open aspect gallery", 5, () -> setScreen(new SmokeGalleryScreen(SmokeGalleryScreen.Page.ASPECTS))));
        STEPS.add(new Step("screenshot aspects", 20, () -> screenshot("04-aspects")));
        book("05-thaumonomicon-map", "fundamentals", null);
        book("06-entry-crucible-recipes", "alchemy", "alchemy/alchemical_fuels");
        book("07-entry-arcane-recipe", "artifice", "artifice/goggles");
        book("08-entry-infusion-recipe", "infusion", "infusion/thaumium_caster");
        menu("09-arcane-workbench", -2);
        menu("10-research-table", 0);
        menu("11-alchemical-furnace", 2);
        STEPS.add(new Step("back to overview", 5, () -> {
            closeScreen();
            onServer(player -> look(player, 0, 1.5, -3, 0, 28));
        }));
        STEPS.add(new Step("screenshot overview", 60, () -> screenshot("12-workshop-later")));
        STEPS.add(new Step("finish", 60, ClientSmokeTest::finish));
    }

    private static void book(String shot, String category, String entry) {
        STEPS.add(new Step("open Thaumonomicon " + (entry == null ? category : entry), 5, () -> {
            closeScreen();
            ThaumonomiconScreen.bookmark(ThaumicUnity.id(category), entry == null ? null : ThaumicUnity.id(entry));
            setScreen(new ThaumonomiconScreen());
        }));
        STEPS.add(new Step("screenshot " + shot, 20, () -> screenshot(shot)));
    }

    private static void menu(String shot, int dx) {
        STEPS.add(new Step("open " + shot, 5, () -> {
            closeScreen();
            onServer(player -> {
                BlockPos pos = origin.offset(dx, 0, 3);
                ServerLevel level = (ServerLevel) player.level();
                level.getBlockState(pos).useWithoutItem(level, player,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
            });
        }));
        STEPS.add(new Step("screenshot " + shot, 20, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gui.screen() == null) {
                fail("screenshot " + shot, "no screen opened");
            }
            screenshot(shot);
        }));
    }

    // --- server side

    /** Workshop in front of the player: devices at z+3, an infusion altar at z+6, material blocks at z+9. */
    private static void buildWorkshop(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        for (String command : List.of("time set noon", "weather clear", "gamerule advance_time false",
                "gamerule advance_weather false", "gamerule spawn_mobs false")) {
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
        }
        origin = player.blockPosition();
        report("origin " + origin.toShortString());
        for (int x = -6; x <= 6; x++) {
            for (int z = -3; z <= 11; z++) {
                for (int y = 0; y <= 4; y++) {
                    level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        // Devices.
        level.setBlock(origin.offset(-4, -1, 3), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        place(level, -4, 0, 3, TUBlocks.CRUCIBLE.get());
        place(level, -2, 0, 3, TUBlocks.ARCANE_WORKBENCH.get());
        place(level, 0, 0, 3, TUBlocks.RESEARCH_TABLE.get());
        place(level, 2, 0, 3, TUBlocks.ALCHEMICAL_FURNACE.get());
        place(level, 4, 0, 3, TUBlocks.WARDED_JAR.get());
        place(level, 5, 0, 3, TUBlocks.WARDED_JAR.get());
        place(level, -5, 0, 3, TUBlocks.WARDED_JAR.get());
        if (level.getBlockEntity(origin.offset(-4, 0, 3)) instanceof CrucibleBlockEntity crucible) {
            crucible.fillWater();
        } else {
            fail("build workshop", "no crucible block entity");
        }
        fillJar(level, 4, Aspects.IGNIS, 200);
        fillJar(level, 5, Aspects.AQUA, 90);
        fillJar(level, -5, Aspects.PRAECANTATIO, 150);
        // Infusion altar: centre pedestal under the matrix, two side pedestals.
        place(level, 0, 0, 6, TUBlocks.ARCANE_PEDESTAL.get());
        place(level, -2, 0, 6, TUBlocks.ARCANE_PEDESTAL.get());
        place(level, 2, 0, 6, TUBlocks.ARCANE_PEDESTAL.get());
        place(level, 0, 2, 6, TUBlocks.RUNIC_MATRIX.get());
        pedestal(level, 0, TUItems.BASIC_CASTER.get());
        pedestal(level, -2, TUItems.THAUMIUM_INGOT.get());
        pedestal(level, 2, TUItems.AMBER.get());
        // Materials.
        List<Block> materials = List.of(TUBlocks.ARCANE_STONE.get(), TUBlocks.ARCANE_STONE_BRICKS.get(),
                TUBlocks.THAUMIUM_BLOCK.get(), TUBlocks.BRASS_BLOCK.get(), TUBlocks.AMBER_BLOCK.get(),
                TUBlocks.CINNABAR_ORE.get(), TUBlocks.AMBER_ORE.get());
        for (int i = 0; i < materials.size(); i++) {
            place(level, i - 3, 0, 9, materials.get(i));
        }
        // Player: all research, every item, thaumometer in hand for the aura display.
        ResearchManager.allIds(level.registryAccess()).forEach(id -> ResearchManager.setComplete(player, id, true));
        player.getInventory().clearContent();
        player.getInventory().setItem(0, new ItemStack(TUItems.THAUMOMETER.get()));
        for (Item item : BuiltInRegistries.ITEM) {
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(ThaumicUnity.MOD_ID) && item != TUItems.THAUMOMETER.get()) {
                player.getInventory().add(new ItemStack(item));
            }
        }
        player.getInventory().add(new ItemStack(Items.PAPER, 16));
        // Hover, so that camera positions hold.
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        look(player, 0, 1.5, -3, 0, 28);
    }

    private static void dissolve(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        BlockPos pos = origin.offset(-4, 0, 3);
        if (!(level.getBlockEntity(pos) instanceof CrucibleBlockEntity crucible)) {
            fail("dissolve", "no crucible block entity");
            return;
        }
        report("crucible heat " + crucible.heat() + ", water " + crucible.water() + ", boiling " + crucible.isBoiling());
        for (ItemStack stack : List.of(new ItemStack(Items.REDSTONE, 4), new ItemStack(Items.GLOWSTONE_DUST, 3),
                new ItemStack(Items.AMETHYST_SHARD, 2))) {
            ItemEntity entity = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, stack);
            level.addFreshEntity(entity);
            crucible.onItemEntered(level, entity);
        }
        report("crucible essentia " + crucible.essentia().asMap() + ", colour " + Integer.toHexString(crucible.color()));
        if (crucible.essentia().isEmpty()) {
            fail("dissolve", "nothing dissolved");
        }
    }

    private static void place(ServerLevel level, int dx, int dy, int dz, Block block) {
        level.setBlock(origin.offset(dx, dy, dz), block.defaultBlockState(), Block.UPDATE_ALL);
    }

    private static void fillJar(ServerLevel level, int dx, Identifier aspect, int amount) {
        if (level.getBlockEntity(origin.offset(dx, 0, 3)) instanceof WardedJarBlockEntity jar) {
            jar.add(aspect, amount);
        } else {
            fail("build workshop", "no jar block entity at x" + dx);
        }
    }

    private static void pedestal(ServerLevel level, int dx, Item item) {
        if (level.getBlockEntity(origin.offset(dx, 0, 6)) instanceof ArcanePedestalBlockEntity pedestal) {
            pedestal.setItem(new ItemStack(item));
        } else {
            fail("build workshop", "no pedestal block entity at x" + dx);
        }
    }

    /** Moves the player to origin + (dx, dy, dz) facing the given direction. */
    private static void look(ServerPlayer player, double dx, double dy, double dz, float yaw, float pitch) {
        player.teleportTo((ServerLevel) player.level(), origin.getX() + 0.5 + dx, origin.getY() + dy, origin.getZ() + 0.5 + dz,
                Set.of(), yaw, pitch, false);
    }

    /** Runs a task on the integrated server thread with the local player's server-side entity and waits for it. */
    private static void onServer(Consumer<ServerPlayer> task) {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (server == null || minecraft.player == null) {
            throw new IllegalStateException("no integrated server");
        }
        var id = minecraft.player.getUUID();
        try {
            CompletableFuture.runAsync(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player == null) {
                    throw new IllegalStateException("player not on server");
                }
                task.accept(player);
            }, server).get(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e.getCause() != null ? e.getCause() : e);
        }
    }

    // --- client side

    private static void tick() {
        if (finished) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ticks++;
        if (ticks > TIMEOUT_TICKS) {
            fail("timeout", "still at step " + nextStep + " after " + ticks + " ticks");
            finish();
            return;
        }
        if (!worldRequested) {
            if (minecraft.gui.overlay() == null && minecraft.level == null && minecraft.gui.screen() != null) {
                worldRequested = true;
                createWorld(minecraft);
            }
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        if (nextStep == 0 && waited == 0 && minecraft.gui.screen() != null) {
            // Still on the level loading screen.
            return;
        }
        if (nextStep >= STEPS.size()) {
            return;
        }
        Step step = STEPS.get(nextStep);
        if (++waited < step.delayTicks()) {
            return;
        }
        waited = 0;
        nextStep++;
        try {
            step.action().run();
            if (!step.name().startsWith("screenshot") && !step.name().equals("finish")) {
                report("ok   " + step.name());
            }
        } catch (RuntimeException e) {
            ThaumicUnity.LOGGER.error("[smoke] step '{}' failed", step.name(), e);
            fail(step.name(), e.toString());
        }
    }

    private static void createWorld(Minecraft minecraft) {
        String name = "smoke-" + System.currentTimeMillis();
        report("creating world " + name);
        LevelSettings settings = new LevelSettings(name, GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(20260314L, false, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value()
                        .createWorldDimensions(),
                minecraft.gui.screen());
    }

    private static void setScreen(net.minecraft.client.gui.screens.Screen screen) {
        Minecraft.getInstance().gui.setScreen(screen);
    }

    private static void closeScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu != minecraft.player.inventoryMenu) {
            minecraft.player.closeContainer();
        }
        minecraft.gui.setScreen(null);
    }

    private static void screenshot(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        String screen = minecraft.gui.screen() == null ? "world" : minecraft.gui.screen().getClass().getSimpleName();
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.gameRenderer.mainRenderTarget(), 1,
                message -> ThaumicUnity.LOGGER.info("[smoke] {}", message.getString()));
        report("shot " + name + " (" + screen + ")");
    }

    private static void finish() {
        finished = true;
        long failures = REPORT.stream().filter(line -> line.startsWith("FAIL")).count();
        report(failures == 0 ? "RESULT ok" : "RESULT " + failures + " failure(s)");
        try {
            Path file = Minecraft.getInstance().gameDirectory.toPath().resolve("smoke-report.txt");
            Files.write(file, REPORT, StandardCharsets.UTF_8);
        } catch (IOException e) {
            ThaumicUnity.LOGGER.error("[smoke] could not write report", e);
        }
        Minecraft.getInstance().stop();
    }

    private static void report(String line) {
        ThaumicUnity.LOGGER.info("[smoke] {}", line);
        REPORT.add(line);
    }

    private static void fail(String step, String message) {
        report("FAIL " + step + ": " + message);
    }
}
