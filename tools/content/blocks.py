"""Blocks and items: model shape, loot, tags and English names.

Model kinds:
  cube           one texture on all faces (block/<name>)
  bottom_top     textures <top>, <side>, <bottom> (see `textures`)
  custom:<kind>  element model written by gen_content.py (crucible, pedestal, jar, matrix)
Loot kinds:
  self           drops itself (if it survives the explosion)
  ore:<item>     silk touch drops the ore, otherwise <item> with Fortune
  jar            drops itself keeping its essentia (copy_components from the block entity)
"""

BLOCKS = {
    "crucible": dict(
        name="Crucible", model="custom:crucible", loot="self",
        textures=dict(side="crucible_side", top="crucible_top", bottom="crucible_bottom", inside="crucible_inner"),
        tags=["minecraft:mineable/pickaxe", "minecraft:needs_stone_tool"]),
    "arcane_workbench": dict(
        name="Arcane Workbench", model="bottom_top", loot="self",
        textures=dict(top="arcane_workbench_top", side="arcane_workbench_side", bottom="arcane_workbench_bottom"),
        tags=["minecraft:mineable/axe"]),
    "research_table": dict(
        name="Research Table", model="bottom_top", loot="self",
        textures=dict(top="research_table_top", side="research_table_side", bottom="arcane_workbench_bottom"),
        tags=["minecraft:mineable/axe"]),
    "runic_matrix": dict(
        name="Runic Matrix", model="custom:matrix", loot="self",
        textures=dict(all="runic_matrix"),
        tags=["minecraft:mineable/pickaxe", "minecraft:needs_iron_tool"]),
    "arcane_pedestal": dict(
        name="Arcane Pedestal", model="custom:pedestal", loot="self",
        textures=dict(side="arcane_pedestal_side", top="arcane_pedestal_top"),
        tags=["minecraft:mineable/pickaxe"]),
    "wand_pedestal": dict(
        name="Wand Recharge Pedestal", model="custom:pedestal", loot="self",
        textures=dict(side="wand_pedestal_side", top="wand_pedestal_top"),
        tags=["minecraft:mineable/pickaxe"]),
    "warded_jar": dict(
        name="Warded Jar", model="custom:jar", loot="jar",
        textures=dict(side="warded_jar_side", lid="warded_jar_lid"),
        tags=[]),
    "alchemical_furnace": dict(
        name="Alchemical Furnace", model="bottom_top", loot="self",
        textures=dict(top="alchemical_furnace_top", side="alchemical_furnace_side", bottom="alchemical_furnace_top"),
        tags=["minecraft:mineable/pickaxe"]),
    "arcane_stone": dict(
        name="Arcane Stone", model="cube", loot="self",
        tags=["minecraft:mineable/pickaxe"]),
    "arcane_stone_bricks": dict(
        name="Arcane Stone Bricks", model="cube", loot="self",
        tags=["minecraft:mineable/pickaxe"]),
    "thaumium_block": dict(
        name="Block of Thaumium", model="cube", loot="self",
        tags=["minecraft:mineable/pickaxe", "minecraft:needs_iron_tool", "minecraft:beacon_base_blocks",
              "c:storage_blocks", "c:storage_blocks/thaumium"]),
    "brass_block": dict(
        name="Block of Brass", model="cube", loot="self",
        tags=["minecraft:mineable/pickaxe", "minecraft:needs_stone_tool", "c:storage_blocks", "c:storage_blocks/brass"]),
    "amber_block": dict(
        name="Block of Amber", model="cube", loot="self",
        tags=["minecraft:mineable/pickaxe", "c:storage_blocks", "c:storage_blocks/amber"]),
    "cinnabar_ore": dict(
        name="Cinnabar Ore", model="cube", loot="ore:thaumicunity:cinnabar",
        tags=["minecraft:mineable/pickaxe", "minecraft:needs_iron_tool", "c:ores", "c:ores/cinnabar",
              "c:ores_in_ground/stone"]),
    "amber_ore": dict(
        name="Amber Ore", model="cube", loot="ore:thaumicunity:amber",
        tags=["minecraft:mineable/pickaxe", "minecraft:needs_stone_tool", "c:ores", "c:ores/amber",
              "c:ores_in_ground/stone"]),
}

# Block tags whose block items get the same item tag (c: material tags).
MIRRORED_ITEM_TAGS = ("c:storage_blocks", "c:storage_blocks/thaumium", "c:storage_blocks/brass", "c:storage_blocks/amber",
                      "c:ores", "c:ores/cinnabar", "c:ores/amber", "c:ores_in_ground/stone")

# Extra block tag entries that are not our blocks.
BLOCK_TAGS = {
    "thaumicunity:crucible_heat_sources": ["minecraft:fire", "minecraft:soul_fire", "minecraft:lava", "minecraft:magma_block",
                                           "#minecraft:campfires", "thaumicunity:alchemical_furnace"],
    "thaumicunity:infusion_stabilizers": ["#minecraft:candles", "minecraft:skeleton_skull", "minecraft:wither_skeleton_skull",
                                          "minecraft:zombie_head", "minecraft:creeper_head", "minecraft:amethyst_cluster",
                                          "minecraft:enchanting_table", "minecraft:bookshelf"],
    "thaumicunity:magic_immune": ["minecraft:bedrock", "minecraft:barrier", "minecraft:end_portal_frame",
                                  "minecraft:end_portal", "minecraft:nether_portal", "minecraft:end_gateway",
                                  "minecraft:command_block", "minecraft:chain_command_block",
                                  "minecraft:repeating_command_block", "minecraft:structure_block", "minecraft:jigsaw",
                                  "minecraft:reinforced_deepslate", "thaumicunity:runic_matrix"],
}

# Simple items: name -> (English name, texture override or None, item tags)
ITEMS = {
    "thaumonomicon": ("Thaumonomicon", []),
    "thaumometer": ("Thaumometer", []),
    "goggles_of_revealing": ("Goggles of Revealing", ["thaumicunity:revealing", "minecraft:head_armor"]),
    "scribing_tools": ("Scribing Tools", []),
    "research_notes": ("Research Notes", []),
    "salis_mundus": ("Salis Mundus", []),
    "phial": ("Essentia Phial", []),
    "quicksilver": ("Quicksilver", []),
    "amber": ("Amber", ["c:gems", "c:gems/amber"]),
    "cinnabar": ("Cinnabar", ["c:raw_materials", "c:raw_materials/cinnabar"]),
    "alumentum": ("Alumentum", []),
    "nitor": ("Nitor", []),
    "tallow": ("Magic Tallow", []),
    "enchanted_fabric": ("Enchanted Fabric", []),
    "thaumium_ingot": ("Thaumium Ingot", ["c:ingots", "c:ingots/thaumium"]),
    "thaumium_nugget": ("Thaumium Nugget", ["c:nuggets", "c:nuggets/thaumium"]),
    "brass_ingot": ("Brass Ingot", ["c:ingots", "c:ingots/brass"]),
    "brass_nugget": ("Brass Nugget", ["c:nuggets", "c:nuggets/brass"]),
    "aer_vis_crystal": ("Aer Vis Crystal", ["thaumicunity:vis_crystals"]),
    "terra_vis_crystal": ("Terra Vis Crystal", ["thaumicunity:vis_crystals"]),
    "ignis_vis_crystal": ("Ignis Vis Crystal", ["thaumicunity:vis_crystals"]),
    "aqua_vis_crystal": ("Aqua Vis Crystal", ["thaumicunity:vis_crystals"]),
    "ordo_vis_crystal": ("Ordo Vis Crystal", ["thaumicunity:vis_crystals"]),
    "perditio_vis_crystal": ("Perditio Vis Crystal", ["thaumicunity:vis_crystals"]),
    "basic_caster": ("Caster's Gauntlet", ["thaumicunity:casting_instruments"]),
    "thaumium_caster": ("Thaumium Caster's Gauntlet", ["thaumicunity:casting_instruments"]),
    "apprentice_focus": ("Apprentice's Focus", []),
    "adept_focus": ("Adept's Focus", []),
    "archmage_focus": ("Archmage's Focus", []),
}

# Extra item tag entries.
ITEM_TAGS = {
    "thaumicunity:research_paper": ["minecraft:paper"],
    "thaumicunity:casting_instruments": ["thaumicunity:wand"],
}
