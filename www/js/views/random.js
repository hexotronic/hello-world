// Zufallsfoto-Modus: zufällige gespeicherte Fotos erkennen, als Runde, Diashow oder Galerie.
import { app } from '../app.js';
import { h, topbar, iconBtn, btn, segmented, field, toast, emptyState } from '../ui.js';
import { icon } from '../icons.js';
import { allCards, getCard, listDecks, getPhoto, addLog, saveCards, displayName } from '../db.js';
import { photoURL, lazyPhoto } from '../images.js';
import { openZoom, herbariumLabel } from '../cardview.js';
import { shuffle, MONTHS, speak } from '../util.js';
import { bloomsIn, hintText } from '../plants.js';

const prefs = { deckId: '', filter: 'all', tag: '', flow: 'manual', per: 'photo', count: 0, delay: 5, tab: 'play' };

export default async function randomView(params = {}) {
  const [cards0, decks] = await Promise.all([
    params.cardIds?.length ? Promise.all(params.cardIds.map(getCard)).then(a => a.filter(Boolean)) : allCards(),
    listDecks(),
  ]);
  const st = { ...prefs };
  if (params.deckId !== undefined) st.deckId = params.deckId;
  const fixedSet = !!params.cardIds?.length;
  if (fixedSet) { st.deckId = ''; st.filter = 'all'; st.tag = ''; }
  const month = new Date().getMonth() + 1;
  const withPhotos = cards0.filter(c => c.photoIds?.length);
  const tags = [...new Set(withPhotos.flatMap(c => c.tags || []))].sort();

  const root = h('div', { class: 'page random' });
  const body = h('div', { class: 'random-body' });
  const save = () => { if (!fixedSet) Object.assign(prefs, st); };

  function scope() {
    return withPhotos.filter(c => {
      if (st.deckId && c.deckId !== st.deckId) return false;
      if (st.tag && !(c.tags || []).includes(st.tag)) return false;
      if (st.filter === 'marked' && !c.marked) return false;
      if (st.filter === 'bloom' && !bloomsIn(c, month)) return false;
      if (st.filter === 'hard' && (c.srs.lapses || 0) < 1 && !c.marked) return false;
      return true;
    });
  }

  function buildItems(cards) {
    let items = [];
    if (st.per === 'photo') for (const c of cards) for (const pid of c.photoIds) items.push({ card: c, pid });
    else items = cards.map(c => ({ card: c, pid: c.photoIds[Math.floor(Math.random() * c.photoIds.length)] }));
    items = shuffle(items);
    // gleiche Pflanze nicht direkt hintereinander
    for (let i = 1; i < items.length; i++) {
      if (items[i].card.id === items[i - 1].card.id) {
        const j = items.findIndex((x, k) => k > i && x.card.id !== items[i].card.id);
        if (j > 0) [items[i], items[j]] = [items[j], items[i]];
      }
    }
    return st.count ? items.slice(0, st.count) : items;
  }

  // ---------- Einstellungen ----------
  function renderSetup() {
    const sc = scope();
    const nPhotos = sc.reduce((s, c) => s + c.photoIds.length, 0);
    const start = btn(nPhotos ? `Los geht's · ${st.per === 'photo' ? nPhotos + ' Fotos' : sc.length + ' Pflanzen'}` : 'Keine Fotos in dieser Auswahl',
      () => startSession(buildItems(sc)), { kind: 'primary', iconName: 'play', cls: 'study-btn', attrs: { disabled: !nPhotos } });
    const rerender = () => { save(); renderSetup(); };
    const filterChips = h('div', { class: 'chip-row' }, [
      { v: 'all', l: 'Alle' }, { v: 'marked', l: 'Markierte' }, { v: 'bloom', l: `Blüht im ${MONTHS[month - 1]}` }, { v: 'hard', l: 'Schwierige' },
    ].map(f => h('button', { type: 'button', class: 'chip' + (st.filter === f.v ? ' on' : ''), onclick: () => { st.filter = f.v; rerender(); } }, f.l)));

    body.replaceChildren(
      h('p', { class: 'lead' }, 'Zufällige Fotos aus deiner Sammlung: Erkennst du die Pflanze? Das zählt nicht für die Lernplanung, du kannst also beliebig oft üben.'),
      fixedSet ? h('p', { class: 'muted' }, `Auswahl: ${withPhotos.length} Karten mit Fotos`) : h('div', { class: 'form' },
        field('Stapel', h('select', { class: 'input', id: 'rnd-deck', value: st.deckId, onchange: e => { st.deckId = e.target.value; rerender(); } },
          h('option', { value: '' }, 'Alle Stapel'), decks.map(d => h('option', { value: d.id }, d.name)))),
        h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Auswahl'), filterChips),
        tags.length ? field('Schlagwort', h('select', { class: 'input', id: 'rnd-tag', value: st.tag, onchange: e => { st.tag = e.target.value; rerender(); } },
          h('option', { value: '' }, 'Alle'), tags.map(t => h('option', { value: t }, '#' + t)))) : null),
      h('div', { class: 'form' },
        h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Fotos'), segmented([
          { value: 'photo', label: 'Jedes Foto einzeln' },
          { value: 'card', label: 'Ein Foto pro Pflanze' },
        ], st.per, v => { st.per = v; rerender(); })),
        h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Ablauf'), segmented([
          { value: 'manual', label: 'Selbst aufdecken', icon: 'eye' },
          { value: 'slideshow', label: 'Diashow', icon: 'play' },
        ], st.flow, v => { st.flow = v; rerender(); })),
        st.flow === 'slideshow' ? h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Aufdecken nach'), segmented(
          [3, 5, 8, 12].map(s => ({ value: s, label: `${s} s` })), st.delay, v => { st.delay = v; save(); })) : null,
        h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Anzahl'), segmented(
          [{ value: 0, label: 'Alle' }, { value: 10, label: '10' }, { value: 20, label: '20' }, { value: 50, label: '50' }], st.count, v => { st.count = v; rerender(); }))),
      start);
  }

  // ---------- Runde ----------
  let session = null;
  function startSession(items) {
    if (!items.length) return;
    if (!session) app.pushLayer();
    session = { items, i: 0, known: [], missed: [], revealed: false, hint: 0, timer: null, paused: false, streak: 0, best: 0 };
    document.body.classList.add('fullscreen-view');
    root.classList.add('playing');
    renderItem();
  }

  function stopTimers() {
    if (session?.timer) { clearTimeout(session.timer); session.timer = null; }
  }

  async function renderItem() {
    stopTimers();
    const s = session;
    if (s.i >= s.items.length) return renderSummary();
    const { card, pid } = s.items[s.i];
    const [url, meta] = await Promise.all([photoURL(pid, 'full'), getPhoto(pid)]);
    if (session !== s) return;
    const p = card.plant || {};
    const name = card.type === 'plant' ? (p.deName || card.title || p.latinName) : displayName(card);
    const img = h('img', { class: 'rnd-img', src: url || '', alt: 'Zufälliges Foto', draggable: 'false' });
    const fig = h('figure', { class: 'rnd-photo', onclick: () => openZoom([pid]) }, img,
      meta?.caption ? h('span', { class: 'stage-caption' }, meta.caption) : null);

    const answerBox = h('div', { class: 'rnd-answer' + (s.revealed ? ' show' : '') });
    if (s.revealed) {
      answerBox.append(
        h('div', { class: 'rnd-name' }, name,
          iconBtn('volume', 'Vorlesen', () => speak(name, app.settings?.speechLang), { class: 'speak' })),
        card.type === 'plant' && p.latinName && p.latinName !== name ? h('div', { class: 'rnd-latin' }, p.latinName) : null,
        h('div', { class: 'rnd-fam muted' }, [p.family, card.title && card.title !== name ? `Kartenname: ${card.title}` : null].filter(Boolean).join(' · ')),
        h('details', { class: 'rnd-more' }, h('summary', null, 'Steckbrief'), card.type === 'plant' ? herbariumLabel(card, { speechLang: app.settings?.speechLang }) : h('p', { class: 'pre' }, card.back || '')));
    } else if (s.hint) {
      answerBox.append(h('div', { class: 'hints' }, hintText(card, s.hint).map(t => h('div', { class: 'hint' }, icon('bulb', 16), h('span', null, t)))));
      answerBox.classList.add('show');
    }

    const slideshow = st.flow === 'slideshow';
    let controls;
    if (!s.revealed) {
      controls = h('div', { class: 'rv-row' },
        card.type === 'plant' ? btn('Tipp', () => { s.hint = Math.min(3, s.hint + 1); renderItem(); }, { iconName: 'bulb', attrs: { disabled: s.hint >= 3 } }) : null,
        btn('Aufdecken', reveal, { kind: 'primary', cls: 'reveal-btn', iconName: 'eye' }));
    } else if (slideshow) {
      controls = h('div', { class: 'rv-row' },
        btn('Nicht gewusst', () => judge(false), { kind: 'again' }),
        btn('Weiter', () => judge(true), { kind: 'primary', iconName: 'chevron' }));
    } else {
      controls = h('div', { class: 'rate-row two' },
        h('button', { type: 'button', class: 'rate rate-again', onclick: () => judge(false) }, icon('close', 22), h('span', { class: 'rate-label' }, 'Nicht gewusst')),
        h('button', { type: 'button', class: 'rate rate-good', onclick: () => judge(true) }, icon('check', 22), h('span', { class: 'rate-label' }, 'Gewusst')));
    }

    root.replaceChildren(
      topbar({
        title: `${s.i + 1} / ${s.items.length}`,
        sub: `${s.known.length} gewusst${s.streak > 1 ? ` · Serie ${s.streak}` : ''}`,
        back: endSession,
        actions: [
          slideshow ? iconBtn(s.paused ? 'play' : 'pause', s.paused ? 'Fortsetzen' : 'Pause', () => { s.paused = !s.paused; renderItem(); }) : null,
          iconBtn('edit', 'Karte bearbeiten', () => { stopTimers(); app.go('edit', { id: card.id }); }),
        ],
        cls: 'rnd-top',
      }),
      h('div', { class: 'rv-progress' }, h('span', { style: { width: `${Math.round((s.i / s.items.length) * 100)}%` } })),
      h('div', { class: 'rnd-stage' }, fig, answerBox),
      h('div', { class: 'rv-bottom' }, controls));

    if (slideshow && !s.paused) {
      s.timer = setTimeout(() => { if (!s.revealed) reveal(); else judge(true); }, (s.revealed ? Math.max(3, st.delay) : st.delay) * 1000);
    }
  }

  function reveal() {
    if (!session || session.revealed) return;
    session.revealed = true;
    renderItem();
  }

  function judge(knew) {
    const s = session;
    if (!s || !s.revealed) return;
    const item = s.items[s.i];
    (knew ? s.known : s.missed).push(item);
    s.streak = knew ? s.streak + 1 : 0;
    s.best = Math.max(s.best, s.streak);
    addLog({ cardId: item.card.id, deckId: item.card.deckId, rating: knew ? 3 : 1, mode: 'random', photoId: item.pid });
    s.i++;
    s.revealed = false;
    s.hint = 0;
    renderItem();
  }

  function renderSummary() {
    stopTimers();
    const s = session;
    const total = s.known.length + s.missed.length;
    const pct = total ? Math.round((s.known.length / total) * 100) : 0;
    const missedCards = [...new Map(s.missed.map(x => [x.card.id, x.card])).values()];
    root.replaceChildren(
      topbar({ title: 'Runde beendet', back: endSession }),
      h('div', { class: 'random-body' },
        h('section', { class: 'score' },
          h('div', { class: 'score-ring', style: { '--p': pct } }, h('span', null, `${pct} %`)),
          h('div', null,
            h('div', { class: 'score-main' }, `${s.known.length} von ${total} erkannt`),
            h('div', { class: 'muted' }, s.best > 1 ? `Längste Serie: ${s.best}` : ''))),
        s.missed.length ? h('section', null,
          h('h2', { class: 'section-h' }, 'Nicht erkannt'),
          h('div', { class: 'missed-grid' }, s.missed.map(x => h('button', { type: 'button', class: 'missed', onclick: () => app.go('edit', { id: x.card.id }) },
            lazyPhoto(h('img', { alt: '' }), x.pid, 'thumb'),
            h('span', null, displayName(x.card)))))) : h('p', { class: 'lead' }, 'Alles erkannt. Stark!'),
        h('div', { class: 'finished-actions' },
          s.missed.length ? btn('Fehler wiederholen', () => startSession(shuffle(s.missed)), { kind: 'primary', iconName: 'undo' }) : null,
          s.missed.length ? btn('Fehler markieren', async () => {
            missedCards.forEach(c => { c.marked = true; });
            await saveCards(missedCards);
            toast(`${missedCards.length} Karten markiert. Du findest sie unter „Markiert“.`);
          }, { iconName: 'star' }) : null,
          btn('Neue Runde', () => startSession(buildItems(scope())), { iconName: 'shuffle' }),
          btn('Fertig', endSession, { kind: 'ghost' }))));
  }

  function endSession() {
    app.popLayer();
  }
  function onPop() {
    if (!session) return false;
    stopTimers();
    session = null;
    document.body.classList.remove('fullscreen-view');
    root.classList.remove('playing');
    renderShell();
    return true;
  }

  // ---------- Galerie ----------
  let showNames = false;
  function renderGallery() {
    const sc = scope();
    const items = shuffle(sc.flatMap(c => c.photoIds.map(pid => ({ card: c, pid }))));
    const grid = h('div', { class: 'gallery' + (showNames ? ' names' : '') }, items.map(({ card, pid }) => {
      const tile = h('button', {
        type: 'button', class: 'g-tile', onclick: () => {
          if (!showNames && !tile.classList.contains('revealed')) { tile.classList.add('revealed'); return; }
          openZoom(card.photoIds, card.photoIds.indexOf(pid));
        },
      }, lazyPhoto(h('img', { alt: '' }), pid, 'thumb'), h('span', { class: 'g-name' }, displayName(card)));
      return tile;
    }));
    body.replaceChildren(
      h('div', { class: 'gallery-tools' },
        h('label', { class: 'inline-toggle' },
          h('input', { type: 'checkbox', checked: showNames, onchange: e => { showNames = e.target.checked; grid.classList.toggle('names', showNames); grid.querySelectorAll('.revealed').forEach(t => t.classList.remove('revealed')); } }),
          h('span', null, 'Namen anzeigen')),
        h('span', { class: 'muted small' }, `${items.length} Fotos, gemischt · Antippen deckt auf`)),
      items.length ? grid : emptyState('image', 'Keine Fotos', 'Füge Karten Fotos hinzu, dann erscheinen sie hier.'));
  }

  function renderShell() {
    const tabs = segmented([
      { value: 'play', label: 'Zufallsrunde', icon: 'shuffle' },
      { value: 'gallery', label: 'Galerie', icon: 'grid' },
    ], st.tab, v => { st.tab = v; save(); (v === 'play' ? renderSetup : renderGallery)(); }, { id: 'rnd-tabs' });
    root.replaceChildren(
      topbar({ title: 'Zufallsfotos', back: params.deckId || fixedSet ? () => app.back() : null }),
      h('div', { class: 'rnd-tabs' }, tabs),
      body);
    if (!withPhotos.length) {
      body.replaceChildren(emptyState('image', 'Noch keine Fotos gespeichert', 'Füge deinen Karten Fotos hinzu oder erfasse viele Fotos auf einmal.',
        btn('Fotos schnell erfassen', () => app.go('quick', {}), { kind: 'primary', iconName: 'camera' })));
      return;
    }
    (st.tab === 'gallery' ? renderGallery : renderSetup)();
  }

  const onKey = e => {
    if (!session || document.querySelector('.backdrop, .zoom')) return;
    if ((e.key === ' ' || e.key === 'Enter') && !e.target.matches?.('button, input, select, summary')) { e.preventDefault(); if (!session.revealed) reveal(); else judge(true); }
    else if (session.revealed && (e.key === '1' || e.key === 'ArrowLeft')) judge(false);
    else if (session.revealed && (e.key === '2' || e.key === 'ArrowRight')) judge(true);
    else if (e.key === 'h' && !session.revealed) { session.hint = Math.min(3, session.hint + 1); renderItem(); }
  };
  document.addEventListener('keydown', onKey);

  renderShell();
  return {
    el: root,
    tab: 'random',
    onPop,
    destroy: () => { stopTimers(); document.removeEventListener('keydown', onKey); document.body.classList.remove('fullscreen-view'); },
  };
}
