"""Wand rods and caps (datapack registries thaumicunity:wand_rod and thaumicunity:wand_cap), the items they are made
from, and one arcane workbench recipe per rod and cap combination (docs/DESIGN_DECISIONS.md, D4).

A wand stores vis per primal, up to its rod's capacity. Spells cast with it cost the focus's primal vis multiplied by
the cap's cost factor for that primal; a rod with an affinity pays 10 % less of its primal and slowly regains it on
its own. Capacities are in whole vis.
"""

PRIMALS = ("aer", "terra", "ignis", "aqua", "ordo", "perditio")

# id: (English adjective, rod item, capacity, affinity primal or None, colour, research entry)
RODS = {
    "wood": ("Wooden", "minecraft:stick", 30, None, 0x8B6A3E, "auromancy/wands"),
    "reed": ("Reed", "thaumicunity:reed_wand_core", 75, "aer", 0xC9D98C, "auromancy/elemental_cores"),
    "obsidian": ("Obsidian", "thaumicunity:obsidian_wand_core", 75, "terra", 0x40305E, "auromancy/elemental_cores"),
    "blaze": ("Blazing", "thaumicunity:blaze_wand_core", 75, "ignis", 0xF2A23A, "auromancy/elemental_cores"),
    "ice": ("Icy", "thaumicunity:ice_wand_core", 75, "aqua", 0xB8E6F6, "auromancy/elemental_cores"),
    "quartz": ("Quartz", "thaumicunity:quartz_wand_core", 75, "ordo", 0xEDE6DE, "auromancy/elemental_cores"),
    "bone": ("Bone", "thaumicunity:bone_wand_core", 75, "perditio", 0xE2DCC2, "auromancy/elemental_cores"),
}

# id: (English adjective, cap item, cost factor, per-primal cost factors, colour, research entry)
CAPS = {
    "iron": ("Iron", "thaumicunity:iron_wand_cap", 1.1, {}, 0xC4C4C4, "auromancy/wands"),
    "copper": ("Copper", "thaumicunity:copper_wand_cap", 1.1, {"ordo": 1.0, "perditio": 1.0}, 0xD9824B,
               "auromancy/wand_caps"),
    "gold": ("Gold", "thaumicunity:gold_wand_cap", 1.0, {}, 0xF2D13C, "auromancy/wand_caps"),
    "brass": ("Brass", "thaumicunity:brass_wand_cap", 1.0, {"ignis": 0.9, "terra": 0.9}, 0xC9A43A, "auromancy/wand_caps"),
    "thaumium": ("Thaumium", "thaumicunity:thaumium_wand_cap", 0.9, {}, 0x7558B8, "auromancy/thaumium_caps"),
}

# Items. Cores are infused from a vanilla material; caps are worked from nuggets at the arcane workbench.
ITEMS = {
    "reed_wand_core": "Reed Wand Core",
    "obsidian_wand_core": "Obsidian Wand Core",
    "blaze_wand_core": "Blazing Wand Core",
    "ice_wand_core": "Icy Wand Core",
    "quartz_wand_core": "Quartz Wand Core",
    "bone_wand_core": "Bone Wand Core",
    "iron_wand_cap": "Iron Wand Cap",
    "copper_wand_cap": "Copper Wand Cap",
    "gold_wand_cap": "Gold Wand Cap",
    "brass_wand_cap": "Brass Wand Cap",
    "thaumium_wand_cap": "Thaumium Wand Cap",
}

# Cap recipes at the arcane workbench: cap -> (nugget, vis); each makes two caps.
CAP_RECIPES = {
    "iron": ("minecraft:iron_nugget", "ordo 2 aer 2"),
    "copper": ("minecraft:copper_nugget", "ordo 3 perditio 3"),
    "gold": ("minecraft:gold_nugget", "ordo 4 aer 4"),
    "brass": ("thaumicunity:brass_nugget", "ordo 4 ignis 4"),
    "thaumium": ("thaumicunity:thaumium_nugget", "ordo 8 aer 6 perditio 6"),
}

# Core infusions: rod -> (central item, two kinds of components, essentia, instability)
CORE_INFUSIONS = {
    "reed": ("minecraft:sugar_cane", ["minecraft:feather", "thaumicunity:aer_vis_crystal"], "aer 16 motus 8 praecantatio 8", 2),
    "obsidian": ("minecraft:obsidian", ["minecraft:flint", "thaumicunity:terra_vis_crystal"], "terra 16 tutamen 8 praecantatio 8", 2),
    "blaze": ("minecraft:blaze_rod", ["minecraft:magma_cream", "thaumicunity:ignis_vis_crystal"], "ignis 16 potentia 8 praecantatio 8", 2),
    "ice": ("minecraft:packed_ice", ["minecraft:snowball", "thaumicunity:aqua_vis_crystal"], "aqua 16 gelum 8 praecantatio 8", 2),
    "quartz": ("minecraft:quartz", ["minecraft:amethyst_shard", "thaumicunity:ordo_vis_crystal"], "ordo 16 vitreus 8 praecantatio 8", 2),
    "bone": ("minecraft:bone", ["minecraft:rotten_flesh", "thaumicunity:perditio_vis_crystal"], "perditio 16 mortuus 8 praecantatio 8", 2),
}

# Wand recharge pedestal recipe (arcane).
PEDESTAL_RECIPE = (["NBN", " S ", "SSS"], {"N": "minecraft:gold_nugget", "B": "minecraft:amethyst_shard",
                                           "S": "thaumicunity:arcane_stone"}, "aer 5 terra 5 ignis 5 aqua 5 ordo 5 perditio 5")


def assembly_vis(rod, cap):
    """Vis to assemble a wand at the arcane workbench: grows with the rod's capacity and the cap's quality."""
    capacity = RODS[rod][2]
    quality = {1.1: 0, 1.0: 2, 0.9: 4}[CAPS[cap][2]]
    per_primal = capacity // 10 + quality
    return " ".join(f"{p} {per_primal}" for p in PRIMALS)


def wand_name(rod, cap):
    return f"{CAPS[cap][0]}-capped {RODS[rod][0]} Wand"

# Aspects of the new items (arcane and infusion recipes do not pass aspects on, so they are explicit).
ITEM_ASPECTS = {
    "thaumicunity:wand": "instrumentum 4 praecantatio 4 auram 2",
    "thaumicunity:wand_pedestal": "auram 4 ordo 2 terra 4 praecantatio 2",
    "thaumicunity:iron_wand_cap": "metallum 2 instrumentum 1",
    "thaumicunity:copper_wand_cap": "metallum 2 permutatio 1",
    "thaumicunity:gold_wand_cap": "aurum 2 instrumentum 1",
    "thaumicunity:brass_wand_cap": "instrumentum 2 machina 1",
    "thaumicunity:thaumium_wand_cap": "praecantatio 3 metallum 1",
    "thaumicunity:reed_wand_core": "aer 4 herba 2 praecantatio 2",
    "thaumicunity:obsidian_wand_core": "terra 4 tutamen 2 praecantatio 2",
    "thaumicunity:blaze_wand_core": "ignis 4 potentia 2 praecantatio 2",
    "thaumicunity:ice_wand_core": "aqua 4 gelum 2 praecantatio 2",
    "thaumicunity:quartz_wand_core": "ordo 4 vitreus 2 praecantatio 2",
    "thaumicunity:bone_wand_core": "perditio 4 mortuus 2 praecantatio 2",
    # Essentia transport (phase 4.2); kept here with the other explicit device aspects.
    "thaumicunity:essentia_tube": "aqua 1 vitreus 1 instrumentum 1",
    "thaumicunity:essentia_valve": "aqua 1 machina 1 instrumentum 1",
    "thaumicunity:essentia_buffer": "aqua 4 vacuos 2 instrumentum 2",
    "thaumicunity:centrifuge": "machina 4 permutatio 4 perditio 2",
}
