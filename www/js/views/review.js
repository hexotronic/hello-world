// Lernsitzung: Karte zeigen, aufdecken, bewerten (wie bei klassischen Karteikarten-Apps).
import { app } from '../app.js';
import { h, topbar, iconBtn, btn, menu, confirmDialog, toast } from '../ui.js';
import { icon } from '../icons.js';
import { getDeck, getCard, cardsInDeck, allCards, saveCard, addLog, logsSince, deleteLastLog, deleteCards, saveDeck } from '../db.js';
import { answer as srsAnswer, buildQueues, deckConfig, dayStart, dayStartPlus, previewIntervals } from '../srs.js';
import { cardFace } from '../cardview.js';
import { acceptedNames } from '../plants.js';
import { checkAnswer, shuffle } from '../util.js';
import { cloudSoon } from '../cloud.js';

const RATINGS = [
  { r: 1, label: 'Nochmal', cls: 'again' },
  { r: 2, label: 'Schwer', cls: 'hard' },
  { r: 3, label: 'Gut', cls: 'good' },
  { r: 4, label: 'Einfach', cls: 'easy' },
];

export default async function reviewView({ deckId, mode = 'srs', cardIds, title }) {
  const deck = deckId ? await getDeck(deckId) : null;
  const cfg = deckConfig(deck);
  const learnAhead = (app.settings?.learnAheadMin ?? 20) * 60000;
  let cards;
  if (cardIds?.length) cards = (await Promise.all(cardIds.map(getCard))).filter(Boolean);
  else cards = deckId ? await cardsInDeck(deckId) : await allCards();

  let learn = [], reviewQ = [], newQ = [], cramQ = [];
  let cramTotal = 0;
  if (mode === 'srs') {
    const logs = await logsSince(dayStart());
    ({ learn, review: reviewQ, fresh: newQ } = buildQueues(cards, deck, logs));
  } else {
    cramQ = shuffle(cards.filter(c => !c.suspended));
    cramTotal = cramQ.length;
  }
  const newEvery = reviewQ.length && newQ.length ? Math.max(1, Math.floor(reviewQ.length / newQ.length)) : 1;
  let sinceNew = 0;

  let current = null, group = null;
  let revealed = false, hintLevel = 0, typed = null, photoIndex = 0, shownAt = 0;
  let done = 0, cramKnown = 0;
  const startedAt = Date.now();
  const undoStack = [];

  const root = h('div', { class: 'page review' });
  const countsEl = h('div', { class: 'rv-counts' });
  const progress = h('div', { class: 'rv-progress' }, h('span'));
  const stageEl = h('div', { class: 'rv-stage' });
  const bottom = h('div', { class: 'rv-bottom' });
  const undoBtn = iconBtn('undo', 'Rückgängig', () => undo());
  const sessionTitle = title || (mode === 'cram' ? 'Pauken' : '') || deck?.name || 'Lernen';

  root.append(
    topbar({
      title: sessionTitle,
      sub: mode === 'cram' ? 'ohne Einfluss auf die Planung' : null,
      back: () => app.back(),
      actions: [undoBtn, iconBtn('more', 'Kartenaktionen', () => cardMenu())],
    }),
    h('div', { class: 'rv-meta' }, countsEl, mode === 'cram' ? progress : null),
    stageEl,
    bottom);

  function take(queue, g) {
    const i = queue === learn ? queue.indexOf(queue.reduce((a, b) => (b.srs.due < a.srs.due ? b : a))) : 0;
    const [c] = queue.splice(i, 1);
    return { card: c, group: g };
  }

  function pickNext() {
    const now = Date.now();
    if (mode === 'cram') {
      const c = cramQ.shift();
      return c ? { card: c, group: 'cram' } : null;
    }
    const learnDueNow = learn.filter(c => c.srs.due <= now);
    if (learnDueNow.length) return take(learn, 'learn');
    if (newQ.length && (!reviewQ.length || sinceNew >= newEvery)) { sinceNew = 0; return take(newQ, 'new'); }
    if (reviewQ.length) { sinceNew++; return take(reviewQ, 'review'); }
    if (learn.length && Math.min(...learn.map(c => c.srs.due)) <= now + learnAhead) return take(learn, 'learn');
    return null;
  }

  function renderCounts() {
    if (mode === 'cram') {
      countsEl.replaceChildren(h('span', { class: 'muted' }, `${cramKnown} gewusst · ${cramQ.length + (current ? 1 : 0)} übrig`));
      progress.firstChild.style.width = cramTotal ? `${Math.round((cramKnown / cramTotal) * 100)}%` : '0%';
      return;
    }
    const n = newQ.length + (group === 'new' ? 1 : 0);
    const l = learn.length + (group === 'learn' ? 1 : 0);
    const r = reviewQ.length + (group === 'review' ? 1 : 0);
    countsEl.replaceChildren(
      h('span', { class: 'cnt cnt-new' + (group === 'new' ? ' cur' : '') }, n),
      h('span', { class: 'cnt cnt-learn' + (group === 'learn' ? ' cur' : '') }, l),
      h('span', { class: 'cnt cnt-due' + (group === 'review' ? ' cur' : '') }, r));
  }

  function answersFor(card) {
    if (card.type === 'plant') return acceptedNames(card);
    return [(card.back || '').split('\n')[0]];
  }

  function show() {
    undoBtn.disabled = !undoStack.length;
    renderCounts();
    if (!current) return finish();
    const c = current;
    stageEl.replaceChildren(cardFace(c, {
      revealed, photoIndex, hintLevel, typed,
      singlePhoto: cfg.singlePhoto,
      speechLang: app.settings?.speechLang,
      onPhotoChange: i => { photoIndex = i; },
    }));
    if (c.marked) stageEl.firstChild.classList.add('is-marked');
    renderBottom();
    if (revealed) stageEl.querySelector('#answer')?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    else stageEl.scrollTop = 0;
  }

  function renderBottom() {
    const c = current;
    if (!revealed) {
      const canHint = cfg.showHint && c.type === 'plant' && (c.plant?.deName || c.plant?.latinName || c.plant?.family);
      const useType = cfg.typeAnswer && answersFor(c).some(Boolean);
      const input = useType ? h('input', {
        class: 'input type-input', id: 'type-answer', placeholder: c.type === 'plant' ? 'Name eintippen …' : 'Antwort eintippen …',
        autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false', enterkeyhint: 'done',
        onkeydown: e => { if (e.key === 'Enter') { e.preventDefault(); reveal(); } },
      }) : null;
      bottom.replaceChildren(...[
        input,
        h('div', { class: 'rv-row' },
          canHint ? btn(hintLevel ? `Tipp ${Math.min(hintLevel + 1, 3)}` : 'Tipp', () => { hintLevel = Math.min(3, hintLevel + 1); const v = input?.value; show(); if (v != null) { const i = bottom.querySelector('#type-answer'); if (i) i.value = v; } }, { iconName: 'bulb', cls: 'hint-btn', attrs: { disabled: hintLevel >= 3 } }) : null,
          btn('Antwort zeigen', () => reveal(), { kind: 'primary', cls: 'reveal-btn' }))].filter(Boolean));
      if (input) setTimeout(() => input.focus(), 50);
      return;
    }
    let suggested = null;
    if (typed) suggested = typed.result === 'correct' ? 3 : typed.result === 'close' ? 2 : 1;
    if (mode === 'cram') {
      bottom.replaceChildren(h('div', { class: 'rate-row three' },
        rateBtn({ r: 1, label: 'Nochmal', cls: 'again' }, 'kommt gleich wieder', suggested),
        rateBtn({ r: 2, label: 'Schwer', cls: 'hard' }, 'etwas später', suggested),
        rateBtn({ r: 3, label: 'Gewusst', cls: 'good' }, 'fertig', suggested === 4 ? 3 : suggested)));
      return;
    }
    const iv = previewIntervals(c.srs, cfg);
    bottom.replaceChildren(h('div', { class: 'rate-row' }, RATINGS.map((x, i) => rateBtn(x, iv[i], suggested))));
  }

  function rateBtn(x, sub, suggested) {
    return h('button', {
      class: `rate rate-${x.cls}` + (suggested === x.r ? ' suggested' : ''), type: 'button',
      onclick: () => rate(x.r),
    }, h('span', { class: 'rate-iv' }, sub), h('span', { class: 'rate-label' }, x.label));
  }

  function reveal() {
    if (!current || revealed) return;
    const input = bottom.querySelector('#type-answer');
    if (input) {
      const res = checkAnswer(input.value, answersFor(current));
      typed = { value: input.value.trim(), ...res };
    }
    revealed = true;
    show();
  }

  function next() {
    const n = pickNext();
    current = n?.card || null;
    group = n?.group || null;
    revealed = false; hintLevel = 0; typed = null;
    const count = current?.photoIds?.length || 0;
    photoIndex = count > 1 && cfg.randomPhoto ? Math.floor(Math.random() * count) : 0;
    shownAt = Date.now();
    show();
  }

  async function rate(r) {
    if (!current || !revealed) return;
    const card = current;
    const prev = JSON.parse(JSON.stringify(card));
    const now = Date.now();
    if (mode === 'srs') {
      const prevState = card.srs.state;
      card.srs = srsAnswer(card.srs, r, cfg, now);
      if (r === 1 && prevState === 'review' && card.srs.lapses >= cfg.leechThreshold && !(card.tags || []).includes('schwierig')) {
        card.tags = [...(card.tags || []), 'schwierig'];
        card.marked = true;
        toast('Diese Karte vergisst du oft. Sie ist jetzt markiert und mit „schwierig“ getaggt.', { duration: 5000 });
      }
      await saveCard(card, { touch: false });
      await addLog({ cardId: card.id, deckId: card.deckId, rating: r, prevState, ivl: card.srs.ivl, prevIvl: prev.srs.ivl, ease: card.srs.ease, ms: now - shownAt, mode: 'srs' });
      if ((card.srs.state === 'learning' || card.srs.state === 'relearning') && card.srs.due < dayStartPlus(1, now)) learn.push(card);
    } else {
      if (r === 1) cramQ.splice(Math.min(3, cramQ.length), 0, card);
      else if (r === 2) cramQ.splice(Math.min(8, cramQ.length), 0, card);
      else cramKnown++;
      await addLog({ cardId: card.id, deckId: card.deckId, rating: r, prevState: card.srs.state, ms: now - shownAt, mode: 'cram' });
    }
    undoStack.push({ prev, group, rating: r });
    if (undoStack.length > 30) undoStack.shift();
    done++;
    next();
  }

  function putBack(card, g) {
    if (!card) return;
    if (g === 'new') newQ.unshift(card);
    else if (g === 'review') reviewQ.unshift(card);
    else if (g === 'learn') learn.push(card);
    else if (g === 'cram') cramQ.unshift(card);
  }

  async function undo() {
    const u = undoStack.pop();
    if (!u) return;
    putBack(current, group);
    learn = learn.filter(c => c.id !== u.prev.id);
    const idx = cramQ.findIndex(c => c.id === u.prev.id);
    if (idx >= 0) cramQ.splice(idx, 1);
    newQ = newQ.filter(c => c.id !== u.prev.id);
    reviewQ = reviewQ.filter(c => c.id !== u.prev.id);
    if (mode === 'srs') await saveCard(u.prev, { touch: false });
    else if (u.rating >= 3) cramKnown--;
    await deleteLastLog(u.prev.id);
    current = u.prev; group = u.group;
    revealed = false; hintLevel = 0; typed = null;
    done = Math.max(0, done - 1);
    show();
    toast('Letzte Bewertung rückgängig gemacht');
  }

  async function cardMenu() {
    if (!current) return;
    const c = current;
    const choice = await menu('Karte', [
      { label: 'Bearbeiten', icon: 'edit', value: 'edit', hint: 'E' },
      { label: c.marked ? 'Markierung entfernen' : 'Markieren', icon: 'star', value: 'mark', hint: 'M' },
      mode === 'srs' ? { label: 'Für heute zurückstellen', icon: 'clock', value: 'bury' } : null,
      { label: cfg.singlePhoto ? 'Wieder alle Fotos zeigen' : 'Nur ein Foto zeigen', icon: 'image', value: 'single', hint: 'P' },
      { label: 'Aussetzen (nicht mehr abfragen)', icon: 'ban', value: 'suspend' },
      { label: 'Karte löschen', icon: 'trash', value: 'delete', danger: true },
    ]);
    if (choice === 'edit') app.go('edit', { id: c.id });
    if (choice === 'mark') toggleMark();
    if (choice === 'single') toggleSingle();
    if (choice === 'bury') {
      c.buriedUntil = dayStartPlus(1);
      await saveCard(c, { touch: false });
      toast('Bis morgen zurückgestellt');
      next();
    }
    if (choice === 'suspend') {
      c.suspended = true;
      await saveCard(c);
      toast('Karte ausgesetzt', { action: 'Rückgängig', onAction: async () => { c.suspended = false; await saveCard(c); putBack(current, group); current = c; group = 'review'; revealed = false; show(); } });
      next();
    }
    if (choice === 'delete') {
      if (await confirmDialog('Karte löschen?', 'Die Karte und ihre Fotos werden endgültig gelöscht.')) {
        await deleteCards([c.id]);
        toast('Karte gelöscht');
        next();
      }
    }
  }

  /** Umschalten: nur ein Foto oder alle Fotos – wird im Stapel gespeichert. */
  async function toggleSingle() {
    cfg.singlePhoto = !cfg.singlePhoto;
    if (deck) {
      deck.config = { ...(deck.config || {}), singlePhoto: cfg.singlePhoto };
      await saveDeck(deck);
    }
    toast(cfg.singlePhoto ? 'Ab jetzt nur ein Foto pro Karte' : 'Alle Fotos werden gezeigt');
    show();
  }

  async function toggleMark() {
    if (!current) return;
    current.marked = !current.marked;
    await saveCard(current);
    toast(current.marked ? 'Markiert' : 'Markierung entfernt');
    stageEl.firstChild?.classList.toggle('is-marked', current.marked);
  }

  function finish() {
    cloudSoon();
    const mins = Math.max(1, Math.round((Date.now() - startedAt) / 60000));
    const later = learn.length;
    stageEl.replaceChildren(h('div', { class: 'finished' },
      h('div', { class: 'finished-icon' }, icon('leaf', 40)),
      h('h2', null, done ? 'Geschafft!' : (mode === 'cram' ? 'Keine Karten zum Pauken' : 'Für heute ist nichts mehr offen')),
      done ? h('p', null, `${done} ${done === 1 ? 'Karte' : 'Karten'} in etwa ${mins} Min.`) : null,
      later ? h('p', { class: 'muted' }, `${later} ${later === 1 ? 'Karte ist' : 'Karten sind'} noch in der Lernphase und ${later === 1 ? 'kommt' : 'kommen'} später heute wieder.`) : null,
      h('div', { class: 'finished-actions' },
        deckId ? btn('Zurück zum Stapel', () => app.back(), { kind: 'primary' }) : btn('Zur Übersicht', () => app.go('home', {}, { replace: true }), { kind: 'primary' }),
        deckId && mode === 'srs' ? btn('Pauken', () => app.go('review', { deckId, mode: 'cram' }, { replace: true }), { iconName: 'bolt' }) : null,
        btn('Zufallsfotos', () => app.go('random', deckId ? { deckId } : {}, { replace: true }), { iconName: 'shuffle' }))));
    bottom.replaceChildren();
  }

  const onKey = e => {
    if (e.target.closest?.('input, textarea, select') && e.key !== 'Enter') return;
    if (document.querySelector('.backdrop, .zoom')) return;
    if (!current) return;
    if (e.key === ' ' || e.key === 'Enter') {
      if (e.target.matches?.('input, button, select, summary')) return;
      e.preventDefault();
      if (!revealed) reveal(); else rate(mode === 'cram' ? 3 : 3);
    } else if (revealed && ['1', '2', '3', '4'].includes(e.key)) {
      const r = +e.key;
      if (mode === 'cram' && r === 4) return;
      rate(r);
    } else if (e.key === 'h' && !revealed) bottom.querySelector('.hint-btn')?.click();
    else if (e.key === 'e') app.go('edit', { id: current.id });
    else if (e.key === 'm') toggleMark();
    else if (e.key === 'p') toggleSingle();
    else if (e.key === 'z' || e.key === 'u') undo();
  };
  document.addEventListener('keydown', onKey);

  next();

  return {
    el: root,
    fullscreen: true,
    destroy: () => document.removeEventListener('keydown', onKey),
  };
}
