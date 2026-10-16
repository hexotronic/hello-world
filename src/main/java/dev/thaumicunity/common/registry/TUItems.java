package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import dev.thaumicunity.common.item.CasterItem;
import dev.thaumicunity.common.item.FocusItem;
import dev.thaumicunity.common.item.PhialItem;
import dev.thaumicunity.common.item.ResearchNotesItem;
import dev.thaumicunity.common.item.SalisMundusItem;
import dev.thaumicunity.common.item.ThaumometerItem;
import dev.thaumicunity.common.item.ThaumonomiconItem;
import dev.thaumicunity.common.item.WandItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Items (phase 3 set). */
public final class TUItems {
    static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ThaumicUnity.MOD_ID);

    // --- tools of research
    public static final DeferredItem<ThaumonomiconItem> THAUMONOMICON = ITEMS.registerItem("thaumonomicon", ThaumonomiconItem::new,
            p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<ThaumometerItem> THAUMOMETER = ITEMS.registerItem("thaumometer", ThaumometerItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<Item> GOGGLES_OF_REVEALING = ITEMS.registerItem("goggles_of_revealing", Item::new,
            p -> p.stacksTo(1).durability(350).equippable(EquipmentSlot.HEAD));
    public static final DeferredItem<Item> SCRIBING_TOOLS = ITEMS.registerItem("scribing_tools", Item::new,
            p -> p.stacksTo(1).durability(100));
    public static final DeferredItem<ResearchNotesItem> RESEARCH_NOTES = ITEMS.registerItem("research_notes", ResearchNotesItem::new,
            p -> p.stacksTo(1));
    public static final DeferredItem<SalisMundusItem> SALIS_MUNDUS = ITEMS.registerItem("salis_mundus", SalisMundusItem::new);

    // --- alchemy
    public static final DeferredItem<PhialItem> PHIAL = ITEMS.registerItem("phial", PhialItem::new, p -> p.stacksTo(16));
    public static final DeferredItem<Item> QUICKSILVER = ITEMS.registerSimpleItem("quicksilver");
    public static final DeferredItem<Item> AMBER = ITEMS.registerSimpleItem("amber");
    public static final DeferredItem<Item> CINNABAR = ITEMS.registerSimpleItem("cinnabar");
    public static final DeferredItem<Item> ALUMENTUM = ITEMS.registerSimpleItem("alumentum");
    public static final DeferredItem<Item> NITOR = ITEMS.registerSimpleItem("nitor", p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> TALLOW = ITEMS.registerSimpleItem("tallow");
    public static final DeferredItem<Item> ENCHANTED_FABRIC = ITEMS.registerSimpleItem("enchanted_fabric");

    // --- metals
    public static final DeferredItem<Item> THAUMIUM_INGOT = ITEMS.registerSimpleItem("thaumium_ingot");
    public static final DeferredItem<Item> THAUMIUM_NUGGET = ITEMS.registerSimpleItem("thaumium_nugget");
    public static final DeferredItem<Item> BRASS_INGOT = ITEMS.registerSimpleItem("brass_ingot");
    public static final DeferredItem<Item> BRASS_NUGGET = ITEMS.registerSimpleItem("brass_nugget");

    // --- vis crystals (one per primal), named after their aspect
    public static final Map<String, DeferredItem<Item>> VIS_CRYSTALS = new LinkedHashMap<>();

    static {
        for (String primal : List.of("aer", "terra", "ignis", "aqua", "ordo", "perditio")) {
            VIS_CRYSTALS.put(primal, ITEMS.registerSimpleItem(primal + "_vis_crystal"));
        }
    }

    // --- casting
    public static final DeferredItem<CasterItem> BASIC_CASTER = ITEMS.registerItem("basic_caster",
            p -> new CasterItem(1.0, 1.0, p), p -> p.stacksTo(1));
    public static final DeferredItem<CasterItem> THAUMIUM_CASTER = ITEMS.registerItem("thaumium_caster",
            p -> new CasterItem(1.25, 0.85, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<FocusItem> APPRENTICE_FOCUS = ITEMS.registerItem("apprentice_focus",
            p -> new FocusItem(1, p), p -> p.stacksTo(1));
    public static final DeferredItem<FocusItem> ADEPT_FOCUS = ITEMS.registerItem("adept_focus",
            p -> new FocusItem(2, p), p -> p.stacksTo(1).rarity(Rarity.UNCOMMON));
    public static final DeferredItem<FocusItem> ARCHMAGE_FOCUS = ITEMS.registerItem("archmage_focus",
            p -> new FocusItem(3, p), p -> p.stacksTo(1).rarity(Rarity.RARE));

    // --- wands: one item; rod and caps are data (thaumicunity:wand_rod, thaumicunity:wand_cap)
    public static final DeferredItem<WandItem> WAND = ITEMS.registerItem("wand", WandItem::new, p -> p.stacksTo(1));
    public static final Map<String, DeferredItem<Item>> WAND_CORES = new LinkedHashMap<>();
    public static final Map<String, DeferredItem<Item>> WAND_CAPS = new LinkedHashMap<>();

    static {
        for (String core : List.of("greatwood", "silverwood", "reed", "obsidian", "blaze", "ice", "quartz", "bone")) {
            WAND_CORES.put(core, ITEMS.registerSimpleItem(core + "_wand_core", p -> p.rarity(Rarity.UNCOMMON)));
        }
        for (String cap : List.of("iron", "copper", "gold", "brass", "thaumium")) {
            WAND_CAPS.put(cap, ITEMS.registerSimpleItem(cap + "_wand_cap"));
        }
    }

    // --- block items
    public static final DeferredItem<BlockItem> CRUCIBLE = ITEMS.registerSimpleBlockItem(TUBlocks.CRUCIBLE);
    public static final DeferredItem<BlockItem> ARCANE_WORKBENCH = ITEMS.registerSimpleBlockItem(TUBlocks.ARCANE_WORKBENCH);
    public static final DeferredItem<BlockItem> RESEARCH_TABLE = ITEMS.registerSimpleBlockItem(TUBlocks.RESEARCH_TABLE);
    public static final DeferredItem<BlockItem> RUNIC_MATRIX = ITEMS.registerSimpleBlockItem(TUBlocks.RUNIC_MATRIX);
    public static final DeferredItem<BlockItem> ARCANE_PEDESTAL = ITEMS.registerSimpleBlockItem(TUBlocks.ARCANE_PEDESTAL);
    public static final DeferredItem<BlockItem> WARDED_JAR = ITEMS.registerSimpleBlockItem(TUBlocks.WARDED_JAR);
    public static final DeferredItem<BlockItem> WAND_PEDESTAL = ITEMS.registerSimpleBlockItem(TUBlocks.WAND_PEDESTAL);
    public static final DeferredItem<BlockItem> ALCHEMICAL_FURNACE = ITEMS.registerSimpleBlockItem(TUBlocks.ALCHEMICAL_FURNACE);
    public static final DeferredItem<BlockItem> ESSENTIA_TUBE = ITEMS.registerSimpleBlockItem(TUBlocks.ESSENTIA_TUBE);
    public static final DeferredItem<BlockItem> ESSENTIA_VALVE = ITEMS.registerSimpleBlockItem(TUBlocks.ESSENTIA_VALVE);
    public static final DeferredItem<BlockItem> ESSENTIA_BUFFER = ITEMS.registerSimpleBlockItem(TUBlocks.ESSENTIA_BUFFER);
    public static final DeferredItem<BlockItem> CENTRIFUGE = ITEMS.registerSimpleBlockItem(TUBlocks.CENTRIFUGE);
    public static final DeferredItem<BlockItem> ARCANE_STONE = ITEMS.registerSimpleBlockItem(TUBlocks.ARCANE_STONE);
    public static final DeferredItem<BlockItem> ARCANE_STONE_BRICKS = ITEMS.registerSimpleBlockItem(TUBlocks.ARCANE_STONE_BRICKS);
    public static final DeferredItem<BlockItem> THAUMIUM_BLOCK = ITEMS.registerSimpleBlockItem(TUBlocks.THAUMIUM_BLOCK);
    public static final DeferredItem<BlockItem> BRASS_BLOCK = ITEMS.registerSimpleBlockItem(TUBlocks.BRASS_BLOCK);
    public static final DeferredItem<BlockItem> AMBER_BLOCK = ITEMS.registerSimpleBlockItem(TUBlocks.AMBER_BLOCK);
    public static final DeferredItem<BlockItem> CINNABAR_ORE = ITEMS.registerSimpleBlockItem(TUBlocks.CINNABAR_ORE);
    public static final DeferredItem<BlockItem> AMBER_ORE = ITEMS.registerSimpleBlockItem(TUBlocks.AMBER_ORE);

    private TUItems() {
    }

    public static void init(IEventBus modBus) {
        TUEquipment.bootstrap();
        ITEMS.register(modBus);
    }
}
