# Third library extension: more fantasy music from open-source games (November 2026).
# Sources, licences and composers come from each game's own credits:
#   Valyria Tear  LICENSES.txt            Wyrmsun      music/credits.txt
#   Duelyst       LICENSE (CC0)           HyperRogue   hyperrogue-music.txt
#   Tuxemon       ATTRIBUTIONS.md         MegaGlest    docs/AUTHORS.data.txt, docs/LICENSE.data.txt
#   Ardentryst    COPYING.data            The Mana World tmw-music/music-license.md
#   TMW2          evol-music/music/LICENSE   Stendhal  doc/sources/audio-music.txt
#   Wesnoth       copyrights.csv          Veloren      assets/voxygen/audio/soundtrack.ron (GPL v3)
# Moods and places were assigned by hand from titles, where the games play each piece and the measurements.
# Each entry: key, files (paths in the repository), title, composer, licence, moods, places.

GH = 'https://raw.githubusercontent.com/{repo}/{sha}/{path}'
GL = 'https://gitlab.com/{repo}/-/raw/{sha}/{path}'

SOURCES = {
    'VALYRIA_TEAR': ('ValyriaTear/ValyriaTear', '1c882efff325c941c59aa59c93fb1256258ac845', GH),
    'WYRMSUN': ('Andrettin/Wyrmsun', 'f0330d2a747f6746ea8c067e65cf6c944a4653f3', GH),
    'DUELYST': ('open-duelyst/duelyst', '1d78066eff4bcc453df12bda568425263af4cf0a', GH),
    'HYPERROGUE': ('zenorogue/hyperrogue', '70453170074a0695a52f11ce7a806ac3f782a48c', GH),
    'TUXEMON': ('Tuxemon/Tuxemon', '9e6258ff726b786040a267e8bdbbf037b560285e', GH),
    'MEGAGLEST': ('MegaGlest/megaglest-data', 'f5ab4c1dedcc4568f784ed5ebbcec96290fd4456', GH),
    'ARDENTRYST': ('ardentryst/ardentryst', '7b66716d99b63ad0fa5794931fd39115b1d2056b', GH),
    'MANA_WORLD': ('themanaworld/tmw-music', '444e7b1d7775e69d539356520ca0cecd2c1b1e4a', GH),
    'MOUBOOTAUR': ('TMW2/evol-music', 'ef0ffd6fbd29bc0d9024128c88850656332a6299', GL),
    'STENDHAL': ('arianne/stendhal', 'fcd8330db06034098f9b164aa67fed82f261ae59', GH),
    'WESNOTH': ('wesnoth/wesnoth', '9f54f6a4f65b0d942a70436372e7cf28c4ba14d0', GH),
    'WIDELANDS': ('widelands/widelands', '29753773d9c130e2a6a835ee6a14ad2bf317371d', GH),
    'FLARE': ('flareteam/flare-game', 'af6eee6d339ac98011864bfe89da837fe7769c28', GH),
    'VELOREN': ('veloren/veloren', '131d1193387861d35c74655662148f303c22c5b4', GH),
    'INVERTIKA': ('invertika/data', 'cdf4edc6b1b1fc47edfec5b30a496b57a9cc1e00', GH),
}

# id prefix per collection
PREFIX = {
    'VALYRIA_TEAR': 'valyria', 'WYRMSUN': 'wyrmsun', 'DUELYST': 'duelyst', 'HYPERROGUE': 'hyperrogue',
    'TUXEMON': 'tuxemon', 'MEGAGLEST': 'megaglest', 'ARDENTRYST': 'ardentryst', 'MANA_WORLD': 'tmw',
    'MOUBOOTAUR': 'tmw2', 'STENDHAL': 'stendhal', 'WESNOTH': 'wesnoth', 'WIDELANDS': 'widelands',
    'FLARE': 'flare', 'VELOREN': 'veloren', 'INVERTIKA': 'invertika',
}

V, D, HR, TX = 'data/music/', 'app/resources/music/', 'music/', 'mods/tuxemon/music/'
WY = 'music/oaml/'
MF = 'Marcelo Fernandez'


def oaml(theme, parts):
    return [f'{WY}{theme}_{p}.ogg' for p in parts]


MORE = {
    'VALYRIA_TEAR': [
        ('caketown', [V + 'Caketown_1-OGA-mat-pablo.ogg'], 'Caketown', 'Matthew Pablo', 'CC_BY_SA_3', 'HAPPY IDYLLIC', 'TOWN'),
        ('soliloquy', [V + 'Soliloquy_1-OGA-mat-pablo.ogg'], 'Soliloquy', 'Matthew Pablo', 'CC_BY_SA_3', 'SAD CALM', ''),
        ('accion', [V + 'accion-OGA-djsaryon.ogg'], 'Acción', 'djsaryon', 'CC_BY_SA_3', 'ACTION TENSE', 'BATTLEFIELD'),
        ('awareness', [V + 'awareness_el_corleo.ogg'], 'Awareness', 'Eliot Corley', 'CC_BY_SA_3', 'MYSTERIOUS TENSE', ''),
        ('dont_close_your_eyes', [V + 'dont_close_your_eyes.ogg'], "Don't Close Your Eyes", 'Alexandr Zhelanov', 'CC_BY_3', 'EERIE MYSTERIOUS', 'HAUNTED'),
        ('heroism', [V + 'heroism-OGA-Edward-J-Blakeley.ogg'], 'Heroism', 'Edward J. Blakeley', 'GPL2_OR_3', 'ACTION HAPPY', 'BATTLEFIELD'),
        ('house_in_a_forest', [V + 'house_in_a_forest_loop_horrorpen_oga.ogg'], 'House in a Forest', 'HorrorPen', 'CC_BY_3', 'MYSTERIOUS CALM', 'FOREST'),
        ('icy_heights', [V + 'icy_wind.ogg'], 'Icy Heights', 'Écrivain', 'CC0', 'CALM MYSTERIOUS', 'WINTER MOUNTAINS'),
        ('winter_feeling', [V + 'mountain_shrine.ogg'], 'Winter Feeling', 'Tom Peter', 'CC_BY_SA_3', 'CALM SAD', 'WINTER TEMPLE'),
        ('sad_moment', [V + 'sad_moment.ogg'], 'Sad Moment', 'lasercheese', 'CC_BY_3', 'SAD', ''),
        ('shrine', [V + 'shrine-OGA-yd.ogg'], 'Shrine', 'yd', 'CC0', 'MYSTERIOUS CALM', 'TEMPLE'),
        ('gray_trip', [V + 'overworld_present.ogg'], 'Gray Trip', 'HorrorPen', 'CC_BY_3', 'NEUTRAL MYSTERIOUS', 'OPEN_LAND'),
    ],
    'WYRMSUN': [
        ('dwarf_theme', oaml('DwarfTheme', ['A1', 'A2', 'B1']), 'Zwergen-Thema', MF, 'CC_BY_4', 'NEUTRAL HAPPY', 'MOUNTAINS CAVE'),
        ('dwarf_theme_2', oaml('DwarfTheme2', ['A1', 'A2', 'B1']), 'Zwergen-Thema II', MF, 'CC_BY_4', 'NEUTRAL TENSE', 'MOUNTAINS CAVE'),
        ('dwarf_loading', oaml('DwarfLoading', ['A1', 'A2']), 'Zwergenhallen', MF, 'CC_BY_4', 'CALM MYSTERIOUS', 'CAVE MOUNTAINS'),
        ('germanic_theme', oaml('GermanicTheme', ['A1', 'A2', 'B1', 'B2']), 'Germanen-Thema', MF, 'CC_BY_4', 'NEUTRAL TENSE', 'NORTH FOREST'),
        ('germanic_theme_2', oaml('GermanicTheme2', ['Intro', 'A1', 'A2', 'A3', 'B1', 'B2']), 'Germanen-Thema II', MF, 'CC_BY_4', 'TENSE ACTION', 'NORTH BATTLEFIELD'),
        ('germanic_theme_3', oaml('GermanicTheme3', ['A1', 'A2', 'A3', 'B1', 'B2', 'N1', 'N2', 'N3', 'N4']), 'Germanen-Thema III', MF, 'CC_BY_4', 'MYSTERIOUS NEUTRAL', 'NORTH FOREST'),
        ('germanic_loading', oaml('GermanicLoading', ['A1', 'A2', 'A3']), 'Am Lagerfeuer der Germanen', MF, 'CC_BY_4', 'CALM NEUTRAL', 'NORTH'),
        ('menu_theme', oaml('MenuTheme', ['Intro', 'A1', 'A2', 'A3', 'A4', 'C']), 'Wyrmsun', MF, 'CC_BY_4', 'NEUTRAL MYSTERIOUS', 'NORTH'),
    ],
    'DUELYST': [
        ('main_menu', [D + 'mainmenu_v2c_looping.m4a'], 'Hauptthema', '', 'CC0', 'NEUTRAL HAPPY', ''),
        ('age_of_disjunction', [D + 'music_ageofdisjunction.m4a'], 'Age of Disjunction', '', 'CC0', 'TENSE MYSTERIOUS', 'MAGIC'),
        ('battlemap_1', [D + 'music_battlemap01.m4a'], 'Schlachtfeld I', '', 'CC0', 'TENSE ACTION', 'BATTLEFIELD'),
        ('battlemap_2', [D + 'music_battlemap02.m4a'], 'Schlachtfeld II', '', 'CC0', 'ACTION TENSE', 'BATTLEFIELD'),
        ('abyssian', [D + 'music_battlemap_abyssian.m4a'], 'Abyssian', '', 'CC0', 'EERIE TENSE', 'HAUNTED'),
        ('blue_monolith', [D + 'music_battlemap_bluemonolith.m4a'], 'Blue Monolith', '', 'CC0', 'MYSTERIOUS TENSE', 'MAGIC'),
        ('duskfall', [D + 'music_battlemap_duskfall.m4a'], 'Duskfall', '', 'CC0', 'MYSTERIOUS SAD', 'FOREST'),
        ('fires_of_victory', [D + 'music_battlemap_firesofvictory.m4a'], 'Fires of Victory', '', 'CC0', 'ACTION HAPPY', 'BATTLEFIELD'),
        ('morinkhur', [D + 'music_battlemap_morinkhur.m4a'], 'Morinkhur', '', 'CC0', 'TENSE MYSTERIOUS', 'JUNGLE'),
        ('risen_sun', [D + 'music_battlemap_risensun2.m4a'], 'Risen Sun', '', 'CC0', 'NEUTRAL TENSE', 'CASTLE'),
        ('songhai', [D + 'music_battlemap_songhai.m4a'], 'Songhai', '', 'CC0', 'TENSE MYSTERIOUS', 'ASIA'),
        ('vanar', [D + 'music_battlemap_vanar.m4a'], 'Vanar', '', 'CC0', 'MYSTERIOUS TENSE', 'WINTER NORTH'),
        ('vetruv', [D + 'music_battlemap_vetruv.m4a'], 'Vetruv', '', 'CC0', 'TENSE MYSTERIOUS', 'DESERT ORIENT'),
        ('challenge', [D + 'music_challengemode.m4a'], 'Herausforderung', '', 'CC0', 'TENSE NEUTRAL', ''),
        ('codex', [D + 'music_codex.m4a'], 'Codex', '', 'CC0', 'MYSTERIOUS CALM', 'MAGIC TEMPLE'),
        ('collection', [D + 'music_collection.m4a'], 'Sammlung', '', 'CC0', 'CALM NEUTRAL', ''),
        ('gauntlet', [D + 'music_gauntlet.m4a'], 'Gauntlet', '', 'CC0', 'ACTION TENSE', 'BATTLEFIELD'),
        ('lyonar', [D + 'music_mainmenu_lyonar.m4a'], 'Lyonar', '', 'CC0', 'NEUTRAL HAPPY', 'CASTLE TEMPLE'),
        ('songhai_theme', [D + 'music_mainmenu_songhai.m4a'], 'Songhai-Thema', '', 'CC0', 'MYSTERIOUS CALM', 'ASIA'),
        ('play_mode', [D + 'music_playmode.m4a'], 'Aufbruch', '', 'CC0', 'NEUTRAL TENSE', ''),
        ('tutorial', [D + 'music_tutorial.m4a'], 'Lehrjahre', '', 'CC0', 'NEUTRAL CALM', ''),
    ],
    'HYPERROGUE': [
        ('crossroads', [HR + 'hr3-crossroads.ogg'], 'Crossroads', 'Shawn Parrotte', 'CC_BY_SA_3', 'NEUTRAL MYSTERIOUS', 'OPEN_LAND'),
        ('desert', [HR + 'hr3-desert.ogg'], 'Desert', 'Shawn Parrotte', 'CC_BY_SA_3', 'MYSTERIOUS NEUTRAL', 'DESERT ORIENT'),
        ('icy_lands', [HR + 'hr3-icyland.ogg'], 'Icy Lands', 'Shawn Parrotte', 'CC_BY_SA_3', 'CALM MYSTERIOUS', 'WINTER'),
        ('living_caves', [HR + 'hr3-caves.ogg'], 'Living Caves', 'Shawn Parrotte', 'CC_BY_SA_3', 'EERIE MYSTERIOUS', 'CAVE'),
        ('jungle', [HR + 'hr3-jungle.ogg'], 'Jungle', 'Shawn Parrotte', 'CC_BY_SA_3', 'TENSE MYSTERIOUS', 'JUNGLE'),
        ('alchemists_lab', [HR + 'hr3-laboratory.ogg'], "Alchemist's Lab", 'Shawn Parrotte', 'CC_BY_SA_3', 'MYSTERIOUS HAPPY', 'MAGIC'),
        ('hall_of_mirrors', [HR + 'hr3-mirror.ogg'], 'Hall of Mirrors', 'Shawn Parrotte', 'CC_BY_SA_3', 'MYSTERIOUS EERIE', 'MAGIC'),
        ('graveyard', [HR + 'hr3-graveyard.ogg'], 'Graveyard', 'Shawn Parrotte', 'CC_BY_SA_3', 'EERIE SAD', 'HAUNTED'),
        ('rlyeh', [HR + 'hr3-rlyeh.ogg'], "R'Lyeh", 'Shawn Parrotte', 'CC_BY_SA_3', 'EERIE TENSE', 'UNDERWATER TEMPLE'),
        ('hell', [HR + 'hr3-hell.ogg'], 'Hell', 'Shawn Parrotte', 'CC_BY_SA_3', 'TENSE EERIE', 'HAUNTED'),
        ('eternal_motion', [HR + 'hr3-motion.ogg'], 'Land of Eternal Motion', 'Shawn Parrotte', 'CC_BY_SA_3', 'TENSE MYSTERIOUS', 'MAGIC'),
        ('caribbean', [HR + 'hr-savino-caribbean.ogg'], 'Caribbean', 'Will Savino', 'CC_BY_SA_3', 'HAPPY NEUTRAL', 'SEA'),
        ('ivory_tower', [HR + 'hr-savino-ivory.ogg'], 'Ivory Tower', 'Will Savino', 'CC_BY_SA_3', 'MYSTERIOUS CALM', 'MAGIC CASTLE'),
        ('ocean', [HR + 'hr-savino-ocean.ogg'], 'Ocean', 'Will Savino', 'CC_BY_SA_3', 'CALM IDYLLIC', 'SEA'),
        ('palace', [HR + 'hr-savino-palace.ogg'], 'Palace', 'Will Savino', 'CC_BY_SA_3', 'NEUTRAL HAPPY', 'CASTLE'),
        ('hunting_grounds', [HR + 'hr-domina-hunting.ogg'], 'Hunting Grounds', 'Lincoln Domina', 'CC_BY_SA_3', 'TENSE NEUTRAL', 'FOREST'),
        ('lost_mountain', [HR + 'hr-domina-mountain.ogg'], 'Lost Mountain', 'Lincoln Domina', 'CC_BY_SA_3', 'MYSTERIOUS CALM', 'MOUNTAINS'),
    ],
    'TUXEMON': [
        ('battle', [TX + 'JRPGCollection/ogg/JRPG_battle_loop.ogg'], 'Battle', 'Yubatake', 'CC_BY_3', 'ACTION TENSE', 'BATTLEFIELD'),
        ('battle_slow', [TX + 'JRPGCollection/ogg/JRPG_battle_loop_slow.ogg'], 'Battle (slow)', 'Yubatake', 'CC_BY_3', 'TENSE', 'BATTLEFIELD'),
        ('dungeon', [TX + 'JRPGCollection/ogg/JRPG_dungeon_loop.ogg'], 'Dungeon', 'Yubatake', 'CC_BY_3', 'TENSE MYSTERIOUS', 'DUNGEON'),
        ('fields', [TX + 'JRPGCollection/ogg/JRPG_fields_loop.ogg'], 'Fields', 'Yubatake', 'CC_BY_3', 'HAPPY IDYLLIC', 'OPEN_LAND'),
        ('labyrinth', [TX + 'JRPGCollection/ogg/JRPG_labyrinth_loop.ogg'], 'Labyrinth', 'Yubatake', 'CC_BY_3', 'MYSTERIOUS EERIE', 'DUNGEON CAVE'),
        ('main_theme', [TX + 'JRPGCollection/ogg/JRPG_mainTheme.ogg'], 'Main Theme', 'Yubatake', 'CC_BY_3', 'NEUTRAL HAPPY', ''),
        ('temple', [TX + 'JRPGCollection/ogg/JRPG_temple.ogg'], 'Temple', 'Yubatake', 'CC_BY_3', 'MYSTERIOUS CALM', 'TEMPLE'),
        ('inn', [TX + 'JRPGCollection2/ogg/JRPG_inn_loop.ogg'], 'Inn', 'Yubatake', 'CC_BY_3', 'CALM HAPPY', 'TAVERN HOME'),
        ('shop', [TX + 'JRPGCollection2/ogg/JRPG_shop_loop.ogg'], 'Shop', 'Yubatake', 'CC_BY_3', 'HAPPY', 'TOWN'),
        ('tavern', [TX + 'JRPGCollection2/ogg/JRPG_tavern.ogg'], 'Tavern', 'Yubatake', 'CC_BY_3', 'HAPPY', 'TAVERN'),
        ('docks', [TX + 'JRPG_docks_loop.ogg'], 'Docks', 'Yubatake', 'CC_BY_3', 'NEUTRAL HAPPY', 'SEA TOWN'),
        ('mystic_isle', [TX + 'JRPG_mysticIsle.ogg'], 'Mystic Isle', 'Yubatake', 'CC_BY_3', 'MYSTERIOUS CALM', 'MAGIC SEA'),
        ('princess', [TX + 'JRPG_princess.ogg'], 'Princess', 'Yubatake', 'CC_BY_3', 'ROMANTIC CALM', 'CASTLE'),
        ('royal_court', [TX + 'JRPG_royalCourt_loop.ogg'], 'Royal Court', 'Yubatake', 'CC_BY_3', 'NEUTRAL HAPPY', 'CASTLE'),
        ('town', [TX + 'JRPG_town_loop.ogg'], 'Town', 'Yubatake', 'CC_BY_3', 'HAPPY IDYLLIC', 'TOWN'),
        ('good_morning', [TX + 'JRPG_goodMorning.ogg'], 'Good Morning', 'Yubatake', 'CC_BY_3', 'HAPPY CALM', 'HOME'),
        ('good_night', [TX + 'JRPGCollection/ogg/JRPG_goodNight.ogg'], 'Good Night', 'Yubatake', 'CC_BY_3', 'CALM ROMANTIC', 'HOME'),
        ('peasant_kingdom', [TX + 'peasant_kingdom.ogg'], 'Peasant Kingdom', 'Spring', 'CC_BY_3', 'HAPPY IDYLLIC', 'TOWN OPEN_LAND'),
    ],
    'MEGAGLEST': [
        ('intro', ['data/core/menu/music/intro_music.ogg'], 'Intro', 'The Glest Team', 'CC_BY_SA_3', 'NEUTRAL TENSE', ''),
        ('menu', ['data/core/menu/music/menu_music.ogg'], 'Menu', 'The Glest Team', 'CC_BY_SA_3', 'NEUTRAL MYSTERIOUS', ''),
        ('magic', ['techs/megapack/factions/magic/music/music_magic.ogg'], 'Magic', 'The Glest Team', 'CC_BY_SA_3', 'MYSTERIOUS NEUTRAL', 'MAGIC'),
        ('tech', ['techs/megapack/factions/tech/music/music_tech.ogg'], 'Tech', 'The Glest Team', 'CC_BY_SA_3', 'TENSE NEUTRAL', 'CASTLE BATTLEFIELD'),
        ('norsemen', ['techs/megapack/factions/norsemen/music/manolo_camp-medieval.ogg'], 'Medieval', 'Manolo Camp', 'CC_BY_SA_3', 'NEUTRAL HAPPY', 'NORTH'),
        ('romans', ['techs/megapack/factions/romans/music/romans.ogg'], 'Chronicles III', 'Mattias Westlund', 'CC_BY_SA_3', 'NEUTRAL TENSE', 'ANCIENT BATTLEFIELD'),
        ('egypt', ['techs/megapack/factions/egypt/music/asak-jadeflange.ogg'], 'Jadeflange', 'Asak', 'CC_BY_SA_3', 'MYSTERIOUS', 'ORIENT DESERT'),
        ('persian', ['techs/megapack/factions/persian/music/aminos-together_together.ogg'], 'Together', 'Aminos', 'CC_BY_SA_3', 'NEUTRAL MYSTERIOUS', 'ORIENT'),
    ],
    'ARDENTRYST': [
        ('sempridge', ['Music/Sempridge.ogg'], 'Sempridge', 'Jordan Trudgett', 'CC_BY_3', 'HAPPY IDYLLIC', 'TOWN'),
        ('sempridge_2', ['Music/Sempridge2.ogg'], 'Sempridge II', 'Jordan Trudgett', 'CC_BY_3', 'CALM HAPPY', 'TOWN'),
        ('snodom', ['Music/Snodom.ogg'], 'Snodom', 'Jordan Trudgett', 'CC_BY_3', 'MYSTERIOUS CALM', 'WINTER'),
        ('story', ['Music/Story.ogg'], 'Story', 'Jordan Trudgett', 'CC_BY_3', 'NEUTRAL SAD', ''),
        ('world_map', ['Music/World_Map.ogg'], 'World Map', 'Jordan Trudgett', 'CC_BY_3', 'HAPPY NEUTRAL', 'OPEN_LAND'),
        ('world_map_2', ['Music/World_Map2.ogg'], 'World Map II', 'Jordan Trudgett', 'CC_BY_3', 'NEUTRAL IDYLLIC', 'OPEN_LAND'),
        ('battle', ['Music/battle.ogg'], 'Battle', 'Jordan Trudgett', 'CC_BY_3', 'ACTION TENSE', 'BATTLEFIELD'),
        ('battle_2', ['Music/battle2.ogg'], 'Battle II', 'Jordan Trudgett', 'CC_BY_3', 'ACTION', 'BATTLEFIELD'),
        ('castle', ['Music/castle.ogg'], 'Castle', 'Jordan Trudgett', 'CC_BY_3', 'TENSE MYSTERIOUS', 'CASTLE'),
        ('castle_2', ['Music/castle2.ogg'], 'Castle II', 'Jordan Trudgett', 'CC_BY_3', 'TENSE', 'CASTLE DUNGEON'),
        ('holy', ['Music/holy.ogg'], 'Holy', 'Jordan Trudgett', 'CC_BY_3', 'CALM MYSTERIOUS', 'TEMPLE'),
        ('shop', ['Music/shop.ogg'], 'Shop', 'Jordan Trudgett', 'CC_BY_3', 'HAPPY', 'TOWN TAVERN'),
        ('theme', ['Music/theme1.ogg'], 'Ardentryst', 'Jordan Trudgett', 'CC_BY_3', 'NEUTRAL HAPPY', ''),
    ],
    'MANA_WORLD': [
        ('barbarians', ['barbarians.ogg'], 'Barbarians', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'TENSE ACTION', 'NORTH BATTLEFIELD'),
        ('cavesong', ['cavesong.ogg'], 'Cave Song', 'finalman & yakslem', 'GPL', 'MYSTERIOUS EERIE', 'CAVE'),
        ('clouds_calling', ['clouds_calling.ogg'], 'Clouds Calling', 'Dariunas', 'GPL2_CC_BY_SA', 'CALM IDYLLIC', 'MOUNTAINS OPEN_LAND'),
        ('despair', ['despair.ogg'], 'Despair', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'SAD', ''),
        ('dimonds_cove', ['dimonds_cove.ogg'], "Dimond's Cove", 'Aasif Chaudry', 'GPL2_CC_BY_SA', 'HAPPY IDYLLIC', 'SEA TOWN'),
        ('dramatic', ['dramatic.ogg'], 'Dramatic 4', 'Sei Mutsuki (PeriTune)', 'CC_BY_4', 'TENSE ACTION', ''),
        ('dreary_day', ['dreary_day.ogg'], 'Dreary Day', 'Ledmitz', 'GPL2_CC_BY_SA', 'SAD CALM', ''),
        ('epilogue', ['epilogue.ogg'], 'Epilogue', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'CALM SAD', ''),
        ('explorers_melody', ['explorers_melody.ogg'], "Explorer's Melody", 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'HAPPY NEUTRAL', 'OPEN_LAND'),
        ('ghoulish_fun', ['ghoulish_fun.ogg'], 'Ghoulish Fun', 'Eric Matyas', 'CC_BY_4', 'EERIE HAPPY', 'HAUNTED'),
        ('graveyard', ['graveyard.ogg'], 'Graveyard', 'Feline Monstrosity', 'GPL', 'EERIE MYSTERIOUS', 'HAUNTED'),
        ('in_my_heart', ['in_my_heart.ogg'], 'In My Heart', 'Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'ROMANTIC CALM', ''),
        ('inquisitive_inn', ['inquisitive_inn.ogg'], 'Inquisitive Inn', 'Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'HAPPY', 'TAVERN'),
        ('magick_real', ['magick-real.ogg'], 'Magick Real', 'Aasif Chaudry', 'GPL2_CC_BY_SA', 'MYSTERIOUS', 'MAGIC'),
        ('mystique_forest', ['mystique_forest.ogg'], 'Mystique Forest', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'MYSTERIOUS CALM', 'FOREST MAGIC'),
        ('night_is_calling', ['night_is_calling.ogg'], 'Night is Calling', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'MYSTERIOUS CALM', ''),
        ('ominous', ['ominous_3.ogg'], 'Ominous 3 Active', 'Sei Mutsuki (PeriTune)', 'CC_BY_4', 'TENSE EERIE', 'DUNGEON'),
        ('overwhelmed_underground', ['overwhelmed_underground.ogg'], 'Overwhelmed Underground', 'Ledmitz', 'GPL2_CC_BY_SA', 'TENSE EERIE', 'CAVE DUNGEON'),
        ('peace', ['peace.ogg'], 'Peace', 'Doulos', 'GPL', 'CALM IDYLLIC', 'OPEN_LAND'),
        ('sail_away', ['sail_away.ogg'], 'Sail Away', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL2_CC_BY_SA', 'HAPPY NEUTRAL', 'SEA'),
        ('snow_village', ['snow_village.ogg'], 'Snow Village', 'Aasif Chaudry', 'GPL2_CC_BY_SA', 'CALM HAPPY', 'WINTER TOWN'),
        ('the_forest', ['the_forest.ogg'], 'The Forest', 'Dariunas', 'GPL2_CC_BY_SA', 'IDYLLIC CALM', 'FOREST'),
    ],
    'MOUBOOTAUR': [
        ('3b5', ['music/3b5.ogg'], '3b5', 'Brandon Morris', 'CC0', 'TENSE ACTION', ''),
        ('academy_bells', ['music/academy_bells.ogg'], 'Academy Bells', 'Nelson James Gatlin', 'GPL3_CC_BY_SA_3', 'CALM HAPPY', 'TEMPLE TOWN'),
        ('adonthell', ['music/adonthell.ogg'], "Waste's Edge", 'ZhayTee', 'GPL3', 'NEUTRAL MYSTERIOUS', 'OPEN_LAND'),
        ('arabesque', ['music/Arabesque.ogg'], 'Arabesque', 'Chris Janowiak', 'CC_BY_3', 'MYSTERIOUS NEUTRAL', 'ORIENT DESERT'),
        ('around_the_world', ['music/around_the_world.ogg'], 'Around the World', 'Snabisch', 'CC_BY_3', 'HAPPY NEUTRAL', 'OPEN_LAND'),
        ('bartk_adventure', ['music/bartk_adventure.ogg'], 'Adventure', 'Bart Kelsey', 'CC_BY_4', 'HAPPY NEUTRAL', 'OPEN_LAND'),
        ('broken_kingdom', ['music/broken_kingdom.ogg'], 'Broken Kingdom', 'Kim Lightyear', 'CC_BY_3', 'SAD CALM', 'CASTLE'),
        ('dariunas_forest', ['music/dariunas_forest.ogg'], "Dariunas' Forest", 'Aasif Chaudry', 'GPL', 'IDYLLIC MYSTERIOUS', 'FOREST'),
        ('deep_cave', ['music/Deep_Cave.ogg'], 'WeltHerrscherer Theme 1', 'yd', 'CC0', 'EERIE MYSTERIOUS', 'CAVE'),
        ('ghouls', ['music/eric_matyas_ghouls.ogg'], 'Ghouls', 'Eric Matyas', 'CC_BY_4', 'EERIE TENSE', 'HAUNTED'),
        ('surreal', ['music/eric_matyas_surreal.ogg'], 'Surreal', 'Eric Matyas', 'CC_BY_4', 'MYSTERIOUS EERIE', 'MAGIC'),
        ('icecave', ['music/icecave.ogg'], 'Ice Cave', 'aquileia', 'GPL', 'MYSTERIOUS CALM', 'CAVE WINTER'),
        ('last_minute', ['music/last_minute.ogg'], 'Last Minute', 'HorrorPen', 'CC_BY_3', 'TENSE ACTION', ''),
        ('let_the_battles_begin', ['music/let_the_battles_begin.ogg'], 'Let the Battles Begin', 'northivanastan', 'CC0', 'ACTION TENSE', 'BATTLEFIELD'),
        ('misty_shrine', ['music/Misty_Shrine.ogg'], 'Misty Shrine', 'ORMIX', 'CC_BY_3', 'MYSTERIOUS CALM', 'TEMPLE'),
        ('mythica', ['music/mythica.ogg'], 'Mythica', 'congusbongus', 'CC0', 'MYSTERIOUS NEUTRAL', 'MAGIC'),
        ('no_chains', ['music/mvrasseli_nochains.ogg'], 'No Chains', 'mvrasseli', 'CC_BY_3', 'TENSE NEUTRAL', ''),
        ('new_woodlands', ['music/New_Woodlands.ogg'], 'Goodbye to Friends', 'Écrivain', 'CC0', 'SAD CALM', 'FOREST'),
        ('peace_2', ['music/peace2.ogg'], 'Peace II', 'Aasif Chaudry', 'GPL', 'CALM IDYLLIC', 'TOWN'),
        ('peace_3', ['music/peace3.ogg'], 'Peace III', 'Saturn', 'GPL', 'CALM IDYLLIC', 'OPEN_LAND'),
        ('minstrel_harp', ['music/PerituneMaterial_Minstrel2_Harp.ogg'], 'Minstrel 2 (Harfe)', 'Sei Mutsuki (PeriTune)', 'CC_BY_4', 'CALM ROMANTIC', 'TAVERN CASTLE'),
        ('ruins', ['music/Ruins.ogg'], 'Ruins', 'Aphyllix', 'CC_BY_3', 'MYSTERIOUS SAD', 'ANCIENT'),
        ('slaying_shovel', ['music/Slaying_Shovel.ogg'], 'The God-Slaying Shovel', 'Scott Elliott', 'CC_BY_4', 'ACTION TENSE', 'BATTLEFIELD'),
        ('the_fire_dragon', ['music/the_fire_dragon.ogg'], 'The Fire Dragon', 'Umplix', 'CC0', 'ACTION TENSE', 'CAVE'),
        ('birds_in_the_sunrise', ['music/tws_birds_in_the_sunrise.ogg'], 'Birds in the Sunrise', 'Terraws', 'CC_BY_2', 'IDYLLIC HAPPY', 'FOREST OPEN_LAND'),
        ('green_island', ['music/tws_green_island.ogg'], 'Green Island', 'Terraws', 'CC_BY_2', 'IDYLLIC CALM', 'SEA JUNGLE'),
        ('unforgiving_lands', ['music/Unforgiving_Lands.ogg'], 'Unforgiving Lands', 'HorrorPen', 'CC_BY_3', 'TENSE SAD', 'DESERT'),
        ('water_prelude', ['music/water_prelude.ogg'], 'Water Prelude', 'Kevin MacLeod', 'CC_BY_3', 'CALM MYSTERIOUS', 'UNDERWATER SEA'),
        ('woodland_fantasy', ['music/woodland_fantasy.ogg'], 'Woodland Fantasy', 'Matthew Pablo', 'CC_BY_3', 'IDYLLIC HAPPY', 'FOREST'),
        ('tmw_adventure', ['music/tmw_adventure.ogg'], 'Explorers Melody / Faith', 'Jose S. & Dakota D. (Chicka-Maria)', 'GPL', 'HAPPY NEUTRAL', 'OPEN_LAND'),
    ],
    'STENDHAL': [
        ('casket', ['data/music/casket.ogg'], 'Casket', 'Tozan', 'CC0', 'EERIE SAD', 'HAUNTED'),
        ('dungeon_entrance', ['data/music/dungeon_entrance.ogg'], 'Dungeon Entrance', 'Storyteller', 'GPL', 'TENSE MYSTERIOUS', 'DUNGEON'),
        ('medieval_harp', ['data/music/medieval_harp.ogg'], 'Medieval Harp', 'Storyteller', 'GPL', 'CALM ROMANTIC', 'CASTLE TAVERN'),
        ('new_hope', ['data/music/new_hope.ogg'], 'New Hope', 'Storyteller', 'GPL', 'HAPPY NEUTRAL', ''),
        ('up_in_the_sky', ['data/music/up_in_the_sky.ogg'], 'Up in the Sky', 'Memoraphile', 'OGA_BY_3', 'HAPPY IDYLLIC', 'MAGIC OPEN_LAND'),
    ],
    'WESNOTH': [
        ('elf-land', ['data/core/music/elf-land.ogg'], 'Elf Land', 'Aleksi Aubry-Carlson', 'GPL2', 'IDYLLIC MYSTERIOUS', 'FOREST MAGIC'),
        ('cry_from_elensefar', ['data/campaigns/Eastern_Invasion/music/cry_from_elensefar.ogg'], 'Cry from Elensefar', 'pluft', 'CC_BY_SA_4', 'SAD TENSE', 'CASTLE'),
        ('heavens-remix', ['data/campaigns/Eastern_Invasion/music/heavens-remix.ogg'], 'Heavens (Remix)', 'gabriel silver & dalas', 'CC_BY_SA_4', 'TENSE ACTION', 'BATTLEFIELD'),
    ],
    'FLARE': [
        ('forest_theme', ['mods/fantasycore/music/forest_theme.ogg'], 'Forest Theme', 'Brandon Morris', 'CC_BY_SA_3', 'IDYLLIC MYSTERIOUS', 'FOREST'),
    ],
    'VELOREN': [
        ('cave/cavernous_hollow', ['assets/voxygen/audio/soundtrack/cave/cavernous_hollow.ogg'], 'Cavernous Hollow', 'GeekyGami', 'GPL3', 'MYSTERIOUS EERIE', 'CAVE'),
        ('dungeon/a_hearts_ice-cold_protection', ['assets/voxygen/audio/soundtrack/dungeon/a_hearts_ice-cold_protection.ogg'], "A Heart's Ice-Cold Protection", 'GeekyGami', 'GPL3', 'TENSE MYSTERIOUS', 'DUNGEON WINTER'),
        ('dungeon/still_hearts_coffin', ['assets/voxygen/audio/soundtrack/dungeon/still_hearts_coffin.ogg'], "Still Heart's Coffin", 'GeekyGami', 'GPL3', 'EERIE TENSE', 'DUNGEON HAUNTED'),
        ('overworld/ahuyo_del_desierto', ['assets/voxygen/audio/soundtrack/overworld/ahuyo_del_desierto.ogg'], 'Ahuyo del Desierto', 'KHANIBALMUSIC', 'GPL3', 'MYSTERIOUS NEUTRAL', 'DESERT'),
        ('overworld/alma', ['assets/voxygen/audio/soundtrack/overworld/alma.ogg'], 'ALMA', 'KHANIBALMUSIC', 'GPL3', 'CALM IDYLLIC', 'WINTER MOUNTAINS'),
        ('overworld/ecos_del_paraiso', ['assets/voxygen/audio/soundtrack/overworld/ecos_del_paraiso.ogg'], 'Ecos del Paraiso', 'KHANIBALMUSIC', 'GPL3', 'IDYLLIC CALM', 'JUNGLE'),
        ('overworld/jungla_estrellada', ['assets/voxygen/audio/soundtrack/overworld/jungla_estrellada.ogg'], 'Jungla Estrellada', 'KHANIBALMUSIC', 'GPL3', 'MYSTERIOUS CALM', 'JUNGLE'),
        ('overworld/moonlit_canopy', ['assets/voxygen/audio/soundtrack/overworld/moonlit_canopy.ogg'], 'Moonlit Canopy', 'GeekyGami', 'GPL3', 'CALM MYSTERIOUS', 'FOREST'),
        ('overworld/oasis_en_el_desierto', ['assets/voxygen/audio/soundtrack/overworld/oasis_en_el_desierto.ogg'], 'Oasis en el Desierto', 'KHANIBALMUSIC', 'GPL3', 'CALM IDYLLIC', 'DESERT ORIENT'),
        ('overworld/stargazing', ['assets/voxygen/audio/soundtrack/overworld/stargazing.ogg'], 'Stargazing', 'GeekyGami', 'GPL3', 'CALM IDYLLIC', 'OPEN_LAND'),
        ('town/mi_claro_de_luna', ['assets/voxygen/audio/soundtrack/town/mi_claro_de_luna.ogg'], 'Mi Claro de Luna', 'KHANIBALMUSIC', 'GPL3', 'ROMANTIC CALM', 'TOWN SEA'),
    ],
    'INVERTIKA': [
        ('academy', ['music/academy.ogg'], 'Academy', '', 'GPL3', 'CALM NEUTRAL', 'TOWN TEMPLE'),
        ('bells', ['music/bells.ogg'], 'Bells', '', 'GPL3', 'CALM MYSTERIOUS', 'TEMPLE'),
        ('castle', ['music/castle.ogg'], 'Castle', '', 'GPL3', 'NEUTRAL TENSE', 'CASTLE'),
        ('cavesong', ['music/cavesong.ogg'], 'Cave Song', '', 'GPL3', 'MYSTERIOUS EERIE', 'CAVE'),
        ('church-of-invertika', ['music/church-of-invertika.ogg'], 'Church of Invertika', '', 'GPL3', 'CALM SAD', 'TEMPLE'),
        ('darkness', ['music/darkness.ogg'], 'Darkness', '', 'GPL3', 'EERIE TENSE', 'HAUNTED'),
        ('deep-loneliness', ['music/deep-loneliness.ogg'], 'Deep Loneliness', '', 'GPL3', 'SAD CALM', ''),
        ('desert1', ['music/desert1.ogg'], 'Desert I', '', 'GPL3', 'MYSTERIOUS NEUTRAL', 'DESERT'),
        ('desert2', ['music/desert2.ogg'], 'Desert II', '', 'GPL3', 'MYSTERIOUS CALM', 'DESERT ORIENT'),
        ('desert3', ['music/desert3.ogg'], 'Desert III', '', 'GPL3', 'TENSE MYSTERIOUS', 'DESERT'),
        ('dungeon1', ['music/dungeon1.ogg'], 'Dungeon', '', 'GPL3', 'TENSE EERIE', 'DUNGEON'),
        ('epic-darkness', ['music/epic-darkness.ogg'], 'Epic Darkness', '', 'GPL3', 'TENSE EERIE', 'HAUNTED BATTLEFIELD'),
        ('faith', ['music/faith.ogg'], 'Faith', 'The Mana World', 'GPL', 'CALM', 'TEMPLE'),
        ('forest2', ['music/forest2.ogg'], 'Forest II', '', 'GPL3', 'IDYLLIC CALM', 'FOREST'),
        ('forrest1', ['music/forrest1.ogg'], 'Forest I', '', 'GPL3', 'IDYLLIC MYSTERIOUS', 'FOREST'),
        ('graveyard', ['music/graveyard.ogg'], 'Graveyard', 'Feline Monstrosity', 'GPL', 'EERIE SAD', 'HAUNTED'),
        ('guitar', ['music/guitar.ogg'], 'Guitar', '', 'GPL3', 'CALM ROMANTIC', 'TAVERN HOME'),
        ('happy', ['music/happy.ogg'], 'Happy', '', 'GPL3', 'HAPPY', 'TOWN'),
        ('hurnscald', ['music/hurnscald.ogg'], 'Hurnscald', 'Aasif Chaudry', 'GPL', 'HAPPY IDYLLIC', 'TOWN'),
        ('in-the-cave', ['music/in-the-cave.ogg'], 'In the Cave', '', 'GPL3', 'MYSTERIOUS EERIE', 'CAVE'),
        ('inside', ['music/inside.ogg'], 'Inside', '', 'GPL3', 'CALM', 'HOME'),
        ('inside2', ['music/inside2.ogg'], 'Inside II', '', 'GPL3', 'CALM NEUTRAL', 'HOME'),
        ('long-journey', ['music/long-journey.ogg'], 'Long Journey', '', 'GPL3', 'NEUTRAL SAD', 'OPEN_LAND'),
        ('madness', ['music/madness.ogg'], 'Madness', '', 'GPL3', 'TENSE EERIE', 'HAUNTED'),
        ('mountain', ['music/mountain.ogg'], 'Mountain', '', 'GPL3', 'CALM MYSTERIOUS', 'MOUNTAINS'),
        ('mystery-cave', ['music/mystery-cave.ogg'], 'Mystery Cave', '', 'GPL3', 'MYSTERIOUS EERIE', 'CAVE'),
        ('mystery-forrest', ['music/mystery-forrest.ogg'], 'Mystery Forest', '', 'GPL3', 'MYSTERIOUS', 'FOREST MAGIC'),
        ('nex', ['music/nex.ogg'], 'Nex', '', 'GPL3', 'NEUTRAL MYSTERIOUS', ''),
        ('night', ['music/night.ogg'], 'Night', '', 'GPL3', 'CALM MYSTERIOUS', ''),
        ('ocarina-overture', ['music/ocarina-overture.ogg'], 'Ocarina Overture', '', 'GPL3', 'HAPPY IDYLLIC', 'OPEN_LAND'),
        ('ocarina', ['music/ocarina.ogg'], 'Ocarina', '', 'GPL3', 'IDYLLIC CALM', 'OPEN_LAND FOREST'),
        ('port', ['music/port.ogg'], 'Port', '', 'GPL3', 'NEUTRAL HAPPY', 'SEA TOWN'),
        ('powerful-time', ['music/powerful-time.ogg'], 'Powerful Time', '', 'GPL3', 'ACTION HAPPY', 'BATTLEFIELD'),
        ('ready-for-battle', ['music/ready-for-battle.ogg'], 'Ready for Battle', '', 'GPL3', 'ACTION TENSE', 'BATTLEFIELD'),
        ('royally', ['music/royally.ogg'], 'Royally', '', 'GPL3', 'NEUTRAL HAPPY', 'CASTLE'),
        ('selphi-timlet', ['music/selphi-timlet.ogg'], 'Selphi Timlet', '', 'GPL3', 'HAPPY IDYLLIC', 'TOWN'),
        ('still-emotions', ['music/still-emotions.ogg'], 'Still Emotions', '', 'GPL3', 'SAD CALM', ''),
        ('strings', ['music/strings.ogg'], 'Strings', '', 'GPL3', 'CALM ROMANTIC', ''),
        ('terrific', ['music/terrific.ogg'], 'Terrific', '', 'GPL3', 'ACTION TENSE', 'BATTLEFIELD'),
        ('theme-orchestral', ['music/theme-orchestral.ogg'], 'Orchestral Theme', '', 'GPL3', 'NEUTRAL TENSE', ''),
        ('threat', ['music/threat.ogg'], 'Threat', '', 'GPL3', 'TENSE EERIE', ''),
        ('town', ['music/town.ogg'], 'Town', '', 'GPL3', 'HAPPY', 'TOWN'),
        ('uncanny', ['music/uncanny.ogg'], 'Uncanny', '', 'GPL3', 'EERIE MYSTERIOUS', 'HAUNTED'),
    ],
}

# Composers named in the files' own tags (Invertika lists its musicians only as a group).
TAG_ARTISTS = {'saturn': 'Nikola Whallon (saturn)', 'Saturn': 'Nikola Whallon (saturn)', 'Nelson Gatlin': 'Nelson James Gatlin',
               'saturn & zartersound': 'Nikola Whallon (saturn) & zartersound'}

COMPOSER_FALLBACK = {'DUELYST': 'Counterplay Games', 'INVERTIKA': 'Invertika-Team'}

# Widelands pieces not in the first library: composer and title from the files' tags.
WIDELANDS_MORE = {
    'ingame_14': ('NEUTRAL CALM', 'OPEN_LAND'), 'ingame_15': ('CALM IDYLLIC', 'FOREST'), 'ingame_16': ('NEUTRAL', 'TOWN'),
    'ingame_17': ('TENSE NEUTRAL', ''), 'ingame_21': ('MYSTERIOUS', 'DESERT'), 'ingame_28': ('NEUTRAL CALM', ''),
    'intro': ('NEUTRAL HAPPY', ''), 'menu': ('CALM NEUTRAL', ''),
}


def url(coll, path):
    import urllib.parse
    repo, sha, pattern = SOURCES[coll]
    return pattern.format(repo=repo, sha=sha, path=urllib.parse.quote(path))


def candidates():
    """(file id, url) for every audio file of the extension."""
    out = []
    for coll, entries in MORE.items():
        for key, files, *_ in entries:
            for i, path in enumerate(files):
                fid = f'{PREFIX[coll]}/{key}' + (f'#{i}' if len(files) > 1 else '')
                out.append((fid, url(coll, path)))
    for key in WIDELANDS_MORE:
        out.append((f'widelands/{key}', url('WIDELANDS', f'data/music/{key}.ogg')))
    return out


if __name__ == '__main__':
    for fid, u in candidates():
        print(fid, u)
