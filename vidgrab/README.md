# VidGrab – Videos extern abspielen (Firefox für Android)

Firefox-Add-on, das Video-Dateien und Streams auf Webseiten findet und sie mit einem Fingertipp in
**VLC**, MX Player, mpv, Just Player, Kodi oder einer beliebigen anderen App öffnet.
Läuft auch in Firefox für den Desktop (dort mit „Link kopieren“ und „M3U für VLC“).

## Funktionen

- **Erkennt** MP4, WebM, MKV, MOV, … sowie **HLS** (`.m3u8`) und **DASH** (`.mpd`) – über die
  Netzwerkantworten (Content-Type) *und* über das Seiten-DOM (`<video>`, `<source>`, Links, `og:video`).
- **Filtert Ballast**: HLS/DASH-Segmente (`.ts`, `.m4s`, …), Blob-URLs, Vorschau-Clips unter 1 MB.
- **Analysiert Streams**: Qualitäten (1080p, 720p, …) als Direktstart-Knöpfe, Dauer, Live, DRM-Warnung.
- **Startet den Player** per Android-Intent (`intent://…;package=org.videolan.vlc;type=video/*`),
  inkl. Seitentitel als Videotitel.
- **„Andere App“** öffnet die Android-App-Auswahl, **„Kopieren“** und **„Teilen“** für alles andere.
- **„M3U“** speichert eine Playlist mit Referer und User-Agent der Seite – damit spielt VLC auch Streams ab,
  die ohne diese Header mit *403 Forbidden* antworten.
- Zähler am Add-on-Symbol, helles und dunkles Design, keine Datensammlung, keine externen Server.

## Bedienung

1. Seite mit Video öffnen und das Video **kurz abspielen** (viele Seiten laden den Stream erst dann).
2. Firefox-Menü (⋮) → **Erweiterungen** → **VidGrab**.
3. Beim gewünschten Eintrag auf **„In VLC öffnen“** tippen. Firefox fragt evtl. „In App öffnen?“ → bestätigen.

**Noch schneller:** Auf jedem Video im Browser sitzt oben rechts ein kleines, halbtransparentes Symbol (▶↗).
**Tippen** öffnet das Video direkt in VLC bzw. der eingestellten App, **lange drücken** zeigt alle gefundenen
Videos. Läuft das Video, blendet sich das Symbol nach ein paar Sekunden fast ganz aus und erscheint bei
Berührung wieder.
Läuft das Video im Browser über einen Stream (blob:), nimmt der Knopf automatisch den besten erkannten
Stream der Seite. Abschaltbar in den Einstellungen („Knopf direkt am Video anzeigen“).

**Firefox soll nicht jedes Mal nachfragen?** Das ist eine Firefox-Einstellung, kein Add-on-Verhalten:
Firefox → Einstellungen → **„Links in Apps öffnen“** → **„Immer“**. (Nebenwirkung: auch normale Links,
z. B. zu YouTube oder Maps, öffnen dann direkt die passende App. In privaten Tabs fragt Firefox immer.)

**4K ruckelt?** VidGrab → Einstellungen → **„Maximale Qualität beim Abspielen“** (Standard: 1080p).
Bei Streams mit mehreren Qualitäten (HLS) startet VidGrab dann direkt die passende Stufe statt 4K – das
Popup zeigt „startet mit 1080p“. Hat ein Stream die Tonspur getrennt, bleibt es beim Original (sonst fehlte
der Ton); dann wählt VLC selbst. Bei DASH-Streams und einzelnen MP4-Dateien gibt es nichts zu wählen.
Zusätzlich hilft in VLC: Einstellungen → Video → **Hardwarebeschleunigung** auf „Automatisch“ bzw.
„Vollständig“ und unter Erweitert den **Netzwerk-Cache** erhöhen (z. B. 3000 ms).

**Video läuft in VLC nur ein paar Sekunden?** Meist wurde ein Werbe-/Vorschau-Clip oder nur ein Teilstück
erwischt. Über **„⋯“** bzw. das VidGrab-Menü einen anderen Eintrag wählen (HLS-Stream oder die größte
Datei). Bricht ein Stream nach kurzer Zeit mit Fehler ab, verlangt der Server meist den Referer der
Seite → **„M3U“** speichern und die Datei mit VLC öffnen.

Standard-Player und Startmethode lassen sich in den Einstellungen ändern
(Add-ons verwalten → VidGrab → Einstellungen). Falls der Knopf auf deinem Gerät nichts tut, stelle die
Startmethode auf „Direkt aus dem Popup“ oder „Über eine Startseite im neuen Tab“.

## Installation auf Android

Zwei Dinge muss man wissen:

1. **Normales Firefox installiert nur von Mozilla signierte Add-ons.** Unsignierte Dateien gehen nur in
   Firefox **Nightly** (oder Beta) mit geänderter Einstellung.
2. **Datei lässt sich nicht auswählen (ausgegraut)?** Der Datei-Dialog von Firefox erkennt `.xpi` auf
   manchen Geräten nicht. Dann die **`.zip`-Variante** nehmen (gleicher Inhalt) bzw. die Datei in `.zip`
   umbenennen.

Das Menü „Erweiterung aus Datei installieren“ ist versteckt:
**Einstellungen → Über Firefox → mehrmals (5–10×) auf das Firefox-Logo tippen**, bis
„Debug-Menü aktiviert“ erscheint. Danach steht es in den Einstellungen.

### Variante A – schnell, ohne Konto: Firefox Nightly

1. „Firefox Nightly for Developers“ aus dem Play Store installieren.
2. In Nightly `about:config` öffnen, nach `xpinstall.signatures.required` suchen und auf **false** setzen.
3. Debug-Menü freischalten (siehe oben) → **„Erweiterung aus Datei installieren“** →
   `vidgrab-<version>.zip` auswählen.

### Variante B – dauerhaft im normalen Firefox: von Mozilla signieren lassen

Geht komplett im Handy-Browser, kostenlos:

1. Auf <https://addons.mozilla.org/developers/> mit einem Mozilla-Konto anmelden.
2. **„Submit a New Add-on“** → **„On your own“** wählen (= nicht öffentlich, nur signieren).
3. `vidgrab-<version>.xpi` (oder `.zip`) hochladen, „Firefox for Android“ als Plattform anhaken, absenden.
4. Nach der automatischen Prüfung (meist wenige Minuten) unter *Manage Status & Versions* die
   **signierte Datei herunterladen**.
5. Falls nötig in `.zip` umbenennen (z. B. mit der App „Dateien“), dann im normalen Firefox:
   Debug-Menü freischalten → **„Erweiterung aus Datei installieren“** → Datei auswählen.

Alternativ vom Rechner aus: `npx web-ext sign --source-dir extension --channel unlisted --api-key … --api-secret …`
(API-Schlüssel unter <https://addons.mozilla.org/developers/addon/api/key/>).

### Variante C – Entwicklertest per USB

USB-Debugging am Handy aktivieren, in Firefox „Remote-Debugging über USB“ einschalten, dann:

```bash
npm run start:android -- --android-device <GERÄTE-ID>   # Geräte-ID: adb devices
```

Das Add-on ist dann bis zum Beenden von Firefox geladen.

## Grenzen

- **DRM** (Netflix, Prime Video, Disney+, viele Mediatheken mit Widevine): nicht extern abspielbar.
  VidGrab zeigt in dem Fall eine Warnung.
- **YouTube** & Co. liefern Bild und Ton als getrennte Häppchen über Media Source Extensions –
  dafür ist VidGrab nicht gedacht.
- Manche Server verlangen **Cookies**; die kann ein externer Player nicht mitschicken.
  Gegen Referer-Sperren hilft der **M3U**-Knopf.
- Signierte Links laufen oft nach einiger Zeit ab – dann die Seite neu laden.

Bitte nur Inhalte abspielen, für die du die Rechte hast.

## Entwicklung

```bash
npm install       # web-ext
npm test          # Unit-Tests (Node, ohne Browser)
npm run lint      # Mozilla-Prüfung des Add-ons
npm run build     # erzeugt dist/vidgrab-<version>.xpi und .zip (unsigniert, gleicher Inhalt)
npm run icons     # PNG-Symbole aus icons/icon.svg rendern (benötigt Playwright)
```

Aufbau:

| Datei | Aufgabe |
| --- | --- |
| `extension/manifest.json` | Manifest V2, Android-kompatibel (`gecko_android`) |
| `extension/src/background.js` | beobachtet Netzwerkantworten, hält die Liste pro Tab, analysiert m3u8/mpd |
| `extension/src/content.js` | sucht Videos im DOM, startet den Intent im Tab |
| `extension/src/common/media.js` | reine Hilfsfunktionen (Erkennung, Parser, Intent, M3U) – auch für Tests |
| `extension/src/popup/` | Popup mit Fundliste |
| `extension/src/options/` | Einstellungen |
| `extension/src/launch/` | Startseite als Ausweichweg für den Intent |
