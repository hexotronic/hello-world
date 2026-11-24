// Karte anlegen oder bearbeiten.
import { app } from '../app.js';
import { h, topbar, iconBtn, btn, field, segmented, toggle, pickFiles, toast, confirmDialog, menu, chipPicker, promptDialog } from '../ui.js';
import { icon } from '../icons.js';
import {
  getCard, getDeck, listDecks, emptyCard, saveCard, deleteCards, allCards, addPhoto, getPhoto, savePhoto,
  deletePhoto, setMeta, displayName,
} from '../db.js';
import { processImage, photoURL, blobURL, forgetPhoto } from '../images.js';
import { newSrs, formatDays } from '../srs.js';
import { FAMILIES, TOXICITY, CAPTIONS, getNameMode, familyKey } from '../plants.js';
import { saveSettings } from '../db.js';
import { openImageSearch } from '../imagesearch.js';
import { lookupPlant, applyFacts, FACT_LABELS } from '../plantfacts.js';
import { MONTHS_ABBR, MONTHS, norm, fmtDate, nameFromFilename } from '../util.js';
import { newDeckFlow } from './home.js';

function parseTags(s) {
  return [...new Set(String(s).split(/[,;]+/).map(t => t.trim().toLowerCase().replace(/\s+/g, '-')).filter(Boolean))];
}

export default async function editView({ id, deckId, type }) {
  const existing = id ? await getCard(id) : null;
  const decks = await listDecks();
  if (!decks.length) {
    const d = await newDeckFlow();
    if (!d) { app.back(); return { el: h('div') }; }
    decks.push(d);
  }
  const startDeck = existing?.deckId || (decks.some(d => d.id === deckId) ? deckId : decks[0].id);
  let card = existing ? JSON.parse(JSON.stringify(existing)) : emptyCard(startDeck, type || 'plant');
  card.plant = { ...emptyCard('').plant, ...(card.plant || {}) };
  card.plant.lifeForm = [...(card.plant.lifeForm || [])];
  card.plant.usesList = [...(card.plant.usesList || [])];
  const isNew = !existing;
  const everything = await allCards();

  // Tags und Familien aus dem Bestand für Vorschläge
  const tagCount = new Map();
  const famSet = new Set(FAMILIES);
  for (const c of everything) {
    for (const t of c.tags || []) tagCount.set(t, (tagCount.get(t) || 0) + 1);
    if (c.plant?.family) famSet.add(c.plant.family);
  }
  const topTags = [...tagCount.entries()].sort((a, b) => b[1] - a[1]).slice(0, 14).map(([t]) => t);

  // Fotos: {id?, data?, caption, url, existing}
  let photos = [];
  const removed = [];
  async function loadPhotos() {
    photos = [];
    for (const pid of card.photoIds || []) {
      const meta = await getPhoto(pid);
      if (!meta) continue;
      photos.push({ id: pid, caption: meta.caption || '', origCaption: meta.caption || '', url: await photoURL(pid, 'thumb'), existing: true, source: meta.source || null });
    }
  }
  await loadPhotos();

  const root = h('div', { class: 'page edit' });
  const form = h('form', { class: 'form', onsubmit: e => { e.preventDefault(); save(false); } });
  const photoGrid = h('div', { class: 'photo-grid' });
  const dupHint = h('div', { class: 'dup-hint', hidden: true });

  function renderPhotos() {
    photoGrid.replaceChildren(
      ...photos.map((p, i) => h('div', { class: 'ph' },
        h('img', { src: p.url, alt: p.caption || `Foto ${i + 1}` }),
        i === 0 && photos.length > 1 ? h('span', { class: 'ph-main' }, 'Hauptfoto') : null,
        p.source ? h('span', { class: 'ph-web', title: [p.source.author, p.source.license, p.source.source].filter(Boolean).join(' · ') }, icon('globe', 14), p.source.license || 'Web') : null,
        h('select', {
          class: 'ph-cap', 'aria-label': 'Bildunterschrift', value: p.caption,
          onchange: e => { p.caption = e.target.value; },
        }, CAPTIONS.map(c => h('option', { value: c }, c || 'Ohne Beschriftung'))),
        h('div', { class: 'ph-tools' },
          i > 0 ? iconBtn('back', 'Nach vorne', () => { photos.splice(i - 1, 0, photos.splice(i, 1)[0]); renderPhotos(); }, { class: 'small' }) : null,
          iconBtn('trash', 'Foto entfernen', () => { const [x] = photos.splice(i, 1); if (x.existing) removed.push(x.id); renderPhotos(); }, { class: 'small' })))),
      h('div', { class: 'ph-add' },
        h('button', { type: 'button', class: 'ph-add-btn', onclick: () => addPhotos('environment') }, icon('camera', 26), h('span', null, 'Kamera')),
        h('button', { type: 'button', class: 'ph-add-btn', onclick: () => addPhotos() }, icon('image', 26), h('span', null, 'Galerie')),
        h('button', { type: 'button', class: 'ph-add-btn', onclick: () => webSearch() }, icon('globe', 26), h('span', null, 'Internet'))));
  }

  /** Beispielbilder zum eingegebenen (botanischen) Namen im Internet suchen. */
  async function webSearch() {
    const query = laInput.value.trim() || deInput.value.trim() || titleInput.value.trim() || (card.type === 'basic' ? frontInput.value.trim() : '');
    if (!query) { toast('Bitte zuerst einen botanischen (oder deutschen) Namen eintragen.'); (card.type === 'plant' ? laInput : frontInput).focus(); return; }
    await openImageSearch({
      query,
      onAdd: async (blob, item) => {
        const data = await processImage(new File([blob], 'web.jpg', { type: blob.type || 'image/jpeg' }));
        photos.push({ data, caption: '', url: blobURL(data.thumb), existing: false, source: { author: item.author, license: item.license, source: item.source, page: item.page } });
        renderPhotos();
      },
      onTaxon: card.type === 'plant' ? taxon => {
        const filled = [];
        if (!laInput.value.trim() && taxon.name) { laInput.value = taxon.name; filled.push('botanischer Name'); }
        if (!deInput.value.trim() && taxon.commonName) { deInput.value = taxon.commonName; filled.push('deutscher Name'); }
        if (!famInput.value.trim() && taxon.family) {
          const known = [...famSet].find(f => familyKey(f).toLowerCase() === taxon.family.toLowerCase());
          famInput.value = known || (taxon.familyCommon ? `${taxon.family} (${taxon.familyCommon})` : taxon.family);
          filled.push('Familie');
        }
        updateTitleHint();
        toast(filled.length ? `Übernommen: ${filled.join(', ')}` : 'Die Felder sind schon ausgefüllt – nichts überschrieben.');
      } : null,
    });
  }

  async function addPhotos(capture) {
    const files = await pickFiles({ multiple: !capture, capture });
    if (!files.length) return;
    const t = toast(files.length > 1 ? `${files.length} Fotos werden verarbeitet …` : 'Foto wird verarbeitet …', { duration: 60000 });
    for (const f of files) {
      try {
        const data = await processImage(f);
        photos.push({ data, caption: '', url: blobURL(data.thumb), existing: false });
        if (!titleInput.value && !deInput?.value && photos.length === 1) {
          const n = nameFromFilename(f.name);
          if (n) { if (card.type === 'plant') deInput.value = n; else titleInput.value = n; }
        }
      } catch (err) {
        toast(`„${f.name}“ konnte nicht gelesen werden.`);
      }
    }
    t();
    renderPhotos();
  }

  // ---------- Felder ----------
  const deckSelect = h('select', { class: 'input', id: 'ed-deck', value: card.deckId, onchange: async e => {
    if (e.target.value === '__new') {
      const d = await newDeckFlow();
      if (d) { decks.push(d); deckSelect.append(h('option', { value: d.id }, d.name)); deckSelect.value = d.id; }
      else deckSelect.value = card.deckId;
    }
    card.deckId = deckSelect.value;
  } }, decks.map(d => h('option', { value: d.id }, d.name)), h('option', { value: '__new' }, '+ Neuer Stapel …'));

  const titleInput = h('input', { class: 'input', id: 'ed-title', value: card.title, placeholder: 'z. B. „Eiche am Spielplatz“ oder „Löwenzahn Blatt“', autocomplete: 'off' });
  const tagsInput = h('input', { class: 'input', id: 'ed-tags', value: (card.tags || []).join(', '), placeholder: 'wiese, essbar, prüfung', autocomplete: 'off' });
  const tagChips = h('div', { class: 'chip-row' }, topTags.map(t => h('button', {
    type: 'button', class: 'chip', onclick: () => {
      const tags = parseTags(tagsInput.value);
      const i = tags.indexOf(t);
      if (i >= 0) tags.splice(i, 1); else tags.push(t);
      tagsInput.value = tags.join(', ');
      syncChips();
    },
  }, t)));
  const syncChips = () => {
    const tags = parseTags(tagsInput.value);
    for (const c of tagChips.children) c.classList.toggle('on', tags.includes(c.textContent));
  };
  tagsInput.addEventListener('input', syncChips);
  syncChips();

  // Pflanzenfelder
  const p = card.plant;
  const deInput = h('input', { class: 'input', id: 'ed-de', value: p.deName, placeholder: 'z. B. Spitzwegerich', autocomplete: 'off' });
  const laInput = h('input', { class: 'input latin', id: 'ed-latin', value: p.latinName, placeholder: 'z. B. Plantago lanceolata', autocomplete: 'off', autocapitalize: 'off', spellcheck: 'false' });
  const famList = h('datalist', { id: 'fam-list' }, [...famSet].sort().map(f => h('option', { value: f })));
  const famInput = h('input', { class: 'input', id: 'ed-family', value: p.family, list: 'fam-list', placeholder: 'z. B. Plantaginaceae (Wegerichgewächse)', autocomplete: 'off' });
  const ta = (key, ph, rows = 3) => h('textarea', { class: 'input', id: 'ed-' + key, rows, value: p[key] || '', placeholder: ph });
  const featInput = ta('features', 'Erkennungsmerkmale: Blattform, Blüte, Wuchs, Geruch …', 4);
  const habInput = ta('habitat', 'Wo wächst sie? z. B. Wegränder, Magerwiesen', 2);
  const usesInput = ta('uses', 'Details, z. B. „Blätter als Tee bei Husten“', 2);
  // Schnellauswahl aus den Listen in den Einstellungen; „+ Eigene“ ergänzt auch die Liste
  const addToList = (key, title) => async () => {
    const v = (await promptDialog(title, { label: 'Neuer Eintrag', ok: 'Hinzufügen' }))?.trim();
    if (!v) return null;
    const list = app.settings[key] || [];
    if (!list.some(x => norm(x) === norm(v))) {
      app.settings = await saveSettings({ [key]: [...list, v] });
      toast(`„${v}“ steht jetzt auch in der Auswahlliste`);
    }
    return v;
  };
  const lifePicker = chipPicker(app.settings.lifeForms || [], card.plant.lifeForm, { id: 'ed-life', label: 'Lebensform', onAdd: addToList('lifeForms', 'Eigene Lebensform') });
  const usesPicker = chipPicker(app.settings.usesList || [], card.plant.usesList, { id: 'ed-uses', label: 'Verwendung', onAdd: addToList('usesList', 'Eigener Verwendungszweck') });
  const confInput = ta('confusion', 'Mit welchen Pflanzen kann man sie verwechseln, und woran erkennt man den Unterschied?', 2);
  const notesInput = ta('notes', 'Eigene Notizen, Fundorte, Eselsbrücken …', 2);
  const toxSelect = h('select', { class: 'input', id: 'ed-tox', value: p.toxicity || '' }, TOXICITY.map(t => h('option', { value: t.value }, t.label)));
  const bloom = new Set(p.bloom || []);
  const bloomRow = h('div', { class: 'month-picker', role: 'group', 'aria-label': 'Blütezeit' }, MONTHS_ABBR.map((m, i) => h('button', {
    type: 'button', class: 'month' + (bloom.has(i + 1) ? ' on' : ''), title: MONTHS[i], 'aria-pressed': String(bloom.has(i + 1)),
    onclick: e => {
      const k = i + 1;
      if (bloom.has(k)) bloom.delete(k); else bloom.add(k);
      e.currentTarget.classList.toggle('on', bloom.has(k));
      e.currentTarget.setAttribute('aria-pressed', String(bloom.has(k)));
    },
  }, m)));
  const questionInput = h('input', { class: 'input', id: 'ed-question', value: card.type === 'plant' ? card.front : '', placeholder: 'Standard: „Welche Pflanze ist das?“', autocomplete: 'off' });

  // Standardkarte
  const frontInput = h('textarea', { class: 'input', id: 'ed-front', rows: 3, value: card.type === 'basic' ? card.front : '', placeholder: 'Frage oder Begriff' });
  const backInput = h('textarea', { class: 'input', id: 'ed-back', rows: 4, value: card.back, placeholder: 'Antwort' });
  let photosOnFront = card.photosOnFront !== false;

  // Duplikat-Hinweis (deutscher oder botanischer Name schon vorhanden?)
  const checkDup = () => {
    const de = norm(deInput.value), la = norm(laInput.value);
    if (!de && !la) { dupHint.hidden = true; return; }
    const other = everything.find(c => c.id !== card.id && ((de && norm(c.plant?.deName) === de) || (la && norm(c.plant?.latinName) === la)));
    dupHint.hidden = !other;
    if (other) dupHint.replaceChildren(icon('info', 18), h('span', null, `Es gibt schon eine Karte „${displayName(other)}“. `),
      h('button', { type: 'button', class: 'link', onclick: () => app.go('edit', { id: other.id }) }, 'Öffnen'));
  };
  deInput.addEventListener('change', checkDup);
  laInput.addEventListener('change', checkDup);
  // Reihenfolge der Namensfelder nach Einstellung „Hauptname“
  const latinFirst = getNameMode() === 'la';
  const updateTitleHint = () => {
    const main = latinFirst ? (laInput.value || deInput.value) : (deInput.value || laInput.value);
    titleInput.placeholder = main ? `Leer lassen = „${main}“` : 'z. B. „Eiche am Spielplatz“';
  };
  deInput.addEventListener('input', updateTitleHint);
  laInput.addEventListener('input', updateTitleHint);
  const deField = field(latinFirst ? 'Deutscher Name' : 'Deutscher Name (Hauptname)', deInput);
  const laField = field(latinFirst ? 'Botanischer Name (Hauptname)' : 'Botanischer Name', laInput, 'Gattung groß, Art klein – z. B. Bellis perennis');

  // ---------- Steckbrief automatisch ergänzen (iNaturalist + Wikipedia) ----------
  const factsBar = h('div', { class: 'facts-bar' });
  let lastLookup = '';
  let undoState = null;

  /** Aktuelle Feldwerte als Pflanzenobjekt (für applyFacts). */
  const readFields = () => ({
    deName: deInput.value.trim(), latinName: laInput.value.trim(), family: famInput.value.trim(), features: featInput.value.trim(),
    bloom: [...bloom].sort((a, b) => a - b), lifeForm: [...card.plant.lifeForm], toxicity: toxSelect.value, habitat: habInput.value.trim(),
    uses: usesInput.value.trim(), confusion: confInput.value.trim(), notes: notesInput.value.trim(),
  });
  const inputs = { deName: deInput, latinName: laInput, family: famInput, features: featInput, habitat: habInput, uses: usesInput, confusion: confInput, notes: notesInput, toxicity: toxSelect };
  /** Werte zurück in die Felder schreiben und kurz hervorheben. */
  const writeFields = (v, highlight = []) => {
    for (const [k, el] of Object.entries(inputs)) el.value = v[k] ?? '';
    bloom.clear();
    (v.bloom || []).forEach(m => bloom.add(m));
    [...bloomRow.children].forEach((b, i) => { b.classList.toggle('on', bloom.has(i + 1)); b.setAttribute('aria-pressed', String(bloom.has(i + 1))); });
    card.plant.lifeForm.splice(0, card.plant.lifeForm.length, ...(v.lifeForm || []));
    lifePicker.refresh();
    updateTitleHint();
    for (const k of highlight) {
      const el = k === 'bloom' ? bloomRow : k === 'lifeForm' ? lifePicker : inputs[k];
      el?.classList.add('autofilled');
      setTimeout(() => el?.classList.remove('autofilled'), 2500);
    }
  };

  async function fetchFacts({ manual = false } = {}) {
    const latin = laInput.value.trim(), german = deInput.value.trim();
    if (!latin && !german) { if (manual) toast('Bitte zuerst einen botanischen oder deutschen Namen eintragen.'); return; }
    const key = norm(latin || german);
    if (!manual && key === lastLookup) return;
    lastLookup = key;
    factsBar.replaceChildren(h('span', { class: 'facts-status' }, icon('globe', 16), 'Suche Familie und Steckbrief …'));
    try {
      const facts = await lookupPlant({ latin, german, lifeForms: app.settings.lifeForms || [] });
      const before = readFields();
      const values = readFields();
      let filled = applyFacts(values, facts, { families: [...famSet] });
      if (!filled.length && manual) {
        if (await confirmDialog('Alle Felder sind schon ausgefüllt', 'Sollen die gefundenen Angaben die vorhandenen überschreiben?', { ok: 'Überschreiben', danger: false })) {
          filled = applyFacts(values, facts, { onlyEmpty: false, families: [...famSet] });
        }
      }
      if (!filled.length) {
        factsBar.replaceChildren(h('span', { class: 'facts-status' }, icon('check', 16), 'Nichts zu ergänzen – alles schon ausgefüllt.'),
          btn('Erneut suchen', () => fetchFacts({ manual: true }), { kind: 'ghost' }));
        return;
      }
      undoState = before;
      writeFields(values, filled);
      const src = facts.sources.map(s => s.name).join(' & ');
      factsBar.replaceChildren(
        h('span', { class: 'facts-status ok' }, icon('check', 16), h('span', null, h('strong', null, 'Ergänzt: '), filled.filter(k => k !== 'notes').map(k => FACT_LABELS[k]).join(', '), ` (${src})`)),
        h('div', { class: 'row-gap' },
          btn('Rückgängig', () => { if (undoState) { writeFields(undoState); undoState = null; factsBar.replaceChildren(h('span', { class: 'facts-status' }, 'Rückgängig gemacht.'), btn('Erneut suchen', () => fetchFacts({ manual: true }), { kind: 'ghost' })); } }, { kind: 'ghost' }),
          btn('Erneut suchen', () => fetchFacts({ manual: true }), { kind: 'ghost' })));
    } catch (err) {
      lastLookup = '';
      factsBar.replaceChildren(h('span', { class: 'facts-status bad' }, icon('info', 16), err?.message || String(err)),
        btn('Nochmal versuchen', () => fetchFacts({ manual: true }), { kind: 'ghost' }));
    }
  }
  // Automatisch, sobald ein Name eingegeben wurde (beim Verlassen des Feldes)
  const autoFacts = () => { if (card.type === 'plant' && app.settings.autoFacts !== false) fetchFacts(); };
  laInput.addEventListener('change', autoFacts);
  deInput.addEventListener('change', autoFacts);
  factsBar.append(btn('Familie & Steckbrief aus dem Internet ergänzen', () => fetchFacts({ manual: true }), { kind: 'ghost', iconName: 'globe' }));

  const plantSection = h('div', { class: 'form-section' },
    latinFirst ? laField : deField,
    dupHint,
    latinFirst ? deField : laField,
    factsBar,
    field('Familie', famInput),
    famList,
    field('Merkmale', featInput),
    h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Blütezeit'), bloomRow),
    h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Lebensform'), lifePicker,
      h('span', { class: 'field-hint' }, 'Mehrfachauswahl möglich, z. B. „Staude“ und „Hemikryptophyt“. Liste anpassen: Einstellungen → Auswahllisten.')),
    field('Standort', habInput),
    field('Giftigkeit', toxSelect),
    h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Verwendung'), usesPicker, usesInput),
    field('Verwechslungsgefahr', confInput),
    field('Notizen', notesInput),
    field('Eigene Frage (optional)', questionInput));

  const basicSection = h('div', { class: 'form-section' },
    field('Vorderseite', frontInput),
    field('Rückseite', backInput),
    toggle('Fotos schon auf der Vorderseite zeigen', photosOnFront, v => { photosOnFront = v; }, { id: 'ed-pof' }));

  const typeSwitch = segmented([
    { value: 'plant', label: 'Pflanze', icon: 'leaf' },
    { value: 'basic', label: 'Standard', icon: 'stack' },
  ], card.type, v => { card.type = v; plantSection.hidden = v !== 'plant'; basicSection.hidden = v !== 'basic'; }, { id: 'ed-type' });
  plantSection.hidden = card.type !== 'plant';
  basicSection.hidden = card.type !== 'basic';

  // Lernstand (nur bei bestehenden Karten)
  const s = card.srs;
  const stateLabel = { new: 'Neu', learning: 'Lernphase', relearning: 'Wiederholt lernen', review: 'Wiederholung' }[s.state];
  const srsInfo = existing ? h('section', { class: 'srs-info' },
    h('h3', { class: 'form-h' }, 'Lernstand'),
    h('div', { class: 'facts' },
      h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, stateLabel), h('span', { class: 'fact-l' }, 'Status')),
      h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, s.state === 'new' ? '–' : fmtDate(s.due)), h('span', { class: 'fact-l' }, 'Fällig')),
      h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, s.ivl ? formatDays(s.ivl) : '–'), h('span', { class: 'fact-l' }, 'Intervall')),
      h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, s.reps || 0), h('span', { class: 'fact-l' }, 'Abfragen')),
      h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, s.lapses || 0), h('span', { class: 'fact-l' }, 'Vergessen')),
      h('div', { class: 'fact' }, h('span', { class: 'fact-v' }, s.ease ? Math.round(s.ease * 100) + ' %' : '–'), h('span', { class: 'fact-l' }, 'Leichtigkeit'))),
    h('p', { class: 'muted small' }, `Erstellt ${fmtDate(card.created)} · geändert ${fmtDate(card.modified)}`)) : null;

  form.append(...[
    field('Stapel', deckSelect),
    h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Kartentyp'), typeSwitch),
    h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Fotos'), photoGrid,
      h('span', { class: 'field-hint' }, 'Mehrere Fotos pro Pflanze helfen: Blüte, Blatt, Frucht, Rinde. Beim Lernen wird zufällig eins gezeigt.')),
    plantSection,
    basicSection,
    h('div', { class: 'form-section naming' },
      field('Kartenname (zum Wiederfinden)', titleInput, 'Dein eigener Name für die Suche. Wird beim Lernen erst auf der Rückseite gezeigt.'),
      field('Schlagwörter', tagsInput, 'Mit Komma trennen. Antippen zum Hinzufügen:'),
      tagChips),
    srsInfo,
    h('div', { class: 'form-actions' },
      btn('Speichern', () => save(false), { kind: 'primary', iconName: 'check' }),
      isNew ? btn('Speichern & nächste', () => save(true), { iconName: 'plus' }) : null)].filter(Boolean));

  renderPhotos();

  function collect() {
    card.title = titleInput.value.trim();
    card.tags = parseTags(tagsInput.value);
    card.deckId = deckSelect.value === '__new' ? card.deckId : deckSelect.value;
    if (card.type === 'plant') {
      Object.assign(card.plant, {
        deName: deInput.value.trim(), latinName: laInput.value.trim(), family: famInput.value.trim(),
        features: featInput.value.trim(), bloom: [...bloom].sort((a, b) => a - b), habitat: habInput.value.trim(),
        toxicity: toxSelect.value, uses: usesInput.value.trim(), confusion: confInput.value.trim(), notes: notesInput.value.trim(),
      });
      card.front = questionInput.value.trim();
    } else {
      card.front = frontInput.value.trim();
      card.back = backInput.value.trim();
      card.photosOnFront = photosOnFront;
    }
  }

  function validate() {
    if (card.type === 'plant') {
      if (!card.plant.deName && !card.plant.latinName && !card.title && !photos.length) return 'Bitte mindestens einen Namen oder ein Foto angeben.';
      if (!card.plant.deName && !card.plant.latinName && !card.title) return 'Bitte einen Namen eintragen (deutsch, botanisch oder Kartenname), damit die Karte eine Antwort hat.';
    } else if (!card.front && !photos.length) return 'Die Vorderseite ist leer.';
    return null;
  }

  async function save(again) {
    collect();
    const err = validate();
    if (err) { toast(err); return; }
    // Fotos speichern
    for (const pid of removed) { await deletePhoto(pid); forgetPhoto(pid); }
    const ids = [];
    for (const ph of photos) {
      if (ph.existing) {
        if (ph.caption !== ph.origCaption) {
          const meta = await getPhoto(ph.id);
          if (meta) { meta.caption = ph.caption; await savePhoto(meta); }
        }
        ids.push(ph.id);
      } else {
        const saved = await addPhoto({ ...ph.data, caption: ph.caption, source: ph.source || null }, card.id);
        ids.push(saved.id);
      }
    }
    card.photoIds = ids;
    await saveCard(card);
    await setMeta('lastDeck', card.deckId);
    const deck = await getDeck(card.deckId);
    if (again) {
      toast(`Gespeichert in „${deck?.name}“`);
      app.go('edit', { deckId: card.deckId, type: card.type }, { replace: true });
    } else {
      toast(isNew ? `Karte in „${deck?.name}“ gespeichert` : 'Änderungen gespeichert');
      app.back();
    }
  }

  async function more() {
    const choice = await menu(displayName(card), [
      { label: 'Fotos zu dieser Pflanze erfassen', icon: 'camera', value: 'quick' },
      card.srs.state !== 'new' ? { label: 'Lernfortschritt zurücksetzen', icon: 'undo', value: 'reset' } : null,
      { label: card.suspended ? 'Wieder abfragen' : 'Aussetzen', icon: 'ban', value: 'suspend' },
      { label: card.marked ? 'Markierung entfernen' : 'Markieren', icon: 'star', value: 'mark' },
      { label: 'Karte löschen', icon: 'trash', value: 'delete', danger: true },
    ]);
    if (choice === 'quick') { app.go('quick', { targetId: card.id }); return; }
    if (choice === 'reset' && await confirmDialog('Lernfortschritt zurücksetzen?', 'Die Karte wird wieder als neue Karte abgefragt.', { ok: 'Zurücksetzen' })) {
      card.srs = newSrs();
      await saveCard(card);
      toast('Als neu markiert');
      app.reload();
    }
    if (choice === 'suspend') { card.suspended = !card.suspended; await saveCard(card); toast(card.suspended ? 'Ausgesetzt' : 'Wird wieder abgefragt'); }
    if (choice === 'mark') { card.marked = !card.marked; await saveCard(card); toast(card.marked ? 'Markiert' : 'Markierung entfernt'); }
    if (choice === 'delete' && await confirmDialog('Karte löschen?', 'Die Karte und ihre Fotos werden endgültig gelöscht.')) {
      await deleteCards([card.id]);
      toast('Karte gelöscht');
      app.back();
    }
  }

  root.append(
    topbar({
      title: isNew ? 'Neue Karte' : 'Karte bearbeiten',
      back: () => app.back(),
      actions: [
        existing ? iconBtn('more', 'Mehr', more) : null,
        iconBtn('check', 'Speichern', () => save(false)),
      ],
    }),
    form);

  if (isNew) setTimeout(() => (card.type === 'plant' ? (latinFirst ? laInput : deInput) : frontInput).focus(), 80);
  return { el: root, fullscreen: true };
}
