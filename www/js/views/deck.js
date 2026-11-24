import { app } from '../app.js';
import { h, topbar, iconBtn, btn, dialog, field, toggle, segmented, menu, confirmDialog, promptDialog, toast } from '../ui.js';
import { icon } from '../icons.js';
import { getDeck, saveDeck, cardsInDeck, logsSince, deleteDeck, setMeta } from '../db.js';
import { deckCounts, deckConfig, dayStart, dayStartPlus, dayIndex, formatDelay, maturity } from '../srs.js';
import { exportBackup } from '../backup.js';

function parseSteps(s) {
  return String(s).split(/[\s,;]+/).map(Number).filter(n => Number.isFinite(n) && n > 0);
}

export async function deckOptionsDialog(deck) {
  const cfg = deckConfig(deck);
  const v = { ...cfg, name: deck.name, description: deck.description || '' };
  const num = (key, min, max) => h('input', {
    class: 'input', type: 'number', inputmode: 'numeric', min, max, value: String(v[key]), id: 'opt-' + key,
    oninput: e => { v[key] = Math.max(min, Math.min(max, parseInt(e.target.value, 10) || 0)); },
  });
  const body = h('div', { class: 'form' },
    field('Name', h('input', { class: 'input', value: v.name, id: 'opt-name', oninput: e => { v.name = e.target.value; } })),
    field('Beschreibung', h('textarea', { class: 'input', rows: 2, value: v.description, id: 'opt-desc', oninput: e => { v.description = e.target.value; } })),
    h('h3', { class: 'form-h' }, 'Tageslimits'),
    h('div', { class: 'grid2' },
      field('Neue Karten pro Tag', num('newPerDay', 0, 9999)),
      field('Wiederholungen pro Tag', num('reviewsPerDay', 0, 99999))),
    h('h3', { class: 'form-h' }, 'Lernschritte'),
    h('div', { class: 'grid2' },
      field('Lernschritte (Min.)', h('input', { class: 'input', value: v.learnSteps.join(' '), id: 'opt-steps', oninput: e => { v.learnSteps = parseSteps(e.target.value); } }), 'z. B. „1 10“'),
      field('Nach „Nochmal“ (Min.)', h('input', { class: 'input', value: v.relearnSteps.join(' '), id: 'opt-relearn', oninput: e => { v.relearnSteps = parseSteps(e.target.value); } }))),
    h('div', { class: 'grid2' },
      field('Erstes Intervall (Tage)', num('graduatingInterval', 1, 365)),
      field('Intervall bei „Einfach“ (Tage)', num('easyInterval', 1, 365))),
    field('Reihenfolge neuer Karten', segmented([
      { value: 'added', label: 'Wie hinzugefügt' },
      { value: 'random', label: 'Zufällig' },
    ], v.newOrder, x => { v.newOrder = x; }, { id: 'opt-order' })),
    h('h3', { class: 'form-h' }, 'Beim Lernen'),
    toggle('Antwort eintippen', v.typeAnswer, x => { v.typeAnswer = x; }, { id: 'opt-type', hint: 'Name eingeben statt nur aufdecken. Kleine Tippfehler werden erkannt.' }),
    toggle('Tipp-Knopf anzeigen', v.showHint, x => { v.showHint = x; }, { id: 'opt-hint', hint: 'Zeigt nacheinander Familie, Anfangsbuchstaben und Silbe.' }),
    toggle('Zufälliges Foto zeigen', v.randomPhoto, x => { v.randomPhoto = x; }, { id: 'opt-rphoto', hint: 'Bei mehreren Fotos pro Karte jedes Mal ein anderes zeigen.' }),
    toggle('Nur ein Foto zeigen', v.singlePhoto, x => { v.singlePhoto = x; }, { id: 'opt-single', hint: 'Bei mehreren Fotos nur eins zeigen, ohne zu den anderen wischen zu können. Nach dem Aufdecken siehst du alle.' }));

  const ok = await dialog({
    title: 'Stapel-Optionen',
    body,
    cls: 'wide',
    actions: [{ label: 'Abbrechen', value: false }, { label: 'Speichern', value: true, kind: 'primary' }],
  });
  if (!ok) return false;
  deck.name = v.name.trim() || deck.name;
  deck.description = v.description.trim();
  const { name, description, ...config } = v;
  if (!config.learnSteps.length) config.learnSteps = [1, 10];
  deck.config = config;
  await saveDeck(deck);
  toast('Optionen gespeichert');
  return true;
}

export default async function deckView({ id }) {
  const deck = await getDeck(id);
  if (!deck) {
    return { el: h('div', { class: 'page' }, topbar({ title: 'Stapel nicht gefunden', back: () => app.back() })) };
  }
  setMeta('lastDeck', id);
  const now = Date.now();
  const [cards, logs] = await Promise.all([cardsInDeck(id), logsSince(dayStart(now))]);
  const counts = deckCounts(cards, deck, logs, now);
  const total = counts.n + counts.l + counts.r;
  const photos = cards.reduce((s, c) => s + (c.photoIds?.length || 0), 0);
  const plants = cards.filter(c => c.type === 'plant').length;
  const mat = { new: 0, learning: 0, young: 0, mature: 0 };
  for (const c of cards) mat[maturity(c)]++;
  const suspended = cards.filter(c => c.suspended).length;
  const marked = cards.filter(c => c.marked).length;

  // Nächste Fälligkeit, wenn heute nichts mehr offen ist
  let nextInfo = '';
  if (!total && cards.length) {
    const learning = cards.filter(c => !c.suspended && (c.srs.state === 'learning' || c.srs.state === 'relearning')).map(c => c.srs.due);
    const tomorrow = cards.filter(c => !c.suspended && c.srs.state === 'review' && dayIndex(c.srs.due) === dayIndex(dayStartPlus(1, now))).length;
    if (learning.length) nextInfo = `Nächste Lernkarte in ${formatDelay(Math.min(...learning) - now)}.`;
    else if (tomorrow) nextInfo = `Morgen ${tomorrow === 1 ? 'ist 1 Karte' : `sind ${tomorrow} Karten`} fällig.`;
    else nextInfo = 'Heute ist alles erledigt.';
  }

  const more = async () => {
    const choice = await menu(deck.name, [
      { label: 'Optionen', icon: 'sliders', value: 'options' },
      { label: 'Heute mehr neue Karten', icon: 'plus', value: 'extra' },
      { label: 'Stapel sichern (Export)', icon: 'download', value: 'export' },
      { label: 'Stapel löschen', icon: 'trash', value: 'delete', danger: true },
    ]);
    if (choice === 'options') { if (await deckOptionsDialog(deck)) app.reload(); }
    if (choice === 'extra') {
      const n = await promptDialog('Mehr neue Karten heute', { label: 'Wie viele zusätzlich?', value: '10', type: 'number', ok: 'Hinzufügen' });
      const k = parseInt(n, 10);
      if (k > 0) {
        const today = dayIndex(Date.now());
        const prev = deck.extraNew?.day === today ? deck.extraNew.n : 0;
        deck.extraNew = { day: today, n: prev + k };
        await saveDeck(deck);
        toast(`Heute ${k} neue Karten mehr`);
        app.reload();
      }
    }
    if (choice === 'export') exportBackup({ deckId: deck.id });
    if (choice === 'delete') {
      const ok = await confirmDialog('Stapel löschen?', `„${deck.name}“ mit ${cards.length} Karten und ${photos} Fotos wird endgültig gelöscht.`);
      if (ok) { await deleteDeck(deck.id); toast('Stapel gelöscht'); app.go('home', {}, { replace: true }); }
    }
  };

  const tile = (iconName, label, sub, onclick, disabled) => h('button', { class: 'tile', type: 'button', onclick, disabled },
    icon(iconName, 24), h('span', { class: 'tile-label' }, label), sub ? h('span', { class: 'tile-sub' }, sub) : null);

  const el = h('div', { class: 'page deck' },
    topbar({
      title: deck.name,
      back: () => app.back(),
      actions: [
        iconBtn('sliders', 'Optionen', async () => { if (await deckOptionsDialog(deck)) app.reload(); }),
        iconBtn('more', 'Mehr', more),
      ],
    }),
    deck.description ? h('p', { class: 'deck-desc muted' }, deck.description) : null,
    h('section', { class: 'deck-counts' },
      h('div', { class: 'dc dc-new' }, h('span', { class: 'dc-num' }, counts.n), h('span', { class: 'dc-label' }, 'Neu')),
      h('div', { class: 'dc dc-learn' }, h('span', { class: 'dc-num' }, counts.l), h('span', { class: 'dc-label' }, 'Lernen')),
      h('div', { class: 'dc dc-due' }, h('span', { class: 'dc-num' }, counts.r), h('span', { class: 'dc-label' }, 'Fällig'))),
    total
      ? btn('Jetzt lernen', () => app.go('review', { deckId: id }), { kind: 'primary', iconName: 'play', cls: 'study-btn' })
      : h('div', { class: 'done-box' }, icon('check', 22), h('span', null, cards.length ? nextInfo : 'Dieser Stapel ist noch leer.')),
    h('div', { class: 'tiles' },
      tile('plus', 'Karte hinzufügen', null, () => app.go('edit', { deckId: id })),
      tile('camera', 'Fotos schnell erfassen', 'Viele Fotos auf einmal', () => app.go('quick', { deckId: id })),
      tile('shuffle', 'Zufallsfotos', `${photos} Fotos`, () => app.go('random', { deckId: id }), !photos),
      tile('target', 'Training', 'Quiz & Eintippen', () => app.go('quiz', { deckId: id }), plants < 2),
      tile('bolt', 'Pauken', 'Alle Karten, ohne Planung', () => app.go('review', { deckId: id, mode: 'cram' }), !cards.length),
      tile('search', 'Karten ansehen', `${cards.length} Karten`, () => app.go('browse', { deckId: id }))),
    h('section', { class: 'deck-facts' },
      h('h2', { class: 'section-h' }, 'Bestand'),
      h('div', { class: 'facts' },
        fact('Neu', mat.new), fact('In Lernphase', mat.learning), fact('Jung (< 21 T)', mat.young),
        fact('Ausgereift', mat.mature), fact('Markiert', marked), fact('Ausgesetzt', suspended))));

  return { el, tab: 'home' };
}

function fact(label, value) {
  return h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, value), h('span', { class: 'fact-l' }, label));
}
