#!/usr/bin/env python3
"""Writes a stand-in "Thaumcraft jar" for CI: a few generated checkerboard textures under Thaumcraft's file names, so
the client's Thaumcraft texture loader can be tested without any Thaumcraft file.

    python3 tools/ci/make_fake_thaumcraft_jar.py run/smoke/thaumicunity/fake-thaumcraft.jar
"""
import io
import sys
import zipfile

from PIL import Image


def checker(a, b, size=16):
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            px[x, y] = a if (x // 4 + y // 4) % 2 == 0 else b
    return img


def png(img):
    out = io.BytesIO()
    img.save(out, "PNG")
    return out.getvalue()


def main():
    magenta, black = (255, 0, 255, 255), (20, 20, 20, 255)
    grey_light, grey_dark = (230, 230, 230, 255), (120, 120, 120, 255)
    files = {
        "items/thaumonomicon.png": checker(magenta, black),
        "blocks/crucible1.png": checker(magenta, black),
        "items/ingot_thaumium.png": checker((80, 255, 80, 255), black),
        # Grey artwork that the loader must tint (Ignis glyph, vis crystals).
        "aspects/ignis.png": checker(grey_light, grey_dark),
        "items/crystal_essence.png": checker(grey_light, grey_dark),
    }
    with zipfile.ZipFile(sys.argv[1], "w") as jar:
        for name, img in files.items():
            jar.writestr("assets/thaumcraft/textures/" + name, png(img))
    print(f"wrote {sys.argv[1]} with {len(files)} stand-in textures")


if __name__ == "__main__":
    main()
