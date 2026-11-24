// Statistik: heute, Lernserie, Kartenbestand, Prognose, Verlauf, schwierigste Karten.
import { app } from '../app.js';
import { h, topbar } from '../ui.js';
import { icon } from '../icons.js';
import { allCards, allLogs, listDecks, displayName } from '../db.js';
import { lazyPhoto } from '../images.js';
import { dayIndex, dayStart, dayStartPlus, maturity } from '../srs.js';

const WD = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
const prefs = { deckId: '' };

/** Säulendiagramm einer Reihe mit Tooltip beim Antippen/Hovern. */
function columns(data, { label, unit = 'Karten', highlightLast = false }) {
  const max = Math.max(1, ...data.map(d => d.v));
  const nice = max <= 5 ? max : Math.ceil(max / 5) * 5;
  const tip = h('div', { class: 'col-tip', hidden: true, role: 'status' });
  const plot = h('div', { class: 'col-plot' },
    data.map((d, i) => {
      const slot = h('button', {
        type: 'button', class: 'col-slot', 'aria-label': `${d.title}: ${d.v} ${unit}`,
        onpointerenter: () => showTip(slot, d), onfocus: () => showTip(slot, d), onclick: () => showTip(slot, d),
        onpointerleave: () => { tip.hidden = true; }, onblur: () => { tip.hidden = true; },
      },
      h('span', { class: 'col-bar' + (highlightLast && i === data.length - 1 ? ' accent' : ''), style: { height: d.v ? `max(3px, ${(d.v / nice) * 100}%)` : '0' } }));
      return slot;
    }),
    tip);
  function showTip(slot, d) {
    tip.hidden = false;
    tip.replaceChildren(h('strong', null, `${d.v} ${unit}`), h('span', null, d.title));
    const pr = plot.getBoundingClientRect(), sr = slot.getBoundingClientRect();
    const x = sr.left - pr.left + sr.width / 2;
    tip.style.left = `${Math.min(Math.max(x, 60), pr.width - 60)}px`;
  }
  const axis = h('div', { class: 'col-axis' }, data.map((d, i) => h('span', null, d.tick && i % (data.length > 20 ? 5 : data.length > 10 ? 2 : 1) === 0 ? d.tick : '')));
  return h('figure', { class: 'colchart', 'aria-label': label },
    h('div', { class: 'col-wrap' },
      h('div', { class: 'col-y' }, h('span', null, nice), h('span', null, Math.round(nice / 2)), h('span', null, 0)),
      h('div', { class: 'col-main' }, h('div', { class: 'col-grid' }, h('i'), h('i'), h('i')), plot, axis)),
    h('table', { class: 'sr-only' }, h('caption', null, label), data.map(d => h('tr', null, h('th', null, d.title), h('td', null, d.v)))));
}

function stat(label, value, sub) {
  return h('div', { class: 'stat' }, h('span', { class: 'stat-l' }, label), h('span', { class: 'stat-v' }, value), sub ? h('span', { class: 'stat-s' }, sub) : null);
}

export default async function statsView() {
  const [cards0, logs0, decks] = await Promise.all([allCards(), allLogs(), listDecks()]);
  const root = h('div', { class: 'page stats' });
  const content = h('div', { class: 'stats-body' });

  function render() {
    const deckId = prefs.deckId;
    const cards = deckId ? cards0.filter(c => c.deckId === deckId) : cards0;
    const logs = deckId ? logs0.filter(l => l.deckId === deckId) : logs0;
    const now = Date.now();
    const today = dayIndex(now);
    const t0 = dayStart(now);

    // Heute
    const todayLogs = logs.filter(l => l.time >= t0);
    const srsToday = todayLogs.filter(l => l.mode === 'srs');
    const correctToday = srsToday.filter(l => l.rating > 1).length;
    const minutes = Math.round(srsToday.reduce((s, l) => s + Math.min(l.ms || 0, 60000), 0) / 60000);
    const practiceToday = todayLogs.filter(l => l.mode === 'random' || l.mode === 'quiz');

    // Serie: aufeinanderfolgende Tage mit Lernaktivität
    const days = new Set(logs.map(l => dayIndex(l.time)));
    let streak = 0;
    let d = days.has(today) ? today : today - 1;
    while (days.has(d)) { streak++; d--; }

    // Bestand
    const mat = { new: 0, learning: 0, young: 0, mature: 0 };
    let suspended = 0;
    for (const c of cards) { if (c.suspended) suspended++; else mat[maturity(c)]++; }
    const total = cards.length;
    const segs = [
      { k: 'new', label: 'Neu', v: mat.new },
      { k: 'learning', label: 'Lernphase', v: mat.learning },
      { k: 'young', label: 'Jung', v: mat.young },
      { k: 'mature', label: 'Ausgereift', v: mat.mature },
      { k: 'susp', label: 'Ausgesetzt', v: suspended },
    ];

    // Prognose 14 Tage
    const forecast = Array.from({ length: 14 }, (_, i) => {
      const ds = new Date(dayStartPlus(i, now));
      return { v: 0, title: i === 0 ? 'Heute (inkl. überfällig)' : ds.toLocaleDateString('de-DE', { weekday: 'long', day: 'numeric', month: 'numeric' }), tick: i === 0 ? 'heute' : WD[ds.getDay()] };
    });
    for (const c of cards) {
      if (c.suspended || c.srs.state === 'new') continue;
      const k = Math.max(0, dayIndex(c.srs.due) - today);
      if (k < 14) forecast[k].v++;
    }

    // Verlauf 30 Tage
    const history = Array.from({ length: 30 }, (_, i) => {
      const ds = new Date(dayStartPlus(i - 29, now));
      return { v: 0, title: ds.toLocaleDateString('de-DE', { weekday: 'short', day: 'numeric', month: 'long' }), tick: `${ds.getDate()}.${ds.getMonth() + 1}.` };
    });
    let rev30 = 0, ok30 = 0, matRev = 0, matOk = 0;
    for (const l of logs) {
      const k = dayIndex(l.time) - (today - 29);
      if (k < 0 || k > 29) continue;
      if (l.mode === 'srs' || l.mode === 'cram') history[k].v++;
      if (l.mode === 'srs' && l.prevState === 'review') {
        rev30++; if (l.rating > 1) ok30++;
        if ((l.prevIvl || 0) >= 21) { matRev++; if (l.rating > 1) matOk++; }
      }
    }
    const practice30 = logs.filter(l => (l.mode === 'random' || l.mode === 'quiz') && dayIndex(l.time) > today - 30);
    const practiceOk = practice30.filter(l => l.rating > 1).length;

    const hardest = cards.filter(c => (c.srs.lapses || 0) > 0).sort((a, b) => b.srs.lapses - a.srs.lapses).slice(0, 8);
    const pct = (a, b) => (b ? `${Math.round((a / b) * 100)} %` : '–');

    content.replaceChildren(...[
      h('section', { class: 'stat-grid' },
        stat('Heute gelernt', srsToday.length, srsToday.length ? `${pct(correctToday, srsToday.length)} gewusst · ${minutes} Min.` : 'noch nichts'),
        stat('Lernserie', `${streak} ${streak === 1 ? 'Tag' : 'Tage'}`, days.has(today) ? 'heute schon gelernt' : (streak ? 'heute noch lernen!' : 'Starte heute')),
        stat('Behalten (30 T)', pct(ok30, rev30), rev30 ? `ausgereift: ${pct(matOk, matRev)}` : 'noch keine Wiederholungen'),
        stat('Foto-Übungen (30 T)', practice30.length, practice30.length ? `${pct(practiceOk, practice30.length)} erkannt · heute ${practiceToday.length}` : 'Zufallsfotos & Training')),

      h('section', { class: 'stats-sec' },
        h('h2', { class: 'section-h' }, 'Kartenbestand', h('span', { class: 'muted small' }, ` · ${total} Karten`)),
        total ? h('div', { class: 'segbar', role: 'img', 'aria-label': segs.map(s => `${s.label} ${s.v}`).join(', ') },
          segs.filter(s => s.v).map(s => h('span', { class: 'seg seg-' + s.k, style: { flexGrow: s.v }, title: `${s.label}: ${s.v}` }))) : h('p', { class: 'muted' }, 'Noch keine Karten.'),
        h('ul', { class: 'legend-list' }, segs.map(s => h('li', null, h('i', { class: 'sw seg-' + s.k }), h('span', null, s.label), h('strong', null, s.v))))),

      h('section', { class: 'stats-sec' },
        h('h2', { class: 'section-h' }, 'Fällige Karten, nächste 14 Tage'),
        columns(forecast, { label: 'Prognose fälliger Karten' })),

      h('section', { class: 'stats-sec' },
        h('h2', { class: 'section-h' }, 'Abfragen pro Tag, letzte 30 Tage'),
        columns(history, { label: 'Abfragen der letzten 30 Tage', unit: 'Abfragen', highlightLast: true })),

      hardest.length ? h('section', { class: 'stats-sec' },
        h('h2', { class: 'section-h' }, 'Am häufigsten vergessen'),
        h('div', { class: 'wrong-list' }, hardest.map(c => h('button', { type: 'button', class: 'wrong-row', onclick: () => app.go('edit', { id: c.id }) },
          c.photoIds?.length ? lazyPhoto(h('img', { class: 'cl-thumb', alt: '' }), c.photoIds[0]) : h('span', { class: 'cl-thumb none' }, icon('leaf', 18)),
          h('span', { class: 'cl-main' }, h('span', { class: 'cl-name' }, displayName(c)), h('span', { class: 'cl-sub' }, `${c.srs.lapses}× vergessen`)),
          icon('chevron', 18))))) : null].filter(Boolean));
  }

  root.append(
    topbar({ title: 'Statistik' }),
    h('div', { class: 'stats-tools' },
      h('select', { class: 'input compact', id: 'stats-deck', 'aria-label': 'Stapel', value: prefs.deckId, onchange: e => { prefs.deckId = e.target.value; render(); } },
        h('option', { value: '' }, 'Alle Stapel'), decks.map(d => h('option', { value: d.id }, d.name)))),
    content);
  render();
  return { el: root, tab: 'stats' };
}
