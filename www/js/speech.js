// Spracherkennung: in der Android-App über das eigene Modul, im Browser über die Web-Speech-Schnittstelle.
import { h } from './ui.js';
import { icon } from './icons.js';
import { SpeechNative } from './native.js';

const SR = window.SpeechRecognition || window.webkitSpeechRecognition;

export const SPEECH_LANGS = [
  { value: 'de-DE', label: 'Deutsch' },
  { value: 'it-IT', label: 'Italienisch (gut für botanische Namen)' },
  { value: 'en-US', label: 'Englisch' },
];

export function speechSupported() {
  return !!(SpeechNative || SR);
}

export class SpeechError extends Error {
  constructor(message, code) { super(message); this.code = code; }
}

/** Hört zu und liefert die erkannten Vorschläge (bester zuerst). */
export function listen({ lang = 'de-DE', prompt = 'Name sagen …' } = {}) {
  if (SpeechNative) {
    return SpeechNative.listen({ language: lang, prompt })
      .then(r => (r.matches || []).filter(Boolean))
      .catch(err => {
        throw new SpeechError(err?.code === 'CANCELLED' ? 'Nichts erkannt.' : (err?.message || 'Spracherkennung fehlgeschlagen.'), err?.code || 'ERROR');
      });
  }
  if (!SR) return Promise.reject(new SpeechError('Spracherkennung wird hier nicht unterstützt.', 'UNAVAILABLE'));
  return new Promise((resolve, reject) => {
    const rec = new SR();
    rec.lang = lang;
    rec.maxAlternatives = 5;
    rec.interimResults = false;
    rec.continuous = false;
    let done = false;
    rec.onresult = e => {
      done = true;
      const res = e.results[0];
      const alts = [];
      for (let i = 0; i < res.length; i++) alts.push(res[i].transcript);
      resolve(alts.filter(Boolean));
    };
    rec.onerror = e => {
      done = true;
      const msg = {
        'not-allowed': 'Mikrofon ist nicht erlaubt.',
        'service-not-allowed': 'Mikrofon ist nicht erlaubt.',
        'no-speech': 'Nichts gehört. Bitte noch einmal.',
        'audio-capture': 'Kein Mikrofon gefunden.',
        network: 'Für die Spracherkennung wird Internet gebraucht.',
      }[e.error] || 'Spracherkennung fehlgeschlagen.';
      reject(new SpeechError(msg, e.error));
    };
    rec.onend = () => { if (!done) reject(new SpeechError('Nichts erkannt.', 'no-speech')); };
    try { rec.start(); } catch (err) { reject(new SpeechError(err?.message || 'Spracherkennung fehlgeschlagen.', 'ERROR')); }
  });
}

/**
 * Mikrofon-Knopf. onResult(alternatives) wird mit den erkannten Vorschlägen aufgerufen.
 * big: großer runder Knopf (Training „Name sprechen“).
 */
export function micButton({ getLang, onResult, onError, big = false, label = 'Antwort sprechen' }) {
  const b = h('button', {
    type: 'button', class: 'mic-btn' + (big ? ' big' : ''), 'aria-label': label, title: label,
    onclick: async () => {
      if (b.classList.contains('listening')) return;
      b.classList.add('listening');
      try {
        const alts = await listen({ lang: getLang() });
        b.classList.remove('listening');
        if (alts.length) onResult(alts);
        else onError?.(new SpeechError('Nichts erkannt.', 'no-speech'));
      } catch (err) {
        b.classList.remove('listening');
        onError?.(err);
      }
    },
  }, icon('mic', big ? 34 : 22), big ? h('span', null, label) : null);
  return b;
}
