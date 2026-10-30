#!/bin/bash
# Installs the APK on the CI emulator, plays the demo audiobook, takes screenshots of the
# main screens in light and dark mode and fails if the app crashed.
# usage: emulator-screens.sh <apk> <output-dir>
set -u
APK=$1
OUT=$2
PKG=io.github.hexotronic.lauscher
mkdir -p "$OUT"

adb install -r "$APK"
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true
adb shell settings put system screen_off_timeout 1800000
adb shell svc power stayon true
adb logcat -c

start() { adb shell am start -W -n "$PKG/.MainActivity" "$@" > /dev/null; }
shot() {
  sleep "$2"
  # A busy emulator sometimes shows "… isn't responding" for the launcher; close such dialogs
  # (not for the notification screenshot, which needs the open shade).
  if [ "$1" != "10-benachrichtigung" ]; then
    adb shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS > /dev/null 2>&1 || true
    sleep 1
  fi
  adb exec-out screencap -p > "$OUT/$1.png"
  echo "screenshot $1"
}

adb shell cmd uimode night no
start --ez demo true
shot 01-player-hell 25
start --es screen library
shot 02-bibliothek-hell 4
start --es preset NOISY_RECORDING --es screen studio
shot 03-klangstudio-hell 6
adb shell input swipe 20 2000 20 300 400
adb shell input swipe 20 2000 20 300 400
shot 03b-klangstudio-unten-hell 2
adb shell input swipe 20 2000 20 300 400
shot 03c-equalizer-hell 2
start --es screen detail
shot 04-details-hell 4
sleep 15
start --es screen stats
shot 05-statistik-hell 5
start --es screen settings
shot 06-einstellungen-hell 3
start --ez music true --es screen player
shot 06b-player-musik-hell 6
start --es screen music
shot 06c-hintergrundmusik-hell 4

# --- Music library: download pieces from the games' repositories, check them and play them.
# spd/sewers_1 is a 26-second loop that is played several times in a row.
start --es music_download wesnoth/sad,spd/sewers_1 --es screen musiclibrary
for i in $(seq 1 40); do
  if adb logcat -d -s LauscherMusik:* | grep -qE "Geladen: spd/sewers_1|fehlgeschlagen"; then break; fi
  sleep 2
done
start --es music_preview wesnoth/sad --es screen musiclibrary
shot 06d-musikbibliothek-hell 6
start --es music_preview spd/sewers_1 --es screen player
sleep 32
start --es music_fixed TENSE@DUNGEON --es screen player
shot 06e-player-kerker-musik-hell 4
start --ez music true --es screen feedback
shot 06f-rueckmeldung-hell 4
adb logcat -d -s LauscherMusik:* | tail -30 | tee "$OUT/musik.txt"
if ! grep -q "Dekodiert: wesnoth/sad" "$OUT/musik.txt" || ! grep -q "Fortsetzung: spd/sewers_1, Teil 2" "$OUT/musik.txt"; then
  echo "::error::Die Musikbibliothek wurde nicht geladen oder nicht (in Schleife) abgespielt"
  E2E_FAILED=1
fi

adb shell cmd uimode night yes
sleep 3
start --es screen player
shot 07-player-dunkel 5
start --es screen studio
shot 08-klangstudio-dunkel 6
start --es screen library
shot 09-bibliothek-dunkel 4
adb shell cmd statusbar expand-notifications
shot 10-benachrichtigung 3
adb shell cmd statusbar collapse

# --- Local AI end to end: speech file + small models -> scene analysis on the device.
if [ -n "${E2E_DIR:-}" ] && [ -f "$E2E_DIR/story.wav" ]; then
  DATA=/sdcard/Android/data/$PKG/files
  adb shell mkdir -p "$DATA/models" "$DATA/test"
  adb push "$E2E_DIR/ggml-tiny-q5_1.bin" "$DATA/models/" > /dev/null
  adb push "$E2E_DIR/qwen2.5-0.5b-instruct-q4_k_m.gguf" "$DATA/models/" > /dev/null
  adb push "$E2E_DIR/story.wav" "$DATA/test/" > /dev/null
  adb push "$E2E_DIR/story.txt" "$DATA/test/" > /dev/null
  adb shell cmd uimode night no
  # Speech recognition at double speed (time-stretched, same pitch): the first sample is compared with
  # normal speed, and the faster speed is only kept if recognition hardly suffers.
  start --ez music true --es whisper_model whisper-tiny --es llm_model qwen2.5-0.5b --es language en --es speech_speed 2 \
    --es import_path "$DATA/test/story.wav" --ez analyze true
  echo "Warte auf die lokale Analyse …"
  for i in $(seq 1 120); do
    if adb logcat -d -s LauscherAnalyse:* | grep -qE "Szenen|Analysis failed"; then break; fi
    sleep 5
  done
  adb logcat -d -s LauscherAnalyse:* LauscherLlm:* LauscherWhisper:* | tail -40 | tee "$OUT/analyse.txt"
  sleep 5
  start --es screen music
  shot 11-ki-analyse-hell 5
  if ! grep -q "Szenen" "$OUT/analyse.txt"; then
    echo "::error::Die lokale Analyse hat keine Szenen geliefert"
    E2E_FAILED=1
  fi
  if ! grep -q "Tempoprobe 2.0" "$OUT/analyse.txt"; then
    echo "::error::Die Tempoprobe bei 2× fehlt"
    E2E_FAILED=1
  fi

  # The same book with its text: the analysis reads instead of listening. Windows of 30 s, so the
  # story has two and a change of music between them is looked for more precisely.
  adb logcat -c
  start --es sample_every 0.5 --es book_text_path "$DATA/test/story.txt" --ez analyze true
  echo "Warte auf die Analyse mit Buchtext …"
  for i in $(seq 1 120); do
    if adb logcat -d -s LauscherAnalyse:* | grep -qE "Analyse fertig|Analysis failed"; then break; fi
    sleep 5
  done
  adb logcat -d -s LauscherAnalyse:* | tail -20 | tee "$OUT/analyse-buchtext.txt"
  if ! grep -q "Buchtext" "$OUT/analyse-buchtext.txt" || ! grep -q "Szenen" "$OUT/analyse-buchtext.txt"; then
    echo "::error::Die Analyse mit Buchtext hat nicht funktioniert"
    E2E_FAILED=1
  fi
  start --es screen analysis
  shot 13-ki-analyse-tab-hell 5
  # Two windows with different music: the scene change must have been looked for.
  KINDS=$(grep "Szenen:" "$OUT/analyse-buchtext.txt" | grep -oE "[0-9]+:[0-9]+=[A-Z]+/[0-9]@[A-Z_?]+" | cut -d= -f2 | sort -u | wc -l)
  if [ "$KINDS" -gt 1 ]; then
    if ! grep -q "Szenenwechsel bei" "$OUT/analyse-buchtext.txt"; then
      echo "::error::Der Szenenwechsel wurde nicht genauer gesucht"
      E2E_FAILED=1
    fi
  else
    echo "::notice::Beide Abschnitte bekamen dieselbe Musik – kein Szenenwechsel zu verfeinern"
  fi

  # Speech recognition of the same recording at 1×, 1.25×, 1.5× and 2×: time and words recognised.
  adb logcat -c
  start --es speech_check "$DATA/test/story.wav"
  echo "Warte auf den Sprachtest …"
  for i in $(seq 1 60); do
    if adb logcat -d -s LauscherAnalyse:* | grep -q "Sprachtest fertig"; then break; fi
    sleep 5
  done
  adb logcat -d -s LauscherAnalyse:* | grep "Sprachtest" > "$OUT/sprachtest.txt" || true
  sed -E 's/^.*(Sprachtest)/\1/' "$OUT/sprachtest.txt" | cut -c1-300
  python3 - "$E2E_DIR/story.txt" "$OUT/sprachtest.txt" <<'PY' | tee "$OUT/sprachtest-auswertung.txt"
import difflib, re, sys
words = lambda t: re.findall(r"[a-z]+", t.lower())
ref = words(open(sys.argv[1]).read())
for line in open(sys.argv[2]):
    m = re.search(r"Sprachtest ([\d.]+)×: (\d+) ms, (\d+) Wörter: (.*)", line)
    if not m:
        continue
    heard = words(m.group(4))
    hit = sum(b.size for b in difflib.SequenceMatcher(None, ref, heard, autojunk=False).get_matching_blocks())
    print(f"Tempo {m.group(1)}×: {int(m.group(2)) / 1000:.1f} s, {100 * hit / len(ref):.0f} % der {len(ref)} Wörter erkannt")
PY
  if ! grep -q "Sprachtest fertig" "$OUT/sprachtest.txt" || ! grep -q "Tempo 2.0" "$OUT/sprachtest-auswertung.txt"; then
    echo "::error::Der Sprachtest bei mehreren Tempi hat nicht funktioniert"
    E2E_FAILED=1
  fi

  # --- Downloads: a video from the phone as MP3, one LibriVox chapter, a PeerTube search result.
  if [ -f "$E2E_DIR/video.mp4" ]; then adb push "$E2E_DIR/video.mp4" "$DATA/test/" > /dev/null; fi
  adb logcat -c
  start --es download_convert "$DATA/test/video.mp4" --es screen downloads
  start --es download_audiobook "Grimm" --es download_limit 1
  start --es download_media "peertube:peertube" --es download_limit 1
  echo "Warte auf die Downloads …"
  for i in $(seq 1 96); do
    if [ "$(adb logcat -d -s LauscherDownload:* | grep -cE "Auftrag [0-9]+ (fertig|fehlgeschlagen)")" -ge 3 ]; then break; fi
    sleep 5
  done
  start --es screen downloads
  shot 12-herunterladen-hell 4
  adb logcat -d -s LauscherDownload:* | tail -40 | tee "$OUT/downloads.txt"
  if ! grep -q "Umgewandelt: .*video.mp3" "$OUT/downloads.txt" || ! grep -qE "Importiert \[download:[0-9a-f]+\]: video .*Buch [0-9]" "$OUT/downloads.txt"; then
    echo "::error::Das Video wurde nicht in MP3 umgewandelt"
    E2E_FAILED=1
  fi
  if ! grep -qE "Importiert \[download:librivox:.*Buch [0-9]" "$OUT/downloads.txt"; then
    echo "::error::Das LibriVox-Hörbuch wurde nicht geladen"
    E2E_FAILED=1
  fi
  if ! grep -E "Importiert \[download:[0-9a-f]+\]: .*Buch [0-9]" "$OUT/downloads.txt" | grep -qv "\]: video "; then
    echo "::warning::Der PeerTube-Download hat nicht geklappt (externer Dienst)"
  fi

  # YouTube (Big Buck Bunny, CC BY 3.0 Blender Foundation): best effort, YouTube often blocks CI servers.
  adb logcat -c
  start --es download_media "https://www.youtube.com/watch?v=aqz-KE-bpKQ" --es download_limit 1
  for i in $(seq 1 48); do
    if adb logcat -d -s LauscherDownload:* | grep -qE "Auftrag [0-9]+ (fertig|fehlgeschlagen)|Automatischer Download fehlgeschlagen"; then break; fi
    sleep 5
  done
  adb logcat -d -s LauscherDownload:* | tail -15 | tee "$OUT/downloads-youtube.txt"
  if ! grep -qE "Importiert \[.*Buch [0-9]" "$OUT/downloads-youtube.txt"; then
    echo "::warning::Der YouTube-Download hat in der CI nicht geklappt (siehe downloads-youtube.txt)"
  fi
fi

adb shell dumpsys media_session > "$OUT/media_session.txt" || true
adb logcat -d -b crash > "$OUT/crash.txt" || true
adb logcat -d > "$OUT/logcat-full.txt" || true
grep -E "AndroidRuntime|lauscher|ExoPlayer|AudioTrack|MediaSession|LibraryScanner" "$OUT/logcat-full.txt" | tail -400 > "$OUT/logcat.txt" || true

APP_PID=$(adb shell pidof "$PKG" | tr -d '\r')
echo "--- Warnungen/Fehler der App (PID ${APP_PID:-?}) ---"
if [ -n "$APP_PID" ]; then
  grep -E "^[0-9-]+ [0-9:.]+ +$APP_PID .* [EW] " "$OUT/logcat-full.txt" | grep -vE "TextToSpeech|OpenGLRenderer|EGL_emulation|HWUI" | tail -60 || true
fi

if grep -q "$PKG" "$OUT/crash.txt"; then
  echo "::error::Lauscher ist abgestürzt"
  cat "$OUT/crash.txt"
  exit 1
fi
if ! grep -q "$PKG" "$OUT/media_session.txt"; then
  echo "::warning::Keine Media-Session von Lauscher gefunden"
fi
if [ "${E2E_FAILED:-0}" = 1 ]; then exit 1; fi
echo "Keine Abstürze."
