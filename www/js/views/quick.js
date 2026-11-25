// Schnell-Erfassung: viele Fotos auf einmal auswählen und nacheinander benennen –
// oder alle Fotos einer fest gewählten, vorhandenen Pflanze hinzufügen.
import { app } from '../app.js';
import { h, topbar, btn, field, pickFiles, toast, emptyState, chipPicker, segmented } from '../ui.js';
import { icon } from '../icons.js';
import { listDecks, allCards, getCard, emptyCard, saveCard, addPhoto, setMeta, getMeta, displayName } from '../db.js';
import { processImage, blobURL, photoURL } from '../images.js';
import { FAMILIES, CAPTIONS, getNameMode, photoBaseName } from '../plants.js';
import { isNative, CloudFile, readNativeFile } from '../native.js';
import { norm, nameFromFilename } from '../util.js';
import { newDeckFlow } from './home.js';
import { cloudSoon } from '../cloud.js';
import { pickCard, rememberTarget } from '../cardpicker.js';
import { lookupPlant, applyFacts, skippedFacts, enrichLater } from '../plantfacts.js';
import { isOn } from '../features.js';

const TARGET_KEY = 'quickTarget';

export default async function quickView(params = {}) {
  let decks = await listDecks();
  const everything = await allCards();
  const last = await getMeta('lastDeck');
  let deckId = params.deckId || (decks.some(d => d.id === last) ? last : decks[0]?.id);
  const root = h('div', { class: 'page quick' });

  // files: [{ name, load: () => Promise<File>, uri? }] – uri = Originalfoto in der Galerie (nur Android)
  let files = [];
  let i = 0;
  // Originalfotos, die am Ende umbenannt werden: { uri, cardId, photoId }
  let renameJobs = [];
  let renameNote = '';
  let created = 0, appended = 0, skipped = 0;
  let lastCard = null;
  let stickyTags = '';
  let stickyFamily = '';
  let current = null; // {file, data, url}

  // Festes Ziel: alle Fotos gehen zu dieser vorhandenen Karte, bis die Auswahl geändert wird.
  let target = null;
  const savedTargetId = params.targetId || await getMeta(TARGET_KEY);
  if (savedTargetId) target = await getCard(savedTargetId) || null;
  let mode = params.targetId ? 'existing' : ((await getMeta('quickMode')) === 'existing' && target ? 'existing' : 'new');
  const setMode = m => { mode = m; setMeta('quickMode', m); };

  // Bekannte Pflanzen, auffindbar über deutschen Namen, botanischen Namen oder Kartennamen
  const byName = new Map();
  const remember = c => { for (const n of [c.plant?.deName, c.plant?.latinName, c.title]) if (norm(n)) byName.set(norm(n), c); };
  everything.forEach(remember);
  const latinFirst = getNameMode() === 'la';

  async function setTarget(card) {
    target = card;
    if (card) {
      setMode('existing');
      await setMeta(TARGET_KEY, card.id);
      await rememberTarget(card.id);
    } else {
      await setMeta(TARGET_KEY, null);
    }
  }

  async function chooseTarget() {
    const card = await pickCard({ title: 'Zu welcher Pflanze gehören die Fotos?' });
    if (card) await setTarget(card);
    return card;
  }

  /** Kleine Leiste mit dem festen Ziel (Vorschaubild, Name, Ändern, Lösen). */
  function targetBar(onChange) {
    const thumb = h('img', { class: 'cl-thumb', alt: '' });
    if (target.photoIds?.length) photoURL(target.photoIds[0], 'thumb').then(u => { if (u) thumb.src = u; });
    return h('div', { class: 'target-bar' },
      target.photoIds?.length ? thumb : h('span', { class: 'cl-thumb none' }, icon('leaf', 20)),
      h('span', { class: 'target-main' },
        h('span', { class: 'target-label' }, 'Fotos werden hinzugefügt zu'),
        h('strong', { class: 'target-name' }, displayName(target)),
        h('span', { class: 'muted small' }, `${target.photoIds?.length || 0} Fotos vorhanden`)),
      h('div', { class: 'target-actions' },
        btn('Ändern', async () => { if (await chooseTarget()) onChange(); }, { kind: 'ghost' }),
        h('button', {
          type: 'button', class: 'icon-btn small', 'aria-label': 'Feste Auswahl aufheben',
          onclick: async () => { await setTarget(null); setMode('new'); onChange(); },
        }, icon('close', 18))));
  }

  function renderStart() {
    const photoButtons = () => h('div', { class: 'btn-col' },
      btn('Fotos aus Galerie wählen', () => choose(), { kind: 'primary', iconName: 'image' }),
      btn('Mit Kamera aufnehmen', () => choose('environment'), { iconName: 'camera' }));

    const modeSwitch = segmented([
      { value: 'new', label: 'Neue Pflanzen', icon: 'plus' },
      { value: 'existing', label: 'Vorhandene Pflanze', icon: 'leaf' },
    ], mode, v => { setMode(v); renderStart(); }, { id: 'q-mode' });

    let section;
    if (mode === 'existing') {
      section = target
        ? h('div', { class: 'form' },
          targetBar(renderStart),
          photoButtons(),
          h('p', { class: 'field-hint' }, 'Die Auswahl bleibt bestehen, auch beim nächsten Öffnen – bis du sie änderst oder mit × aufhebst.'))
        : h('div', { class: 'form' },
          h('p', { class: 'lead' }, 'Wähle eine Pflanze aus deinen Karten. Alle Fotos, die du danach erfasst, kommen zu dieser Pflanze.'),
          btn('Pflanze auswählen …', async () => { if (await chooseTarget()) renderStart(); }, { kind: 'primary', iconName: 'search' }));
    } else {
      section = decks.length
        ? h('div', { class: 'form' },
          h('p', { class: 'lead' }, 'Viele Fotos auf einmal auswählen und nacheinander benennen. Fotos derselben Pflanze landen mit einem Tipp auf einer Karte.'),
          field('In Stapel', h('select', { class: 'input', id: 'q-deck', value: deckId, onchange: async e => {
            if (e.target.value === '__new') {
              const d = await newDeckFlow();
              if (d) { decks = await listDecks(); deckId = d.id; }
              renderStart();
              return;
            }
            deckId = e.target.value;
          } }, decks.map(d => h('option', { value: d.id }, d.name)), h('option', { value: '__new' }, '+ Neuer Stapel …'))),
          photoButtons())
        : emptyState('layers', 'Erst einen Stapel anlegen', null, btn('Stapel anlegen', async () => { const d = await newDeckFlow(); if (d) { decks = await listDecks(); deckId = d.id; renderStart(); } }, { kind: 'primary' }));
    }

    root.replaceChildren(
      topbar({ title: 'Fotos schnell erfassen', back: () => app.back() }),
      h('div', { class: 'quick-body' }, modeSwitch, section));
  }

  const nativeGallery = () => isNative && CloudFile && app.settings.renameOriginals !== false;

  async function choose(capture) {
    if (mode === 'existing' && !target && !(await chooseTarget())) return;
    if (!capture && nativeGallery()) {
      // Android: eigene Auswahl, damit die Originalfotos später umbenannt werden können
      let res;
      try {
        res = await CloudFile.pickImages({ multiple: true });
      } catch (err) {
        if (err?.code !== 'CANCELLED') toast('Fotos konnten nicht geöffnet werden: ' + (err?.message || err));
        return;
      }
      if (!res.items?.length) return;
      files = res.items.map(it => ({
        name: it.name, uri: it.uri,
        load: async () => { const blob = await readNativeFile(it.path); return new File([blob], it.name, { type: blob.type || 'image/jpeg' }); },
      }));
    } else {
      const picked = await pickFiles({ multiple: !capture, capture });
      if (!picked.length) return;
      files = picked.map(f => ({ name: f.name, load: async () => f }));
    }
    i = 0;
    renameNote = '';
    if (mode === 'new') await setMeta('lastDeck', deckId);
    loadCurrent();
  }

  async function loadCurrent() {
    if (i >= files.length) return renderDone();
    root.replaceChildren(topbar({ title: `Foto ${i + 1} von ${files.length}`, back: renderDone }), h('div', { class: 'boot' }, icon('image', 32), h('p', null, 'Foto wird vorbereitet …')));
    try {
      const data = await processImage(await files[i].load());
      current = { file: files[i], data, url: blobURL(data.blob) };
      if (mode === 'existing' && target) renderTargetItem();
      else renderItem();
    } catch {
      toast(`„${files[i].name}“ konnte nicht gelesen werden.`);
      skipped++;
      i++;
      loadCurrent();
    }
  }

  /** Originalfoto merken, damit es am Ende nach der Pflanze benannt wird. */
  function queueRename(fileEntry, cardId, photoId) {
    if (fileEntry?.uri) renameJobs.push({ uri: fileEntry.uri, cardId, photoId });
  }

  /** Foto an eine vorhandene Karte anhängen (Karte frisch laden, damit nichts überschrieben wird). */
  async function appendPhoto(cardId, data, caption, fileEntry) {
    const card = await getCard(cardId);
    if (!card) throw new Error('Die Karte gibt es nicht mehr.');
    const p = await addPhoto({ ...data, caption }, card.id);
    card.photoIds = [...(card.photoIds || []), p.id];
    await saveCard(card);
    queueRename(fileEntry, card.id, p.id);
    appended++;
    return card;
  }

  /** Benennt alle gemerkten Originalfotos in der Galerie um (Android fragt einmal um Erlaubnis). */
  async function renameOriginals() {
    if (!renameJobs.length || !CloudFile) return;
    const jobs = renameJobs;
    renameJobs = [];
    const items = [];
    for (const j of jobs) {
      const card = await getCard(j.cardId);
      if (!card) continue;
      const idx = (card.photoIds || []).indexOf(j.photoId);
      items.push({ uri: j.uri, name: `${photoBaseName(card)} ${idx >= 0 ? idx + 1 : 1}` });
    }
    try {
      const res = await CloudFile.renameImages({ items });
      const failed = res.failed || [];
      renameNote = `${res.renamed} ${res.renamed === 1 ? 'Originalfoto' : 'Originalfotos'} in der Galerie umbenannt`
        + (failed.length ? ` · ${failed.length} nicht möglich (${failed[0].reason})` : '');
    } catch (err) {
      renameNote = 'Originalfotos konnten nicht umbenannt werden: ' + (err?.message || err);
    }
    CloudFile.clearPicked?.().catch(() => {});
  }

  function next() {
    if (current) URL.revokeObjectURL(current.url);
    current = null;
    i++;
    loadCurrent();
  }

  // ---------- Modus: feste vorhandene Pflanze ----------
  function renderTargetItem() {
    const cap = h('select', { class: 'input', id: 'q-cap', 'aria-label': 'Bildunterschrift' }, CAPTIONS.map(c => h('option', { value: c }, c || 'Bildunterschrift (optional)')));
    const remaining = files.length - i;

    async function addThis() {
      try {
        target = await appendPhoto(target.id, current.data, cap.value, current.file);
        next();
      } catch (err) {
        toast(err.message);
      }
    }

    async function addAll() {
      const stop = toast(`${remaining} Fotos werden hinzugefügt …`, { duration: 120000 });
      try {
        target = await appendPhoto(target.id, current.data, cap.value, current.file);
        URL.revokeObjectURL(current.url);
        current = null;
        for (let k = i + 1; k < files.length; k++) {
          try {
            const data = await processImage(await files[k].load());
            target = await appendPhoto(target.id, data, '', files[k]);
          } catch {
            skipped++;
          }
        }
        i = files.length;
      } catch (err) {
        toast(err.message);
      }
      stop();
      renderDone();
    }

    root.replaceChildren(
      topbar({ title: `Foto ${i + 1} von ${files.length}`, sub: `${appended} hinzugefügt`, back: renderDone }),
      h('div', { class: 'quick-item' },
        h('figure', { class: 'quick-photo' }, h('img', { src: current.url, alt: 'Ausgewähltes Foto' })),
        h('div', { class: 'form' },
          targetBar(() => { if (mode === 'existing' && target) renderTargetItem(); else renderItem(); }),
          cap,
          h('div', { class: 'btn-col' },
            btn(`Zu „${displayName(target)}“ hinzufügen`, addThis, { kind: 'primary', iconName: 'check' }),
            remaining > 1 ? btn(`Alle ${remaining} verbleibenden Fotos hinzufügen`, addAll, { iconName: 'plus' }) : null,
            btn('Überspringen', () => { skipped++; next(); }, { kind: 'ghost' })))));
  }

  // ---------- Modus: neue Pflanzen benennen ----------
  function renderItem() {
    const suggested = nameFromFilename(current.file.name);
    const de = h('input', { class: 'input', id: 'q-de', 'aria-label': 'Deutscher Name', value: latinFirst ? '' : suggested, placeholder: latinFirst ? 'Deutscher Name (optional)' : 'Deutscher Name', autocomplete: 'off', list: latinFirst ? null : 'q-names', enterkeyhint: 'next' });
    const la = h('input', { class: 'input latin', id: 'q-la', 'aria-label': 'Botanischer Name', value: latinFirst ? suggested : '', placeholder: latinFirst ? 'Botanischer Name' : 'Botanischer Name (optional)', autocomplete: 'off', list: latinFirst ? 'q-names' : null, autocapitalize: 'off', spellcheck: 'false', enterkeyhint: latinFirst ? 'next' : 'done' });
    const first = latinFirst ? la : de;
    const second = latinFirst ? de : la;
    const fam = h('input', { class: 'input', id: 'q-fam', 'aria-label': 'Familie', value: stickyFamily, list: 'q-fams', placeholder: 'Familie (optional)', autocomplete: 'off' });
    const cap = h('select', { class: 'input', id: 'q-cap', 'aria-label': 'Bildunterschrift' }, CAPTIONS.map(c => h('option', { value: c }, c || 'Bildunterschrift (optional)')));
    const tags = h('input', { class: 'input', id: 'q-tags', 'aria-label': 'Schlagwörter', value: stickyTags, placeholder: 'Schlagwörter, z. B. garten, exkursion-mai', autocomplete: 'off' });
    const match = h('div', { class: 'dup-hint', hidden: true });
    const life = [];
    const lifePicker = chipPicker(app.settings.lifeForms || [], life, { id: 'q-life', label: 'Lebensform' });
    const uses = [];
    const usesPicker = chipPicker(app.settings.usesList || [], uses, { id: 'q-uses', label: 'Verwendung' });
    const names = h('datalist', { id: 'q-names' }, [...new Set(everything.map(c => (latinFirst ? c.plant?.latinName : c.plant?.deName)).filter(Boolean))].sort().map(n => h('option', { value: n })));
    const fams = h('datalist', { id: 'q-fams' }, FAMILIES.map(f => h('option', { value: f })));

    const checkMatch = () => {
      const c = byName.get(norm(first.value));
      match.hidden = !c;
      if (c) {
        match.replaceChildren(icon('info', 18), h('span', null, `„${displayName(c)}“ gibt es schon (${c.photoIds?.length || 0} Fotos). `),
          h('button', { type: 'button', class: 'link', onclick: () => appendTo(c, cap.value) }, 'Foto dort hinzufügen'),
          h('span', null, ' · '),
          h('button', { type: 'button', class: 'link', onclick: async () => { await setTarget(c); renderTargetItem(); } }, 'Fest auswählen'));
      }
    };
    first.addEventListener('input', checkMatch);
    first.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); second.focus(); } });
    second.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); saveNew(); } });

    async function saveNew(unnamed = false) {
      const name = de.value.trim();
      if (!name && !la.value.trim() && !unnamed) { toast('Bitte einen Namen eingeben oder „Ohne Namen speichern“ wählen.'); first.focus(); return; }
      stickyTags = tags.value;
      stickyFamily = fam.value.trim();
      const card = emptyCard(deckId, 'plant');
      Object.assign(card.plant, { deName: name, latinName: la.value.trim(), family: fam.value.trim(), lifeForm: [...life], usesList: [...uses] });
      card.tags = [...new Set(tags.value.split(/[,;]+/).map(t => t.trim().toLowerCase().replace(/\s+/g, '-')).filter(Boolean))];
      if (unnamed) { card.title = ''; card.tags.push('unbenannt'); }
      const p = await addPhoto({ ...current.data, caption: cap.value }, card.id);
      card.photoIds = [p.id];
      await saveCard(card);
      queueRename(current.file, card.id, p.id);
      // Familie und Steckbrief im Hintergrund ergänzen (nur leere Felder)
      if (app.settings.autoFacts !== false && (name || card.plant.latinName)) {
        const latin = card.plant.latinName, german = name;
        enrichLater(async () => {
          const facts = await lookupPlant({ latin, german, lifeForms: app.settings.lifeForms || [] });
          const fresh = await getCard(card.id);
          if (fresh && applyFacts(fresh.plant, facts, { families: FAMILIES, skip: skippedFacts(app.settings) }).length) await saveCard(fresh);
        });
      }
      everything.push(card);
      remember(card);
      lastCard = card;
      created++;
      next();
    }

    async function appendTo(card, caption) {
      try {
        lastCard = await appendPhoto(card.id, current.data, caption, current.file);
        toast(`Foto zu „${displayName(lastCard)}“ hinzugefügt`);
        next();
      } catch (err) {
        toast(err.message);
      }
    }

    root.replaceChildren(
      topbar({ title: `Foto ${i + 1} von ${files.length}`, sub: `${created} neu · ${appended} ergänzt`, back: renderDone }),
      h('div', { class: 'quick-item' },
        h('figure', { class: 'quick-photo' }, h('img', { src: current.url, alt: 'Ausgewähltes Foto' })),
        h('div', { class: 'form' },
          first, names, match, second,
          isOn('field:lifeForm') ? h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Lebensform (optional)'), lifePicker) : null,
          isOn('field:uses') ? h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Verwendung (optional)'), usesPicker) : null,
          isOn('field:family') ? fam : null, fams,
          h('div', { class: 'grid2' }, cap, isOn('field:tags') ? tags : null),
          h('div', { class: 'btn-col' },
            btn('Speichern & weiter', () => saveNew(), { kind: 'primary', iconName: 'check' }),
            lastCard ? btn(`Gleiche Pflanze wie eben („${displayName(lastCard)}“)`, () => appendTo(lastCard, cap.value), { iconName: 'plus' }) : null,
            btn('Zu vorhandener Pflanze … (fest auswählen)', async () => { if (await chooseTarget()) renderTargetItem(); }, { iconName: 'leaf' }),
            h('div', { class: 'row-gap' },
              btn('Ohne Namen speichern', () => saveNew(true), { kind: 'ghost' }),
              btn('Überspringen', () => { skipped++; next(); }, { kind: 'ghost' }))))));
    checkMatch();
    setTimeout(() => first.focus(), 60);
  }

  async function renderDone() {
    cloudSoon();
    if (current) { URL.revokeObjectURL(current.url); current = null; }
    if (renameJobs.length) {
      root.replaceChildren(topbar({ title: 'Erfassung beendet' }), h('div', { class: 'boot' }, icon('image', 32), h('p', null, 'Originalfotos werden umbenannt …')));
      await renameOriginals();
    }
    const withTarget = mode === 'existing' && target;
    const summary = withTarget && !created
      ? `${appended} ${appended === 1 ? 'Foto' : 'Fotos'} zu „${displayName(target)}“ hinzugefügt`
      : `${created} neue Karten, ${appended} Fotos ergänzt`;
    root.replaceChildren(
      topbar({ title: 'Erfassung beendet', back: () => app.back() }),
      h('div', { class: 'quick-body' },
        h('div', { class: 'finished' },
          h('div', { class: 'finished-icon' }, icon('check', 36)),
          h('h2', null, summary),
          skipped ? h('p', { class: 'muted' }, `${skipped} übersprungen`) : null,
          renameNote ? h('p', { class: 'muted' }, renameNote) : null,
          withTarget
            ? h('div', { class: 'finished-actions' },
              btn('Noch ein Foto (Kamera)', () => choose('environment'), { kind: 'primary', iconName: 'camera' }),
              btn('Weitere aus Galerie', () => choose(), { iconName: 'image' }),
              btn('Karte ansehen', () => app.go('edit', { id: target.id }, { replace: true }), { kind: 'ghost' }),
              btn('Andere Pflanze wählen', async () => { if (await chooseTarget()) renderStart(); }, { kind: 'ghost' }))
            : h('div', { class: 'finished-actions' },
              btn('Zum Stapel', () => app.go('deck', { id: deckId }, { replace: true }), { kind: 'primary' }),
              btn('Weitere Fotos', () => renderStart(), { iconName: 'image' }),
              btn('Unbenannte ansehen', () => app.go('browse', { tag: 'unbenannt' }), { kind: 'ghost' })))));
  }

  renderStart();
  return { el: root, fullscreen: true };
}
