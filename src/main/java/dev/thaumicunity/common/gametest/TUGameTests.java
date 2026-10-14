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
import dev.thaumicunity.core.aspect.AspectGraph;
import dev.thaumicunity.core.research.InsightPuzzle;
import dev.thaumicunity.core.spell.CompiledSpell;
import dev.thaumicunity.core.spell.SpellNode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
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
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(TUItems.SALIS_MUNDUS.get()));
        helper.useItemOnBlock(player, TUItems.SALIS_MUNDUS.get(), table);
        helper.succeedWhen(() -> helper.assertBlockPresent(TUBlocks.ARCANE_WORKBENCH.get(), table));
    }
}
