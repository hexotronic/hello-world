// Auswahl einer vorhandenen Karte (z. B. um Fotos einer bestimmten Pflanze hinzuzufügen).
import { h, dialog } from './ui.js';
import { icon } from './icons.js';
import { allCards, listDecks, getMeta, setMeta, displayName } from './db.js';
import { lazyPhoto } from './images.js';
import { norm, debounce } from './util.js';
import { mainName, otherName } from './plants.js';

const RECENT_KEY = 'recentTargets';

/** Merkt sich eine Karte als zuletzt gewählt (erscheint oben in der Auswahl). */
export async function rememberTarget(id) {
  const recent = (await getMeta(RECENT_KEY, [])).filter(x => x !== id);
  await setMeta(RECENT_KEY, [id, ...recent].slice(0, 6));
}

function cardRow(c, deckName, onPick) {
  const p = c.plant || {};
  const sub = [c.title ? mainName(p) : otherName(p), deckName].filter(Boolean).join(' · ');
  return h('button', { type: 'button', class: 'cl-row', onclick: () => onPick(c) },
    c.photoIds?.length
      ? lazyPhoto(h('img', { class: 'cl-thumb', alt: '' }), c.photoIds[0], 'thumb')
      : h('span', { class: 'cl-thumb none' }, icon(c.type === 'plant' ? 'leaf' : 'stack', 20)),
    h('span', { class: 'cl-main' },
      h('span', { class: 'cl-name' }, displayName(c)),
      sub ? h('span', { class: 'cl-sub' }, sub) : null),
    h('span', { class: 'cl-due' }, `${c.photoIds?.length || 0} Fotos`));
}

/** Öffnet die Auswahl. Ergebnis: die gewählte Karte oder undefined. */
export async function pickCard({ title = 'Pflanze auswählen' } = {}) {
  const [cards, decks, recentIds] = await Promise.all([allCards(), listDecks(), getMeta(RECENT_KEY, [])]);
  const deckName = new Map(decks.map(d => [d.id, d.name]));
  const byId = new Map(cards.map(c => [c.id, c]));
  const recent = recentIds.map(id => byId.get(id)).filter(Boolean);
  const sorted = cards.slice().sort((a, b) => displayName(a).localeCompare(displayName(b), 'de'));
  const index = new Map(cards.map(c => [c.id, norm([displayName(c), c.plant?.deName, c.plant?.latinName, c.title, ...(c.tags || [])].join(' '))]));

  return dialog({
    title,
    sheet: true,
    cls: 'picker-sheet',
    body: close => {
      const list = h('div', { class: 'picker-list' });
      const input = h('input', {
        class: 'input search-input', type: 'search', id: 'pick-q', placeholder: 'Name suchen …', autocomplete: 'off', enterkeyhint: 'search',
      });
      const pick = c => close(c);
      const render = () => {
        const q = norm(input.value);
        const found = q ? sorted.filter(c => q.split(' ').every(w => index.get(c.id).includes(w))) : sorted;
        const rows = [];
        if (!q && recent.length) {
          rows.push(h('div', { class: 'picker-h' }, 'Zuletzt gewählt'));
          rows.push(...recent.map(c => cardRow(c, deckName.get(c.deckId), pick)));
          rows.push(h('div', { class: 'picker-h' }, 'Alle Karten'));
        }
        rows.push(...found.slice(0, 150).map(c => cardRow(c, deckName.get(c.deckId), pick)));
        if (!found.length) rows.push(h('p', { class: 'muted picker-empty' }, 'Keine Karte gefunden.'));
        list.replaceChildren(...rows);
      };
      input.addEventListener('input', debounce(render, 100));
      render();
      setTimeout(() => input.focus(), 80);
      return h('div', { class: 'card-picker' }, h('div', { class: 'search-wrap' }, icon('search', 20), input), list);
    },
  });
}
