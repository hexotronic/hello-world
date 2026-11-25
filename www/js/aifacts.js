// Steckbrief mit dem Sprachmodell auf dem Gerät verfeinern (nach der schnellen Texterkennung).
import { llmGenerate, llmStatus, llmSupported, llmAbort } from './llm.js';
import { articleParts, articleForLlm, factSources, llmMessages, parseLlmJson, llmToFacts } from './llmfacts.js';

/** Felder, die das Modell kurz zusammenfasst (ersetzen das Ergebnis der Texterkennung). */
export const AI_SUMMARY_FIELDS = ['features', 'uses', 'confusion'];
/** Felder, die das Modell nur ergänzt, wenn die Texterkennung nichts gefunden hat. */
export const AI_FALLBACK_FIELDS = ['habitat', 'bloom', 'lifeForm'];

/** Ist die KI-Auswertung eingeschaltet und das Modell auf dem Gerät? */
export async function aiReady(settings) {
  if (!settings?.aiFacts || !llmSupported()) return false;
  return (await llmStatus()).cached;
}

/**
 * Artikeltext mit dem Modell auswerten. facts: Ergebnis von lookupPlant (mit wikiText).
 * skip: Felder, die nicht ergänzt werden sollen. onProgress(0..1). Ergebnis: nur belegte Felder.
 */
export async function aiFacts(facts, { name, lifeForms = [], skip = new Set(), onProgress } = {}) {
  if (!facts?.wikiText) return {};
  const parts = articleParts(facts.wikiText);
  const sources = factSources(parts);
  const has = k => (Array.isArray(facts[k]) ? facts[k].length : !!facts[k]);
  const fields = [
    ...AI_SUMMARY_FIELDS,
    ...AI_FALLBACK_FIELDS.filter(k => !has(k)),
  ].filter(k => !skip.has(k) && sources[k]);
  if (!fields.length) return {};
  const messages = llmMessages({ name, article: articleForLlm(parts, 2600), lifeForms, fields });
  const expected = 14 + fields.length * 22;          // ungefähre Länge der Antwort in Tokens
  onProgress?.(0);
  const out = await llmGenerate(messages, {
    maxTokens: Math.min(260, Math.round(expected * 1.6)),
    onToken: ev => onProgress?.(Math.min(0.97, 0.1 + 0.9 * ev.n / expected)),
  });
  onProgress?.(1);
  const ai = llmToFacts(parseLlmJson(out.text), sources, { lifeForms });
  for (const k of Object.keys(ai)) if (!fields.includes(k)) delete ai[k];
  return ai;
}

/**
 * KI-Ergebnis in Pflanzenangaben übernehmen: nur Felder, die eben automatisch gefüllt wurden
 * (filledKeys, Werte damals filledValues) und seitdem unverändert sind, oder die noch leer sind.
 * Ergebnis: geänderte Felder.
 */
export function mergeAi(target, ai, filledKeys, filledValues) {
  const same = (a, b) => JSON.stringify(a ?? '') === JSON.stringify(b ?? '');
  const empty = v => (Array.isArray(v) ? !v.length : !String(v ?? '').trim());
  const changed = [];
  for (const [k, v] of Object.entries(ai)) {
    const untouched = filledKeys.has(k) && same(target[k], filledValues[k]);
    if (!untouched && !empty(target[k])) continue;
    if (same(target[k], v)) continue;
    target[k] = Array.isArray(v) ? [...v] : v;
    changed.push(k);
  }
  return changed;
}

export { llmAbort as aiAbort };
