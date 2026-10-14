"""World data: biome aura profiles, alchemical fuels and infusion stabilisers (data maps).

Aura attunement weights are in the order aer, terra, ignis, aqua, ordo, perditio (1.0 = neutral). Entries are
applied in order, so broad biome tags come first and specific biomes override them.
"""

AURA_PROFILES = [
    # (biome or #tag, base vis, attunement)
    ("#minecraft:is_overworld", 100, [1, 1, 1, 1, 1, 1]),
    ("#minecraft:is_ocean", 90, [1.1, 0.7, 0.6, 1.8, 1.0, 0.8]),
    ("#minecraft:is_deep_ocean", 110, [0.9, 0.6, 0.4, 2.0, 1.1, 1.0]),
    ("#minecraft:is_beach", 95, [1.3, 0.9, 0.9, 1.4, 1.0, 0.7]),
    ("#minecraft:is_river", 105, [1.0, 1.0, 0.8, 1.6, 1.1, 0.7]),
    ("#minecraft:is_mountain", 120, [1.6, 1.4, 0.7, 0.8, 1.2, 0.6]),
    ("#minecraft:is_hill", 110, [1.4, 1.3, 0.8, 0.9, 1.1, 0.7]),
    ("#minecraft:is_forest", 125, [1.0, 1.4, 0.7, 1.1, 1.0, 0.8]),
    ("#minecraft:is_taiga", 115, [1.1, 1.3, 0.6, 1.0, 1.2, 0.8]),
    ("#minecraft:is_jungle", 140, [1.0, 1.5, 0.9, 1.4, 0.7, 1.0]),
    ("#minecraft:is_savanna", 100, [1.3, 1.1, 1.5, 0.6, 0.9, 0.9]),
    ("#minecraft:is_badlands", 90, [1.0, 1.5, 1.6, 0.4, 0.8, 1.1]),
    ("#minecraft:is_nether", 160, [0.5, 1.0, 2.2, 0.3, 0.6, 1.6]),
    ("#minecraft:is_end", 180, [1.2, 0.6, 0.5, 0.5, 1.8, 1.4]),
    ("#c:is_snowy", 100, [1.2, 1.0, 0.4, 1.2, 1.5, 0.8]),
    ("#c:is_desert", 85, [1.4, 1.2, 1.8, 0.3, 0.9, 1.0]),
    ("#c:is_swamp", 120, [0.8, 1.1, 0.6, 1.6, 0.7, 1.5]),
    ("#c:is_spooky", 130, [0.8, 1.0, 0.7, 0.9, 0.6, 1.8]),
    ("#c:is_magical", 200, [1.2, 1.2, 1.2, 1.2, 1.2, 1.2]),
    ("#c:is_cave", 95, [0.6, 1.8, 0.9, 1.0, 1.0, 1.1]),
    ("minecraft:plains", 100, [1.3, 1.2, 1.0, 1.0, 1.0, 0.8]),
    ("minecraft:sunflower_plains", 110, [1.3, 1.2, 1.3, 0.9, 1.0, 0.7]),
    ("minecraft:meadow", 125, [1.4, 1.2, 0.9, 1.0, 1.2, 0.6]),
    ("minecraft:flower_forest", 150, [1.2, 1.3, 1.0, 1.1, 1.3, 0.5]),
    ("minecraft:cherry_grove", 160, [1.4, 1.1, 0.9, 1.0, 1.4, 0.5]),
    ("minecraft:dark_forest", 140, [0.7, 1.4, 0.6, 1.0, 0.7, 1.6]),
    ("minecraft:pale_garden", 150, [0.8, 1.0, 0.4, 0.9, 0.8, 2.0]),
    ("minecraft:mushroom_fields", 220, [0.9, 1.4, 0.6, 1.3, 1.0, 1.2]),
    ("minecraft:ice_spikes", 130, [1.2, 0.8, 0.2, 1.3, 2.0, 0.7]),
    ("minecraft:lush_caves", 150, [0.9, 1.5, 0.6, 1.5, 1.1, 0.7]),
    ("minecraft:dripstone_caves", 110, [0.6, 1.9, 0.8, 1.3, 1.2, 0.8]),
    ("minecraft:deep_dark", 170, [0.4, 1.3, 0.4, 0.7, 0.5, 2.4]),
    ("minecraft:soul_sand_valley", 170, [0.6, 0.9, 1.6, 0.2, 0.5, 2.2]),
    ("minecraft:warped_forest", 165, [0.9, 1.0, 1.4, 0.5, 1.4, 1.2]),
    ("minecraft:crimson_forest", 165, [0.6, 1.2, 2.0, 0.3, 0.8, 1.4]),
    ("minecraft:basalt_deltas", 155, [0.8, 1.6, 2.0, 0.2, 0.6, 1.3]),
    ("minecraft:the_void", 10, [1, 1, 1, 1, 1, 1]),
]

# Item -> burn ticks in the alchemical furnace. Thaumic fuels burn far longer than coal.
ALCHEMICAL_FUELS = {
    "minecraft:coal": 1600,
    "minecraft:charcoal": 1600,
    "minecraft:coal_block": 16000,
    "minecraft:blaze_rod": 2400,
    "minecraft:blaze_powder": 1200,
    "minecraft:lava_bucket": 20000,
    "minecraft:dried_kelp_block": 4000,
    "#minecraft:logs_that_burn": 300,
    "#minecraft:planks": 300,
    "thaumicunity:alumentum": 6400,
    "thaumicunity:tallow": 800,
}

# Block -> stability added when placed near a runic matrix (blocks also need the infusion_stabilizers tag).
INFUSION_STABILIZERS = {
    "#minecraft:candles": 0.5,
    "minecraft:skeleton_skull": 1.0,
    "minecraft:wither_skeleton_skull": 1.5,
    "minecraft:zombie_head": 1.0,
    "minecraft:creeper_head": 1.0,
    "minecraft:amethyst_cluster": 0.75,
    "minecraft:enchanting_table": 2.0,
    "minecraft:bookshelf": 0.2,
}
