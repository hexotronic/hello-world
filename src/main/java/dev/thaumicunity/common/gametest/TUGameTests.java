package dev.thaumicunity.common.gametest;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.aspect.AspectList;
import dev.thaumicunity.common.aspect.Aspects;
import dev.thaumicunity.common.aspect.ItemAspects;
import dev.thaumicunity.common.config.TUConfig;
import dev.thaumicunity.common.item.FocusItem;
import dev.thaumicunity.common.recipe.ArcaneCraftingRecipe;
import dev.thaumicunity.common.recipe.CrucibleInput;
import dev.thaumicunity.common.recipe.InfusionInput;
import dev.thaumicunity.common.registry.TUBlocks;
import dev.thaumicunity.common.registry.TUDataComponents;
import dev.thaumicunity.common.registry.TUDataMaps;
import dev.thaumicunity.common.registry.TUItems;
import dev.thaumicunity.common.registry.TURecipes;
import dev.thaumicunity.common.registry.TURegistries;
import dev.thaumicunity.common.research.ResearchEntry;
import dev.thaumicunity.common.research.ResearchRequirement;
import dev.thaumicunity.common.research.ResearchStage;
import dev.thaumicunity.common.spell.SpellComponent;
import dev.thaumicunity.common.spell.SpellLibrary;
import dev.thaumicunity.common.blockentity.WardedJarBlockEntity;
import dev.thaumicunity.common.wand.WandCap;
import dev.thaumicunity.common.wand.WandFocus;
import dev.thaumicunity.common.wand.WandParts;
import dev.thaumicunity.common.wand.WandRod;
import dev.thaumicunity.common.wand.WandVis;
import dev.thaumicunity.common.wand.Wands;
import dev.thaumicunity.core.aspect.AspectAmounts;
import dev.thaumicunity.core.aspect.AspectGraph;
import dev.thaumicunity.core.research.InsightPuzzle;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * In-game tests, run by {@code ./gradlew runGameTestServer} (and in CI). The test instances are data
 * ({@code data/thaumicunity/test_instance/}); this class registers the functions they call. Together they prove that
 * every generated data file loads and that the main systems work on a real server.
 */
public final class TUGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
            DeferredRegister.create(Registries.TEST_FUNCTION, ThaumicUnity.MOD_ID);

    static {
        test("registries_loaded", TUGameTests::registriesLoaded);
        test("foci_compile", TUGameTests::fociCompile);
        test("item_aspects", TUGameTests::itemAspects);
        test("crucible_and_infusion", TUGameTests::crucibleAndInfusion);
        test("world_data", TUGameTests::worldData);
        test("insight_puzzles_solvable", TUGameTests::insightPuzzlesSolvable);
        test("salis_mundus", TUGameTests::salisMundus);
        test("wands", TUGameTests::wands);
        test("essentia_transport", TUGameTests::essentiaTransport);
    }

    private TUGameTests() {
    }

    private static void test(String name, Consumer<GameTestHelper> function) {
        FUNCTIONS.register(name, () -> function);
    }

    public static void init(IEventBus modBus) {
        FUNCTIONS.register(modBus);
    }

    private static <T> Registry<T> registry(GameTestHelper helper, ResourceKey<Registry<T>> key) {
        return helper.getLevel().registryAccess().lookup(key).orElseThrow(() -> helper.assertionException("Missing registry " + key));
    }

    private static RecipeManager recipes(GameTestHelper helper) {
        return helper.getLevel().getServer().getRecipeManager();
    }

    private static void registriesLoaded(GameTestHelper helper) {
        helper.assertTrue(registry(helper, TURegistries.ASPECT).size() >= 183, "expected at least 183 aspects");
        helper.assertTrue(registry(helper, TURegistries.RESEARCH_CATEGORY).size() >= 5, "expected at least 5 research categories");
        Registry<ResearchEntry> research = registry(helper, TURegistries.RESEARCH);
        helper.assertTrue(research.size() >= 26, "expected at least 26 research entries");
        helper.assertTrue(registry(helper, TURegistries.SPELL_COMPONENT).size() >= 80, "expected at least 80 spell components");
        RecipeManager recipes = recipes(helper);
        research.entrySet().forEach(e -> {
            Identifier id = e.getKey().identifier();
            ResearchEntry entry = e.getValue();
            helper.assertTrue(registry(helper, TURegistries.RESEARCH_CATEGORY).containsKey(entry.category()),
                    id + " has unknown category " + entry.category());
            for (Identifier parent : entry.parents()) {
                helper.assertTrue(research.containsKey(parent), id + " has unknown parent " + parent);
            }
            for (ResearchStage stage : entry.stages()) {
                for (Identifier recipe : stage.recipes()) {
                    helper.assertTrue(recipes.byKey(ResourceKey.create(Registries.RECIPE, recipe)).isPresent(),
                            id + " shows missing recipe " + recipe);
                }
            }
        });
        helper.succeed();
    }

    private static void fociCompile(GameTestHelper helper) {
        RegistryAccess access = helper.getLevel().registryAccess();
        SpellLibrary library = SpellLibrary.get(access);
        for (Map.Entry<Identifier, SpellComponent> e : library.all().entrySet()) {
            helper.assertTrue(TURegistries.SPELL_PRIMITIVES.containsKey(e.getValue().primitive()),
                    e.getKey() + " uses unknown primitive " + e.getValue().primitive());
        }
        int foci = 0;
        for (RecipeHolder<?> holder : recipes(helper).getRecipes()) {
            if (!(holder.value() instanceof ArcaneCraftingRecipe arcane)) {
                continue;
            }
            ItemStack result = arcane.result().create();
            SpellNode node = result.get(TUDataComponents.SPELL.get());
            if (node == null || !(result.getItem() instanceof FocusItem focus)) {
                continue;
            }
            CompiledSpell spell = library.compile(node, focus.limits());
            helper.assertTrue(spell.isValid(), holder.id().identifier() + " makes an invalid spell: " + spell.errors());
            foci++;
        }
        helper.assertTrue(foci >= 11, "expected at least 11 focus recipes, found " + foci);
        helper.succeed();
    }

    private static void itemAspects(GameTestHelper helper) {
        AspectList iron = ItemAspects.of(new ItemStack(Items.IRON_INGOT), false);
        helper.assertTrue(iron.get(ThaumicUnity.id("metallum")) > 0, "iron ingot should contain metallum: " + iron);
        AspectList log = ItemAspects.of(new ItemStack(Items.OAK_LOG), false);
        helper.assertTrue(log.get(ThaumicUnity.id("arbor")) > 0, "oak log should get arbor from its tag rule: " + log);
        AspectList pickaxe = ItemAspects.of(new ItemStack(Items.IRON_PICKAXE), false);
        helper.assertFalse(pickaxe.isEmpty(), "an iron pickaxe should inherit aspects from its recipe");
        AspectList thaumium = ItemAspects.of(new ItemStack(TUItems.THAUMIUM_INGOT.get()), false);
        helper.assertTrue(thaumium.get(Aspects.PRAECANTATIO) > 0, "thaumium should contain praecantatio: " + thaumium);
        helper.succeed();
    }

    private static void crucibleAndInfusion(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CrucibleInput crucible = new CrucibleInput(new ItemStack(Items.IRON_INGOT),
                AspectList.of(Map.of(Aspects.PRAECANTATIO, 4, Aspects.TERRA, 2)));
        var crucibleMatch = level.recipeAccess().getRecipeFor(TURecipes.CRUCIBLE.get(), crucible, level);
        helper.assertTrue(crucibleMatch.isPresent(), "iron + praecantatio 4 + terra 2 should match a crucible recipe");
        helper.assertTrue(crucibleMatch.get().value().assemble(crucible).is(TUItems.THAUMIUM_INGOT.get()),
                "the crucible recipe should make thaumium");

        ItemStack thaumium = new ItemStack(TUItems.THAUMIUM_INGOT.get());
        InfusionInput infusion = new InfusionInput(new ItemStack(TUItems.BASIC_CASTER.get()),
                List.of(thaumium.copy(), thaumium.copy(), thaumium.copy(), thaumium.copy()));
        var infusionMatch = level.recipeAccess().getRecipeFor(TURecipes.INFUSION.get(), infusion, level);
        helper.assertTrue(infusionMatch.isPresent(), "a caster ringed by four thaumium should match an infusion recipe");
        helper.assertTrue(infusionMatch.get().value().assemble(infusion).is(TUItems.THAUMIUM_CASTER.get()),
                "the infusion should make a thaumium caster");
        helper.succeed();
    }

    private static void worldData(GameTestHelper helper) {
        var plains = registry(helper, Registries.BIOME).get(Biomes.PLAINS);
        helper.assertTrue(plains.isPresent() && plains.get().getData(TUDataMaps.AURA_PROFILE) != null,
                "plains should have an aura profile");
        Integer coal = Items.COAL.builtInRegistryHolder().getData(TUDataMaps.ALCHEMICAL_FUEL);
        helper.assertTrue(coal != null && coal > 0, "coal should be an alchemical fuel");
        Float candle = Blocks.CANDLE.builtInRegistryHolder().getData(TUDataMaps.INFUSION_STABILIZER);
        helper.assertTrue(candle != null && candle > 0, "candles should stabilise infusions");
        helper.assertTrue(registry(helper, Registries.PLACED_FEATURE).containsKey(ThaumicUnity.id("ore_cinnabar")),
                "cinnabar ore should be a placed feature");
        helper.assertTrue(registry(helper, Registries.PLACED_FEATURE).containsKey(ThaumicUnity.id("ore_amber")),
                "amber ore should be a placed feature");
        helper.succeed();
    }

    private static void insightPuzzlesSolvable(GameTestHelper helper) {
        AspectGraph graph = Aspects.graph(helper.getLevel().registryAccess());
        int puzzles = 0;
        for (var e : registry(helper, TURegistries.RESEARCH).entrySet()) {
            for (ResearchStage stage : e.getValue().stages()) {
                for (ResearchRequirement requirement : stage.requirements()) {
                    if (requirement instanceof ResearchRequirement.Insight insight) {
                        List<String> anchors = insight.aspects().stream().map(Identifier::toString).toList();
                        InsightPuzzle puzzle = InsightPuzzle.generate(anchors, TUConfig.INSIGHT_BOARD_RADIUS.get(), 2, 1L);
                        helper.assertTrue(puzzle.isSolvable(graph), e.getKey().identifier() + " has an unsolvable puzzle " + anchors);
                        puzzles++;
                    }
                }
            }
        }
        helper.assertTrue(puzzles > 0, "expected insight puzzles in the research tree");
        helper.succeed();
    }

    private static void salisMundus(GameTestHelper helper) {
        BlockPos table = new BlockPos(0, 1, 0);
        helper.setBlock(table, Blocks.CRAFTING_TABLE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack salis = new ItemStack(TUItems.SALIS_MUNDUS.get(), 2);
        player.setItemInHand(InteractionHand.MAIN_HAND, salis);
        BlockPos absolute = helper.absolutePos(table);
        UseOnContext context = new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        InteractionResult result = TUItems.SALIS_MUNDUS.get().useOn(context);
        helper.assertTrue(result.consumesAction(), "Salis Mundus should act on a crafting table, got " + result);
        helper.assertBlockPresent(TUBlocks.ARCANE_WORKBENCH.get(), table);
        helper.assertValueEqual(salis.getCount(), 1, "Salis Mundus should be used up");
        helper.succeed();
    }

    private static void wands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RegistryAccess access = level.registryAccess();
        Registry<WandRod> rods = registry(helper, TURegistries.WAND_ROD);
        Registry<WandCap> caps = registry(helper, TURegistries.WAND_CAP);
        helper.assertTrue(rods.size() >= 7 && caps.size() >= 5, "expected at least 7 rods and 5 caps");
        rods.entrySet().forEach(e -> helper.assertTrue(BuiltInRegistries.ITEM.containsKey(e.getValue().item()),
                "rod " + e.getKey().identifier() + " names a missing item"));
        caps.entrySet().forEach(e -> helper.assertTrue(BuiltInRegistries.ITEM.containsKey(e.getValue().item()),
                "cap " + e.getKey().identifier() + " names a missing item"));
        // One assembly recipe per rod and cap, each making a wand of known parts.
        int assemblies = 0;
        for (RecipeHolder<?> holder : recipes(helper).getRecipes()) {
            if (holder.value() instanceof ArcaneCraftingRecipe arcane && arcane.result().create().is(TUItems.WAND.get())) {
                WandParts parts = arcane.result().create().get(TUDataComponents.WAND_PARTS.get());
                helper.assertTrue(parts != null && rods.containsKey(parts.rod()) && caps.containsKey(parts.cap()),
                        holder.id().identifier() + " makes a wand of unknown parts: " + parts);
                assemblies++;
            }
        }
        helper.assertValueEqual(assemblies, rods.size() * caps.size(), "wand assembly recipes");

        // Costs: iron caps make spells a tenth dearer; a blaze rod spares a tenth of its Ignis.
        ItemStack wooden = Wands.create(access, ThaumicUnity.id("wood"), ThaumicUnity.id("iron"));
        helper.assertValueEqual(Wands.capacity(access, wooden), 30 * WandVis.SCALE, "wooden rod capacity");
        AspectAmounts fire = AspectAmounts.of(Aspects.IGNIS.toString(), 10);
        int ignis = Aspects.PRIMALS.indexOf(Aspects.IGNIS);
        helper.assertValueEqual(Wands.cost(access, wooden, fire)[ignis], 1100, "Ignis cost with iron caps");
        ItemStack blazing = Wands.create(access, ThaumicUnity.id("blaze"), ThaumicUnity.id("gold"));
        helper.assertValueEqual(Wands.cost(access, blazing, fire)[ignis], 900, "Ignis cost with a blaze rod and gold caps");

        // Paying: from the wand first, then from vis crystals in the inventory; all or nothing.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        wooden.set(TUDataComponents.WAND_VIS.get(), WandVis.EMPTY.withCapacity(3000).with(ignis, 500));
        int[] cost = Wands.cost(access, wooden, fire);
        helper.assertFalse(Wands.pay(player, wooden, cost), "a wand with 5 Ignis and no crystals cannot pay 11");
        helper.assertValueEqual(Wands.vis(wooden).get(ignis), 500, "a failed payment must not take vis");
        player.getInventory().add(new ItemStack(TUItems.VIS_CRYSTALS.get("ignis").get(), 3));
        helper.assertTrue(Wands.pay(player, wooden, cost), "one Ignis crystal should make up the shortfall");
        helper.assertValueEqual(Wands.vis(wooden).get(ignis), 500 + Wands.crystalValue() - 1100, "Ignis left after paying");
        helper.assertValueEqual(player.getInventory().countItem(TUItems.VIS_CRYSTALS.get("ignis").get()), 2, "crystals left");

        // Recharging from the aura fills primals that are not full and drains the aura by as much.
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        var aura = dev.thaumicunity.common.aura.AuraManager.auraAt(level, at);
        aura.setVis(aura.base() * 2 + 50);
        double before = aura.vis();
        double moved = Wands.rechargeFromAura(level, at, wooden, 6.0, 0.25);
        helper.assertTrue(moved > 0, "a wand below capacity should draw vis from a full aura");
        helper.assertTrue(Math.abs(before - moved - dev.thaumicunity.common.aura.AuraManager.auraAt(level, at).vis()) < 1.0e-6,
                "the aura should lose exactly what the wand gained");

        // Fixing a focus: sneak-use with a focus in the other hand.
        ItemStack focus = new ItemStack(TUItems.APPRENTICE_FOCUS.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, wooden);
        player.setItemInHand(InteractionHand.OFF_HAND, focus);
        player.setShiftKeyDown(true);
        TUItems.WAND.get().use(level, player, InteractionHand.MAIN_HAND);
        WandFocus fixed = wooden.get(TUDataComponents.WAND_FOCUS.get());
        helper.assertTrue(fixed != null && fixed.stack().is(TUItems.APPRENTICE_FOCUS.get()), "the focus should be fixed to the wand");
        helper.assertTrue(player.getItemInHand(InteractionHand.OFF_HAND).isEmpty(), "the focus should leave the other hand");
        helper.succeed();
    }

    private static WardedJarBlockEntity jar(GameTestHelper helper, BlockPos pos) {
        if (helper.getLevel().getBlockEntity(helper.absolutePos(pos)) instanceof WardedJarBlockEntity jar) {
            return jar;
        }
        throw helper.assertionException("no warded jar at " + pos);
    }

    /**
     * A labelled jar draws its aspect out of an unlabelled jar through three tubes; a centrifuge fed from a jar of
     * Lux splits it and a jar below catches one of the parts.
     */
    private static void essentiaTransport(GameTestHelper helper) {
        BlockPos source = new BlockPos(0, 1, 0);
        BlockPos sink = new BlockPos(2, 1, 0);
        helper.setBlock(source, TUBlocks.WARDED_JAR.get());
        helper.setBlock(sink, TUBlocks.WARDED_JAR.get());
        for (int x = 0; x <= 2; x++) {
            helper.setBlock(new BlockPos(x, 2, 0), TUBlocks.ESSENTIA_TUBE.get());
        }
        jar(helper, source).add(Aspects.IGNIS, 20);
        jar(helper, sink).setFilter(Aspects.IGNIS);

        Identifier lux = ThaumicUnity.id("lux");
        List<Identifier> parts = dev.thaumicunity.common.aspect.Aspects.get(helper.getLevel().registryAccess(), lux)
                .orElseThrow(() -> helper.assertionException("no Lux aspect")).components();
        BlockPos luxJar = new BlockPos(4, 1, 0);
        BlockPos centrifuge = new BlockPos(6, 1, 0);
        BlockPos catcher = new BlockPos(6, 0, 0);
        helper.setBlock(luxJar, TUBlocks.WARDED_JAR.get());
        for (int x = 4; x <= 6; x++) {
            helper.setBlock(new BlockPos(x, 2, 0), TUBlocks.ESSENTIA_TUBE.get());
        }
        helper.setBlock(centrifuge, TUBlocks.CENTRIFUGE.get());
        helper.setBlock(catcher, TUBlocks.WARDED_JAR.get());
        jar(helper, luxJar).add(lux, 10);

        helper.succeedWhen(() -> {
            helper.assertTrue(jar(helper, sink).amount() >= 3, "the labelled jar should have drawn Ignis, has " + jar(helper, sink).amount());
            WardedJarBlockEntity caught = jar(helper, catcher);
            helper.assertTrue(caught.amount() > 0 && parts.contains(caught.aspect()),
                    "the jar under the centrifuge should hold a part of Lux " + parts + ", has " + caught.contents());
        });
    }
}
