# Assets and licensing

## Why nothing is copied from Thaumcraft or its addons

The request was to "use all resources and textures from those mods you can find". That cannot be done here:

* **Thaumcraft** (all versions, by Azanor) is distributed under an *All Rights Reserved* licence. Its code,
  textures, models, sounds and in-game text may not be copied into another project or redistributed.
* Most addons are either also All Rights Reserved, or use licences (for example CC BY-NC-SA, LGPL) whose terms
  do not fit into one combined project without the authors' permission. Many addon textures are also derived
  from Thaumcraft's own art.
* This repository is public, so anything committed is redistributed.

What *can* be reused freely are **ideas and game mechanics** (aspects as a concept, crucible alchemy, infusion
stability, chunk aura, golem seals…) and **common words** (Latin aspect names, descriptive names like "arcane
workbench"). Thaumic Unity therefore re-implements mechanics with **original code, original text and original
art**, and records where each mechanic comes from in [INVENTORY.md](INVENTORY.md).

If the rights holders grant permission in the future, their assets could be added as a separate, optional
resource pack with the licence terms they specify; the mod's resource paths are stable for that purpose.

## Where Thaumic Unity's art comes from

* `tools/gen_textures.py` generates every texture procedurally (Pillow): item sprites from character templates and
  palettes, block textures from noise and patterns, a rune glyph for each of the 183 aspects, particle sprites,
  research map backgrounds and the logo. The generator is deterministic, so the output can be regenerated and
  tweaked by changing palettes and shapes instead of hand-editing pixels.
* Screens (workbench, research table, furnace, Thaumonomicon) are drawn in code in a parchment-and-brass style, so
  they need no background textures.
* Hand-made textures can replace any generated file; the generator never overwrites paths listed in
  `tools/texture_overrides.txt`.
* Sounds: until original recordings exist, `sounds.json` forwards each Thaumic Unity sound event to a vanilla sound
  event (the sound files stay in the player's own game installation). Resource packs can retarget them.

## Licence of this project

The mod licence in `gradle.properties` is the MDK default ("All Rights Reserved") until the project owner chooses
one. Recommended: MIT or LGPL-3.0 for code (others can build addons against the API) and CC BY 4.0 for the
generated art and text. The Gradle wrapper and the MDK template files are under their own MIT/Apache licences.
