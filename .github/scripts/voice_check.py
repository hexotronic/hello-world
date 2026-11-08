"""Checks the voice command test: which spoken commands (espeak-ng) were recognised and carried out.

usage: voice_check.py <log of LauscherVoice>
Exits with 1 if the speech detector did not cut out all utterances, fewer than 5 of the 6
commands were carried out in order, or the sentence without the wake word triggered something.
"""
import re
import sys

EXPECTED = ["PAUSE", "PLAY", "REWIND", "NEXT_CHAPTER", "WHERE", "PAUSE"]
lines = open(sys.argv[1], encoding="utf-8", errors="replace").read().splitlines()
heard = [l.split("LauscherVoice: ", 1)[-1] for l in lines if "Gehört (" in l]
done = [m.group(1) for l in lines for m in [re.search(r"Ausgeführt: (\w+)", l)] if m]
count = next((int(m.group(1)) for l in lines for m in [re.search(r"Sprachbefehl-Test: (\d+) Äußerungen", l)] if m), 0)
print(f"Äußerungen gefunden: {count} (erwartet 7)")
for h in heard:
    print("  " + h)
print("Ausgeführt: " + (", ".join(done) or "nichts"))

# Longest run of the expected commands in order (a misheard one may be missing).
table = [[0] * (len(done) + 1) for _ in range(len(EXPECTED) + 1)]
for i, e in enumerate(EXPECTED):
    for j, d in enumerate(done):
        table[i + 1][j + 1] = table[i][j] + 1 if e == d else max(table[i][j + 1], table[i + 1][j])
matched = table[-1][-1]
weather = next((h for h in heard if "Wetter" in h), None)
print(f"Richtig ausgeführt: {matched} von {len(EXPECTED)}")
failed = False
if count != 7:
    print(f"::error::Der Sprach-Detektor fand {count} statt 7 Äußerungen")
    failed = True
if matched < 5:
    print(f"::error::Nur {matched} von {len(EXPECTED)} Sprachbefehlen wurden erkannt und ausgeführt")
    failed = True
if weather is not None and "kein Befehl" not in weather:
    print("::error::Ein Satz ohne Aktivierungswort hat einen Befehl ausgelöst: " + weather)
    failed = True
if len(done) > len(EXPECTED):
    print("::error::Mehr Befehle ausgeführt als gesprochen")
    failed = True
if not any("REWIND 30" in h for h in heard):
    print("::warning::„dreißig Sekunden zurück“ wurde nicht mit der Zahl erkannt")
sys.exit(1 if failed else 0)
