#!/usr/bin/env python3
"""Generates Thaumic Unity's data and asset files from the tables in tools/content/.

Run from the repository root:  python3 tools/gen_content.py   (textures: python3 tools/gen_textures.py)
Outputs (all committed, regenerate after editing a table):
  data/thaumicunity/thaumicunity/{aspect,research_category,research,spell_component,aspect_rule}/   registries
  data/thaumicunity/{recipe,loot_table/blocks,data_maps,worldgen,neoforge/biome_modifier}/
  data/{minecraft,c,thaumicunity}/tags/
  assets/thaumicunity/{blockstates,models,items,particles}/ and sounds.json
  datapacks/modern_combinations/                                   optional built-in datapack
  assets/thaumicunity/lang/en_us.json                              merged with tools/content/lang_static.json
Every generator validates its table and exits non-zero on problems, so a broken table never reaches the jar.
"""
import json
import os
import sys
from collections import Counter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools", "content"))

import aspects  # noqa: E402
import aspect_lore  # noqa: E402
import blocks  # noqa: E402
import equipment  # noqa: E402
import item_aspects  # noqa: E402
import recipes  # noqa: E402
import research  # noqa: E402
import spells  # noqa: E402
import thaumcraft_import  # noqa: E402
import wands  # noqa: E402
import world  # noqa: E402

NS = "thaumicunity"
RES = os.path.join(ROOT, "src", "main", "resources")
DATA = os.path.join(RES, "data")
ASSETS = os.path.join(RES, "assets", NS)
# Data pack format of Minecraft 26.3 (version.json: pack_version.data_major).
DATA_PACK_FORMAT = 121
GENERATORS = []


def generator(fn):
    GENERATORS.append(fn)
    return fn


def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def clean_dir(path, suffix=".json"):
    if os.path.isdir(path):
        for name in os.listdir(path):
            if name.endswith(suffix):
                os.remove(os.path.join(path, name))


def rid(path):
    return f"{NS}:{path}"


def validate_aspects(rows, label):
    ids = [r[0] for r in rows]
    problems = [f"duplicate aspect {k}" for k, v in Counter(ids).items() if v > 1]
    pairs = Counter(tuple(sorted(r[2])) for r in rows if r[2])
    problems += [f"pair {k} used {v} times" for k, v in pairs.items() if v > 1]
    known = set(ids)
    problems += [f"{r[0]} uses unknown {c}" for r in rows for c in r[2] if c not in known]
    problems += [f"{r[0]} must have 0 or 2 components" for r in rows if len(r[2]) not in (0, 2)]
    # acyclic: resolve tiers
    tiers = {r[0]: 0 for r in rows if not r[2]}
    changed = True
    while changed:
        changed = False
        for r in rows:
            if r[0] not in tiers and r[2] and all(c in tiers for c in r[2]):
                tiers[r[0]] = 1 + max(tiers[c] for c in r[2])
                changed = True
    problems += [f"{r[0]} is in a component cycle" for r in rows if r[0] not in tiers]
    if problems:
        raise SystemExit(f"[{label}] invalid aspect table:\n  " + "\n  ".join(problems))
    return tiers


def aspect_json(row):
    aspect_id, color, components, origin, _ = row
    return {
        "color": f"#{color:06x}",
        "components": [rid(c) for c in components],
        "origin": origin,
    }


@generator
def gen_aspects(lang):
    rows = aspects.ALL
    tiers = validate_aspects(rows, "classic")
    out = os.path.join(RES, "data", NS, NS, "aspect")
    clean_dir(out)
    for row in rows:
        write_json(os.path.join(out, row[0] + ".json"), aspect_json(row))
        lang[f"aspect.{NS}.{row[0]}"] = row[0].capitalize()
        lang[f"aspect.{NS}.{row[0]}.meaning"] = row[4]
        lang[f"aspect.{NS}.{row[0]}.desc"] = aspect_lore.LORE[row[0]]

    # Optional datapack with Thaumcraft 6 component pairs.
    modern = [(r[0], r[1], aspects.MODERN_PAIRS.get(r[0], r[2]), r[3], r[4]) for r in rows]
    validate_aspects(modern, "modern")
    pack = os.path.join(RES, "datapacks", "modern_combinations")
    pack_aspects = os.path.join(pack, "data", NS, NS, "aspect")
    clean_dir(pack_aspects)
    for row in modern:
        if row[0] in aspects.MODERN_PAIRS:
            write_json(os.path.join(pack_aspects, row[0] + ".json"), aspect_json(row))
    write_json(os.path.join(pack, "pack.mcmeta"), {
        "pack": {
            "description": {"translate": f"pack.{NS}.modern_combinations.description"},
            "min_format": DATA_PACK_FORMAT,
            "max_format": DATA_PACK_FORMAT,
        }
    })
    lang[f"pack.{NS}.modern_combinations"] = "Thaumic Unity: Modern Combinations"
    lang[f"pack.{NS}.modern_combinations.description"] = "Thaumcraft 6 component pairs for shared aspects"
    by_tier = Counter(tiers.values())
    print(f"aspects: {len(rows)} ({', '.join(f'tier {t}: {n}' for t, n in sorted(by_tier.items()))}),"
          f" modern overrides: {len(aspects.MODERN_PAIRS)}")


# ---------------------------------------------------------------------------------------------------------- helpers

ASPECT_IDS = {row[0] for row in aspects.ALL}
ASPECT_PARENTS = {row[0]: tuple(row[2]) for row in aspects.ALL}


def full(i):
    """Adds the thaumicunity namespace to bare ids."""
    return i if ":" in i else rid(i)


def parse_aspects(text, where):
    """'ignis 4 terra 2' -> {"thaumicunity:ignis": 4, "thaumicunity:terra": 2} (validated)."""
    parts = text.split()
    if len(parts) % 2:
        raise SystemExit(f"{where}: odd aspect list '{text}'")
    out = {}
    for name, amount in zip(parts[::2], parts[1::2]):
        if name not in ASPECT_IDS:
            raise SystemExit(f"{where}: unknown aspect '{name}'")
        if int(amount) <= 0:
            raise SystemExit(f"{where}: aspect amounts must be positive ('{text}')")
        out[rid(name)] = out.get(rid(name), 0) + int(amount)
    return out


def related(a, b):
    return a in ASPECT_PARENTS.get(b, ()) or b in ASPECT_PARENTS.get(a, ())


def combine(a, b):
    for aspect, parts in ASPECT_PARENTS.items():
        if parts and sorted(parts) == sorted((a, b)):
            return aspect
    return None


def english(path):
    return path.replace("_", " ").title()


def tag_file(tag, kind):
    """'c:ores/amber' -> data/c/tags/<kind>/ores/amber.json"""
    namespace, path = tag.split(":", 1)
    return os.path.join(DATA, namespace, "tags", kind, path + ".json")


def write_tags(tags, kind):
    for tag, values in sorted(tags.items()):
        unique = list(dict.fromkeys(values))
        write_json(tag_file(tag, kind), {"values": unique})


def clean_tree(path):
    """Removes generated .json files below path (only directories this script owns)."""
    if not os.path.isdir(path):
        return
    for root, _, files in os.walk(path):
        for name in files:
            if name.endswith(".json"):
                os.remove(os.path.join(root, name))


# Simple items from every table: name -> (English name, item tags).
SIMPLE_ITEMS = dict(blocks.ITEMS)
SIMPLE_ITEMS.update({name: (english_name, []) for name, english_name in wands.ITEMS.items()})
SIMPLE_ITEMS.update(equipment.ITEMS)

EQUIPMENT_TOOLS = equipment.tools()
EQUIPMENT_ARMOR = equipment.armor()

ITEM_IDS = {rid("wand")}
ITEM_IDS |= {rid(name) for name in EQUIPMENT_TOOLS} | {rid(name) for name in EQUIPMENT_ARMOR}
for _name, _spec in blocks.BLOCKS.items():
    if _spec["model"] != "invisible":
        ITEM_IDS.add(rid(_name))
for _name in SIMPLE_ITEMS:
    ITEM_IDS.add(rid(_name))


def check_item(i, where):
    if i.startswith("#"):
        return
    if i.startswith(NS + ":") and i not in ITEM_IDS:
        raise SystemExit(f"{where}: unknown Thaumic Unity item {i}")


# ---------------------------------------------------------------------------------------------------------- blocks and items

def cube_element(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def all_faces(texture, cull=None):
    faces = {}
    for d in ("north", "east", "south", "west", "up", "down"):
        face = {"texture": texture}
        if cull and d in cull:
            face["cullface"] = d
        faces[d] = face
    return faces


def custom_model(kind, textures):
    tex = {k: f"{NS}:block/{v}" for k, v in textures.items()}
    if kind == "crucible":
        tex["particle"] = tex["side"]
        side = {"texture": "#side"}
        top = {"texture": "#top", "cullface": "up"}
        inner = {"texture": "#inside"}
        leg = {d: {"texture": "#side"} for d in ("north", "east", "south", "west")}
        leg["down"] = {"texture": "#bottom", "cullface": "down"}
        elements = [
            # walls
            cube_element([0, 3, 0], [2, 16, 16], {"north": dict(side, cullface="north"), "east": side,
                                                  "south": dict(side, cullface="south"), "west": dict(side, cullface="west"),
                                                  "up": top, "down": inner}),
            cube_element([14, 3, 0], [16, 16, 16], {"north": dict(side, cullface="north"), "east": dict(side, cullface="east"),
                                                    "south": dict(side, cullface="south"), "west": side, "up": top, "down": inner}),
            cube_element([2, 3, 0], [14, 16, 2], {"north": dict(side, cullface="north"), "south": side, "up": top,
                                                  "down": inner}),
            cube_element([2, 3, 14], [14, 16, 16], {"north": side, "south": dict(side, cullface="south"), "up": top,
                                                    "down": inner}),
            # floor
            cube_element([2, 3, 2], [14, 4, 14], {"up": inner, "down": inner}),
            # legs
            cube_element([0, 0, 0], [3, 3, 3], leg),
            cube_element([13, 0, 0], [16, 3, 3], leg),
            cube_element([0, 0, 13], [3, 3, 16], leg),
            cube_element([13, 0, 13], [16, 3, 16], leg),
        ]
        return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
    if kind == "pedestal":
        tex["particle"] = tex["side"]
        def part(frm, to):
            faces = {d: {"texture": "#side"} for d in ("north", "east", "south", "west")}
            faces["up"] = {"texture": "#top"}
            faces["down"] = {"texture": "#top"}
            return cube_element(frm, to, faces)
        elements = [part([2, 0, 2], [14, 3, 14]), part([4, 3, 4], [12, 11, 12]), part([2, 11, 2], [14, 14, 14])]
        elements[0]["faces"]["down"]["cullface"] = "down"
        return {"parent": "minecraft:block/block", "textures": tex, "elements": elements}
    if kind == "jar":
        tex["particle"] = tex["side"]
        body = {d: {"texture": "#side"} for d in ("north", "east", "south", "west")}
        body["down"] = {"texture": "#lid", "cullface": "down"}
        body["up"] = {"texture": "#lid"}
        elements = [cube_element([3, 0, 3], [13, 11, 13], body),
                    cube_element([4, 11, 4], [12, 12, 12], all_faces("#lid"))]
        return {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex, "elements": elements}
    if kind == "matrix":
        tex["particle"] = tex["all"]
        return {"parent": "minecraft:block/block", "textures": tex,
                "elements": [cube_element([3, 3, 3], [13, 13, 13], all_faces("#all"))]}
    raise SystemExit(f"unknown custom model {kind}")


def gen_tube_models(name, spec, blockstates, block_models):
    """Pipe blocks: a core, one arm model rotated to each connected side, and for valves a handle that turns red
    while the valve is shut. The item shows a straight piece."""
    tex = {k: f"{NS}:block/{v}" for k, v in spec["textures"].items()}
    tex["particle"] = tex["tube"]
    core = cube_element([5.5, 5.5, 5.5], [10.5, 10.5, 10.5], all_faces("#tube"))
    arm_faces = {d: {"texture": "#tube"} for d in ("east", "west", "up", "down")}
    arm_faces["north"] = {"texture": "#tube", "cullface": "north"}
    arm = cube_element([6.5, 6.5, 0], [9.5, 9.5, 5.5], arm_faces)
    elements_core = [core]
    if spec["model"] == "valve":
        elements_core.append(cube_element([4.5, 7, 7], [11.5, 9, 9], all_faces("#valve")))
    base = {"parent": "minecraft:block/block", "ambientocclusion": False, "textures": tex}
    write_json(os.path.join(block_models, name + "_core.json"), dict(base, elements=elements_core))
    write_json(os.path.join(block_models, name + "_arm.json"), dict(base, elements=[arm]))
    south_arm = cube_element([6.5, 6.5, 10.5], [9.5, 9.5, 16], all_faces("#tube"))
    write_json(os.path.join(block_models, name + "_inventory.json"),
               dict(base, elements=elements_core + [cube_element([6.5, 6.5, 0], [9.5, 9.5, 5.5], all_faces("#tube")), south_arm]))
    rotations = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": f"{NS}:block/{name}_core"}}]
    for side, rotation in rotations.items():
        parts.append({"when": {side: "true"}, "apply": dict({"model": f"{NS}:block/{name}_arm"}, **rotation)})
    if spec["model"] == "valve":
        shut = cube_element([4.4, 6.9, 6.9], [11.6, 9.1, 9.1], all_faces("#shut"))
        write_json(os.path.join(block_models, name + "_shut.json"),
                   dict(base, textures=dict(tex, shut=f"{NS}:block/essentia_valve_shut"), elements=[shut]))
        parts.append({"when": {"OR": [{"open": "false"}, {"powered": "true"}]},
                      "apply": {"model": f"{NS}:block/{name}_shut"}})
    write_json(os.path.join(blockstates, name + ".json"), {"multipart": parts})


@generator
def gen_blocks_items(lang):
    blockstates = os.path.join(ASSETS, "blockstates")
    block_models = os.path.join(ASSETS, "models", "block")
    item_models = os.path.join(ASSETS, "models", "item")
    item_defs = os.path.join(ASSETS, "items")
    loot = os.path.join(DATA, NS, "loot_table", "blocks")
    for d in (blockstates, block_models, item_models, item_defs, loot):
        clean_dir(d)
    block_tags = {t: list(v) for t, v in blocks.BLOCK_TAGS.items()}
    item_tags = {t: list(v) for t, v in blocks.ITEM_TAGS.items()}

    for name, spec in blocks.BLOCKS.items():
        block_id = rid(name)
        model = spec["model"]
        if model == "invisible":
            write_json(os.path.join(blockstates, name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
            write_json(os.path.join(block_models, name + ".json"),
                       {"textures": {"particle": "minecraft:block/glass"}, "elements": []})
            lang[f"block.{NS}.{name}"] = spec["name"]
            continue
        if model in ("tube", "valve"):
            gen_tube_models(name, spec, blockstates, block_models)
            write_json(os.path.join(item_defs, name + ".json"),
                       {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}_inventory"}})
        else:
            write_json(os.path.join(blockstates, name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        if model in ("tube", "valve"):
            pass
        elif model == "cube":
            data = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}}
        elif model == "bottom_top":
            t = spec["textures"]
            data = {"parent": "minecraft:block/cube_bottom_top",
                    "textures": {k: f"{NS}:block/{t[k]}" for k in ("top", "side", "bottom")}}
        elif model.startswith("custom:"):
            data = custom_model(model.split(":", 1)[1], spec["textures"])
        else:
            raise SystemExit(f"{name}: unknown model kind {model}")
        if model not in ("tube", "valve"):
            write_json(os.path.join(block_models, name + ".json"), data)
            write_json(os.path.join(item_defs, name + ".json"),
                       {"model": {"type": "minecraft:model", "model": f"{NS}:block/{name}"}})
        lang[f"block.{NS}.{name}"] = spec["name"]

        loot_kind = spec["loot"]
        if loot_kind == "self":
            entries = [{"type": "minecraft:item", "condition": {"type": "minecraft:survives_explosion"}, "name": block_id}]
        elif loot_kind == "jar":
            entries = [{"type": "minecraft:item",
                        "modifier": {"type": "minecraft:copy_components", "include": [f"{NS}:essentia"],
                                     "source": "block_entity"},
                        "name": block_id}]
        elif loot_kind.startswith("ore:"):
            drop = loot_kind.split(":", 1)[1]
            entries = [{"type": "minecraft:alternatives", "children": [
                {"type": "minecraft:item", "condition": "minecraft:tool/can_silk_touch", "name": block_id},
                {"type": "minecraft:item",
                 "modifier": [{"type": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                               "formula": "minecraft:ore_drops"},
                              {"type": "minecraft:explosion_decay"}],
                 "name": drop}]}]
        else:
            raise SystemExit(f"{name}: unknown loot kind {loot_kind}")
        write_json(os.path.join(loot, name + ".json"),
                   {"type": "minecraft:block", "pools": [{"entries": entries, "rolls": 1}],
                    "random_sequence": f"{NS}:blocks/{name}"})

        for tag in spec["tags"]:
            block_tags.setdefault(tag, []).append(block_id)
            if tag in blocks.MIRRORED_ITEM_TAGS:
                item_tags.setdefault(tag, []).append(block_id)

    for name, (english_name, tags) in SIMPLE_ITEMS.items():
        write_json(os.path.join(item_models, name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        write_json(os.path.join(item_defs, name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        lang[f"item.{NS}.{name}"] = english_name
        for tag in tags:
            item_tags.setdefault(tag, []).append(rid(name))

    gen_wand_item(item_models, item_defs, lang)
    gen_equipment_items(item_models, item_defs, item_tags, lang)

    for kind in ("block", "item"):
        for namespace in ("minecraft", "c", NS):
            clean_tree(os.path.join(DATA, namespace, "tags", kind))
    write_tags(block_tags, "block")
    write_tags(item_tags, "item")

    # Particles: four animation frames each.
    particles = os.path.join(ASSETS, "particles")
    clean_dir(particles)
    for name in ("spark", "wisp", "essentia", "rune"):
        write_json(os.path.join(particles, name + ".json"), {"textures": [f"{NS}:{name}_{i}" for i in range(4)]})

    # Sounds: Thaumic Unity events forward to vanilla events until original recordings exist.
    sounds = {
        "item.thaumometer.scan": ("minecraft:block.amethyst_block.chime", "Thaumometer scans"),
        "research.progress": ("minecraft:block.enchantment_table.use", "Research progresses"),
        "book.page": ("minecraft:item.book.page_turn", "Page turns"),
        "block.crucible.bubble": ("minecraft:block.bubble_column.bubble_pop", "Crucible bubbles"),
        "craft.success": ("minecraft:block.enchantment_table.use", "Arcane crafting succeeds"),
        "craft.fail": ("minecraft:block.fire.extinguish", "Arcane crafting fails"),
        "block.runic_matrix.infuse": ("minecraft:block.beacon.ambient", "Infusion hums"),
        "item.caster.cast": ("minecraft:entity.evoker.cast_spell", "Spell cast"),
        "item.caster.fizzle": ("minecraft:block.fire.extinguish", "Spell fizzles"),
        "misc.zap": ("minecraft:block.respawn_anchor.charge", "Magic crackles"),
    }
    sounds_json = {}
    for event, (target, subtitle) in sounds.items():
        sounds_json[event] = {"sounds": [{"name": target, "type": "event"}], "subtitle": f"subtitles.{NS}.{event}"}
        lang[f"subtitles.{NS}.{event}"] = subtitle
    write_json(os.path.join(ASSETS, "sounds.json"), sounds_json)
    print(f"blocks: {len(blocks.BLOCKS)}, items: {len(SIMPLE_ITEMS) + 1}, block tags: {len(block_tags)}, "
          f"item tags: {len(item_tags)}")


# ---------------------------------------------------------------------------------------------------------- equipment

def gen_equipment_items(item_models, item_defs, item_tags, lang):
    """Tools, weapons and armour (tools/content/equipment.py): models, names and tags, the equipment assets that
    dress a wearer, and the data map of vis discounts."""
    every = []
    for name, (material, kind, english_name) in EQUIPMENT_TOOLS.items():
        write_json(os.path.join(item_models, name + ".json"),
                   {"parent": "minecraft:item/handheld", "textures": {"layer0": f"{NS}:item/{name}"}})
        every.append((name, english_name, kind))
    for name, (set_id, piece, english_name) in EQUIPMENT_ARMOR.items():
        write_json(os.path.join(item_models, name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        every.append((name, english_name, piece))
    for name, english_name, kind in every:
        write_json(os.path.join(item_defs, name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        lang[f"item.{NS}.{name}"] = english_name
        for tag in equipment.KIND_TAGS[kind] + [f"{NS}:equipment"]:
            item_tags.setdefault(tag, []).append(rid(name))
    for name in equipment.SELF_REPAIRING:
        item_tags.setdefault(f"{NS}:self_repairing", []).append(rid(name))
    for name in equipment.WEAKENING:
        item_tags.setdefault(f"{NS}:weakening", []).append(rid(name))
    for set_id, (tag, values) in equipment.REPAIR_TAGS.items():
        if values is not None:
            for value in values:
                check_item(value, f"repair tag {tag}")
            item_tags.setdefault(tag, []).extend(values)
    for name in [*equipment.SELF_REPAIRING, *equipment.WEAKENING]:
        if name not in EQUIPMENT_TOOLS and name not in EQUIPMENT_ARMOR:
            raise SystemExit(f"equipment: {name} is not a tool or armour piece")

    # How a set looks when worn: one texture for helmet, chestplate and boots, one for leggings.
    assets_dir = os.path.join(ASSETS, "equipment")
    clean_dir(assets_dir)
    for set_id, spec in equipment.ARMOR_SETS.items():
        pieces = spec[0]
        layers = {}
        if pieces["helmet"] or pieces["chestplate"] or pieces["boots"]:
            layers["humanoid"] = [{"texture": rid(set_id)}]
        if pieces["leggings"]:
            layers["humanoid_leggings"] = [{"texture": rid(set_id)}]
        write_json(os.path.join(assets_dir, set_id + ".json"), {"layers": layers})

    for item in equipment.VIS_DISCOUNT:
        check_item(item, "vis discount")
    write_json(os.path.join(DATA, NS, "data_maps", "item", "vis_discount.json"), {"values": equipment.VIS_DISCOUNT})
    lang[f"tooltip.{NS}.vis_discount"] = "Vis discount: %s%%"
    print(f"equipment: {len(EQUIPMENT_TOOLS)} tools, {len(EQUIPMENT_ARMOR)} armour pieces, "
          f"{len(equipment.ARMOR_SETS)} equipment assets")


def equipment_recipes():
    """Arcane recipes for every tool and armour piece made from an ingot or cloth."""
    arcane = {}
    for name, (material, kind, _) in EQUIPMENT_TOOLS.items():
        spec = equipment.TOOL_MATERIALS[material]
        arcane[f"equipment/{name}"] = (equipment.TOOL_PATTERNS[kind], {"#": spec[1], "S": "minecraft:stick"},
                                       rid(name), 1, spec[8], spec[7])
    for name, (set_id, piece, _) in EQUIPMENT_ARMOR.items():
        spec = equipment.ARMOR_SETS[set_id]
        if spec[8] is None:
            continue
        arcane[f"equipment/{name}"] = (equipment.ARMOR_PATTERNS[piece], {"#": spec[1]}, rid(name), 1, spec[8], spec[7])
    return arcane


def aspect_pairs(text):
    words = text.split()
    return [(words[i], int(words[i + 1])) for i in range(0, len(words), 2)]


def equipment_aspects():
    """Aspects of tools and armour from what they are made of (three quarters of it, as recipes pass aspects on) and
    what they are for. Infused pieces have theirs written out in equipment.ITEM_ASPECTS."""
    out = {}
    made = [(name, equipment.TOOL_PATTERNS[kind], {"#": equipment.TOOL_MATERIALS[material][1], "S": "minecraft:stick"},
             kind) for name, (material, kind, _) in EQUIPMENT_TOOLS.items()]
    made += [(name, equipment.ARMOR_PATTERNS[piece], {"#": equipment.ARMOR_SETS[set_id][1]}, piece)
             for name, (set_id, piece, _) in EQUIPMENT_ARMOR.items() if equipment.ARMOR_SETS[set_id][8] is not None]
    for name, pattern, key, kind in made:
        total = Counter()
        for symbol, item in key.items():
            count = sum(row.count(symbol) for row in pattern)
            for aspect, amount in aspect_pairs(equipment.MATERIAL_ASPECTS[item]):
                total[aspect] += amount * count
        result_aspects = Counter({a: max(1, n * 3 // 4) for a, n in total.items()})
        for aspect, amount in aspect_pairs(equipment.KIND_ASPECTS[kind]):
            result_aspects[aspect] += amount
        out[rid(name)] = " ".join(f"{a} {n}" for a, n in result_aspects.items())
    return out


# ---------------------------------------------------------------------------------------------------------- wands

def gen_wand_item(item_models, item_defs, lang):
    """The wand's look comes from its rod and cap colours (custom model data colours 0 and 1, set by the assembly
    recipe) tinting two grey layers; an attached focus adds a third layer."""
    layers = {"layer0": f"{NS}:item/wand_rod", "layer1": f"{NS}:item/wand_caps"}
    write_json(os.path.join(item_models, "wand.json"), {"parent": "minecraft:item/handheld", "textures": layers})
    write_json(os.path.join(item_models, "wand_focused.json"),
               {"parent": "minecraft:item/handheld", "textures": dict(layers, layer2=f"{NS}:item/wand_focus")})
    wood, iron = wands.RODS["wood"][4], wands.CAPS["iron"][4]
    tints = [{"type": "minecraft:custom_model_data", "index": 0, "default": wood},
             {"type": "minecraft:custom_model_data", "index": 1, "default": iron}]
    write_json(os.path.join(item_defs, "wand.json"), {"model": {
        "type": "minecraft:condition", "property": "minecraft:has_component", "component": f"{NS}:wand_focus",
        "on_true": {"type": "minecraft:model", "model": f"{NS}:item/wand_focused", "tints": tints},
        "on_false": {"type": "minecraft:model", "model": f"{NS}:item/wand", "tints": tints}}})
    lang[f"item.{NS}.wand"] = "Wand"
    lang[f"item.{NS}.wand.parts"] = "%s-capped %s Wand"
    for rod, spec in wands.RODS.items():
        lang[f"wand_rod.{NS}.{rod}"] = spec[0]
    for cap, spec in wands.CAPS.items():
        lang[f"wand_cap.{NS}.{cap}"] = spec[0]


def wand_components(rod, cap):
    return {
        f"{NS}:wand_parts": {"rod": rid(rod), "cap": rid(cap)},
        "minecraft:custom_model_data": {"colors": [wands.RODS[rod][4], wands.CAPS[cap][4]]},
        "minecraft:item_name": {"translate": f"item.{NS}.wand.parts",
                                "with": [{"translate": f"wand_cap.{NS}.{cap}"}, {"translate": f"wand_rod.{NS}.{rod}"}]},
    }


def wand_recipes():
    """Arcane and infusion recipes for caps, cores, the recharge pedestal and every rod and cap combination."""
    arcane, infusion = {}, {}
    for cap, (nugget, vis) in wands.CAP_RECIPES.items():
        arcane[f"wand/{cap}_cap"] = (["NNN", "N N"], {"N": nugget}, wands.CAPS[cap][1], 2, vis, wands.CAPS[cap][5])
    for rod, (central, (first, second), essentia, instability) in wands.CORE_INFUSIONS.items():
        infusion[f"infusion/{rod}_wand_core"] = (central, [first, second, first, second], essentia, instability,
                                                 wands.RODS[rod][1], wands.RODS[rod][5])
    pattern, key, vis = wands.PEDESTAL_RECIPE
    arcane["wand/wand_pedestal"] = (pattern, key, rid("wand_pedestal"), 1, vis, "auromancy/wand_pedestal")
    for rod, rod_spec in wands.RODS.items():
        for cap, cap_spec in wands.CAPS.items():
            # Gate on the rod's research; plain wooden rods take the cap's.
            entry = rod_spec[5] if rod != "wood" else cap_spec[5]
            arcane[f"wand/{rod}_{cap}"] = (["  C", " R ", "C  "], {"C": cap_spec[1], "R": rod_spec[1]}, rid("wand"), 1,
                                           wands.assembly_vis(rod, cap), entry, wand_components(rod, cap))
    return arcane, infusion


WAND_ARCANE, WAND_INFUSION = wand_recipes()


def merged(*tables):
    out = {}
    for table in tables:
        for key, value in table.items():
            if key in out:
                raise SystemExit(f"recipe {key} is defined twice")
            out[key] = value
    return out


ARCANE = merged(recipes.ARCANE, WAND_ARCANE, equipment_recipes())
INFUSION = merged(recipes.INFUSION, WAND_INFUSION, equipment.INFUSION)
CRUCIBLE = merged(recipes.CRUCIBLE, equipment.CRUCIBLE)
SHAPED = merged(recipes.SHAPED, equipment.SHAPED)
SHAPELESS = merged(recipes.SHAPELESS, equipment.SHAPELESS)


@generator
def gen_wand_parts(lang):
    rods_dir = os.path.join(DATA, NS, NS, "wand_rod")
    caps_dir = os.path.join(DATA, NS, NS, "wand_cap")
    clean_dir(rods_dir)
    clean_dir(caps_dir)
    for rod, (english_name, item, capacity, affinity, color, entry) in wands.RODS.items():
        check_item(item, f"rod {rod}")
        if affinity is not None and affinity not in wands.PRIMALS:
            raise SystemExit(f"rod {rod}: affinity must be a primal, got {affinity}")
        data = {"item": item, "capacity": capacity, "color": f"#{color:06x}"}
        if affinity:
            data["affinity"] = rid(affinity)
        write_json(os.path.join(rods_dir, rod + ".json"), data)
    for cap, (english_name, item, cost, primal_cost, color, entry) in wands.CAPS.items():
        check_item(item, f"cap {cap}")
        for primal in primal_cost:
            if primal not in wands.PRIMALS:
                raise SystemExit(f"cap {cap}: {primal} is not a primal")
        data = {"item": item, "cost": cost, "color": f"#{color:06x}"}
        if primal_cost:
            data["primal_cost"] = {rid(p): f for p, f in primal_cost.items()}
        write_json(os.path.join(caps_dir, cap + ".json"), data)
    print(f"wand rods: {len(wands.RODS)}, caps: {len(wands.CAPS)}, wand recipes: {len(WAND_ARCANE) + len(WAND_INFUSION)}")


# ---------------------------------------------------------------------------------------------------------- aspects of things

@generator
def gen_item_aspects(lang):
    item_map = {}
    all_aspects = dict(item_aspects.ITEM_ASPECTS)
    for table in (wands.ITEM_ASPECTS, equipment.ITEM_ASPECTS, equipment_aspects()):
        for item, text in table.items():
            if item in all_aspects:
                raise SystemExit(f"item aspects: {item} is defined twice")
            all_aspects[item] = text
    for item, text in all_aspects.items():
        check_item(item, "item aspects")
        item_map[item] = parse_aspects(text, item)
    write_json(os.path.join(DATA, NS, "data_maps", "item", "aspects.json"), {"values": item_map})

    entity_map = {e: parse_aspects(t, e) for e, t in item_aspects.ENTITY_ASPECTS.items()}
    write_json(os.path.join(DATA, NS, "data_maps", "entity_type", "entity_aspects.json"), {"values": entity_map})

    rules_dir = os.path.join(DATA, NS, NS, "aspect_rule")
    clean_dir(rules_dir)
    for tag, text, priority in item_aspects.TAG_RULES:
        name = tag.replace(":", "_").replace("/", "_")
        rule = {"tag": "#" + tag, "aspects": parse_aspects(text, tag), "priority": priority}
        write_json(os.path.join(rules_dir, name + ".json"), rule)
    print(f"item aspects: {len(item_map)}, entity aspects: {len(entity_map)}, tag rules: {len(item_aspects.TAG_RULES)}")


# ---------------------------------------------------------------------------------------------------------- world

@generator
def gen_world(lang):
    profiles = {}
    for key, base, attunement in world.AURA_PROFILES:
        if len(attunement) != 6:
            raise SystemExit(f"aura profile {key}: attunement needs 6 weights")
        profiles[key] = {"base": base, "attunement": [float(w) for w in attunement]}
    write_json(os.path.join(DATA, NS, "data_maps", "worldgen", "biome", "aura_profile.json"), {"values": profiles})
    for item in world.ALCHEMICAL_FUELS:
        check_item(item.lstrip("#"), "alchemical fuels")
    write_json(os.path.join(DATA, NS, "data_maps", "item", "alchemical_fuel.json"), {"values": world.ALCHEMICAL_FUELS})
    write_json(os.path.join(DATA, NS, "data_maps", "block", "infusion_stabilizer.json"),
               {"values": world.INFUSION_STABILIZERS})

    features = os.path.join(DATA, NS, "worldgen", "feature")
    placed = os.path.join(DATA, NS, "worldgen", "placed_feature")
    modifiers = os.path.join(DATA, NS, "neoforge", "biome_modifier")
    for d in (features, placed, modifiers):
        clean_dir(d)
    ores = {
        # name: (block, vein size, veins per chunk, min y, max y)
        "ore_cinnabar": ("cinnabar_ore", 6, 5, -24, 48),
        "ore_amber": ("amber_ore", 4, 4, 32, 112),
    }
    for name, (block, size, count, low, high) in ores.items():
        write_json(os.path.join(features, name + ".json"), {
            "type": "minecraft:ore",
            "discard_chance_on_air_exposure": 0.0,
            "size": size,
            "targets": [{"state": rid(block),
                         "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}}],
        })
        write_json(os.path.join(placed, name + ".json"), {
            "feature": rid(name),
            "placement": [
                {"type": "minecraft:count", "count": count},
                {"type": "minecraft:in_square"},
                {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid",
                                                              "max_inclusive": {"absolute": high},
                                                              "min_inclusive": {"absolute": low}}},
                {"type": "minecraft:biome"},
            ],
        })
    write_json(os.path.join(modifiers, "ores.json"), {
        "type": f"{NS}:add_features",
        "biomes": "#minecraft:is_overworld",
        "features": [rid(n) for n in ores],
        "step": "underground_ores",
    })
    print(f"aura profiles: {len(profiles)}, ore features: {len(ores)}")


# ---------------------------------------------------------------------------------------------------------- spells

def visual(color, particle, sound=None):
    v = {"color": f"#{color:06x}", "particle": particle}
    if sound:
        v["sound"] = sound
    return v


@generator
def gen_spells(lang):
    out = os.path.join(DATA, NS, NS, "spell_component")
    clean_dir(out)
    count = 0
    for name, (primitive, tags, cost, complexity, multiplier, params, particle) in spells.MEDIUMS.items():
        data = {"kind": "medium", "primitive": rid(primitive), "complexity": complexity, "cost_multiplier": multiplier,
                "tags": tags, "params": params,
                "visual": {"particle": spells.PRIMITIVE_PARTICLE[particle]}}
        if cost:
            data["cost"] = parse_aspects(cost, f"medium {name}")
        write_json(os.path.join(out, name + ".json"), data)
        lang[f"spell.{NS}.{name}"] = english(name)
        count += 1
    effect_aspects = {}
    for name, (primitive, aspect, cost, complexity, cooldown, params, color, tags) in spells.EFFECTS.items():
        if aspect not in ASPECT_IDS:
            raise SystemExit(f"effect {name}: unknown aspect {aspect}")
        effect_aspects[name] = aspect
        data = {"kind": "effect", "primitive": rid(primitive), "aspect": rid(aspect),
                "cost": parse_aspects(cost, f"effect {name}"), "complexity": complexity, "cooldown": cooldown,
                "tags": tags, "params": params, "visual": visual(color, f"{NS}:spark", f"{NS}:misc.zap")}
        write_json(os.path.join(out, name + ".json"), data)
        lang[f"spell.{NS}.{name}"] = english(name)
        count += 1
    # Every synergy must be reachable: two effects whose aspects combine into its compound aspect.
    formable = {}
    names = list(effect_aspects)
    for i, a in enumerate(names):
        for b in names[i + 1:]:
            compound = combine(effect_aspects[a], effect_aspects[b])
            if compound:
                formable.setdefault(compound, []).append(f"{a}+{b}")
    for name, (compound, primitive, cost, warp, params, color) in spells.SYNERGIES.items():
        if compound not in formable:
            raise SystemExit(f"synergy {name}: no two effects combine into {compound}")
        data = {"kind": "effect", "primitive": rid(primitive), "aspect": rid(compound), "synergy_of": rid(compound),
                "complexity": 0, "params": params, "visual": visual(color, f"{NS}:wisp"), "tags": ["synergy"]}
        if cost:
            data["cost"] = parse_aspects(cost, f"synergy {name}")
        if warp:
            data["warp"] = warp
        write_json(os.path.join(out, name + ".json"), data)
        lang[f"spell.{NS}.{name}"] = english(name)
        count += 1
    for name, (primitive, cost, complexity, ops, params, applies_to, tier) in spells.MODIFIERS.items():
        data = {"kind": "modifier", "primitive": rid(primitive), "complexity": complexity, "ops": ops,
                "params": params, "applies_to": applies_to, "tier": tier}
        if cost:
            data["cost"] = parse_aspects(cost, f"modifier {name}")
        write_json(os.path.join(out, name + ".json"), data)
        lang[f"spell.{NS}.{name}"] = english(name)
        count += 1
    print(f"spell components: {count} ({len(spells.MEDIUMS)} mediums, {len(spells.EFFECTS)} effects, "
          f"{len(spells.SYNERGIES)} synergies, {len(spells.MODIFIERS)} modifiers)")


# ---------------------------------------------------------------------------------------------------------- recipes

def ingredient(value, where):
    if isinstance(value, list):
        for v in value:
            check_item(v, where)
        return value
    check_item(value, where)
    return value


def result(item, count=1, components=None):
    check_item(item, "recipe result")
    out = {"id": item}
    if count != 1:
        out["count"] = count
    if components:
        out["components"] = components
    return out


def recipe_ids():
    ids = set(SHAPELESS) | set(SHAPED) | set(recipes.SMELTING) | set(ARCANE)
    ids |= set(CRUCIBLE) | set(INFUSION)
    ids |= {f"focus/{name}" for name in spells.FOCUS_ARCHETYPES}
    return ids


def spell_node(node):
    out = {"medium": rid(node["medium"])}
    for key in ("effects", "modifiers"):
        if node.get(key):
            out[key] = [rid(c) for c in node[key]]
    return out


@generator
def gen_recipes(lang):
    out = os.path.join(DATA, NS, "recipe")
    clean_tree(out)
    gates = set(research.ENTRIES)

    def gate(entry, where):
        if entry not in gates:
            raise SystemExit(f"{where}: unknown research gate {entry}")
        return rid(entry)

    for name, (ingredients, item, count) in SHAPELESS.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [ingredient(i, name) for i in ingredients],
            "result": result(item, count)})
    for name, (pattern, key, item, count) in SHAPED.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "key": {k: ingredient(v, name) for k, v in key.items()},
            "pattern": pattern,
            "result": result(item, count)})
    for name, (source, item, xp) in recipes.SMELTING.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:smelting", "cookingtime": 200, "experience": xp,
            "ingredient": ingredient(source, name), "result": result(item)})
    for name, (pattern, key, item, count, vis, entry, *extra) in ARCANE.items():
        data = {"type": f"{NS}:arcane_shaped" if pattern else f"{NS}:arcane_shapeless"}
        if pattern:
            data["key"] = {k: ingredient(v, name) for k, v in key.items()}
            data["pattern"] = pattern
        else:
            data["ingredients"] = [ingredient(i, name) for i in key]
        data["vis"] = parse_aspects(vis, name)
        for aspect in data["vis"]:
            if aspect.split(":")[1] not in aspects.descriptions():
                raise SystemExit(f"{name}: arcane vis must be primal, got {aspect}")
        data["result"] = result(item, count, extra[0] if extra else None)
        data["research"] = gate(entry, name)
        write_json(os.path.join(out, name + ".json"), data)
    for name, (catalyst, aspect_text, item, count, entry) in CRUCIBLE.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": f"{NS}:crucible", "catalyst": ingredient(catalyst, name),
            "aspects": parse_aspects(aspect_text, name), "result": result(item, count), "research": gate(entry, name)})
    for name, (central, components, essentia, instability, item, entry) in INFUSION.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": f"{NS}:infusion", "central": ingredient(central, name),
            "components": [ingredient(c, name) for c in components],
            "essentia": parse_aspects(essentia, name), "instability": instability,
            "result": result(item), "research": gate(entry, name)})
    for name, (english_name, focus, node, entry, vis, extras) in spells.FOCUS_ARCHETYPES.items():
        for component in [node["medium"], *node.get("effects", []), *node.get("modifiers", [])]:
            if component not in spells.MEDIUMS and component not in spells.EFFECTS and component not in spells.MODIFIERS:
                raise SystemExit(f"focus {name}: unknown spell component {component}")
        focus_id = rid(focus)
        write_json(os.path.join(out, "focus", name + ".json"), {
            "type": f"{NS}:arcane_shapeless",
            "ingredients": [focus_id] + [ingredient(e, name) for e in extras],
            "vis": parse_aspects(vis, name),
            "result": result(focus_id, 1, {
                f"{NS}:spell": spell_node(node),
                "minecraft:item_name": {"translate": f"focus.{NS}.{name}"}}),
            "research": gate(entry, name)})
        lang[f"focus.{NS}.{name}"] = english_name
    print(f"recipes: {len(recipe_ids())}")


# ---------------------------------------------------------------------------------------------------------- research

SCAN_KINDS = ("item", "block", "entity", "aspect", "item_tag")


def requirement(req, where):
    kind = req[0]
    if kind == "scan":
        target = req[1]
        if target.split(":", 1)[0] not in SCAN_KINDS or target.count(":") != 2:
            raise SystemExit(f"{where}: bad scan target {target}")
        return {"type": "scan", "target": target}
    if kind == "craft":
        check_item(req[1], where)
        return {"type": "craft", "item": req[1]}
    if kind == "obtain":
        check_item(req[1], where)
        return {"type": "obtain", "item": req[1], "count": req[2]}
    if kind == "knowledge":
        if req[2] not in research.CATEGORIES:
            raise SystemExit(f"{where}: unknown category {req[2]}")
        return {"type": "knowledge", "kind": req[1], "category": rid(req[2]), "amount": req[3]}
    if kind == "insight":
        names = req[1]
        for a in names:
            if a not in ASPECT_IDS:
                raise SystemExit(f"{where}: unknown aspect {a}")
        # Starter puzzles must be solvable with few kinds of points: neighbouring anchors are related directly
        # or through one shared aspect (Aer - Lux - Ignis).
        for a, b in zip(names, names[1:]):
            if not related(a, b) and not any(related(a, m) and related(m, b) for m in ASPECT_IDS):
                raise SystemExit(f"{where}: insight aspects {a} and {b} need more than one aspect between them")
        return {"type": "insight", "aspects": [rid(a) for a in names]}
    if kind == "research":
        if req[1] not in research.ENTRIES:
            raise SystemExit(f"{where}: unknown research {req[1]}")
        return {"type": "research", "id": rid(req[1])}
    raise SystemExit(f"{where}: unknown requirement {req}")


@generator
def gen_research(lang):
    categories_dir = os.path.join(DATA, NS, NS, "research_category")
    entries_dir = os.path.join(DATA, NS, NS, "research")
    clean_dir(categories_dir)
    clean_tree(entries_dir)
    known_recipes = recipe_ids()
    for name, (english_name, icon, order, requires, color) in research.CATEGORIES.items():
        check_item(icon, f"category {name}")
        data = {"icon": icon, "background": rid(name), "order": order, "color": f"#{color:06x}"}
        if requires:
            if requires not in research.ENTRIES:
                raise SystemExit(f"category {name}: unknown required research {requires}")
            data["requires"] = rid(requires)
        write_json(os.path.join(categories_dir, name + ".json"), data)
        lang[f"research_category.{NS}.{name}"] = english_name
    positions = {}
    for entry_id, spec in research.ENTRIES.items():
        category = entry_id.split("/")[0]
        if category not in research.CATEGORIES:
            raise SystemExit(f"{entry_id}: unknown category {category}")
        pos = (category, spec["x"], spec["y"])
        if pos in positions:
            raise SystemExit(f"{entry_id} overlaps {positions[pos]} on the research map")
        positions[pos] = entry_id
        for parent in spec["parents"]:
            if parent not in research.ENTRIES:
                raise SystemExit(f"{entry_id}: unknown parent {parent}")
        check_item(spec["icon"], entry_id)
        stages = []
        key_base = f"research.{NS}.{entry_id.replace('/', '.')}"
        for index, stage in enumerate(spec["stages"]):
            text_key = f"{key_base}.stage{index}"
            lang[text_key] = stage["text"]
            for recipe in stage.get("recipes", []):
                if recipe not in known_recipes:
                    raise SystemExit(f"{entry_id}: unknown recipe {recipe}")
            stages.append({
                "text": text_key,
                "requirements": [requirement(r, entry_id) for r in stage.get("requirements", [])],
                "recipes": [rid(r) for r in stage.get("recipes", [])],
            })
        for a in spec["aspects"]:
            if a not in ASPECT_IDS:
                raise SystemExit(f"{entry_id}: unknown aspect {a}")
        data = {"category": rid(category), "x": spec["x"], "y": spec["y"], "icon": spec["icon"],
                "parents": [rid(p) for p in spec["parents"]], "aspects": [rid(a) for a in spec["aspects"]],
                "stages": stages}
        for flag in ("hidden", "special", "auto_unlock"):
            if spec.get(flag):
                data[flag] = True
        write_json(os.path.join(entries_dir, entry_id + ".json"), data)
        lang[key_base] = spec["title"]
    # Every gated recipe must be shown by some entry, and a gate must not block its own unlock requirement.
    shown = {r for spec in research.ENTRIES.values() for st in spec["stages"] for r in st.get("recipes", [])}
    for name in ARCANE:
        if name not in shown:
            raise SystemExit(f"arcane recipe {name} is not shown in any research entry")
    print(f"research categories: {len(research.CATEGORIES)}, entries: {len(research.ENTRIES)}")


# ---------------------------------------------------------------------------------------------------------- Thaumcraft textures

@generator
def gen_thaumcraft_import(lang):
    """Mapping the client uses to build a resource pack from the player's own Thaumcraft jar (no Thaumcraft file is
    part of this project). Every target must be a texture Thaumic Unity has, so the pack only ever replaces."""
    textures = os.path.join(ASSETS, "textures")
    for target in thaumcraft_import.KNOWN:
        if not os.path.exists(os.path.join(textures, target + ".png")):
            raise SystemExit(f"thaumcraft_import: {target} is not a Thaumic Unity texture")
    primals = {row[0]: row[1] for row in aspects.PRIMALS}
    data = {
        "version": 1,
        "pack_format": 97,
        "known": thaumcraft_import.KNOWN,
        "tinted": {f"item/{p}_vis_crystal": {"candidates": thaumcraft_import.TINTED_CRYSTALS, "color": f"#{c:06x}"}
                   for p, c in primals.items()},
        "aspects": {row[0]: f"#{row[1]:06x}" for row in aspects.ALL},
        "noise": thaumcraft_import.NOISE,
    }
    write_json(os.path.join(ASSETS, "thaumcraft_import.json"), data)
    lang[f"pack.{NS}.thaumcraft_textures"] = "Thaumcraft textures"
    lang[f"pack.{NS}.thaumcraft_textures.description"] = "From your own Thaumcraft jar, for your own game only"
    print(f"thaumcraft texture map: {len(thaumcraft_import.KNOWN)} textures, {len(data['aspects'])} aspect glyphs")


# ---------------------------------------------------------------------------------------------------------- game tests

# Test functions registered by dev.thaumicunity.common.gametest.TUGameTests -> ticks they may take.
GAME_TESTS = {
    "registries_loaded": 20,
    "foci_compile": 20,
    "item_aspects": 20,
    "crucible_and_infusion": 20,
    "world_data": 20,
    "insight_puzzles_solvable": 20,
    "salis_mundus": 40,
    "wands": 20,
    "essentia_transport": 300,
    "portable_hole": 120,
    "equipment": 60,
}


@generator
def gen_game_tests(lang):
    out = os.path.join(DATA, NS, "test_instance")
    clean_dir(out)
    for name, ticks in GAME_TESTS.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:function", "environment": "minecraft:default", "function": rid(name),
            "max_ticks": ticks, "required": True, "setup_ticks": 1, "structure": "minecraft:empty"})
    print(f"game tests: {len(GAME_TESTS)}")


def main():
    lang = {}
    for gen in GENERATORS:
        gen(lang)
    static_path = os.path.join(ROOT, "tools", "content", "lang_static.json")
    if os.path.exists(static_path):
        with open(static_path, encoding="utf-8") as f:
            static = json.load(f)
        clash = sorted(set(static) & set(lang))
        if clash:
            raise SystemExit("lang keys defined twice: " + ", ".join(clash))
        lang.update(static)
    write_json(os.path.join(RES, "assets", NS, "lang", "en_us.json"), dict(sorted(lang.items())))
    print(f"lang entries: {len(lang)}")


if __name__ == "__main__":
    main()
