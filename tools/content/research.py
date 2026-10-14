"""Research categories and the phase 3 research tree (datapack registries thaumicunity:research_category and
thaumicunity:research). Stage text is written here in English; gen_content.py turns it into translation keys.

Requirement shorthand (see `requirement()` in gen_content.py):
  ("scan", "item:minecraft:iron_ingot")      ("craft", "thaumicunity:thaumometer")
  ("obtain", "thaumicunity:crucible", 1)     ("knowledge", "observation", "alchemy", 2)
  ("insight", ["metallum", "vitreus"])       ("research", "fundamentals/aspects")
"""

CATEGORIES = {
    # id: (English name, icon item, order, required research or None, colour)
    "fundamentals": ("Fundamentals", "thaumicunity:thaumonomicon", 0, None, 0xC9A44C),
    "alchemy": ("Alchemy", "thaumicunity:crucible", 10, "fundamentals/aspects", 0x5FB06A),
    "artifice": ("Artifice", "thaumicunity:arcane_workbench", 20, "fundamentals/thaumometer", 0xB0703C),
    "infusion": ("Infusion", "thaumicunity:runic_matrix", 30, "alchemy/metallurgy", 0x9B59D0),
    "auromancy": ("Auromancy", "thaumicunity:basic_caster", 40, "fundamentals/aura", 0x4FA3D9),
}

# Each entry: id -> dict(title, x, y, icon, parents, aspects, stages, flags)
# Each stage: dict(text, requirements, recipes)
ENTRIES = {
    # ------------------------------------------------------------------ fundamentals
    "fundamentals/thaumonomicon": dict(
        title="The Thaumonomicon", x=0, y=0, icon="thaumicunity:thaumonomicon", parents=[], special=True,
        auto_unlock=True, aspects=["praecantatio", "cognitio"],
        stages=[dict(text=(
            "Every scholar of the arcane keeps a book, and this one keeps itself. Pages fill as you learn; diagrams "
            "redraw themselves when you understand them better.\n\nThe first discovery every thaumaturge makes is "
            "Salis Mundus, a salt that remembers the shape of things. Grind redstone, glowstone, bone meal and an "
            "amethyst shard together and sprinkle the result on a bookshelf, and a copy of this book appears. "
            "Sprinkled on other things, it does other things. Read on."),
            recipes=["salis_mundus", "thaumonomicon"])]),
    "fundamentals/arcane_workbench": dict(
        title="The Arcane Workbench", x=2, y=0, icon="thaumicunity:arcane_workbench",
        parents=["fundamentals/thaumonomicon"], auto_unlock=True, aspects=["fabrico", "praecantatio"],
        stages=[dict(text=(
            "Sprinkle Salis Mundus on an ordinary crafting table and it becomes an Arcane Workbench. It still crafts "
            "everything a crafting table can, but it also draws vis, the raw magic of the world, out of the air "
            "around it and spends it on recipes that need more than skill.\n\nThe six primal costs of a recipe are "
            "shown around the grid. The vis comes from the aura of the chunk the workbench stands in; a place "
            "attuned to a primal pays less of that primal."))]),
    "fundamentals/thaumometer": dict(
        title="The Thaumometer", x=4, y=0, icon="thaumicunity:thaumometer",
        parents=["fundamentals/arcane_workbench"], aspects=["sensus", "visio"],
        stages=[
            dict(text=(
                "Magic hides in plain sight. A lens of amethyst in a gold frame, assembled at the arcane workbench, "
                "lets you see the aspects that things are made of.\n\nCraft a Thaumometer to continue."),
                requirements=[("craft", "thaumicunity:thaumometer")], recipes=["thaumometer"]),
            dict(text=(
                "Point the thaumometer at a block or a creature and use it to scan it; with nothing in sight, it "
                "scans the item in your other hand. Each new scan teaches you the aspects of what you looked at "
                "and gives you points of those aspects for your research. While you hold it, it also shows the "
                "aura of the place you stand in."))]),
    "fundamentals/aspects": dict(
        title="Aspects", x=4, y=2, icon="thaumicunity:salis_mundus",
        parents=["fundamentals/thaumometer"], aspects=["aer", "terra", "ignis", "aqua", "ordo", "perditio"],
        stages=[
            dict(text=(
                "Everything is made of aspects, and every aspect is made of two others, down to the six primals: "
                "Aer, Terra, Ignis, Aqua, Ordo and Perditio.\n\nScan some grass, a cow and an iron ingot."),
                requirements=[("scan", "block:minecraft:grass_block"), ("scan", "entity:minecraft:cow"),
                              ("scan", "item:minecraft:iron_ingot")]),
            dict(text=(
                "Aspects can be combined: Aer and Ignis make Lux, Lux and Vacuos make Tenebrae, and so on, two "
                "hundred times over. Hold Shift over an item to see its aspects once you have scanned it.\n\n"
                "The aspect points you gain from scanning are the ink of research: you spend them solving insight "
                "puzzles at the research table."))]),
    "fundamentals/research_table": dict(
        title="The Research Table", x=6, y=2, icon="thaumicunity:research_table",
        parents=["fundamentals/aspects"], aspects=["cognitio", "scientia"],
        stages=[
            dict(text=(
                "Salis Mundus turns a lectern into a research table. Put scribing tools in its upper slot and paper "
                "in the lower one.\n\nObtain a research table."),
                requirements=[("obtain", "thaumicunity:research_table", 1)], recipes=["scribing_tools"]),
            dict(text=(
                "With blank paper on the table, the table lists every insight puzzle your current research asks "
                "for. Choosing one writes a fresh puzzle on the paper and uses a little ink.\n\nThe table can also "
                "turn two observations of a field into one theory, at the cost of a sheet of paper."))]),
    "fundamentals/insight": dict(
        title="Insight", x=8, y=2, icon="thaumicunity:research_notes",
        parents=["fundamentals/research_table"], aspects=["cognitio", "lux"],
        stages=[
            dict(text=(
                "An insight puzzle is a hexagonal board with a few fixed aspects at its corners. Fill the cells "
                "between them with aspects from your points so that every fixed aspect is connected to the others "
                "through a chain of neighbours, where each neighbour is a component of the next (Aer sits next to "
                "Lux, Lux next to Ignis).\n\nSolve the puzzle that links Aer and Ignis."),
                requirements=[("insight", ["aer", "ignis"])]),
            dict(text=(
                "Solved notes are proof of understanding. In unified research every solved puzzle also earns a "
                "theory in the field it belongs to."))]),
    "fundamentals/theory": dict(
        title="Theorycraft", x=8, y=0, icon="minecraft:writable_book",
        parents=["fundamentals/insight"], aspects=["scientia", "memoria"],
        stages=[
            dict(text=(
                "Scanning things grants observations in the fields their aspects belong to. Two observations can be "
                "worked into a theory at the research table, and theories pay for the deeper secrets of each field."
                "\n\nDevelop one theory of Fundamentals."),
                requirements=[("knowledge", "theory", "fundamentals", 1)]),
            dict(text=(
                "Keep scanning. Every field (Alchemy, Artifice, Infusion, Auromancy) has its own observations, "
                "and the book tells you which ones each discovery needs."))]),
    "fundamentals/aura": dict(
        title="Vis and Flux", x=6, y=4, icon="thaumicunity:aer_vis_crystal",
        parents=["fundamentals/aspects"], aspects=["auram", "vitium"],
        stages=[
            dict(text=(
                "Every chunk of the world holds a reservoir of vis. It slowly refills to a level set by the land: "
                "forests and mushroom fields are rich, deserts poor, and the Nether and the End richer still. Each "
                "place also favours some primals over others.\n\nScan an amethyst shard, the most common crystal of "
                "raw magic."),
                requirements=[("scan", "item:minecraft:amethyst_shard")]),
            dict(text=(
                "Magic is never clean. Spending vis leaves flux behind, and flux makes the aura heal more slowly. "
                "Where too much flux gathers it stops dissipating, and strange things begin to happen. Spread your "
                "work out, and let the land rest."))]),
    # ------------------------------------------------------------------ alchemy
    "alchemy/crucible": dict(
        title="The Crucible", x=0, y=0, icon="thaumicunity:crucible",
        parents=["fundamentals/aspects"], special=True, aspects=["alkimia", "aqua", "ignis"],
        stages=[
            dict(text=(
                "Salis Mundus turns a cauldron into a crucible. Fill it with water and keep a fire, lava, magma or a "
                "campfire under it; once it boils, anything thrown in dissolves into its essentia.\n\nMake an "
                "observation in Alchemy."),
                requirements=[("knowledge", "observation", "alchemy", 1)]),
            dict(text=(
                "Throw in a catalyst while the right essentia is dissolved and it becomes something new, using up "
                "exactly the essentia the recipe needs. A crucible holds only so much; what overflows becomes flux. "
                "Empty it by using it with an empty hand while sneaking."))]),
    "alchemy/metallurgy": dict(
        title="Thaumic Metallurgy", x=0, y=2, icon="thaumicunity:thaumium_ingot",
        parents=["alchemy/crucible"], aspects=["metallum", "praecantatio"],
        stages=[
            dict(text=(
                "Metal remembers what it was steeped in. Iron dropped into a crucible heavy with Praecantatio comes "
                "out as thaumium, which holds magic the way copper holds heat.\n\nSolve the puzzle linking Metallum "
                "and Vitreus."),
                requirements=[("insight", ["metallum", "vitreus"])]),
            dict(text=(
                "Thaumium is the backbone of every arcane device. Brass, copper hardened with Metallum and Ordo, "
                "makes fine fittings for instruments."),
                recipes=["alchemy/thaumium_ingot", "alchemy/brass_ingot", "thaumium_block", "thaumium_ingot_from_block",
                         "thaumium_nugget", "thaumium_ingot_from_nuggets", "brass_block", "brass_ingot_from_block",
                         "brass_nugget", "brass_ingot_from_nuggets"])]),
    "alchemy/alchemical_fuels": dict(
        title="Alchemical Fuels", x=2, y=2, icon="thaumicunity:alumentum",
        parents=["alchemy/crucible"], aspects=["potentia", "lux"],
        stages=[
            dict(text=(
                "Some substances burn too eagerly for a furnace. Scan glowstone dust and gunpowder to see why."),
                requirements=[("scan", "item:minecraft:glowstone_dust"), ("scan", "item:minecraft:gunpowder")]),
            dict(text=(
                "Nitor is a captured flame that never goes out, wanted wherever a device needs light. Alumentum "
                "burns hot enough to drive an alchemical furnace for a long time. Magic tallow, rendered from "
                "rotten flesh, is the base of arcane candles."),
                recipes=["alchemy/nitor", "alchemy/alumentum", "alchemy/tallow", "tallow_from_smelting"])]),
    "alchemy/essentia": dict(
        title="Essentia Storage", x=2, y=0, icon="thaumicunity:warded_jar",
        parents=["alchemy/crucible"], aspects=["vacuos", "vinculum"],
        stages=[
            dict(text=(
                "Dissolved essentia does not keep. To store it you need warded glass and a furnace that distils "
                "instead of smelting.\n\nMake two observations in Alchemy."),
                requirements=[("knowledge", "observation", "alchemy", 2)]),
            dict(text=(
                "The alchemical furnace boils items into essentia and pushes it into a warded jar above it. A jar "
                "holds one aspect at a time; label it with paper to keep it that way. Phials carry ten points of "
                "essentia from jar to jar."),
                recipes=["warded_jar", "alchemical_furnace", "phial"])]),
    "alchemy/vis_crystals": dict(
        title="Vis Crystals", x=4, y=1, icon="thaumicunity:ignis_vis_crystal",
        parents=["alchemy/essentia", "alchemy/metallurgy"], aspects=["vitreus", "auram"],
        stages=[
            dict(text=(
                "An amethyst shard steeped in a single primal grows into a crystal of that primal's vis.\n\nDevelop a "
                "theory of Alchemy."),
                requirements=[("knowledge", "theory", "alchemy", 1)]),
            dict(text="Six crystals, one for each primal. They are the raw stuff of the more demanding arcane recipes.",
                 recipes=["alchemy/aer_vis_crystal", "alchemy/terra_vis_crystal", "alchemy/ignis_vis_crystal",
                          "alchemy/aqua_vis_crystal", "alchemy/ordo_vis_crystal", "alchemy/perditio_vis_crystal"])]),
    "alchemy/enchanted_fabric": dict(
        title="Enchanted Fabric", x=4, y=3, icon="thaumicunity:enchanted_fabric",
        parents=["alchemy/metallurgy"], aspects=["pannus", "praecantatio"],
        stages=[
            dict(text="Wool dipped in a crucible of Praecantatio and Vinculum keeps the magic woven into it.\n\n"
                      "Make an observation in Alchemy.",
                 requirements=[("knowledge", "observation", "alchemy", 1)]),
            dict(text="Enchanted fabric lines robes, bags and bindings that must hold magic in.",
                 recipes=["alchemy/enchanted_fabric"])]),
    # ------------------------------------------------------------------ artifice
    "artifice/arcane_stone": dict(
        title="Arcane Stone", x=0, y=0, icon="thaumicunity:arcane_stone",
        parents=["fundamentals/arcane_workbench"], aspects=["saxum", "praecantatio"],
        stages=[dict(text=(
            "Stone worked at the arcane workbench takes on a faint violet sheen and a stubborn hardness. It is the "
            "builder's first arcane material."),
            recipes=["arcane_stone", "arcane_stone_bricks"])]),
    "artifice/goggles": dict(
        title="Goggles of Revealing", x=2, y=0, icon="thaumicunity:goggles_of_revealing",
        parents=["artifice/arcane_stone", "fundamentals/thaumometer"], aspects=["visio", "sensus"],
        stages=[
            dict(text="Two thaumometer lenses in a leather strap.\n\nMake two observations in Artifice.",
                 requirements=[("knowledge", "observation", "artifice", 2)]),
            dict(text=(
                "Wearing the goggles shows the aura as if you were holding a thaumometer and reveals the aspects of "
                "scanned items without holding Shift."),
                recipes=["goggles_of_revealing"])]),
    "artifice/amber": dict(
        title="Amber and Cinnabar", x=0, y=2, icon="thaumicunity:amber",
        parents=["artifice/arcane_stone"], aspects=["vinculum", "hydrargyrum"],
        stages=[
            dict(text=(
                "Two minerals turn up wherever the aura is old: amber, which holds things still, and cinnabar, which "
                "smelts down into quicksilver.\n\nScan some amber and some cinnabar."),
                requirements=[("scan", "item:thaumicunity:amber"), ("scan", "item:thaumicunity:cinnabar")]),
            dict(text="Both are common in recipes of every field.",
                 recipes=["amber_block", "amber_from_block", "quicksilver_from_smelting_cinnabar",
                          "quicksilver_from_smelting_cinnabar_ore", "amber_from_smelting_amber_ore"])]),
    # ------------------------------------------------------------------ infusion
    "infusion/runic_matrix": dict(
        title="Infusion", x=0, y=0, icon="thaumicunity:runic_matrix",
        parents=["alchemy/metallurgy", "artifice/arcane_stone"], special=True, aspects=["praecantatio", "ordo"],
        stages=[
            dict(text=(
                "Infusion binds essentia and items into one object. Place the item to be infused on an arcane "
                "pedestal, set a runic matrix two blocks above it, and ring the pedestal with more pedestals holding "
                "the components. The essentia is drawn from warded jars nearby.\n\nDevelop a theory of Infusion."),
                requirements=[("knowledge", "theory", "infusion", 1)]),
            dict(text=(
                "Use the matrix with a casting instrument (or Salis Mundus) to begin. The matrix pulls essentia one "
                "point at a time, then absorbs the components one by one."),
                recipes=["runic_matrix", "arcane_pedestal"])]),
    "infusion/stability": dict(
        title="Stability", x=2, y=0, icon="minecraft:candle",
        parents=["infusion/runic_matrix"], aspects=["ordo", "vitreus"],
        stages=[
            dict(text="Solve the puzzle linking Ordo and Vitreus.",
                 requirements=[("insight", ["ordo", "vitreus"])]),
            dict(text=(
                "An infusion that is unstable lashes out: essentia spills as flux, components are flung away, the "
                "air itself bites. Symmetric pedestals, fewer components and calm, flux-free surroundings all help, "
                "and so do stabilisers placed around the altar: candles, skulls, amethyst clusters and enchanting "
                "tables."))]),
    "infusion/thaumium_caster": dict(
        title="Thaumium Gauntlet", x=0, y=2, icon="thaumicunity:thaumium_caster",
        parents=["infusion/runic_matrix", "auromancy/casting"], aspects=["instrumentum", "potentia"],
        stages=[
            dict(text="A caster's gauntlet can be infused with thaumium.\n\nObtain a caster's gauntlet.",
                 requirements=[("obtain", "thaumicunity:basic_caster", 1)]),
            dict(text="The thaumium gauntlet casts with a quarter more power and draws vis more efficiently.",
                 recipes=["infusion/thaumium_caster"])]),
    "infusion/greater_foci": dict(
        title="Greater Foci", x=2, y=2, icon="thaumicunity:adept_focus",
        parents=["infusion/runic_matrix", "auromancy/foci"], aspects=["praecantatio", "cognitio"],
        stages=[
            dict(text="Develop a theory of Auromancy.",
                 requirements=[("knowledge", "theory", "auromancy", 1)]),
            dict(text=(
                "Infused gems make foci that can hold more of a spell: the adept's focus takes three effects and "
                "three modifiers, the archmage's focus four effects, five modifiers and nested spells."),
                recipes=["infusion/adept_focus", "infusion/archmage_focus"])]),
    # ------------------------------------------------------------------ auromancy
    "auromancy/casting": dict(
        title="Casting", x=0, y=0, icon="thaumicunity:basic_caster",
        parents=["fundamentals/aura"], special=True, aspects=["auram", "potentia"],
        stages=[
            dict(text=(
                "A caster's gauntlet channels the aura through a focus held in the other hand. The focus decides "
                "what the spell does; the gauntlet decides how much vis it draws and how strongly it casts.\n\nMake "
                "two observations in Auromancy."),
                requirements=[("knowledge", "observation", "auromancy", 2)]),
            dict(text=(
                "Spells cost primal vis, taken from the aura around you. A blank focus does nothing until a spell "
                "is set into it."),
                recipes=["basic_caster", "apprentice_focus"])]),
    "auromancy/foci": dict(
        title="Apprentice Foci", x=2, y=0, icon="thaumicunity:apprentice_focus",
        parents=["auromancy/casting"], aspects=["praecantatio", "ignis"],
        stages=[
            dict(text="Craft a caster's gauntlet.", requirements=[("craft", "thaumicunity:basic_caster")]),
            dict(text=(
                "Six spells every apprentice learns. Each is set into a blank focus at the arcane workbench: embers, "
                "frost shards, excavation, mending, gale and warding."),
                recipes=["focus/embers", "focus/frost_shard", "focus/excavation", "focus/mending", "focus/gale",
                         "focus/warding"])]),
    "auromancy/advanced_foci": dict(
        title="Adept Foci", x=4, y=0, icon="thaumicunity:adept_focus",
        parents=["auromancy/foci", "infusion/greater_foci"], aspects=["potentia", "ignis"],
        stages=[
            dict(text="Solve the puzzle linking Potentia and Ignis.",
                 requirements=[("insight", ["potentia", "ignis"])]),
            dict(text=(
                "Spells that combine two effects can produce a third: fire and water together scald, air and fire "
                "flare. These foci use that."),
                recipes=["focus/shock", "focus/steam_burst", "focus/growth", "focus/blink"])]),
    "auromancy/master_foci": dict(
        title="Starfall", x=6, y=0, icon="thaumicunity:archmage_focus",
        parents=["auromancy/advanced_foci"], aspects=["lux", "radio"],
        stages=[
            dict(text="Develop two theories of Auromancy.",
                 requirements=[("knowledge", "theory", "auromancy", 2)]),
            dict(text="Light and lightning, split into seeking stars.", recipes=["focus/starfall"])]),
    "auromancy/wands": dict(
        title="Wands", x=0, y=-2, icon="thaumicunity:wand",
        parents=["auromancy/casting"], aspects=["auram", "instrumentum"],
        stages=[
            dict(text=(
                "A gauntlet borrows vis from the place where you stand. A wand carries its own: a rod that holds vis "
                "of each primal, capped at both ends with metal that keeps it from leaking away.\n\nMake an "
                "observation in Auromancy."),
                requirements=[("knowledge", "observation", "auromancy", 1)]),
            dict(text=(
                "Set two caps and a rod diagonally on the arcane workbench. A wooden rod holds 30 vis of each primal. "
                "The wand casts the focus fixed to it: hold a focus in your other hand and use the wand while "
                "sneaking to fix it, or sneak and use it with an empty off-hand to take the focus off. Spells cost "
                "the wand's own vis, made a little dearer by iron caps.\n\nA wand carried around slowly draws vis "
                "from a strong aura, and when it runs dry it eats vis crystals from your pack. Used on a bookshelf, "
                "crafting table, cauldron or lectern it works like Salis Mundus, for a little vis."),
                recipes=["wand/iron_cap", "wand/wood_iron"])]),
    "auromancy/wand_caps": dict(
        title="Wand Caps", x=2, y=-2, icon="thaumicunity:gold_wand_cap",
        parents=["auromancy/wands"], aspects=["metallum", "instrumentum"],
        stages=[
            dict(text="Craft a wand.", requirements=[("craft", "thaumicunity:wand")]),
            dict(text=(
                "Better metal wastes less. Gold caps cost nothing extra; copper caps are cheap but only spare order "
                "and entropy; brass caps make fire and earth spells a tenth cheaper."),
                recipes=["wand/copper_cap", "wand/gold_cap", "wand/brass_cap", "wand/wood_copper", "wand/wood_gold",
                         "wand/wood_brass"])]),
    "auromancy/thaumium_caps": dict(
        title="Thaumium Caps", x=4, y=-2, icon="thaumicunity:thaumium_wand_cap",
        parents=["auromancy/wand_caps", "alchemy/metallurgy"], aspects=["praecantatio", "metallum"],
        stages=[
            dict(text="Develop a theory of Auromancy and obtain a thaumium ingot.",
                 requirements=[("knowledge", "theory", "auromancy", 1), ("obtain", "thaumicunity:thaumium_ingot", 1)]),
            dict(text="Thaumium caps make every spell a tenth cheaper than its plain cost.",
                 recipes=["wand/thaumium_cap", "wand/wood_thaumium"])]),
    "auromancy/elemental_cores": dict(
        title="Elemental Cores", x=2, y=-4, icon="thaumicunity:blaze_wand_core",
        parents=["auromancy/wands", "infusion/runic_matrix"], aspects=["praecantatio", "potentia"],
        stages=[
            dict(text="Develop a theory of Auromancy.", requirements=[("knowledge", "theory", "auromancy", 1)]),
            dict(text=(
                "Infuse a material steeped in one primal and it becomes a wand core that holds 75 vis of each. "
                "Each core favours its primal: spells pay a tenth less of it, and the core slowly refills it by "
                "itself. Reed favours Aer, obsidian Terra, blaze Ignis, ice Aqua, quartz Ordo and bone Perditio."),
                recipes=[f"infusion/{rod}_wand_core" for rod in ("reed", "obsidian", "blaze", "ice", "quartz", "bone")]
                + [f"wand/{rod}_{cap}" for rod in ("reed", "obsidian", "blaze", "ice", "quartz", "bone")
                   for cap in ("iron", "copper", "gold", "brass", "thaumium")])]),
    "auromancy/wand_pedestal": dict(
        title="Recharge Pedestal", x=0, y=-4, icon="thaumicunity:wand_pedestal",
        parents=["auromancy/wands"], aspects=["auram", "ordo"],
        stages=[
            dict(text="Craft a wand.", requirements=[("craft", "thaumicunity:wand")]),
            dict(text=(
                "A wand left on this pedestal fills from the aura of the chunk far faster than one carried around, "
                "and it may take the aura lower. Place it where the aura is strong and give the place time to "
                "recover."),
                recipes=["wand/wand_pedestal"])]),
    "auromancy/flux": dict(
        title="Living with Flux", x=0, y=2, icon="thaumicunity:perditio_vis_crystal",
        parents=["auromancy/casting"], aspects=["vitium", "auram"],
        stages=[
            dict(text="Make an observation in Auromancy.",
                 requirements=[("knowledge", "observation", "auromancy", 1)]),
            dict(text=(
                "Every spell adds a little flux to the aura where it is cast, and the more vis a spell costs, the "
                "more flux it leaves. Cast where the aura is strong, move on before it is drained, and give "
                "overworked places time to recover."))]),
}
