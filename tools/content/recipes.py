"""Phase 3 recipes. Ids are paths under data/thaumicunity/recipe/. Research gates name the entry that must be
complete before an arcane, crucible or infusion recipe works (vanilla crafting and smelting recipes are never gated).
Ingredients: "ns:item", "#ns:tag" or a list of those. Aspect amounts use the "aspect n" shorthand.
"""

# --- vanilla crafting table
SHAPELESS = {
    "salis_mundus": (["minecraft:redstone", "minecraft:glowstone_dust", "minecraft:bone_meal", "minecraft:amethyst_shard"],
                     "thaumicunity:salis_mundus", 2),
    "thaumonomicon": (["minecraft:book", "thaumicunity:salis_mundus"], "thaumicunity:thaumonomicon", 1),
    "scribing_tools": (["minecraft:feather", "minecraft:glass_bottle", "minecraft:ink_sac"], "thaumicunity:scribing_tools", 1),
    "thaumium_ingot_from_block": (["thaumicunity:thaumium_block"], "thaumicunity:thaumium_ingot", 9),
    "thaumium_nugget": (["thaumicunity:thaumium_ingot"], "thaumicunity:thaumium_nugget", 9),
    "brass_ingot_from_block": (["thaumicunity:brass_block"], "thaumicunity:brass_ingot", 9),
    "brass_nugget": (["thaumicunity:brass_ingot"], "thaumicunity:brass_nugget", 9),
    "amber_from_block": (["thaumicunity:amber_block"], "thaumicunity:amber", 9),
}

SHAPED = {
    "thaumium_block": (["###", "###", "###"], {"#": "thaumicunity:thaumium_ingot"}, "thaumicunity:thaumium_block", 1),
    "thaumium_ingot_from_nuggets": (["###", "###", "###"], {"#": "thaumicunity:thaumium_nugget"}, "thaumicunity:thaumium_ingot", 1),
    "brass_block": (["###", "###", "###"], {"#": "thaumicunity:brass_ingot"}, "thaumicunity:brass_block", 1),
    "brass_ingot_from_nuggets": (["###", "###", "###"], {"#": "thaumicunity:brass_nugget"}, "thaumicunity:brass_ingot", 1),
    "amber_block": (["###", "###", "###"], {"#": "thaumicunity:amber"}, "thaumicunity:amber_block", 1),
    "arcane_stone_bricks": (["##", "##"], {"#": "thaumicunity:arcane_stone"}, "thaumicunity:arcane_stone_bricks", 4),
    "phial": ([" C ", "G G", " G "], {"C": "minecraft:clay_ball", "G": "minecraft:glass"}, "thaumicunity:phial", 8),
}

SMELTING = {
    "tallow_from_smelting": ("minecraft:rotten_flesh", "thaumicunity:tallow", 0.1),
    "quicksilver_from_smelting_cinnabar": ("thaumicunity:cinnabar", "thaumicunity:quicksilver", 0.5),
    "quicksilver_from_smelting_cinnabar_ore": ("thaumicunity:cinnabar_ore", "thaumicunity:quicksilver", 0.7),
    "amber_from_smelting_amber_ore": ("thaumicunity:amber_ore", "thaumicunity:amber", 0.7),
}

# --- arcane workbench: id -> (pattern or None for shapeless, key or ingredient list, result, count, vis, research)
ARCANE = {
    "thaumometer": ([" G ", "GAG", " G "], {"G": "minecraft:gold_ingot", "A": "minecraft:amethyst_shard"},
                    "thaumicunity:thaumometer", 1, "aer 5 ordo 5", "fundamentals/arcane_workbench"),
    "goggles_of_revealing": (["LBL", "T T"], {"L": "minecraft:leather", "B": "thaumicunity:brass_ingot",
                                             "T": "minecraft:glass_pane"},
                             "thaumicunity:goggles_of_revealing", 1, "aer 10 ordo 10", "artifice/goggles"),
    "arcane_stone": (["SSS", "SAS", "SSS"], {"S": "#c:stones", "A": "minecraft:amethyst_shard"},
                     "thaumicunity:arcane_stone", 8, "terra 2 ordo 2", "artifice/arcane_stone"),
    "warded_jar": (["GBG", "G G", "GGG"], {"G": "minecraft:glass", "B": "#minecraft:wooden_slabs"},
                   "thaumicunity:warded_jar", 1, "aqua 5 ordo 5", "alchemy/essentia"),
    "alchemical_furnace": (["SBS", "SFS", "SSS"], {"S": "thaumicunity:arcane_stone", "B": "thaumicunity:brass_ingot",
                                                  "F": "minecraft:furnace"},
                           "thaumicunity:alchemical_furnace", 1, "ignis 10 aqua 5", "alchemy/essentia"),
    "runic_matrix": (["SAS", "ATA", "SAS"], {"S": "thaumicunity:arcane_stone_bricks", "A": "minecraft:amethyst_shard",
                                            "T": "thaumicunity:thaumium_ingot"},
                     "thaumicunity:runic_matrix", 1, "ordo 20 aer 10 terra 10", "infusion/runic_matrix"),
    "arcane_pedestal": (["SSS", " B ", "SSS"], {"S": "thaumicunity:arcane_stone", "B": "thaumicunity:brass_ingot"},
                        "thaumicunity:arcane_pedestal", 2, "ordo 5", "infusion/runic_matrix"),
    "basic_caster": (["BLB", "LCL", " L "], {"B": "thaumicunity:brass_ingot", "L": "minecraft:leather",
                                           "C": "minecraft:amethyst_shard"},
                     "thaumicunity:basic_caster", 1, "aer 10 ordo 10 ignis 5", "auromancy/casting"),
    "apprentice_focus": ([" N ", "NAN", " N "], {"N": "minecraft:gold_nugget", "A": "minecraft:amethyst_shard"},
                         "thaumicunity:apprentice_focus", 1, "ordo 5 aer 5", "auromancy/casting"),
}

# --- crucible: id -> (catalyst, aspects, result, count, research)
CRUCIBLE = {
    "alchemy/thaumium_ingot": ("minecraft:iron_ingot", "praecantatio 4 terra 2", "thaumicunity:thaumium_ingot", 1,
                               "alchemy/metallurgy"),
    "alchemy/brass_ingot": ("minecraft:copper_ingot", "metallum 2 ordo 2", "thaumicunity:brass_ingot", 1,
                            "alchemy/metallurgy"),
    "alchemy/nitor": ("minecraft:glowstone_dust", "lux 4 potentia 2 ignis 2", "thaumicunity:nitor", 1,
                      "alchemy/alchemical_fuels"),
    "alchemy/alumentum": ("minecraft:coal", "potentia 4 ignis 3 exitium 2", "thaumicunity:alumentum", 1,
                          "alchemy/alchemical_fuels"),
    "alchemy/tallow": ("minecraft:rotten_flesh", "ignis 2 corpus 2", "thaumicunity:tallow", 2, "alchemy/alchemical_fuels"),
    "alchemy/enchanted_fabric": ("#minecraft:wool", "praecantatio 2 vinculum 1", "thaumicunity:enchanted_fabric", 1,
                                 "alchemy/enchanted_fabric"),
}
for _primal in ("aer", "terra", "ignis", "aqua", "ordo", "perditio"):
    CRUCIBLE[f"alchemy/{_primal}_vis_crystal"] = ("minecraft:amethyst_shard", f"{_primal} 5",
                                                  f"thaumicunity:{_primal}_vis_crystal", 1, "alchemy/vis_crystals")

# --- infusion: id -> (central, components, essentia, instability, result, research)
INFUSION = {
    "infusion/thaumium_caster": ("thaumicunity:basic_caster",
                                 ["thaumicunity:thaumium_ingot", "thaumicunity:thaumium_ingot", "thaumicunity:thaumium_ingot",
                                  "thaumicunity:thaumium_ingot"],
                                 "praecantatio 16 potentia 8 instrumentum 8", 3, "thaumicunity:thaumium_caster",
                                 "infusion/thaumium_caster"),
    "infusion/adept_focus": ("minecraft:diamond",
                             ["thaumicunity:thaumium_ingot", "minecraft:amethyst_shard", "thaumicunity:thaumium_ingot",
                              "minecraft:amethyst_shard"],
                             "praecantatio 12 cognitio 6 vitreus 6", 3, "thaumicunity:adept_focus", "infusion/greater_foci"),
    "infusion/archmage_focus": ("minecraft:ender_eye",
                                ["thaumicunity:thaumium_ingot", "minecraft:diamond", "thaumicunity:thaumium_ingot",
                                 "minecraft:diamond", "thaumicunity:thaumium_ingot", "minecraft:diamond"],
                                "praecantatio 24 alienis 8 cognitio 8 potentia 8", 6, "thaumicunity:archmage_focus",
                                "infusion/greater_foci"),
}
