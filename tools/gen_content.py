#!/usr/bin/env python3
"""Generates Thaumic Unity's bulk data files from the tables in tools/content/.

Run from the repository root:  python3 tools/gen_content.py
Outputs (all committed, regenerate after editing a table):
  src/main/resources/data/thaumicunity/thaumicunity/aspect/*.json
  src/main/resources/datapacks/modern_combinations/**             (optional built-in datapack)
  src/main/resources/assets/thaumicunity/lang/en_us.json           (merged with tools/content/lang_static.json)
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

NS = "thaumicunity"
RES = os.path.join(ROOT, "src", "main", "resources")
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
            "min_format": 15,
            "max_format": 2147483647,
            "pack_format": 15,
            "supported_formats": [15, 2147483647],
        }
    })
    lang[f"pack.{NS}.modern_combinations"] = "Thaumic Unity: Modern Combinations"
    lang[f"pack.{NS}.modern_combinations.description"] = "Thaumcraft 6 component pairs for shared aspects"
    by_tier = Counter(tiers.values())
    print(f"aspects: {len(rows)} ({', '.join(f'tier {t}: {n}' for t, n in sorted(by_tier.items()))}),"
          f" modern overrides: {len(aspects.MODERN_PAIRS)}")


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
