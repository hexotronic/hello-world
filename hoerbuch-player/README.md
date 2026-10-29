# Lauscher – Hörbuch-Player für Android

Ein Hörbuch-Player mit eingebautem **Klangstudio**: Rauschen, Brummen, Knacksen und
Zischlaute werden direkt beim Abspielen herausgefiltert.

## Aussehen

Retro-Blockstil in Erdtönen: Pergament, Terrakotta, Moos, Ocker und Umbra (dunkler Modus
„Erde bei Nacht“, dazu AMOLED-Schwarz). Alles ist eckig, mit dicken Rahmen und harten, versetzten
Schatten. Tasten „drücken sich ein“. Überschriften stehen in der Pixel-Schrift *Silkscreen*, Text in
*Space Mono*. Fortschrittsbalken bestehen aus Blöcken, Spektrum und Equalizer aus LED-Säulen.
Bücher ohne Cover bekommen ein Pixel-Muster, App-Icon und Widget sind Pixel-Art.

Echte Screenshots aus dem Emulator hängen bei jedem Build am Release (hell und dunkel).

## Installieren

1. Im Repository auf **Releases** gehen und beim neuesten „Lauscher – neuester Build“
   die Datei `Lauscher-<nummer>.apk` auf dem Handy herunterladen
   (alternativ: **Actions → Lauscher APK → Artifacts**).
2. Die APK öffnen und die Installation aus dieser Quelle erlauben.
3. In der App **Hörbuch-Ordner auswählen** – fertig.

Mindestens Android 8.0. Neue Builds lassen sich einfach über die alte Version installieren,
Fortschritt und Einstellungen bleiben erhalten.

## Funktionen

### Bibliothek
- Ordner per Android-Dateiauswahl freigeben (keine Speicher-Berechtigung nötig), beliebig viele Ordner
- Automatische Erkennung: jeder Unterordner = ein Hörbuch, `CD 1`/`Teil 2`-Unterordner werden zusammengeführt,
  einzelne `.m4b`-Dateien sind eigene Bücher
- Titel, Autor, Sprecher, Jahr aus den Tags oder aus Ordnernamen („Autor - Titel“, „Autor/Titel“)
- Cover aus `cover.jpg`/`folder.jpg` oder eingebettet, eigenes Cover wählbar
- Kapitel aus **M4B/M4A** (Nero-`chpl` und QuickTime-Kapitelspuren) und **MP3** (ID3v2 `CHAP`),
  sonst eine Datei pro Kapitel
- Natürliche Sortierung (Kapitel 2 vor Kapitel 10), schnelles Neu-Einlesen nur geänderter Bücher
- Raster- oder Listenansicht, Suche, Filter (Angefangen/Neu/Beendet), 6 Sortierungen
- „Weiterhören“-Karte, Fortschrittsbalken und Restzeit je Buch
- Einzelne Dateien hinzufügen oder aus anderen Apps mit „Öffnen mit … Lauscher“ abspielen
- **Demo-Hörbuch** (Einstellungen): künstliche Stimme mit Bandrauschen, Netzbrummen und Knistern
  zum Ausprobieren des Klangstudios

### Wiedergabe
- Fortschritt wird automatisch pro Buch gespeichert, über Dateigrenzen hinweg
- Kapitel-Navigation, Kapitelliste, Suchleiste pro Kapitel, Gesamtfortschritt
- Zurück-/Vorspulen frei einstellbar (5–120 s), Doppeltippen auf das Cover spult
- Geschwindigkeit 0,5×–3,5× in 0,05er-Schritten mit natürlicher Tonhöhe, **pro Buch gespeichert**
- **Intelligentes Zurückspulen** nach Pausen und nach Anrufen
- „Rückgängig“ nach großen Sprüngen
- Stille überspringen
- Lesezeichen mit Notizen, bearbeitbar
- Sperrmodus gegen versehentliches Tippen in der Tasche, Bildschirm-anlassen-Option
- Benachrichtigung und Sperrbildschirm mit Spul-Tasten, Bluetooth-/Kopfhörertasten
  (wahlweise Kapitel oder Spulen), Pause beim Abziehen der Kopfhörer
- Weiterhören per Bluetooth-Play-Taste, auch wenn die App geschlossen ist
- **Android Auto** (Weiterhören / Alle Hörbücher, Sprachsuche)
- Homescreen-**Widget** mit Cover, Kapitel, Fortschritt und Tasten
- Korrigiert ungenaue Längen von VBR-MP3s automatisch

### Schlaf-Timer
- 1–180 Minuten oder „Ende des Kapitels“, verlängern um 5/15 Minuten
- Sanftes Ausblenden, **Schütteln verlängert**
- Automatisches Lesezeichen „Eingeschlafen um …“
- Automatischer Timer in einem Zeitfenster (z. B. 22–6 Uhr)

### Klangstudio (Störgeräusche entfernen)
Alle Effekte laufen direkt im Player, vor der Geschwindigkeitsänderung, und lassen sich global
oder **nur für ein bestimmtes Hörbuch** speichern.
- **Rauschunterdrückung**: adaptive spektrale Rauschminderung (STFT, Minimum-Statistik,
  Decision-Directed-Wiener-Filter). Erkennt Rauschen, Brummen, Lüfter automatisch,
  Rauschprofil einfrierbar
- Rumpelfilter (Hochpass 60–220 Hz) gegen Trittschall und Netzbrummen
- Höhen-Rauschfilter (Tiefpass 4,5–12 kHz) gegen Bandrauschen
- Knackser glätten (digitalisierte Kassetten/Platten)
- Pausen säubern (adaptives Noise-Gate)
- Zischlaute dämpfen (De-Esser)
- Sprachverständlichkeit (Präsenz-Anhebung, weniger Dröhnen)
- Kompressor / Nachtmodus, automatischer Lautstärkeausgleich
- Lautstärke-Boost bis +15 dB mit Limiter
- 10-Band-Equalizer zum Ziehen mit Voreinstellungen
- Mono-Mix und Balance
- **Live-Spektrum** mit Original, bearbeitetem Signal und erkanntem Rauschboden
- **A/B-Taste**: gedrückt halten, um das Original zu hören
- Voreinstellungen: Klare Sprache, Verrauschte Aufnahme, Altes Tonband, Laute Umgebung, Nachtmodus, Ein Ohrhörer

### Hintergrundmusik passend zur Szene (lokale KI)
- **Alles auf dem Handy:** Die Spracherkennung (whisper.cpp) schreibt kurze Ausschnitte des Hörbuchs mit,
  ein **lokales Sprachmodell** (llama.cpp, z. B. Qwen2.5 1.5B) liest den Text, versteht die Szene und entscheidet,
  **ob** und **welche** Musik passt – ruhig, spannend, traurig, fröhlich, geheimnisvoll, dramatisch, romantisch,
  gruselig, idyllisch oder neutral. Nichts wird hochgeladen, keine API-Schlüssel, keine Kosten.
- Die Modelle werden einmal in der App heruntergeladen (Whisper ab 32 MB, Sprachmodell ab 400 MB) oder als
  eigene GGUF-Datei gewählt; danach läuft alles offline.
- **Vorausschauende Analyse:** Beim Hören bereitet die App die nächsten Minuten im Hintergrund vor –
  oder analysiert auf Wunsch das ganze Buch (Stichproben oder vollständig).
- Die **Musik wird live erzeugt** (Flächen, gezupfte Saiten, Glocken, Herzschlag, Natur-Geräusche, Hall),
  überblendet weich zwischen Szenen und **weicht der Stimme aus** (Ducking), Lautstärke und Übergänge einstellbar.
- **Stimmungskarte** des ganzen Buchs; jede Szene lässt sich antippen und korrigieren
- Weitere Modi: **Kapitel-Intros** (ohne KI) oder **feste Atmosphäre** (Regen, Kaminfeuer, Wald, Meer,
  Sommernacht, Sturm – auch kombiniert mit Musik), Gong bei Kapitelwechsel, Hörproben aller Stimmungen
- **„Was bisher geschah“:** Das lokale Sprachmodell schreibt einen spoilerfreien Rückblick bis zur aktuellen Stelle
- **Volltextsuche** im Hörbuch über alles, was bereits transkribiert wurde
- Ohne Sprachmodell ordnet ein Schlüsselwort-Verfahren die Szenen grob ein; das Demo-Hörbuch hat fertige Szenen
- **Tempo:** drei Stufen (Turbo 12 s, Stichproben 25 s oder vollständig je 2-Minuten-Abschnitt), schnelle
  CPU-Variante (ARMv8.2 dotprod/fp16) mit automatischer Erkennung, nur Leistungskerne, wiederverwendeter
  KV-Cache, Grammatik nur bei Bedarf; die App zeigt die geschätzte Restzeit an

### Statistik & Daten
- Hörzeit heute / 7 Tage / gesamt, Serie in Tagen, beendete Bücher
- Tagesdiagramm für 7/30/90 Tage, meistgehörte Bücher
- Sicherung exportieren/importieren (Fortschritt, Lesezeichen, Statistik, Einstellungen)
- Design: System, Hell, Dunkel, AMOLED-Schwarz; optional Material-You-Farben statt Erdtönen

## Technik

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer mit `MediaLibraryService`, SQLite,
llama.cpp (v0.8.0) und whisper.cpp (v1.9.5) über JNI (Module `llm/` und `whisper/`, beim Build per CMake geladen).
Die gesamte Klangverarbeitung (`audio/dsp`), der Musik-Synthesizer (`music/synth`), die Szenen-Logik
(`analysis/core`: Prompt, GBNF-Grammatik, Auswertung) und die Kapitel-Parser (`scan/core`) sind reines
Kotlin ohne Android-Abhängigkeiten und durch Unit-Tests abgedeckt
(`./gradlew testReleaseUnitTest`). Ein Test misst z. B., dass die Rauschunterdrückung bei
verrauschter Sprache den Störabstand um mehr als 5 dB verbessert und Sprechpausen um mehr als
15 dB leiser macht.

### Selbst bauen

```bash
cd hoerbuch-player
./gradlew assembleRelease   # APK: app/build/outputs/apk/release/app-release.apk
```

Benötigt JDK 17 und das Android SDK (API 35).

Die CI baut die APK (inklusive llama.cpp und whisper.cpp für arm64 und x86_64), führt die Unit-Tests aus
und startet die App anschließend in einem Android-14-Emulator: Demo-Hörbuch abspielen, Screenshots aller
Hauptseiten, ein Ende-zu-Ende-Test der lokalen KI (synthetische Sprache → Whisper Tiny → Qwen2.5 0.5B →
Szenen) und Prüfung auf Abstürze.

### Schriften

Silkscreen und Space Mono stehen unter der SIL Open Font License (siehe `licenses/`).

### Signatur

Ohne weitere Einrichtung werden APKs mit dem öffentlichen Schlüssel `app/lauscher-debug.keystore`
signiert, damit Updates über die alte Version installiert werden können. Für eine Veröffentlichung
(z. B. im Play Store) einen eigenen Schlüssel als Repository-Secrets hinterlegen:
`LAUSCHER_KEYSTORE_BASE64` (Keystore, base64-kodiert), `LAUSCHER_KEYSTORE_PASSWORD`,
`LAUSCHER_KEY_ALIAS`, `LAUSCHER_KEY_PASSWORD`. Achtung: Ein Wechsel des Schlüssels erfordert
einmal Deinstallieren (vorher Sicherung exportieren).
