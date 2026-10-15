"""Which Thaumcraft texture dresses which Thaumic Unity texture, for players who own Thaumcraft.

Used by the game itself (gen_content.py writes it to assets/thaumicunity/thaumcraft_import.json, which the client's
ThaumcraftTextures reads to build a resource pack from a Thaumcraft jar in <game dir>/thaumicunity/) and by
tools/import_thaumcraft_textures.py. Paths are relative to assets/thaumcraft/textures/ and tried in order; where
none exists, any texture of the same kind whose file name contains every word of the Thaumic Unity name is used.
No Thaumcraft file is part of this project.
"""

KNOWN = {
    "item/thaumonomicon": ["items/thaumonomicon.png", "items/thaumonomiconcheat.png"],
    "item/thaumometer": ["items/thaumometer.png", "items/scanner.png"],
    "item/goggles_of_revealing": ["items/goggles.png", "items/goggles_revealing.png"],
    "item/scribing_tools": ["items/scribing_tools.png", "items/scribingtools.png", "items/inkwell.png"],
    "item/research_notes": ["items/researchnotes.png", "items/research_notes.png", "items/notes.png"],
    "item/salis_mundus": ["items/salis_mundus.png", "items/salismundus.png"],
    "item/phial": ["items/phial.png", "items/phial_empty.png", "items/essentia_phial.png"],
    "item/quicksilver": ["items/quicksilver.png"],
    "item/amber": ["items/amber.png", "items/amberalt.png"],
    "item/cinnabar": ["items/cinnabar.png", "items/cluster_cinnabar.png", "items/cinnibar.png"],
    "item/alumentum": ["items/alumentum.png"],
    "item/nitor": ["items/nitor.png", "items/nitor/nitor_yellow.png", "items/nitor_yellow.png"],
    "item/tallow": ["items/tallow.png"],
    "item/enchanted_fabric": ["items/fabric.png", "items/enchantedfabric.png", "items/clothic.png"],
    "item/thaumium_ingot": ["items/ingot_thaumium.png", "items/thaumiumingot.png"],
    "item/thaumium_nugget": ["items/nugget_thaumium.png", "items/nuggetthaumium.png"],
    "item/brass_ingot": ["items/ingot_brass.png", "items/brassingot.png"],
    "item/brass_nugget": ["items/nugget_brass.png", "items/nuggetbrass.png"],
    "item/basic_caster": ["items/caster_basic.png", "items/casters_basic.png", "items/caster.png"],
    "item/thaumium_caster": ["items/caster_thaumium.png", "items/casters_thaumium.png"],
    "item/apprentice_focus": ["items/focus_1.png", "items/focus_blank.png", "items/focus_fire.png"],
    "item/adept_focus": ["items/focus_2.png", "items/focus_frost.png"],
    "item/archmage_focus": ["items/focus_3.png", "items/focus_primal.png"],
    "block/arcane_stone": ["blocks/arcane_stone.png", "blocks/arcanestone.png", "blocks/es_1.png"],
    "block/arcane_stone_bricks": ["blocks/arcane_stone_brick.png", "blocks/arcane_brick.png", "blocks/es_2.png"],
    "block/thaumium_block": ["blocks/metal_thaumium.png", "blocks/thaumiumblock.png"],
    "block/brass_block": ["blocks/metal_brass.png", "blocks/brassblock.png"],
    "block/amber_block": ["blocks/amber_block.png", "blocks/amberblock.png"],
    "block/cinnabar_ore": ["blocks/ore_cinnabar.png", "blocks/cinnibar.png", "blocks/cinnabar.png"],
    "block/amber_ore": ["blocks/ore_amber.png", "blocks/amberore.png"],
    "block/crucible_side": ["blocks/crucible_side.png", "blocks/crucible1.png", "blocks/crucible3.png"],
    "block/crucible_top": ["blocks/crucible_top.png", "blocks/crucible4.png"],
    "block/crucible_inner": ["blocks/crucible_inner.png", "blocks/crucible2.png"],
    "block/crucible_bottom": ["blocks/crucible_bottom.png", "blocks/crucible5.png"],
    "block/warded_jar_side": ["blocks/jar_side.png", "blocks/jar_side_warded.png"],
    "block/warded_jar_lid": ["blocks/jar_top.png", "blocks/jar_top_warded.png"],
    "block/arcane_pedestal_side": ["blocks/pedestal_side.png", "blocks/pedestal_arcane_side.png"],
    "block/arcane_pedestal_top": ["blocks/pedestal_top.png", "blocks/pedestal_arcane_top.png"],
    "block/runic_matrix": ["blocks/infusion_matrix.png", "blocks/infusionmatrix.png", "blocks/matrix.png"],
    "block/alchemical_furnace_side": ["blocks/al_furnace_front_off.png", "blocks/al_furnace_front.png",
                                      "blocks/smelter_front.png", "blocks/smelter_side.png"],
    "block/alchemical_furnace_top": ["blocks/al_furnace_top.png", "blocks/smelter_top.png"],
    "block/arcane_workbench_top": ["blocks/arcane_workbench_top.png", "blocks/worktable_top.png"],
    "block/arcane_workbench_side": ["blocks/arcane_workbench_side.png", "blocks/worktable_side.png"],
    "block/arcane_workbench_bottom": ["blocks/arcane_workbench_bottom.png", "blocks/worktable_bottom.png"],
    "block/research_table_top": ["blocks/research_table_top.png", "blocks/restable_top.png"],
    "block/research_table_side": ["blocks/research_table_side.png", "blocks/restable_side.png"],
    # Wands (phase 4.1)
    "item/reed_wand_core": ["items/wand_rod_reed.png", "items/rod_reed.png"],
    "item/obsidian_wand_core": ["items/wand_rod_obsidian.png", "items/rod_obsidian.png"],
    "item/blaze_wand_core": ["items/wand_rod_blaze.png", "items/rod_blaze.png"],
    "item/ice_wand_core": ["items/wand_rod_ice.png", "items/rod_ice.png"],
    "item/quartz_wand_core": ["items/wand_rod_quartz.png", "items/rod_quartz.png"],
    "item/bone_wand_core": ["items/wand_rod_bone.png", "items/rod_bone.png"],
    "item/iron_wand_cap": ["items/wand_cap_iron.png", "items/cap_iron.png"],
    "item/copper_wand_cap": ["items/wand_cap_copper.png", "items/cap_copper.png"],
    "item/gold_wand_cap": ["items/wand_cap_gold.png", "items/cap_gold.png"],
    "item/brass_wand_cap": ["items/wand_cap_brass.png", "items/cap_brass.png"],
    "item/thaumium_wand_cap": ["items/wand_cap_thaumium.png", "items/cap_thaumium.png"],
    "block/wand_pedestal_side": ["blocks/pedestal_side.png", "blocks/recharge_pedestal_side.png"],
    "block/wand_pedestal_top": ["blocks/pedestal_top.png", "blocks/recharge_pedestal_top.png"],
    # Essentia transport (phase 4.2)
    "block/essentia_tube": ["blocks/pipe_1.png", "blocks/pipe.png", "blocks/tube.png", "blocks/tube_1.png"],
    "block/essentia_valve": ["blocks/pipe_valve.png", "blocks/valve.png", "blocks/tube_valve.png"],
    "block/essentia_buffer_side": ["blocks/pipe_buffer.png", "blocks/essentia_buffer.png", "blocks/tube_buffer.png"],
    "block/essentia_buffer_top": ["blocks/pipe_buffer.png", "blocks/essentia_buffer.png", "blocks/tube_buffer.png"],
    "block/centrifuge_side": ["blocks/centrifuge_side.png", "blocks/centrifuge.png"],
    "block/centrifuge_top": ["blocks/centrifuge_top.png", "blocks/centrifuge.png"],
    "block/centrifuge_bottom": ["blocks/centrifuge_bottom.png", "blocks/centrifuge.png"],
    # Thaumonomicon map backgrounds
    "gui/research/fundamentals": ["gui/gui_researchback.png", "gui/gui_research_back_1.png"],
    "gui/research/alchemy": ["gui/gui_researchback.png", "gui/gui_research_back_2.png"],
    "gui/research/artifice": ["gui/gui_researchback.png", "gui/gui_research_back_3.png"],
    "gui/research/infusion": ["gui/gui_researchback.png", "gui/gui_research_back_4.png"],
    "gui/research/auromancy": ["gui/gui_researchback.png", "gui/gui_research_back_5.png"],
}

# Thaumcraft draws these grey and colours them in code: target -> candidates; the colour is the primal's.
TINTED_CRYSTALS = ["items/crystal_essence.png", "items/vis_crystal.png", "items/shard.png"]

# Words that say nothing about which texture is meant.
NOISE = ["item", "block", "of", "the", "basic", "gui", "research"]

