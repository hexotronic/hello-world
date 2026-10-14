# Thaumic Unity — project plan and feature list

**Target platform (verified 2026-10-13):** Minecraft Java Edition **26.3** "Wilderness Bound" (released 2026-09-15,
latest stable), **NeoForge 26.3.0.69-beta** (the build pinned by the official 26.3 MDK), Java 25,
ModDevGradle 2.0.148, Gradle 9.2.1.

This is a very large project. It is delivered in nine phases; each phase leaves the mod buildable and playable.
The tables below give the **targets** (≈5× new content overall, ≈10× spell components) and track what is
**implemented** so far.

## 1. Feature counts

"Existing" is the de-duplicated union of every Thaumcraft version and the addons in [INVENTORY.md](INVENTORY.md).
"New" is original Thaumic Unity content. Targets aim at roughly five times the existing amount of new content,
weighted towards systems players interact with most.

| Category | Existing | New (target) | Total (target) | Implemented now |
|---|---:|---:|---:|---|
| Aspects | 67 | 116 | **183** | **183** (data, validated by unit tests) + optional TC6 pair datapack; explicit aspects for 183 items, 65 creatures, 31 tag rules, the rest derived from recipes |
| Research categories | 8 | 12 | 20 | 5 (fundamentals, alchemy, artifice, infusion, auromancy) |
| Research entries | ≈ 520 | ≈ 2 600 | ≈ 3 100 | 26 (phase 3 starter tree, original text) |
| Items | ≈ 900 | ≈ 4 500 | ≈ 5 400 | 43 (29 items + 14 block items) |
| Blocks | ≈ 450 | ≈ 2 200 | ≈ 2 650 | 14 |
| Devices (block entities) | ≈ 180 | ≈ 900 | ≈ 1 080 | 7 (crucible, arcane workbench, research table, runic matrix, arcane pedestal, warded jar, alchemical furnace) |
| Recipes (arcane, crucible, infusion, rituals) | ≈ 1 100 | ≈ 5 500 | ≈ 6 600 | 54 (9 arcane, 12 crucible, 3 infusion, 11 focus archetypes, 19 crafting and smelting) |
| **Spell components** | ≈ 72 | **≈ 650** (10×) | ≈ 720 | see §2 |
| Golem parts and behaviours | ≈ 95 | ≈ 475 | ≈ 570 | 0 (phase 5) |
| Armor sets | ≈ 14 | ≈ 60 | ≈ 74 | 0 (phase 4/5) |
| Baubles / trinkets | ≈ 40 | ≈ 200 | ≈ 240 | 0 (phase 5) |
| Mobs | ≈ 60 | ≈ 240 | ≈ 300 | 0 (phase 4/5) |
| Bosses | 6 | 24 | 30 | 0 (phase 5) |
| Biomes | ≈ 8 | ≈ 32 | ≈ 40 | 0 (phase 4/5) |
| Structures | ≈ 10 | ≈ 45 | ≈ 55 | 0 (phase 5) |
| Dimensions | 3 | 2 | 5 (Outer Lands with 3 regions, pocket planes, two new) | 0 (phase 5) |
| Warp events | ≈ 12 | ≈ 60 | ≈ 72 | flux side-effects only |
| Built-in integrations | — | 17 mods | 17 | 0 (phase 7) |

## 2. Spell system (≈10×)

| Part | Existing | Target | Java primitives now | JSON components now |
|---|---:|---:|---:|---|
| Mediums | ≈ 16 | 80 | 12 (touch, bolt, beam, chain, self, burst, wave, ground wave, plan, projectile incl. homing, zone incl. cloud/rune/mine/delay/aura, targeted zone) | 16 |
| Effects | ≈ 45 | 500 | 26 (damage, drain, potion, heal, shield, dispel, ignite, extinguish, freeze, break, place, exchange, push, teleport, light, lightning, explosion, grow, smelt, transmute, summon, experience, aura, warp, attract items, illusion) | 38 |
| Modifiers | ≈ 11 | 140 | 5 behaviours (numeric, echo, vampiric, gravity well, reveal) + any numeric modifier in JSON | 12 |
| Aspect synergies | 0 | 120 | automatic (compound aspect of two carried effects) | 14 |
| Forbidden / eldritch spells | ≈ 3 | 40 | warp cost supported by the compiler | phase 6 |

Hundreds of components are cheap because a component is a JSON file that configures a Java primitive. Phase 6
adds the remaining primitives and the bulk of the JSON catalogue (generated from `tools/content` tables), the
focus workbench GUI and the per-component visuals.

## 3. Phases

| Phase | Content | Status |
|---|---|---|
| 1 | Research and inventory of every source mod | **Done** — [INVENTORY.md](INVENTORY.md) (with a verification backlog) |
| 2 | Architecture: packages, registries, data formats, aspect system, API | **Done** — [ARCHITECTURE.md](ARCHITECTURE.md), [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md) |
| 3 | Core systems: aspects, research, aura/vis, crucible, arcane crafting, infusion, spell framework | **Done** — Minecraft-independent core with 45 unit tests; NeoForge layer, client screens, 258 generated textures, all phase 3 data; in-game tests run in CI |
| 4 | Migration of all existing content (items, blocks, devices, wands, foci, tools, armor, flora, ores, basic mobs) | Not started |
| 5 | New content: devices, golems, armor, worldgen, mobs, bosses, dimensions, endgame | Not started |
| 6 | Spell expansion to ≈720 components, focus workbench, synergies, forbidden magic | Framework done; catalogue not started |
| 7 | Integrations (JEI, EMI, REI, Jade, Curios, KubeJS, CraftTweaker, Create, Mekanism, AE2, RS, Botania, Ars Nouveau, Iron's Spells, Blood Magic, Tinkers', Farmer's Delight) and API docs | Not started |
| 8 | Visual polish: animated Thaumonomicon, GUIs, models, particles and shaders, sound, balancing, configs | Not started |
| 9 | Testing: single player, dedicated server, large packs, performance | CI: build, 45 unit tests, 7 in-game tests on a dedicated server |

## 4. Phase 3 deliverables in detail

* **Aspects:** 183 aspects as a synced datapack registry; item aspects resolved from data maps, tag rules,
  recipe inheritance (cycle-safe, memoised) and fallbacks; synced to clients; `/tu aspects`.
* **Research:** categories and entries as datapack registries; staged requirements (scan, craft, obtain, knowledge,
  insight, research, warp, dimension, kill); unified/classic/modern modes; scanning with the thaumometer grants
  aspect points and observations; research table with insight puzzles (hex grid) and theorising; player knowledge
  attachment (survives death, synced to its owner).
* **Aura:** per-chunk vis/flux/base with biome profiles and primal attunement; bounded round-robin simulation
  with closed-form catch-up; diffusion; flux side-effects; HUD snapshot packet.
* **Crafting:** crucible (heat, water, dissolving, catalysts, overflow to flux), arcane workbench (vis drawn from
  the aura with attunement, also does vanilla crafting), alchemical furnace → warded jar, infusion at the runic
  matrix (pedestals, essentia from jars, stability model with symmetry and stabilisers, instability events).
* **Spells:** spell component registry, primitive registry, compiler with cost/complexity/cooldown/warp and
  synergies, runner with mediums/effects/modifiers, projectile and zone entities, caster gauntlets and three
  focus tiers.

## 5. Next steps (exact continuation point)

Phase 3 is complete. Continue with **phase 4, migration of existing content**, in this order:

1. `common/wand/` — wands: rods and caps as datapack registries (`thaumicunity:wand_rod`, `thaumicunity:wand_cap`),
   `WandItem` storing vis per primal (data component), recharge from the aura and from vis crystals, the wand
   recharge pedestal; wands cast the same foci as gauntlets (D4).
2. `common/essentia/` — essentia tubes, valves, filters and buffers, the alembic on the alchemical furnace, the
   centrifuge and the essentia mirror.
3. TC4/TC6 focus archetypes as data (`tools/content/spells.py`): the remaining classic foci (portable hole, primal,
   warding, equal trade with presets, …) and their research.
4. Tools, weapons and armour (thaumium, void metal, robes, fortress) with their research.
5. Flora and ores: greatwood and silverwood trees, cinderpearl, shimmerleaf, vishroom, crystal clusters; nodes for
   the hybrid aura (D2).

Each step adds its content tables in `tools/content/`, its textures to `tools/gen_textures.py`, its research to
`tools/content/research.py` and an in-game test.
