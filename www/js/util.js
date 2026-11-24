// Kleine Hilfsfunktionen ohne Abhängigkeiten.

export function uid() {
  try {
    if (crypto.randomUUID) return crypto.randomUUID();
  } catch { /* unsicherer Kontext */ }
  return 'id-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
}

export function shuffle(arr) {
  const a = arr.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

export function pick(arr) {
  return arr[Math.floor(Math.random() * arr.length)];
}

export function clamp(x, lo, hi) {
  return Math.min(hi, Math.max(lo, x));
}

/** Normalisiert Text für Suche und Antwortvergleich (Umlaute, Groß/klein, Satzzeichen). */
export function norm(s) {
  return String(s ?? '')
    .toLowerCase()
    .replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
    .normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();
}

export function levenshtein(a, b) {
  if (a === b) return 0;
  if (!a.length) return b.length;
  if (!b.length) return a.length;
  let prev = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    const cur = [i];
    for (let j = 1; j <= b.length; j++) {
      cur[j] = Math.min(
        prev[j] + 1,
        cur[j - 1] + 1,
        prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1)
      );
    }
    prev = cur;
  }
  return prev[b.length];
}

/**
 * Vergleicht eine eingetippte Antwort mit einer Liste erlaubter Antworten.
 * Gibt {result: 'correct' | 'close' | 'wrong', best} zurück.
 */
export function checkAnswer(input, answers) {
  const n = norm(input);
  const list = answers.filter(Boolean);
  if (!n || !list.length) return { result: 'wrong', best: list[0] || '' };
  let best = list[0];
  let bestDist = Infinity;
  for (const ans of list) {
    // Auch Teile wie "Wiesen-Salbei" / "Salbei" oder Alternativen mit "/" oder "," zulassen.
    const variants = [ans, ...String(ans).split(/[\/,;]| oder /)].map(norm).filter(Boolean);
    for (const v of variants) {
      const d = levenshtein(n, v);
      if (d < bestDist) { bestDist = d; best = ans; }
    }
  }
  if (bestDist === 0) return { result: 'correct', best };
  const len = norm(best).length;
  if (bestDist <= Math.max(1, Math.floor(len / 6))) return { result: 'close', best };
  // Nur ein Teil des Namens (z. B. "Salbei" statt "Wiesen-Salbei"): fast richtig
  if (n.length >= 4) {
    for (const ans of list) {
      if (norm(ans).split(' ').includes(n)) return { result: 'close', best: ans };
    }
  }
  return { result: 'wrong', best };
}

/**
 * Klangschlüssel für gesprochene Namen (deutsch/lateinisch): gleich klingende Schreibweisen werden gleich,
 * z. B. „Taraxakum offizinale“ ≈ „Taraxacum officinale“, „Füsalis“ ≈ „Physalis“.
 */
export function soundKey(s) {
  let k = norm(s);
  const rules = [
    [/sch/g, 's'], [/ch/g, 'k'], [/ph/g, 'f'], [/th/g, 't'], [/rh/g, 'r'], [/qu/g, 'kw'], [/ck/g, 'k'],
    [/c(?=[eiy])/g, 's'], [/c/g, 'k'], [/z/g, 's'], [/x/g, 'ks'], [/y/g, 'i'], [/ae/g, 'e'], [/oe/g, 'e'],
    [/ue/g, 'u'], [/v/g, 'w'], [/ie/g, 'i'], [/h(?![aeiou])/g, ''], [/(.)\1+/g, '$1'],
  ];
  for (const [re, r] of rules) k = k.replace(re, r);
  // Wortgrenzen ignorieren: Spracherkennung trennt Namen oft anders („mille folium“)
  return k.replace(/\s+/g, '');
}

/**
 * Bewertet gesprochene Antworten (mehrere Erkennungsvorschläge) gegen erlaubte Antworten.
 * Ergebnis wie checkAnswer, zusätzlich heard = bester erkannter Text.
 */
export function checkSpoken(alternatives, answers) {
  const rank = { correct: 2, close: 1, wrong: 0 };
  let best = { result: 'wrong', best: answers.filter(Boolean)[0] || '', heard: alternatives[0] || '' };
  for (const alt of alternatives) {
    const plain = checkAnswer(alt, answers);
    let res = plain;
    if (plain.result !== 'correct') {
      // nach Klang vergleichen
      const key = soundKey(alt);
      for (const ans of answers.filter(Boolean)) {
        const variants = [ans, ...String(ans).split(/[\/,;]| oder /)].map(soundKey).filter(Boolean);
        for (const v of variants) {
          const d = levenshtein(key, v);
          const r = d === 0 ? 'correct' : d <= Math.max(1, Math.floor(v.length / 5)) ? 'close' : null;
          if (r && rank[r] > rank[res.result]) res = { result: r, best: ans };
        }
      }
    }
    if (rank[res.result] > rank[best.result]) best = { ...res, heard: alt };
    if (best.result === 'correct') break;
  }
  return best;
}

export const MONTHS_SHORT = ['J', 'F', 'M', 'A', 'M', 'J', 'J', 'A', 'S', 'O', 'N', 'D'];
export const MONTHS = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli', 'August', 'September', 'Oktober', 'November', 'Dezember'];
export const MONTHS_ABBR = ['Jan', 'Feb', 'Mär', 'Apr', 'Mai', 'Jun', 'Jul', 'Aug', 'Sep', 'Okt', 'Nov', 'Dez'];

/** Fasst Monate (1–12) zu lesbaren Bereichen zusammen, z. B. "Mai–Aug". */
export function formatMonths(months) {
  const m = [...new Set(months || [])].filter(x => x >= 1 && x <= 12).sort((a, b) => a - b);
  if (!m.length) return '';
  if (m.length === 12) return 'ganzjährig';
  const ranges = [];
  let start = m[0], prev = m[0];
  for (let i = 1; i <= m.length; i++) {
    if (m[i] === prev + 1) { prev = m[i]; continue; }
    ranges.push(start === prev ? MONTHS_ABBR[start - 1] : `${MONTHS_ABBR[start - 1]}–${MONTHS_ABBR[prev - 1]}`);
    start = prev = m[i];
  }
  return ranges.join(', ');
}

export function plural(n, one, many) {
  return `${n} ${n === 1 ? one : many}`;
}

export function fmtDate(ts) {
  if (!ts) return '–';
  return new Date(ts).toLocaleDateString('de-DE', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function fmtBytes(b) {
  if (!b && b !== 0) return '–';
  if (b < 1024) return b + ' B';
  if (b < 1024 * 1024) return (b / 1024).toFixed(0) + ' KB';
  if (b < 1024 * 1024 * 1024) return (b / 1024 / 1024).toFixed(1).replace('.', ',') + ' MB';
  return (b / 1024 / 1024 / 1024).toFixed(2).replace('.', ',') + ' GB';
}

export function debounce(fn, ms) {
  let t;
  return (...args) => { clearTimeout(t); t = setTimeout(() => fn(...args), ms); };
}

/** Name aus Dateinamen ableiten: "loewenzahn_wiese.jpg" → "Loewenzahn wiese". Kamera-Namen ergeben ''. */
export function nameFromFilename(filename) {
  const base = String(filename || '').replace(/\.[a-z0-9]+$/i, '').replace(/[_]+/g, ' ').trim();
  if (!base) return '';
  if (/^(img|dsc|pxl|photo|image|bild|foto|screenshot|signal|wa)[\s\-_]?\d/i.test(base)) return '';
  if (/^[\d\s\-_.]+$/.test(base)) return '';
  return base.charAt(0).toUpperCase() + base.slice(1);
}

export function speak(text, lang = 'de-DE') {
  try {
    if (!('speechSynthesis' in window) || !text) return false;
    speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = lang;
    u.rate = 0.9;
    speechSynthesis.speak(u);
    return true;
  } catch {
    return false;
  }
}
