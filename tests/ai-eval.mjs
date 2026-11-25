// Prüft das Lesen und Absichern von Sprachmodell-Antworten (ohne Modell). Aufruf: npm run test:ai
import { parseLlmJson, llmToFacts, articleParts, factSources, llmMessages, groundedShare } from '../www/js/llmfacts.js';
import { mergeAi } from '../www/js/aifacts.js';

let fails = 0;
const eq = (label, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (!ok) fails++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label}: ${JSON.stringify(got)}${ok ? '' : ` (erwartet ${JSON.stringify(want)})`}`);
};

const article = `Der Rote Fingerhut (Digitalis purpurea) ist eine Pflanzenart in der Familie der Wegerichgewächse (Plantaginaceae).
== Beschreibung ==
Der Rote Fingerhut wächst meist als zweijährige, krautige Pflanze und wird bis zu 200 Zentimeter hoch. Die purpurroten Blüten hängen glockenförmig in einer einseitswendigen Traube.
== Vorkommen ==
Er bevorzugt saure, humusreiche Böden an halbschattigen Standorten, etwa auf Kahlschlägen und an Waldwegen.
== Verwendung in der Pflanzenheilkunde ==
Der Rote Fingerhut wird seit dem 18. Jahrhundert als Mittel gegen Herzschwäche verwendet. Alle Pflanzenteile sind hochgiftig.`;

// JSON lesen
eq('JSON in ```-Block', parseLlmJson('```json\n{"merkmale": "Bis 2 m hoch"}\n```'), { merkmale: 'Bis 2 m hoch' });
eq('abgeschnittene Antwort', parseLlmJson('{"merkmale": "Bis 2 m hoch", "verwendung": "Herzmit'), { merkmale: 'Bis 2 m hoch', verwendung: 'Herzmit' });
eq('Denkblock entfernt', parseLlmJson('<think>{"x": 1}</think>{"merkmale": "a"}'), { merkmale: 'a' });
eq('kein JSON', parseLlmJson('Ich weiß es nicht.'), null);

const parts = articleParts(article);
const src = factSources(parts);
eq('ohne Verwechslungs-Abschnitt keine Belegstelle', src.confusion, '');
eq('Giftigkeit aus dem ganzen Artikel', parts.tox, 'Alle Pflanzenteile sind hochgiftig.');

// Nur Felder abfragen, die es gibt
const msg = llmMessages({ name: 'Roter Fingerhut', article: 'x', fields: ['features', 'uses'] })[1].content;
eq('Anweisung nur mit gewünschten Feldern', /"merkmale": "", "verwendung": ""}/.test(msg) && !/verwechslung/.test(msg), true);

// Absichern
const facts = llmToFacts({
  merkmale: 'Zweijährige Pflanze, bis 200 cm hoch, purpurrote glockenförmige Blüten',
  verwendung: 'Mittel gegen Herzschwäche',
  verwechslung: 'Fingerkraut',                       // steht nirgends → verwerfen
  standort: 'Woran erkennt man die Pflanze?',          // Wiederholung der Anweisung → verwerfen
  lebensform: 'Baum',                                  // passt nicht zum Text → verwerfen
  bluetezeit: 'Juni bis August',                       // Monate nicht im Text → verwerfen
}, src, { lifeForms: ['Einjährig', 'Zweijährig', 'Staude', 'Baum'] });
eq('belegte Angaben bleiben', [facts.features?.slice(0, 20), facts.uses], ['Zweijährige Pflanze,', 'Mittel gegen Herzschwäche']);
eq('Erfundenes fällt weg', ['confusion', 'habitat', 'lifeForm', 'bloom'].filter(k => k in facts), []);
eq('Lebensform aus eigener Liste', llmToFacts({ lebensform: 'zweijährig' }, src, { lifeForms: ['Zweijährig'] }).lifeForm, ['Zweijährig']);
eq('Belegquote', groundedShare('Mittel gegen Herzschwäche', article) >= 0.6, true);
eq('Belegquote erfunden', groundedShare('Wächst in tropischen Regenwäldern Brasiliens', article) < 0.6, true);

// Zusammenführen: Getipptes nie überschreiben
const filledValues = { features: 'Langer Text aus der Texterkennung', uses: '', confusion: '' };
const target = { features: 'Langer Text aus der Texterkennung', uses: 'Selbst getippt', confusion: '' };
const changed = mergeAi(target, { features: 'Kurz', uses: 'KI', confusion: 'X' }, new Set(['features']), filledValues);
eq('geänderte Felder', changed, ['features', 'confusion']);
eq('Getipptes bleibt', target.uses, 'Selbst getippt');
const target2 = { features: 'Vom Nutzer geändert' };
eq('nach Änderung durch Nutzer nichts ersetzen', mergeAi(target2, { features: 'Kurz' }, new Set(['features']), filledValues), []);

process.exit(fails ? 1 : 0);
