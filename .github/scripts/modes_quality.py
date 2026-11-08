"""Judges the MP3s of the conversion-mode test with ViSQOL, an objective listening model.

usage: modes_quality.py <source audio> <log> <mp3 dir>
Prints a table (time, speed, LAME setting, ViSQOL score 1-5 against the source) and exits with
1 if a file is broken or the wrong LAME setting was used. The test sound has music and noise
under the voice, where "Schnell" is expected to score lower (locally: about -0.6 at 64 kbit/s,
-0.2 at 128 kbit/s); a GitHub warning only when it is far beyond that or not faster.
"""
import re
import subprocess
import sys
import tempfile

import numpy as np
import soundfile as sf
from visqol import VisqolApi

SECONDS = 120  # judged part of each file (the whole one takes long in pure Python)
source, log, mp3dir = sys.argv[1:4]


def decode(path):
    pcm = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-t", str(SECONDS + 1), "-f", "s16le", "-ac", "1", "-ar", "48000", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(pcm, dtype="<i2").astype(np.float64) / 32768


ref = decode(source)[: SECONDS * 48000]
api = VisqolApi()
api.create(mode="audio")
lines = open(log, encoding="utf-8", errors="replace").read().splitlines()
rows, failed, scores, times = [], 0, {}, {}
for quality, kbps in (("speech", 64), ("standard", 128)):
    for mode in ("balanced", "fast"):
        name = f"modes-{quality}-{mode}"
        line = next((l for l in lines if "Umgewandelt: " in l and f"{name}.mp3 –" in l), "")
        m = re.search(r"– (\d+):(\d+) in ([0-9.]+) s.*\[LAME -q (\d)\]", line)
        if not m:
            print(f"::error::Keine Umwandlung von {name} im Log")
            failed += 1
            continue
        audio_s, took, lame = int(m.group(1)) * 60 + int(m.group(2)), float(m.group(3)), int(m.group(4))
        expected = 7 if mode == "fast" else 5
        if lame != expected:
            print(f"::error::{name} wurde mit LAME -q {lame} statt -q {expected} umgewandelt")
            failed += 1
        errors = subprocess.run(["ffmpeg", "-v", "error", "-i", f"{mp3dir}/{name}.mp3", "-f", "null", "-"], capture_output=True, text=True).stderr
        deg = decode(f"{mp3dir}/{name}.mp3")
        # The MP3 starts later (encoder delay, and the phone's AAC decoder may keep the priming).
        n = 48000 * 5
        lag = max(range(0, 6000), key=lambda k: float(np.dot(ref[48000:48000 + n], deg[48000 + k:48000 + k + n])))
        deg = deg[lag:lag + len(ref)]
        r = ref[: len(deg)]
        with tempfile.TemporaryDirectory() as tmp:
            sf.write(f"{tmp}/r.wav", r, 48000, subtype="PCM_16")
            sf.write(f"{tmp}/d.wav", deg, 48000, subtype="PCM_16")
            score = api.measure(f"{tmp}/r.wav", f"{tmp}/d.wav").moslqo
        scores[(kbps, mode)], times[(kbps, mode)] = score, took
        label = "Ausgewogen" if mode == "balanced" else "Schnell"
        rows.append(f"{kbps:>3} kbit/s {label:10s}  {took:5.1f} s  {audio_s / took:5.0f}× Echtzeit  LAME -q {lame}  ViSQOL {score:.2f}")
        if errors.strip() or score < 3.0:
            print(f"::error::{name}.mp3 ist fehlerhaft (ViSQOL {score:.2f}) {errors.strip()[:200]}")
            failed += 1
print("Bitrate    Modus        Zeit       Tempo          LAME    Hörmodell (1-5)")
print("\n".join(rows))
for kbps in (64, 128):
    if (kbps, "fast") in times and (kbps, "balanced") in times:
        if times[(kbps, "fast")] >= times[(kbps, "balanced")]:
            print(f"::warning::„Schnell“ war bei {kbps} kbit/s nicht schneller als „Ausgewogen“")
        diff = scores[(kbps, "fast")] - scores[(kbps, "balanced")]
        print(f"{kbps} kbit/s: „Schnell“ {times[(kbps, 'balanced')] / times[(kbps, 'fast')]:.2f}× so schnell, Hörmodell {diff:+.2f}")
        if diff < -1.0:
            print(f"::warning::„Schnell“ klingt bei {kbps} kbit/s laut Hörmodell viel schlechter als erwartet")
sys.exit(1 if failed else 0)
