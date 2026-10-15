"""Tools, weapons and armour (phase 4.4). The Java side (common/registry/TUEquipment.java) registers the items with the
numbers below; this table drives their recipes, textures, models, names, tags, aspects and research. Keep both in
step: the game test `equipment` checks the registered items against the tag thaumicunity:equipment generated from
this table.
"""

PIECES = ("helmet", "chestplate", "leggings", "boots")
TOOL_KINDS = ("sword", "pickaxe", "axe", "shovel", "hoe")

# Tool materials: id -> (English material name, ingredient, durability, mining speed, attack bonus, enchantability,
#                        colour for generated art, research entry, arcane vis per tool)
TOOL_MATERIALS = {
    "thaumium": ("Thaumium", "thaumicunity:thaumium_ingot", 500, 7.0, 2.5, 22, 0x6A4FA8,
                 "artifice/thaumium_equipment", "ordo 3 terra 3"),
    "void": ("Void Metal", "thaumicunity:void_metal_ingot", 150, 8.0, 3.0, 10, 0x2E1F46,
             "alchemy/void_metal", "perditio 6 ordo 4"),
}

# Armour sets: id -> (English names per piece (None = no such piece), ingredient, durability factor,
#                     defence per piece [helmet, chestplate, leggings, boots], enchantability, toughness,
#                     colour for generated art, research entry, arcane vis per piece or None for infused sets)
ARMOR_SETS = {
    "thaumium": (dict(helmet="Thaumium Helmet", chestplate="Thaumium Chestplate", leggings="Thaumium Leggings",
                      boots="Thaumium Boots"),
                 "thaumicunity:thaumium_ingot", 25, [2, 6, 5, 2], 25, 1.0, 0x6A4FA8, "artifice/thaumium_equipment",
                 "ordo 5 terra 5"),
    "void": (dict(helmet="Void Metal Helmet", chestplate="Void Metal Chestplate", leggings="Void Metal Leggings",
                  boots="Void Metal Boots"),
             "thaumicunity:void_metal_ingot", 10, [3, 8, 6, 3], 10, 2.0, 0x2E1F46, "alchemy/void_metal",
             "perditio 8 ordo 6 terra 4"),
    "thaumaturge": (dict(helmet=None, chestplate="Thaumaturge's Robe", leggings="Thaumaturge's Leggings",
                         boots="Thaumaturge's Boots"),
                    "thaumicunity:enchanted_fabric", 25, [1, 3, 2, 1], 25, 0.0, 0x5B3A8C, "artifice/robes",
                    "aer 5 ordo 5 ignis 3"),
    "traveller": (dict(helmet=None, chestplate=None, leggings=None, boots="Boots of the Traveller"),
                  "minecraft:leather", 15, [1, 3, 2, 2], 15, 0.0, 0x8A5A32, "infusion/travellers_boots", None),
    "fortress": (dict(helmet="Thaumium Fortress Helmet", chestplate="Thaumium Fortress Cuirass",
                      leggings="Thaumium Fortress Greaves", boots=None),
                 "thaumicunity:thaumium_ingot", 30, [3, 8, 6, 0], 25, 2.5, 0x55407F, "infusion/fortress_armor", None),
}

# Item tag each set is repaired with in an anvil (written by gen_content unless it is a vanilla tag).
REPAIR_TAGS = {
    "thaumium": ("thaumicunity:repairs_thaumium", ["thaumicunity:thaumium_ingot"]),
    "void": ("thaumicunity:repairs_void_metal", ["thaumicunity:void_metal_ingot"]),
    "thaumaturge": ("thaumicunity:repairs_thaumaturge", ["thaumicunity:enchanted_fabric"]),
    "traveller": ("minecraft:repairs_leather_armor", None),
    "fortress": ("thaumicunity:repairs_thaumium", None),
}

# Share of spell vis cost (in percent) each piece spares its wearer (data map thaumicunity:vis_discount).
VIS_DISCOUNT = {
    "thaumicunity:thaumaturge_chestplate": 5,
    "thaumicunity:thaumaturge_leggings": 4,
    "thaumicunity:thaumaturge_boots": 3,
    "thaumicunity:goggles_of_revealing": 5,
}

TOOL_NAMES = {"sword": "Sword", "pickaxe": "Pickaxe", "axe": "Axe", "shovel": "Shovel", "hoe": "Hoe"}

# Void metal mends itself, and its blades weaken what they strike.
SELF_REPAIRING = [f"void_{k}" for k in TOOL_KINDS] + [f"void_{p}" for p in PIECES]
WEAKENING = ["void_sword", "void_axe"]

# Vanilla item tags per tool kind and armour piece (they also make the right enchantments apply).
KIND_TAGS = {
    "sword": ["minecraft:swords"], "pickaxe": ["minecraft:pickaxes"], "axe": ["minecraft:axes"],
    "shovel": ["minecraft:shovels"], "hoe": ["minecraft:hoes"],
    "helmet": ["minecraft:head_armor"], "chestplate": ["minecraft:chest_armor"],
    "leggings": ["minecraft:leg_armor"], "boots": ["minecraft:foot_armor"],
}

# Plain items that come with the equipment.
ITEMS = {
    "void_metal_ingot": ("Void Metal Ingot", ["c:ingots", "c:ingots/void_metal"]),
    "void_metal_nugget": ("Void Metal Nugget", ["c:nuggets", "c:nuggets/void_metal"]),
}


def tools():
    """name -> (material, kind, English name)"""
    return {f"{m}_{k}": (m, k, f"{spec[0]} {TOOL_NAMES[k]}") for m, spec in TOOL_MATERIALS.items() for k in TOOL_KINDS}


def armor():
    """name -> (set, piece, English name)"""
    out = {}
    for set_id, spec in ARMOR_SETS.items():
        for piece in PIECES:
            name = spec[0][piece]
            if name:
                out[f"{set_id}_{piece}"] = (set_id, piece, name)
    return out


# Crafting shapes for tools and armour made from an ingot or cloth ('#' the material, 'S' a stick).
TOOL_PATTERNS = {
    "sword": ["#", "#", "S"],
    "pickaxe": ["###", " S ", " S "],
    "axe": ["##", "#S", " S"],
    "shovel": ["#", "S", "S"],
    "hoe": ["##", " S", " S"],
}
ARMOR_PATTERNS = {
    "helmet": ["###", "# #"],
    "chestplate": ["# #", "###", "###"],
    "leggings": ["###", "# #", "# #"],
    "boots": ["# #", "# #"],
}

# Vanilla crafting for the void metal nuggets.
SHAPELESS = {
    "void_metal_nugget": (["thaumicunity:void_metal_ingot"], "thaumicunity:void_metal_nugget", 9),
}
SHAPED = {
    "void_metal_ingot_from_nuggets": (["###", "###", "###"], {"#": "thaumicunity:void_metal_nugget"},
                                      "thaumicunity:void_metal_ingot", 1),
}

# Crucible: id -> (catalyst, aspects, result, count, research)
CRUCIBLE = {
    "alchemy/void_metal_ingot": ("thaumicunity:thaumium_ingot", "vacuos 6 alienis 4 tenebrae 2",
                                 "thaumicunity:void_metal_ingot", 1, "alchemy/void_metal"),
}

# Infusion: id -> (central item, components, essentia, instability, result, research)
INFUSION = {
    "infusion/travellers_boots": ("minecraft:leather_boots",
                                  ["thaumicunity:enchanted_fabric", "minecraft:feather", "thaumicunity:aer_vis_crystal",
                                   "thaumicunity:enchanted_fabric", "minecraft:sugar", "thaumicunity:aer_vis_crystal"],
                                  "iter 16 motus 16 aer 8", 2, "thaumicunity:traveller_boots",
                                  "infusion/travellers_boots"),
    "infusion/fortress_helmet": ("thaumicunity:thaumium_helmet",
                                 ["thaumicunity:thaumium_ingot", "minecraft:gold_ingot", "thaumicunity:thaumium_ingot",
                                  "minecraft:emerald"],
                                 "tutamen 16 metallum 16 praecantatio 8", 3, "thaumicunity:fortress_helmet",
                                 "infusion/fortress_armor"),
    "infusion/fortress_chestplate": ("thaumicunity:thaumium_chestplate",
                                     ["thaumicunity:thaumium_ingot", "minecraft:gold_ingot", "thaumicunity:thaumium_ingot",
                                      "minecraft:gold_ingot", "thaumicunity:thaumium_ingot", "minecraft:emerald"],
                                     "tutamen 24 metallum 24 praecantatio 12", 4, "thaumicunity:fortress_chestplate",
                                     "infusion/fortress_armor"),
    "infusion/fortress_leggings": ("thaumicunity:thaumium_leggings",
                                   ["thaumicunity:thaumium_ingot", "minecraft:gold_ingot", "thaumicunity:thaumium_ingot",
                                    "minecraft:gold_ingot", "minecraft:emerald"],
                                   "tutamen 20 metallum 20 praecantatio 10", 4, "thaumicunity:fortress_leggings",
                                   "infusion/fortress_armor"),
}

# Aspects of each tool kind and armour piece on top of what its materials carry.
KIND_ASPECTS = {
    "sword": "telum 3", "pickaxe": "perfodio 3", "axe": "instrumentum 2 telum 1", "shovel": "perfodio 2",
    "hoe": "messis 2", "helmet": "tutamen 2", "chestplate": "tutamen 4", "leggings": "tutamen 3", "boots": "tutamen 2",
}
# Aspects of one unit of each material (for the derived aspects of the equipment made from it).
MATERIAL_ASPECTS = {
    "thaumicunity:thaumium_ingot": "metallum 4 praecantatio 3",
    "thaumicunity:void_metal_ingot": "metallum 4 vacuos 4 alienis 2",
    "thaumicunity:enchanted_fabric": "pannus 3 praecantatio 2",
    "minecraft:stick": "arbor 1",
}

ITEM_ASPECTS = {
    "thaumicunity:void_metal_ingot": "metallum 4 vacuos 4 alienis 2",
    "thaumicunity:void_metal_nugget": "vacuos 1",
    "thaumicunity:traveller_boots": "tutamen 2 iter 4 motus 4 pannus 2 bestia 2",
    "thaumicunity:fortress_helmet": "metallum 15 praecantatio 10 tutamen 8 aurum 2",
    "thaumicunity:fortress_chestplate": "metallum 30 praecantatio 20 tutamen 12 aurum 4",
    "thaumicunity:fortress_leggings": "metallum 26 praecantatio 17 tutamen 10 aurum 4",
}

# Research entries (merged into research.ENTRIES).
RESEARCH = {
    "artifice/thaumium_equipment": dict(
        title="Thaumium Arms and Armour", x=2, y=2, icon="thaumicunity:thaumium_sword",
        parents=["artifice/arcane_stone", "alchemy/metallurgy"], aspects=["metallum", "tutamen"],
        stages=[
            dict(text=(
                "Thaumium takes an edge better than iron and holds an enchantment better than gold. Worked at the "
                "arcane workbench, with a little vis to settle it, it makes tools and armour a thaumaturge can rely "
                "on.\n\nObtain three thaumium ingots."),
                requirements=[("obtain", "thaumicunity:thaumium_ingot", 3)]),
            dict(text=(
                "Thaumium tools dig as fast as diamond ones and are far easier to enchant. Thaumium armour is "
                "nearly as strong as diamond and as easy to enchant as gold. Both are mended with thaumium ingots."),
                recipes=["equipment/thaumium_sword", "equipment/thaumium_pickaxe", "equipment/thaumium_axe",
                         "equipment/thaumium_shovel", "equipment/thaumium_hoe", "equipment/thaumium_helmet",
                         "equipment/thaumium_chestplate", "equipment/thaumium_leggings", "equipment/thaumium_boots"])]),
    "artifice/robes": dict(
        title="Thaumaturge's Robes", x=4, y=2, icon="thaumicunity:thaumaturge_chestplate",
        parents=["artifice/goggles", "alchemy/enchanted_fabric"], aspects=["pannus", "praecantatio"],
        stages=[
            dict(text=(
                "Enchanted fabric cut and stitched at the arcane workbench keeps a little of every spell its wearer "
                "casts from leaving.\n\nObtain four pieces of enchanted fabric."),
                requirements=[("obtain", "thaumicunity:enchanted_fabric", 4)]),
            dict(text=(
                "The robe, leggings and boots protect no better than leather, but each spares part of the vis your "
                "spells cost: five, four and three parts in a hundred. The goggles of revealing spare another five. "
                "The savings add up, to at most half of the cost."),
                recipes=["equipment/thaumaturge_chestplate", "equipment/thaumaturge_leggings",
                         "equipment/thaumaturge_boots"])]),
    "alchemy/void_metal": dict(
        title="Void Metal", x=6, y=2, icon="thaumicunity:void_metal_ingot",
        parents=["alchemy/vis_crystals", "artifice/thaumium_equipment"], aspects=["vacuos", "alienis"],
        stages=[
            dict(text=(
                "Thaumium steeped in Vacuos, Alienis and Tenebrae comes out of the crucible dark and cold, and wrong "
                "to look at.\n\nSolve the puzzle linking Vacuos and Alienis."),
                requirements=[("insight", ["vacuos", "alienis"])]),
            dict(text=(
                "Void metal is harder than diamond but brittle. It mends itself slowly while you carry it, a point "
                "every second, and blades of void metal leave whatever they strike weakened. Tools and armour of "
                "void metal are worked at the arcane workbench."),
                recipes=["alchemy/void_metal_ingot", "void_metal_nugget", "void_metal_ingot_from_nuggets",
                         "equipment/void_sword", "equipment/void_pickaxe", "equipment/void_axe",
                         "equipment/void_shovel", "equipment/void_hoe", "equipment/void_helmet",
                         "equipment/void_chestplate", "equipment/void_leggings", "equipment/void_boots"])]),
    "infusion/fortress_armor": dict(
        title="Thaumium Fortress Armour", x=4, y=2, icon="thaumicunity:fortress_chestplate",
        parents=["infusion/runic_matrix", "artifice/thaumium_equipment"], aspects=["tutamen", "metallum"],
        stages=[
            dict(text="Thaumium armour can be infused into something far heavier.\n\nObtain a thaumium chestplate.",
                 requirements=[("obtain", "thaumicunity:thaumium_chestplate", 1)]),
            dict(text=(
                "Infused with Tutamen, gold and an emerald, thaumium helmet, chestplate and leggings become fortress "
                "armour: as strong as netherite, hard to knock back, and as easy to enchant as before. Each piece is "
                "infused from its thaumium counterpart, keeping nothing of it but the shape."),
                recipes=["infusion/fortress_helmet", "infusion/fortress_chestplate", "infusion/fortress_leggings"])]),
    "infusion/travellers_boots": dict(
        title="Boots of the Traveller", x=4, y=0, icon="thaumicunity:traveller_boots",
        parents=["infusion/stability"], aspects=["iter", "motus"],
        stages=[
            dict(text="Make two observations in Infusion.",
                 requirements=[("knowledge", "observation", "infusion", 2)]),
            dict(text=(
                "Leather boots infused with Iter and Motus carry their wearer a fifth faster and lift them up a full "
                "block without a jump."),
                recipes=["infusion/travellers_boots"])]),
}

# Thaumcraft textures for these items (assets/thaumcraft/textures/...), merged into thaumcraft_import.KNOWN.
# Fortress armour is drawn by its own model in Thaumcraft, so its worn texture does not fit vanilla's layout.
THAUMCRAFT_TEXTURES = {
    "item/void_metal_ingot": ["items/ingot_void.png", "items/voidingot.png", "items/void_ingot.png"],
    "item/void_metal_nugget": ["items/nugget_void.png", "items/voidnugget.png", "items/void_nugget.png"],
    "item/thaumium_pickaxe": ["items/thaumium_pick.png", "items/thaumiumpick.png"],
    "item/thaumium_helmet": ["items/thaumium_helm.png", "items/thaumiumhelm.png"],
    "item/thaumium_chestplate": ["items/thaumium_chest.png", "items/thaumiumchest.png"],
    "item/thaumium_leggings": ["items/thaumium_legs.png", "items/thaumiumlegs.png"],
    "item/void_pickaxe": ["items/void_pick.png", "items/voidpick.png"],
    "item/void_helmet": ["items/void_helm.png", "items/voidhelm.png"],
    "item/void_chestplate": ["items/void_chest.png", "items/voidchest.png"],
    "item/void_leggings": ["items/void_legs.png", "items/voidlegs.png"],
    "item/thaumaturge_chestplate": ["items/cloth_chest.png", "items/robeupper.png", "items/clothchest.png"],
    "item/thaumaturge_leggings": ["items/cloth_legs.png", "items/robelegs.png", "items/clothlegs.png"],
    "item/thaumaturge_boots": ["items/cloth_boots.png", "items/robeboots.png", "items/clothboots.png"],
    "item/traveller_boots": ["items/traveler_boots.png", "items/bootstraveler.png", "items/boots_traveler.png"],
    "entity/equipment/humanoid/thaumium": ["models/armor/thaumium_1.png", "entity/armor/thaumium_1.png",
                                           "models/thaumium_1.png"],
    "entity/equipment/humanoid_leggings/thaumium": ["models/armor/thaumium_2.png", "entity/armor/thaumium_2.png",
                                                    "models/thaumium_2.png"],
    "entity/equipment/humanoid/void": ["models/armor/void_1.png", "entity/armor/void_1.png", "models/void_1.png"],
    "entity/equipment/humanoid_leggings/void": ["models/armor/void_2.png", "entity/armor/void_2.png",
                                                "models/void_2.png"],
    "entity/equipment/humanoid/thaumaturge": ["models/armor/robes_1.png", "entity/armor/robes_1.png",
                                              "models/robes_1.png"],
    "entity/equipment/humanoid_leggings/thaumaturge": ["models/armor/robes_2.png", "entity/armor/robes_2.png",
                                                       "models/robes_2.png"],
    "item/fortress_helmet": ["items/thaumium_fortress_helm.png", "items/fortresshelm.png", "items/fortress_helm.png"],
    "item/fortress_chestplate": ["items/thaumium_fortress_chest.png", "items/fortresschest.png", "items/fortress_chest.png"],
    "item/fortress_leggings": ["items/thaumium_fortress_legs.png", "items/fortresslegs.png", "items/fortress_legs.png"],
    "entity/equipment/humanoid/traveller": ["models/armor/bootstraveler.png", "entity/armor/bootstraveler.png",
                                            "models/bootstraveler.png"],
}
