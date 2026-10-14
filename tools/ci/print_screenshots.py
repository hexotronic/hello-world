#!/usr/bin/env python3
"""Prints smoke test screenshots into the CI log as base64 JPEG, so they can be read without artifact access.

    python3 tools/ci/print_screenshots.py run/smoke/screenshots [max_width]

Each image becomes "===== SCREENSHOT <name> <width>x<height>" followed by lines of base64 and "===== END <name>".
Decode with: tools/ci/decode_screenshots.py <saved log>.
"""
import base64
import io
import os
import sys

from PIL import Image

CHUNK = 20000


def main():
    folder = sys.argv[1]
    max_width = int(sys.argv[2]) if len(sys.argv) > 2 else 960
    names = sorted(n for n in os.listdir(folder) if n.lower().endswith(".png")) if os.path.isdir(folder) else []
    if not names:
        print(f"===== NO SCREENSHOTS in {folder}")
        return
    for name in names:
        image = Image.open(os.path.join(folder, name)).convert("RGB")
        if image.width > max_width:
            image = image.resize((max_width, round(image.height * max_width / image.width)), Image.LANCZOS)
        out = io.BytesIO()
        image.save(out, "JPEG", quality=72, optimize=True)
        data = base64.b64encode(out.getvalue()).decode("ascii")
        print(f"===== SCREENSHOT {name} {image.width}x{image.height}")
        for i in range(0, len(data), CHUNK):
            print(data[i:i + CHUNK])
        print(f"===== END {name}")


if __name__ == "__main__":
    main()
