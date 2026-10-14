#!/usr/bin/env python3
"""Extracts screenshots printed by print_screenshots.py from a saved CI log into a folder.

    python3 tools/ci/decode_screenshots.py job.log out/
"""
import base64
import json
import os
import re
import sys


def main():
    source, target = sys.argv[1], sys.argv[2]
    text = open(source, encoding="utf-8", errors="replace").read()
    try:
        text = json.loads(text).get("logs_content", text)
    except (ValueError, AttributeError):
        pass
    os.makedirs(target, exist_ok=True)
    name, parts, count = None, [], 0
    for line in text.splitlines():
        line = re.sub(r"^\d{4}-\d\d-\d\dT[\d:.]+Z ", "", line).strip()
        if line.startswith("===== SCREENSHOT "):
            name, parts = line.split()[2], []
        elif line.startswith("===== END ") and name:
            with open(os.path.join(target, os.path.splitext(name)[0] + ".jpg"), "wb") as f:
                f.write(base64.b64decode("".join(parts)))
            count += 1
            name = None
        elif name:
            parts.append(line)
    print(f"{count} screenshots written to {target}")


if __name__ == "__main__":
    main()
