// Pflanzen-spezifische Daten und Hilfen.
import { norm } from './util.js';

export const FAMILIES = [
  'Adoxaceae (Moschuskrautgewächse)', 'Amaranthaceae (Fuchsschwanzgewächse)', 'Amaryllidaceae (Amaryllisgewächse)',
  'Apiaceae (Doldenblütler)', 'Apocynaceae (Hundsgiftgewächse)', 'Araceae (Aronstabgewächse)', 'Araliaceae (Araliengewächse)',
  'Asparagaceae (Spargelgewächse)', 'Asteraceae (Korbblütler)', 'Betulaceae (Birkengewächse)', 'Boraginaceae (Raublattgewächse)',
  'Brassicaceae (Kreuzblütler)', 'Cactaceae (Kakteen)', 'Campanulaceae (Glockenblumengewächse)', 'Caprifoliaceae (Geißblattgewächse)',
  'Caryophyllaceae (Nelkengewächse)', 'Crassulaceae (Dickblattgewächse)', 'Cupressaceae (Zypressengewächse)', 'Cyperaceae (Sauergräser)',
  'Dryopteridaceae (Wurmfarngewächse)', 'Equisetaceae (Schachtelhalmgewächse)', 'Ericaceae (Heidekrautgewächse)',
  'Euphorbiaceae (Wolfsmilchgewächse)', 'Fabaceae (Hülsenfrüchtler)', 'Fagaceae (Buchengewächse)', 'Geraniaceae (Storchschnabelgewächse)',
  'Hypericaceae (Johanniskrautgewächse)', 'Iridaceae (Schwertliliengewächse)', 'Juncaceae (Binsengewächse)', 'Lamiaceae (Lippenblütler)',
  'Liliaceae (Liliengewächse)', 'Malvaceae (Malvengewächse)', 'Oleaceae (Ölbaumgewächse)', 'Onagraceae (Nachtkerzengewächse)',
  'Orchidaceae (Orchideen)', 'Papaveraceae (Mohngewächse)', 'Pinaceae (Kieferngewächse)', 'Plantaginaceae (Wegerichgewächse)',
  'Poaceae (Süßgräser)', 'Polygonaceae (Knöterichgewächse)', 'Primulaceae (Primelgewächse)', 'Ranunculaceae (Hahnenfußgewächse)',
  'Rosaceae (Rosengewächse)', 'Rubiaceae (Rötegewächse)', 'Salicaceae (Weidengewächse)', 'Sapindaceae (Seifenbaumgewächse)',
  'Solanaceae (Nachtschattengewächse)', 'Urticaceae (Brennnesselgewächse)', 'Violaceae (Veilchengewächse)',
];

export const TOXICITY = [
  { value: '', label: 'Unbekannt' },
  { value: 'none', label: 'Ungiftig' },
  { value: 'low', label: 'Schwach giftig' },
  { value: 'toxic', label: 'Giftig' },
  { value: 'high', label: 'Sehr giftig' },
];

export function toxicityLabel(v) {
  return TOXICITY.find(t => t.value === v)?.label || '';
}

export const CAPTIONS = ['', 'Blüte', 'Blatt', 'Frucht', 'Samen', 'Rinde', 'Knospe', 'Stängel', 'Wurzel', 'Ganze Pflanze', 'Standort'];

/** Familie ohne deutschen Zusatz, z. B. "Asteraceae". */
export function familyKey(f) {
  return String(f || '').split('(')[0].trim();
}

/** Alle Antworten, die beim Eintippen als richtig gelten. */
export function acceptedNames(card, { latin = true } = {}) {
  const p = card.plant || {};
  const list = [p.deName, latin ? p.latinName : null, card.title].filter(Boolean);
  return list.length ? list : [card.back].filter(Boolean);
}

export function primaryName(card) {
  const p = card.plant || {};
  return p.deName || p.latinName || card.title || card.back || '';
}

/** Stufenweise Tipps: 1 = Familie, 2 = Anfangsbuchstabe und Länge, 3 = erste Silbe. */
export function hintText(card, level) {
  const p = card.plant || {};
  const name = p.deName || p.latinName || card.title || '';
  const hints = [];
  if (level >= 1) hints.push(p.family ? `Familie: ${p.family}` : (p.latinName ? `Gattung beginnt mit „${p.latinName[0]}“` : 'Kein Familien-Tipp vorhanden'));
  if (level >= 2 && name) {
    const pattern = name.split('').map((ch, i) => (i === 0 || /[\s\-]/.test(ch) ? ch : '·')).join('');
    hints.push(`${pattern}  (${name.replace(/[\s\-]/g, '').length} Buchstaben)`);
  }
  if (level >= 3 && name) hints.push(`Beginnt mit „${name.slice(0, Math.min(4, Math.ceil(name.length / 3)))}…“`);
  return hints;
}

export function bloomsIn(card, month) {
  return card.type === 'plant' && (card.plant?.bloom || []).includes(month);
}

export function isPlantComplete(card) {
  return card.type === 'plant' && !!(card.plant?.deName || card.plant?.latinName);
}

export function sameFamily(a, b) {
  const fa = norm(familyKey(a.plant?.family)), fb = norm(familyKey(b.plant?.family));
  return !!fa && fa === fb;
}
