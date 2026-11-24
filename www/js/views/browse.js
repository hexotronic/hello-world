// Kartensuche mit Filtern und Sammelaktionen.
import { app } from '../app.js';
import { h, topbar, iconBtn, btn, menu, confirmDialog, promptDialog, toast, emptyState, dialog } from '../ui.js';
import { icon } from '../icons.js';
import { allCards, listDecks, saveCards, deleteCards, displayName, searchText } from '../db.js';
import { lazyPhoto } from '../images.js';
import { dayStartPlus, formatDays, maturity } from '../srs.js';
import { norm, debounce, MONTHS } from '../util.js';
import { bloomsIn, mainName, otherName, mainIsLatin } from '../plants.js';
import { isOn } from '../features.js';

const FILTERS = [
  { value: 'all', label: 'Alle' },
  { value: 'due', label: 'Fällig' },
  { value: 'new', label: 'Neu' },
  { value: 'marked', label: 'Markiert' },
  { value: 'photos', label: 'Mit Fotos' },
  { value: 'nophotos', label: 'Ohne Fotos' },
  { value: 'bloom', label: 'Blüht jetzt' },
  { value: 'hard', label: 'Oft vergessen' },
  { value: 'suspended', label: 'Ausgesetzt' },
  { value: 'unnamed', label: 'Ohne Namen' },
];

const SORTS = [
  { value: 'name', label: 'Name A–Z' },
  { value: 'created', label: 'Neueste zuerst' },
  { value: 'due', label: 'Fälligkeit' },
  { value: 'lapses', label: 'Am häufigsten vergessen' },
];

const sessionState = { q: '', filter: 'all', sort: 'name', deckId: '', tag: '', prop: '' };

export default async function browseView(params = {}) {
  const [cards, decks] = await Promise.all([allCards(), listDecks()]);
  const deckName = new Map(decks.map(d => [d.id, d.name]));
  const st = { ...sessionState };
  if (params.deckId !== undefined) st.deckId = params.deckId;
  if (params.filter) st.filter = params.filter;
  if (params.tag) { st.tag = params.tag; st.q = ''; }

  const index = new Map(cards.map(c => [c.id, norm(searchText(c))]));
  const allTags = [...new Set(cards.flatMap(c => c.tags || []))].sort((a, b) => a.localeCompare(b, 'de'));
  const month = new Date().getMonth() + 1;
  const now = Date.now();
  const endToday = dayStartPlus(1, now);
  let selecting = false;
  const selected = new Set();
  let limit = 150;

  const root = h('div', { class: 'page browse' });
  const list = h('div', { class: 'card-list' });
  const countEl = h('span', { class: 'muted small' });
  const selBar = h('div', { class: 'sel-bar', hidden: true });
  const search = h('input', {
    class: 'input search-input', type: 'search', id: 'browse-q', placeholder: 'Name, Kartenname, Merkmal, Schlagwort …',
    value: st.q, autocomplete: 'off', enterkeyhint: 'search',
    oninput: debounce(() => { st.q = search.value; limit = 150; persist(); update(); }, 120),
  });
  const deckSel = h('select', { class: 'input compact', id: 'browse-deck', 'aria-label': 'Stapel', value: st.deckId, onchange: e => { st.deckId = e.target.value; persist(); update(); } },
    h('option', { value: '' }, 'Alle Stapel'), decks.map(d => h('option', { value: d.id }, d.name)));
  const sortSel = h('select', { class: 'input compact', id: 'browse-sort', 'aria-label': 'Sortierung', value: st.sort, onchange: e => { st.sort = e.target.value; persist(); update(); } },
    SORTS.map(s => h('option', { value: s.value }, s.label)));
  const tagSel = h('select', { class: 'input compact', id: 'browse-tag', 'aria-label': 'Schlagwort', value: st.tag, onchange: e => { st.tag = e.target.value; persist(); update(); } },
    h('option', { value: '' }, 'Alle Schlagwörter'), allTags.map(t => h('option', { value: t }, '#' + t)));
  // Filter nach Lebensform oder Verwendung (Listen aus den Einstellungen plus Werte aus den Karten)
  const lifeVals = [...new Set([...(app.settings.lifeForms || []), ...cards.flatMap(c => c.plant?.lifeForm || [])])];
  const useVals = [...new Set([...(app.settings.usesList || []), ...cards.flatMap(c => c.plant?.usesList || [])])];
  const propSel = h('select', { class: 'input compact', id: 'browse-prop', 'aria-label': 'Lebensform oder Verwendung', value: st.prop, onchange: e => { st.prop = e.target.value; persist(); update(); } },
    h('option', { value: '' }, 'Lebensform / Verwendung'),
    h('optgroup', { label: 'Lebensform' }, lifeVals.map(v => h('option', { value: 'lf:' + v }, v))),
    h('optgroup', { label: 'Verwendung' }, useVals.map(v => h('option', { value: 'use:' + v }, v))));
  const chips = h('div', { class: 'chip-row scroll' });

  function persist() { Object.assign(sessionState, st); }

  function renderChips() {
    chips.replaceChildren(...FILTERS.filter(f => f.value !== 'bloom' || (isOn('field:bloom') && isOn('bloomBanner'))).map(f => h('button', {
      type: 'button', class: 'chip' + (st.filter === f.value ? ' on' : ''),
      onclick: () => { st.filter = f.value; limit = 150; persist(); renderChips(); update(); },
    }, f.value === 'bloom' ? `Blüht im ${MONTHS[month - 1]}` : f.label)));
  }

  function matches(c) {
    if (st.deckId && c.deckId !== st.deckId) return false;
    if (st.tag && !(c.tags || []).includes(st.tag)) return false;
    if (st.prop.startsWith('lf:') && !(c.plant?.lifeForm || []).includes(st.prop.slice(3))) return false;
    if (st.prop.startsWith('use:') && !(c.plant?.usesList || []).includes(st.prop.slice(4))) return false;
    switch (st.filter) {
      case 'due': if (c.suspended || c.srs.state === 'new' || c.srs.due >= endToday) return false; break;
      case 'new': if (c.srs.state !== 'new') return false; break;
      case 'marked': if (!c.marked) return false; break;
      case 'photos': if (!c.photoIds?.length) return false; break;
      case 'nophotos': if (c.photoIds?.length) return false; break;
      case 'bloom': if (!bloomsIn(c, month)) return false; break;
      case 'hard': if ((c.srs.lapses || 0) < 2) return false; break;
      case 'suspended': if (!c.suspended) return false; break;
      case 'unnamed': if (c.title || c.plant?.deName || c.plant?.latinName || (c.type === 'basic' && c.front)) return false; break;
    }
    const q = norm(st.q);
    if (!q) return true;
    const text = index.get(c.id);
    return q.split(' ').every(w => text.includes(w));
  }

  function sorted(arr) {
    const by = {
      name: (a, b) => displayName(a).localeCompare(displayName(b), 'de'),
      created: (a, b) => (b.created || 0) - (a.created || 0),
      due: (a, b) => (a.srs.state === 'new') - (b.srs.state === 'new') || (a.srs.due || 0) - (b.srs.due || 0),
      lapses: (a, b) => (b.srs.lapses || 0) - (a.srs.lapses || 0),
    }[st.sort];
    return arr.sort(by);
  }

  function dueLabel(c) {
    if (c.suspended) return 'ausgesetzt';
    const m = maturity(c);
    if (m === 'new') return 'neu';
    if (m === 'learning') return 'lernt';
    if (c.srs.due < endToday) return 'fällig';
    const days = Math.round((c.srs.due - now) / 864e5);
    return `in ${formatDays(Math.max(1, days))}`;
  }

  function row(c) {
    const name = displayName(c);
    const p = c.plant || {};
    // Unter dem Listennamen steht der andere Pflanzenname (bei eigenem Kartennamen beide)
    const subNames = c.type === 'plant' ? (c.title ? [mainName(p), otherName(p)] : [otherName(p)]).filter(n => n && n !== name) : [];
    const sub = c.type === 'plant' ? subNames.join(' · ') : (c.back || '').split('\n')[0];
    const subLatin = subNames.length === 1 && subNames[0] === p.latinName;
    const thumb = c.photoIds?.length
      ? lazyPhoto(h('img', { class: 'cl-thumb', alt: '' }), c.photoIds[0], 'thumb')
      : h('span', { class: 'cl-thumb none' }, icon(c.type === 'plant' ? 'leaf' : 'stack', 20));
    const isSel = selected.has(c.id);
    return h('button', {
      type: 'button', class: 'cl-row' + (isSel ? ' sel' : '') + (c.suspended ? ' susp' : ''),
      onclick: () => {
        if (selecting) { if (selected.has(c.id)) selected.delete(c.id); else selected.add(c.id); update(); }
        else app.go('edit', { id: c.id });
      },
    },
      selecting ? h('span', { class: 'cl-check' }, icon(isSel ? 'checkbox' : 'square', 22)) : null,
      thumb,
      h('span', { class: 'cl-main' },
        h('span', { class: 'cl-name' + (!c.title && c.type === 'plant' && mainIsLatin(p) ? ' latin' : '') }, c.marked ? h('span', { class: 'mark-star', 'aria-label': 'markiert' }, icon('star', 14)) : null, name),
        sub ? h('span', { class: 'cl-sub' + (subLatin ? ' latin' : '') }, sub) : null,
        h('span', { class: 'cl-meta' },
          !st.deckId ? h('span', null, deckName.get(c.deckId) || '') : null,
          (c.tags || []).slice(0, 3).map(t => h('span', { class: 'tagpill' }, '#' + t)),
          c.photoIds?.length > 1 ? h('span', null, `${c.photoIds.length} Fotos`) : null)),
      h('span', { class: 'cl-due due-' + dueLabel(c).split(' ')[0] }, dueLabel(c)));
  }

  let lastResult = [];
  function update() {
    lastResult = sorted(cards.filter(matches));
    countEl.textContent = `${lastResult.length} von ${cards.length} Karten`;
    if (!lastResult.length) {
      list.replaceChildren(cards.length
        ? emptyState('search', 'Keine Treffer', 'Versuche einen anderen Suchbegriff oder Filter.')
        : emptyState('stack', 'Noch keine Karten', 'Lege deine erste Karte an.', btn('Karte hinzufügen', () => app.go('edit', {}), { kind: 'primary', iconName: 'plus' })));
    } else {
      list.replaceChildren(...lastResult.slice(0, limit).map(row));
      if (lastResult.length > limit) list.append(btn(`Weitere ${Math.min(150, lastResult.length - limit)} anzeigen`, () => { limit += 150; update(); }, { cls: 'more-btn' }));
    }
    renderSelBar();
  }

  function renderSelBar() {
    selBar.hidden = !selecting;
    if (!selecting) return;
    selBar.replaceChildren(
      h('span', { class: 'sel-count' }, `${selected.size} ausgewählt`),
      btn('Alle', () => { lastResult.forEach(c => selected.add(c.id)); update(); }, { kind: 'ghost' }),
      btn('Aktion', bulkMenu, { kind: 'primary', attrs: { disabled: !selected.size } }),
      iconBtn('close', 'Auswahl beenden', () => { selecting = false; selected.clear(); update(); }));
  }

  async function bulkMenu() {
    const chosen = cards.filter(c => selected.has(c.id));
    const choice = await menu(`${chosen.length} Karten`, [
      { label: 'Lernen (Pauken)', icon: 'bolt', value: 'cram' },
      { label: 'Zufallsfotos daraus', icon: 'shuffle', value: 'random' },
      { label: 'In anderen Stapel verschieben', icon: 'move', value: 'move' },
      { label: 'Schlagwort hinzufügen', icon: 'tag', value: 'tag' },
      { label: 'Markieren / Markierung entfernen', icon: 'star', value: 'mark' },
      { label: 'Aussetzen / wieder abfragen', icon: 'ban', value: 'suspend' },
      { label: 'Löschen', icon: 'trash', value: 'delete', danger: true },
    ]);
    if (!choice) return;
    if (choice === 'cram') return app.go('review', { mode: 'cram', cardIds: chosen.map(c => c.id), title: 'Auswahl pauken' });
    if (choice === 'random') return app.go('random', { cardIds: chosen.map(c => c.id) });
    if (choice === 'move') {
      const target = await dialog({
        title: 'Verschieben nach …', sheet: true,
        body: close => h('div', { class: 'menu-list' }, decks.map(d => h('button', { type: 'button', class: 'menu-item', onclick: () => close(d.id) }, icon('stack'), h('span', null, d.name)))),
      });
      if (!target) return;
      chosen.forEach(c => { c.deckId = target; c.modified = Date.now(); });
      await saveCards(chosen);
      toast(`${chosen.length} Karten verschoben`);
    }
    if (choice === 'tag') {
      const t = await promptDialog('Schlagwort hinzufügen', { label: 'Schlagwort', placeholder: 'z. B. prüfung', ok: 'Hinzufügen' });
      const tag = t?.trim().toLowerCase().replace(/\s+/g, '-');
      if (!tag) return;
      chosen.forEach(c => { c.tags = [...new Set([...(c.tags || []), tag])]; c.modified = Date.now(); });
      await saveCards(chosen);
      toast(`#${tag} bei ${chosen.length} Karten ergänzt`);
    }
    if (choice === 'mark') {
      const val = !chosen.every(c => c.marked);
      chosen.forEach(c => { c.marked = val; });
      await saveCards(chosen);
      toast(val ? 'Markiert' : 'Markierung entfernt');
    }
    if (choice === 'suspend') {
      const val = !chosen.every(c => c.suspended);
      chosen.forEach(c => { c.suspended = val; });
      await saveCards(chosen);
      toast(val ? 'Ausgesetzt' : 'Werden wieder abgefragt');
    }
    if (choice === 'delete') {
      if (!(await confirmDialog(`${chosen.length} Karten löschen?`, 'Karten und Fotos werden endgültig gelöscht.'))) return;
      await deleteCards(chosen.map(c => c.id));
      toast(`${chosen.length} Karten gelöscht`);
    }
    selecting = false;
    selected.clear();
    app.reload();
  }

  renderChips();
  root.append(
    topbar({
      title: 'Karten suchen',
      back: params.deckId ? () => app.back() : null,
      actions: [
        iconBtn('checkbox', 'Mehrere auswählen', () => { selecting = !selecting; selected.clear(); update(); }),
        iconBtn('plus', 'Karte hinzufügen', () => app.go('edit', { deckId: st.deckId || undefined })),
      ],
    }),
    h('div', { class: 'browse-tools' },
      h('div', { class: 'search-wrap' }, icon('search', 20), search),
      h('div', { class: 'select-row' }, isOn('field:tags') ? tagSel : null, deckSel, isOn('field:lifeForm') || isOn('field:uses') ? propSel : null, sortSel),
      chips,
      countEl),
    selBar,
    list);
  update();

  return { el: root, tab: 'browse', afterMount: () => { if (!('ontouchstart' in window)) search.focus(); } };
}
