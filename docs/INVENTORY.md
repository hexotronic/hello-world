# Phase 1 — Source inventory and unified mapping

This document lists the content of every Thaumcraft version and of the major addons that Thaumic Unity
absorbs, and maps each element onto the unified architecture described in [ARCHITECTURE.md](ARCHITECTURE.md).
Nothing here is silently dropped: every row has a destination (a unified ID, a data-driven registry entry, or
an explicit merge with another row).

## How to read this file

* **Confidence.** The inventory was compiled from knowledge of the original mods; their wikis and jars were
  not reachable from the build environment. Rows marked **✔** are well established. Rows marked **?** are
  remembered with less certainty (exact names, numbers or the addon that introduced them) and must be checked
  against archived sources before content for them is finalised. See *Verification backlog* at the end.
* **Destination.** `tu:<path>` is the unified registry ID (`thaumicunity:` namespace). *Data* means the
  element is a JSON entry in a datapack registry rather than Java code. *Merge →* means the element is
  folded into another row, with the merge recorded in [DESIGN_DECISIONS.md](DESIGN_DECISIONS.md).
* **Phase.** The phase of [PLAN.md](PLAN.md) in which the element is implemented.
* **No assets or code are copied.** Thaumcraft is "All Rights Reserved" and most addons either are too, or
  have licences that do not allow relicensing inside this project. Thaumic Unity re-implements *mechanics and
  ideas* with original code, original text and original art. See [ASSETS_AND_LICENSING.md](ASSETS_AND_LICENSING.md).

---

## 1. Thaumcraft 1 (Minecraft 1.0 – 1.2.5) — Azanor

| Element | Notes | Destination | Phase | Conf. |
|---|---|---|---|---|
| Vis and Taint as two kinds of magic in the world | First version of "good" vs "bad" ambient magic | Merge → aura `vis` / `flux` per chunk | 3 | ✔ |
| Crucible tiers (basic / eyes / thaumium) | Item melting into vis/taint | Merge → `tu:crucible` + crucible upgrades (`tu:crucible_eye_rim`, `tu:thaumium_crucible`) | 3/4 | ? |
| Arcane furnace | Multiblock smelting | `tu:arcane_furnace` (multiblock, essentia-boosted smelting) | 5 | ? |
| Arcane infuser / dark infuser | Early infusion | Merge → infusion altar recipes (`tu:infusion`) | 3 | ? |
| Wand of Excavation, Wand of Fire, Wand of Lightning, Wand of Frost, Wand of Trade | Single-purpose wands | Spell components `tu:excavation`, `tu:fire`, `tu:shock`, `tu:frost`, `tu:equal_trade`; plus "legacy wand" casters | 6 | ? |
| Arcane Seal / portals | Rune seals that build portals and wards | `tu:arcane_seal` device + `tu:seal_glyph` runes (portal, ward, light, push) | 5 | ? |
| Magical forest, tainted lands | Biomes | `tu:magical_forest`, `tu:tainted_wastes` | 5 | ✔ |
| Greatwood, Silverwood, Shimmerleaf, Cinderpearl | Flora | `tu:greatwood_*`, `tu:silverwood_*`, `tu:shimmerleaf`, `tu:cinderpearl` | 4 | ✔ |
| Wisps, angry zombies | Mobs | `tu:wisp`, `tu:angry_zombie` | 5 | ✔ |
| Mysterious tomes / sealed research found as loot | Research by discovery | Research "curiosities" loot (`tu:forgotten_tome`, `tu:sealed_notes`) | 3 | ? |

## 2. Thaumcraft 2 (Minecraft 1.2.5 – 1.4.x)

| Element | Notes | Destination | Phase | Conf. |
|---|---|---|---|---|
| Aspects (first aspect system, item "tags") | Items carry aspects | Core aspect system (`tu:aspect` registry, item aspect data map) | 3 | ✔ |
| Research table, research notes, research duplicator | First research flow | Research table (`tu:research_table`) + `tu:research_notes`; duplication → *copy notes* recipe | 3 | ? |
| Thaumonomicon | Research book | `tu:thaumonomicon` (redesigned, animated, searchable) | 3/8 | ✔ |
| Golems: straw, wood, tallow, clay, flesh, stone, iron, thaumium | First golems | Golem materials (data) `tu:golem_material/*` | 5 | ✔ |
| Aura nodes with vis/flux | Node-based aura | Merge → `tu:aura_node` block entity in hybrid aura | 3 | ✔ |
| Goggles of Revealing, Thaumometer | Scanning tools | `tu:goggles_of_revealing`, `tu:thaumometer` | 3 | ✔ |
| Magic mirrors | Item teleport | `tu:arcane_mirror`, `tu:essentia_mirror` | 5 | ? |
| Eldritch altar / obelisk structures | Early eldritch content | Worldgen structure `tu:eldritch_obelisk` | 5 | ? |
| Arcane lamp, warding stones | Utility | `tu:arcane_lamp`, `tu:warding_stone` | 4 | ? |

## 3. Thaumcraft 3 (Minecraft 1.4.7 – 1.5.2)

| Element | Notes | Destination | Phase | Conf. |
|---|---|---|---|---|
| Hex-grid research minigame | Connect aspects on a hex grid | Kept as the **Insight puzzle** (classic research mode) | 3 | ✔ |
| Alchemical furnace, alembic, essentia jars | Essentia production/storage | `tu:alchemical_furnace`, `tu:alembic`, `tu:warded_jar` | 3/4 | ✔ |
| Infusion altar with runic matrix and pedestals | Infusion | `tu:runic_matrix`, `tu:arcane_pedestal`, `tu:infusion` recipes | 3 | ✔ |
| Wands with tiers (apprentice/adept/magus) and early foci | Fire, Frost, Shock, Trade, Excavation, Portable Hole | Wand tiers → wand rods/caps; foci → spell components | 4/6 | ? |
| Legacy aspects (*Saxum*, *Lignum*, *Purus*, *Granum*, *Tempus*) | Early aspect names | Revived as aspects `tu:saxum`, `tu:lignum`, `tu:purus`, `tu:granum`, `tu:tempus` | 3 | ? |
| Arcane bore | Mining device | `tu:arcane_bore` | 4 | ✔ |

## 4. Thaumcraft 4 (Minecraft 1.6.4 / 1.7.10) — the canonical classic version

### 4.1 Aspects (48) ✔

Primal: Aer, Terra, Ignis, Aqua, Ordo, Perditio.
Compound (component pairs as in TC4): Vacuos (Aer+Perditio), Lux (Aer+Ignis), Tempestas (Aer+Aqua),
Motus (Aer+Ordo), Gelum (Ignis+Perditio), Vitreus (Terra+Ordo), Victus (Aqua+Terra), Venenum (Aqua+Perditio),
Potentia (Ordo+Ignis), Permutatio (Motus+Aqua), Metallum (Terra+Vitreus), Mortuus (Victus+Perditio),
Volatus (Aer+Motus), Tenebrae (Vacuos+Lux), Spiritus (Victus+Mortuus), Sano (Victus+Ordo), Iter (Motus+Terra),
Alienis (Vacuos+Tenebrae), Praecantatio (Vacuos+Potentia), Auram (Praecantatio+Aer),
Vitium (Praecantatio+Perditio), Limus (Victus+Aqua), Herba (Victus+Terra), Arbor (Aer+Herba),
Bestia (Motus+Victus), Corpus (Mortuus+Bestia), Exanimis (Motus+Mortuus), Cognitio (Ignis+Spiritus),
Sensus (Aer+Spiritus), Humanus (Bestia+Cognitio), Messis (Herba+Humanus), Perfodio (Humanus+Terra),
Instrumentum (Humanus+Ordo), Meto (Messis+Instrumentum), Telum (Instrumentum+Ignis),
Tutamen (Instrumentum+Terra), Fames (Victus+Vacuos), Lucrum (Humanus+Fames), Fabrico (Humanus+Instrumentum),
Pannus (Instrumentum+Bestia), Machina (Motus+Instrumentum), Vinculum (Motus+Perditio).

Destination: one JSON file each in `data/thaumicunity/thaumicunity/aspect/` (phase 3, **implemented**).

### 4.2 Research and progression ✔

| Element | Destination | Phase |
|---|---|---|
| Tabs: Basic Information, Thaumaturgy, Alchemy, Artifice, Golemancy, Eldritch | Research categories (data) `tu:research_category/*` (+ new categories) | 3 |
| Research notes + hex minigame, aspect pool from scanning | Insight puzzle (classic mode); scanning feeds the aspect pool | 3 |
| Research points per aspect, Thaumometer scanning | Scan registry per player; scanning grants aspect points | 3 |
| Secondary / hidden research, research by warp | Research entry flags `hidden`, `warp_gated` | 3 |
| Knowledge fragments, research duplication | `tu:knowledge_fragment`; *copy notes* arcane recipe | 3 |

### 4.3 Vis, wands, nodes ✔

| Element | Destination | Phase |
|---|---|---|
| Aura nodes: types (normal, unstable, dark, hungry, pure, tainted), modifiers (bright, pale, fading) | `tu:aura_node` block entity with `NodeType`/`NodeModifier` | 3 |
| Node in a jar, node stabilizer, advanced node stabilizer, node transducer | `tu:node_jar`, `tu:node_stabilizer`, `tu:node_transducer` | 5 |
| Energized nodes, vis relays, vis charge relay (amulet charging) | `tu:vis_relay` network, `tu:vis_charge_relay` | 5 |
| Wand rods: wood, greatwood, reed, blaze, obsidian, ice, quartz, bone, silverwood, primal (+ staves) | Wand rod data `tu:wand_rod/*`; staff variants are rod `form=staff` | 4 |
| Wand caps: iron, copper, gold, silver, thaumium, void | Wand cap data `tu:wand_cap/*` | 4 |
| Sceptre, staff of the primal | `form=sceptre`; `tu:staff_of_the_primal` | 4 |
| Wand recharge pedestal, amulets/girdles of vis storage | `tu:wand_recharge_pedestal`, `tu:vis_amulet`, `tu:vis_girdle` | 4 |

### 4.4 Foci and focus upgrades ✔

| Focus | Upgrades | Destination |
|---|---|---|
| Fire | Fireball, Alchemist's Fire (fire beam) | Effect `tu:fire`; mediums `tu:projectile`, `tu:beam` |
| Frost | Scatter shot, Ice boulder | Effect `tu:frost`; modifier `tu:scatter`; effect `tu:ice_boulder` |
| Shock | Chain lightning, Earth shock | Effect `tu:shock`; modifier `tu:chain`; medium `tu:ground_wave` |
| Equal Trade | Architect, Silk touch | Effect `tu:equal_trade`; modifier `tu:architect`, `tu:silk_touch` |
| Excavation | Dowsing, Treasure (fortune), Silk touch, Enlarge | Effect `tu:break`; modifiers `tu:dowsing`, `tu:treasure`, `tu:enlarge` |
| Portable Hole | Extend, Architect | Effect `tu:portable_hole` |
| Warding | — | Effect `tu:ward` |
| Hellbat | Devil bats, Bat bombs, Vampire bats | Medium `tu:spellbat`; modifiers `tu:devil_bats`, `tu:bat_bomb`, `tu:vampiric` |
| Primal | — | Effect `tu:primal_burst` |
| Generic upgrades: Potency, Frugal, Enlarge, Treasure, Extend | Modifiers `tu:potency`, `tu:frugal`, `tu:enlarge`, `tu:treasure`, `tu:extend` |

All become data entries in `tu:spell_component` (phase 6); the base set is implemented in phase 3.

### 4.5 Crafting devices ✔

Arcane workbench; crucible; alchemical furnace; alembic; essentia tubes (normal, valve, filtered, restricted,
one-way, buffer); warded jar, void jar, jar labels; thaumatorium (+ mnemonic matrix); infusion altar
(runic matrix, arcane pedestals, stabilizers); deconstruction table; research table; arcane bore (+ base);
infernal furnace; arcane bellows; arcane spa; arcane lamps (light, growth, fertility); arcane ear; arcane
pressure plate; arcane door; warded glass; paving stones of warding and travel; hungry chest; travel trunk;
arcane levitator; brain in a jar; mirrors.
Destination: one block (and block entity where needed) each under the same name (`tu:arcane_workbench`, …).
Crucible, arcane workbench, infusion, research table: phase 3. The rest: phase 4.

### 4.6 Items ✔

Thaumonomicon; thaumometer; goggles of revealing; scribing tools; research notes; knowledge fragment;
salis mundus; nitor; alumentum; quicksilver; amber; balanced shards (6 primal); thaumium (ingot, nugget,
block); void metal (ingot, nugget, block); enchanted fabric; tallow; primordial pearl; eldritch eye; crimson
rites; runed tablet; ethereal essence (wisp essence); phials; bottle of taint; bath salts; sanity soap;
mirrored glass; zombie brain; mana bean; triple meat treat; arcane key; golem bell; focus pouch; charms
and baubles (runic ring/amulet/girdle incl. charged, revitalizing, kinetic; hover girdle; boots of the
traveller; amulet of vis storage; band of cleansing? → see verification).
Equipment: thaumium tools/armor; elemental tools (Axe of the Stream, Pickaxe of the Core, Shovel of the
Earthmover, Hoe of Growth, Sword of the Zephyr); thaumium fortress armor (+ masks: grinning devil, angry
ghost, sipping fiend); void tools/armor; void robe armor; crimson cult armor (plate, leader, robe); crimson
blade; primal crusher; hover harness; bone bow? and primal arrows?.
Destination: same names under `tu:`; phase 4.

### 4.7 Golemancy ✔

Materials: straw, wood, tallow, clay, flesh, stone, iron, thaumium. Cores: fill, empty, gather, harvest,
guard, liquid, essentia, lumber, use, butcher, fish, sort. Upgrades: air, earth, fire, water, order, entropy.
Golem bell, golem animation. Destination: golem part registries (data) — material/core/upgrade map to the
TC6-style head/arm/leg/addon/seal model (see DESIGN_DECISIONS D5); phase 5.

### 4.8 World, mobs, eldritch ✔

Ores: cinnabar, amber, infused stone (6 primal). Flora: greatwood, silverwood, shimmerleaf, cinderpearl,
vishroom, ethereal bloom, mana pods. Biomes: magical forest, tainted land, eerie biome. Structures: obsidian
totems, eldritch obelisks/altars, crimson cult camps, barrows/mounds, outer lands mazes.
Mobs: wisp, firebat, pech (forager, stalker, thaumaturge), angry/brainy/giant brainy zombie, thaumic slime,
taint swarm, taint crawler, taintacle (+ giant), tainted animals (villager, chicken, cow, pig, sheep,
creeper), eldritch guardian, eldritch warden, eldritch golem, eldritch crab, inhabited zombie, mind spider,
crimson knight, crimson cleric, crimson praetor, cultist portal, primal orb / eldritch orb projectiles.
Dimension: the Outer Lands. Bosses: eldritch warden, eldritch golem, crimson praetor, taintacle giant.
Mechanics: warp (permanent, sticky, temporary) and warp events; taint spread with flux goo/gas; pech trading;
fluids (pure fluid, liquid death); candles (16 colours).
Destination: same names; phase 4 (world/flora/basic mobs) and phase 5 (eldritch, bosses, dimension).

## 5. Thaumcraft 5 (Minecraft 1.8) ✔

Port of TC4 to 1.8 with reworked visuals, changed aspects for some items, the *Thaumostatic harness*
precursor, the aura becoming chunk-based in late betas, and the *crimson cult* rework. All of its content
already exists in TC4 or TC6 rows; differences are recorded as merge notes in DESIGN_DECISIONS (D1, D2).

## 6. Thaumcraft 6 (Minecraft 1.12.2)

### 6.1 Aspects ✔

TC6 keeps the 6 primals and has 31 compounds. It drops 15 TC4 aspects (Tempestas, Venenum, Sano, Iter,
Limus, Arbor, Corpus, Messis, Perfodio, Meto, Telum, Tutamen, Fames, Lucrum, Pannus) and adds four:
**Alkimia** (alchemy), **Aversio** (aversion), **Praemunio** (protection), **Desiderium** (desire). Several
shared aspects use different component pairs (e.g. Vitreus = Terra+Aer, Metallum = Terra+Ordo,
Permutatio = Perditio+Ordo, Mortuus = Aqua+Perditio).
Destination: all four new aspects exist; the dropped TC4 aspects are kept; TC6 component pairs ship as the
optional built-in datapack `thaumicunity:modern_combinations` (DESIGN_DECISIONS D1).

### 6.2 Research ✔

Knowledge (observations / theories per category), theorycrafting at the research table with cards and
"inspiration", research stages with requirements (scan, craft, obtain, knowledge), curiosities (arcane tome,
druidic notes, celestial notes, eldritch notes…), celestial notes from the sky, the sanity checker.
Categories: Fundamentals, Auromancy, Alchemy, Artifice, Infusion, Golemancy, Eldritch.
Destination: research stages and knowledge points are the backbone of the unified research model; the card
game is the **Theorycraft** mode of the research table (DESIGN_DECISIONS D3). Phase 3.

### 6.3 Aura and flux ✔

Per-chunk vis and flux, flux rifts, flux phage, taint seeds, taint fibres/logs/crust/soil/geysers, flux goo.
Vis devices: vis generator, vis battery, flux condenser + lattice, stabilizer, crystal clusters.
Destination: hybrid aura (D2); devices under same names; phase 3 (aura core) and 5 (devices).

### 6.4 Casters and focus crafting ✔

Basic caster's gauntlet, thaumium caster, void caster; focus I/II/III; focus workbench; focus pouch.
Focus parts — mediums: root, touch, bolt, projectile, cloud, mine, plan, spellbat; effects: air, earth, fire,
frost, break, exchange, curse, flux, heal, rift; modifiers: scatter, split target, split trajectory.
Destination: casters `tu:caster_*`; focus blanks `tu:focus_blank_*`; every part is a `tu:spell_component`
entry; the focus workbench is the spell-crafting GUI. Phase 3 (core set) and 6 (expansion).

### 6.5 Crafting and devices ✔

Arcane workbench (vis + crystals), crucible, infusion (stability, pillars: arcane/ancient/eldritch, matrix
speed and cost upgrades, symmetry, candles/skulls as stabilizers), thaumatorium, essentia smelters
(basic/thaumium/void, vents, aux), alembic, centrifuge, essentia tubes, jars, essentia input/output hatches,
pattern crafter, golem builder, recharge pedestal, levitator, arcane ear, lamp of growth/fertility, potion
sprayer, dioptra, redstone relay, infernal furnace, hungry chest, arcane spa, banners, arcane/ancient/eldritch
infusion pillars.
Destination: same names under `tu:`. Phase 3/4.

### 6.6 Golemancy ✔

Materials: wood, iron, clay, brass, thaumium, void. Heads: basic, smart, scout. Arms: basic, fine, claws,
breakers, darts. Legs: walker, roller, climber, flyer. Addons: armored, hauler. Seals: collect, empty, fill,
provide, stock, guard, breaker, harvest, butcher, lumber, use, store, and their advanced variants.
Destination: golem part registries (data) + seals; phase 5.

### 6.7 Items ✔

Brass ingot/plate, thaumium, void metal, clockwork mechanism, vis resonator, morphic resonator, mirrored glass,
plates (brass, iron, thaumium, void), crystal essence, phials, labels, curiosity band, cloud ring, charm of
undying, verdant charms (life, sustain), ring of runic shielding, thaumostatic harness, boots of the
traveller, primordial pearl, causality collapser, bath salts, sanity soap, grapple gun, arcane crossbow
turrets (basic/advanced), pech wand, crimson cult weapons and armor, elemental tools, primal crusher, void tools
and armor, fortress armor, goggles, thaumometer, salis mundus, celestial notes.
Destination: same names; phase 4.

---

## 7. Addons (Thaumcraft 4 era)

### 7.1 Thaumic Tinkerer (Vazkii, later maintained by others)

| Element | Destination | Phase | Conf. |
|---|---|---|---|
| Foci: Dislocation, Telekinesis, Smelting, Ender Rift, Flight, Deflection, Healing | Effects `tu:dislocate`, `tu:telekinesis`, `tu:smelt`, `tu:ender_rift`, `tu:flight`, `tu:deflect`, `tu:heal` | 6 | ✔ |
| KAMI: Ichor, ichorium (tools/armor, awakened variants), ichor cloth, dimension shards, block talisman, placement mirror, warp gate, bedrock portal, focus of recall, focus of shadowbeam, focus of XP drain | `tu:ichor`, `tu:ichorium_*`; devices; effects `tu:recall`, `tu:shadow_beam`, `tu:xp_drain` | 5/6 | ? |
| Transvector interface and dislocator | `tu:transvector_interface`, `tu:transvector_dislocator` | 5 | ✔ |
| Animation tablet | `tu:animation_tablet` | 5 | ✔ |
| Essentia funnel, repairer, osmotic enchanter, aspect analyzer | `tu:essentia_funnel`, `tu:essentia_repairer`, `tu:osmotic_enchanter`, `tu:aspect_analyzer` | 5 | ✔ |
| Gaseous light/shadow, spellbinding cloth, mob magnet, corporeal attractor, soul mould, infused inkwell | Same names | 5 | ? |
| Infused seeds / infused farmland (aspect crops) | `tu:infused_seeds/*` (one per primal + data-driven aspect crops) | 5 | ? |
| Talismans (XP, cleansing, remedium) | `tu:talisman_*` | 5 | ? |

### 7.2 Thaumic Exploration

Thinking cap, everfull urn, bound chest/jar, trash jar, replicator, crucible of souls, necromantic altar,
boots of the meteor/comet, extra wand cores and caps (sojourner, mechanist), talisman of remembrance,
custom enchantments (disarming, night vision, ?).
Destination: same names; caps/cores as wand data. Phase 5. Conf. ? for wand core list and enchantments.

### 7.3 Forbidden Magic

| Element | Destination | Conf. |
|---|---|---|
| Sin aspects: Infernus (Ignis+Praecantatio), Superbia (Volatus+Vacuos), Gula (Fames+Vacuos), Invidia (Sensus+Fames), Luxuria (Corpus+Fames), Ira (Telum+Ignis), Desidia (Vinculum+Spiritus) | Aspects `tu:infernus` … `tu:desidia` (implemented) | ✔ names / ? pairs |
| Sin shards, nether shards | `tu:sin_shard/*` | ✔ |
| Wand cores: infernal, blood, profane/tainted, livingwood/dreamwood (Botania compat) | Wand rod data (compat-gated where needed) | ? |
| Morphic tools, distortion pickaxe, skull axe, blood rapier, fork of diabolism, ring of vampirism | Same names | ? |
| Wrath cage (aspect-fuelled spawner), arcane cake, black flower/ink | Same names | ? |

### 7.4 Thaumic Horizons

Vat of Life (self-infusions: e.g. vis regeneration, shock skin, cleansing, night vision; mob creation),
pocket planes (planar conduit, vortex stabilizer), synthetic node, light solvent, goggle lenses (fire, water,
air, earth, order, entropy), foci (liquefaction, animation, containment, illumination), soul beacon.
Destination: `tu:vat_of_life` + `tu:self_infusion/*` (data), pocket plane dimension type `tu:pocket_plane`,
`tu:lens/*`, spell effects. Phase 5/6. Conf. ? for the self-infusion list and focus names.

### 7.5 Gadomancy

Infusion claw (automated infusion), arcane packager, essentia compressor, aura pylon (long-distance essentia),
node manipulator, remote jar, ethereal familiars, golem cores (bodyguard, break, …), ancient golem,
sanity checker integration. Destination: same names; golem cores → seals/behaviours. Phase 5. Conf. ?.

### 7.6 Witching Gadgets

Cloaks (spectral, storage, raven, wolf), kamas/belts, primordial gauntlet + primordial armor and tools,
gem cutting and infused gems, terraformer (+ biome foci), spinning wheel, loom, ethereal wall, witch's robes,
thaumic lamp, bag of tricks. Destination: curios-slot baubles (Curios optional, vanilla slots otherwise),
`tu:terraformer`, `tu:loom`, `tu:spinning_wheel`, `tu:infused_gem/*`. Phase 5. Conf. ? for minor items.

### 7.7 Automagy

Golem workbench, golem inhibitor, golem upgrade modules, hungry maw, finical maw, greedy chest, thirsty tank
(XP), redcrystal (signal storage), vis reader, tally box, arcane totems (mob attract/repel), inventarium,
mirror of shared visions, glyphs, vishroom spores, bottled dreams, nether heart. Destination: same names;
golem modules → golem addons/seal behaviours. Phase 5. Conf. ? for several names.

### 7.8 Thaumic Bases

Thauminite (tools/armor), mithrillium, adaminite, mithminite (tools/armor), node manipulation foci
(efficiency, destruction, speed, hunger, order, taint), node linker, brazier, campfire, aurelia, tobacco and
smoking pipes (several tobacco types), void flint and steel, thaumic revolver (+ upgrades), spike traps,
thaumanaic/eldritch bricks, crystal blocks, overchanter, pyrofluid, enlightened stone, dust blocks.
Destination: same names; revolver upgrades as data. Phase 5. Conf. ? for some names.

### 7.9 Thaumic Energistics (Applied Energistics 2 addon; TC4 and TC6 versions)

Essentia storage components (1k–64k), essentia cells, essentia terminal (+ wireless), essentia import/export/
storage buses, essentia level emitter, arcane crafting terminal, infusion provider, essentia provider,
essentia vibration chamber, gaseous essentia, knowledge inscriber/core, coalescence/diffusion cores.
Destination: `compat.ae2` integration, loaded only when AE2 is present. Phase 7.

### 7.10 Thaumic Warden

Warden armor (wardenic chain), wardenic blade with upgrade infusions (fire, air, water, earth, order,
warp…), warden amulet, exubitura plant, wardenic crystal/quartz, wardenic binder.
Destination: `tu:wardenic_*` set with upgrades as data. Phase 5. Conf. ?.

### 7.11 Electro-Magic Tools (IndustrialCraft 2 addon)

Thaumium drills/chainsaws, electric/nano/quantum goggles of revealing, electric boots of the traveller,
feather/thaumium wings, mjolnir (+ unstable), foci (christmas, shield, energy ball, explosion, charging),
portable node, compressed solars. Destination: energy variants via `compat.energy` (NeoForge energy
capability, so Mekanism/IC2-likes work); foci → spell components `tu:shield`, `tu:energy_ball`,
`tu:explosion`, `tu:recharge`, `tu:festive`. Phase 6/7. Conf. ?.

### 7.12 Other TC4-era addons (QoL)

ThaumCraft NEI Plugin (aspect lookup, research notes), Thaumcraft Inventory Scanning (scan items in the
inventory), Magic Bees (adds **Tempus** = Vacuos+Ordo), GregTech aspects (**Electrum** Potentia+Machina,
**Magneto** Metallum+Iter, **Radio** Lux+Potentia, **Nebrisum** Perfodio+Lucrum, **Strontio**
Cognitio+Perditio), Avaritia (**Terminus** Lucrum+Alienis), Blood Magic compat (**Sanguis**).
Destination: QoL is built in (inventory scanning, JEI/EMI/REI aspect pages); all listed aspects implemented.

## 8. Addons (Thaumcraft 6 era)

### 8.1 Thaumic Augmentation

Impetus system (relay, drainer, gate, diode, matrix, mirror, generator, impulse cannon with railgun/beam/burst
augments), thaumostatic harness augments (gyroscope, girdle, elytra), void boots (+ augments), augmentable
casters (strength, efficiency, elemental augments), focus effects (light, ward, water, void shield), warded
blocks (chest, door, trapdoor, button, pressure plate), glass of seeing, fortified glass, celestial observer,
rift monitor, rift jar, stability field generator, primal cutter, thaumostatic robes, starfield glass,
**The Emptiness** dimension with void fortresses and eldritch spires, new eldritch/cultist mobs.
Destination: impetus → `tu:impetus` energy network (phase 5); dimension merged into the expanded Outer
Lands (`tu:outer_lands` with an *Emptiness* region, DESIGN_DECISIONS D7); augments → caster upgrades (data).
Conf. ✔ for systems, ? for individual augment names.

### 8.2 Thaumic Additions: Reconstructed

Ports of TC4 addon content to TC6 (adaminite, mithrillium, mithminite), shadow beam staff, crystal crusher,
aura charger, void anvil, essentia pistons, entity cell, mob summoning, ender-transport seals, fragrant
pendant, chester, crystal bag, vis crops. Destination: merged with the TC4 originals (no duplicates).
Phase 5. Conf. ?.

### 8.3 Thaumic Wonders

Eldritch clusters, primordial accumulator, catalyzation chamber, void beacon, cleansing charm, disjunction
cloth, flying carpet, inspiration engine, portable portal, bone bow, flux distiller, meaty orb, timewinder,
alchemist's stone, panacea, primal destroyer. Destination: same names. Phase 5. Conf. ?.

### 8.4 Planar Artifice

Alkimium metal, planar mirrors and mirror gates (teleport), flux scrubber, alchemical constructs.
Destination: same names, mirrors merged with TC2/TC4 mirrors. Phase 5. Conf. ?.

### 8.5 TC6 QoL addons

Thaumic JEI (aspect/recipe pages), Thaumic Speedup (performance), research cheat/tweak mods.
Destination: built-in JEI/EMI/REI integration, performance budget for aura ticking (ARCHITECTURE §6).

---

## 9. Counts (union of all sources, after removing duplicates)

| Category | Count | Notes |
|---|---:|---|
| Aspects | 67 | 48 TC4 + 4 TC6 + 15 addon aspects (7 sins, Tempus, Sanguis, 5 GregTech, Terminus) |
| Research entries | ≈ 520 | TC4 ≈ 210, TC6 ≈ 170, addons ≈ 300, minus ≈ 160 duplicates |
| Items | ≈ 900 | incl. tools/armor pieces, baubles, components |
| Blocks | ≈ 450 | incl. decorative variants |
| Devices (block entities) | ≈ 180 | |
| Recipes (arcane + crucible + infusion) | ≈ 1 100 | |
| Spell components (foci, focus parts, upgrades) | ≈ 72 | 9 TC4 foci + 14 upgrades + 21 TC6 parts + ≈ 28 addon foci/effects |
| Golem parts and behaviours | ≈ 95 | materials, cores/seals, upgrades, heads/arms/legs/addons |
| Mobs | ≈ 60 | |
| Bosses | 6 | eldritch warden, eldritch golem, crimson praetor, taintacle giant, Emptiness bosses |
| Dimensions | 3 | Outer Lands, The Emptiness, pocket planes |
| Biomes | ≈ 8 | |

These counts are the baseline for the expansion targets in [PLAN.md](PLAN.md).

## 10. Verification backlog

Before final content for a **?** row is written, check it against archived sources (CurseForge file pages,
the FTB/Thaumcraft wikis, archived changelogs). Do not copy text or art from those sources — record only
names and mechanics. Open items:

1. TC1–TC3 device and aspect names (§1–3).
2. Thaumic Tinkerer KAMI item list and focus names (§7.1).
3. Forbidden Magic sin-aspect component pairs and wand cores (§7.3).
4. Thaumic Horizons self-infusions, lenses, foci (§7.4).
5. Gadomancy, Witching Gadgets, Automagy, Thaumic Bases minor items (§7.5–7.8).
6. Thaumic Warden, Electro-Magic Tools item lists (§7.10–7.11).
7. Thaumic Augmentation augment names; Thaumic Additions, Thaumic Wonders, Planar Artifice lists (§8).
