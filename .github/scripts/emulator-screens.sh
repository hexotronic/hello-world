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
echo "Keine Abstürze."
