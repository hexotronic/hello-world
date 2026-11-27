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

Standard-Player und Startmethode lassen sich in den Einstellungen ändern
(Add-ons verwalten → VidGrab → Einstellungen). Falls der Knopf auf deinem Gerät nichts tut, stelle die
Startmethode auf „Direkt aus dem Popup“ oder „Über eine Startseite im neuen Tab“.

## Installation auf Android

Firefox für Android installiert nur **signierte** Add-ons (außer Nightly/Beta mit geänderter Konfiguration).

### Variante A – signieren lassen und dauerhaft installieren (empfohlen)

1. Bei <https://addons.mozilla.org/developers/addon/api/key/> API-Schlüssel erzeugen (kostenloses Konto).
2. Auf einem Rechner im Ordner `vidgrab/`:
   ```bash
   npm install
   npx web-ext sign --source-dir extension --channel unlisted \
     --api-key "DEIN_JWT_ISSUER" --api-secret "DEIN_JWT_SECRET"
   ```
   Die signierte `.xpi` liegt danach in `web-ext-artifacts/`. „unlisted“ heißt: Mozilla signiert nur,
   das Add-on wird nicht öffentlich gelistet.
3. Die `.xpi` aufs Handy kopieren.
4. Firefox: **Einstellungen → Über Firefox → 5× auf das Firefox-Logo tippen** (schaltet das Debug-Menü frei).
5. Zurück zu den Einstellungen → **„Erweiterung aus Datei installieren“** → `.xpi` auswählen.

### Variante B – Firefox Nightly/Beta ohne Signatur

In `about:config` `xpinstall.signatures.required` auf `false` setzen, dann wie oben ab Schritt 4 die
unsignierte `.xpi` aus `npm run build` (liegt in `dist/`) installieren.

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
npm run build     # erzeugt dist/vidgrab-<version>.xpi (unsigniert)
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
