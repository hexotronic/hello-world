// Steckbrief mit einem kleinen Sprachmodell aus dem Wikipedia-Text herauslesen:
// Text vorbereiten, Anweisung bauen, Antwort prüfen. Reine Funktionen (auch in Node testbar).
import { sections, sectionText, sentencesOf, clip, monthsFrom, monthsMentioned } from './plantfacts.js';
import { norm } from './util.js';

/** Die Fakten, die das Modell liefern kann (Schlüssel wie in den Pflanzenangaben). */
export const LLM_FIELDS = ['features', 'bloom', 'lifeForm', 'habitat', 'toxicity', 'uses', 'confusion'];

/** Für den Steckbrief wichtige Abschnitte eines Wikipedia-Artikels (gekürzt). */
export function articleParts(text) {
  const secs = sections(text);
  const pick = (re, max) => clip(sectionText(secs, re), max);
  return {
    lead: clip(secs[0]?.text || '', 500),
    descr: pick(/^(Beschreibung|Merkmale|Vegetative Merkmale|Erscheinungsbild|Gestalt|Beschreibung und Ökologie)/i, 1000),
    flower: pick(/^(Blüte|Blütenstand|Generative Merkmale|Blütenknospen|Blütezeit)/i, 450),
    habitat: pick(/(Vorkommen|Standort|Verbreitung)/i, 550),
    uses: pick(/^(Verwendung|Nutzung|Heilpflanze|Speisepflanze|Zierpflanze|Anbau)/i, 450),
    // Giftigkeit: Sätze aus dem ganzen Artikel, die davon handeln
    tox: clip(sentencesOf(secs.map(x => x.text).join(' ')).filter(x => /giftig|toxisch|giftstoff/i.test(x) && !/liste giftiger/i.test(x)).slice(0, 2).join(' '), 350),
    confusion: pick(/(Verwechslung|Unterscheidung|Ähnliche Arten)/i, 350),
  };
}

const PART_TITLES = { descr: 'Beschreibung', flower: 'Blüte', habitat: 'Vorkommen', uses: 'Verwendung', tox: 'Giftigkeit', confusion: 'Verwechslung' };

/** Gekürzter Artikeltext für das Modell. */
export function articleForLlm(textOrParts, maxChars = 3200) {
  const parts = typeof textOrParts === 'string' ? articleParts(textOrParts) : textOrParts;
  let out = Object.entries(parts).filter(([, t]) => t).map(([k, t]) => (PART_TITLES[k] ? `${PART_TITLES[k]}: ${t}` : t)).join('\n');
  if (out.length > maxChars) out = clip(out, maxChars);
  return out;
}

/**
 * Belegstellen je Feld: eine Angabe des Modells zählt nur, wenn sie im passenden Abschnitt steht
 * (z. B. Verwechslungen nur im Abschnitt „Verwechslung“). Leere Belegstelle = Feld nicht abfragen.
 */
export function factSources(parts) {
  const j = (...xs) => xs.filter(Boolean).join(' ');
  const all = j(...Object.values(parts));
  return {
    features: j(parts.lead, parts.descr, parts.flower),
    bloom: j(parts.descr, parts.flower, parts.lead),
    lifeForm: j(parts.lead, parts.descr),
    habitat: j(parts.habitat, parts.descr),
    toxicity: /giftig|toxisch|giftstoff|vergiftung/i.test(all) ? all : '',
    uses: j(parts.uses, /verwend|genutzt|nutzung|heilpflanze|zierpflanze|essbar|genießbar/i.test(parts.lead) ? parts.lead : ''),
    confusion: parts.confusion,
  };
}

const FIELD_RULES = {
  features: ['merkmale', 'Woran erkennt man die Pflanze? Wuchs, Höhe, Blätter, Blüten. Höchstens 20 Wörter.'],
  bloom: ['bluetezeit', 'Monate der Blüte, genau wie im Text, z. B. "Mai bis Juli".'],
  lifeForm: ['lebensform', 'Genau eins von: {forms}.'],
  habitat: ['standort', 'Wo wächst sie? Lebensraum, Boden, Licht. Keine Länder. Höchstens 15 Wörter.'],
  toxicity: ['giftigkeit', 'Genau eins von: ungiftig, schwach giftig, giftig, stark giftig. Leer, wenn der Text nichts dazu sagt.'],
  uses: ['verwendung', 'Wofür wird sie genutzt? Höchstens 12 Wörter.'],
  confusion: ['verwechslung', 'Namen ähnlicher Pflanzen, mit denen man sie verwechseln kann. Leer, wenn der Text keine nennt.'],
};

/** Chat-Nachrichten für das Modell. fields: gewünschte Felder (Standard: alle). */
export function llmMessages({ name, article, lifeForms = [], fields = LLM_FIELDS }) {
  const forms = (lifeForms.length ? lifeForms : ['Einjährig', 'Zweijährig', 'Staude', 'Halbstrauch', 'Strauch', 'Baum']).join(', ');
  const use = fields.filter(f => FIELD_RULES[f]);
  const template = '{' + use.map(f => `"${FIELD_RULES[f][0]}": ""`).join(', ') + '}';
  const rules = use.map(f => `- ${FIELD_RULES[f][0]}: ${FIELD_RULES[f][1].replace('{forms}', forms)}`).join('\n');
  return [
    {
      role: 'system',
      content: 'Du liest Texte über Pflanzen und trägst Fakten in einen Steckbrief ein. Du verwendest nur, was im Text steht. Steht etwas nicht im Text, bleibt der Wert "". Du antwortest nur mit JSON auf Deutsch.',
    },
    {
      role: 'user',
      content: `Text über die Pflanze „${name}“:
"""
${article}
"""

Fülle die Werte aus dem Text aus:
${template}

Regeln:
${rules}`,
    },
  ];
}

/** Erstes JSON-Objekt aus der Modellantwort (auch mit Text drumherum oder ```json). */
export function parseLlmJson(output) {
  const s = String(output || '').replace(/<think>[\s\S]*?<\/think>/g, '');
  const start = s.indexOf('{');
  if (start < 0) return null;
  let depth = 0, inStr = false, esc = false;
  for (let i = start; i < s.length; i++) {
    const c = s[i];
    if (inStr) {
      if (esc) esc = false;
      else if (c === '\\') esc = true;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === '{') depth++;
    else if (c === '}' && --depth === 0) {
      try { return JSON.parse(s.slice(start, i + 1)); } catch { return null; }
    }
  }
  // abgeschnittene Antwort: offene Zeichenkette und Klammer schließen
  try { return JSON.parse(s.slice(start) + (inStr ? '"' : '') + '}'); } catch { return null; }
}

const EMPTY = /^(|-|–|—|keine?( angaben?)?|nicht (angegeben|bekannt|im text|erwähnt)|unbekannt|k\.\s?a\.|n\/a|none|null)\.?$/i;

/** Anteil der inhaltlichen Wörter der Antwort, die (als Wortstamm) im Quelltext vorkommen. */
export function groundedShare(answer, source) {
  const src = norm(source);
  const words = (norm(answer).match(/[a-z]{5,}/g) || []);
  if (!words.length) return 1;
  const hits = words.filter(w => src.includes(w.slice(0, Math.min(w.length, 6))));
  return hits.length / words.length;
}

/** Text säubern: Platzhalter verwerfen, Länge begrenzen, nur wenn im Quelltext belegt. */
function cleanText(value, source, max) {
  if (!source) return '';
  let v = Array.isArray(value) ? value.join(', ') : String(value ?? '');
  v = v.replace(/\s+/g, ' ').trim().replace(/^["„“]|["“”]$/g, '');
  if (EMPTY.test(v)) return '';
  // Wiederholung des Beispiels aus der Anweisung
  if (/woran erkennt man|höchstens \d+ wörter|lebensraum, boden, licht|namen ähnlicher pflanzen|wuchs, höhe, blätter, blüten/i.test(v)) return '';
  if (groundedShare(v, source) < 0.6) return '';
  if (v.length > max) v = clip(v, max);
  return v.charAt(0).toUpperCase() + v.slice(1);
}

const LIFE_HINTS = [
  [/einjähr/i, /einjähr|annuell|sommerannuell|winterannuell|therophyt/i],
  [/zweijähr/i, /zweijähr|bienn/i],
  [/staude/i, /staude|ausdauernd|mehrjähr|perenn|hemikryptophyt|geophyt|chamaephyt/i],
  [/halbstrauch|zwergstrauch/i, /halbstrauch|zwergstrauch/i],
  [/strauch/i, /strauch|sträucher/i],
  [/baum/i, /baum/i],
  [/sukkulent|kakt/i, /sukkulent|kakte|kaktus/i],
];

/** Lebensform(en) auf die eigene Auswahlliste abbilden; nur, wenn der Text dazu passt. */
function cleanLifeForm(value, source, lifeForms) {
  if (!source) return [];
  const items = (Array.isArray(value) ? value : String(value ?? '').split(/,|\/(?!Kak)| oder | und /)).map(x => String(x).trim()).filter(Boolean);
  const out = [];
  for (const item of items) {
    if (EMPTY.test(item)) continue;
    const own = lifeForms.find(x => norm(x) === norm(item)) || lifeForms.find(x => norm(item).includes(norm(x)) || norm(x).includes(norm(item)));
    const label = own || item;
    const hint = LIFE_HINTS.find(([re]) => re.test(label));
    if (hint ? !hint[1].test(source) : !own) continue;
    if (!out.includes(label)) out.push(label);
  }
  return out.slice(0, 2);
}

function cleanToxicity(value, source) {
  const v = String(Array.isArray(value) ? value.join(' ') : value ?? '').toLowerCase().trim();
  if (!v || EMPTY.test(v)) return '';
  if (!/giftig|toxisch|giftstoff|vergiftung/i.test(source)) return '';
  if (/ungiftig|nicht giftig|kein\w* gift/.test(v)) return 'none';
  if (/stark|hoch|sehr|tödlich/.test(v)) return 'high';
  if (/schwach|leicht|gering|mäßig|wenig/.test(v)) return 'low';
  if (/giftig/.test(v)) return 'toxic';
  return '';
}

/** Monate nur übernehmen, wenn sie auch im Text stehen. */
function cleanBloom(value, source) {
  if (!source) return [];
  const months = monthsFrom(Array.isArray(value) ? value.join(', ') : value);
  if (!months.length || months.length > 10) return [];
  const present = new Set(monthsMentioned(source));
  const ends = [months[0], months[months.length - 1]];
  // Anfangs- und Endmonat müssen vorkommen (Zwischenmonate ergeben sich aus „bis“)
  return ends.every(m => present.has(m)) || months.every(m => present.has(m)) ? months : [];
}

/**
 * Modellantwort in Steckbrief-Felder umwandeln. source: der Text, den das Modell bekommen hat.
 * Ergebnis enthält nur belegte, nicht leere Felder.
 */
export function llmToFacts(obj, source, { lifeForms = [] } = {}) {
  if (!obj || typeof obj !== 'object') return {};
  const src = k => (typeof source === 'string' ? source : source?.[k] || '');
  const get = (...keys) => {
    for (const k of keys) {
      const hit = Object.keys(obj).find(x => norm(x).replace(/[^a-z]/g, '') === k);
      if (hit != null) return obj[hit];
    }
    return '';
  };
  const out = {
    features: cleanText(get('merkmale', 'beschreibung'), src('features'), 360),
    bloom: cleanBloom(get('bluetezeit', 'blutezeit', 'bluhzeit'), src('bloom')),
    lifeForm: cleanLifeForm(get('lebensform'), src('lifeForm'), lifeForms),
    habitat: cleanText(get('standort', 'vorkommen'), src('habitat'), 300),
    toxicity: cleanToxicity(get('giftigkeit', 'giftig'), src('toxicity')),
    uses: cleanText(get('verwendung', 'nutzung'), src('uses'), 260),
    confusion: cleanText(get('verwechslung', 'verwechslungen'), src('confusion'), 260),
  };
  // dieselbe Antwort in mehreren Feldern: nur im ersten behalten
  const seen = new Set();
  for (const k of ['features', 'habitat', 'uses', 'confusion']) {
    if (!out[k]) continue;
    const key = norm(out[k]);
    if (seen.has(key)) out[k] = ''; else seen.add(key);
  }
  for (const k of Object.keys(out)) if (Array.isArray(out[k]) ? !out[k].length : !out[k]) delete out[k];
  return out;
}
