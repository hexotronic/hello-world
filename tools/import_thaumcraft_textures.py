#!/usr/bin/env python3
"""Builds a personal resource pack that dresses Thaumic Unity in the textures of YOUR OWN copy of Thaumcraft.

    python3 tools/import_thaumcraft_textures.py path/to/Thaumcraft-<version>.jar [--out my-pack.zip]
    python3 tools/import_thaumcraft_textures.py Thaumcraft.jar --list        # print every texture in the jar

Thaumcraft's art is "All Rights Reserved" by its author, so Thaumic Unity cannot include it. This tool reads the jar
you already have and writes a resource pack on your computer; put it in .minecraft/resourcepacks and enable it.
The pack is for your own use: do not upload or share it.

How textures are found, for each Thaumic Unity texture:
  1. known file names from Thaumcraft 4 (1.7.10) and Thaumcraft 6 (1.12.2), tried in order;
  2. otherwise any texture whose file name contains all the words of the Thaumic Unity name, in any order
     (thaumium_ingot matches ingot_thaumium.png and thaumiumingot.png);
  3. otherwise the Thaumic Unity texture stays as it is.
Thaumcraft draws aspect glyphs, vis crystals and essentia in grey and colours them in code; those are tinted with the
aspect's colour here. Run with --map extra.json to add or correct matches: {"item/thaumometer": ["items/x.png"]}.
Requires Pillow (pip install pillow).
"""
import argparse
import io
import json
import os
import re
import sys
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools", "content"))
import aspects as aspect_table  # noqa: E402
from thaumcraft_import import KNOWN, NOISE, TINTED_CRYSTALS  # noqa: E402

# Minecraft 26.3 resource pack format (version.json: pack_version.resource_major).
RESOURCE_PACK_FORMAT = 97
TC_TEXTURES = "assets/thaumcraft/textures/"

# Thaumic Unity texture -> Thaumcraft candidates (relative to assets/thaumcraft/textures/), best first.

# Thaumcraft textures that are grey and tinted in code: target -> (candidates, colour).
PRIMAL_COLOURS = {row[0]: row[1] for row in aspect_table.PRIMALS}
TINTED = {f"item/{primal}_vis_crystal": (TINTED_CRYSTALS, colour) for primal, colour in PRIMAL_COLOURS.items()}



def tokens(name):
    return [t for t in re.split(r"[_/\-.]+", name.lower()) if t and t not in NOISE]


def fuzzy(target, pngs):
    """Shortest texture of the same kind (item or block) whose file name contains every word of the target's name."""
    kind, name = target.split("/", 1)
    if kind not in ("item", "block"):
        return None
    folders = (kind + "/", kind + "s/")
    words = tokens(name)
    if not words:
        return None
    matches = []
    for path in pngs:
        if not path.startswith(folders):
            continue
        stem = os.path.splitext(os.path.basename(path))[0].lower().replace("_", "").replace("-", "")
        if all(w in stem for w in words):
            matches.append(path)
    if not matches:
        return None
    matches.sort(key=lambda p: (len(os.path.basename(p)), p))
    return matches[0]


def tint(image, colour):
    """Multiplies a grey texture by an RGB colour, keeping alpha."""
    image = image.convert("RGBA")
    r, g, b = (colour >> 16) & 255, (colour >> 8) & 255, colour & 255
    px = image.load()
    for y in range(image.height):
        for x in range(image.width):
            pr, pg, pb, pa = px[x, y]
            grey = (pr + pg + pb) / 3 / 255
            px[x, y] = (int(r * grey), int(g * grey), int(b * grey), pa)
    return image


def is_greyscale(image):
    data = image.convert("RGBA").tobytes()
    for i in range(0, len(data), 4):
        pr, pg, pb, pa = data[i:i + 4]
        if pa and (abs(pr - pg) > 12 or abs(pg - pb) > 12):
            return False
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("jar", help="your Thaumcraft jar")
    parser.add_argument("--out", default="thaumic-unity-thaumcraft-textures.zip", help="resource pack to write")
    parser.add_argument("--map", help="JSON file with extra or corrected matches")
    parser.add_argument("--list", action="store_true", help="only list the textures in the jar")
    args = parser.parse_args()

    with zipfile.ZipFile(args.jar) as jar:
        files = set(jar.namelist())
        pngs = sorted(f[len(TC_TEXTURES):] for f in files if f.startswith(TC_TEXTURES) and f.endswith(".png"))
        if not pngs:
            raise SystemExit(f"{args.jar} contains no {TC_TEXTURES}*.png - is it a Thaumcraft jar?")
        if args.list:
            print("\n".join(pngs))
            return

        known = {k: list(v) for k, v in KNOWN.items()}
        if args.map:
            with open(args.map, encoding="utf-8") as f:
                for target, candidates in json.load(f).items():
                    known[target] = list(candidates) + known.get(target, [])

        available = set(pngs)
        chosen = {}
        for target, candidates in known.items():
            source = next((c for c in candidates if c in available), None) or fuzzy(target, pngs)
            if source:
                chosen[target] = (source, None)
        for target, (candidates, colour) in TINTED.items():
            source = next((c for c in candidates if c in available), None)
            if source:
                chosen[target] = (source, colour)
        # Aspect glyphs keep their names across Thaumcraft versions; colour them like Thaumcraft does.
        for row in aspect_table.ALL:
            source = f"aspects/{row[0]}.png"
            if source in available:
                chosen[f"aspect/{row[0]}"] = (source, row[1])

        with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as pack:
            pack.writestr("pack.mcmeta", json.dumps({"pack": {
                "description": "Thaumic Unity with textures from your own Thaumcraft jar - personal use only",
                "min_format": RESOURCE_PACK_FORMAT,
                "max_format": RESOURCE_PACK_FORMAT,
            }}, indent=2))
            for target, (source, colour) in sorted(chosen.items()):
                image = Image.open(io.BytesIO(jar.read(TC_TEXTURES + source)))
                if colour is not None and is_greyscale(image):
                    image = tint(image, colour)
                out = io.BytesIO()
                image.convert("RGBA").save(out, "PNG")
                pack.writestr(f"assets/thaumicunity/textures/{target}.png", out.getvalue())
                meta = TC_TEXTURES + source + ".mcmeta"
                if meta in files:
                    pack.writestr(f"assets/thaumicunity/textures/{target}.png.mcmeta", jar.read(meta))

    wanted = set(known) | set(TINTED) | {f"aspect/{row[0]}" for row in aspect_table.ALL}
    aspects_found = sum(1 for t in chosen if t.startswith("aspect/"))
    others = sorted(t for t in wanted - set(chosen) if not t.startswith("aspect/"))
    print(f"Wrote {args.out}: {len(chosen)} textures ({aspects_found} aspect glyphs).")
    for target, (source, colour) in sorted(chosen.items()):
        if not target.startswith("aspect/"):
            print(f"  {target:34} <- {source}{'  (tinted)' if colour is not None else ''}")
    if others:
        print("Kept Thaumic Unity's own texture for (no match found; see --list and --map):")
        for target in others:
            print(f"  {target}")
    print("Copy the pack to .minecraft/resourcepacks and enable it in Options > Resource Packs. Do not share it.")


if __name__ == "__main__":
    main()
