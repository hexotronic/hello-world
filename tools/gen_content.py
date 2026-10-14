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
import item_aspects  # noqa: E402
import recipes  # noqa: E402
import research  # noqa: E402
import spells  # noqa: E402
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


ITEM_IDS = set()
for _name in blocks.BLOCKS:
    ITEM_IDS.add(rid(_name))
for _name in blocks.ITEMS:
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
        write_json(os.path.join(blockstates, name + ".json"), {"variants": {"": {"model": f"{NS}:block/{name}"}}})
        model = spec["model"]
        if model == "cube":
            data = {"parent": "minecraft:block/cube_all", "textures": {"all": f"{NS}:block/{name}"}}
        elif model == "bottom_top":
            t = spec["textures"]
            data = {"parent": "minecraft:block/cube_bottom_top",
                    "textures": {k: f"{NS}:block/{t[k]}" for k in ("top", "side", "bottom")}}
        elif model.startswith("custom:"):
            data = custom_model(model.split(":", 1)[1], spec["textures"])
        else:
            raise SystemExit(f"{name}: unknown model kind {model}")
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

    for name, (english_name, tags) in blocks.ITEMS.items():
        write_json(os.path.join(item_models, name + ".json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{name}"}})
        write_json(os.path.join(item_defs, name + ".json"),
                   {"model": {"type": "minecraft:model", "model": f"{NS}:item/{name}"}})
        lang[f"item.{NS}.{name}"] = english_name
        for tag in tags:
            item_tags.setdefault(tag, []).append(rid(name))

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
    print(f"blocks: {len(blocks.BLOCKS)}, items: {len(blocks.ITEMS)}, block tags: {len(block_tags)}, "
          f"item tags: {len(item_tags)}")


# ---------------------------------------------------------------------------------------------------------- aspects of things

@generator
def gen_item_aspects(lang):
    item_map = {}
    for item, text in item_aspects.ITEM_ASPECTS.items():
        check_item(item, "item aspects")
        item_map[item] = parse_aspects(text, item)
    write_json(os.path.join(DATA, NS, "data_maps", "item", "aspects.json"), {"values": item_map})

    entity_map = {e: parse_aspects(t, e) for e, t in item_aspects.ENTITY_ASPECTS.items()}
    write_json(os.path.join(DATA, NS, "data_maps", "entity_type", "entity_aspects.json"), {"values": entity_map})

    rules_dir = os.path.join(DATA, NS, NS, "aspect_rule")
    clean_dir(rules_dir)
    for tag, text, priority in item_aspects.TAG_RULES:
        name = tag.replace(":", "_").replace("/", "_")
        rule = {"tag": tag, "aspects": parse_aspects(text, tag), "priority": priority}
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
    ids = set(recipes.SHAPELESS) | set(recipes.SHAPED) | set(recipes.SMELTING) | set(recipes.ARCANE)
    ids |= set(recipes.CRUCIBLE) | set(recipes.INFUSION)
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

    for name, (ingredients, item, count) in recipes.SHAPELESS.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shapeless",
            "ingredients": [ingredient(i, name) for i in ingredients],
            "result": result(item, count)})
    for name, (pattern, key, item, count) in recipes.SHAPED.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:crafting_shaped",
            "key": {k: ingredient(v, name) for k, v in key.items()},
            "pattern": pattern,
            "result": result(item, count)})
    for name, (source, item, xp) in recipes.SMELTING.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": "minecraft:smelting", "cookingtime": 200, "experience": xp,
            "ingredient": ingredient(source, name), "result": result(item)})
    for name, (pattern, key, item, count, vis, entry) in recipes.ARCANE.items():
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
        data["result"] = result(item, count)
        data["research"] = gate(entry, name)
        write_json(os.path.join(out, name + ".json"), data)
    for name, (catalyst, aspect_text, item, count, entry) in recipes.CRUCIBLE.items():
        write_json(os.path.join(out, name + ".json"), {
            "type": f"{NS}:crucible", "catalyst": ingredient(catalyst, name),
            "aspects": parse_aspects(aspect_text, name), "result": result(item, count), "research": gate(entry, name)})
    for name, (central, components, essentia, instability, item, entry) in recipes.INFUSION.items():
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
    for name in recipes.ARCANE:
        if name not in shown:
            raise SystemExit(f"arcane recipe {name} is not shown in any research entry")
    print(f"research categories: {len(research.CATEGORIES)}, entries: {len(research.ENTRIES)}")


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
