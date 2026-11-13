<!-- Bericht des CI-Jobs „Sprachmodelle vergleichen (ARM)“ vom 11.11.2026 (Lauf 43043834201, Commit 9f059c2), erzeugt von .github/scripts/model_bench.py. -->

## Lauscher – Sprachmodelle im Vergleich

Gemessen auf einem GitHub-ARM-Rechner (Neoverse-N2, 4 Kerne) mit 4 Threads, llama.cpp wie in der App (ARMv8.2 dotprod+fp16, gierig, JSON-Grammatik). Testgeschichte: 16 Abschnitte, 8 Fragen, 9 Befehle in Alltagssprache.

### Verstehen

| Modell | Datei | Verstehen | Stimmung | Ort | Stärke | Musik-Merkmale | Fragen | Spoiler-sicher | Befehle | JSON gültig |
|---|---|---|---|---|---|---|---|---|---|---|
| **Qwen2.5 0.5B** (klein (in der App)) | 491 MB | **26 %** | 12 % | 7 % | 100 % | 0 % | 38 % | 100 % | 0 % | 100 % |
| **Qwen2.5 1.5B** (mittel, empfohlen (in der App)) | 1117 MB | **45 %** | 38 % | 14 % | 75 % | 36 % | 75 % | 100 % | 33 % | 100 % |
| **Qwen2.5 3B** (stärkstes (in der App)) | 2105 MB | **70 %** | 88 % | 50 % | 94 % | 57 % | 100 % | 100 % | 33 % | 100 % |
| **Qwen3 1.7B** (neuer, mittel) | 1107 MB | **73 %** | 81 % | 43 % | 94 % | 79 % | 88 % | 100 % | 56 % | 100 % |
| **Qwen3 4B Instruct 2507** (neuer, groß) | 2497 MB | **86 %** | 100 % | 64 % | 100 % | 50 % | 100 % | 100 % | 100 % | 100 % |
| **Gemma 3 4B** (neuer, groß) | 2490 MB | **82 %** | 81 % | 57 % | 100 % | 86 % | 100 % | 100 % | 67 % | 100 % |
| **Llama 3.2 3B** (Alternative) | 2019 MB | **69 %** | 75 % | 29 % | 100 % | 43 % | 100 % | 100 % | 67 % | 100 % |

### Geschwindigkeit

Handy-Spalten: geschätzt für Xiaomi 13T (Dimensity 8200-Ultra, 4× Cortex-A78) als 50–75 % der gemessenen Geschwindigkeit (langsamerer Speicher, Wärmedrosselung). Genaue Werte misst „Modelle testen“ in der App.

| Modell | Laden | Prompt lesen | Antwort schreiben | Szenen-Aufruf (8 Abschnitte) | Frage/Befehl | Handy: Frage/Befehl | Handy: Sprachmodell je Stunde Hörbuch |
|---|---|---|---|---|---|---|---|
| Qwen2.5 0.5B | 1.0 s | 138 tok/s | 59.2 tok/s | 21.0 s | 3.3 s | 4–7 s | 1.7–2.6 Min. |
| Qwen2.5 1.5B | 2.0 s | 86 tok/s | 28.7 tok/s | 39.0 s | 5.5 s | 7–11 s | 3.3–4.9 Min. |
| Qwen2.5 3B | 3.0 s | 42 tok/s | 16.0 tok/s | 71.7 s | 11.8 s | 16–24 s | 6.0–9.0 Min. |
| Qwen3 1.7B | 2.0 s | 79 tok/s | 22.4 tok/s | 38.1 s | 6.4 s | 9–13 s | 3.2–4.8 Min. |
| Qwen3 4B Instruct 2507 | 4.0 s | 30 tok/s | 10.4 tok/s | 109.0 s | 14.8 s | 20–30 s | 9.1–13.6 Min. |
| Gemma 3 4B | 4.0 s | 37 tok/s | 12.5 tok/s | 94.1 s | 14.3 s | 19–29 s | 7.8–11.8 Min. |
| Llama 3.2 3B | 3.5 s | 41 tok/s | 13.5 tok/s | 82.8 s | 11.3 s | 15–23 s | 6.9–10.4 Min. |

### Szenen in kleineren Portionen

Wie viele Abschnitte das Modell in einem Aufruf bekommt (die App nahm bisher 8). Zeit jeweils für 8 Abschnitte.

| Modell | 8 je Aufruf: Stimmung / Ort / Stärke / Merkmale / Zeit | 4 je Aufruf: Stimmung / Ort / Stärke / Merkmale / Zeit |
|---|---|---|
| Qwen2.5 0.5B | 12 % / 7 % / 100 % / 0 % / 21 s | 12 % / 7 % / 100 % / 0 % / 20 s |
| Qwen2.5 1.5B | 38 % / 14 % / 75 % / 36 % / 39 s | 44 % / 36 % / 75 % / 57 % / 37 s |
| Qwen2.5 3B | 88 % / 50 % / 94 % / 57 % / 72 s | 81 % / 43 % / 100 % / 71 % / 67 s |
| Qwen3 1.7B | 81 % / 43 % / 94 % / 79 % / 38 s | 75 % / 21 % / 100 % / 71 % / 38 s |
| Qwen3 4B Instruct 2507 | 100 % / 64 % / 100 % / 50 % / 109 s | 75 % / 43 % / 75 % / 71 % / 106 s |
| Gemma 3 4B | 81 % / 57 % / 100 % / 86 % / 94 s | 81 % / 57 % / 94 % / 71 % / 98 s |
| Llama 3.2 3B | 75 % / 29 % / 100 % / 43 % / 83 s | 88 % / 64 % / 100 % / 57 % / 75 s |

<details><summary>Qwen2.5 0.5B: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnte mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Aelrin
- ❌ *Wovor hat der Fremde in der Taverne gewarnt?* → Die Höhle im Eisberg.
- ❌ *Wie endet der Kampf gegen den Drachen?* → In der Nacht saßen die beiden am Lagerfeuer. Aelrin verband die Schramme an Lenas Arm, ganz vorsichtig, und sie spürte, wie ihr Herz schneller schlug. Er erzählte von den Sternen und vom Lied der Elfen, und sie erzählte von Weidenbach. Irgendwann lehnte sie den Kopf an seine
- ❌ *Was stand in dem Brief, den der Rabe brachte?* → Lena sank in den Schnee und weinte.
- ✅ *Was leuchtete im Tempel?* → Die Bäume standen so dicht, dass kaum Licht durch die Kronen fiel.
- ❌ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin schoss Pfeil um Pfeil, Lena rannte zwischen den Flammen hindurch zur Glut.
- ❌ *Wer ehrte Lena am Ende?* → Die Glut von Weidenbach
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → NEXT_CHAPTER „Kapitel 1“ (erwartet FIND)
- ❌ *Mach bitte in einer halben Stunde aus* → PAUSE „halben Stunde aus“ (erwartet SLEEP_TIMER)
- ❌ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → SLEEP_OFF „30“ (erwartet REWIND)
- ❌ *Lies etwas schneller vor, so 1,2-fach* → SLEEP_OFF „1,2-fach“ (erwartet SPEED)
- ❌ *Diese Stelle möchte ich mir merken* → SLEEP_OFF „15:30“ (erwartet BOOKMARK)
- ❌ *Die Musik stört gerade, schalt sie ab* → SLEEP_OFF „minutes“ (erwartet MUSIC_OFF)
- ❌ *Bring mich zum Anfang vom zweiten Kapitel* → NEXT_CHAPTER „Kapitel 2“ (erwartet JUMP_CHAPTER)
- ❌ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „520“ (erwartet JUMP_TIME)
- ❌ *Wer ist eigentlich dieser Aelrin?* → SLEEP_OFF „2“ (erwartet NONE)

Szenen: NONE/1@FOREST  – Die Glut von Weidenbach · NONE/1@JUNGLE  – Die Glut von Weidenbach · NONE/1@OPEN_LAND  – Die Glut von Weidenbach · NONE/1@MOUNTAINS  – Die Glut von Weidenbach · NONE/1@WINTER  – Die Glut von Weidenbach · NONE/1@DESERT  – Die Glut von Weidenbach · NONE/1@SEA  – Die Glut von Weidenbach · NONE/1@OTHER  – Die Glut von Weidenbach · NONE/1@FOREST  – Die Glut von Weidenbach · NONE/1@JUNGLE  – Die Glut von Weidenbach · NONE/1@OPEN_LAND  – Die Glut von Weidenbach · NONE/1@MOUNTAINS  – Die Glut von Weidenbach · NONE/1@WINTER  – Die Glut von Weidenbach · NONE/1@DESERT  – Die Glut von Weidenbach · NONE/1@SEA  – Die Glut von Weidenbach · NONE/1@OTHER  – Die Glut von Weidenbach

</details>

<details><summary>Qwen2.5 1.5B: Antworten</summary>

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

<details><summary>Qwen2.5 3B: Antworten</summary>

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

<details><summary>Qwen3 1.7B: Antworten</summary>

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

<details><summary>Qwen3 4B Instruct 2507: Antworten</summary>

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

<details><summary>Gemma 3 4B: Antworten</summary>

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

<details><summary>Llama 3.2 3B: Antworten</summary>

- ✅ *Wo wohnt Lena mit ihrer Großmutter?* → Lena wohnt mit ihrer Großmutter in einem kleinen Haus am Rand von Weidenbach, einem Dorf am Fluss.
- ✅ *Wer hat Lena vor den Wölfen gerettet?* → Ein schlanker Mann mit spitzen Ohren und einem langen Bogen, der Elf Aelrin, hat Lena vor den Wölfen gerettet.
- ✅ *Wovor hat der Fremde in der Taverne gewarnt?* → Der Fremde hat Lena gewarnt, dass ein Drache erwacht ist, der das Feuer der Alten gestohlen hat und bald über die Dörfer kommen wird.
- ✅ *Wie endet der Kampf gegen den Drachen?* → Es wird nicht in den Exzerten erzählt.
- ✅ *Was stand in dem Brief, den der Rabe brachte?* → Es handelte sich um eine Nachricht von Lenas Großmutter, die friedlich eingeschlafen und von Lena gesprochen haben soll.
- ✅ *Was leuchtete im Tempel?* → Blaue Runen
- ✅ *Was ist mit Aelrin im Kampf gegen den Drachen passiert?* → Aelrin fiel im Kampf gegen den Drachen.
- ✅ *Wer ehrte Lena am Ende?* → Der König ehrte Lena mit einer goldenen Kette und nannte sie Hüterin der Glut.
- ❌ *Bring mich zu der Stelle, wo die Wölfe heulen* → JUMP_TIME „15:30“ (erwartet FIND)
- ❌ *Mach bitte in einer halben Stunde aus* → PAUSE „30“ (erwartet SLEEP_TIMER)
- ✅ *Ich hab nicht aufgepasst, spul ein paar Minuten zurück* → REWIND „2“ (erwartet REWIND)
- ✅ *Lies etwas schneller vor, so 1,2-fach* → SPEED „1.2“ (erwartet SPEED)
- ✅ *Diese Stelle möchte ich mir merken* → BOOKMARK „Lagerfeuer“ (erwartet BOOKMARK)
- ✅ *Die Musik stört gerade, schalt sie ab* → MUSIC_OFF „“ (erwartet MUSIC_OFF)
- ❌ *Bring mich zum Anfang vom zweiten Kapitel* → JUMP_TIME „15:30:00:00:00:00:00:00:00:00:00“ (erwartet JUMP_CHAPTER)
- ✅ *Geh zur Stelle bei fünfundzwanzig Minuten* → JUMP_TIME „25:00“ (erwartet JUMP_TIME)
- ✅ *Wer ist eigentlich dieser Aelrin?* → NONE „“ (erwartet NONE)

Szenen: NONE/1@TOWN  – Die Glut von Weidenbach. Ein Hör · CALM/1@HOME  – Kapitel eins. Der Fremde · CALM/1@HOME  – Kapitel eins. Der Fremde · TENSE/2@TOWN BATTLE – Kapitel eins. Der Fremde · HAPPY/1@TOWN FEAST – Kapitel eins. Der Fremde · TENSE/2@WINTER DANGER – Kapitel zwei. Die Höhle im Eis · ACTION/3@WINTER CHASE – Kapitel zwei. Die Höhle im Eis · ROMANTIC/2@WINTER LOVE – Kapitel zwei. Die Höhle im Eis · CALM/1@MOUNTAINS  – Die Höhle im Eisberg. Tagelang · MYSTERIOUS/2@CAVE MAGIC – Der uralte Tempel · EERIE/3@CAVE HORROR – Die Gruft · SAD/1@CAVE GRIEF – Die Nachricht von Mira · ACTION/3@CAVE BATTLE – Der Kampf um die Glut · ACTION/3@CAVE BATTLE – Der Kampf um die Glut · HAPPY/2@TOWN FEAST – Die Rückkehr nach Weidenbach · HAPPY/2@TOWN FEAST – Die Rückkehr nach Weidenbach

</details>
