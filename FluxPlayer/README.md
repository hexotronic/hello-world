# FluxPlayer – Video-Player für Android mit Frame-Generierung

Ein vollwertiger Video-Player im Stil der großen Desktop-/Mobil-Player – für den privaten Gebrauch.
Besonderheit: **Frame-Generierung** (Zwischenbildberechnung auf der GPU), damit 24/30-fps-Videos
flüssig mit 60/90/120 Hz laufen, plus **frei zoombares Bild**.

## Funktionen

**Wiedergabe**
- Engine: AndroidX Media3 (ExoPlayer) + FFmpeg-Erweiterung (nextlib)
  - Container: MP4, MKV, WebM, MOV, AVI, TS/M2TS, FLV, MPEG-PS, OGG, WAV … sowie WMV/ASF/RMVB über den FFmpeg-Demuxer
  - Codecs: H.264, H.265/HEVC, VP8/VP9, AV1 (Hardware) und FFmpeg-Software-Decoder als Rückfall;
    Audio inkl. AC-3, E-AC-3, DTS, TrueHD, FLAC, Opus, Vorbis, MP3, AAC
  - Automatischer Rückfall: unbekannter Container → FFmpeg-Demuxer, Decoder-Fehler → FFmpeg-Decoder
- Hardware/Software-Decoder umschaltbar
- Audiospuren, Untertitelspuren, Videoqualität (HLS/DASH) wählbar
- Externe Untertitel laden (SRT, VTT, ASS/SSA, TTML), Untertitel-Verzögerung und -Größe
- Geschwindigkeit **0,05×–4×** (logarithmischer Regler mit Feinschritten), langes Drücken = 2×;
  unter 0,1× wird der Ton automatisch stummgeschaltet (Untergrenze der Audio-Engine)
- A-B-Wiederholung, Wiederholen (Titel/Alle), Zufall, Sleep-Timer (auch „am Videoende“)
- Playlists (ganzer Ordner, M3U/PLS), Fortsetzen an der letzten Position, Verlauf
- Bild-in-Bild (auch automatisch), Hintergrundwiedergabe mit Medien-Benachrichtigung
- Schnappschuss (PNG in „Bilder/FluxPlayer“)
- Equalizer mit Presets + Lautstärke-Boost bis 200 %
- Bildeinstellungen: Helligkeit, Kontrast, Sättigung, Gamma

**Internet**
- Direkte Links (MP4, MKV, WebM …), HLS (.m3u8), DASH (.mpd), Smooth Streaming, RTSP, RTMP, UDP
- IPTV-/M3U-Playlists inkl. `#EXTVLCOPT` (User-Agent, Referer), PLS
- Webseiten-Erkennung: sucht `<video>`/`<source>`, `og:video` und eingebettete .m3u8/.mp4-Links
- Eigene Header (Referer, User-Agent), Links per „Teilen“ aus dem Browser öffnen

**Frame-Generierung** (eigener OpenGL-ES-3-Renderer)
- Jedes Videobild wird mit seinem Präsentationszeitstempel in einen Ringpuffer kopiert;
  bei jedem Display-Refresh wird das Bild für den exakten Zeitpunkt berechnet
- Modi: *Aus*, *Überblenden*, *Bewegungsinterpolation*
- Bewegungsschätzung auf der GPU: grobe Vollsuche → zwei Verfeinerungsstufen mit Kandidatensuche
  (räumliche Nachbarn + zeitliche Vorhersage, Subpixel-Genauigkeit) → Median-Filter;
  das Zwischenbild entsteht durch Warping beider Nachbarbilder entlang des Bewegungsfelds,
  bei unsicheren Stellen (Verdeckungen) wird weich auf Überblendung zurückgefallen
- Qualitätsstufen *Leistung / Ausgewogen / Qualität / Ultra*, Display wird auf maximale Hz geschaltet
- **Ultra (Zeitlupe)**: Bewegungsfeld in halber Auflösung über 4 Pyramidenstufen, Kanten-Verfeinerung
  mit Farbvergleich, **bidirektionaler Fluss** (Vorwärts + Rückwärts) mit Verdeckungserkennung per
  Fixpunkt-Iteration – Objektkanten bleiben sauber statt auszufransen
- **Zeitlupen-Boost**: in Zeitlupe automatisch stärker rechnen (≤ 0,6× mind. „Qualität“, ≤ 0,3× „Ultra“);
  tempoabhängiges Timing, damit auch bei 0,05× (Bilder 0,8 s auseinander) flüssig interpoliert wird;
  die Berechnung wird auf mehrere Display-Refreshes verteilt, damit sie nicht ruckelt
- Statistik-Overlay (Quell-fps, Ausgabe-fps, Anteil generierter Bilder)

**Upscaler**
- *Bikubisch* (Catmull-Rom) oder *Kantenadaptiv*: an echten Kanten ein entlang der Kante ausgerichteter
  Lanczos-Kern mit Anti-Ringing (ähnlich FSR 1), Texturen bleiben bikubisch
- Kontrastadaptive Schärfung (CAS-artig), wirkt auch bei voller Auflösung
- Arbeitet zusammen mit der Frame-Generierung (Zwischenbild in Quellauflösung → Upscaling auf das Display)
- Für Mobil-GPUs optimiert: bikubisch über 9 bilineare Abtastungen, ohne Arrays/Schleifen; läuft nur, wenn
  wirklich vergrößert oder geschärft wird; bei unverändertem Bild wird das Ergebnis aus einem Zwischenspeicher
  kopiert statt neu berechnet

**Zoom & Bild**
- Pinch-Zoom 25 %–1000 % um den Fingerpunkt, Verschieben mit einem oder zwei Fingern
- Seitenverhältnis: Einpassen, Füllen, Strecken, Original 1:1, 16:9, 4:3, 21:9, 1.85:1, 2.35:1, 1:1

**Gesten**: Wischen horizontal = Spulen, links vertikal = Helligkeit, rechts vertikal = Lautstärke,
Doppeltippen links/rechts = ±10 s, Mitte = Pause, Bedienungssperre

## Installation

APK auf das Handy kopieren und öffnen („Installation aus unbekannten Quellen“ erlauben).
Voraussetzung: Android 8.0+ mit OpenGL ES 3.0 (praktisch jedes Gerät seit 2016).

Hinweis: Lokal gebaute und per GitHub Actions gebaute APKs sind mit unterschiedlichen
Debug-Schlüsseln signiert – beim Wechsel zwischen beiden vorher die alte Version deinstallieren.
Für einen festen Schlüssel die Umgebungsvariablen `FLUX_KEYSTORE`, `FLUX_KEYSTORE_PASSWORD`,
`FLUX_KEY_ALIAS` und `FLUX_KEY_PASSWORD` setzen.

## Bauen

```bash
cd FluxPlayer
./gradlew assembleRelease        # -> app/build/outputs/apk/release/app-release.apk
./gradlew testDebugUnitTest      # Unit- und UI-Tests
```

Benötigt JDK 17+ und Android SDK mit Plattform 37.2. Bei jedem Push baut außerdem der
Workflow `.github/workflows/fluxplayer-apk.yml` die APK (Artefakt „FluxPlayer-apk“).

## Grenzen

- DRM-geschützte Streams (Netflix, Disney+ …) und Seiten wie YouTube, die keine direkten Videolinks
  ausliefern, werden nicht unterstützt.
- Frame-Generierung kostet GPU-Leistung und Akku; bei schwachen Geräten „Leistung“ oder „Überblenden“ wählen.
