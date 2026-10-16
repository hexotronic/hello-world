"""Flora, crystal clusters and aura nodes (phase 4.5). The Java side (common/registry/TUFlora.java) registers the
blocks; this table drives their models, loot, tags, names, aspects, recipes, research and world generation. The game
test `flora_and_nodes` checks that the generated features exist and work.

Model kinds added for flora (see blocks.py for the others):
  log      pillar with <name> on the sides and <name>_top on the ends, rotated by its axis
  leaves   one texture, cut out
  cross    two crossed planes (saplings and plants); the item shows the block texture
  cluster  crossed planes rotated to the face the cluster grows from
Loot kinds added: leaves:<sapling> (shears or silk touch keep the leaves, otherwise a sapling now and then and
sticks), cluster:<item> (silk touch keeps the cluster, otherwise one or two of the item, more with Fortune).
"""

PRIMALS = ("aer", "terra", "ignis", "aqua", "ordo", "perditio")

# Woods: id -> English name
WOODS = {
    "greatwood": "Greatwood",
    "silverwood": "Silverwood",
}

# Plants: id -> (English name, block tag of the ground it grows on, light level)
PLANTS = {
    "shimmerleaf": ("Shimmerleaf", "thaumicunity:shimmerleaf_soil", 6),
    "cinderpearl": ("Cinderpearl", "thaumicunity:cinderpearl_soil", 10),
    "vishroom": ("Vishroom", "thaumicunity:vishroom_soil", 4),
}

# Where plants may grow (block tags written by gen_content).
SOIL_TAGS = {
    "thaumicunity:shimmerleaf_soil": ["#minecraft:dirt"],
    "thaumicunity:cinderpearl_soil": ["#minecraft:sand", "#minecraft:dirt", "#minecraft:terracotta"],
    "thaumicunity:vishroom_soil": ["#minecraft:dirt", "minecraft:mycelium", "#minecraft:base_stone_overworld"],
}


def blocks():
    """Block table entries in the format of blocks.BLOCKS (merged there)."""
    out = {}
    for wood, name in WOODS.items():
        out[f"{wood}_log"] = dict(
            name=f"{name} Log", model="log", loot="self",
            tags=["minecraft:logs", "minecraft:logs_that_burn", "minecraft:mineable/axe", f"thaumicunity:{wood}_logs"],
            item_tags=["minecraft:logs", "minecraft:logs_that_burn", f"thaumicunity:{wood}_logs"])
        out[f"{wood}_planks"] = dict(
            name=f"{name} Planks", model="cube", loot="self",
            tags=["minecraft:planks", "minecraft:mineable/axe"], item_tags=["minecraft:planks"])
        out[f"{wood}_leaves"] = dict(
            name=f"{name} Leaves", model="leaves", loot=f"leaves:thaumicunity:{wood}_sapling",
            tags=["minecraft:leaves", "minecraft:mineable/hoe"], item_tags=["minecraft:leaves"])
        out[f"{wood}_sapling"] = dict(
            name=f"{name} Sapling", model="cross", loot="self",
            tags=["minecraft:saplings"], item_tags=["minecraft:saplings"])
    for plant, (name, soil, light) in PLANTS.items():
        out[plant] = dict(
            name=name, model="cross", loot="self",
            tags=["minecraft:small_flowers", "thaumicunity:magical_plants"],
            item_tags=["minecraft:small_flowers", "thaumicunity:magical_plants"])
    for primal in PRIMALS:
        out[f"{primal}_crystal_cluster"] = dict(
            name=f"{primal.capitalize()} Crystal Cluster", model="cluster",
            loot=f"cluster:thaumicunity:{primal}_vis_crystal",
            tags=["minecraft:mineable/pickaxe", "thaumicunity:crystal_clusters"],
            item_tags=["thaumicunity:crystal_clusters"])
    out["aura_node"] = dict(name="Aura Node", model="invisible", loot="none", tags=["thaumicunity:magic_immune"])
    return out


# --- recipes

SHAPELESS = {
    "greatwood_planks": (["#thaumicunity:greatwood_logs"], "thaumicunity:greatwood_planks", 4),
    "silverwood_planks": (["#thaumicunity:silverwood_logs"], "thaumicunity:silverwood_planks", 4),
    "quicksilver_from_shimmerleaf": (["thaumicunity:shimmerleaf"], "thaumicunity:quicksilver", 1),
    "blaze_powder_from_cinderpearl": (["thaumicunity:cinderpearl"], "minecraft:blaze_powder", 1),
}

# Arcane: id -> (pattern, key, result, count, vis, research)
ARCANE = {
    "wand/greatwood_wand_core": ([" G", "G "], {"G": "thaumicunity:greatwood_log"}, "thaumicunity:greatwood_wand_core", 1,
                                 "aer 5 terra 5 ordo 5 perditio 5", "auromancy/greatwood_rod"),
}

# Infusion: id -> (central, components, essentia, instability, result, research)
INFUSION = {
    "infusion/silverwood_wand_core": ("thaumicunity:silverwood_log",
                                      ["thaumicunity:shimmerleaf", "thaumicunity:ordo_vis_crystal", "thaumicunity:quicksilver",
                                       "thaumicunity:aer_vis_crystal"],
                                      "praecantatio 16 ordo 16 sano 8", 3, "thaumicunity:silverwood_wand_core",
                                      "auromancy/silverwood_rod"),
}

# --- aspects

ITEM_ASPECTS = {
    "thaumicunity:greatwood_log": "arbor 4 praecantatio 1 lignum 2",
    "thaumicunity:greatwood_planks": "arbor 1 lignum 1",
    "thaumicunity:greatwood_leaves": "herba 1 arbor 1",
    "thaumicunity:greatwood_sapling": "herba 2 arbor 2 praecantatio 1",
    "thaumicunity:silverwood_log": "arbor 4 praecantatio 2 ordo 2 sano 1",
    "thaumicunity:silverwood_planks": "arbor 1 praecantatio 1",
    "thaumicunity:silverwood_leaves": "herba 1 praecantatio 1 ordo 1",
    "thaumicunity:silverwood_sapling": "herba 2 arbor 1 praecantatio 2 ordo 1",
    "thaumicunity:shimmerleaf": "herba 2 praecantatio 3 aer 1 hydrargyrum 2",
    "thaumicunity:cinderpearl": "herba 2 ignis 4 potentia 1",
    "thaumicunity:vishroom": "herba 2 praecantatio 2 mortuus 1 venenum 1",
    **{f"thaumicunity:{p}_crystal_cluster": f"{p} 6 vitreus 2 auram 1" for p in PRIMALS},
}

# --- world generation

# Tree features (vanilla "minecraft:tree" format of 26.3).
TREES = {
    "greatwood_tree": dict(
        log="thaumicunity:greatwood_log", leaves="thaumicunity:greatwood_leaves",
        trunk={"type": "minecraft:fancy_trunk_placer", "base_height": 8, "height_rand_a": 6, "height_rand_b": 0},
        foliage={"type": "minecraft:fancy_foliage_placer", "height": 4, "offset": 4, "radius": 3},
        size={"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 2}),
    "silverwood_tree": dict(
        log="thaumicunity:silverwood_log", leaves="thaumicunity:silverwood_leaves",
        trunk={"type": "minecraft:straight_trunk_placer", "base_height": 9, "height_rand_a": 3, "height_rand_b": 1},
        foliage={"type": "minecraft:blob_foliage_placer", "height": 4, "offset": 0, "radius": 3},
        size={"type": "minecraft:two_layers_feature_size", "limit": 1, "lower_size": 0, "upper_size": 1}),
}

# Sapling -> tree feature it grows into.
SAPLING_TREES = {"greatwood_sapling": "greatwood_tree", "silverwood_sapling": "silverwood_tree"}

# Placed features: name -> (feature, biomes, generation step, placement modifiers (built by gen_content from kind))
#   kind "tree": (chance, sapling); "patch": (chance, tries); "underground": (count, min y, max y);
#   "surface": chance
PLACED = {
    "greatwood_trees": ("greatwood_tree", "#minecraft:is_forest", "vegetal_decoration", ("tree", 6, "greatwood_sapling")),
    "greatwood_trees_dark_forest": ("greatwood_tree", "minecraft:dark_forest", "vegetal_decoration",
                                    ("tree", 3, "greatwood_sapling")),
    "silverwood_trees": ("silverwood_tree_with_node", "#thaumicunity:silverwood_biomes", "vegetal_decoration",
                         ("tree", 24, "silverwood_sapling")),
    "shimmerleaf_patch": ("shimmerleaf_patch", "#thaumicunity:silverwood_biomes", "vegetal_decoration", ("patch", 20, 12)),
    "cinderpearl_patch": ("cinderpearl_patch", "#thaumicunity:cinderpearl_biomes", "vegetal_decoration", ("patch", 10, 16)),
    "vishroom_patch": ("vishroom_patch", "#thaumicunity:vishroom_biomes", "vegetal_decoration", ("patch", 12, 12)),
    "crystal_clusters": ("crystal_clusters", "#minecraft:is_overworld", "underground_decoration", ("underground", 8, -56, 40)),
    "aura_nodes": ("aura_node", "#minecraft:is_overworld", "top_layer_modification", ("surface", 14)),
}

# Biome tags for flora (written by gen_content).
BIOME_TAGS = {
    "thaumicunity:silverwood_biomes": ["#minecraft:is_forest", "minecraft:plains", "minecraft:meadow",
                                       "minecraft:flower_forest", "minecraft:cherry_grove", "#c:is_magical"],
    "thaumicunity:cinderpearl_biomes": ["#c:is_desert", "#minecraft:is_badlands", "#minecraft:is_savanna"],
    "thaumicunity:vishroom_biomes": ["minecraft:dark_forest", "minecraft:pale_garden", "#c:is_swamp", "#c:is_spooky",
                                     "minecraft:mushroom_fields"],
}

# --- research

RESEARCH = {
    "alchemy/magical_flora": dict(
        title="Magical Flora", x=6, y=0, icon="thaumicunity:shimmerleaf",
        parents=["alchemy/crucible"], aspects=["herba", "praecantatio"],
        stages=[
            dict(text=(
                "Where the aura runs strong, plants drink it. Shimmerleaf grows near silverwood trees, cinderpearl in "
                "hot, dry places and vishroom in dark woods.\n\nScan one of these magical plants."),
                requirements=[("scan", "item_tag:thaumicunity:magical_plants")]),
            dict(text=(
                "Shimmerleaf holds quicksilver in its leaves and gives it up when crushed; cinderpearl crumbles into "
                "blaze powder. Vishroom is rich in Praecantatio.\n\nTwo trees stand out among the others. Greatwood "
                "grows huge and branching in forests, and its timber holds magic well. Silverwood is rare, pale and "
                "straight, and an aura node often hides among its leaves."),
                recipes=["quicksilver_from_shimmerleaf", "blaze_powder_from_cinderpearl", "greatwood_planks",
                         "silverwood_planks"])]),
    "alchemy/crystal_clusters": dict(
        title="Crystal Clusters", x=6, y=-2, icon="thaumicunity:ignis_crystal_cluster",
        parents=["alchemy/vis_crystals"], aspects=["vitreus", "auram"],
        stages=[
            dict(text="Deep in caves, vis settles into crystal.\n\nScan a crystal cluster.",
                 requirements=[("scan", "item_tag:thaumicunity:crystal_clusters")]),
            dict(text=(
                "Crystal clusters grow on cave walls in the colour of one primal, most often the one the place above "
                "resonates with. Broken, they give up vis crystals; broken with a silk touch, they stay whole."))]),
    "auromancy/aura_nodes": dict(
        title="Aura Nodes", x=4, y=-4, icon="thaumicunity:thaumometer",
        parents=["auromancy/wands", "fundamentals/aura"], aspects=["auram", "potentia"],
        stages=[
            dict(text=(
                "Here and there the aura gathers into a knot of light that hangs in the air: an aura node.\n\nScan "
                "an aura node with a thaumometer."),
                requirements=[("scan", "block:thaumicunity:aura_node")]),
            dict(text=(
                "A node holds vis of some of the six primals and slowly refills itself. It also feeds the aura of "
                "the place around it. Use a wand on a node to draw a little of each primal it holds into the wand.\n\n"
                "Nodes differ. Pure nodes wash flux out of the air; dark and tainted nodes add to it; hungry nodes "
                "drink the aura around them; unstable nodes flare up now and then. Bright nodes refill faster, pale "
                "nodes slower, and fading nodes not at all: they dwindle away."))]),
    "auromancy/greatwood_rod": dict(
        title="Greatwood Rods", x=2, y=-6, icon="thaumicunity:greatwood_wand_core",
        parents=["auromancy/elemental_cores", "alchemy/magical_flora"], aspects=["arbor", "praecantatio"],
        stages=[
            dict(text="Greatwood holds vis better than ordinary wood.\n\nObtain a greatwood log.",
                 requirements=[("obtain", "thaumicunity:greatwood_log", 1)]),
            dict(text="A rod of greatwood holds 50 of each primal, with no affinity for any of them.",
                 recipes=["wand/greatwood_wand_core"]
                + [f"wand/greatwood_{cap}" for cap in ("iron", "copper", "gold", "brass", "thaumium")])]),
    "auromancy/silverwood_rod": dict(
        title="Silverwood Rods", x=4, y=-6, icon="thaumicunity:silverwood_wand_core",
        parents=["auromancy/greatwood_rod", "auromancy/aura_nodes", "infusion/runic_matrix"], aspects=["ordo", "sano"],
        stages=[
            dict(text="Silverwood grows around aura nodes and takes on their order.\n\nObtain a silverwood log.",
                 requirements=[("obtain", "thaumicunity:silverwood_log", 1)]),
            dict(text=(
                "Infused with Praecantatio, Ordo and Sano, a silverwood log becomes the finest rod a thaumaturge can "
                "make from wood: it holds 100 of each primal."),
                recipes=["infusion/silverwood_wand_core"]
                + [f"wand/silverwood_{cap}" for cap in ("iron", "copper", "gold", "brass", "thaumium")])]),
}

# --- Thaumcraft texture names (merged into thaumcraft_import.KNOWN)

THAUMCRAFT_TEXTURES = {
    "block/greatwood_log": ["blocks/greatwood_side.png", "blocks/greatwoodside.png", "blocks/log_greatwood.png"],
    "block/greatwood_log_top": ["blocks/greatwood_top.png", "blocks/greatwoodtop.png", "blocks/log_greatwood_top.png"],
    "block/greatwood_planks": ["blocks/greatwood_planks.png", "blocks/planks_greatwood.png", "blocks/greatwoodplanks.png"],
    "block/greatwood_leaves": ["blocks/greatwood_leaves.png", "blocks/greatwoodleaves.png", "blocks/leaves_greatwood.png"],
    "block/greatwood_sapling": ["blocks/greatwood_sapling.png", "blocks/greatwoodsapling.png", "blocks/sapling_greatwood.png"],
    "block/silverwood_log": ["blocks/silverwood_side.png", "blocks/silverwoodside.png", "blocks/log_silverwood.png"],
    "block/silverwood_log_top": ["blocks/silverwood_top.png", "blocks/silverwoodtop.png", "blocks/log_silverwood_top.png"],
    "block/silverwood_planks": ["blocks/silverwood_planks.png", "blocks/planks_silverwood.png", "blocks/silverwoodplanks.png"],
    "block/silverwood_leaves": ["blocks/silverwood_leaves.png", "blocks/silverwoodleaves.png", "blocks/leaves_silverwood.png"],
    "block/silverwood_sapling": ["blocks/silverwood_sapling.png", "blocks/silverwoodsapling.png",
                                 "blocks/sapling_silverwood.png"],
    "block/shimmerleaf": ["blocks/shimmerleaf.png", "blocks/flower_shimmerleaf.png"],
    "block/cinderpearl": ["blocks/cinderpearl.png", "blocks/flower_cinderpearl.png"],
    "block/vishroom": ["blocks/vishroom.png", "blocks/flower_vishroom.png"],
    "item/greatwood_wand_core": ["items/wand_rod_greatwood.png", "items/rod_greatwood.png"],
    "item/silverwood_wand_core": ["items/wand_rod_silverwood.png", "items/rod_silverwood.png"],
}

# --- aura node names (common/aura/node/NodeType and NodeModifier)

NODE_TYPES = {"normal": "Normal", "pure": "Pure", "dark": "Dark", "hungry": "Hungry", "unstable": "Unstable",
              "tainted": "Tainted"}
NODE_MODIFIERS = {"none": "", "bright": "Bright", "pale": "Pale", "fading": "Fading"}

LANG = {
    "node.thaumicunity.name": "%s Aura Node",
    "node.thaumicunity.name_modified": "%2$s %1$s Aura Node",
    "message.thaumicunity.wand.node_drawn": "Drew %s vis from the %s",
    "message.thaumicunity.wand.node_empty": "The node has no vis left to give",
    "message.thaumicunity.wand.full": "The wand cannot hold any more vis",
}
