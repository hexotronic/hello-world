// Steckbrief mit einem kleinen Sprachmodell aus dem Wikipedia-Text herauslesen:
// Text vorbereiten, Anweisung bauen, Antwort prüfen. Reine Funktionen (auch in Node testbar).
import { sections, sectionText, clip, monthsFrom, monthsMentioned } from './plantfacts.js';
import { norm } from './util.js';

/** Die Fakten, die das Modell liefern kann (Schlüssel wie in den Pflanzenangaben). */
export const LLM_FIELDS = ['features', 'bloom', 'lifeForm', 'habitat', 'toxicity', 'uses', 'confusion'];

/** Wikipedia-Artikel auf die für den Steckbrief wichtigen Abschnitte kürzen. */
export function articleForLlm(text, maxChars = 3200) {
  const secs = sections(text);
  const pick = (re, max) => clip(sectionText(secs, re), max);
  const parts = [
    ['', clip(secs[0]?.text || '', 500)],
    ['Beschreibung', pick(/^(Beschreibung|Merkmale|Vegetative Merkmale|Erscheinungsbild|Gestalt|Beschreibung und Ökologie)/i, 1000)],
    ['Blüte', pick(/^(Blüte|Blütenstand|Generative Merkmale|Blütenknospen|Blütezeit)/i, 450)],
    ['Vorkommen', pick(/(Vorkommen|Standort|Verbreitung|Ökologie)/i, 550)],
    ['Verwendung', pick(/^(Verwendung|Nutzung|Heilpflanze|Speisepflanze|Zierpflanze|Anbau)/i, 450)],
    ['Giftigkeit', pick(/(Giftigkeit|Toxikologie|Inhaltsstoffe)/i, 350)],
    ['Verwechslung', pick(/(Verwechslung|Unterscheidung|Ähnliche Arten)/i, 350)],
  ].filter(([, t]) => t);
  let out = parts.map(([title, t]) => (title ? `${title}: ${t}` : t)).join('\n');
  if (out.length > maxChars) out = clip(out, maxChars);
  return out;
}

/** Chat-Nachrichten für das Modell. */
export function llmMessages({ name, article, lifeForms = [] }) {
  const forms = (lifeForms.length ? lifeForms : ['Einjährig', 'Zweijährig', 'Staude', 'Halbstrauch', 'Strauch', 'Baum']).join(', ');
  return [
    {
      role: 'system',
      content: 'Du füllst Pflanzen-Steckbriefe aus. Du nutzt nur Angaben aus dem gegebenen Text und erfindest nichts. Du antwortest nur mit JSON.',
    },
    {
      role: 'user',
      content: `Text über die Pflanze „${name}“:
"""
${article}
"""

Fülle diesen Steckbrief auf Deutsch aus. Schreibe kurz (höchstens 25 Wörter pro Feld). Steht etwas nicht im Text, schreibe "".
{
"merkmale": "woran man die Pflanze erkennt: Wuchs, Höhe, Blätter, Blüten",
"bluetezeit": "Monate, z. B. Juni bis August",
"lebensform": "eins von: ${forms}",
"standort": "wo sie wächst: Lebensraum, Boden, Licht",
"giftigkeit": "ungiftig, schwach giftig, giftig oder stark giftig",
"verwendung": "Nutzung, z. B. als Heil-, Zier- oder Nahrungspflanze",
"verwechslung": "ähnliche Pflanzen, mit denen man sie verwechseln kann"
}`,
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
  let v = Array.isArray(value) ? value.join(', ') : String(value ?? '');
  v = v.replace(/\s+/g, ' ').trim().replace(/^["„“]|["“”]$/g, '');
  if (EMPTY.test(v)) return '';
  // Wiederholung des Beispiels aus der Anweisung
  if (/woran man die Pflanze erkennt|z\. B\. als Heil-|mit denen man sie verwechseln/i.test(v)) return '';
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
  const get = (...keys) => {
    for (const k of keys) {
      const hit = Object.keys(obj).find(x => norm(x).replace(/[^a-z]/g, '') === k);
      if (hit != null) return obj[hit];
    }
    return '';
  };
  const out = {
    features: cleanText(get('merkmale', 'beschreibung'), source, 360),
    bloom: cleanBloom(get('bluetezeit', 'blutezeit', 'bluhzeit'), source),
    lifeForm: cleanLifeForm(get('lebensform'), source, lifeForms),
    habitat: cleanText(get('standort', 'vorkommen'), source, 300),
    toxicity: cleanToxicity(get('giftigkeit', 'giftig'), source),
    uses: cleanText(get('verwendung', 'nutzung'), source, 260),
    confusion: cleanText(get('verwechslung', 'verwechslungen'), source, 260),
  };
  for (const k of Object.keys(out)) if (Array.isArray(out[k]) ? !out[k].length : !out[k]) delete out[k];
  return out;
}
