import { app } from '../app.js';
import { h, topbar, iconBtn, btn, promptDialog, emptyState, toast } from '../ui.js';
import { icon } from '../icons.js';
import { listDecks, allCards, logsSince, createDeck, getMeta, setMeta } from '../db.js';
import { cloudSupported, getCloud, providerLabel, ago } from '../cloud.js';
import { isOn } from '../features.js';
import { deckCounts, dayStart } from '../srs.js';
import { bloomsIn } from '../plants.js';
import { MONTHS } from '../util.js';
import { createSampleDeck } from '../sample.js';

export async function newDeckFlow() {
  const name = await promptDialog('Neuer Stapel', { label: 'Name des Stapels', placeholder: 'z. B. Bäume im Stadtpark', ok: 'Anlegen' });
  if (name == null) return null;
  if (!name.trim()) { toast('Bitte einen Namen eingeben.'); return null; }
  const deck = await createDeck(name);
  toast(`Stapel „${deck.name}“ angelegt`);
  return deck;
}

function countChips({ n, l, r }) {
  return h('div', { class: 'counts', 'aria-label': `${n} neu, ${l} in Lernphase, ${r} fällig` },
    h('span', { class: 'cnt cnt-new' + (n ? '' : ' zero') }, n),
    h('span', { class: 'cnt cnt-learn' + (l ? '' : ' zero') }, l),
    h('span', { class: 'cnt cnt-due' + (r ? '' : ' zero') }, r));
}

export default async function homeView() {
  const now = Date.now();
  const [decks, cards, logs] = await Promise.all([listDecks(), allCards(), logsSince(dayStart(now))]);
  const byDeck = new Map(decks.map(d => [d.id, []]));
  for (const c of cards) byDeck.get(c.deckId)?.push(c);

  let totalDue = 0;
  const rows = decks.map(d => {
    const list = byDeck.get(d.id) || [];
    const counts = deckCounts(list, d, logs, now);
    totalDue += counts.n + counts.l + counts.r;
    const photos = list.reduce((s, c) => s + (c.photoIds?.length || 0), 0);
    return h('button', { class: 'deck-row', type: 'button', onclick: () => app.go('deck', { id: d.id }) },
      h('span', { class: 'deck-glyph' }, icon(d.sample ? 'flower' : 'stack', 22)),
      h('span', { class: 'deck-main' },
        h('span', { class: 'deck-name' }, d.name),
        h('span', { class: 'deck-meta' }, `${list.length} Karten · ${photos} Fotos`)),
      countChips(counts));
  });

  const studiedToday = logs.filter(l => l.mode === 'srs').length;
  const month = new Date().getMonth() + 1;
  const blooming = cards.filter(c => bloomsIn(c, month));

  const addCard = async () => {
    if (!decks.length) {
      const d = await newDeckFlow();
      if (d) app.go('edit', { deckId: d.id });
      return;
    }
    const last = await getMeta('lastDeck');
    app.go('edit', { deckId: decks.some(d => d.id === last) ? last : decks[0].id });
  };

  // Cloud-Sicherung: Status oder Einladung
  let cloudEl = null;
  if (cloudSupported && isOn('cloudHint')) {
    const [c, dismissed] = await Promise.all([getCloud(), getMeta('cloudBannerDismissed')]);
    if (c.uri) {
      cloudEl = h('button', { type: 'button', class: 'cloud-status' + (c.lastError ? ' err' : ''), onclick: () => app.go('settings', { focus: 'cloud' }) },
        icon('cloud', 18),
        h('span', null, c.lastError ? `${providerLabel(c.provider)}: Sicherung fehlgeschlagen – antippen` : `In ${providerLabel(c.provider)} gesichert ${ago(c.lastSync)}`));
    } else if (!dismissed && cards.length) {
      const banner = h('div', { class: 'cloud-banner' },
        h('button', { type: 'button', class: 'cloud-banner-main', onclick: () => app.go('settings', { focus: 'cloud' }) },
          icon('cloudUp', 22),
          h('span', null, h('strong', null, 'Karten in Google Drive sichern'), h('span', { class: 'muted small' }, 'Automatisch, mit deinem eigenen Konto'))),
        h('button', { type: 'button', class: 'icon-btn small', 'aria-label': 'Hinweis ausblenden', onclick: async () => { await setMeta('cloudBannerDismissed', true); banner.remove(); } }, icon('close', 18)));
      cloudEl = banner;
    }
  }

  const el = h('div', { class: 'page home' },
    topbar({
      title: 'Florakarten',
      cls: 'brand',
      actions: [iconBtn('sliders', 'Einstellungen', () => app.go('settings'))],
    }),
    h('section', { class: 'today' },
      h('div', { class: 'today-main' },
        h('span', { class: 'today-num' }, totalDue),
        h('span', { class: 'today-label' }, totalDue === 1 ? 'Karte heute offen' : 'Karten heute offen')),
      h('div', { class: 'today-side' },
        h('span', null, `${studiedToday} heute gelernt`),
        h('span', { class: 'legend' },
          h('i', { class: 'dot-new' }), 'neu ', h('i', { class: 'dot-learn' }), 'lernen ', h('i', { class: 'dot-due' }), 'fällig'))),
    cloudEl,
    blooming.length && isOn('bloomBanner') && isOn('field:bloom') ? h('button', {
      class: 'bloom-banner', type: 'button',
      onclick: () => app.go('browse', { filter: 'bloom' }),
    }, icon('calendar', 20), h('span', null, h('strong', null, `Blüht im ${MONTHS[month - 1]}: `), `${blooming.length} deiner Pflanzen`), icon('chevron', 18)) : null,
    decks.length
      ? h('div', { class: 'deck-list' }, rows)
      : emptyState('layers', 'Noch keine Stapel', 'Lege einen Stapel an, z. B. „Wiesenblumen“ oder „Bäume“, und füge Karten mit Fotos hinzu.',
        btn('Stapel anlegen', async () => { if (await newDeckFlow()) app.reload(); }, { kind: 'primary', iconName: 'plus' }),
        btn('Beispielstapel laden', async () => { await createSampleDeck(); app.reload(); }, { iconName: 'flower' })),
    decks.length ? h('div', { class: 'home-actions' },
      btn('Neuer Stapel', async () => { if (await newDeckFlow()) app.reload(); }, { iconName: 'layers' }),
      isOn('quick') ? btn('Fotos schnell erfassen', () => app.go('quick', {}), { iconName: 'camera' }) : null) : null,
    h('button', { class: 'fab', type: 'button', 'aria-label': 'Karte hinzufügen', onclick: addCard }, icon('plus', 26)));

  return { el };
}

export { countChips };
