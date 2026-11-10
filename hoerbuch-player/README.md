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
- **Fortschritt mit Netzdaten:** geladene Datenmenge (bei LibriVox „12,4 MB von 48 MB“), Geschwindigkeit,
  Verbindung (WLAN/mobile Daten) und das erwartete Ende („Fertig ca. 14:32 Uhr (noch 6 Min.)“) – in der App und
  in der Benachrichtigung. Bei Videos wird die Restzeit wie ein Fließband gerechnet: Download = Restbytes ÷
  Netzgeschwindigkeit, Umwandlung = Tonlänge ÷ MP3-Geschwindigkeit; während das eine Video umgewandelt wird, lädt
  schon das nächste. Beide Geschwindigkeiten misst die App auf dem Gerät und merkt sie sich (die MP3-Geschwindigkeit
  über die ganze Zeit, in der Umwandlungen liefen – mehrere gleichzeitig teilen sich ja die Kerne; Startwert 50×
  Echtzeit), eine laufende Umwandlung korrigiert die Schätzung mit ihrer eigenen Geschwindigkeit. Fertige Aufträge zeigen, wie lange es wirklich gedauert hat
  („Fertig nach 0:42 · 10:05 Ton mit 43× Echtzeit umgewandelt“).
- **Schnell:** jede Datei kommt über 4 Verbindungen gleichzeitig (YouTube und archive.org bremsen jede einzelne
  Verbindung); kleine Dateien und Server ohne Teilstücke laden über eine. Angezeigt wird auch, was gerade passiert
  („lädt herunter“, „wandelt in MP3 um“, „speichert“) – am schnellsten ist das Format „Original“ ohne Umwandlung.
- **Optionen merken sich:** Format, Bitrate, Umwandlung und Gruppierung bleiben gewählt, auch nach einem Neustart.
- **Umwandlung „Ausgewogen“ oder „Schnell“:** „Schnell“ nutzt LAMEs schnelle Stufe (-q 7, ohne
  Rauschformung), etwa 1,3–1,7× so schnell. Gemessen mit ViSQOL (objektives Hörmodell, 1–5; je Bitrate
  Änderung gegenüber -q 5):

  | Bitrate | reine Sprache | Sprache + Raumrauschen | Sprache + Musik + Geräusche | dichte Musik |
  |---|---|---|---|---|
  | 64 kbit/s | −0,01 | −0,06 | −0,57 | −0,39 |
  | 128 kbit/s | ±0 | +0,01 | −0,23 | −0,23 |
  | 192 kbit/s | ±0 | ±0 | −0,02 | −0,01 |
  | 320 kbit/s | ±0 | ±0 | ±0 | ±0 |

  „Schnell“ ist also für reine Sprache verlustfrei, bei Musik oder Geräuschen unter der Stimme hörbar
  schlechter. „Ausgewogen“ (Standard) nimmt bis 128 kbit/s -q 5 und ab 192 kbit/s -q 7 – dort klingt es
  gleich und wird gratis schneller. Eine „gründliche“ Stufe (-q 2) gibt es nicht: 2,5× langsamer, laut
  Hörmodell nicht besser. Die Speech-Prüfung mit PESQ (ITU-T P.862) liegt bei allen Stufen an der
  Skalen-Obergrenze (4,5–4,6). Die CI wandelt dieselbe Aufnahme (6 Min, Sprache über Musik und Geräuschen)
  in beiden Modi um und bewertet die MP3s vom Gerät mit ViSQOL – im Emulator: 64 kbit/s 3,63 → 3,15,
  128 kbit/s 4,23 → 4,04, aber nur 4–7 % schneller (30× → 32× Echtzeit), weil dort das Entpacken der
  AAC-Quelle bremst, nicht LAME. Die App zeigt deshalb bei der Auswahl die auf dem Handy gemessenen
  Geschwindigkeiten beider Modi an, sobald sie bekannt sind.
- **Mehrere Umwandlungen gleichzeitig:** in einem Auftrag laufen bis zu 3 Videos zugleich (2 Downloads, die
  Umwandlungen teilen sich die Kerne) – während eins umgewandelt wird, lädt schon das nächste; jedes Buch kommt in
  die Bibliothek, sobald seine Teile fertig sind. Lange Aufnahmen (32/44,1/48 kHz) werden zusätzlich in Stücke von
  20 s geteilt, die bis zu 4 Kerne gleichzeitig mit LAME kodieren (jedes Stück mit 1,5 s Vorlauf). Die Stücke werden
  nahtlos zusammengesetzt: alle Encoder arbeiten im selben Rahmenraster, und an der Naht wird ein Rahmen gewählt,
  dessen Bit-Reservoir-Daten in die freien Bytes des vorigen Stücks passen (dorthin kopiert). Gemessen mit echtem
  LAME bei 64–320 kbit/s: gleiche Dateigröße und Qualität wie in einem Stück kodiert, an den Nähten höchstens 1,14×
  das normale Kodierrauschen in einem einzigen 24-ms-Rahmen, keine Knackser; ab der Naht sind die Rahmen meist sogar
  bitgleich. LAME schafft je Kern grob 50–100× Echtzeit; mit 4 Kernen wird eine lange Datei rechnerisch etwa
  3,5× so schnell umgewandelt (der Vorlauf kostet ~15 %).
- **Pausieren und fortsetzen** – einzeln, alle auf einmal oder aus der Benachrichtigung. Fertige Kapitel/Videos
  bleiben, die angefangene Datei wird per HTTP-Range (bei YouTube stückweise) an der Stelle fortgesetzt, an der sie
  stand – jede der 4 Verbindungen für sich; abgebrochene Verbindungen werden ebenso fortgesetzt.
- **Bleiben über das Schließen der App hinaus:** die Auftragsliste samt Fortschritt liegt auf dem Gerät
  (`download-jobs.json`, angefangene Dateien in einem Ordner, den Android nicht von selbst leert). Wird die App beendet
  oder vom System geschlossen, sind die Aufträge beim nächsten Öffnen wieder da und laufen von selbst weiter – fertige
  Kapitel werden nicht neu geladen, das angefangene geht an seiner Stelle weiter. Beendet Android nur den
  Hintergrunddienst, startet er neu und lädt weiter (wo Android das nicht erlaubt, bleibt eine Nachricht mit
  „Fortsetzen“). Pausierte Aufträge bleiben pausiert, erledigte stehen bis „Erledigte entfernen“ in der Liste.
- Alles landet als Hörbuch in der Bibliothek. **Speicherort wählbar** („Ordner wählen“): standardmäßig
  **Audiobooks/Lauscher/<Autor – Titel>/** (Android 10+, sichtbar für andere Apps), oder ein eigener Ordner – etwa
  auf der SD-Karte oder der Hörbuch-Ordner der Bibliothek. Liegt der Ordner in einem Bibliotheksordner, bekommt
  das Buch denselben Schlüssel wie beim Einlesen, es erscheint also nicht doppelt. Downloads laufen im Hintergrund
  weiter (Benachrichtigung mit Fortschritt), einzelne Fehler in einer Playlist brechen den Rest nicht ab.
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

### Sprachsteuerung (Hände frei, auch mit dem Handy in der Tasche)
- „**Lauscher, Pause**“, „Lauscher, weiter“, „Lauscher, 30 Sekunden zurück“, „… nächstes Kapitel“, „… lauter“,
  „… schneller“, „… Schlaftimer 20 Minuten“, „… Lesezeichen“, „… wo bin ich?“ (wird angesagt), „… rückgängig“
- **Eigene Befehle:** jede Aktion ein- und ausschaltbar, eigene Wörter hinzufügen oder entfernen, eigenes
  Aktivierungswort (oder ganz ohne – dann zählen nur kurze Befehle, ein Satz mit „weiter“ darin nicht)
- **Erkannt wird auf dem Handy** mit dem Whisper-Modell der Szenen-Analyse; die Befehlswörter werden der
  Erkennung als Hinweis mitgegeben. Ein Sprach-Detektor schneidet kurze Äußerungen aus dem Mikrofon (alles
  über 5 s – Gespräche, Musik – wird verworfen), nur diese kommen in die Erkennung. Wurde das Aktivierungswort
  gehört, der Befehl aber nicht verstanden, hört die Erkennung ein zweites Mal genauer hin (Beam-Suche).
  Nichts wird gespeichert oder gesendet.
- **Audioeingang:** Mikrofon automatisch / Handy / Kabel-Headset / Bluetooth-Headset (mit Hinweis, dass viele
  Bluetooth-Kopfhörer dann in Telefonqualität spielen), Echo-Unterdrückung, nur mit Kopfhörern zuhören,
  Empfindlichkeit, Hörbuch beim Sprechen leiser, Rückmeldung (Ton, Ansage, keine), wie lange nach einer Pause
  weiter zugehört wird, Sprache der Befehle
- Läuft als Vordergrund-Dienst mit Mikrofon (Android zeigt den Mikrofon-Punkt); Android erlaubt den Start nur,
  während die App offen ist – danach hört sie auch bei ausgeschaltetem Bildschirm weiter
- Getestet: echte deutsche Sprachausgabe (espeak-ng) mit Rauschen läuft in der CI durch die ganze Kette
  (Detektor → Whisper Base → Befehle → Player); der Detektor trennt die 7 Äußerungen bei jeder Empfindlichkeit
  richtig, ein Satz ohne Aktivierungswort löst nichts aus

### Ausgabe
- **Ausgabegerät** wählbar: automatisch, Handy-Lautsprecher, Kabel-/USB-Kopfhörer oder Bluetooth
- **Bei Ansagen leiser statt Pause:** Navigation und Nachrichten machen das Hörbuch kurz leiser, statt es
  anzuhalten
- Pause beim Trennen der Kopfhörer abschaltbar; **weiterspielen, wenn sie wieder verbunden sind** (innerhalb
  einer halben Stunde)
- **Kopfhörer-Raumklang (Crossfeed):** jedes Ohr hört das andere leise, tiefpassgefiltert und um 0,3 ms
  verzögert mit (wie Lautsprecher im Raum) – hart links/rechts gemischte Aufnahmen ermüden weniger; mittige
  Stimmen behalten ihre Lautstärke
- Dazu im Klangstudio: Lautstärke-Boost, Mono, Balance, Kompressor, Equalizer

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
- **Übersichtlich in Reitern:** *Jetzt* (Stimmungskarte, Lautstärke, was die KI gerade macht), *Musik* (Art,
  wie genau die Musik folgt, Klangquelle, Mischung, Hörproben), *KI* (Szenen-Analyse, Stichproben, Modelle) und
  *Daten* (Rückmeldungen, Löschen); die Musikbibliothek hat *Laden*, *Stücke* und *Herkunft*.
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
- **Liest voraus:** Beim Hören analysiert die KI im Hintergrund die nächsten Minuten (einstellbar 5–60, Standard
  15) – die Musik steht also fest, bevor du an der Stelle bist. Der erste Durchgang nimmt nur zwei Abschnitte,
  damit die Musik für die aktuelle Stelle schnell bereit ist. Ganze Bücher lassen sich auch vorab analysieren.
- **Reiter „KI-Analyse“** in den Buch-Details (auch aus dem Player über „Alle Szenen“): wie weit vorausgelesen
  ist („bis 1:23:00 – 38 Min. vor dir“), Stimmungskarte, „Was bisher geschah“, ein Überblick über Orte und
  Gefühle des Buchs und jede Szene als Stichpunkte – Zeit, Titel, Ort, Gefühl, Spannung, welche Musik läuft
  und ein Auszug des Texts. Tippen springt zur Szene, der Stift korrigiert sie.
- **KI-Protokoll – sehen, was die KI macht:** Ein Textfenster im Reiter „KI-Analyse“ zeigt live, Zeile für
  Zeile, was die KI gerade tut. Die ganze Seite *KI-Protokoll* (auch über *Hintergrundmusik*) hat Filter
  (Zuhören, Sprachmodell, Szenen, Musik, Probleme); ein Tipp auf einen Eintrag zeigt den vollständigen Text.
  Protokolliert werden:
  - was die Spracherkennung gehört hat (bzw. welcher Buchtext gelesen wurde);
  - die vollständige Frage an das Sprachmodell und seine rohe Antwort (JSON), mit Rechenzeit;
  - welche Szene daraus wurde (Gefühl, Spannung, Ort, Titel), und ob der Ort aus Ortswörtern stammt;
  - jeder genauer gesuchte Szenenwechsel mit den einzelnen A/B-Fragen;
  - die Tempoprobe und der Rückblick;
  - **warum welche Musik spielt**, z. B. „Spannend · Spannung 2 von 3 · Ort „Kerker“ → spielt „…“ (Komponist) –
    Gefühl passt genau · für den Ort geschrieben · Energie passt zur Spannung“, oder warum ein Stück
    weiterläuft bzw. der Synthesizer einspringt.

  Das Protokoll lässt sich als Text kopieren. Es bleibt nur im Speicher (die letzten 400 Schritte) und verlässt
  das Gerät nicht.
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

### Musikbibliothek nach Stimmung, Ort und Spannung (echte Fantasy-Musik, keine KI)
Statt des Synthesizers kann die Hintergrundmusik aus **476 handkomponierten Fantasy-Stücken** (rund 1,4 GB, gut
23 Stunden mit Schleifen) kommen – Soundtracks freier Spiele. Alles ist von Menschen komponiert und eingespielt,
nichts davon ist KI-generiert (Veloren verbietet KI-Inhalte sogar ausdrücklich in seinen Beitragsregeln).
Cartoon-, Großstadt- und Weltraum-Musik (SuperTux, Hedgewars, Pioneer) wurde wieder entfernt, weil sie nicht zu
Fantasy-Hörbüchern passt.

| Quelle | Stücke | Lizenz | Schwerpunkt |
|---|---|---|---|
| [The Battle for Wesnoth](https://github.com/wesnoth/wesnoth) | 38 | GPL v2+, CC BY-SA 4.0 | Orchester: Schlacht, Burg, Wald, Höhle, Elfenland |
| [Widelands](https://github.com/widelands/widelands) | 31 | GPL v2+ | Mittelalter, Dorf, Wüste, Gebirge |
| [Flare](https://github.com/flareteam/flare-game) | 11 | CC BY-SA 3.0 | Rollenspiel: Stadt, Wald, Höhle, Verlies |
| [Veloren](https://github.com/veloren/veloren) | 80 | GPL v3 | Landschaften je Biom, Siedlungen, Höhlen, Verliese, Kampfmusik in drei Teilen |
| [Shattered Pixel Dungeon](https://github.com/00-Evan/shattered-pixel-dungeon) | 31 | GPL v3 | Kerker, Kanalisation, Höhlen, Dämonenhallen – je ruhig, angespannt, Bosskampf |
| [Stendhal](https://github.com/arianne/stendhal) | 36 | GPL v2, CC0, CC BY 3.0/4.0, OGA-BY | Taverne, Markt, Kirche, Kerker, Mine, Spukwald |
| [0 A.D.](https://github.com/0ad/0ad) | 60 | CC BY-SA 3.0 (Wildfire Games) | Antike: Griechen, Römer, Kelten, Ägypten, Persien, Indien, China |
| [Duelyst](https://github.com/open-duelyst/duelyst) | 21 | CC0 | Orchestraler Soundtrack des Fantasy-Kartenspiels |
| [HyperRogue](https://github.com/zenorogue/hyperrogue) | 17 | CC BY-SA 3.0 | Wüste, Eis, Höhlen, Dschungel, Friedhof, Hölle, Palast, Ozean |
| [Tuxemon](https://github.com/Tuxemon/Tuxemon) (Yubatake u. a.) | 16 | CC BY 3.0 | JRPG-Sammlung: Felder, Stadt, Taverne, Tempel, Verlies, Kampf |
| [Valyria Tear](https://github.com/ValyriaTear/ValyriaTear) | 12 | CC BY(-SA) 3.0, CC0, GPL | Matthew Pablo, HorrorPen, Alexandr Zhelanov u. a. |
| [Wyrmsun](https://github.com/Andrettin/Wyrmsun) | 8 | CC BY 4.0 | Zwergen- und Germanen-Themen von Marcelo Fernandez |
| [MegaGlest](https://github.com/MegaGlest/megaglest-data) | 7 | CC BY-SA 3.0 | Magie, Ritter, Nordmänner, Römer, Ägypten, Persien |
| [Ardentryst](https://github.com/ardentryst/ardentryst) | 13 | CC BY 3.0 | Stadt, Schnee, Burg, Kampf, Weltkarte |
| [The Mana World](https://github.com/themanaworld/tmw-music) | 22 | GPL v2, CC BY-SA 3.0, CC BY 4.0 | Wald, Höhlen, Taverne, Schneedorf, Meer |
| [Moubootaur Legends](https://gitlab.com/TMW2/evol-music) | 30 | CC0, CC BY 2.0–4.0, GPL (je Stück) | Stücke vieler freier Komponisten |
| [Invertika](https://github.com/invertika/data) | 43 | GPL v3 (Teile GPL v2) | Burg, Wüste, Höhlen, Wald, Hafen, Kirche |

- **Zufällige Auswahl je Ort:** Für jede Szene kommen alle Stücke in einen „Beutel“, die für ihren Ort und ihr
  Gefühl geschrieben sind und ungefähr zur Spannung passen; daraus wird zufällig gezogen, und ein Stück kommt erst
  wieder, wenn die anderen dran waren (Lieblingsstücke etwas früher). Hat ein Ort zu wenig Eigenes, kommen verwandte
  Orte und Stücke ohne festen Ort dazu. Bei „Genau“ ist der Beutel kleiner (nur die am besten passenden Stücke).
- **26 Orte und Themen:** Wald, Dschungel, Draußen, Gebirge, Schnee und Eis, Wüste, Meer, Unter Wasser, Höhle,
  Kerker, Burg, Dorf und Stadt, Taverne, Zuhause, Schlacht, Tempel, Zauberwelt, Spukort, Orient, Fernost, Antike,
  Norden; für Wilder Westen, Weltraum und Großstadt gibt es keine eigene Fantasy-Musik, dort spielen verwandte
  Orte. Die lokale KI erkennt neben der Stimmung auch den Ort jeder
  Szene (sonst helfen Ortswörter im Text); in der Stimmungskarte lässt sich beides korrigieren.
- **Verschiedene Stücke je Spannung im selben Gebiet:** Im Kerker etwa ruhiges Erkunden, dann „angespannt“,
  dann Bosskampf; Veloren-Kampfmusik spielt Anlauf, Schleife und Ende nacheinander.
- **Bei jedem Szenenwechsel neu gewählt:** Ändert sich Ort, Stimmung oder Spannungsstufe, wird zu einem
  passenden Stück übergeblendet; bleibt alles gleich, läuft das Stück weiter. Die Auswahl richtet sich zuerst
  nach der Stimmung, dann nach dem Ort (sonst verwandte Orte, z. B. Wüste und Prärie für den Wilden Westen,
  für den es noch keine eigenen freien Stücke gibt), dann nach der Spannung, ohne Wiederholungen.
- **Wählbar, wie genau die Musik der Geschichte folgt** (*Hintergrundmusik → Wie genau die Musik der Geschichte
  folgt*):
  - **Ruhig:** Ein Stück läuft in die nächste Szene weiter, solange es nach seinen eigenen Stimmungen, Orten
    und seiner Energie auch dorthin passt. Erst ein deutlicher Umschwung (z. B. ruhig → Kampf) bringt ein neues
    Stück. Das nächste Stück wird dann für die Szene gewählt, die gerade läuft. Auch der Synthesizer spielt
    weiter, solange das Gefühl bleibt.
  - **Ausgewogen** (wie bisher): neues Stück bei anderem Gefühl, Ort oder Spannungsstufe; bei der Wahl zählt
    auch Abwechslung.
  - **Genau:** Jede Änderung, auch eine kleine der Spannung, bringt ein neues Stück. Stücke, die für dieses
    Gefühl und diesen (oder einen verwandten) Ort geschrieben sind, haben Vorrang, und die Spannung zählt mehr.
    Der Zufall für Abwechslung ist fast aus, es gewinnt das am besten passende Stück.
- Kurze Spielmusik-Schleifen werden an ihren Schleifenpunkten mehrmals gespielt (mit winzigen
  Überblendungen gegen Knackser) und am Ende ausgeblendet.
- Optional **Geräusche passend zum Ort** (Wald, Meer, Wind, Kaminfeuer) leise unter der Musik.
- **Laden:** Starterpaket (die passendsten Stücke je Ort und Stimmung), Pakete je Stimmung
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
