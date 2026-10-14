# Thaumic Unity

A NeoForge mod for **Minecraft 26.3** that brings the magic of Thaumcraft (every version) and its major addons into
one mod, then expands it: aspects, research, aura and vis, alchemy, arcane crafting, infusion and a data-driven
spell system, written from scratch with original code, text and art.

> **Status: alpha, phase 4 of 9 under way.** Core systems work end to end (scan → research → crucible / arcane workbench /
> infusion → caster's gauntlet and foci). Later phases add the migrated content, golems, worldgen, dimensions,
> integrations and polish. See [docs/PLAN.md](docs/PLAN.md) for the plan, feature counts and exact next steps.

## What is in this version

| System | Contents |
|---|---|
| Aspects | 183 aspects (primal, Thaumcraft 4/6, addon and new ones) as a datapack registry; item aspects from explicit data, tag rules and recipe inheritance; optional Thaumcraft 6 combinations datapack |
| Research | Thaumonomicon with 5 categories and 26 entries; staged requirements (scan, craft, obtain, knowledge, insight puzzles, …); unified, classic or modern research mode |
| Aura | Per-chunk vis and flux with biome profiles and primal attunement, cheap bounded simulation, aura HUD |
| Crafting | Crucible, arcane workbench, alchemical furnace and warded jars, runic matrix infusion with a stability model |
| Essentia | Suction-driven tubes and valves, essentia buffer, centrifuge; labelled jars pull their aspect |
| Spells | 81 spell components (mediums, effects, synergies, modifiers) on 44 Java primitives; caster's gauntlets; 3 focus tiers; 17 focus archetypes |
| Wands | Rods and caps as data (7 rods, 5 caps, 35 combinations); per-primal vis storage, fixed foci, vis crystals, aura recharge and a recharge pedestal |
| World | Cinnabar and amber ores (switchable), arcane stone, metals |

## Playing

Craft **Salis Mundus** (redstone, glowstone dust, bone meal, amethyst shard) and use it on a bookshelf to get the
**Thaumonomicon**; it explains everything else. Salis Mundus also turns a crafting table into an arcane workbench, a
cauldron into a crucible and a lectern into a research table.

### Installing

1. Install [NeoForge](https://neoforged.net) 26.3.0.69-beta or newer for Minecraft 26.3.
2. Get `thaumicunity-<version>.jar` from the [releases](https://github.com/hexotronic/hello-world/releases), from
   the latest successful [Build run](https://github.com/hexotronic/hello-world/actions/workflows/build.yml)
   (artifact `thaumicunity-jar`, needs a GitHub login) or build it yourself (below), and put it in `.minecraft/mods`.

### Thaumcraft's look

Thaumic Unity ships with its own art. If you own Thaumcraft, `tools/import_thaumcraft_textures.py` turns your jar
into a personal resource pack that gives Thaumic Unity Thaumcraft's textures; see
[Using Thaumcraft's textures](docs/ASSETS_AND_LICENSING.md#using-thaumcrafts-textures-on-your-own-computer).

## Building

Requirements: JDK 25. The Gradle wrapper downloads everything else.

```sh
./gradlew build            # compiles, runs the unit tests, produces build/libs/thaumicunity-<version>.jar
./gradlew runClient        # development client
./gradlew runGameTestServer  # loads all data on a server and runs the in-game tests
./gradlew runSmokeClient     # scripted client tour: test world, every device and screen, screenshots, then quits
```

CI runs all three on every push; the client tour runs under a virtual display and its screenshots are uploaded as
the `client-screenshots` artifact. The **Release** workflow (Actions › Release › Run workflow) builds the jar and
publishes it as a GitHub pre-release.

Generated content is committed. After editing a table in `tools/content/` regenerate it with:

```sh
python3 tools/gen_content.py    # data and asset JSON, language file (validates every table)
python3 tools/gen_textures.py   # textures (requires Pillow)
```

## Repository layout

| Path | What |
|---|---|
| `src/main/java/dev/thaumicunity/core` | Minecraft-independent logic (aspect graph, research graph and insight puzzles, spell compiler, aura simulation, infusion stability, aspect inheritance) with JUnit tests |
| `src/main/java/dev/thaumicunity/api` | Public API for addons: events, spell primitive interfaces, facade |
| `src/main/java/dev/thaumicunity/common` | Server and shared game code: registries, blocks, items, devices, research, aura, spells |
| `src/main/java/dev/thaumicunity/client` | Screens, HUD, tooltips, renderers, particles |
| `tools/` | Content tables and generators, CI API probe |
| `docs/` | [Plan](docs/PLAN.md), [inventory of source mods](docs/INVENTORY.md), [architecture](docs/ARCHITECTURE.md), [design decisions](docs/DESIGN_DECISIONS.md), [assets and licensing](docs/ASSETS_AND_LICENSING.md) |

`index.html`, `styles.css` and `images/` are the repository's original GitHub Pages starter files and are not part of
the mod.

## Licensing

No assets, code or text from Thaumcraft or its addons are included (the texture importer reads your own jar on your
own computer); see
[docs/ASSETS_AND_LICENSING.md](docs/ASSETS_AND_LICENSING.md). The mod licence in `gradle.properties` is still the MDK
default (All Rights Reserved) until the project owner chooses one.
