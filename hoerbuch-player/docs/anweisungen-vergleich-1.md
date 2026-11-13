<!-- Erster Vergleich der KI-Anweisungen (vorher = bis 12.11.2026, neu = erste genauere Fassung) vom 13.11.2026, Lauf 43399532139 (Commit 4344cf3). Ergebnis: gemischt – Qwen3 4B besser, Gemma 3 4B schlechter (abgeschnittenes JSON). Daraufhin wurden Grammatik, Antwortlänge und die Zeitangaben verbessert, siehe anweisungen-vergleich.md. -->

## Lauscher – Sprachmodelle im Vergleich

Gemessen auf einem GitHub-ARM-Rechner (Neoverse-N2, 4 Kerne) mit 4 Threads, llama.cpp wie in der App (ARMv8.2 dotprod+fp16, gierig, JSON-Grammatik). Testgeschichte: 16 Abschnitte, 8 Fragen, 9 Befehle in Alltagssprache.

### Verstehen

| Modell | Datei | Verstehen | Stimmung | Ort | Stärke | Musik-Merkmale | Fragen | Spoiler-sicher | Befehle | JSON gültig |
|---|---|---|---|---|---|---|---|---|---|---|
| **Qwen2.5 1.5B · vorher** (mittel, empfohlen (in der App)) | 1117 MB | **45 %** | 38 % | 14 % | 75 % | 36 % | 75 % | 100 % | 33 % | 100 % |
| **Qwen2.5 1.5B · neu** (mittel, empfohlen (in der App)) | 1117 MB | **43 %** | 38 % | 14 % | 62 % | 21 % | 75 % | 100 % | 44 % | 100 % |
| **Qwen2.5 3B · vorher** (stärkstes (in der App)) | 2105 MB | **70 %** | 88 % | 50 % | 94 % | 57 % | 100 % | 100 % | 33 % | 100 % |
| **Qwen2.5 3B · neu** (stärkstes (in der App)) | 2105 MB | **70 %** | 81 % | 43 % | 100 % | 50 % | 88 % | 100 % | 56 % | 100 % |
| **Qwen3 1.7B · vorher** (neuer, mittel) | 1107 MB | **73 %** | 81 % | 43 % | 94 % | 79 % | 88 % | 100 % | 56 % | 100 % |
| **Qwen3 1.7B · neu** (neuer, mittel) | 1107 MB | **71 %** | 75 % | 36 % | 94 % | 79 % | 75 % | 100 % | 67 % | 100 % |
| **Qwen3 4B Instruct 2507 · vorher** (neuer, groß) | 2497 MB | **86 %** | 100 % | 64 % | 100 % | 50 % | 100 % | 100 % | 100 % | 100 % |
| **Qwen3 4B Instruct 2507 · neu** (neuer, groß) | 2497 MB | **94 %** | 100 % | 86 % | 100 % | 100 % | 100 % | 100 % | 78 % | 100 % |
| **Gemma 3 4B · vorher** (neuer, groß) | 2490 MB | **82 %** | 81 % | 57 % | 100 % | 86 % | 100 % | 100 % | 67 % | 100 % |
| **Gemma 3 4B · neu** (neuer, groß) | 2490 MB | **55 %** | 38 % | 43 % | 50 % | 36 % | 88 % | 100 % | 78 % | 50 % |

### Anweisungen „vorher“ → „neu“

| Modell | Verstehen | Stimmung | Ort | Stärke | Merkmale | Fragen | Befehle | Zeit je Frage |
|---|---|---|---|---|---|---|---|---|
| Qwen2.5 1.5B | 45 % → **43 %** (-3) | 38 % → **38 %** (+0) | 14 % → **14 %** (+0) | 75 % → **62 %** (-12) | 36 % → **21 %** (-14) | 75 % → **75 %** (+0) | 33 % → **44 %** (+11) | 5.5 → 6.1 s |
| Qwen2.5 3B | 70 % → **70 %** (-1) | 88 % → **81 %** (-6) | 50 % → **43 %** (-7) | 94 % → **100 %** (+6) | 57 % → **50 %** (-7) | 100 % → **88 %** (-12) | 33 % → **56 %** (+22) | 11.8 → 12.1 s |
| Qwen3 1.7B | 73 % → **71 %** (-2) | 81 % → **75 %** (-6) | 43 % → **36 %** (-7) | 94 % → **94 %** (+0) | 79 % → **79 %** (+0) | 88 % → **75 %** (-12) | 56 % → **67 %** (+11) | 6.4 → 6.8 s |
| Qwen3 4B Instruct 2507 | 86 % → **94 %** (+8) | 100 % → **100 %** (+0) | 64 % → **86 %** (+21) | 100 % → **100 %** (+0) | 50 % → **100 %** (+50) | 100 % → **100 %** (+0) | 100 % → **78 %** (-22) | 14.9 → 15.7 s |
| Gemma 3 4B | 82 % → **55 %** (-27) | 81 % → **38 %** (-44) | 57 % → **43 %** (-14) | 100 % → **50 %** (-50) | 86 % → **36 %** (-50) | 100 % → **88 %** (-12) | 67 % → **78 %** (+11) | 14.3 → 14.8 s |

Im Mittel: Verstehen 71 % → **66 %**.

### Geschwindigkeit

Handy-Spalten: geschätzt für Xiaomi 13T (Dimensity 8200-Ultra, 4× Cortex-A78) als 50–75 % der gemessenen Geschwindigkeit (langsamerer Speicher, Wärmedrosselung). Genaue Werte misst „Modelle testen“ in der App.

| Modell | Laden | Prompt lesen | Antwort schreiben | Szenen-Aufruf (8 Abschnitte) | Frage/Befehl | Handy: Frage/Befehl | Handy: Sprachmodell je Stunde Hörbuch |
|---|---|---|---|---|---|---|---|
| Qwen2.5 1.5B · vorher | 2.0 s | 86 tok/s | 28.7 tok/s | 39.0 s | 5.5 s | 7–11 s | 3.3–4.9 Min. |
| Qwen2.5 1.5B · neu | 2.0 s | 84 tok/s | 28.4 tok/s | 38.6 s | 6.1 s | 8–12 s | 3.2–4.8 Min. |
| Qwen2.5 3B · vorher | 3.0 s | 42 tok/s | 16.1 tok/s | 71.7 s | 11.8 s | 16–24 s | 6.0–9.0 Min. |
| Qwen2.5 3B · neu | 3.0 s | 41 tok/s | 15.8 tok/s | 73.0 s | 12.1 s | 16–24 s | 6.1–9.1 Min. |
| Qwen3 1.7B · vorher | 2.0 s | 79 tok/s | 22.1 tok/s | 38.6 s | 6.4 s | 9–13 s | 3.2–4.8 Min. |
| Qwen3 1.7B · neu | 2.0 s | 77 tok/s | 21.1 tok/s | 48.6 s | 6.8 s | 9–14 s | 4.0–6.1 Min. |
| Qwen3 4B Instruct 2507 · vorher | 4.0 s | 30 tok/s | 10.4 tok/s | 109.0 s | 14.9 s | 20–30 s | 9.1–13.6 Min. |
| Qwen3 4B Instruct 2507 · neu | 4.0 s | 30 tok/s | 10.1 tok/s | 116.7 s | 15.7 s | 21–31 s | 9.7–14.6 Min. |
| Gemma 3 4B · vorher | 4.1 s | 37 tok/s | 12.4 tok/s | 95.0 s | 14.3 s | 19–29 s | 7.9–11.9 Min. |
| Gemma 3 4B · neu | 4.1 s | 36 tok/s | 12.1 tok/s | 106.7 s | 14.8 s | 20–30 s | 8.9–13.3 Min. |

<details><summary>Qwen2.5 1.5B · vorher: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Aelrin hat Lena vor den Wölfen gerettet.
- ❌ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde in der Taverne hat den Vorteil von Weidenbach gewarnt.
- ❌ *Wie endet der Kampf gegen den Drachen?* → The summary ends with Lena leaning her head against Aelrin's shoulder, indicating that the fight against the dragon is ongoing.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Der Brief sagte, dass Lena's Großmutter gestorben war und sie friedlich eingeschlafen war, aber sie hatte bis zuletzt von Lena gesprochen.
- ✅ *Was leuchtete im Tempel?* → Die blaue Runen leuchteten im Tempel.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin hat den Drachen getötet und sich selbst verletzt.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena mit einer goldenen Kette und nannte sie Hüterin der Glut.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „15:30:00“ (erwartet FIND)
- ❌ *Mach bitte in einer halben Stunde aus* → PAUSE „“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „30“ (erwartet REWIND)
- ❌ *Lies etwas schneller vor, so 1,2-fach* → PLAY „“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „merken“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ❌ *Bring mich zum Anfang vom zweiten Kapitel* → PLAY „“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → PLAY „“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/1@OTHER LOVE+FEAST+ROYAL – Die Glut von Weidenbach · CALM/2@OTHER NATURE+FEAST+ROYAL – Die Höhle im Eisberg · MYSTERIOUS/2@TEMPLE MYSTERY+HORROR+FEAST – Tempel im Eisberg · HAPPY/2@TEMPLE FEAST+ROYAL+HUMOR – Tempel im Eisberg · HAPPY/2@TEMPLE FEAST+ROYAL+HUMOR – Tempel im Eisberg · MYSTERIOUS/2@TEMPLE MYSTERY+HORROR+FEAST – Tempel im Eisberg · HAPPY/2@TEMPLE FEAST+ROYAL+HUMOR – Tempel im Eisberg · HAPPY/2@TEMPLE FEAST+ROYAL+HUMOR – Tempel im Eisberg · HAPPY/2@TEMPLE FEAST+ROYAL+HUMOR – Tempel im Eisberg

</details>

<details><summary>Qwen2.5 1.5B · neu: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Aelrin
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde hat die Glut zurückbringen müssen, um den Drachen zu aufhalten.
- ❌ *Wie endet der Kampf gegen den Drachen?* → Die beiden saßen am Lagerfeuer.
- ❌ *Was stand in dem Brief, den der Rabe brachte?* → Lena sank in den Schnee und weinte. Sie hatte sich nicht einmal verabschiedet. Aelrin legte schweigend den Arm um sie, und lange sagte keiner ein Wort.
- ✅ *Was leuchtete im Tempel?* → Die blauen Runen leuchteten im Tempel.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin hat den Drachen getötet, ersetzt das aus der Excerpt.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena mit einer goldenen Kette und nannte sie Hüterin der Glut.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „10:00“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ✅ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „120“ (erwartet REWIND)
- ❌ *Lies etwas schneller vor, so 1,2-fach* → PLAY „1.2“ (erwartet SPEED)
- ❌ *Diese Stelle möchte ich mir merken* → NONE „FIND“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „MUSIC“ (erwartet MUSIC_OFF)
- ❌ *Bring mich zum Anfang vom zweiten Kapitel* → PLAY „0:00“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → REWIND „150“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „Aelrin is a character from the“ (erwartet NONE)

Szenen: CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@HOME HOME – Die Glut von Weidenbach. EinFant · CALM/1@OPEN_LAND JOURNEY – Die Höhle im Eisberg · MYSTERIOUS/2@TEMPLE MYSTERY – Tempel im Eisberg · HAPPY/1@TOWN FEAST – Weidenbach · HAPPY/1@TOWN FEAST – Weidenbach · HAPPY/1@TOWN FEAST – Weidenbach · HAPPY/1@TOWN FEAST – Weidenbach · HAPPY/1@TOWN FEAST – Weidenbach · HAPPY/1@TOWN FEAST – Weidenbach

</details>

<details><summary>Qwen2.5 3B · vorher: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Der Elf Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde in der Taverne hat gewarnt, dass ein Drache erwacht ist und bald über die Dörfer kommen wird, der Feuer der Alten gestohlen hat und nur wer die Glut zurückbringt, kann ihn aufhalten.
- ✅ *Wie endet der Kampf gegen den Drachen?* → In der Erzählung wird nicht genau, wie der Kampf gegen den Drachen endet. Der Elf Aelrin hat Lena vor den Wölfen gerettet, aber der Kampf gegen den Drachen ist noch nicht abgeschlossen.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Der Brief aus Weidenbach, den der Rabe brachte, stand: 'Deine Großmutter ist gestorben, sie ist friedlich eingeschlafen und hat bis zuletzt von dir gesprochen.'
- ✅ *Was leuchtete im Tempel?* → Die blaue Runen leuchteten auf den Säulen und über einer Kugel aus Licht, die über einem Altar schwebte.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Im Kampf gegen den Drachen fiel Aelrin, aber Sie sind mit Lena die Glut in die Höhle geschafft.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena als Hüterin der Glut.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „10:00“ (erwartet FIND)
- ❌ *Mach bitte in einer halben Stunde aus* → PAUSE „30:00“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „15:30“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.200000000000000200000000000000“ (erwartet SPEED)
- ❌ *Diese Stelle möchte ich mir merken* → FIND „Weidenbach, Lied, Sternen, Elfen“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ❌ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „title“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „25:00:ss:ss:ss:ss:ss:ss:ss:ss:ss“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NONE/1@OTHER  – Die Glut von Weidenbach. Ein Hör · CALM/1@HOME  – Lena wohnt mit Großmutter · HAPPY/1@TOWN  – Erntefest im Dorf · TENSE/2@TOWN CHASE+DANGER+HORROR – Fremder in Taverne · CALM/1@FOREST  – Lena folgt Pfad · TENSE/2@FOREST CHASE+DANGER+HORROR – Wölfe hinter Lena · ACTION/2@FOREST BATTLE+CHASE+DANGER – Elf Aelrin kämpft mit Wölfen · CALM/1@FOREST  – Lena und Aelrin am Feuer · CALM/1@MOUNTAINS NATURE+ROYAL – Die Höhle im Eisberg · CALM/1@MOUNTAINS NATURE+ROYAL – Der uralte Tempel · CALM/1@MOUNTAINS NATURE+ROYAL – Die Gruft · SAD/1@MOUNTAINS GRIEF – Die Geister der toten Wächter · TENSE/2@MOUNTAINS DANGER+HORROR – Die Höhle des Drachen · ACTION/3@MOUNTAINS BATTLE+CHASE – Der Kampf gegen den Drachen · HAPPY/2@CITY FEAST+ROYAL – Die Rückkehr nach Weidenbach · NONE/1@CITY ROYAL – Die Jubelhalle

</details>

<details><summary>Qwen2.5 3B · neu: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnte mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Der Elf Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde in der Taverne war Lena gewarnt, dass in den Bergen ein Drache erwacht ist, der das Feuer der Alten gestohlen hat und bald über die Dörfer kommen wird. Er sagte, nur wer die Glut zurückbringt, kann ihn aufhalten.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Der Kampf gegen den Drachen wird nicht in den abgerufenen Excerpts erwähnt. Der Elf Aelrin rettet Lena vor den Wölfen, aber der Kampf gegen den Drachen ist noch nicht beschrieben.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → In dem Brief stand, dass Lena's Großmutter gestorben ist und sie friedlich eingeschlafen hat.
- ✅ *Was leuchtete im Tempel?* → Im Tempel leuchteten blaue Runen, die sich bewegten, als sie näher kamen.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Im Kampf gegen den Drachen fiel Aelrin. Lena hielt die Glut in den Händen und seinen Kopf in ihrem Schoß.
- ❌ *Wer ehrte Lena am Ende?* → Am Ende ehrte Lena die Hüterin Aelrin und ihre Großmutter.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „10:00“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „12000000000000000000000000000000“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.200000000000000154237883415015“ (erwartet SPEED)
- ❌ *Diese Stelle möchte ich mir merken* → FIND „Aelrin Kopf an Lenas Arm“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „0:00:00“ (erwartet MUSIC_OFF)
- ❌ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „title“ (erwartet JUMP_CHAPTER)
- ✅ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „25:00“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NONE/1@HOME  – Neue Lesung beginnt · CALM/1@HOME  – Morgens in der Stube · CALM/1@TOWN  – Erntefest am Marktplatz · TENSE/3@TOWN CHASE+DANGER – Der Fremde im Goldenen Krug · CALM/1@OPEN_LAND  – Lena im Wald · ACTION/3@OPEN_LAND CHASE+DANGER – Lena und die Wölfe · ACTION/3@OPEN_LAND CHASE+DANGER – Lena und Aelrin · CALM/1@OPEN_LAND  – Lena und Aelrin am Lagerfeuer · CALM/1@OPEN_LAND NATURE – Die Höhle im Eisberg · CALM/1@TEMPLE MYSTERY – Der uralte Tempel · TENSE/3@TEMPLE DANGER – Die Geister der toten Wächter · SAD/2@TEMPLE GRIEF – Die Freundin Mira · TENSE/3@OPEN_LAND BATTLE – Die Höhle des Drachen · ACTION/3@OPEN_LAND BATTLE+CHASE – Der Kampf gegen den Drachen · HAPPY/2@TEMPLE VICTORY+ROYAL – Die Rückkehr nach Weidenbach · NONE/1@TEMPLE  – Das Feuer der Alten

</details>

<details><summary>Qwen3 1.7B · vorher: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde hat in der Taverne gewarnt, dass ein Drache erwacht und das Feuer der Alten gestohlen wird.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Die Erzählung endet nicht explizit mit dem Kampf gegen den Drachen. Die Geschichte ist noch nicht vollständig abgeschlossen, und der Drache ist nur als Gefahrensymbol dargestellt. Die Handlung fokussiert auf Lena und Aelrin, ihre Beziehung und die Suche nach der Glut, statt auf den konkreten Kampf g
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Der Brief brachte eine Nachricht von Mira: Deine Großmutter ist gestorben.
- ✅ *Was leuchtete im Tempel?* → Im Tempel leuchteten die Runen.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin stieß das Schwert des Drachen ins Herz und fiel in der Schlacht.
- ❌ *Wer ehrte Lena am Ende?* → Die Antwort ist nicht in den gegebenen Exkursen enthalten.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „15:30“ (erwartet FIND)
- ❌ *Mach bitte in einer halben Stunde aus* → NONE „“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „10 seconds“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.25“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „15:30 - Kapitel 1 - Aelrin und L“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ✅ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „2“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → PLAY „“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: CALM/2@FOREST JOURNEY – Der Fremde · CALM/2@FOREST NATURE – Der Fremde · CALM/2@FOREST NATURE – Der Fremde · TENSE/3@FOREST DANGER – Der Fremde · TENSE/3@FOREST JOURNEY – Der Fremde · TENSE/3@FOREST DANGER – Der Fremde · ACTION/3@FOREST BATTLE – Der Fremde · CALM/2@FOREST LOVE – Der Fremde · CALM/2@FOREST JOURNEY – Die Höhle im Eisberg · MYSTERIOUS/3@TEMPLE MYSTERY – Heiligtum der Alten · EERIE/3@CAVE DANGER – Geister der Wächter · SAD/2@WINTER GRIEF – Weine der Großmutter · TENSE/3@WINTER DANGER – Drachen im Eis · ACTION/3@MOUNTAINS BATTLE+CHASE – Kampf mit dem Drachen · HAPPY/2@CASTLE VICTORY – Hüterin der Glut · NONE/1@OTHER  – Ende

</details>

<details><summary>Qwen3 1.7B · neu: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde war in der Taverne gewarnt, dass ein Drache erwacht und das Feuer der Alten gestohlen wird. Er sagte, dass er die Glut zurückbringen muss, um den Drachen zu stoppen.
- ❌ *Wie endet der Kampf gegen den Drachen?* → Die Kampf gegen den Drachen endet mit der Rückkehr der Glut. Lena bringt die Glut zurück, und der Drache wird aufhalten können.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Deine Großmutter ist gestorben, stand dort, sie ist friedlich eingeschlafen und hat bis zuletzt von dir gesprochen.
- ✅ *Was leuchtete im Tempel?* → Im Tempel leuchteten blaue Runen.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin stieß das Schwert des Drachen ins Herz und fiel in der Schlacht.
- ❌ *Wer ehrte Lena am Ende?* → Die Großmutter ehrte Lena am Ende.
- ✅ *Bring mich zu der Stelle, wo die Wölfe heulen* → FIND „Wölfe heulen, Wölfe heulen, Wölf“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „12000000000000000000000000000000“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.25“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „14:00 - Aelrin und Lenas Schramm“ (erwartet BOOKMARK)
- ❌ *Die Musik stört gerade, schalt sie ab* → NONE „“ (erwartet MUSIC_OFF)
- ✅ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „2“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → NONE „“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: CALM/2@TOWN FEAST – Die Glut von Weidenbach · CALM/1@HOME NATURE – Die Glut von Weidenbach · HAPPY/3@TOWN FEAST+NATURE – Die Glut von Weidenbach · TENSE/3@TOWN DANGER+MYSTERY – Die Glut von Weidenbach · CALM/2@TOWN JOURNEY – Die Glut von Weidenbach · ACTION/3@FOREST CHASE+NATURE – Die Glut von Weidenbach · ACTION/3@FOREST BATTLE+MYSTERY – Die Glut von Weidenbach · CALM/2@HOME LOVE – Die Glut von Weidenbach · CALM/3@HAUNTED DANGER+MYSTERY – Die Höhle im Eisberg · CALM/3@HAUNTED MYSTERY+MAGIC – Das Heiligtum der Alten · TENSE/3@HAUNTED DANGER+HORROR – Die Geister der Wächter · SAD/2@HOME GRIEF – Die Tränen der Großmutter · ACTION/3@HAUNTED BATTLE – Die Schlacht im Gletscher · ACTION/3@HAUNTED BATTLE – Der Kampf des Drachen · HAPPY/2@HOME VICTORY – Die Rückkehr der Glut · NONE/1@OTHER  – Die Glut von Weidenbach

</details>

<details><summary>Qwen3 4B Instruct 2507 · vorher: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Der Elf Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde hat gewarnt, dass ein Drache in den Bergen erwacht ist und das Feuer der Alten gestohlen hat. Er sagt, dass dieser Drache bald über die Dörfer kommen wird und nur wer die Glut zurückbringt, ihn aufhalten kann.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Das Ende des Kampfes gegen den Drachen wird nicht im bisherigen Text erzählt.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Deine Großmutter ist gestorben, stand dort, sie ist friedlich eingeschlafen und hat bis zuletzt von dir gesprochen.
- ✅ *Was leuchtete im Tempel?* → Blau leuchtende Runen.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin fiel im Kampf gegen den Drachen.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena am Ende.
- ✅ *Bring mich zu der Stelle, wo die Wölfe heulen* → FIND „Wölfe heulen“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ✅ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „300“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.2“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „Lagerfeuer“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ✅ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „2“ (erwartet JUMP_CHAPTER)
- ✅ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „25:00“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NEUTRAL/1@OTHER  – Einleitung und Kapitel Eins: Der · CALM/1@TOWN HOME+NATURE – Ein ruhiges Leben in Weidenbach · HAPPY/1@TOWN FEAST+HUMOR – Erntefest im Dorf · TENSE/2@TAVERN MYSTERY+DANGER – Der Fremde im Krug · CALM/1@OPEN_LAND JOURNEY+NIGHT – Der Weg nach Norden · TENSE/3@FOREST CHASE+DANGER+NATURE – Wölfe im Wald · ACTION/2@FOREST BATTLE+CHASE+HOME – Der Elf rettet Lena · ROMANTIC/1@OPEN_LAND LOVE+HOME+NIGHT – Das Lagerfeuer · IDYLLIC/1@OPEN_LAND  – Der Weg in die Berge · MYSTERIOUS/2@TEMPLE  – Das Heiligtum der Alten · EERIE/3@CAVE  – Die Wächter erwachen · SAD/2@FOREST  – Die Nachricht von Mira · TENSE/3@DUNGEON  – Der Drache im Eis · ACTION/3@DUNGEON  – Der Kampf um die Glut · HAPPY/2@CASTLE  – Die Rückkehr der Glut · NONE/1@OTHER  – Ende des Hörbooks

</details>

<details><summary>Qwen3 4B Instruct 2507 · neu: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Der Elf Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde hat gewarnt, dass ein Drache in den Bergen erwacht ist und das Feuer der Alten gestohlen hat. Er sagt, dass dieser Drache bald über die Dörfer kommen wird und nur wer die Glut zurückbringt, ihn aufhalten kann.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Das Ende des Kampfes gegen den Drachen wird nicht im bisherigen Text behandelt.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Deine Großmutter ist gestorben, sie ist friedlich eingeschlafen und hat bis zuletzt von dir gesprochen.
- ✅ *Was leuchtete im Tempel?* → Blaue Runen leuchteten im Tempel.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin fiel im Kampf gegen den Drachen.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena.
- ✅ *Bring mich zu der Stelle, wo die Wölfe heulen* → FIND „die Wölfe heulen“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „600“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.2“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „Lagerfeuer“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ✅ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „2“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → FORWARD „300“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NONE/1@OTHER  – Titel und Einleitung (Kapitel 1, · CALM/1@HOME HOME – Tägliche Ruhe im Haus am Fluss · HAPPY/2@TOWN FEAST+HUMOR – Erntefest im Dorf · MYSTERIOUS/2@TAVERN MYSTERY+DANGER – Der Fremde im Goldenen Krug · CALM/1@OPEN_LAND JOURNEY+NIGHT – Der Weg nach Norden · TENSE/3@FOREST CHASE+DANGER – Wölfe im Dunklen Wald · ACTION/3@FOREST BATTLE+CHASE – Der Kampf mit den Wölfen · ROMANTIC/2@OPEN_LAND LOVE+HOME – Das Lagerfeuer und die Nähe · CALM/1@OPEN_LAND NATURE – Aufstieg in die Berge
 · MYSTERIOUS/2@TEMPLE MYSTERY+MAGIC – Das Heiligtum der Alten
 · EERIE/2@DUNGEON DANGER+HORROR – Geister der Wächter
 · SAD/1@TOWN GRIEF – Weinende Lena
 · TENSE/2@CAVE DANGER+NIGHT – Der Drache im Eis
 · ACTION/3@CAVE BATTLE+CHASE+DANGER – Kampf gegen den Drachen
 · HAPPY/2@CASTLE VICTORY+FEAST – Hüterin der Glut
 · NONE/1@OTHER  – Ende des Hörbuchs


</details>

<details><summary>Gemma 3 4B · vorher: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Der Elf Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde warnte vor einem Drachen, der das Feuer der Alten gestohlen hat und bald über die Dörfer kommen wird.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Ich weiß es nicht, das Ende des Kampfes gegen den Drachen wird in diesem Abschnitt noch nicht erzählt.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Der Brief stand: 'Deine Großmutter ist gestorben, sie ist friedlich eingeschlafen und hat bis zuletzt von dir gesprochen.'
- ✅ *Was leuchtete im Tempel?* → Im Tempel leuchteten blaue Runen, die sich bewegten.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Im Kampf gegen den Drachen fiel Aelrin.
- ✅ *Wer ehrte Lena am Ende?* → Am Ende ehrte der König Lena.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → REWIND „10:00“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „14:00“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.25“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ✅ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „2“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „00:15:30“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NEUTRAL/1@TOWN  – Einleitung des Hörbuchs”, 1, 1,  · CALM/1@HOME NATURE – Leben in Weidenbach”, 1, 1,  · HAPPY/2@TOWN FEAST+HUMOR – Erntefest in Weidenbach”, 1, 2,  · TENSE/2@TAVERN DANGER+MYSTERY – Der Fremde”, 1, 3,  · NEUTRAL/1@OTHER JOURNEY – Die Reise beginnt”, 1, 1,  · EERIE/2@FOREST HORROR+NIGHT – Der dunkle Wald”, 1, 3,  · ACTION/3@FOREST BATTLE+VICTORY – Aelrin rettet Lena”, 1, 3,  · ROMANTIC/1@HOME LOVE – Lagerfeuer und Vertrauen”, 1, 2, · CALM/2@OTHER NATURE+GRIEF – Die Berge rufen Lena und Aelrin, · MYSTERIOUS/2@MAGIC MYSTERY+SACRED – Der Tempel der Alten, ein Geheim · EERIE/3@DUNGEON HORROR+DANGER – Die Geister der toten Wächter, · SAD/2@OTHER GRIEF+NATURE – Der Brief von Mira, ein Schmerz, · ACTION/3@OTHER BATTLE+DANGER+HORROR – Der Drache erwacht, die Glut ru · ACTION/3@OTHER BATTLE+VICTORY+HORROR – Flammen und Kampf, Aelrin fällt, · HAPPY/2@CASTLE ROYAL+VICTORY – Lena wird geehrt, die Glut · NEUTRAL/1@OTHER  – Die Glut von Weidenbach, Ende

</details>

<details><summary>Gemma 3 4B · neu: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Der Elf Aelrin hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde warnte vor einem Drachen, der das Feuer der Alten gestohlen hat und bald über die Dörfer kommen wird.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Der Kampf gegen den Drachen wird im Text nicht weiter ausgeführt.
- ❌ *Was stand in dem Brief, den der Rabe brachte?* → Ich habe den Brief gefunden.
- ✅ *Was leuchtete im Tempel?* → Im Tempel leuchteten blaue Runen.
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Im Kampf gegen den Drachen fiel Aelrin.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena am Ende.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „10:00“ (erwartet FIND)
- ✅ *Mach bitte in einer halben Stunde aus* → SLEEP_TIMER „30“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „1400“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.2“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „Lagerfeuerabend“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ✅ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_CHAPTER „2“ (erwartet JUMP_CHAPTER)
- ✅ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „25:00“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NONE/1@HOME JOURNEY – Vorwort und Einleitung”, 1, 1, 1 · CALM/1@HOME NATURE+GRIEF – Familienleben in Weidenbach”, 1, · HAPPY/2@TOWN FEAST+HUMOR – Erntefest in Weidenbach”, 3, 2,  · TENSE/3@TAVERN DANGER+MYSTERY – Der Fremde und die Prophezeiung” · NEUTRAL/1@OPEN_LAND JOURNEY – Reise in den Norden”, 1, 1, 1 · TENSE/3@FOREST DANGER+HORROR – Angriff der Wölfe”, 3, 3, 3 · CALM/2@FOREST VICTORY+MAGIC – Aelrin rettet Lena”, 2, 2, 2 · ROMANTIC/1@HOME LOVE – Lagerfeuer und Vertrautheit”, 1,

</details>
