# Florakarten

Karteikarten-App für Android (und jeden Browser) mit Wiederholung in wachsenden Abständen, wie man es von klassischen Lernkarten-Apps kennt, plus Extras für Pflanzen. Für den privaten Gebrauch, komplett offline, ohne Konto und ohne Server: alle Karten und Fotos bleiben auf deinem Gerät.

## Funktionen

**Lernen wie gewohnt**
- Stapel mit Zählern für neue, lernende und fällige Karten
- Bewertung mit *Nochmal / Schwer / Gut / Einfach*, die Abstände stehen auf den Knöpfen
- Lernschritte, Tageslimits, „heute mehr neue Karten“, Tagesbeginn um 4 Uhr (einstellbar)
- Rückgängig, markieren, für heute zurückstellen, aussetzen, bearbeiten mitten in der Sitzung
- *Pauken*: alle Karten eines Stapels oder einer Auswahl durchgehen, ohne die Planung zu verändern
- Wiederholt vergessene Karten werden automatisch markiert und mit `#schwierig` getaggt

**Zufallsfotos**
- Zufällige gespeicherte Fotos aus allen oder einzelnen Stapeln erkennen: aufdecken, „gewusst“ oder „nicht gewusst“
- Filter: markierte Karten, Schlagwort, „blüht diesen Monat“, schwierige Karten
- Jedes Foto einzeln oder ein Foto pro Pflanze, als Runde oder als **Diashow** mit automatischem Aufdecken
- Auswertung mit „Fehler wiederholen“ und „Fehler markieren“
- **Galerie**: alle Fotos gemischt, Namen verdeckt, Antippen deckt auf

**Eigene Kartennamen zum Wiederfinden**
- Jede Karte hat ein Feld *Kartenname*, z. B. „Eiche am Spielplatz“. Es erscheint beim Lernen erst auf der Rückseite, verrät also nichts.
- Schlagwörter mit Vorschlägen, Suche über Name, botanischen Namen, Familie, Merkmale, Notizen und Schlagwörter
- Filter (fällig, neu, markiert, mit/ohne Fotos, blüht jetzt, oft vergessen, ohne Namen) und Sammelaktionen (verschieben, taggen, markieren, löschen)
- Hinweis, wenn es eine Pflanze schon gibt

**Pflanzen-Extras**
- Kartentyp *Pflanze* mit deutschem und botanischem Namen, Familie (mit Vorschlagsliste), Merkmalen, Blütezeit, Standort, Giftigkeit, Verwendung, Verwechslungsgefahr und Notizen
- Rückseite als Herbar-Etikett mit Blühkalender, botanische Namen kursiv, Vorlesen per Sprachausgabe
- Mehrere Fotos pro Pflanze mit Beschriftung (Blüte, Blatt, Frucht, Rinde …); beim Lernen kommt jedes Mal ein anderes
- Stufenweise Tipps: Familie → Anfangsbuchstabe und Länge → erste Silbe
- Antwort eintippen mit Tippfehler-Erkennung (z. B. „Gänseblümchn“ gilt als fast richtig)
- **Training** ohne Einfluss auf die Planung: Foto → Name (Auswahl oder Eintippen), Deutsch ↔ Botanisch, Foto → Familie, Merkmale → Name. Im Modus *Knifflig* stammen die falschen Antworten bevorzugt aus derselben Familie.
- **Fotos schnell erfassen**: viele Fotos auf einmal auswählen, nacheinander benennen, weitere Fotos derselben Pflanze mit einem Tipp anhängen
- „Blüht im November“-Hinweis auf der Startseite

**Cloud-Speicher: eigenes Google Drive**
- In der Android-App unter *Einstellungen → Cloud-Speicher* eine Datei im eigenen Google Drive anlegen (Android-Dialog „Speichern unter“ → ☰ → Drive → Konto und Ordner wählen)
- Danach sichert die App automatisch: etwa eine Minute nach Änderungen, nach jeder Lernrunde und beim Verlassen der App
- „Aus der Cloud wiederherstellen“ und „Bestehende Sicherung verbinden“ (z. B. auf einem neuen Handy), wahlweise zusammenführen oder ersetzen
- Ohne Google-Entwicklerkonto und ohne Server: Die App schreibt nur in die eine Datei, die du ausgewählt hast. Andere Cloud-Speicher mit Android-Anbindung (z. B. OneDrive, Nextcloud) funktionieren genauso.

**Sonstiges**
- Statistik: Lernserie, Behaltensquote, Kartenbestand, Prognose der nächsten 14 Tage, Verlauf der letzten 30 Tage, am häufigsten vergessene Karten
- Sicherung als JSON-Datei inklusive Fotos und Lernverlauf; Einspielen per Zusammenführen oder Ersetzen
- Text-Import (eine Karte pro Zeile, getrennt mit `;`, Tab oder Komma)
- Hell/Dunkel, funktioniert offline, Beispielstapel „Wiesenpflanzen“ mit gezeichneten Beispielbildern

## Installieren

### Variante A: Android-App (APK)
1. Auf GitHub unter **Actions → „Android-APK bauen“** den neuesten erfolgreichen Lauf öffnen.
2. Unten bei *Artifacts* **florakarten-apk** herunterladen (ZIP) und entpacken.
3. `florakarten.apk` auf dem Handy öffnen und die Installation aus dieser Quelle erlauben.

Der Build startet automatisch bei jeder Änderung an der App. Er lässt sich auch von Hand über *Run workflow* starten.
Die APK ist nicht im Play Store, sondern mit einem eigenen Schlüssel signiert. Damit neue Versionen als Update über die alte gehen (und die Daten bleiben), muss der Schlüssel bei jedem Build derselbe sein:
- **Empfohlen:** Den Schlüssel als Base64-Text im Repository-Secret `FLORAKARTEN_KEYSTORE` hinterlegen (*Settings → Secrets and variables → Actions → New repository secret*). Der Schlüssel gehört nie ins Repository selbst.
- Ohne Secret merkt sich GitHub den Schlüssel im Build-Zwischenspeicher. Der wird aber nach einer Woche ohne Build gelöscht; dann muss die App einmal neu installiert werden (vorher sichern).

> Wichtig: Wird die App deinstalliert, sind die Daten auf dem Handy weg. Mit eingerichtetem Cloud-Speicher holst du sie danach über *Bestehende Sicherung verbinden* zurück.

### Variante B: Als Web-App (auch iPhone)
1. Einmalig auf GitHub: **Settings → Pages → Source: „GitHub Actions“**.
2. Unter **Actions → „Web-App veröffentlichen“ → Run workflow** starten.
3. Die angezeigte Adresse im Handy-Browser öffnen und *Zum Startbildschirm hinzufügen*. Danach funktioniert sie auch offline.

### Variante C: Lokal am Rechner
```bash
npm start            # startet http://localhost:8123
```

## Daten & Sicherung
- Alles liegt in der Browser- bzw. App-Datenbank (IndexedDB) auf deinem Gerät. Fotos werden beim Hinzufügen auf max. 1600 px verkleinert.
- *Einstellungen → Sicherung* erstellt eine JSON-Datei mit allen Stapeln, Karten, Fotos und dem Lernverlauf. Damit kannst du auch zwischen Handy und Browser umziehen.
- Einzelne Stapel lassen sich über das Stapel-Menü (⋮) exportieren.

## Tastatur (am Rechner)
Leertaste/Enter = aufdecken bzw. „Gut“, `1`–`4` = Bewertung, `H` = Tipp, `M` = markieren, `E` = bearbeiten, `Z` = rückgängig.

## Aufbau
```
www/                 Die App (HTML, CSS, JavaScript ohne Build-Schritt)
  js/srs.js          Lernplanung (SM-2 mit Lernschritten)
  js/db.js           Speicher (IndexedDB)
  js/views/          Ansichten: Stapel, Lernen, Editor, Suche, Zufallsfotos, Training, Statistik, Einstellungen
  sw.js              Offline-Cache
android/             Android-Projekt (Capacitor), lädt die App aus www/
.github/workflows/   APK-Build und Web-Veröffentlichung
```

Android-APK lokal bauen (Android SDK und JDK 21 nötig): `npm ci && npm run android:build`.

Schriften: Bricolage Grotesque und Newsreader (SIL Open Font License). Capacitor (MIT).
