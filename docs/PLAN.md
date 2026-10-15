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
| Research entries | ≈ 520 | ≈ 2 600 | ≈ 3 100 | 40 (phase 3 starter tree and phase 4 entries, original text) |
| Items | ≈ 900 | ≈ 4 500 | ≈ 5 400 | 87 (68 items + 19 block items; one wand item covers 35 rod and cap combinations) |
| Blocks | ≈ 450 | ≈ 2 200 | ≈ 2 650 | 20 |
| Devices (block entities) | ≈ 180 | ≈ 900 | ≈ 1 080 | 7 (crucible, arcane workbench, research table, runic matrix, arcane pedestal, warded jar, alchemical furnace) |
| Recipes (arcane, crucible, infusion, rituals) | ≈ 1 100 | ≈ 5 500 | ≈ 6 600 | 139 (75 arcane, 13 crucible, 13 infusion, 17 focus archetypes, 21 crafting and smelting) |
| **Spell components** | ≈ 72 | **≈ 650** (10×) | ≈ 720 | see §2 |
| Golem parts and behaviours | ≈ 95 | ≈ 475 | ≈ 570 | 0 (phase 5) |
| Armor sets | ≈ 14 | ≈ 60 | ≈ 74 | 5 (thaumium, void metal, fortress, thaumaturge's robes, Boots of the Traveller) |
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
| 3 | Core systems: aspects, research, aura/vis, crucible, arcane crafting, infusion, spell framework | **Done** — Minecraft-independent core with 47 unit tests; NeoForge layer, client screens, 258 generated textures, all phase 3 data; in-game tests run in CI |
| 4 | Migration of all existing content (items, blocks, devices, wands, foci, tools, armor, flora, ores, basic mobs) | In progress — steps 1–4 done (wands, essentia transport, classic foci, tools and armour) |
| 5 | New content: devices, golems, armor, worldgen, mobs, bosses, dimensions, endgame | Not started |
| 6 | Spell expansion to ≈720 components, focus workbench, synergies, forbidden magic | Framework done; catalogue not started |
| 7 | Integrations (JEI, EMI, REI, Jade, Curios, KubeJS, CraftTweaker, Create, Mekanism, AE2, RS, Botania, Ars Nouveau, Iron's Spells, Blood Magic, Tinkers', Farmer's Delight) and API docs | Not started |
| 8 | Visual polish: animated Thaumonomicon, GUIs, models, particles and shaders, sound, balancing, configs | Not started |
| 9 | Testing: single player, dedicated server, large packs, performance | CI: build, 47 unit tests, 10 in-game tests on a dedicated server, scripted client tour with 12 screenshots |

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

Phase 3 is complete. **Phase 4, migration of existing content**, continues with step 5:

1. ~~`common/wand/` — wands~~ **done**: rods and caps are datapack registries (`thaumicunity:wand_rod`,
   `thaumicunity:wand_cap`, tables in `tools/content/wands.py`); `WandItem` stores vis per primal, casts its fixed
   focus through the same `Casting` steps as gauntlets, eats vis crystals for shortfalls, recharges from the aura
   when carried and faster on the wand recharge pedestal; one arcane recipe per rod and cap; game test `wands`.
   Still to come with later steps: greatwood, silverwood and primal rods (need the trees), staves and sceptres,
   void caps, paying arcane workbench costs from a wand.
2. ~~`common/essentia/` — essentia transport~~ **done**: suction model (`EssentiaNode`, `EssentiaTransfer`), essentia
   tubes and valves (multipart models), essentia buffer, centrifuge; warded jars pull through their lid (labelled
   jars harder) and the alchemical furnace offers its essentia on every side, so no alembic is needed (as in
   Thaumcraft 6); game test `essentia_transport`. Still to come: the essentia mirror (with mirrors) and filtered
   tubes.
3. ~~Classic focus archetypes~~ **done**: Smelting, Telekinesis, Equal Trade, Portable Hole (new `phase` primitive;
   the opened blocks are kept in `phased_space` block entities, so holes close even across restarts), Primal and
   Thunderhead, with two Auromancy entries; game test `portable_hole`. 17 focus archetypes in all.
4. ~~Tools, weapons and armour~~ **done** (`tools/content/equipment.py`, `TUEquipment`, `EquipmentEffects`):
   thaumium and void metal swords, pickaxes, axes, shovels and hoes; thaumium, void metal and infused thaumium
   fortress armour; thaumaturge's robes; Boots of the Traveller (step height and speed). Void metal is made in the
   crucible, mends itself (tag `thaumicunity:self_repairing`) and its blades weaken (tag `thaumicunity:weakening`);
   worn robes and goggles spare spell vis (data map `thaumicunity:vis_discount`, capped by `maxVisDiscount`). Each
   set has an equipment asset and generated 64×32 worn textures; five research entries; game test `equipment`.
   Still to come: fortress masks, crimson and primal armour, elemental tools, bone bows (with later steps).
5. Flora and ores: greatwood and silverwood trees, cinderpearl, shimmerleaf, vishroom, crystal clusters; nodes for
   the hybrid aura (D2).

Each step adds its content tables in `tools/content/`, its textures to `tools/gen_textures.py`, its research to
`tools/content/research.py` and an in-game test.
