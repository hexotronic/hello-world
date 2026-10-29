"""Generates FantasyCatalog.kt from analysis.jsonl (see measure.sh) and the hand-made table below.

Usage: python3 catalog.py analysis.jsonl > ../../app/src/main/java/io/github/hexotronic/lauscher/music/library/core/FantasyCatalog.kt
"""
import json, sys
A = {json.loads(l)['id']: json.loads(l) for l in open(sys.argv[1])}
W, WL, FL = 'WESNOTH', 'WIDELANDS', 'FLARE'
GPL, BYSA3, BYSA4, BY4 = 'GPL2', 'CC_BY_SA_3', 'CC_BY_SA_4', 'CC_BY_4'
# id, title, composer, year (None = unknown), licence, collection, moods (best fitting first)
T = [
 ('wesnoth/battle-epic', 'Battle Epic', 'Doug Kaufman', 2007, GPL, W, 'ACTION TENSE'),
 ('wesnoth/battle', 'Battle Music', 'Aleksi Aubry-Carlson', 2006, GPL, W, 'ACTION TENSE'),
 ('wesnoth/breaking_the_chains', 'Breaking the Chains', 'Mattias Westlund', 2007, GPL, W, 'ACTION NEUTRAL'),
 ('wesnoth/casualties_of_war', 'Casualties of War', 'Tyler Johnson', 2009, GPL, W, 'TENSE SAD'),
 ('wesnoth/elvish-theme', 'Elvish Theme', 'Doug Kaufman', 2007, GPL, W, 'MYSTERIOUS IDYLLIC CALM'),
 ('wesnoth/frantic-old', 'Frantic (2004)', 'Aleksi Aubry-Carlson', 2004, GPL, W, 'TENSE ACTION'),
 ('wesnoth/frantic', 'Frantic', 'Stephen Rozanc', 2012, GPL, W, 'ACTION TENSE'),
 ('wesnoth/heroes_rite', 'Heroes Rite', 'Doug Kaufman', 2008, GPL, W, 'NEUTRAL CALM HAPPY'),
 ('wesnoth/into_the_shadows', 'Into the Shadows', 'Tyler Johnson', 2009, GPL, W, 'MYSTERIOUS TENSE EERIE'),
 ('wesnoth/journeys_end', "Journey's End", 'Mattias Westlund', 2009, GPL, W, 'CALM SAD NEUTRAL'),
 ('wesnoth/knalgan_theme', 'Knalgan Theme', 'Ryan Reilly', 2008, GPL, W, 'HAPPY NEUTRAL'),
 ('wesnoth/knolls', 'The Knolls of Doldesh', 'Timothy Pinkham', 2006, GPL, W, 'IDYLLIC CALM'),
 ('wesnoth/legends_of_the_north', 'Legends of the North', 'Mattias Westlund', 2007, GPL, W, 'ACTION TENSE'),
 ('wesnoth/love_theme', 'Love Theme', 'Ryan Reilly', 2008, GPL, W, 'ROMANTIC SAD'),
 ('wesnoth/loyalists', 'Loyalists', 'Joseph G. Toscano', 2004, GPL, W, 'ACTION HAPPY'),
 ('wesnoth/main_menu', 'Main Theme', 'Aleksi Aubry-Carlson', 2005, GPL, W, 'NEUTRAL HAPPY'),
 ('wesnoth/northern_mountains', 'Over the Northern Mountains', 'Mattias Westlund', 2008, GPL, W, 'NEUTRAL IDYLLIC'),
 ('wesnoth/northerners', 'Northerners', 'Stephen Rozanc', 2010, GPL, W, 'TENSE ACTION'),
 ('wesnoth/nunc_dimittis', 'Nunc Dimittis', 'Jeremy Nicoll', 2008, GPL, W, 'SAD CALM'),
 ('wesnoth/return_to_wesnoth', 'Return to Wesnoth', 'Mattias Westlund', 2017, BYSA4, W, 'HAPPY NEUTRAL'),
 ('wesnoth/revelation', 'Revelation', 'Joseph G. Toscano', 2004, GPL, W, 'MYSTERIOUS EERIE'),
 ('wesnoth/sad', 'Sad', 'Tyler Johnson', 2010, GPL, W, 'SAD'),
 ('wesnoth/siege_of_laurelmor', 'Siege of Laurelmor', 'Doug Kaufman', 2009, GPL, W, 'ACTION TENSE'),
 ('wesnoth/silvan_sanctuary', 'Silvan Sanctuary', 'Mattias Westlund', 2010, GPL, W, 'IDYLLIC CALM MYSTERIOUS'),
 ('wesnoth/suspense', 'Suspense', 'Ryan Reilly', 2008, GPL, W, 'TENSE EERIE MYSTERIOUS'),
 ('wesnoth/the_city_falls', 'The City Falls', 'Doug Kaufman', 2007, GPL, W, 'ACTION SAD'),
 ('wesnoth/the_dangerous_symphony', 'The Dangerous Symphony', 'Gianmarco Leone', 2008, GPL, W, 'TENSE ACTION'),
 ('wesnoth/the_deep_path', 'The Deep Path', 'Gianmarco Leone', 2008, GPL, W, 'MYSTERIOUS EERIE'),
 ('wesnoth/the_king_is_dead', 'The King is Dead', 'Mattias Westlund', 2008, GPL, W, 'SAD TENSE'),
 ('wesnoth/transience', 'Transience', 'Aleksi Aubry-Carlson', 2004, GPL, W, 'CALM SAD'),
 ('wesnoth/traveling_minstrels', 'Traveling Minstrels', 'Mattias Westlund', 2008, GPL, W, 'HAPPY IDYLLIC'),
 ('wesnoth/underground', 'Underground', 'Aleksi Aubry-Carlson', 2004, GPL, W, 'EERIE MYSTERIOUS'),
 ('wesnoth/vengeful', 'Vengeful Pursuit', 'Jeremy Nicoll', 2007, GPL, W, 'ACTION TENSE'),
 ('wesnoth/wanderer', 'Still Another Wanderer', 'Timothy Pinkham', 2006, GPL, W, 'CALM NEUTRAL IDYLLIC'),
 ('wesnoth/weight_of_revenge', 'Weight of Revenge', 'Doug Kaufman', 2010, GPL, W, 'TENSE SAD'),
 ('widelands/ingame_00', 'Recorder 2', 'Nikola Whallon', None, GPL, WL, 'IDYLLIC HAPPY'),
 ('widelands/ingame_01', 'Hard Time for Workers', 'Jan Bruns', 2009, GPL, WL, 'NEUTRAL SAD'),
 ('widelands/ingame_02', 'Mountainsketch', 'Nikola Whallon', None, GPL, WL, 'CALM IDYLLIC'),
 ('widelands/ingame_03', 'Tranquil Forest', 'Nikola Whallon', None, GPL, WL, 'CALM IDYLLIC'),
 ('widelands/ingame_04', 'Desertsketch 3', 'Nikola Whallon', None, GPL, WL, 'MYSTERIOUS NEUTRAL'),
 ('widelands/ingame_05', 'Widelands 1', 'Barry van Oudtshoorn', None, GPL, WL, 'NEUTRAL CALM'),
 ('widelands/ingame_06', 'Widelands 3', 'Barry van Oudtshoorn', None, GPL, WL, 'NEUTRAL'),
 ('widelands/ingame_07', 'Widelands 6', 'Barry van Oudtshoorn', None, GPL, WL, 'CALM SAD'),
 ('widelands/ingame_08', 'To Be in a Rut', 'Jan Bruns', 2009, GPL, WL, 'SAD CALM'),
 ('widelands/ingame_09', 'Renaissance Village', 'Nikola Whallon', None, GPL, WL, 'HAPPY IDYLLIC'),
 ('widelands/ingame_10', 'Desertsketch 2', 'Nikola Whallon', None, GPL, WL, 'MYSTERIOUS'),
 ('widelands/ingame_11', 'Desertsketch 1', 'Nikola Whallon', None, GPL, WL, 'MYSTERIOUS'),
 ('widelands/ingame_12', 'Between Peace and War', 'Jan Bruns', 2009, GPL, WL, 'TENSE NEUTRAL'),
 ('widelands/ingame_13', 'Castlesketch', 'Nikola Whallon', None, GPL, WL, 'NEUTRAL CALM'),
 ('widelands/ingame_18', 'Orchestral Sketch', 'Nikola Whallon', None, GPL, WL, 'NEUTRAL'),
 ('widelands/ingame_19', 'Widelands Tension 1', 'Nikola Whallon', None, GPL, WL, 'TENSE EERIE'),
 ('widelands/ingame_20', 'Widelands Tension 2', 'Nikola Whallon', None, GPL, WL, 'TENSE'),
 ('widelands/ingame_22', 'From Scotland to Rome', "Joshua O'Leary", 2011, GPL, WL, 'IDYLLIC HAPPY'),
 ('widelands/ingame_23', 'Silkweaver Song', 'Klaus Halfmann', None, GPL, WL, 'CALM ROMANTIC'),
 ('widelands/ingame_24', "Hypatia's Theme", 'Stuart Marshall', 2017, BYSA3, WL, 'ROMANTIC CALM MYSTERIOUS'),
 ('widelands/ingame_25', 'We Work in the Vineyards', 'Stuart Marshall', 2017, BYSA3, WL, 'IDYLLIC HAPPY'),
 ('widelands/ingame_26', 'Running Out of Coal', 'Stuart Marshall', None, BYSA3, WL, 'TENSE ACTION'),
 ('widelands/ingame_27', 'Scotty the Scout', 'Stuart Marshall', None, BYSA3, WL, 'HAPPY'),
 ('widelands/ingame_29', 'Teeming Waltz', 'Jan F. Bruns', None, BY4, WL, 'HAPPY ROMANTIC'),
 ('flare/battle_theme', 'Battle Theme', 'Brandon Morris', 2012, BYSA3, FL, 'ACTION TENSE'),
 ('flare/boss_theme', 'Boss Theme', 'Brandon Morris', 2012, BYSA3, FL, 'ACTION TENSE'),
 ('flare/cave_theme', 'Cave Theme', 'Brandon Morris', 2012, BYSA3, FL, 'EERIE MYSTERIOUS'),
 ('flare/dungeon_theme', 'Dungeon Theme', 'Brandon Morris', 2012, BYSA3, FL, 'EERIE TENSE'),
 ('flare/magical_theme', 'Magical Theme', 'Brandon Morris', 2012, BYSA3, FL, 'MYSTERIOUS CALM'),
 ('flare/overworld_theme', 'Overworld Theme', 'Brandon Morris', 2012, BYSA3, FL, 'NEUTRAL IDYLLIC HAPPY'),
 ('flare/safe_room_theme', 'Safe Room Theme', 'Brandon Morris', 2012, BYSA3, FL, 'CALM ROMANTIC'),
 ('flare/title_theme', 'Title Theme', 'Brandon Morris', 2012, BYSA3, FL, 'NEUTRAL MYSTERIOUS'),
 ('flare/town_theme', 'Town Theme', 'Brandon Morris', 2012, BYSA3, FL, 'HAPPY IDYLLIC'),
 ('flare/unrest_theme', 'Unrest Theme', 'Brandon Morris', 2012, BYSA3, FL, 'TENSE EERIE'),
]
PRIOR = dict(ACTION=1, TENSE=.75, HAPPY=.65, NEUTRAL=.5, MYSTERIOUS=.4, EERIE=.4, ROMANTIC=.35, IDYLLIC=.35, SAD=.25, CALM=.2)
clamp = lambda x: max(0.0, min(1.0, x))
def kstr(s): return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$') + '"'
out = []
from collections import Counter
prim, anym = Counter(), Counter()
total = 0
for tid, title, comp, year, lic, coll, moods in T:
    a = A[tid]; f = a['f']
    moods = moods.split()
    energy = 0.45 * clamp((f['onsets_per_s'] - 0.4) / 2.6) + 0.2 * clamp((f['centroid'] - 300) / 1800) + 0.35 * PRIOR[moods[0]]
    gain = max(-8.0, min(15.0, -20.0 - f['loud_db']))
    prim[moods[0]] += 1
    for m in moods: anym[m] += 1
    total += a['size']
    out.append(f'        t({kstr(tid)}, {kstr(title)}, {kstr(comp)}, {year if year else "null"}, {lic}, {coll}, {kstr(a["url"])}, '
               f'{a["size"]}, {kstr(a["sha256"])}, {round(f["dur"])}, listOf({", ".join(moods)}), {energy:.2f}f, {gain:.1f}f),')
print('// tracks', len(T), 'bytes', total, file=sys.stderr)
print('// primary', dict(prim), file=sys.stderr)
print('// any', dict(anym), file=sys.stderr)
src = '''package io.github.hexotronic.lauscher.music.library.core

import io.github.hexotronic.lauscher.music.library.core.MusicCollection.FLARE
import io.github.hexotronic.lauscher.music.library.core.MusicCollection.WESNOTH
import io.github.hexotronic.lauscher.music.library.core.MusicCollection.WIDELANDS
import io.github.hexotronic.lauscher.music.library.core.MusicLicense.CC_BY_4
import io.github.hexotronic.lauscher.music.library.core.MusicLicense.CC_BY_SA_3
import io.github.hexotronic.lauscher.music.library.core.MusicLicense.CC_BY_SA_4
import io.github.hexotronic.lauscher.music.library.core.MusicLicense.GPL2
import io.github.hexotronic.lauscher.music.synth.Mood.ACTION
import io.github.hexotronic.lauscher.music.synth.Mood.CALM
import io.github.hexotronic.lauscher.music.synth.Mood.EERIE
import io.github.hexotronic.lauscher.music.synth.Mood.HAPPY
import io.github.hexotronic.lauscher.music.synth.Mood.IDYLLIC
import io.github.hexotronic.lauscher.music.synth.Mood.MYSTERIOUS
import io.github.hexotronic.lauscher.music.synth.Mood.NEUTRAL
import io.github.hexotronic.lauscher.music.synth.Mood.ROMANTIC
import io.github.hexotronic.lauscher.music.synth.Mood.SAD
import io.github.hexotronic.lauscher.music.synth.Mood.TENSE

/**
 * The fantasy music library. Composers, years and licences come from the games' own
 * credits (Wesnoth: copyrights.csv and file tags, Widelands: data/music/LICENSE.txt and file
 * tags, Flare: CREDITS.txt and LICENSE.txt). Sizes, checksums, durations, energy and level
 * were measured from the files; the moods were assigned by hand from titles, the composers'
 * descriptions and the measurements.
 *
 * Generated by hoerbuch-player/tools/music-catalog.py – edit the table there.
 */
internal object FantasyCatalog {
    private fun t(
        id: String, title: String, composer: String, year: Int?, license: MusicLicense, collection: MusicCollection,
        url: String, bytes: Long, sha256: String, seconds: Int, moods: List<io.github.hexotronic.lauscher.music.synth.Mood>,
        energy: Float, gainDb: Float,
    ) = LibraryTrack(id, title, composer, year, license, collection, url, bytes, sha256, seconds, moods, energy, gainDb)

    val tracks = listOf(
''' + '\n'.join(out) + '''
    )

    val byId = tracks.associateBy { it.id }
}
'''
print(src)
