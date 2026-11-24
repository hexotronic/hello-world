// Prüft das Herauslesen von Steckbrief-Fakten aus Wikipedia-ähnlichen Texten. Aufruf: npm run test:facts
import { parseBloom, parseLifeForm, parseToxicity, parseFamily, parseLatin, parseWikiPlant } from '../www/js/plantfacts.js';

const list = ['Einjährig', 'Zweijährig', 'Staude', 'Halbstrauch', 'Strauch', 'Baum', 'Sukkulente/Kaktee'];
let fails = 0;
const eq = (label, got, want) => {
  const ok = JSON.stringify(got) === JSON.stringify(want);
  if (!ok) fails++;
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${label}: ${JSON.stringify(got)}${ok ? '' : ` (erwartet ${JSON.stringify(want)})`}`);
};

eq('Blütezeit von–bis', parseBloom('Die Blütezeit reicht von Juni bis August.'), [6, 7, 8]);
eq('Blütezeit bis in den', parseBloom('Die Blütezeit reicht von Februar bis in den November.'), [2, 3, 4, 5, 6, 7, 8, 9, 10, 11]);
eq('Blüten erscheinen', parseBloom('Die Blüten erscheinen mit dem Blattaustrieb von April bis Mai.'), [4, 5]);
eq('Monate aufgezählt', parseBloom('Die Blütezeit liegt im April und Mai.'), [4, 5]);
eq('über den Jahreswechsel', parseBloom('Sie blüht von November bis Februar.'), [1, 2, 11, 12]);
eq('ohne Monate', parseBloom('In der mehrere Tage währenden Blütezeit schließt sich der Blütenstand bei Nacht.'), []);
eq('Lebensform Staude', parseLifeForm('Der Spitzwegerich ist eine ausdauernde, krautige Pflanze.', list), ['Staude']);
eq('Lebensform zweijährig', parseLifeForm('Er wächst meist als zweijährige, krautige Pflanze.', list), ['Zweijährig']);
eq('Lebensform Baum', parseLifeForm('Die Rotbuche wächst als sommergrüner Baum.', list), ['Baum']);
eq('Lebensform Zwergstrauch', parseLifeForm('Der Lavendel ist ein kleiner Strauch, auch Zwergstrauch genannt.', list), ['Halbstrauch']);
eq('Giftig hoch', parseToxicity('Alle Pflanzenteile sind hochgiftig.'), 'high');
eq('Giftig schwach', parseToxicity('Alle Teile enthalten das schwach giftige Rhoeadin.'), 'low');
eq('Giftig leicht', parseToxicity('Rohe Bucheckern sind dadurch leicht giftig.'), 'low');
eq('Liste giftiger Pflanzen ignorieren', parseToxicity('Siehe auch Liste giftiger Pflanzen.'), '');
eq('Familie aus Einleitung', parseFamily('Der Klatschmohn ist eine Pflanzenart innerhalb der Familie der Mohngewächse (Papaveraceae).'), { family: 'Papaveraceae', familyCommon: 'Mohngewächse' });
eq('Botanischer Name aus Einleitung', parseLatin('Der Spitzwegerich (Plantago lanceolata) ist eine Pflanzenart.'), 'Plantago lanceolata');

const text = `Der Rote Fingerhut (Digitalis purpurea) ist eine Pflanzenart in der Familie der Wegerichgewächse (Plantaginaceae).
== Beschreibung ==
=== Vegetative Merkmale ===
Der Rote Fingerhut wächst meist als zweijährige, krautige Pflanze. Im ersten Jahr bildet sie eine Blattrosette.
=== Generative Merkmale ===
Die Blütezeit reicht von Juni bis August. Die Blüten hängen glockenförmig.
== Vorkommen ==
Er bevorzugt saure, humusreiche Böden an halbschattigen Standorten. Man findet ihn an Waldwegen und auf Lichtungen.
== Giftigkeit ==
Alle Pflanzenteile sind hochgiftig.`;
const r = parseWikiPlant(text, { lifeForms: list });
eq('Gesamt: Blütezeit', r.bloom, [6, 7, 8]);
eq('Gesamt: Lebensform', r.lifeForm, ['Zweijährig']);
eq('Gesamt: Giftigkeit', r.toxicity, 'high');
eq('Gesamt: Familie', r.family, { family: 'Plantaginaceae', familyCommon: 'Wegerichgewächse' });
eq('Gesamt: Standort beginnt mit', r.habitat.slice(0, 22), 'Er bevorzugt saure, hu');
process.exit(fails ? 1 : 0);
