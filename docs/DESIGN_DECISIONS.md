# Design decisions — merging Thaumcraft versions and addons

Where versions or addons implemented the same idea differently, Thaumic Unity either keeps both behind a
config switch or merges them into one system that keeps the best of each. Each decision below names the
config keys involved (all in `config/thaumicunity-common.toml` unless stated otherwise).

---

## D1 — Aspect component pairs (TC4 vs TC6)

**Conflict.** TC4 and TC6 share 33 aspects but build several of them from different pairs
(Vitreus: TC4 Terra+Ordo, TC6 Terra+Aer; Metallum: TC4 Terra+Vitreus, TC6 Terra+Ordo; Permutatio: TC4
Motus+Aqua, TC6 Perditio+Ordo; Mortuus: TC4 Victus+Perditio, TC6 Aqua+Perditio; …). TC6 removed 15 TC4
aspects and added 4.

**Decision.** Keep *every* aspect from both versions. The TC4 pairs are the canonical defaults because they
form the larger, fully connected graph. TC6 pairs ship as a built-in optional datapack
`thaumicunity:modern_combinations` that overrides only the `components` field of the affected aspect files.
Because aspects are a datapack registry, a modpack can enable it in world creation or with `/datapack`.

**Invariant (enforced by `AspectGraph` and its unit tests).** Every compound has exactly two components; every
unordered pair is used at most once; the graph is acyclic; every aspect reaches the primals. A datapack that
violates this is rejected with a clear log message naming the offending entries instead of crashing.

## D2 — Vis: nodes (TC4) vs chunk aura (TC6)

**Conflict.** TC4 stores vis in discrete *aura nodes* that wands drain per primal; TC6 stores one vis value and
one flux value per chunk, and casters draw from it.

**Decision — hybrid aura (default).**
* Every chunk has `vis`, `flux` and a `base` level (from the biome, data-driven via the
  `thaumicunity:aura_profile` biome data map). Vis regenerates towards `base`, spreads to neighbours and is
  reduced by flux; flux above thresholds spawns rifts and drives taint (D6).
* Every chunk also has a **primal attunement**: six weights derived from the biome profile. Drawing vis from
  the chunk for a given primal costs `amount / weight`, so fire magic is cheap in deserts and expensive in
  oceans, which preserves the TC4 feeling of "this place is rich in Ignis".
* **Aura nodes** remain as rare worldgen block entities. A node is a concentrated source: it pushes vis into
  its chunk (amount by type/modifier), can be tapped directly by wands for its own aspects (TC4 behaviour), can
  be jarred, moved, stabilised, energised and corrupted.

`aura.mode = hybrid | chunk | nodes`
* `chunk` — TC6 behaviour: no node worldgen; chunk aura only.
* `nodes` — TC4 behaviour: casters cannot draw chunk vis; only wands tapping nodes and vis relays provide vis;
  chunk aura still tracks flux for taint.

## D3 — Research: hex notes (TC3/TC4) vs theorycraft (TC6)

**Conflict.** TC4 research is a puzzle: connect aspects on a hex grid using aspect points gained by scanning.
TC6 research is staged: entries have stages that require scanning, crafting, owning items and spending
knowledge points earned by theorycrafting with cards at the research table.

**Decision — unified stage model.** A research entry is a list of *stages*; each stage has requirements of these
types (all data-driven, `ResearchRequirement` codec):

| Type | Meaning | Origin |
|---|---|---|
| `scan` | Scan a thing (item/block/entity/tag/aspect) | TC4/TC6 |
| `craft` | Craft an item at least once | TC6 |
| `obtain` | Have items in the inventory (optionally consumed) | TC6 |
| `knowledge` | Spend observation or theory points of a category | TC6 |
| `insight` | Solve an insight puzzle that connects the given aspects | TC3/TC4 |
| `research` | Another entry is complete | all |
| `warp` | Minimum warp | TC4 |
| `dimension` / `biome` | Be in a place | new |
| `kill` | Kill an entity type | new |

`research.mode = unified | classic | modern`
* `unified` (default) — stages are used as written. Solving insight puzzles at the research table *also*
  awards theory points for the category, and theorycraft cards can grant aspect points, so either playstyle
  progresses the whole tree.
* `classic` — every `knowledge` requirement is converted into an `insight` puzzle whose aspects are drawn from
  the entry's aspect tags (cost = puzzle size).
* `modern` — every `insight` requirement is converted into a `knowledge` cost (one theory point per aspect in
  the puzzle, in the entry's category).
The conversion functions live in `core.research.ResearchModeConverter` and are unit-tested.

## D4 — Casting: wands and staves (TC4) vs casters and focus crafting (TC6)

**Decision — keep both instruments, unify the spell.**
* A **focus** always stores one compiled spell graph (TC6 focus crafting: medium → effects → modifiers, with
  splits). TC4's fixed foci (Fire, Frost, Shock, Excavation, Equal Trade, Portable Hole, Warding, Hellbat,
  Primal) are *archetype foci*: pre-built spell graphs craftable at the arcane workbench without the focus
  workbench, upgradable the TC4 way (potency, frugal, enlarge, treasure, extend → modifiers).
* **Wands/staves/sceptres** (rod + caps) store vis **per primal**; spells cost per-primal vis; cap discount and
  rod capacity apply. They recharge from nodes, vis relays, the recharge pedestal and (slowly) the chunk aura.
* **Casters** (gauntlets) hold no vis; they pay the spell's cost from the chunk aura, using the primal
  attunement weights of D2, and from vis batteries/crystals carried in the inventory.
* Both instruments accept the same foci and are balanced by the same `SpellCompiler` numbers.

`casting.wands = true`, `casting.casters = true` (either can be disabled).

## D5 — Golems: cores and upgrades (TC2–TC4) vs modular parts and seals (TC6)

**Decision.** TC6's model is the base (material, head, arms, legs, addons; behaviour from seals placed in the
world). TC4 cores map onto seals (`fill`→`seal_fill`, `gather`→`seal_collect`, `harvest`→`seal_harvest`,
`guard`→`seal_guard`, `liquid`→`seal_liquid` (new), `essentia`→`seal_essentia` (new), `lumber`→`seal_lumber`,
`use`→`seal_use`, `butcher`→`seal_butcher`, `fish`→`seal_fish` (new), `sort`→`seal_sort` (new),
`empty`→`seal_empty`). TC4 elemental upgrades (air, earth, fire, water, order, entropy) become **attunement
addons** that change speed, strength, fire immunity, swimming, precision and luck. TC2/TC4-only materials
(straw, tallow, flesh, stone) are added as materials. Automagy and Gadomancy modules become addons or seal
behaviours. A golem's full definition is a data component, so golems are copyable with a *golem pattern*.

## D6 — Taint and flux

TC4 taint spreads from tainted biomes and flux goo; TC6 taint grows from taint seeds spawned by flux rifts.
**Decision.** Flux is the single cause; TC6 rifts and seeds are the mechanism; TC4 tainted biomes are the
*result* (taint seeds convert the biome of the chunk after a configurable time). Every part is toggleable:
`taint.enabled`, `taint.spread`, `taint.biome_conversion`, `taint.max_spread_per_tick`, `flux.rifts`.
New: flux can be *harnessed* (flux condensers produce Vitium essentia; tainted golems; taint magic spells that
cost flux instead of vis — see SPELLS).

## D7 — Dimensions: Outer Lands (TC4), The Emptiness (Thaumic Augmentation), pocket planes (Thaumic Horizons)

**Decision.** One endgame dimension `thaumicunity:outer_lands` with three regions generated by a custom biome
source: the **Labyrinth** (TC4 maze instances with the eldritch bosses), the **Emptiness** (void islands, void
fortresses, eldritch spires) and the new **Shattered Spire** (final progression). Pocket planes stay separate
as instanced, player-owned dimensions created at runtime (one per *planar seed*). `dimensions.outer_lands`,
`dimensions.pocket_planes` toggles; with a dimension disabled its items still exist but cannot open portals.

## D8 — Essentia logistics

TC4/TC6 suction tubes are kept unchanged in spirit. Long-range transport from Gadomancy (aura pylons) and
Planar Artifice/TC2 essentia mirrors merge into **essentia mirrors** (paired, chunk-loading not required on the
receiving side). Thaumic Energistics becomes the AE2 integration (essentia as an AE2 key type). Thaumic
Augmentation's *impetus* is a separate energy network for devices that move entities or fire beams.

## D9 — Infusion stability

TC4 instability (asymmetry, missing essentia, item removal) + TC6 stabilisers (candles, skulls, pillars,
matrix upgrades). **Decision.** Stability is a single number computed by `core.infusion.StabilityModel` from:
symmetry of pedestals, stabiliser blocks (data-driven block tag + data map with value), pillar type, matrix
upgrades, recipe instability, and the chunk's flux. Instability events are a weighted, data-driven table
(`thaumicunity:instability_event` registry) so packs can add or soften them.

## D10 — Duplicated addon content

Thaumic Bases and Thaumic Additions: Reconstructed both provide adaminite / mithrillium / mithminite; TC2
and Planar Artifice both provide mirrors; Thaumic Tinkerer and Thaumic Augmentation both provide warding
items. Duplicates collapse into one item with the union of features. The surviving ID and the merged
features are noted in INVENTORY.md.

## D11 — Addon wand rods/caps and cross-mod materials

Rods/caps are data (`tu:wand_rod`, `tu:wand_cap` datapack registries). Entries tied to another mod (e.g.
livingwood rods for Botania, blood rods for Blood Magic) live in conditional data
(`neoforge:conditions` with `neoforge:mod_loaded`) so they only load when that mod is present.

## D12 — Baubles

TC4 used Baubles, TC6 used Baubles; modern NeoForge has Curios. **Decision.** Thaumic Unity ships its own small
*trinket* inventory (amulet, ring ×2, belt, charm — stored as a player attachment, opened from a button in the
inventory screen) so the mod works standalone. When Curios is installed, trinkets register as Curios items for
the matching slots and the built-in inventory is hidden (`compat.curios.replace_builtin = true`).

## D13 — Automatic aspects for items from other mods

TC4 derived aspects from crafting recipes; TC6 used explicit registration plus recipe inheritance. **Decision.**
`AspectResolver` assigns aspects in this order, first match wins per item:
1. Explicit data map value (`data/<ns>/data_maps/item/aspects.json`, datapack-overridable, `replace` honoured).
2. Tag rules (`thaumicunity/aspect_rule/*.json`: `c:ingots/*` → Metallum + tier-based extras, `c:gems` →
   Vitreus + Lucrum, `c:ores` → Terra + Metallum + material, `minecraft:logs` → Arbor, …).
3. Recipe inheritance: the cheapest recipe producing the item, summing ingredient aspects, dividing by output
   count, multiplying by a loss factor (default 0.75), capped per aspect (default 64).
4. Fallback by item class (BlockItem → Terra, tools → Instrumentum, armor → Tutamen, food → Victus + Fames…).
The resolver is deterministic, cycle-safe (recipe graph DFS with memoisation) and runs after tags and recipes
are loaded; its result is synced to clients in one packet.

## D14 — Text, names and art

All lore, research text, tooltips, textures, models and sounds are original. Latin aspect names are ordinary
Latin words. Item names that describe a mechanic (e.g. "Arcane Workbench") are kept because players know them;
see ASSETS_AND_LICENSING.md.

## D15 — No mixins, no access transformers

Every feature is built on NeoForge events, registries, data maps, attachments, capabilities and the transfer
API. This keeps the mod compatible with large packs; any future exception must be justified in this file.
