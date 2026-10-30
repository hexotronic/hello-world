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
   die Datei `Lauscher.apk` (immer der neueste Build; gleicher Inhalt wie `Lauscher-<nummer>.apk`)
   auf dem Handy herunterladen (alternativ: **Actions → Lauscher APK → Artifacts**).
   Während ein neuer Build veröffentlicht wird, ist der Release für einige Sekunden weg – dann kurz
   warten und neu laden.
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

### Herunterladen und umwandeln
In der Bibliothek über **＋ → Herunterladen und umwandeln …** (oder in den Einstellungen):

- **Kostenlose Hörbücher** von LibriVox (gemeinfrei, von Freiwilligen eingelesen): nach Titel oder Autor
  suchen, Sprache wählen (Deutsch, Englisch, alle), mit einem Tipp das **ganze Buch mit allen Kapiteln**
  laden. Die Dateien kommen aus dem Internet Archive.
- **Videos als MP3** von YouTube, PeerTube, SoundCloud, Bandcamp und media.ccc.de (über
  [NewPipe Extractor](https://github.com/TeamNewPipe/NewPipeExtractor)):
  - **Suchen** oder **Links einfügen**: einzelne Videos, **ganze Playlists und Kanäle**, auch mehrere
    Links auf einmal. Direkte Links auf Ton- oder Videodateien (z. B. `…/vortrag.mp4`) gehen ebenfalls.
  - **Bulk:** Treffer per Häkchen auswählen, „Alle“ oder „Alle Seiten laden“ (bis 500 Videos), dann
    herunterladen – als **ein Hörbuch mit Kapiteln** (Playlist = Buch) oder jedes einzeln.
  - Gespeichert wird nur der Ton: als **MP3** (64 kbit/s mono für Sprache, 128, 192 oder 320 kbit/s) mit
    Titel, Interpret, Album und Cover in den ID3-Tags, oder im **Originalformat** (M4A/WebM, ohne
    Umwandlung, schneller, ohne Qualitätsverlust).
  - Links lassen sich aus YouTube, NewPipe oder dem Browser über **Teilen → In Lauscher öffnen** schicken.
- **Videos vom Handy umwandeln**: Video- oder Tondateien auswählen (oder über **Teilen → In Lauscher
  als MP3** schicken) – sie werden zu MP3 bzw. ihre Tonspur wird herausgelöst.
- Alles landet als Hörbuch in der Bibliothek, die Dateien liegen sichtbar für andere Apps unter
  **Audiobooks/Lauscher/<Autor – Titel>/** (Android 10+). Downloads laufen im Hintergrund weiter
  (Benachrichtigung mit Fortschritt), einzelne Fehler in einer Playlist brechen den Rest nicht ab.
- **Bitte nur herunterladen, was du darfst** – gemeinfreie oder frei lizenzierte Inhalte (LibriVox,
  Creative Commons), eigene Aufnahmen oder mit Erlaubnis. Manche Plattformen, etwa YouTube, untersagen
  das Herunterladen in ihren Nutzungsbedingungen. Wenn eine Plattform ihre Seiten ändert, kann die
  Suche dort bis zum nächsten Update der App ausfallen.

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
- **„Passt die Musik?“** direkt im Player: Szene bestätigen, korrigieren (Stimmung, Ort, Spannung), „hier keine
  Musik“, neu analysieren oder ihre Analyse löschen; zum laufenden Stück „anderes Stück“, „gefällt mir“
  (wird bevorzugt) oder „nie mehr spielen“
- **Die KI lernt aus Rückmeldungen:** korrigierte und bestätigte Szenen bekommt das Sprachmodell als Beispiele,
  wenn es den Rest des Buchs analysiert (abschaltbar); bestätigte Szenen überschreibt keine neue Analyse
- **Löschen nach Wahl:** eine Szene, ein Buch oder alle Analysen – jeweils mit oder ohne die eigenen Korrekturen
  und die Mitschrift; Übersicht über analysierte Bücher, Korrekturen, Lieblings- und ausgeblendete Stücke in den
  Einstellungen
- Weitere Modi: **Kapitel-Intros** (ohne KI) oder **feste Atmosphäre** (Regen, Kaminfeuer, Wald, Meer,
  Sommernacht, Sturm – auch kombiniert mit Musik), Gong bei Kapitelwechsel, Hörproben aller Stimmungen
- **„Was bisher geschah“:** Das lokale Sprachmodell schreibt einen spoilerfreien Rückblick bis zur aktuellen Stelle
- **Volltextsuche** im Hörbuch über alles, was bereits transkribiert wurde
- Ohne Sprachmodell ordnet ein Schlüsselwort-Verfahren die Szenen grob ein; das Demo-Hörbuch hat fertige Szenen
- **Stichproben per Schieberegler:** „Stichprobe alle 1–10 Min.“ (so oft kann die Musik wechseln) und „Länge je
  Stichprobe 5 s … alles“, dazu Schnellwahl (Turbo, Ausgewogen, Gründlich, Vollständig). Die App zeigt, wie viel
  des Buchs gehört wird und wie viel Rechenzeit eine Stunde Hörbuch braucht – nach der ersten Analyse auf dem
  eigenen Gerät gemessen.
- **Szenenwechsel genau finden:** Wo die Musik zwischen zwei Abschnitten wechselt, hört die App an der Grenze
  zweimal kurz nach (12 s, oder liest im Buchtext) und fragt das Sprachmodell nur „gehört das noch zur alten
  oder schon zur neuen Szene?“ (eine Antwort, A oder B). So wechselt die Musik auf etwa ±15 s genau statt im
  2-Minuten-Raster. Abschaltbar; korrigierte Szenen bleiben, wie sie sind.
- **Übergänge passend zur Szene:** Plötzliche Wendungen – ruhig → spannend, spannend → Kampf, fröhlich → traurig –
  werden in 1,2 s übergeblendet, Beruhigung nach einem Kampf weiter langsam (Einstellung „Übergänge“). Der
  Übergang liegt mittig auf dem Szenenwechsel, die neue Musik beginnt also schon kurz davor.
- **Spracherkennung bis 2× schneller:** die Stimme wird vor der Erkennung zeitlich gestaucht (WSOLA, gleiche
  Tonhöhe), die Spracherkennung hat entsprechend weniger zu tun. Das kostet Genauigkeit – im CI-Test (Whisper Tiny,
  synthetische Stimme) fiel die Erkennung schon bei 1,5× von 83 % auf 17 % der Wörter. Deshalb macht Lauscher bei
  jedem Buch zuerst eine **Tempoprobe**: dieselbe Stelle wird mit 1× und mit dem gewählten Tempo erkannt, und das
  schnellere Tempo wird nur genutzt, wenn mindestens 70 % der Wörter übereinstimmen. Das Ergebnis steht unter dem
  Regler.
- **Buchtext einfügen statt mitschreiben:** in den Buch-Details unter „Buchtext“ eine TXT-, EPUB- oder HTML-Datei
  wählen, Text aus der Zwischenablage einfügen oder aus einer E-Book-App über „Teilen → Lauscher“ schicken. Die
  App ordnet den Text über die Kapitel (Nummern/Titel) der Zeit zu und gleicht ihn – falls ein
  Spracherkennungsmodell da ist – alle 10 Minuten mit einer 12-Sekunden-Hörprobe ab (die gehörten Wörter werden im
  Text gesucht). Die KI liest dann den echten Text; das spart die Spracherkennung fast ganz, Suche und
  Rückblick nutzen ihn ebenfalls.
- **Weitere Beschleunigung:** schnelle CPU-Variante (ARMv8.2 dotprod/fp16) mit automatischer Erkennung, nur
  Leistungskerne, wiederverwendeter KV-Cache, Grammatik nur bei Bedarf; die App zeigt die geschätzte Restzeit an

### Musikbibliothek nach Stimmung, Ort und Spannung (echte Musik, keine KI)
Statt des Synthesizers kann die Hintergrundmusik aus **347 handkomponierten Stücken** (rund 1 GB, gut 18 Stunden
mit Schleifen) kommen – Soundtracks freier Spiele. Alles ist von Menschen komponiert und eingespielt, nichts
davon ist KI-generiert (Veloren verbietet KI-Inhalte sogar ausdrücklich in seinen Beitragsregeln):

| Quelle | Stücke | Lizenz | Schwerpunkt |
|---|---|---|---|
| [The Battle for Wesnoth](https://github.com/wesnoth/wesnoth) 1.18.8 | 35 | GPL v2+ (1× CC BY-SA 4.0) | Orchester: Schlacht, Burg, Wald, Höhle |
| [Widelands](https://github.com/widelands/widelands) | 24 | GPL v2+, CC BY-SA 3.0, CC BY 4.0 | Mittelalter, Dorf, Wüste, Gebirge |
| [Flare](https://github.com/flareteam/flare-game) | 10 | CC BY-SA 3.0 | Rollenspiel: Stadt, Höhle, Verlies |
| [Veloren](https://github.com/veloren/veloren) | 69 | GPL v3 | Landschaften je Biom, Siedlungen, Höhlen, Verliese, Kampfmusik in drei Teilen |
| [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) | 31 | GPL v3 | Kerker, Kanalisation, Höhlen, Dämonenhallen – je ruhig, angespannt, Bosskampf |
| [SuperTux](https://github.com/SuperTux/supertux) | 40 | CC BY-SA 3.0/4.0, GPL v2+ | Wald, Geisterwald, Eis, Festung, Unterwasser |
| [Stendhal](https://github.com/arianne/stendhal) | 31 | GPL v2, CC0, CC BY 3.0/4.0, OGA-BY | Taverne, Markt, Kirche, Kerker, Mine, Spukwald |
| [0 A.D.](https://github.com/0ad/0ad) | 60 | CC BY-SA 3.0 (Wildfire Games) | Antike: Griechen, Römer, Kelten, Ägypten, Persien, Indien, China |
| [Pioneer](https://github.com/pioneerspacesim/pioneer) | 39 | CC BY-SA 3.0/4.0 | Weltraum: Sternenflug, Station, Begegnung, Gefecht |
| [Hedgewars](https://github.com/hedgewars/hw) | 8 | GNU FDL 1.2 | Orient, Piraten, Schnee, Dschungel, Großstadt |

- **26 Orte und Themen:** Wald, Dschungel, Draußen, Gebirge, Schnee und Eis, Wüste, Meer, Unter Wasser, Höhle,
  Kerker, Burg, Dorf und Stadt, Taverne, Zuhause, Schlacht, Tempel, Zauberwelt, Spukort, Orient, Fernost, Antike,
  Norden, Wilder Westen, Weltraum, Großstadt. Die lokale KI erkennt neben der Stimmung auch den Ort jeder
  Szene (sonst helfen Ortswörter im Text); in der Stimmungskarte lässt sich beides korrigieren.
- **Verschiedene Stücke je Spannung im selben Gebiet:** Im Kerker etwa ruhiges Erkunden, dann „angespannt“,
  dann Bosskampf; Veloren-Kampfmusik spielt Anlauf, Schleife und Ende nacheinander.
- **Bei jedem Szenenwechsel neu gewählt:** Ändert sich Ort, Stimmung oder Spannungsstufe, wird zu einem
  passenden Stück übergeblendet; bleibt alles gleich, läuft das Stück weiter. Die Auswahl richtet sich zuerst
  nach der Stimmung, dann nach dem Ort (sonst verwandte Orte, z. B. Wüste und Prärie für den Wilden Westen,
  für den es noch keine eigenen freien Stücke gibt), dann nach der Spannung, ohne Wiederholungen.
- Kurze Spielmusik-Schleifen werden an ihren Schleifenpunkten mehrmals gespielt (mit winzigen
  Überblendungen gegen Knackser) und am Ende ausgeblendet.
- Optional **Geräusche passend zum Ort** (Wald, Meer, Wind, Kaminfeuer) leise unter der Musik.
- **Laden:** Starterpaket (die passendsten Stücke je Ort und Stimmung, 66 Stücke, 209 MB), Pakete je Stimmung
  oder je Ort, einzelne Stücke oder alles; jeweils mit Hörprobe. Die Dateien kommen **unverändert aus den
  Repositories der Spiele** (fester Commit bzw. feste Version) und werden per **SHA-256-Prüfsumme** kontrolliert.
- Der Player zeigt Stimmung, Ort und das laufende Stück mit Komponist; Herkunft, Komponisten und Lizenzen
  stehen in der App unter *Musikbibliothek → Herkunft und Lizenzen*.
- Wie der Katalog entstanden ist: `tools/music/` – `candidates*.txt` (Quellen), `measure.sh` (lädt jedes Stück,
  misst Prüfsumme, Länge, Lautstärke, Tempo, liest die Tags), `analysis*.jsonl` (Messwerte),
  `catalog_tables.py` (Komponisten, Lizenzen, Stimmungen und Orte von Hand) und `catalog.py` (erzeugt
  `FantasyCatalog.kt`). Komponisten und Lizenzen stammen aus den Credits der Spiele (`copyrights.csv`,
  `LICENSE.txt`, `CREDITS.txt`, `AUTHORS`, `credits.ron`, `audio-music.txt`, `.music`-Dateien) und den Tags der Dateien.

### Statistik & Daten
- Hörzeit heute / 7 Tage / gesamt, Serie in Tagen, beendete Bücher
- Tagesdiagramm für 7/30/90 Tage, meistgehörte Bücher
- Sicherung exportieren/importieren (Fortschritt, Lesezeichen, Statistik, Einstellungen)
- Design: System, Hell, Dunkel, AMOLED-Schwarz; optional Material-You-Farben statt Erdtönen

## Technik

Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer mit `MediaLibraryService`, SQLite,
llama.cpp (v0.8.0) und whisper.cpp (v1.9.5) über JNI (Module `llm/` und `whisper/`, beim Build per CMake geladen),
der MP3-Encoder LAME 3.100 (Modul `mp3/`, LGPL) und NewPipe Extractor v0.26.5 (GPL-3.0) für die Video-Plattformen.
Die gesamte Klangverarbeitung (`audio/dsp`), der Musik-Synthesizer und Mixer (`music/synth`), der
Musikkatalog mit Stückauswahl (`music/library/core`), die Download-Logik (`download/core`: LibriVox-Suche,
ID3-Tags, Links, Aufteilung in Bücher), die Szenen-Logik
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
Szenen, mit doppelt so schneller Spracherkennung, danach dasselbe Buch mit eingefügtem Buchtext), Laden und
Abspielen von Stücken der Musikbibliothek (auch einer Schleife) und Prüfung auf Abstürze.

### Schriften

Silkscreen und Space Mono stehen unter der SIL Open Font License (siehe `licenses/`).

### Signatur

Ohne weitere Einrichtung werden APKs mit dem öffentlichen Schlüssel `app/lauscher-debug.keystore`
signiert, damit Updates über die alte Version installiert werden können. Für eine Veröffentlichung
(z. B. im Play Store) einen eigenen Schlüssel als Repository-Secrets hinterlegen:
`LAUSCHER_KEYSTORE_BASE64` (Keystore, base64-kodiert), `LAUSCHER_KEYSTORE_PASSWORD`,
`LAUSCHER_KEY_ALIAS`, `LAUSCHER_KEY_PASSWORD`. Achtung: Ein Wechsel des Schlüssels erfordert
einmal Deinstallieren (vorher Sicherung exportieren).
