"""Generates FantasyCatalog.kt, the music library of the app.

Inputs: analysis.jsonl, analysis-themes.jsonl and analysis-more.jsonl (measured by measure.sh from
candidates.txt, candidates-themes.txt and candidates-more.txt, the last made by catalog_more.py), veloren-soundtrack.json and supertux-music.txt (the games' own metadata) and the
hand-made tables in catalog_tables.py: who composed each piece, under which licence (from the games'
credits), and which moods and places it fits (assigned by hand from titles, the games' descriptions and
the measurements). catalog_more.py holds the third extension (more fantasy games).
Not every measured piece is used: SuperTux, Hedgewars and Pioneer (cartoon, modern city and space music)
were dropped from the library because they do not fit fantasy audiobooks.

Usage: python3 catalog.py > ../../app/src/main/java/io/github/hexotronic/lauscher/music/library/core/FantasyCatalog.kt
"""
import json
import math
import os
import re
import sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from catalog_tables import (T, OLD_PLACES, VELOREN, VELOREN_COMBAT, SPD_AREAS, SPD_KINDS,  # noqa: E402
                            STENDHAL, ZERO_AD)
import catalog_more  # noqa: E402

A = {}
for name in ('analysis.jsonl', 'analysis-themes.jsonl', 'analysis-more.jsonl'):
    for line in open(os.path.join(HERE, name)):
        d = json.loads(line)
        if not d.get('error'):
            A[d['id']] = d

PRIOR = dict(ACTION=1, TENSE=.75, HAPPY=.65, NEUTRAL=.5, MYSTERIOUS=.4, EERIE=.4, ROMANTIC=.35, IDYLLIC=.35, SAD=.25, CALM=.2)


def clamp(x):
    return max(0.0, min(1.0, x))


def kstr(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$') + '"'


def energy(f, moods):
    return 0.45 * clamp((f['onsets_per_s'] - 0.4) / 2.6) + 0.2 * clamp((f['centroid'] - 300) / 1800) + 0.35 * PRIOR[moods[0]]


def gain(f):
    return max(-8.0, min(15.0, -20.0 - f['loud_db']))


def whole_loop(dur, target):
    """Repeats a looping file until it fills about [target] seconds."""
    r = max(1, min(4, math.ceil(target / dur)))
    return [(0, 0.0, -1.0, r)], dur * r


TRACKS = []
SINGLE = [(0, 0.0, -1.0, 1)]


def add(id, title, composer, year, lic, coll, files, moods, places, plan=None, seconds=None, feat=None):
    moods = moods.split()
    f = feat or A[files[0]]['f']
    TRACKS.append(dict(id=id, title=title, composer=composer, year=year, lic=lic, coll=coll,
                       files=[A[x] for x in files], moods=moods, places=places.split(), energy=energy(f, moods),
                       gain=gain(f), plan=plan or SINGLE, seconds=seconds or A[files[0]]['f']['dur']))


for tid, title, comp, year, lic, coll, moods in T:
    add(tid, title, comp, year, lic, coll, [tid], moods, OLD_PLACES[tid])

meta = {m['path'].split('.')[-1]: m for m in json.load(open(os.path.join(HERE, 'veloren-soundtrack.json')))}
for key, (moods, places) in VELOREN.items():
    fid = next(i for i in A if i.startswith('veloren/') and i.endswith('/' + key))
    m = meta[key]
    artist = re.split(r';| mixed by', m['artist'])[0].strip()
    add(fid, m['title'], artist, None, 'GPL3', 'VELOREN', [fid], moods, places)
for key, (title, artist, moods, places) in VELOREN_COMBAT.items():
    parts = [f'veloren/combat/{key}/{key}-{p}' for p in ('start', 'loop', 'end')]
    durs = [A[p]['f']['dur'] for p in parts]
    add(f'veloren/combat/{key}', title, artist, None, 'GPL3', 'VELOREN', parts, moods, places,
        plan=[(0, 0.0, -1.0, 1), (1, 0.0, -1.0, 2), (2, 0.0, -1.0, 1)], seconds=durs[0] + 2 * durs[1] + durs[2],
        feat=A[parts[1]]['f'])

for area, (area_title, places) in SPD_AREAS.items():
    for kind, (kind_title, moods) in SPD_KINDS.items():
        fid = f'spd/{area}_{kind}'
        if fid not in A:
            continue
        plan, secs = whole_loop(A[fid]['f']['dur'], 150)
        add(fid, f'{area_title} – {kind_title}', 'Lumine Haaristo', None, 'GPL3', 'SHATTERED_PD', [fid], moods, places, plan, secs)
for fid, title, moods, places in (('spd/theme_1', 'Titelthema I', 'NEUTRAL MYSTERIOUS', 'DUNGEON'),
                                  ('spd/theme_2', 'Titelthema II', 'MYSTERIOUS CALM', 'DUNGEON'),
                                  ('spd/theme_finale', 'Finale', 'HAPPY NEUTRAL', '')):
    plan, secs = whole_loop(A[fid]['f']['dur'], 150)
    add(fid, title, 'Lumine Haaristo', None, 'GPL3', 'SHATTERED_PD', [fid], moods, places, plan, secs)

for key, (title, composer, lic, moods, places) in STENDHAL.items():
    fid = 'stendhal/' + key
    plan, secs = whole_loop(A[fid]['f']['dur'], 120)
    add(fid, title, composer, None, lic, 'STENDHAL', [fid], moods, places, plan, secs)

for key, (moods, places) in ZERO_AD.items():
    fid = '0ad/' + key
    plan, secs = whole_loop(A[fid]['f']['dur'], 90)
    add(fid, key.replace('_', ' '), 'Wildfire Games (Omri Lahav u. a.)', None, 'CC_BY_SA_3', 'ZERO_AD', [fid], moods, places, plan, secs)

# Third extension: more fantasy games (see catalog_more.py).
SKIP_MORE = {'tuxemon/good_morning', 'tuxemon/good_night', 'megaglest/intro'}
for coll, entries in catalog_more.MORE.items():
    prefix = catalog_more.PREFIX[coll]
    for key, files, title, composer, lic, moods, places in entries:
        tid = f'{prefix}/{key}'
        if tid in SKIP_MORE:
            continue
        if not composer:
            tag = (A.get(tid) or A.get(f'{tid}#0'))['tags'].get('artist', '').strip()
            composer = catalog_more.TAG_ARTISTS.get(tag, tag) or catalog_more.COMPOSER_FALLBACK[coll]
        if len(files) > 1:
            parts = [f'{tid}#{i}' for i in range(len(files))]
            durs = [A[p]['f']['dur'] for p in parts]
            plan = [(i, 0.0, -1.0, 1) for i in range(len(parts))]
            add(tid, title, composer, None, lic, coll, parts, moods, places, plan=plan, seconds=sum(durs), feat=A[parts[0]]['f'])
        else:
            dur = A[tid]['f']['dur']
            plan, secs = whole_loop(dur, 120) if dur < 60 else (SINGLE, dur)
            add(tid, title, composer, None, lic, coll, [tid], moods, places, plan, secs)
WIDELANDS_COMPOSERS = {'ingame_15': 'MiddleFinger', 'ingame_16': 'MiddleFinger', 'ingame_17': 'MiddleFinger',
                       'ingame_28': 'Klaus Halfmann (Hasi50)'}
WIDELANDS_TITLES = {'ingame_28': 'The Beauty of the Flat Lands', 'intro': 'Intro'}
for key, (moods, places) in catalog_more.WIDELANDS_MORE.items():
    if key == 'menu':
        continue  # the same file as ingame_14
    tid = f'widelands/{key}'
    dur = A[tid]['f']['dur']
    plan, secs = whole_loop(dur, 120) if dur < 60 else (SINGLE, dur)
    add(tid, WIDELANDS_TITLES.get(key, 'Widelands ' + key.replace('ingame_', '')), WIDELANDS_COMPOSERS.get(key, 'Widelands-Team'),
        None, 'GPL2', 'WIDELANDS', [tid], moods, places, plan, secs)

# ---------------------------------------------------------------- checks and output
ids = [t['id'] for t in TRACKS]
assert len(ids) == len(set(ids)), 'duplicate ids'
shas = [f['sha256'] for t in TRACKS for f in t['files']]
assert len(shas) == len(set(shas)), 'the same file twice: ' + str([x for x, n in Counter(shas).items() if n > 1])
assert not [t['id'] for t in TRACKS if t['seconds'] < 40], 'too short'


def seg(s):
    f, a, b, r = s
    args = []
    if f:
        args.append(f'file = {f}')
    if a:
        args.append(f'fromSec = {a}f')
    if b >= 0:
        args.append(f'toSec = {b}f')
    if r != 1:
        args.append(f'repeat = {r}')
    return 'Segment(' + ', '.join(args) + ')'


def entry(t):
    files = ', '.join(f'f({kstr(x["url"])}, {x["size"]}, {kstr(x["sha256"])})' for x in t['files'])
    moods = ', '.join('M.' + m for m in t['moods'])
    places = ', '.join('S.' + p for p in t['places'])
    plan = '' if t['plan'] == SINGLE else ', listOf(' + ', '.join(seg(s) for s in t['plan']) + ')'
    year = t['year'] if t['year'] else 'null'
    return (f'        t({kstr(t["id"])}, {kstr(t["title"])}, {kstr(t["composer"])}, {year}, L.{t["lic"]}, C.{t["coll"]}, '
            f'listOf({files}), {round(t["seconds"])}, listOf({moods}), listOf({places}), {t["energy"]:.2f}f, {t["gain"]:.1f}f{plan}),')


print(f'// {len(TRACKS)} tracks, {sum(sum(x["size"] for x in t["files"]) for t in TRACKS) / 1e6:.0f} MB, '
      f'{sum(t["seconds"] for t in TRACKS) / 3600:.1f} h', file=sys.stderr)
print('// moods', dict(Counter(m for t in TRACKS for m in t['moods'])), file=sys.stderr)
print('// places', dict(Counter(p for t in TRACKS for p in t['places'])), file=sys.stderr)

HEADER = '''package io.github.hexotronic.lauscher.music.library.core

import io.github.hexotronic.lauscher.music.library.core.MusicCollection as C
import io.github.hexotronic.lauscher.music.library.core.MusicLicense as L
import io.github.hexotronic.lauscher.music.synth.Mood as M
import io.github.hexotronic.lauscher.music.synth.Setting as S

/**
 * The music library. Composers and licences come from the games' own credits; sizes, checksums,
 * durations, energy and level were measured from the files; moods and places were assigned by hand.
 *
 * Generated by hoerbuch-player/tools/music/catalog.py – edit the tables there.
 */
internal object FantasyCatalog {
    private fun f(url: String, bytes: Long, sha256: String) = TrackFile(url, bytes, sha256)

    private fun t(
        id: String, title: String, composer: String, year: Int?, license: L, collection: C, files: List<TrackFile>,
        seconds: Int, moods: List<M>, settings: List<S>, energy: Float, gainDb: Float, plan: List<Segment> = listOf(Segment()),
    ) = LibraryTrack(id, title, composer, year, license, collection, files, seconds, moods, settings, energy, gainDb, plan)
'''

# Several small functions keep each one far below the JVM's method size limit.
CHUNK = 40
chunks = [TRACKS[i:i + CHUNK] for i in range(0, len(TRACKS), CHUNK)]
out = [HEADER]
for i, c in enumerate(chunks):
    out.append(f'\n    private fun part{i}() = listOf(\n' + '\n'.join(entry(t) for t in c) + '\n    )\n')
out.append('\n    val tracks: List<LibraryTrack> = ' + ' + '.join(f'part{i}()' for i in range(len(chunks))) +
           '\n\n    val byId = tracks.associateBy { it.id }\n}\n')
sys.stdout.write(''.join(out))
