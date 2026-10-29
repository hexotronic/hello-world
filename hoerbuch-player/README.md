# Lauscher – Hörbuch-Player für Android

Ein Hörbuch-Player mit eingebautem **Klangstudio**: Rauschen, Brummen, Knacksen und
Zischlaute werden direkt beim Abspielen herausgefiltert.

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

### Statistik & Daten
- Hörzeit heute / 7 Tage / gesamt, Serie in Tagen, beendete Bücher
- Tagesdiagramm für 7/30/90 Tage, meistgehörte Bücher
- Sicherung exportieren/importieren (Fortschritt, Lesezeichen, Statistik, Einstellungen)
- Design: System, Hell, Dunkel, AMOLED-Schwarz, Material-You-Farben

## Technik

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer mit `MediaLibraryService`, SQLite.
Die gesamte Klangverarbeitung (`audio/dsp`) und die Kapitel-Parser (`scan/core`) sind reines
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

### Signatur

Ohne weitere Einrichtung werden APKs mit dem öffentlichen Schlüssel `app/lauscher-debug.keystore`
signiert, damit Updates über die alte Version installiert werden können. Für eine Veröffentlichung
(z. B. im Play Store) einen eigenen Schlüssel als Repository-Secrets hinterlegen:
`LAUSCHER_KEYSTORE_BASE64` (Keystore, base64-kodiert), `LAUSCHER_KEYSTORE_PASSWORD`,
`LAUSCHER_KEY_ALIAS`, `LAUSCHER_KEY_PASSWORD`. Achtung: Ein Wechsel des Schlüssels erfordert
einmal Deinstallieren (vorher Sicherung exportieren).
