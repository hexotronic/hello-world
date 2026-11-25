// Pflanzen-Steckbrief automatisch ergänzen: iNaturalist (Namen, Familie) und deutsche Wikipedia (Fakten).
import { norm } from './util.js';
import { findTaxon } from './webimages.js';
import { familyKey } from './plants.js';
import { isOn } from './features.js';

const WIKI = 'https://de.wikipedia.org/w/api.php';
const MONTH_NAMES = ['januar', 'februar', 'märz', 'april', 'mai', 'juni', 'juli', 'august', 'september', 'oktober', 'november', 'dezember'];
const MONTH_SRC = `\\b(${MONTH_NAMES.join('|')}|jan|feb|mär|apr|jun|jul|aug|sept?|okt|nov|dez)\\.?(?![a-zäöü])`;
const MONTH_RE = new RegExp(MONTH_SRC, 'gi');       // zum Sammeln (matchAll)
const HAS_MONTH = new RegExp(MONTH_SRC, 'i');       // zum Prüfen (ohne lastIndex-Fallen)

function monthIndex(word) {
  const w = word.toLowerCase().replace('.', '');
  const i = MONTH_NAMES.findIndex(m => m === w || m.startsWith(w));
  return i >= 0 ? i + 1 : 0;
}

// ---------- Text zerlegen ----------
/** Teilt den Wikipedia-Klartext in Abschnitte: [{ title, level, text }] (Einleitung = title ''). */
export function sections(text) {
  const out = [{ title: '', level: 1, text: '' }];
  for (const line of String(text).split('\n')) {
    const m = /^(=+)\s*(.+?)\s*=+\s*$/.exec(line);
    if (m) out.push({ title: m[2], level: m[1].length, text: '' });
    else out[out.length - 1].text += line + '\n';
  }
  return out.map(s => ({ ...s, text: s.text.replace(/\s+/g, ' ').trim() }));
}

function sentencesOf(text) {
  return String(text)
    .replace(/\s+/g, ' ')
    .split(/(?<=[.!?])\s+(?=[A-ZÄÖÜ(„"])/)
    .map(s => s.trim())
    .filter(s => s.length > 15);
}

/** Abschnitte, deren Überschrift zum Muster passt (inkl. Unterabschnitte). */
export function sectionText(secs, re, { withChildren = true } = {}) {
  const parts = [];
  for (let i = 0; i < secs.length; i++) {
    if (!re.test(secs[i].title)) continue;
    parts.push(secs[i].text);
    if (withChildren) {
      for (let k = i + 1; k < secs.length && secs[k].level > secs[i].level; k++) parts.push(secs[k].text);
    }
  }
  return parts.filter(Boolean).join(' ');
}

export function clip(text, max) {
  if (!text) return '';
  if (text.length <= max) return text;
  const cut = text.slice(0, max);
  const end = Math.max(cut.lastIndexOf('. '), cut.lastIndexOf('.'));
  return end > max * 0.5 ? cut.slice(0, end + 1) : cut.replace(/\s+\S*$/, '') + ' …';
}

function pickSentences(text, { max = 2, maxChars = 320, must } = {}) {
  const sents = sentencesOf(text).filter(s => !must || must.test(s));
  return clip(sents.slice(0, max).join(' '), maxChars);
}

// ---------- Einzelne Fakten ----------
/** Standort: Sätze mit Standort-Begriffen und Lebensräumen bevorzugen, Verbreitungsangaben nachrangig. */
function pickHabitat(text) {
  const strong = /(Standort|bevorzugt|gedeiht|wächst (in|an|auf)|besiedelt)/i;
  const places = /(Wiese|Weide|Rasen|Wäld|Wald|Acker|Äcker|Wegr|Wegen|Grünland|Ufer|Hecke|Gärten|Garten|Boden|Böden|Felsen|Hänge|Hang|Gebüsch|Lichtung|Schutt|Mauer|Moor|Sumpf|Trockenrasen|Magerrasen)/gi;
  // Statistiken (Zahlen, Prozent, Hektar) und Waldbau-Geschichte abwerten
  const scored = sentencesOf(text).map((s, i) => ({
    s, i,
    score: (strong.test(s) ? 3 : 0) + Math.min(2, (s.match(places) || []).length) - (/\d|Prozent|Hektar|aufgeforstet/.test(s) ? 2 : 0),
  }))
    .filter(x => x.score > 0)
    .sort((a, b) => b.score - a.score || a.i - b.i)
    .slice(0, 2)
    .sort((a, b) => a.i - b.i);
  return clip(scored.map(x => x.s).join(' '), 300);
}

/** Blütezeit als Monatsliste aus Sätzen wie „Die Blütezeit reicht von Juni bis August.“ */
export function parseBloom(text) {
  const sents = sentencesOf(text).filter(s => /blütezeit|blühzeit|blüht|blühen|blüten erscheinen/i.test(s) && HAS_MONTH.test(s));
  for (const s of sents) {
    // Sätze über andere Arten („Bei Bellis sylvestris …“) überspringen
    if (/^bei [A-Z][a-z]+ [a-z]+/i.test(s) && !/blütezeit (reicht|liegt|ist|erstreckt)/i.test(s.slice(0, 40))) continue;
    const range = /(?:von|zwischen|ab)\s+(?:(?:Ende|Anfang|Mitte)\s+)?([A-Za-zä]+)\.?\s+(?:bis(?:\s+in\s+den)?(?:\s+(?:Ende|Anfang|Mitte))?|und)\s+(?:(?:Ende|Anfang|Mitte)\s+)?([A-Za-zä]+)/i.exec(s);
    if (range) {
      const a = monthIndex(range[1]), b = monthIndex(range[2]);
      if (a && b) {
        const months = [];
        for (let m = a; ; m = (m % 12) + 1) { months.push(m); if (m === b || months.length > 12) break; }
        return months.sort((x, y) => x - y);
      }
    }
    const found = [...s.matchAll(MONTH_RE)].map(m => monthIndex(m[1])).filter(Boolean);
    if (found.length) return [...new Set(found)].sort((x, y) => x - y);
  }
  return [];
}

/** Monate aus einer kurzen Angabe wie „Juni bis August“, „April–Mai“ oder „Mai, Juni“. */
export function monthsFrom(str) {
  const s = String(str || '');
  const ranges = s.matchAll(/([A-Za-zäÄ]+)\.?\s*(?:bis(?:\s+in\s+den)?(?:\s+(?:Ende|Anfang|Mitte))?|–|-|—)\s*(?:(?:Ende|Anfang|Mitte)\s+)?([A-Za-zäÄ]+)/gi);
  for (const range of ranges) {
    const a = HAS_MONTH.test(range[1]) && monthIndex(range[1]), b = HAS_MONTH.test(range[2]) && monthIndex(range[2]);
    if (a && b) {
      const months = [];
      for (let m = a; ; m = (m % 12) + 1) { months.push(m); if (m === b || months.length > 12) break; }
      return months.sort((x, y) => x - y);
    }
  }
  return [...new Set([...s.matchAll(MONTH_RE)].map(m => monthIndex(m[1])).filter(Boolean))].sort((x, y) => x - y);
}

/** Alle im Text genannten Monate (1–12). */
export function monthsMentioned(text) {
  return [...new Set([...String(text || '').matchAll(MONTH_RE)].map(m => monthIndex(m[1])).filter(Boolean))];
}

/** Lebensform(en) aus der Beschreibung, abgebildet auf die eigene Auswahlliste. */
export function parseLifeForm(text, list = []) {
  const t = String(text).slice(0, 1500).toLowerCase();
  const found = [];
  if (/halbstrauch/.test(t)) found.push('Halbstrauch');
  else if (/zwergstrauch/.test(t)) found.push('Halbstrauch');
  else if (/\bstr(a|ä)uch(es|er)?\b|wächst als strauch/.test(t) && !/\bbaum\b/.test(t)) found.push('Strauch');
  if (/\b(laub|nadel)?baum(es)?\b|baumart|wächst als baum/.test(t) && !/baumbüll/.test(t)) found.push('Baum');
  if (/\beinjährig/.test(t)) found.push('Einjährig');
  if (/\bzweijährig/.test(t)) found.push('Zweijährig');
  if ((/(ausdauernd|mehrjährig)\w*/.test(t) && /krautig/.test(t)) || /\bstaude\b/.test(t)) found.push('Staude');
  if (/sukkulent|kaktus|kakteen/.test(t)) found.push('Sukkulente/Kaktee');
  // auf Einträge der Auswahlliste abbilden (gleiche Schreibweise wie in den Einstellungen)
  return [...new Set(found.map(f => list.find(x => norm(x) === norm(f)) || list.find(x => norm(x).startsWith(norm(f).slice(0, 6))) || f))];
}

/** Giftigkeit: none | low | toxic | high, oder '' wenn nichts gefunden. */
export function parseToxicity(text) {
  const sents = sentencesOf(text).filter(s => /giftig|toxisch/i.test(s) && !/liste giftiger/i.test(s));
  for (const s of sents) {
    const l = s.toLowerCase();
    if (/(hochgiftig|sehr giftig|stark giftig|tödlich)/.test(l)) return 'high';
    if (/(schwach|leicht|gering|mäßig) giftig/.test(l)) return 'low';
    if (/(ungiftig|nicht giftig)/.test(l)) return 'none';
    if (/\bgiftig/.test(l)) return 'toxic';
  }
  return '';
}

/** Familie aus der Einleitung: „… Familie der Korbblütler (Asteraceae)“ */
export function parseFamily(lead) {
  const m = /Familie der ([A-ZÄÖÜ][\wäöüß-]+(?: [\wäöüß-]+)?)\s*\(([A-Z][a-z]+aceae)\)/.exec(lead)
    || /Familie ([A-Z][a-z]+aceae)/.exec(lead);
  if (!m) return null;
  return m.length > 2 && m[2] ? { family: m[2], familyCommon: m[1] } : { family: m[1], familyCommon: '' };
}

/** Botanischer Name aus der Einleitung: „Der Spitzwegerich (Plantago lanceolata) ist …“ */
export function parseLatin(lead) {
  const m = /\(([A-Z][a-z]+(?: (?:sect\. )?[a-z][a-z-]+)?)(?:[),;]| L\.| agg)/.exec(lead);
  return m ? m[1] : '';
}

/** Alle Fakten aus einem Wikipedia-Klartext. */
export function parseWikiPlant(text, { lifeForms = [] } = {}) {
  const secs = sections(text);
  const lead = secs[0]?.text || '';
  const descr = sectionText(secs, /^(Beschreibung|Merkmale|Vegetative Merkmale|Erscheinungsbild|Gestalt|Beschreibung und Ökologie)/i);
  const flower = sectionText(secs, /^(Blüte|Blütenstand|Generative Merkmale|Blütenknospen)/i);
  const habitatSec = sectionText(secs, /(Vorkommen|Standort|Verbreitung)/i);
  const uses = sectionText(secs, /^(Verwendung|Nutzung|Heilpflanze|Speisepflanze|Zierpflanze)/i);
  const confusion = sectionText(secs, /(Verwechslung|Unterscheidung)/i);
  const toxSec = sectionText(secs, /(Giftigkeit|Toxikologie|Inhaltsstoffe)/i);
  const full = secs.map(s => s.text).join(' ');

  const features = [pickSentences(descr, { max: 3, maxChars: 380 }), pickSentences(flower, { max: 1, maxChars: 200 })]
    .filter(Boolean).join(' ');
  return {
    lead: clip(lead, 400),
    family: parseFamily(lead),
    latinName: parseLatin(lead),
    features: clip(features, 560),
    bloom: parseBloom([flower, descr, full].join(' ')),
    lifeForm: parseLifeForm([descr, lead].join(' '), lifeForms),
    toxicity: parseToxicity([toxSec, descr, full].join(' ')),
    habitat: pickHabitat(habitatSec || full),
    uses: pickSentences(uses, { max: 2, maxChars: 260 }),
    confusion: pickSentences(confusion, { max: 2, maxChars: 260 }),
  };
}

// ---------- Abruf ----------
async function wikiJson(params) {
  const url = `${WIKI}?${new URLSearchParams({ format: 'json', origin: '*', ...params })}`;
  let res;
  try {
    res = await fetch(url, { credentials: 'omit' });
  } catch {
    throw new Error('Keine Internetverbindung.');
  }
  if (res.status === 429) throw new Error('Wikipedia ist gerade überlastet. Bitte gleich noch einmal versuchen.');
  if (!res.ok) throw new Error(`Wikipedia antwortet mit Fehler ${res.status}.`);
  return res.json();
}

async function wikiExtract(title) {
  const d = await wikiJson({ action: 'query', prop: 'extracts', explaintext: '1', exsectionformat: 'wiki', redirects: '1', titles: title });
  const page = Object.values(d.query?.pages || {})[0];
  if (!page || page.missing !== undefined || !page.extract) return null;
  if (/ steht für:|bezeichnet:|ist der Name folgender/i.test(page.extract.slice(0, 300))) return null; // Begriffsklärung
  return { title: page.title, text: page.extract };
}

async function wikiSearch(query) {
  const d = await wikiJson({ action: 'query', list: 'search', srsearch: query, srlimit: '1', srnamespace: '0' });
  return d.query?.search?.[0]?.title || null;
}

/**
 * Steckbrief suchen. latin/german: bekannte Namen. Liefert gefundene Werte (leere Felder fehlen).
 */
export async function lookupPlant({ latin = '', german = '', lifeForms = [] }) {
  const out = { sources: [] };
  let taxon = null;
  let wiki = null;
  // 1) Wikipedia zuerst über den botanischen Namen (leitet auf den deutschen Artikel um), sonst deutscher Name
  try {
    wiki = (latin && await wikiExtract(latin)) || (german && await wikiExtract(german));
    if (!wiki) {
      const t = await wikiSearch(latin || german);
      if (t) wiki = await wikiExtract(t);
    }
  } catch (err) {
    out.wikiError = err.message;
  }
  let parsed = null;
  if (wiki) {
    parsed = parseWikiPlant(wiki.text, { lifeForms });
    out.sources.push({ name: 'Wikipedia', title: wiki.title, url: `https://de.wikipedia.org/wiki/${encodeURIComponent(wiki.title.replace(/ /g, '_'))}`, license: 'CC BY-SA 4.0' });
  }
  // 2) iNaturalist für saubere Namen und Familie
  const latinGuess = latin || parsed?.latinName || '';
  try {
    taxon = await findTaxon(latinGuess || german);
  } catch (err) {
    out.inatError = err.message;
  }
  if (taxon) out.sources.push({ name: 'iNaturalist', title: taxon.name, url: `https://www.inaturalist.org/taxa/${taxon.id}` });

  out.latinName = (taxon && ['species', 'subspecies', 'variety', 'hybrid'].includes(taxon.rank) ? taxon.name : '') || latinGuess || taxon?.name || '';
  out.deName = (wiki && !/^[A-Z][a-z]+ [a-z]/.test(wiki.title) ? wiki.title.replace(/\s*\(.*\)$/, '') : '') || taxon?.commonName || '';
  const fam = taxon?.family ? { family: taxon.family, familyCommon: taxon.familyCommon } : parsed?.family;
  if (fam) out.family = fam;
  if (parsed) {
    for (const k of ['features', 'bloom', 'lifeForm', 'toxicity', 'habitat', 'uses', 'confusion', 'lead']) {
      const v = parsed[k];
      if (Array.isArray(v) ? v.length : v) out[k] = v;
    }
  }
  if (!wiki && !taxon) throw new Error(out.wikiError || out.inatError || 'Zu diesem Namen wurde nichts gefunden.');
  return out;
}

// ---------- Übernehmen ----------
/** Fakten, die automatisch ergänzt werden können (Reihenfolge wie in den Einstellungen). */
export const FACT_KEYS = [
  ['deName', 'Deutscher Name'], ['latinName', 'Botanischer Name'], ['family', 'Familie'],
  ['lifeForm', 'Lebensform'], ['uses', 'Verwendung'], ['features', 'Merkmale'], ['bloom', 'Blütezeit'],
  ['habitat', 'Standort'], ['toxicity', 'Giftigkeit'], ['confusion', 'Verwechslung'], ['notes', 'Quellenangabe in den Notizen'],
];

/** Fakten, die nicht ergänzt werden: in den Einstellungen abgewählt oder Feld unter „Bereiche“ ausgeblendet. */
export function skippedFacts(settings = {}) {
  const skip = new Set(settings.factsOff || []);
  for (const [k] of FACT_KEYS) if (k !== 'deName' && k !== 'latinName' && !isOn('field:' + k)) skip.add(k);
  return skip;
}

export const FACT_LABELS = {
  deName: 'deutscher Name', latinName: 'botanischer Name', family: 'Familie', features: 'Merkmale', bloom: 'Blütezeit',
  lifeForm: 'Lebensform', toxicity: 'Giftigkeit', habitat: 'Standort', uses: 'Verwendung', confusion: 'Verwechslung', notes: 'Quelle',
};

/**
 * Gefundene Fakten in die Pflanzenangaben p übernehmen. onlyEmpty: nur leere Felder füllen.
 * families: bekannte Familiennamen (für die Schreibweise „Asteraceae (Korbblütler)“).
 * skip: Felder, die nicht ergänzt werden sollen (siehe skippedFacts). Ergebnis: gefüllte Felder.
 */
export function applyFacts(p, facts, { onlyEmpty = true, families = [], skip = new Set() } = {}) {
  const filled = [];
  const isEmpty = v => (Array.isArray(v) ? !v.length : !String(v ?? '').trim());
  const set = (key, value) => {
    if (skip.has(key) || value == null || isEmpty(value)) return;
    if (onlyEmpty && !isEmpty(p[key])) return;
    p[key] = Array.isArray(value) ? [...value] : value;
    filled.push(key);
  };
  set('deName', facts.deName);
  set('latinName', facts.latinName);
  if (facts.family?.family) {
    const known = families.find(f => familyKey(f).toLowerCase() === facts.family.family.toLowerCase());
    set('family', known || (facts.family.familyCommon ? `${facts.family.family} (${facts.family.familyCommon})` : facts.family.family));
  }
  for (const k of ['features', 'bloom', 'lifeForm', 'toxicity', 'habitat', 'uses', 'confusion']) set(k, facts[k]);
  // Herkunft vermerken (Wikipedia-Texte stehen unter CC BY-SA)
  if (!skip.has('notes') && filled.length && facts.sources?.length && !/Quelle:/.test(p.notes || '')) {
    const note = 'Quelle: ' + facts.sources.map(s => (s.name === 'Wikipedia' ? `Wikipedia „${s.title}“ (${s.license})` : s.name)).join(', ');
    p.notes = p.notes ? `${p.notes}\n${note}` : note;
    filled.push('notes');
  }
  return filled;
}

// ---------- Im Hintergrund ergänzen (Schnell-Erfassung) ----------
const queue = [];
let busy = false;
/** Fakten für eine gespeicherte Karte nachträglich holen (nacheinander, um die Dienste zu schonen). */
export function enrichLater(task) {
  queue.push(task);
  if (!busy) runQueue();
}
async function runQueue() {
  busy = true;
  while (queue.length) {
    const t = queue.shift();
    try { await t(); } catch { /* offline o. Ä.: einfach überspringen */ }
    await new Promise(r => setTimeout(r, 1200));
  }
  busy = false;
}
