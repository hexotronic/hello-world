package dev.thaumicunity.common.registry;

import dev.thaumicunity.ThaumicUnity;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.neoforged.neoforge.registries.DeferredItem;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * Tools, weapons and armour (phase 4.4). The numbers mirror tools/content/equipment.py, which generates their
 * recipes, models, names, tags, aspects and research; the game test {@code equipment} checks that both agree.
 * <ul>
 *   <li>Thaumium: tools as fast as diamond and very enchantable; armour a little below diamond.</li>
 *   <li>Void metal: the hardest and fastest, but brittle; mends itself and its blades weaken their targets
 *       (tags {@code thaumicunity:self_repairing} and {@code thaumicunity:weakening}, see
 *       {@link dev.thaumicunity.common.equipment.EquipmentEffects}).</li>
 *   <li>Thaumaturge's robes: leather-weak cloth that spares part of every spell's vis
 *       (data map {@code thaumicunity:vis_discount}).</li>
 *   <li>Thaumium fortress armour: infused thaumium armour, as strong as netherite.</li>
 *   <li>Boots of the Traveller: step up a full block and walk a fifth faster.</li>
 * </ul>
 */
public final class TUEquipment {
    public static final String[] TOOL_KINDS = {"sword", "pickaxe", "axe", "shovel", "hoe"};

    // --- materials

    public static final ToolMaterial THAUMIUM_TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_DIAMOND_TOOL, 500, 7.0f, 2.5f, 22,
            TUTags.Items.REPAIRS_THAUMIUM);
    public static final ToolMaterial VOID_TOOLS = new ToolMaterial(BlockTags.INCORRECT_FOR_NETHERITE_TOOL, 150, 8.0f, 3.0f, 10,
            TUTags.Items.REPAIRS_VOID_METAL);

    public static final ArmorMaterial THAUMIUM_ARMOR = armor(25, new int[] {2, 6, 5, 2}, 25, SoundEvents.ARMOR_EQUIP_IRON, 1.0f, 0.0f,
            TUTags.Items.REPAIRS_THAUMIUM, "thaumium");
    public static final ArmorMaterial VOID_ARMOR = armor(10, new int[] {3, 8, 6, 3}, 10, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.0f, 0.05f,
            TUTags.Items.REPAIRS_VOID_METAL, "void");
    public static final ArmorMaterial THAUMATURGE_ARMOR = armor(25, new int[] {1, 3, 2, 1}, 25, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f,
            TUTags.Items.REPAIRS_THAUMATURGE, "thaumaturge");
    public static final ArmorMaterial FORTRESS_ARMOR = armor(30, new int[] {3, 8, 6, 0}, 25, SoundEvents.ARMOR_EQUIP_NETHERITE, 2.5f, 0.1f,
            TUTags.Items.REPAIRS_THAUMIUM, "fortress");
    public static final ArmorMaterial TRAVELLER_ARMOR = armor(15, new int[] {1, 3, 2, 2}, 15, SoundEvents.ARMOR_EQUIP_LEATHER, 0.0f, 0.0f,
            ItemTags.REPAIRS_LEATHER_ARMOR, "traveller");

    /** Extra step height of the Boots of the Traveller (vanilla players step 0.6 blocks). */
    public static final double TRAVELLER_STEP_HEIGHT = 0.4;
    /** Extra walking speed of the Boots of the Traveller, as a share of the base speed. */
    public static final double TRAVELLER_SPEED = 0.2;

    // --- items

    public static final DeferredItem<Item> VOID_METAL_INGOT = TUItems.ITEMS.registerSimpleItem("void_metal_ingot",
            p -> p.rarity(Rarity.UNCOMMON));
    public static final DeferredItem<Item> VOID_METAL_NUGGET = TUItems.ITEMS.registerSimpleItem("void_metal_nugget");

    /** Every tool and armour piece by name, in registration order. */
    public static final Map<String, DeferredItem<Item>> ALL = new LinkedHashMap<>();

    static {
        tools("thaumium", THAUMIUM_TOOLS, p -> p);
        tools("void", VOID_TOOLS, p -> p.rarity(Rarity.UNCOMMON));
        armor("thaumium", THAUMIUM_ARMOR, List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS), p -> p);
        armor("void", VOID_ARMOR, List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS),
                p -> p.rarity(Rarity.UNCOMMON));
        armor("thaumaturge", THAUMATURGE_ARMOR, List.of(ArmorType.CHESTPLATE, ArmorType.LEGGINGS, ArmorType.BOOTS),
                p -> p.rarity(Rarity.UNCOMMON));
        armor("fortress", FORTRESS_ARMOR, List.of(ArmorType.HELMET, ArmorType.CHESTPLATE, ArmorType.LEGGINGS),
                p -> p.rarity(Rarity.RARE));
        ALL.put("traveller_boots", TUItems.ITEMS.registerItem("traveller_boots", Item::new, p -> p
                .humanoidArmor(TRAVELLER_ARMOR, ArmorType.BOOTS)
                .attributes(travellerAttributes())
                .rarity(Rarity.RARE)));
    }

    private TUEquipment() {
    }

    /** Loads this class so its items are registered with {@link TUItems#ITEMS}. */
    static void bootstrap() {
    }

    public static DeferredItem<Item> get(String name) {
        DeferredItem<Item> item = ALL.get(name);
        if (item == null) {
            throw new IllegalArgumentException("no Thaumic Unity equipment named " + name);
        }
        return item;
    }

    private static void tools(String material, ToolMaterial tier, UnaryOperator<Item.Properties> extra) {
        register(material + "_sword", p -> extra.apply(p.sword(tier, 3.0f, -2.4f)));
        register(material + "_pickaxe", p -> extra.apply(p.pickaxe(tier, 1.0f, -2.8f)));
        register(material + "_axe", p -> extra.apply(p.axe(tier, 5.5f, -3.0f)));
        register(material + "_shovel", p -> extra.apply(p.shovel(tier, 1.5f, -3.0f)));
        // A hoe hits for 1, whatever its material.
        register(material + "_hoe", p -> extra.apply(p.hoe(tier, -tier.attackDamageBonus(), 0.0f)));
    }

    private static void armor(String set, ArmorMaterial material, List<ArmorType> types, UnaryOperator<Item.Properties> extra) {
        for (ArmorType type : types) {
            register(set + "_" + type.getSerializedName(), p -> extra.apply(p.humanoidArmor(material, type)));
        }
    }

    private static void register(String name, UnaryOperator<Item.Properties> properties) {
        ALL.put(name, TUItems.ITEMS.registerItem(name, Item::new, properties));
    }

    private static ArmorMaterial armor(int durability, int[] defence, int enchantability, Holder<SoundEvent> sound, float toughness,
                                       float knockbackResistance, TagKey<Item> repair, String asset) {
        Map<ArmorType, Integer> map = new EnumMap<>(ArmorType.class);
        map.put(ArmorType.HELMET, defence[0]);
        map.put(ArmorType.CHESTPLATE, defence[1]);
        map.put(ArmorType.LEGGINGS, defence[2]);
        map.put(ArmorType.BOOTS, defence[3]);
        map.put(ArmorType.BODY, defence[1] * 2);
        ResourceKey<EquipmentAsset> assetId = ResourceKey.create(EquipmentAssets.ROOT_ID, ThaumicUnity.id(asset));
        return new ArmorMaterial(durability, map, enchantability, sound, toughness, knockbackResistance, repair, assetId);
    }

    private static ItemAttributeModifiers travellerAttributes() {
        return TRAVELLER_ARMOR.createAttributes(ArmorType.BOOTS)
                .withModifierAdded(Attributes.STEP_HEIGHT, new AttributeModifier(ThaumicUnity.id("traveller_step"),
                        TRAVELLER_STEP_HEIGHT, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.FEET)
                .withModifierAdded(Attributes.MOVEMENT_SPEED, new AttributeModifier(ThaumicUnity.id("traveller_speed"),
                        TRAVELLER_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_BASE), EquipmentSlotGroup.FEET);
    }
}
