// Schnell-Erfassung: viele Fotos auf einmal auswählen und nacheinander benennen.
import { app } from '../app.js';
import { h, topbar, btn, field, pickFiles, toast, emptyState } from '../ui.js';
import { icon } from '../icons.js';
import { listDecks, allCards, emptyCard, saveCard, addPhoto, setMeta, getMeta, displayName } from '../db.js';
import { processImage, blobURL } from '../images.js';
import { FAMILIES, CAPTIONS } from '../plants.js';
import { norm, nameFromFilename } from '../util.js';
import { newDeckFlow } from './home.js';
import { cloudSoon } from '../cloud.js';

export default async function quickView(params = {}) {
  let decks = await listDecks();
  const everything = await allCards();
  const last = await getMeta('lastDeck');
  let deckId = params.deckId || (decks.some(d => d.id === last) ? last : decks[0]?.id);
  const root = h('div', { class: 'page quick' });

  let files = [];
  let i = 0;
  let created = 0, appended = 0, skipped = 0;
  let lastCard = null;
  let stickyTags = '';
  let stickyFamily = '';
  let current = null; // {file, data, url}

  const byName = new Map();
  for (const c of everything) {
    const n = norm(c.plant?.deName || c.title);
    if (n) byName.set(n, c);
  }

  function renderStart() {
    root.replaceChildren(
      topbar({ title: 'Fotos schnell erfassen', back: () => app.back() }),
      h('div', { class: 'quick-body' },
        h('p', { class: 'lead' }, 'Wähle viele Pflanzenfotos auf einmal aus und benenne sie nacheinander. Mehrere Fotos derselben Pflanze landen mit einem Tipp auf einer Karte.'),
        decks.length ? field('In Stapel', h('select', { class: 'input', id: 'q-deck', value: deckId, onchange: async e => {
          if (e.target.value === '__new') {
            const d = await newDeckFlow();
            if (d) { decks = await listDecks(); deckId = d.id; }
            renderStart();
            return;
          }
          deckId = e.target.value;
        } }, decks.map(d => h('option', { value: d.id }, d.name)), h('option', { value: '__new' }, '+ Neuer Stapel …'))) : null,
        decks.length
          ? h('div', { class: 'btn-col' },
            btn('Fotos aus Galerie wählen', () => choose(), { kind: 'primary', iconName: 'image' }),
            btn('Mit Kamera aufnehmen', () => choose('environment'), { iconName: 'camera' }))
          : emptyState('layers', 'Erst einen Stapel anlegen', null, btn('Stapel anlegen', async () => { const d = await newDeckFlow(); if (d) { decks = await listDecks(); deckId = d.id; renderStart(); } }, { kind: 'primary' }))));
  }

  async function choose(capture) {
    const picked = await pickFiles({ multiple: !capture, capture });
    if (!picked.length) return;
    files = picked;
    i = 0;
    await setMeta('lastDeck', deckId);
    loadCurrent();
  }

  async function loadCurrent() {
    if (i >= files.length) return renderDone();
    root.replaceChildren(topbar({ title: `Foto ${i + 1} von ${files.length}`, back: renderDone }), h('div', { class: 'boot' }, icon('image', 32), h('p', null, 'Foto wird vorbereitet …')));
    try {
      const data = await processImage(files[i]);
      current = { file: files[i], data, url: blobURL(data.blob) };
      renderItem();
    } catch {
      toast(`„${files[i].name}“ konnte nicht gelesen werden.`);
      skipped++;
      i++;
      loadCurrent();
    }
  }

  function renderItem() {
    const suggested = nameFromFilename(current.file.name);
    const de = h('input', { class: 'input', id: 'q-de', 'aria-label': 'Deutscher Name', value: suggested, placeholder: 'Deutscher Name', autocomplete: 'off', list: 'q-names', enterkeyhint: 'next' });
    const la = h('input', { class: 'input latin', id: 'q-la', 'aria-label': 'Botanischer Name', placeholder: 'Botanischer Name (optional)', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false' });
    const fam = h('input', { class: 'input', id: 'q-fam', 'aria-label': 'Familie', value: stickyFamily, list: 'q-fams', placeholder: 'Familie (optional)', autocomplete: 'off' });
    const cap = h('select', { class: 'input', id: 'q-cap', 'aria-label': 'Bildunterschrift' }, CAPTIONS.map(c => h('option', { value: c }, c || 'Bildunterschrift (optional)')));
    const tags = h('input', { class: 'input', id: 'q-tags', 'aria-label': 'Schlagwörter', value: stickyTags, placeholder: 'Schlagwörter, z. B. garten, exkursion-mai', autocomplete: 'off' });
    const match = h('div', { class: 'dup-hint', hidden: true });
    const names = h('datalist', { id: 'q-names' }, [...new Set(everything.map(c => c.plant?.deName).filter(Boolean))].sort().map(n => h('option', { value: n })));
    const fams = h('datalist', { id: 'q-fams' }, FAMILIES.map(f => h('option', { value: f })));

    const checkMatch = () => {
      const c = byName.get(norm(de.value));
      match.hidden = !c;
      if (c) {
        match.replaceChildren(icon('info', 18), h('span', null, `„${displayName(c)}“ gibt es schon (${c.photoIds?.length || 0} Fotos). `),
          h('button', { type: 'button', class: 'link', onclick: () => appendTo(c, cap.value) }, 'Foto dort hinzufügen'));
      }
    };
    de.addEventListener('input', checkMatch);
    de.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); la.focus(); } });
    la.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); saveNew(); } });

    async function saveNew(unnamed = false) {
      const name = de.value.trim();
      if (!name && !la.value.trim() && !unnamed) { toast('Bitte einen Namen eingeben oder „Ohne Namen speichern“ wählen.'); de.focus(); return; }
      stickyTags = tags.value;
      stickyFamily = fam.value.trim();
      const card = emptyCard(deckId, 'plant');
      Object.assign(card.plant, { deName: name, latinName: la.value.trim(), family: fam.value.trim() });
      card.tags = [...new Set(tags.value.split(/[,;]+/).map(t => t.trim().toLowerCase().replace(/\s+/g, '-')).filter(Boolean))];
      if (unnamed) { card.title = ''; card.tags.push('unbenannt'); }
      const p = await addPhoto({ ...current.data, caption: cap.value }, card.id);
      card.photoIds = [p.id];
      await saveCard(card);
      everything.push(card);
      if (name) byName.set(norm(name), card);
      lastCard = card;
      created++;
      next();
    }

    async function appendTo(card, caption) {
      const p = await addPhoto({ ...current.data, caption }, card.id);
      card.photoIds = [...(card.photoIds || []), p.id];
      await saveCard(card);
      lastCard = card;
      appended++;
      toast(`Foto zu „${displayName(card)}“ hinzugefügt`);
      next();
    }

    function next() {
      URL.revokeObjectURL(current.url);
      current = null;
      i++;
      loadCurrent();
    }

    root.replaceChildren(
      topbar({ title: `Foto ${i + 1} von ${files.length}`, sub: `${created} neu · ${appended} ergänzt`, back: renderDone }),
      h('div', { class: 'quick-item' },
        h('figure', { class: 'quick-photo' }, h('img', { src: current.url, alt: 'Ausgewähltes Foto' })),
        h('div', { class: 'form' },
          de, names, match, la, fam, fams,
          h('div', { class: 'grid2' }, cap, tags),
          h('div', { class: 'btn-col' },
            btn('Speichern & weiter', () => saveNew(), { kind: 'primary', iconName: 'check' }),
            lastCard ? btn(`Gleiche Pflanze wie eben („${displayName(lastCard)}“)`, () => appendTo(lastCard, cap.value), { iconName: 'plus' }) : null,
            h('div', { class: 'row-gap' },
              btn('Ohne Namen speichern', () => saveNew(true), { kind: 'ghost' }),
              btn('Überspringen', () => { skipped++; next(); }, { kind: 'ghost' }))))));
    checkMatch();
    setTimeout(() => de.focus(), 60);
  }

  function renderDone() {
    cloudSoon();
    if (current) { URL.revokeObjectURL(current.url); current = null; }
    root.replaceChildren(
      topbar({ title: 'Erfassung beendet', back: () => app.back() }),
      h('div', { class: 'quick-body' },
        h('div', { class: 'finished' },
          h('div', { class: 'finished-icon' }, icon('check', 36)),
          h('h2', null, `${created} neue Karten, ${appended} Fotos ergänzt`),
          skipped ? h('p', { class: 'muted' }, `${skipped} übersprungen`) : null,
          h('div', { class: 'finished-actions' },
            btn('Zum Stapel', () => app.go('deck', { id: deckId }, { replace: true }), { kind: 'primary' }),
            btn('Weitere Fotos', () => renderStart(), { iconName: 'image' }),
            btn('Unbenannte ansehen', () => app.go('browse', { tag: 'unbenannt' }), { kind: 'ghost' })))));
  }

  renderStart();
  return { el: root, fullscreen: true };
}
