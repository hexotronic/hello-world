// Lernplanung nach dem SM-2-Prinzip mit Lernschritten (wie bei klassischen Karteikarten-Apps).
// Bewertungen: 1 = Nochmal, 2 = Schwer, 3 = Gut, 4 = Einfach.

export const DECK_DEFAULTS = {
  newPerDay: 20,
  reviewsPerDay: 200,
  learnSteps: [1, 10],      // Minuten
  relearnSteps: [10],       // Minuten
  graduatingInterval: 1,    // Tage
  easyInterval: 4,          // Tage
  startEase: 2.5,
  easyBonus: 1.3,
  hardFactor: 1.2,
  intervalModifier: 1,
  lapseMult: 0,
  minLapseInterval: 1,
  maxInterval: 36500,
  leechThreshold: 8,
  newOrder: 'added',        // 'added' | 'random'
  typeAnswer: false,        // Antwort eintippen
  showHint: true,           // Tipp-Knopf bei Pflanzenkarten
  randomPhoto: true,        // bei mehreren Fotos zufälliges zeigen
};

export function deckConfig(deck) {
  return { ...DECK_DEFAULTS, ...(deck?.config || {}) };
}

let dayStartHour = 4;
export function setDayStartHour(h) {
  dayStartHour = Number.isFinite(+h) ? +h : 4;
}

/** Beginn des Lerntags (Standard 4:00 Uhr), zu dem ts gehört. */
export function dayStart(ts = Date.now()) {
  const d = new Date(ts);
  const s = new Date(d.getFullYear(), d.getMonth(), d.getDate(), dayStartHour);
  if (d < s) s.setDate(s.getDate() - 1);
  return s.getTime();
}
export function dayStartPlus(days, ts = Date.now()) {
  const s = new Date(dayStart(ts));
  s.setDate(s.getDate() + days);
  return s.getTime();
}
const REF = new Date(2020, 0, 1).getTime();
export function dayIndex(ts) {
  return Math.round((dayStart(ts) - REF) / 864e5);
}

export function newSrs() {
  return { state: 'new', due: 0, ivl: 0, ease: 0, reps: 0, lapses: 0, step: 0, last: 0 };
}

function fuzz(ivl) {
  if (ivl < 3) return ivl;
  const range = ivl < 7 ? 1 : ivl < 30 ? Math.max(2, Math.round(ivl * 0.15)) : Math.max(4, Math.round(ivl * 0.05));
  return Math.max(1, ivl + Math.floor(Math.random() * (2 * range + 1)) - range);
}

/** Berechnet den neuen Lernzustand nach einer Bewertung. Ändert das Original nicht. */
export function answer(srs, rating, cfg, now = Date.now(), { useFuzz = true } = {}) {
  const s = { ...srs };
  s.reps = (s.reps || 0) + 1;
  s.last = now;
  const inMin = m => now + Math.round(m * 60000);

  if (s.state === 'new') {
    s.state = 'learning';
    s.step = 0;
    s.ease = cfg.startEase;
  }

  if (s.state === 'learning' || s.state === 'relearning') {
    const relearn = s.state === 'relearning';
    const steps = relearn ? cfg.relearnSteps : cfg.learnSteps;
    const graduate = ivl => {
      s.state = 'review';
      s.step = 0;
      s.ivl = Math.max(1, Math.round(ivl));
      s.due = dayStartPlus(s.ivl, now);
    };
    const gradIvl = relearn ? Math.max(cfg.minLapseInterval, s.ivl || 1) : cfg.graduatingInterval;
    const easyIvl = relearn ? Math.max(cfg.minLapseInterval, s.ivl || 1) + 1 : cfg.easyInterval;
    if (!steps.length) {
      graduate(rating === 4 ? easyIvl : gradIvl);
      return s;
    }
    const step = Math.min(s.step || 0, steps.length - 1);
    switch (rating) {
      case 1:
        s.step = 0;
        s.due = inMin(steps[0]);
        break;
      case 2: {
        let d = steps[step];
        if (step === 0) d = steps.length > 1 ? (steps[0] + steps[1]) / 2 : Math.min(steps[0] * 1.5, steps[0] + 1440);
        s.step = step;
        s.due = inMin(d);
        break;
      }
      case 3:
        if (step + 1 >= steps.length) graduate(gradIvl);
        else { s.step = step + 1; s.due = inMin(steps[s.step]); }
        break;
      default:
        graduate(easyIvl);
    }
    return s;
  }

  // Wiederholung (review)
  const overdue = Math.max(0, dayIndex(now) - dayIndex(s.due || now));
  const ivl = Math.max(1, s.ivl || 1);
  const mod = cfg.intervalModifier || 1;
  const lim = x => Math.min(cfg.maxInterval, Math.max(1, Math.round(x)));

  if (rating === 1) {
    s.lapses = (s.lapses || 0) + 1;
    s.ease = Math.max(1.3, (s.ease || cfg.startEase) - 0.2);
    s.ivl = Math.max(cfg.minLapseInterval, Math.round(ivl * cfg.lapseMult));
    if (cfg.relearnSteps.length) {
      s.state = 'relearning';
      s.step = 0;
      s.due = inMin(cfg.relearnSteps[0]);
    } else {
      s.due = dayStartPlus(s.ivl, now);
    }
    return s;
  }
  const ease = s.ease || cfg.startEase;
  const hard = lim(Math.max(ivl + 1, ivl * cfg.hardFactor * mod));
  const good = lim(Math.max(hard + 1, (ivl + overdue / 2) * ease * mod));
  const easy = lim(Math.max(good + 1, (ivl + overdue) * ease * cfg.easyBonus * mod));
  let next;
  if (rating === 2) { s.ease = Math.max(1.3, ease - 0.15); next = hard; }
  else if (rating === 3) { next = good; }
  else { s.ease = ease + 0.15; next = easy; }
  s.ivl = useFuzz ? Math.min(cfg.maxInterval, fuzz(next)) : next;
  s.due = dayStartPlus(s.ivl, now);
  return s;
}

/** Kurzer Text für die Dauer bis zur nächsten Fälligkeit, z. B. "10 Min", "4 T", "2,5 Mo". */
export function formatDelay(ms) {
  const min = ms / 60000;
  if (min < 1) return '<1 Min';
  if (min < 60) return `${Math.round(min)} Min`;
  const hrs = min / 60;
  if (hrs < 24) return `${Math.round(hrs)} Std`;
  return formatDays(Math.round(hrs / 24));
}
export function formatDays(days) {
  if (days < 30) return `${days} T`;
  if (days < 365) return `${(days / 30).toFixed(1).replace('.0', '').replace('.', ',')} Mo`;
  return `${(days / 365).toFixed(1).replace('.0', '').replace('.', ',')} J`;
}

/** Vorschau der Intervalle für alle vier Knöpfe. */
export function previewIntervals(srs, cfg, now = Date.now()) {
  return [1, 2, 3, 4].map(r => {
    const s = answer(srs, r, cfg, now, { useFuzz: false });
    if (s.state === 'review') return formatDays(s.ivl);
    return formatDelay(s.due - now);
  });
}

export function isActive(card, now = Date.now()) {
  return !card.suspended && !(card.buriedUntil && card.buriedUntil > now);
}

/** Wie viele neue Karten und Wiederholungen heute schon gemacht wurden (pro Stapel). */
export function doneToday(logs, deckId) {
  const out = { new: 0, review: 0 };
  for (const l of logs) {
    if (l.mode !== 'srs' || (deckId && l.deckId !== deckId)) continue;
    if (l.prevState === 'new') out.new++;
    else if (l.prevState === 'review') out.review++;
  }
  return out;
}

function extraNewToday(deck, now) {
  const e = deck?.extraNew;
  return e && e.day === dayIndex(now) ? e.n : 0;
}

/** Zählt Neu / Lernen / Fällig eines Stapels unter Beachtung der Tageslimits. */
export function deckCounts(cards, deck, logsToday, now = Date.now()) {
  const cfg = deckConfig(deck);
  const end = dayStartPlus(1, now);
  let n = 0, l = 0, r = 0;
  for (const c of cards) {
    if (!isActive(c, now)) continue;
    const s = c.srs;
    if (s.state === 'new') n++;
    else if (s.state === 'learning' || s.state === 'relearning') { if (s.due < end) l++; }
    else if (s.due < end) r++;
  }
  const done = doneToday(logsToday, deck?.id);
  n = Math.max(0, Math.min(n, cfg.newPerDay + extraNewToday(deck, now) - done.new));
  r = Math.max(0, Math.min(r, cfg.reviewsPerDay - done.review));
  return { n, l, r };
}

/** Stellt die Warteschlangen für eine Lernsitzung zusammen. */
export function buildQueues(cards, deck, logsToday, now = Date.now()) {
  const cfg = deckConfig(deck);
  const end = dayStartPlus(1, now);
  const learn = [], review = [], fresh = [];
  for (const c of cards) {
    if (!isActive(c, now)) continue;
    const s = c.srs;
    if (s.state === 'new') fresh.push(c);
    else if (s.state === 'learning' || s.state === 'relearning') { if (s.due < end) learn.push(c); }
    else if (s.due < end) review.push(c);
  }
  const done = doneToday(logsToday, deck?.id);
  review.sort((a, b) => a.srs.due - b.srs.due);
  if (cfg.newOrder === 'random') fresh.sort(() => Math.random() - 0.5);
  else fresh.sort((a, b) => (a.created || 0) - (b.created || 0));
  return {
    learn,
    review: review.slice(0, Math.max(0, cfg.reviewsPerDay - done.review)),
    fresh: fresh.slice(0, Math.max(0, cfg.newPerDay + extraNewToday(deck, now) - done.new)),
    cfg,
  };
}

/** Karte ist "ausgereift", wenn das Intervall mindestens 21 Tage beträgt. */
export function maturity(card) {
  const s = card.srs;
  if (s.state === 'new') return 'new';
  if (s.state === 'learning' || s.state === 'relearning') return 'learning';
  return s.ivl >= 21 ? 'mature' : 'young';
}
