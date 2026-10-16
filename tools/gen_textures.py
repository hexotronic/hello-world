#!/usr/bin/env python3
"""Generates every Thaumic Unity texture: original procedural art (docs/ASSETS_AND_LICENSING.md).

    python3 tools/gen_textures.py

Output is deterministic (seeded by texture name), so re-running only changes files whose generator changed.
Paths listed in tools/texture_overrides.txt (relative to assets/thaumicunity/textures) are never overwritten,
so hand-made textures can replace generated ones one by one.

Items are drawn from 16x16 character templates (shape) and palettes (colour); blocks, particles, aspect glyphs,
research backgrounds and the logo are drawn procedurally.
"""
import hashlib
import math
import os
import random
import sys

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, os.path.join(ROOT, "tools", "content"))
import aspects as aspect_table  # noqa: E402

TEXTURES = os.path.join(ROOT, "src", "main", "resources", "assets", "thaumicunity", "textures")
LOGO = os.path.join(ROOT, "src", "main", "resources", "thaumicunity_logo.png")
OVERRIDES_FILE = os.path.join(ROOT, "tools", "texture_overrides.txt")


# ---------------------------------------------------------------------------------------------------------- helpers

def load_overrides():
    if not os.path.exists(OVERRIDES_FILE):
        return set()
    with open(OVERRIDES_FILE, encoding="utf-8") as f:
        return {line.strip() for line in f if line.strip() and not line.startswith("#")}


OVERRIDES = load_overrides()
WRITTEN = []


def save(img, rel):
    """Saves under textures/<rel>.png unless the path is overridden by hand-made art."""
    if rel in OVERRIDES or rel + ".png" in OVERRIDES:
        return
    path = os.path.join(TEXTURES, rel + ".png")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path, optimize=True)
    WRITTEN.append(rel)


def rng(name):
    return random.Random(int(hashlib.sha256(name.encode()).hexdigest()[:16], 16))


def rgb(hex_color):
    return ((hex_color >> 16) & 255, (hex_color >> 8) & 255, hex_color & 255)


def clamp(v):
    return max(0, min(255, int(round(v))))


def shade(color, factor):
    """factor < 1 darkens, > 1 lightens towards white."""
    r, g, b = color[:3]
    if factor <= 1:
        return (clamp(r * factor), clamp(g * factor), clamp(b * factor))
    t = factor - 1
    return (clamp(r + (255 - r) * t), clamp(g + (255 - g) * t), clamp(b + (255 - b) * t))


def mix(a, b, t):
    return tuple(clamp(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(color, outline=0.28):
    """Palette for templates: O outline, 1..5 dark to highlight."""
    return {
        "O": shade(color, outline) + (255,),
        "1": shade(color, 0.48) + (255,),
        "2": shade(color, 0.68) + (255,),
        "3": shade(color, 0.86) + (255,),
        "4": shade(color, 1.18) + (255,),
        "5": shade(color, 1.5) + (255,),
    }


def ramp_as(color, letters):
    """Second palette for templates on letters like 'abcde' (dark to light)."""
    factors = [0.45, 0.68, 0.88, 1.15, 1.45]
    return {ch: shade(color, f) + (255,) for ch, f in zip(letters, factors)}


def template(rows, palette):
    if len(rows) != 16:
        raise ValueError(f"template has {len(rows)} rows")
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        if len(row) != 16:
            raise ValueError(f"template row {y} has {len(row)} columns: {row!r}")
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            if ch not in palette:
                raise ValueError(f"no colour for {ch!r} in row {y}")
            px[x, y] = palette[ch]
    return img


def value_noise(r, size, cells):
    """Smooth tileable noise in [0, 1]."""
    grid = [[r.random() for _ in range(cells)] for _ in range(cells)]
    out = [[0.0] * size for _ in range(size)]
    for y in range(size):
        for x in range(size):
            gx = x * cells / size
            gy = y * cells / size
            x0, y0 = int(gx), int(gy)
            tx, ty = gx - x0, gy - y0
            tx = tx * tx * (3 - 2 * tx)
            ty = ty * ty * (3 - 2 * ty)
            a = grid[y0 % cells][x0 % cells]
            b = grid[y0 % cells][(x0 + 1) % cells]
            c = grid[(y0 + 1) % cells][x0 % cells]
            d = grid[(y0 + 1) % cells][(x0 + 1) % cells]
            out[y][x] = (a * (1 - tx) + b * tx) * (1 - ty) + (c * (1 - tx) + d * tx) * ty
    return out


def noisy_fill(name, color, size=16, amount=0.18, cells=4, grain=0.08):
    r = rng(name)
    n = value_noise(r, size, cells)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            f = 1 + (n[y][x] - 0.5) * 2 * amount + (r.random() - 0.5) * grain
            px[x, y] = shade(color, f) + (255,)
    return img


# ---------------------------------------------------------------------------------------------------------- items

T_INGOT = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....OOOOOOOO...",
    "....O55544443O..",
    "...O554444333O..",
    "..O5444433332O..",
    ".OOOOOOOOOOO2O..",
    ".O3333333332OO..",
    ".O2222222221O...",
    ".OOOOOOOOOOOO...",
    "................",
    "................",
    "................",
]

T_NUGGET = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".......OOO......",
    "......O543O.....",
    ".....O54332O....",
    "...OOO43321O....",
    "..O5443O21O.....",
    "..O43321OO......",
    "...O321O........",
    "....OOO.........",
    "................",
    "................",
    "................",
]

T_CRYSTAL = [
    "................",
    ".......O........",
    "......O5O.......",
    "......O53O......",
    ".....O543O..O...",
    ".....O5432O.O4O.",
    "..O..O5432OO43O.",
    ".O4O.O5432O.O32O",
    ".O43OO5432O.O32O",
    ".O432O4321OO321O",
    ".O432O4321O.O21O",
    "..O32O3321O..OO.",
    "..O21O3221O.....",
    "...OOOO221O.....",
    "......OOOO......",
    "................",
]

T_GEM = [
    "................",
    "................",
    "................",
    ".....OOOOOO.....",
    "....O554443O....",
    "...O55444333O...",
    "..O5544443332O..",
    "..O5444433332O..",
    "..O4444333322O..",
    "..O4443333221O..",
    "...O33332221O...",
    "....O322211O....",
    ".....O2211O.....",
    "......OOOO......",
    "................",
    "................",
]

T_CHUNK = [
    "................",
    "................",
    "................",
    "................",
    "......OOOO......",
    "....OO5443OO....",
    "...O55443332O...",
    "..O544433322O...",
    "..O443333221O...",
    ".O4433322211O...",
    ".O3333222111O...",
    ".O332222111O....",
    "..OO221111O.....",
    "....OOOOOO......",
    "................",
    "................",
]

T_DROPLET = [
    "................",
    "................",
    "........O.......",
    ".......O4O......",
    ".......O43O.....",
    "......O5433O....",
    "......O54332O...",
    ".....O554332O...",
    ".....O544332O...",
    ".....O443322O...",
    ".....O433221O...",
    "......O32211O...",
    ".......O211O....",
    "........OOO.....",
    "................",
    "................",
]

T_BOOK = [
    "................",
    "................",
    "...OOOOOOOOOO...",
    "..O3444444443O..",
    "..O34ccccc443ee.",
    "..O34cdddc443ee.",
    "..O34cdedc443ee.",
    "..O34cdddc443ee.",
    "..O34ccccc443ee.",
    "..O3444444443ee.",
    "..O3444444443ee.",
    "..O3444444443ee.",
    "..O2333333332ee.",
    "..OOOOOOOOOOOdd.",
    "................",
    "................",
]

T_LENS = [
    "................",
    "................",
    ".....OOOOOO.....",
    "....OaabbaaO....",
    "...Oa112345aO...",
    "...Oa123455aO...",
    "...Ob223455bO...",
    "...Ob223344bO...",
    "...Oa122334aO...",
    "...Oa112233aO...",
    "....OaabbaaO....",
    ".....OOccOO.....",
    "......OccO......",
    "......OddO......",
    "......OddO......",
    ".......OO.......",
]

T_GOGGLES = [
    "................",
    "................",
    "................",
    "................",
    "..OOOO....OOOO..",
    ".OaaaaO..OaaaaO.",
    "Oa5432aOOa5432aO",
    "Oa4321accca4321a",
    "Oa3211aOOa3211aO",
    ".OaaaaO..OaaaaO.",
    "..OOOO....OOOO..",
    "................",
    "................",
    "................",
    "................",
    "................",
]

T_QUILL = [
    "................",
    "............OO..",
    "...........O54O.",
    "..........O543O.",
    ".........O543O..",
    "........O543O...",
    ".......O543O....",
    "......O53OO.....",
    ".....OcOO.......",
    "..OOOcOOO.......",
    ".OaaacdaaO......",
    ".OabbbbbaO......",
    ".OabbbbbaO......",
    ".OaaaaaaaO......",
    "..OOOOOOO.......",
    "................",
]

T_NOTES = [
    "................",
    "................",
    "..OOOOOOOOOO....",
    "..O55555554OO...",
    "..O5aa5a55444O..",
    "..O5555aa55554O.",
    "..O55a5555a554O.",
    "..O5aba55aba54O.",
    "..O55a5555a554O.",
    "..O5555aa55554O.",
    "..O5a55a5aa554O.",
    "..O55555555554O.",
    "..O44444444443O.",
    "..OOOOOOOOOOOOO.",
    "................",
    "................",
]

T_DUST = [
    "................",
    "................",
    "................",
    "................",
    "........x.......",
    "................",
    "....y.......z...",
    "................",
    ".......OOO......",
    "......O545O.....",
    ".....O54x43O....",
    "....O5y43z32O...",
    "...O54433x221O..",
    "..O44z3322y211O.",
    "..OOOOOOOOOOOOO.",
    "................",
]

T_PHIAL = [
    "................",
    "................",
    "......OOOO......",
    "......OccO......",
    ".......OO.......",
    "......OaaO......",
    "......OabO......",
    ".....OabbbO.....",
    "....OabbbbbO....",
    "...Oab11111bO...",
    "...Oa123321bO...",
    "...Oa123321aO...",
    "...Oa112211aO...",
    "....Oa1111aO....",
    ".....OOOOOO.....",
    "................",
]

T_ORB = [
    "................",
    "................",
    "......x..x......",
    "....x......x....",
    "......OOOO......",
    "....OO5554OO....",
    "...O55554443O...",
    "..xO55444433Ox..",
    "...O54444333O...",
    "...O44443332O...",
    "....OO3322OO....",
    "......OOOO......",
    "....x......x....",
    "......x..x......",
    "................",
    "................",
]

T_LUMP = [
    "................",
    "................",
    "................",
    "................",
    "................",
    ".....OOOOOO.....",
    "...OO554444OO...",
    "..O5544444433O..",
    "..O4444443333O..",
    "..O3333333322O..",
    "...OO2222221O...",
    ".....OOOOOOO....",
    "................",
    "................",
    "................",
    "................",
]

T_FABRIC = [
    "................",
    "................",
    "................",
    "..OOOOOOOOOOO...",
    "..O5445445443O..",
    "..O44x444x4443O.",
    "..O4444444443O..",
    "...O3x333x332O..",
    "...O333333322O..",
    "..O33x333x3322O.",
    "..O3333333322O..",
    "..O2222222211O..",
    "...OOOOOOOOOO...",
    "................",
    "................",
    "................",
]

T_GAUNTLET = [
    "................",
    "......OO.OO.....",
    ".....O54O54O....",
    ".....O43O43OOO..",
    ".....O43O43O54O.",
    ".....O43O43O43O.",
    "..OO.O32O32O32O.",
    ".O54OO3333333O..",
    ".O433aaaaa332O..",
    "..O33abcba332O..",
    "...O3aaaaa32O...",
    "...O333333321O..",
    "....Occcccccc O.",
    "....OddddddddO..",
    "....OOOOOOOOOO..",
    "................",
]

T_FOCUS = [
    "................",
    "................",
    ".....OOOOOO.....",
    "....OaabbaaO....",
    "...Oa..55..aO...",
    "..Oa.O5544O.aO..",
    "..Ob.O5443O.bO..",
    "..Ob.O4433O.bO..",
    "..Oa.O3322O.aO..",
    "...Oa..22..aO...",
    "....OaabbaaO....",
    ".....OOOOOO.....",
    "................",
    "................",
    "................",
    "................",
]

# Fix the one irregular gauntlet row (a space marks a deliberate gap in the cuff outline).
T_GAUNTLET[12] = "....OccccccccO.."

BRASS = rgb(0xC9A13B)
THAUMIUM = rgb(0x6A4FA8)
IRON = rgb(0x8E949C)
PAPER = rgb(0xE8DDBF)
LEATHER = rgb(0x7A4E2D)
WOOD = rgb(0x8A6239)

PRIMAL_COLORS = {row[0]: rgb(row[1]) for row in aspect_table.PRIMALS}
# Perditio's grey is too dark for a readable crystal; lift it a little.
CRYSTAL_COLORS = dict(PRIMAL_COLORS, perditio=(96, 96, 104), ordo=(214, 212, 236))


def items():
    save(template(T_INGOT, ramp(THAUMIUM)), "item/thaumium_ingot")
    save(template(T_NUGGET, ramp(THAUMIUM)), "item/thaumium_nugget")
    save(template(T_INGOT, ramp(BRASS)), "item/brass_ingot")
    save(template(T_NUGGET, ramp(BRASS)), "item/brass_nugget")
    for primal, color in CRYSTAL_COLORS.items():
        save(template(T_CRYSTAL, ramp(color, 0.3)), f"item/{primal}_vis_crystal")
    save(template(T_GEM, ramp(rgb(0xF0A020))), "item/amber")
    save(template(T_CHUNK, ramp(rgb(0xB8322A))), "item/cinnabar")
    save(template(T_CHUNK, ramp(rgb(0xE8662A))), "item/alumentum")
    save(template(T_DROPLET, ramp(rgb(0xC8CED6))), "item/quicksilver")
    save(template(T_LUMP, ramp(rgb(0xE9DFA8))), "item/tallow")

    book = dict(ramp(rgb(0x5B2A6E)))
    book.update(ramp_as(BRASS, "cdefg"))
    book["e"] = shade(PAPER, 0.95) + (255,)
    book["d"] = shade(PAPER, 0.7) + (255,)
    save(template(T_BOOK, book), "item/thaumonomicon")

    lens = dict(ramp(rgb(0x9FE6F0)))
    lens.update(ramp_as(BRASS, "abcdf"))
    lens["c"] = shade(BRASS, 0.75) + (255,)
    lens["d"] = shade(WOOD, 0.9) + (255,)
    save(template(T_LENS, lens), "item/thaumometer")

    goggles = dict(ramp(rgb(0xD77BE8)))
    goggles.update(ramp_as(BRASS, "abcdf"))
    goggles["c"] = shade(LEATHER, 1.0) + (255,)
    save(template(T_GOGGLES, goggles), "item/goggles_of_revealing")

    quill = dict(ramp(rgb(0xF2F2EE)))
    quill.update(ramp_as(rgb(0x2B2B48), "abcdf"))
    quill["a"] = shade(rgb(0x3A4A5E), 1.0) + (255,)
    quill["b"] = shade(rgb(0x14141E), 1.0) + (255,)
    quill["c"] = shade(rgb(0x14141E), 1.0) + (255,)
    quill["d"] = shade(rgb(0x6C7C90), 1.2) + (255,)
    save(template(T_QUILL, quill), "item/scribing_tools")

    notes = dict(ramp(PAPER))
    notes["a"] = shade(rgb(0x3B2A1A), 1.0) + (255,)
    notes["b"] = shade(rgb(0x9700C0), 1.0) + (255,)
    save(template(T_NOTES, notes), "item/research_notes")

    dust = dict(ramp(rgb(0xC9B4E0)))
    dust["x"] = rgb(0xFFF07E) + (255,)
    dust["y"] = rgb(0x56E0FF) + (255,)
    dust["z"] = rgb(0xFF6A3C) + (255,)
    save(template(T_DUST, dust), "item/salis_mundus")

    phial = dict(ramp(rgb(0xB06CE0)))
    phial["a"] = (210, 236, 240, 255)
    phial["b"] = (170, 210, 220, 150)
    phial["c"] = shade(WOOD, 0.9) + (255,)
    save(template(T_PHIAL, phial), "item/phial")

    nitor = dict(ramp(rgb(0xFFD84A)))
    nitor["x"] = (255, 240, 160, 200)
    save(template(T_ORB, nitor), "item/nitor")

    fabric = dict(ramp(rgb(0x7E4FB8)))
    fabric["x"] = shade(BRASS, 1.2) + (255,)
    save(template(T_FABRIC, fabric), "item/enchanted_fabric")

    for name, base in (("basic_caster", LEATHER), ("thaumium_caster", THAUMIUM)):
        gauntlet = dict(ramp(base))
        gauntlet.update(ramp_as(BRASS, "abcxy"))
        gauntlet["b"] = rgb(0x9FE6F0) + (255,)
        gauntlet["c"] = shade(BRASS, 0.9) + (255,)
        gauntlet["d"] = shade(BRASS, 0.6) + (255,)
        save(template(T_GAUNTLET, gauntlet), f"item/{name}")

    for name, core, cage in (("apprentice_focus", rgb(0x5CC8E8), IRON),
                             ("adept_focus", rgb(0xB06CE0), BRASS),
                             ("archmage_focus", rgb(0xFF5A9A), THAUMIUM)):
        focus = dict(ramp(core))
        focus["a"] = shade(cage, 0.85) + (255,)
        focus["b"] = shade(cage, 1.25) + (255,)
        save(template(T_FOCUS, focus), f"item/{name}")


# ---------------------------------------------------------------------------------------------------------- blocks

STONE = (120, 118, 128)
ARCANE_STONE = (92, 86, 112)


def stone(name, color=STONE, amount=0.16):
    return noisy_fill(name, color, amount=amount, cells=4, grain=0.12)


def bricks(name, color, mortar):
    img = noisy_fill(name, color, amount=0.12, cells=4, grain=0.1)
    px = img.load()
    for y in range(16):
        for x in range(16):
            course = y // 8
            edge_y = y % 8 == 7
            joint = (x % 8 == 7) if course == 0 else ((x + 4) % 8 == 7)
            if edge_y or joint:
                px[x, y] = shade(mortar, 0.9 + (x * 7 + y * 3) % 5 * 0.03) + (255,)
            elif y % 8 == 0 or (x % 8 == 0 if course == 0 else (x + 4) % 8 == 0):
                px[x, y] = shade(px[x, y], 1.12) + (255,)
    return img


def metal_block(name, color):
    img = noisy_fill(name, color, amount=0.06, cells=2, grain=0.05)
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=shade(color, 0.45))
    d.line([1, 1, 14, 1], fill=shade(color, 1.35))
    d.line([1, 1, 1, 14], fill=shade(color, 1.35))
    d.line([1, 14, 14, 14], fill=shade(color, 0.7))
    d.line([14, 1, 14, 14], fill=shade(color, 0.7))
    d.rectangle([4, 4, 11, 11], outline=shade(color, 0.75))
    d.line([5, 5, 10, 5], fill=shade(color, 1.2))
    for cx, cy in ((3, 3), (12, 3), (3, 12), (12, 12)):
        img.putpixel((cx, cy), shade(color, 1.5) + (255,))
    return img


def ore(name, ore_color, base=STONE):
    img = stone(name + "_base", base)
    r = rng(name)
    px = img.load()
    for _ in range(6):
        cx, cy = r.randrange(2, 14), r.randrange(2, 14)
        for dx, dy in ((0, 0), (1, 0), (0, 1), (-1, 0), (1, 1), (0, -1)):
            if r.random() < 0.8:
                x, y = cx + dx, cy + dy
                if 0 <= x < 16 and 0 <= y < 16:
                    f = 1.25 if (dx, dy) in ((0, 0), (0, -1)) else 0.85
                    px[x, y] = shade(ore_color, f) + (255,)
        px[cx, cy] = shade(ore_color, 1.55) + (255,)
    return img


def wood(name, color=WOOD, horizontal=True):
    r = rng(name)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            u, v = (x, y) if horizontal else (y, x)
            grain = math.sin(u * 0.9 + math.sin(v * 0.35) * 2.0) * 0.07
            plank = 0.95 if (v // 4) % 2 else 1.03
            f = plank + grain + (r.random() - 0.5) * 0.06
            if v % 4 == 3:
                f *= 0.72
            px[x, y] = shade(color, f) + (255,)
    return img


def rune_overlay(img, color, seed, count=5, glow=True):
    r = rng(seed)
    d = ImageDraw.Draw(img)
    for _ in range(count):
        x, y = r.randrange(2, 13), r.randrange(2, 13)
        shape = r.randrange(4)
        c = color + (255,)
        if shape == 0:
            d.line([x, y, x + 2, y], fill=c)
            d.line([x + 1, y, x + 1, y + 2], fill=c)
        elif shape == 1:
            d.line([x, y, x + 2, y + 2], fill=c)
            d.point([x + 2, y], fill=c)
        elif shape == 2:
            d.rectangle([x, y, x + 1, y + 1], outline=c)
        else:
            d.line([x, y + 2, x + 1, y], fill=c)
            d.line([x + 1, y, x + 2, y + 2], fill=c)
    if glow:
        halo = img.filter(ImageFilter.GaussianBlur(0.6))
        return Image.alpha_composite(halo, img)
    return img


def blocks():
    save(stone("arcane_stone", ARCANE_STONE, 0.14), "block/arcane_stone")
    save(bricks("arcane_stone_bricks", ARCANE_STONE, (58, 52, 70)), "block/arcane_stone_bricks")
    save(metal_block("thaumium_block", THAUMIUM), "block/thaumium_block")
    save(metal_block("brass_block", BRASS), "block/brass_block")
    save(ore("cinnabar_ore", rgb(0xC8382E)), "block/cinnabar_ore")
    save(ore("amber_ore", rgb(0xF0A020)), "block/amber_ore")

    amber = noisy_fill("amber_block", rgb(0xE8901C), amount=0.2, cells=3, grain=0.04)
    d = ImageDraw.Draw(amber)
    d.rectangle([0, 0, 15, 15], outline=shade(rgb(0xE8901C), 0.6))
    d.line([2, 2, 6, 2], fill=shade(rgb(0xE8901C), 1.5))
    d.line([2, 2, 2, 5], fill=shade(rgb(0xE8901C), 1.5))
    save(amber, "block/amber_block")

    # Crucible: riveted iron.
    iron = rgb(0x4A4E56)
    side = noisy_fill("crucible_side", iron, amount=0.1, cells=3, grain=0.08)
    d = ImageDraw.Draw(side)
    for y in (2, 12):
        d.line([0, y, 15, y], fill=shade(iron, 0.6))
        d.line([0, y + 1, 15, y + 1], fill=shade(iron, 1.3))
        for x in range(1, 16, 5):
            side.putpixel((x, y), shade(iron, 1.6) + (255,))
    save(side, "block/crucible_side")
    top = noisy_fill("crucible_top", iron, amount=0.08, cells=2, grain=0.06)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=shade(iron, 1.35))
    d.rectangle([1, 1, 14, 14], outline=shade(iron, 1.1))
    d.rectangle([2, 2, 13, 13], fill=shade(iron, 0.35))
    save(top, "block/crucible_top")
    save(noisy_fill("crucible_inner", shade(iron, 0.6), amount=0.12, cells=4), "block/crucible_inner")
    bottom = noisy_fill("crucible_bottom", shade(iron, 0.8), amount=0.08, cells=2)
    ImageDraw.Draw(bottom).rectangle([0, 0, 15, 15], outline=shade(iron, 0.5))
    save(bottom, "block/crucible_bottom")

    # Arcane workbench: dark wood, a cloth with a brass rune circle on top.
    wb_top = wood("arcane_workbench_top", shade(WOOD, 0.8))
    d = ImageDraw.Draw(wb_top)
    cloth = rgb(0x4B2F78)
    d.rectangle([2, 2, 13, 13], fill=cloth)
    d.rectangle([2, 2, 13, 13], outline=shade(cloth, 0.6))
    d.ellipse([4, 4, 11, 11], outline=shade(BRASS, 1.15))
    d.point([7, 7], fill=shade(BRASS, 1.4))
    d.point([8, 8], fill=shade(BRASS, 1.4))
    save(wb_top, "block/arcane_workbench_top")
    wb_side = wood("arcane_workbench_side", shade(WOOD, 0.8))
    d = ImageDraw.Draw(wb_side)
    d.rectangle([0, 0, 15, 2], fill=shade(WOOD, 0.62))
    d.line([0, 3, 15, 3], fill=shade(BRASS, 0.9))
    for x in (1, 14):
        d.line([x, 4, x, 15], fill=shade(WOOD, 0.55))
    d.rectangle([5, 7, 10, 11], outline=shade(BRASS, 1.1))
    save(wb_side, "block/arcane_workbench_side")
    save(wood("arcane_workbench_bottom", shade(WOOD, 0.7), horizontal=False), "block/arcane_workbench_bottom")

    # Research table: light wood with paper, an ink bottle and a quill.
    rt_top = wood("research_table_top", WOOD)
    d = ImageDraw.Draw(rt_top)
    d.rectangle([2, 3, 10, 13], fill=PAPER)
    d.rectangle([2, 3, 10, 13], outline=shade(PAPER, 0.7))
    for y in (5, 7, 9, 11):
        d.line([4, y, 8 - (y % 3), y], fill=(90, 70, 50))
    d.rectangle([11, 2, 13, 4], fill=(30, 30, 46))
    d.line([12, 5, 14, 12], fill=(240, 240, 236))
    save(rt_top, "block/research_table_top")
    rt_side = wood("research_table_side", WOOD)
    d = ImageDraw.Draw(rt_side)
    d.rectangle([0, 0, 15, 2], fill=shade(WOOD, 0.75))
    d.rectangle([2, 3, 13, 15], fill=shade(WOOD, 0.45))
    d.rectangle([2, 3, 4, 15], fill=shade(WOOD, 0.85))
    d.rectangle([11, 3, 13, 15], fill=shade(WOOD, 0.85))
    save(rt_side, "block/research_table_side")

    # Runic matrix: dark stone with glowing violet runes.
    matrix = stone("runic_matrix", (52, 46, 66), 0.14)
    d = ImageDraw.Draw(matrix)
    d.rectangle([0, 0, 15, 15], outline=(34, 30, 44))
    save(rune_overlay(matrix, (196, 120, 255), "runic_matrix_runes", 6), "block/runic_matrix")

    # Arcane pedestal: pale stone with gold trim.
    pale = (176, 170, 160)
    ped_side = stone("arcane_pedestal_side", pale, 0.1)
    d = ImageDraw.Draw(ped_side)
    d.line([0, 0, 15, 0], fill=shade(BRASS, 1.1))
    d.line([0, 15, 15, 15], fill=shade(BRASS, 0.8))
    d.line([3, 2, 3, 13], fill=shade(pale, 0.8))
    d.line([12, 2, 12, 13], fill=shade(pale, 0.8))
    save(ped_side, "block/arcane_pedestal_side")
    ped_top = stone("arcane_pedestal_top", pale, 0.1)
    d = ImageDraw.Draw(ped_top)
    d.rectangle([0, 0, 15, 15], outline=shade(BRASS, 1.0))
    d.ellipse([4, 4, 11, 11], outline=shade(BRASS, 1.2))
    save(ped_top, "block/arcane_pedestal_top")

    # Warded jar: faintly tinted glass with brass bands; the partly transparent pixels make it a translucent model,
    # so the essentia inside (drawn by WardedJarRenderer) shows through.
    jar = Image.new("RGBA", (16, 16), (214, 238, 244, 56))
    d = ImageDraw.Draw(jar)
    d.rectangle([0, 0, 15, 15], outline=(196, 226, 232, 255))
    d.line([1, 1, 1, 14], fill=(236, 250, 252, 255))
    d.line([2, 1, 2, 5], fill=(236, 250, 252, 255))
    d.line([0, 3, 15, 3], fill=shade(BRASS, 1.0) + (255,))
    d.line([0, 12, 15, 12], fill=shade(BRASS, 0.85) + (255,))
    save(jar, "block/warded_jar_side")
    lid = noisy_fill("warded_jar_lid", shade(WOOD, 0.85), amount=0.1, cells=2)
    d = ImageDraw.Draw(lid)
    d.rectangle([0, 0, 15, 15], outline=shade(BRASS, 0.9))
    d.rectangle([6, 6, 9, 9], fill=shade(BRASS, 1.2))
    save(lid, "block/warded_jar_lid")
    paper = noisy_fill("warded_jar_label", PAPER, amount=0.06)
    save(paper, "block/warded_jar_label")

    # Alchemical furnace: stone with an iron grate and a violet glow.
    furnace_stone = (110, 104, 100)
    front = stone("alchemical_furnace_side", furnace_stone, 0.12)
    d = ImageDraw.Draw(front)
    d.rectangle([0, 0, 15, 15], outline=shade(furnace_stone, 0.55))
    d.rectangle([3, 7, 12, 13], fill=(44, 22, 58))
    for x in range(4, 12, 2):
        d.line([x, 7, x, 13], fill=(70, 70, 76))
    d.line([4, 12, 11, 12], fill=(200, 110, 255))
    d.point([6, 11], fill=(255, 190, 120))
    d.point([9, 11], fill=(255, 190, 120))
    save(front, "block/alchemical_furnace_side")
    ftop = stone("alchemical_furnace_top", furnace_stone, 0.1)
    d = ImageDraw.Draw(ftop)
    d.rectangle([0, 0, 15, 15], outline=shade(furnace_stone, 0.55))
    d.ellipse([4, 4, 11, 11], fill=(50, 46, 52), outline=(150, 150, 160))
    save(ftop, "block/alchemical_furnace_top")


# ---------------------------------------------------------------------------------------------------------- particles

def particles():
    # White sprites: the particle colour comes from the ColorParticleOption tint.
    for frame in range(4):
        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        arm = 3 - frame
        c = (255, 255, 255, 255)
        d.line([4 - arm, 4, 4 + arm, 4], fill=c)
        d.line([4, 4 - arm, 4, 4 + arm], fill=c)
        d.point([4, 4], fill=c)
        if frame < 2:
            d.point([3, 3], fill=(255, 255, 255, 140))
            d.point([5, 5], fill=(255, 255, 255, 140))
        save(img, f"particle/spark_{frame}")

        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        px = img.load()
        radius = 3.4 - frame * 0.5
        for y in range(8):
            for x in range(8):
                dist = math.hypot(x - 3.5, y - 3.5)
                a = max(0.0, 1 - dist / radius)
                px[x, y] = (255, 255, 255, clamp(255 * a ** 1.4))
        save(img, f"particle/wisp_{frame}")

        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        r = 1 + frame
        d.ellipse([4 - r, 4 - r, 3 + r, 3 + r], outline=(255, 255, 255, 230))
        if frame < 3:
            d.point([3, 3], fill=(255, 255, 255, 255))
        save(img, f"particle/essentia_{frame}")

        img = Image.new("RGBA", (8, 8), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        r2 = rng(f"rune_particle_{frame}")
        c = (255, 255, 255, 255)
        pts = [(r2.randrange(1, 7), r2.randrange(1, 7)) for _ in range(4)]
        d.line(pts[:2], fill=c)
        d.line(pts[1:3], fill=c)
        d.line(pts[2:], fill=c)
        save(img, f"particle/rune_{frame}")


# ---------------------------------------------------------------------------------------------------------- aspects

# Hand-drawn glyphs for the primals on a 12x12 grid ('#' = stroke).
PRIMAL_GLYPHS = {
    "aer": [
        "............",
        ".....##.....",
        "....#..#....",
        "...#....#...",
        "..#......#..",
        "..########..",
        "............",
        "..########..",
        "............",
        "....####....",
        "............",
        "............",
    ],
    "terra": [
        "............",
        "..########..",
        "............",
        "....####....",
        "............",
        "..#......#..",
        "...#....#...",
        "....#..#....",
        ".....##.....",
        "............",
        "..########..",
        "............",
    ],
    "ignis": [
        "............",
        ".....#......",
        ".....##.....",
        "....#.#.....",
        "....#..#....",
        "...#...#....",
        "...#.#..#...",
        "..#.###.#...",
        "..#.#.#..#..",
        "..#.....#...",
        "...######...",
        "............",
    ],
    "aqua": [
        "............",
        "..#.....#...",
        "..##...##...",
        "..#.#.#.#...",
        "..#..#..#...",
        "............",
        "..#.....#...",
        "..##...##...",
        "..#.#.#.#...",
        "..#..#..#...",
        "............",
        "............",
    ],
    "ordo": [
        "............",
        ".....##.....",
        "....#..#....",
        "...#....#...",
        "..#..##..#..",
        "..#.#..#.#..",
        "..#.#..#.#..",
        "..#..##..#..",
        "...#....#...",
        "....#..#....",
        ".....##.....",
        "............",
    ],
    "perditio": [
        "............",
        "..#......#..",
        "...#....#...",
        "....#..#....",
        "............",
        "..###..###..",
        "............",
        "....#..#....",
        "...#....#...",
        "..#......#..",
        "............",
        "............",
    ],
}


def rune_glyph(aspect_id):
    """Mirror-symmetric rune: a few strokes between points of an 11x11 node lattice, seeded by the aspect id."""
    r = rng("aspect:" + aspect_id)
    xs = (1, 5)
    ys = (0, 3, 7, 10)
    nodes = [(x, y) for x in xs for y in ys]
    canvas = Image.new("1", (11, 11), 0)
    d = ImageDraw.Draw(canvas)
    top, bottom = sorted(r.sample(ys, 2))
    d.line([5, top, 5, bottom], fill=1)

    def stroke():
        a, b = r.sample(nodes, 2)
        d.line([a[0], a[1], b[0], b[1]], fill=1)
        d.line([10 - a[0], a[1], 10 - b[0], b[1]], fill=1)

    for _ in range(r.randint(2, 4)):
        stroke()
    # Too sparse glyphs (a lone line) are hard to tell apart; keep adding strokes until there is enough ink.
    while sum(canvas.histogram()[1:]) < 24:
        stroke()
    if r.random() < 0.45:
        cy = r.choice((2, 5, 8))
        d.ellipse([3, cy - 2, 7, cy + 2], outline=1)
    if r.random() < 0.35:
        y = r.choice(ys)
        d.point([2, y], fill=1)
        d.point([8, y], fill=1)
    px = canvas.load()
    rows = []
    for y in range(12):
        row = ""
        for x in range(12):
            row += "#" if x < 11 and y < 11 and px[x, y] else "."
        rows.append(row)
    return rows


def aspect_icon(aspect_id, color):
    glyph = PRIMAL_GLYPHS.get(aspect_id) or rune_glyph(aspect_id)
    color = rgb(color)
    # Very dark aspects get a light rim so they stay visible on dark backgrounds.
    lum = 0.3 * color[0] + 0.59 * color[1] + 0.11 * color[2]
    outline = (24, 18, 30, 255) if lum > 70 else (200, 196, 210, 255)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    strokes = {(x + 2, y + 2) for y, row in enumerate(glyph) for x, ch in enumerate(row) if ch == "#"}
    for (x, y) in strokes:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                n = (x + dx, y + dy)
                if 0 <= n[0] < 16 and 0 <= n[1] < 16 and n not in strokes:
                    px[n] = outline
    for (x, y) in strokes:
        f = 1.25 if y < 8 else 0.95
        px[x, y] = shade(color, f) + (255,)
    return img


def aspects():
    for row in aspect_table.ALL:
        save(aspect_icon(row[0], row[1]), f"aspect/{row[0]}")


# ---------------------------------------------------------------------------------------------------------- research backgrounds

CATEGORY_COLORS = {
    "fundamentals": 0xC9A44C,
    "alchemy": 0x5FB06A,
    "artifice": 0xB0703C,
    "infusion": 0x9B59D0,
    "auromancy": 0x4FA3D9,
}


def research_background(name, color):
    size = 256
    r = rng("bg:" + name)
    base = (226, 214, 184)
    n1 = value_noise(r, size, 8)
    n2 = value_noise(r, size, 32)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            f = 1 + (n1[y][x] - 0.5) * 0.18 + (n2[y][x] - 0.5) * 0.08
            px[x, y] = shade(base, f) + (255,)
    tint = rgb(color)
    d = ImageDraw.Draw(img, "RGBA")
    faint = tint + (34,)
    # Constellation of arcane circles and connecting lines, tileable by drawing wrapped copies.
    points = [(r.randrange(size), r.randrange(size)) for _ in range(9)]
    for i, (x, y) in enumerate(points):
        rad = r.randrange(10, 34)
        for ox in (-size, 0, size):
            for oy in (-size, 0, size):
                d.ellipse([x + ox - rad, y + oy - rad, x + ox + rad, y + oy + rad], outline=faint, width=1)
                d.ellipse([x + ox - rad // 2, y + oy - rad // 2, x + ox + rad // 2, y + oy + rad // 2], outline=faint)
                nx, ny = points[(i + 1) % len(points)]
                if abs(nx - x) < size // 2 and abs(ny - y) < size // 2:
                    d.line([x + ox, y + oy, nx + ox, ny + oy], fill=tint + (22,), width=1)
    return img


def backgrounds():
    for name, color in CATEGORY_COLORS.items():
        save(research_background(name, color), f"gui/research/{name}")


# ---------------------------------------------------------------------------------------------------------- logo

def logo():
    if "thaumicunity_logo" in OVERRIDES:
        return
    w, h = 512, 128
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # Ring of the six primal colours.
    cx, cy, rad = 64, 64, 46
    for i, row in enumerate(aspect_table.PRIMALS):
        start = i * 60 - 90
        d.arc([cx - rad, cy - rad, cx + rad, cy + rad], start, start + 56, fill=rgb(row[1]) + (255,), width=9)
    d.ellipse([cx - 22, cy - 22, cx + 22, cy + 22], outline=(201, 161, 59, 255), width=4)
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=(151, 0, 192, 255))
    try:
        font = ImageFont.load_default(size=54)
    except TypeError:
        font = ImageFont.load_default()
    d.text((130, 34), "Thaumic Unity", font=font, fill=(60, 36, 90, 255), stroke_width=2, stroke_fill=(232, 214, 160, 255))
    os.makedirs(os.path.dirname(LOGO), exist_ok=True)
    img.save(LOGO, optimize=True)
    WRITTEN.append("../../thaumicunity_logo")


# ---------------------------------------------------------------------------------------------------------- wands

T_WAND_CAP_ITEM = [
    "................",
    "................",
    "................",
    "................",
    "......OOOO......",
    ".....O5444O.....",
    "....O543332O....",
    "....O4OOOO2O....",
    "....O3O..O1O....",
    "....O3O..O1O....",
    "....O32OO21O....",
    ".....O3221O.....",
    "......OOOO......",
    "................",
    "................",
    "................",
]

ROD_COLORS = {
    "greatwood": rgb(0x6B4A2E), "silverwood": rgb(0xDDE3EC),
    "reed": rgb(0xC9D98C), "obsidian": rgb(0x40305E), "blaze": rgb(0xF2A23A),
    "ice": rgb(0xB8E6F6), "quartz": rgb(0xEDE6DE), "bone": rgb(0xE2DCC2),
}
CAP_COLORS = {
    "iron": rgb(0xC4C4C4), "copper": rgb(0xD9824B), "gold": rgb(0xF2D13C), "brass": rgb(0xC9A43A),
    "thaumium": rgb(0x7558B8),
}


def outline(img, color):
    """Adds a one-pixel outline around the opaque pixels of a sprite."""
    px = img.load()
    filled = {(x, y) for y in range(16) for x in range(16) if px[x, y][3]}
    for x, y in filled:
        for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and (nx, ny) not in filled:
                px[nx, ny] = color
    return img


def diagonal(img, palette, lo, hi, x_from, x_to):
    """Fills the diagonal band lo <= x + y <= hi for x_from <= x <= x_to, lit from the upper left."""
    px = img.load()
    for x in range(x_from, x_to + 1):
        for total in range(lo, hi + 1):
            y = total - x
            if 0 <= y < 16:
                shade_key = "4" if total == lo else "2" if total == hi else "3"
                px[x, y] = palette[shade_key]


def wands():
    # Wand layers are light grey; the item model tints them with the rod's and the caps' colours.
    grey = ramp(rgb(0xE8E8E8), 0.32)
    rod = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    diagonal(rod, grey, 15, 16, 3, 12)
    save(outline(rod, grey["O"]), "item/wand_rod")
    caps = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    diagonal(caps, grey, 14, 17, 1, 3)
    diagonal(caps, grey, 14, 17, 12, 14)
    px = caps.load()
    px[2, 13] = grey["5"]
    px[13, 2] = grey["5"]
    save(outline(caps, grey["O"]), "item/wand_caps")
    gem = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    gem_palette = ramp(rgb(0xB06CE0))
    gp = gem.load()
    for x, y, k in ((14, 0, "4"), (15, 1, "3"), (14, 1, "5"), (13, 1, "3"), (14, 2, "2"), (15, 0, "3"), (13, 0, "4")):
        gp[x, y] = gem_palette[k]
    save(gem, "item/wand_focus")

    # Cores: the rod's material, with a glint of its primal.
    for name, color in ROD_COLORS.items():
        palette = ramp(color, 0.3)
        core = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
        diagonal(core, palette, 14, 17, 2, 13)
        cp = core.load()
        for x, y in ((5, 10), (8, 7), (11, 4)):
            cp[x, y] = palette["5"]
        save(outline(core, palette["O"]), f"item/{name}_wand_core")
    for name, color in CAP_COLORS.items():
        save(template(T_WAND_CAP_ITEM, ramp(color)), f"item/{name}_wand_cap")

    # Recharge pedestal: dark arcane stone with amethyst inlay.
    side = stone("wand_pedestal_side", ARCANE_STONE, 0.1)
    d = ImageDraw.Draw(side)
    d.line([0, 0, 15, 0], fill=shade(BRASS, 1.1))
    d.line([0, 15, 15, 15], fill=shade(BRASS, 0.8))
    d.line([3, 2, 3, 13], fill=shade(ARCANE_STONE, 0.75))
    d.line([12, 2, 12, 13], fill=shade(ARCANE_STONE, 0.75))
    d.line([7, 4, 7, 11], fill=rgb(0xB98AF0))
    d.line([8, 4, 8, 11], fill=rgb(0x8E5FC8))
    save(side, "block/wand_pedestal_side")
    top = stone("wand_pedestal_top", ARCANE_STONE, 0.1)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=shade(BRASS, 1.0))
    d.ellipse([3, 3, 12, 12], outline=rgb(0xB98AF0))
    d.ellipse([6, 6, 9, 9], fill=rgb(0x8E5FC8))
    save(top, "block/wand_pedestal_top")


# ---------------------------------------------------------------------------------------------------------- essentia transport

def essentia_transport():
    # Tube: brass bands around a glass window; the same texture covers the core and the arms.
    tube = Image.new("RGBA", (16, 16), (196, 226, 232, 150))
    d = ImageDraw.Draw(tube)
    for y in (0, 1, 14, 15):
        d.line([0, y, 15, y], fill=shade(BRASS, 1.1 if y in (0, 14) else 0.8) + (255,))
    for x in (0, 15):
        d.line([x, 0, x, 15], fill=shade(BRASS, 0.7) + (255,))
    d.line([3, 4, 3, 11], fill=(236, 250, 252, 200))
    save(tube, "block/essentia_tube")
    valve = noisy_fill("essentia_valve", shade(BRASS, 0.95), amount=0.08, cells=2)
    d = ImageDraw.Draw(valve)
    d.rectangle([0, 0, 15, 15], outline=shade(BRASS, 0.6))
    save(valve, "block/essentia_valve")
    shut = noisy_fill("essentia_valve_shut", rgb(0xB8322A), amount=0.08, cells=2)
    save(shut, "block/essentia_valve_shut")

    # Buffer: a wooden crate with glass windows.
    side = noisy_fill("essentia_buffer_side", shade(WOOD, 0.9), amount=0.1, cells=3)
    d = ImageDraw.Draw(side)
    d.rectangle([0, 0, 15, 15], outline=shade(BRASS, 0.85))
    d.rectangle([4, 4, 11, 11], fill=(150, 196, 210, 255), outline=shade(BRASS, 0.7))
    d.line([5, 5, 5, 9], fill=(220, 244, 250, 255))
    save(side, "block/essentia_buffer_side")
    top = noisy_fill("essentia_buffer_top", shade(WOOD, 0.8), amount=0.1, cells=3)
    d = ImageDraw.Draw(top)
    d.rectangle([0, 0, 15, 15], outline=shade(BRASS, 0.85))
    d.ellipse([5, 5, 10, 10], fill=shade(BRASS, 1.1), outline=shade(BRASS, 0.6))
    save(top, "block/essentia_buffer_top")

    # Centrifuge: iron housing with a brass rotor.
    iron = rgb(0x8E949C)
    cside = noisy_fill("centrifuge_side", iron, amount=0.08, cells=2)
    d = ImageDraw.Draw(cside)
    d.rectangle([0, 0, 15, 15], outline=shade(iron, 0.5))
    d.rectangle([2, 5, 13, 10], fill=shade(BRASS, 0.9), outline=shade(BRASS, 0.55))
    for x in range(3, 13, 3):
        d.line([x, 6, x + 1, 9], fill=shade(BRASS, 1.35))
    save(cside, "block/centrifuge_side")
    ctop = noisy_fill("centrifuge_top", iron, amount=0.08, cells=2)
    d = ImageDraw.Draw(ctop)
    d.rectangle([0, 0, 15, 15], outline=shade(iron, 0.5))
    d.ellipse([3, 3, 12, 12], outline=shade(BRASS, 1.0))
    d.ellipse([6, 6, 9, 9], fill=(40, 40, 46))
    save(ctop, "block/centrifuge_top")
    cbottom = noisy_fill("centrifuge_bottom", shade(iron, 0.8), amount=0.08, cells=2)
    d = ImageDraw.Draw(cbottom)
    d.rectangle([0, 0, 15, 15], outline=shade(iron, 0.45))
    d.rectangle([6, 6, 9, 9], fill=shade(BRASS, 0.8))
    save(cbottom, "block/centrifuge_bottom")


# ---------------------------------------------------------------------------------------------------------- equipment

T_HELMET = [
    "................",
    "................",
    "................",
    "....OOOOOOOO....",
    "...O55444443O...",
    "..O5444x443332O.",
    "..O44OOOOOO32O..",
    "..O43O....O32O..",
    "..O43O....O21O..",
    "..O32O....O21O..",
    "..OOOO....OOOO..",
    "................",
    "................",
    "................",
    "................",
    "................",
]

T_CHESTPLATE = [
    "................",
    "..OOOO....OOOO..",
    ".O5443OOOOO432O.",
    ".O44433444333 2O",
    ".O4333444433332O",
    ".OOO334x44333OOO",
    "...O34444333O...",
    "...O34444332O...",
    "...O34444332O...",
    "...O33444332O...",
    "...O33333322O...",
    "...O22222211O...",
    "...OOOOOOOOOO...",
    "................",
    "................",
    "................",
]

T_LEGGINGS = [
    "................",
    "................",
    "...OOOOOOOOOO...",
    "...O54xxxx32O...",
    "...O44444332O...",
    "...O443OO332O...",
    "...O43O..O32O...",
    "...O43O..O32O...",
    "...O43O..O32O...",
    "...O33O..O21O...",
    "...O32O..O21O...",
    "...O32O..O21O...",
    "...OOOO..OOOO...",
    "................",
    "................",
    "................",
]

T_BOOTS = [
    "................",
    "................",
    "................",
    "................",
    "................",
    "...OOOO..OOOO...",
    "...O54O..O43O...",
    "...O43O..O32O...",
    "...O43O..O32O...",
    "..O443O.O432O...",
    ".O4433O.O3322O..",
    ".OxxxxO.OxxxxO..",
    ".OOOOOO.OOOOOO..",
    "................",
    "................",
    "................",
]

# Boots of the Traveller: leather with a fur cuff and a white wing.
T_TRAVELLER_BOOTS = [
    "................",
    "................",
    "................",
    "............ww..",
    "...ffff..ffffww.",
    "...ffff..ffffw..",
    "...O43O..O32Ow..",
    "...O43O..O32O...",
    "...O43O..O32O...",
    "..O443O.O432O...",
    ".O4433O.O3322O..",
    ".O1111O.O1111O..",
    ".OOOOOO.OOOOOO..",
    "................",
    "................",
    "................",
]

# Thaumaturge's robe and leggings: cloth with a gold trim.
T_ROBE = [
    "................",
    "..OOOO....OOOO..",
    ".O5443OOOO4432O.",
    ".O4433xOOx4332O.",
    ".O443333x33332O.",
    ".OOO3333x333OOO.",
    "...O3333x332O...",
    "...O4333x332O...",
    "...O4333x332O...",
    "...O3333x332O...",
    "..O33333x3322O..",
    "..O33333x3322O..",
    "..O22222x2211O..",
    "..OxxxxxxxxxxO..",
    "..OOOOOOOOOOOO..",
    "................",
]

T_ROBE_LEGS = [
    "................",
    "................",
    "...OOOOOOOOOO...",
    "...OxxxxxxxxO...",
    "...O44444332O...",
    "...O443OO332O...",
    "...O43O..O32O...",
    "...O43O..O32O...",
    "...O43O..O32O...",
    "...O33O..O21O...",
    "...O32O..O21O...",
    "...OxxO..OxxO...",
    "...OOOO..OOOO...",
    "................",
    "................",
    "................",
]

EQUIPMENT_SETS = {
    # id: (main colour, trim colour, cloth?)
    "thaumium": (THAUMIUM, BRASS, False),
    "void": (rgb(0x2E1F46), rgb(0x9A6AE0), False),
    "thaumaturge": (rgb(0x4A2A78), rgb(0xE8C04A), True),
    "traveller": (rgb(0x8A5A32), rgb(0xE8DCC0), True),
    "fortress": (rgb(0x55407F), rgb(0xE0B84A), False),
}


def tool_sprite(kind, head, handle, accent):
    """A 16x16 tool or weapon: a wooden handle along the diagonal and a head drawn from simple shapes."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()

    def put(x, y, color):
        if 0 <= x < 16 and 0 <= y < 16:
            px[x, y] = color

    def handle_band(x_from, x_to):
        for x in range(x_from, x_to + 1):
            put(x, 15 - x, handle["d"])
            put(x, 16 - x, handle["b"])

    if kind == "sword":
        for x in range(6, 15):
            put(x, 15 - x, head["5"] if x > 8 else head["4"])
            put(x, 16 - x, head["3"] if x > 8 else head["2"])
        put(14, 0, head["4"])
        put(15, 0, head["5"])
        for i in range(5):
            put(3 + i, 8 + i, accent["4"] if i == 2 else accent["3"])
        handle_band(2, 4)
        put(1, 14, accent["4"])
        put(1, 15, accent["2"])
        put(2, 15, accent["3"])
    elif kind == "pickaxe":
        handle_band(2, 10)
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - 2.5, y - 12.5)
                if 9.2 <= d <= 11.2 and x >= 3 and y <= 12 and x - y > -9 and x - y < 13:
                    shade_key = "5" if d < 9.9 and (x + y) < 15 else "4" if d < 10.4 else "2"
                    put(x, y, head[shade_key])
    elif kind == "axe":
        handle_band(2, 13)
        blade = {1: (8, 9), 2: (7, 10), 3: (6, 11), 4: (6, 10), 5: (7, 9), 6: (8, 8)}
        for y, (x_from, x_to) in blade.items():
            for x in range(x_from, x_to + 1):
                put(x, y, head["5"] if x + y <= 9 or x == x_from else head["4"] if x + y <= 12 else head["2"])
        for x, y in ((13, 4), (14, 4), (13, 5)):
            put(x, y, head["3"])
    elif kind == "shovel":
        handle_band(2, 9)
        for y in range(16):
            for x in range(16):
                if abs((x - 11.5) + (y - 4.5)) <= 3.6 and abs((x - 11.5) - (y - 4.5)) <= 2.6:
                    light = (x - y) < 6
                    put(x, y, head["5"] if light and x + y < 15 else head["4"] if light else head["2"])
        put(9, 6, accent["3"])
    elif kind == "hoe":
        handle_band(2, 11)
        for x, y, key in ((6, 2, "5"), (7, 2, "5"), (8, 2, "4"), (9, 2, "4"), (10, 2, "4"), (11, 3, "3"),
                          (7, 3, "4"), (8, 3, "3"), (9, 3, "3"), (10, 3, "3"), (5, 3, "4"), (5, 4, "3"), (6, 3, "4")):
            put(x, y, head[key])
    else:
        raise ValueError(kind)
    return outline(img, head["O"])


def face(img, noise, x0, y0, w, h, palette, rows=None, edge=True):
    """Fills one face of an armour model (u, v, width, height) with noisy shading, darker along its lowest row."""
    px = img.load()
    for y in range(y0, y0 + h):
        if rows is not None and (y - y0) not in rows:
            continue
        for x in range(x0, x0 + w):
            n = noise[y % len(noise)][x % len(noise[0])]
            key = "4" if n > 0.72 else "2" if n < 0.28 else "3"
            if edge and rows is not None and y - y0 == max(rows):
                key = "1"
            px[x, y] = palette[key]


def box(img, noise, u, v, w, h, d, palette, rows=None, faces=("top", "right", "front", "left", "back")):
    """Paints a model box laid out like vanilla's (u, v origin; width, height, depth); rows limits the side faces
    to some rows counted from the top."""
    spec = {
        "top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h), "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h),
    }
    for name in faces:
        x0, y0, fw, fh = spec[name]
        face(img, noise, x0, y0, fw, fh, palette, None if name in ("top", "bottom") else rows)


def armor_layers(set_id, pieces):
    main, trim, cloth = EQUIPMENT_SETS[set_id]
    palette = ramp(main, 0.4)
    trims = ramp(trim, 0.5)
    r = rng("armor_" + set_id)
    noise = value_noise(r, 64, 8 if cloth else 12)
    outer = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    inner = Image.new("RGBA", (64, 32), (0, 0, 0, 0))
    o = outer.load()
    if pieces["helmet"]:
        box(outer, noise, 0, 0, 8, 8, 8, palette, rows=range(0, 6), faces=("top", "right", "left", "back"))
        box(outer, noise, 0, 0, 8, 8, 8, palette, rows=range(0, 3), faces=("front",))
        for y in range(11, 14):
            o[8, y] = palette["2"]
            o[15, y] = palette["2"]
        for x in range(8, 16):
            o[x, 10] = trims["4"]
        for x in range(0, 32):
            if o[x, 13][3]:
                o[x, 13] = trims["3"]
    if pieces["chestplate"]:
        box(outer, noise, 16, 16, 8, 12, 4, palette)
        sleeve = range(0, 12) if cloth else range(0, 5)
        box(outer, noise, 40, 16, 4, 12, 4, palette, rows=sleeve)
        for x in range(16, 40):
            o[x, 20] = trims["4"]
        for x in range(40, 56):
            o[x, 20 + len(sleeve) - 1] = trims["3"]
        if cloth:
            for y in range(21, 32):
                o[23, y] = trims["3"]
                o[24, y] = trims["4"]
            for x in range(16, 40):
                o[x, 31] = trims["3"]
        else:
            o[23, 23] = trims["5"]
            o[24, 23] = trims["4"]
            o[23, 24] = trims["3"]
            o[24, 24] = trims["2"]
    if pieces["boots"]:
        start = 5 if set_id == "traveller" else 7
        box(outer, noise, 0, 16, 4, 12, 4, palette, rows=range(start, 12), faces=("right", "front", "left", "back"))
        box(outer, noise, 0, 16, 4, 12, 4, palette, faces=("bottom",))
        for x in range(0, 16):
            o[x, 20 + start] = trims["4"]
            if set_id == "traveller":
                o[x, 21 + start] = trims["3"]
        if set_id == "traveller":
            for x, y in ((0, 26), (1, 25), (2, 25), (1, 26), (8, 26), (9, 25), (10, 25), (9, 26)):
                o[x, y] = (246, 246, 240, 255)
    if pieces["leggings"]:
        i = inner.load()
        box(inner, noise, 16, 16, 8, 12, 4, palette, rows=range(6, 12), faces=("right", "front", "left", "back"))
        legs = range(0, 12) if cloth else range(0, 10)
        box(inner, noise, 0, 16, 4, 12, 4, palette, rows=legs, faces=("top", "right", "front", "left", "back"))
        for x in range(16, 40):
            if i[x, 26][3]:
                i[x, 26] = trims["4"]
        for x in range(0, 16):
            i[x, 20 + len(legs) - 1] = trims["3"]
        save(inner, f"entity/equipment/humanoid_leggings/{set_id}")
    if pieces["helmet"] or pieces["chestplate"] or pieces["boots"]:
        save(outer, f"entity/equipment/humanoid/{set_id}")


def equipment():
    sys.path.insert(0, os.path.join(ROOT, "tools", "content"))
    import equipment as table  # noqa: E402
    handle = ramp_as(WOOD, "abcde")
    for material, spec in table.TOOL_MATERIALS.items():
        head = ramp(rgb(spec[6]) if material != "void" else rgb(0x3A2858), 0.3)
        accent = ramp(BRASS if material == "thaumium" else rgb(0x9A6AE0))
        for kind in table.TOOL_KINDS:
            save(tool_sprite(kind, head, handle, accent), f"item/{material}_{kind}")
    templates = {"helmet": T_HELMET, "chestplate": T_CHESTPLATE, "leggings": T_LEGGINGS, "boots": T_BOOTS}
    for set_id, spec in table.ARMOR_SETS.items():
        main, trim, cloth = EQUIPMENT_SETS[set_id]
        palette = dict(ramp(main if set_id != "void" else rgb(0x3A2858), 0.3))
        palette["x"] = shade(trim, 1.05) + (255,)
        palette[" "] = palette["3"]
        palette["f"] = shade(trim, 1.0) + (255,)
        palette["w"] = (246, 246, 240, 255)
        for piece in table.PIECES:
            if spec[0][piece]:
                rows = T_TRAVELLER_BOOTS if set_id == "traveller" else templates[piece]
                if set_id == "thaumaturge":
                    rows = {"chestplate": T_ROBE, "leggings": T_ROBE_LEGS}.get(piece, rows)
                save(template(rows, palette), f"item/{set_id}_{piece}")
        armor_layers(set_id, spec[0])
    void = ramp(rgb(0x3A2858), 0.3)
    void["5"] = shade(rgb(0x9A6AE0), 1.1) + (255,)
    save(template(T_INGOT, void), "item/void_metal_ingot")
    save(template(T_NUGGET, void), "item/void_metal_nugget")


# ---------------------------------------------------------------------------------------------------------- flora

FLORA_WOODS = {
    # id: (bark, wood, leaves)
    "greatwood": (rgb(0x4A3626), rgb(0x9A7448), rgb(0x2F6B2C)),
    "silverwood": (rgb(0xC9CFDA), rgb(0xE6E9EF), rgb(0x7FB9C8)),
}


def bark(name, color, silver=False):
    r = rng(name)
    n = value_noise(r, 16, 4)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    furrows = [r.randrange(16) for _ in range(4)]
    for y in range(16):
        for x in range(16):
            f = 0.92 + (n[(y // 3) % 16][x] - 0.5) * 0.3 + (r.random() - 0.5) * 0.08
            if not silver and any((x - fx - (y // 5)) % 16 == 0 for fx in furrows):
                f *= 0.62
            px[x, y] = shade(color, f) + (255,)
    if silver:
        # Silverwood bark: pale with short dark streaks and a faint blue sheen.
        for _ in range(9):
            x, y, w = r.randrange(16), r.randrange(16), r.randrange(2, 5)
            for i in range(w):
                px[(x + i) % 16, y] = shade(color, 0.55) + (255,)
        for _ in range(6):
            px[r.randrange(16), r.randrange(16)] = (190, 225, 245, 255)
    return img


def log_top(name, bark_color, wood_color):
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    r = rng(name)
    for y in range(16):
        for x in range(16):
            dx, dy = x - 7.5, y - 7.5
            d = math.hypot(dx, dy)
            if max(abs(dx), abs(dy)) > 6.6:
                px[x, y] = shade(bark_color, 0.9 + (r.random() - 0.5) * 0.15) + (255,)
            else:
                ring = 0.88 if int(d * 1.1) % 2 else 1.04
                px[x, y] = shade(wood_color, ring + (r.random() - 0.5) * 0.05) + (255,)
    return img


def leaves(name, color, sparkle=None):
    r = rng(name)
    n = value_noise(r, 16, 8)
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    for y in range(16):
        for x in range(16):
            v = n[y][x] + (r.random() - 0.5) * 0.35
            if v < 0.3:
                continue
            f = 0.6 + v * 0.7
            px[x, y] = shade(color, f) + (255,)
    if sparkle:
        for _ in range(5):
            x, y = r.randrange(16), r.randrange(16)
            if px[x, y][3]:
                px[x, y] = sparkle + (255,)
    return img


def sprite(points, size=16):
    """A 16x16 sprite from {(x, y): rgba} with a dark outline."""
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    px = img.load()
    for (x, y), c in points.items():
        if 0 <= x < size and 0 <= y < size:
            px[x, y] = c if len(c) == 4 else c + (255,)
    return img


def sapling(name, stem, leaf, sparkle=None):
    r = rng(name)
    pts = {}
    for y in range(9, 16):
        pts[(7, y)] = shade(stem, 0.9)
        pts[(8, y)] = shade(stem, 0.7)
    for cx, cy, rad in ((7.5, 6, 4.2), (4.5, 8.5, 2.6), (10.5, 8, 2.6), (7.5, 2.5, 2.2)):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x - cx, y - cy)
                if d <= rad and r.random() > 0.15:
                    pts[(x, y)] = shade(leaf, 1.25 - d / rad * 0.6)
    if sparkle:
        for x, y in ((6, 4), (9, 7), (4, 8), (11, 9)):
            pts[(x, y)] = sparkle
    return sprite(pts)


def flower(kind):
    pts = {}
    stem = rgb(0x3E7A35)
    if kind == "shimmerleaf":
        # Tall silvery-blue fronds with glowing tips.
        for y in range(4, 16):
            pts[(8, y)] = shade(stem, 0.9)
        for i in range(6):
            pts[(7 - i // 2, 12 - i)] = shade(rgb(0x9FD3E0), 1.0 + i * 0.05)
            pts[(9 + i // 2, 11 - i)] = shade(rgb(0x9FD3E0), 0.9 + i * 0.05)
            pts[(6 - i // 3, 15 - i)] = shade(rgb(0x7FB0C0), 0.9)
            pts[(10 + i // 3, 15 - i)] = shade(rgb(0x7FB0C0), 0.8)
        for x, y in ((8, 3), (5, 7), (11, 6), (4, 10), (12, 9)):
            pts[(x, y)] = (225, 250, 255)
    elif kind == "cinderpearl":
        # A stalk with drooping leaves and a glowing ember-coloured pearl.
        for y in range(7, 16):
            pts[(8, y)] = shade(rgb(0x6B5A2E), 0.9)
        for i in range(4):
            pts[(7 - i, 12 + i // 2)] = shade(rgb(0x8A7A3A), 1.0)
            pts[(9 + i, 11 + i // 2)] = shade(rgb(0x8A7A3A), 0.85)
        for y in range(2, 8):
            for x in range(5, 12):
                d = math.hypot(x - 8, y - 4.5)
                if d <= 3.0:
                    pts[(x, y)] = shade(rgb(0xF07A28), 1.35 - d / 3.0 * 0.7)
        pts[(7, 3)] = (255, 236, 160)
        pts[(8, 3)] = (255, 220, 120)
    elif kind == "vishroom":
        # A purple mushroom with pale spots on a thick stalk.
        for y in range(10, 16):
            for x in range(7, 10):
                pts[(x, y)] = shade(rgb(0xD9CDBA), 1.0 if x == 7 else 0.8)
        for y in range(4, 11):
            for x in range(2, 15):
                if (x - 8) ** 2 / 36.0 + (y - 10) ** 2 / 36.0 <= 1.0 and y <= 10:
                    pts[(x, y)] = shade(rgb(0x7A3FB0), 1.25 - (y - 4) * 0.08)
        for x, y in ((5, 7), (8, 5), (11, 7), (9, 8), (6, 9)):
            pts[(x, y)] = (235, 205, 255)
    else:
        raise ValueError(kind)
    return sprite(pts)


def crystal_cluster(color):
    """Amethyst-cluster-like shards in a primal's colour, drawn on the cross model's plane."""
    pts = {}
    shards = ((7.5, 2, 1.6), (4, 6, 1.3), (11, 5, 1.3), (5.5, 9, 1.1), (10, 9, 1.1))
    for cx, top, half in shards:
        for y in range(int(top), 16):
            w = half * (0.6 + (y - top) / (16 - top) * 0.6)
            for x in range(16):
                if abs(x - cx) <= w:
                    light = 1.35 if x < cx else 0.95
                    pts[(x, y)] = shade(color, light - (y - top) * 0.02)
    for cx, top, half in shards:
        pts[(int(cx), int(top))] = shade(color, 1.7)
    return sprite(pts)


def node_glow():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = img.load()
    for y in range(32):
        for x in range(32):
            d = math.hypot(x - 15.5, y - 15.5) / 15.5
            if d < 1:
                a = (1 - d) ** 1.8
                core = max(0.0, 1 - d * 3.0)
                v = clamp(200 + 55 * core)
                px[x, y] = (v, v, v, clamp(255 * a))
    return img


def flora():
    for wood_id, (bark_color, wood_color, leaf_color) in FLORA_WOODS.items():
        silver = wood_id == "silverwood"
        save(bark(f"{wood_id}_log", bark_color, silver), f"block/{wood_id}_log")
        save(log_top(f"{wood_id}_log_top", bark_color, wood_color), f"block/{wood_id}_log_top")
        save(wood(f"{wood_id}_planks", wood_color), f"block/{wood_id}_planks")
        save(leaves(f"{wood_id}_leaves", leaf_color, (230, 250, 255) if silver else None), f"block/{wood_id}_leaves")
        save(sapling(f"{wood_id}_sapling", bark_color, leaf_color, (230, 250, 255) if silver else None),
             f"block/{wood_id}_sapling")
    for kind in ("shimmerleaf", "cinderpearl", "vishroom"):
        save(flower(kind), f"block/{kind}")
    for primal, color in CRYSTAL_COLORS.items():
        save(crystal_cluster(color), f"block/{primal}_crystal_cluster")
    save(node_glow(), "misc/node_glow")


# ---------------------------------------------------------------------------------------------------------- misc

def liquids():
    """Light grey, tileable liquid surface; renderers tint it with water or essentia colours and scroll it."""
    size = 32
    r = rng("essentia_liquid")
    broad = value_noise(r, size, 4)
    fine = value_noise(r, size, 8)
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            v = 0.72 + 0.18 * broad[y][x] + 0.1 * fine[y][x]
            # Soft highlight ripples.
            if abs(fine[y][x] - 0.5) < 0.03:
                v = min(1.0, v + 0.12)
            g = clamp(255 * v)
            px[x, y] = (g, g, g, 255)
    save(img, "misc/essentia_liquid")


def main():
    items()
    blocks()
    particles()
    aspects()
    backgrounds()
    liquids()
    wands()
    essentia_transport()
    equipment()
    flora()
    logo()
    print(f"Wrote {len(WRITTEN)} textures ({len(OVERRIDES)} overridden paths skipped)")


if __name__ == "__main__":
    main()
