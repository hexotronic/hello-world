// Fenster „Beispielbilder aus dem Internet“: suchen, auswählen, hinzufügen.
import { h, dialog, segmented, btn, toast } from './ui.js';
import { icon } from './icons.js';
import { searchINaturalist, searchCommons, downloadImage } from './webimages.js';

let lastSource = 'inat';

/**
 * query: Suchbegriff (botanischer Name bevorzugt)
 * onAdd(blob, item): wird für jedes gewählte Bild aufgerufen
 * onTaxon(taxon): optional – „Namen & Familie übernehmen“
 */
export async function openImageSearch({ query, onAdd, onTaxon }) {
  let source = lastSource;
  let q = query;
  let items = [];
  let page = 1;
  let offset = 0;
  let hasMore = false;
  let taxon = null;
  const selected = new Map();
  let token = 0;

  const input = h('input', { class: 'input search-input', type: 'search', id: 'img-q', value: q, autocomplete: 'off', enterkeyhint: 'search', autocapitalize: 'off', spellcheck: 'false' });
  const grid = h('div', { class: 'web-grid' });
  const info = h('div', { class: 'web-info' });
  const status = h('p', { class: 'muted small web-status' });
  const more = btn('Mehr laden', () => load(true), { cls: 'more-btn', attrs: { hidden: true } });

  function updateCount() {
    const b = document.querySelector('.web-sheet .dialog-actions .btn-primary');
    if (b) { b.textContent = selected.size ? `${selected.size} ${selected.size === 1 ? 'Bild' : 'Bilder'} hinzufügen` : 'Hinzufügen'; b.disabled = !selected.size; }
  }

  function tile(it) {
    const on = selected.has(it.id);
    const t = h('button', {
      type: 'button', class: 'web-tile' + (on ? ' on' : ''), 'aria-pressed': String(on), title: [it.author, it.license].filter(Boolean).join(' · '),
      onclick: () => {
        if (selected.has(it.id)) selected.delete(it.id); else selected.set(it.id, it);
        t.classList.toggle('on', selected.has(it.id));
        t.setAttribute('aria-pressed', String(selected.has(it.id)));
        updateCount();
      },
    },
    h('img', { src: it.thumb, alt: '', loading: 'lazy', referrerpolicy: 'no-referrer' }),
    it.license ? h('span', { class: 'web-lic' }, it.license) : null,
    h('span', { class: 'web-check' }, icon('check', 18)));
    return t;
  }

  function renderInfo() {
    info.replaceChildren();
    if (source !== 'inat' || !taxon) return;
    const parts = [h('strong', { class: 'latin' }, taxon.name), taxon.commonName ? ` · ${taxon.commonName}` : '', taxon.family ? ` · ${taxon.family}` : ''];
    info.append(h('span', null, 'Gefunden: ', ...parts));
    if (onTaxon && (taxon.commonName || taxon.family)) {
      info.append(h('button', { type: 'button', class: 'link small', onclick: () => onTaxon(taxon) }, 'Namen & Familie übernehmen'));
    }
  }

  async function load(append = false) {
    const my = ++token;
    if (!append) { items = []; page = 1; offset = 0; taxon = null; grid.replaceChildren(); }
    status.textContent = 'Suche läuft …';
    more.hidden = true;
    try {
      let res;
      if (source === 'inat') {
        res = await searchINaturalist(q, page);
        taxon = res.taxon;
        hasMore = res.hasMore;
        page++;
      } else {
        res = await searchCommons(q, offset);
        hasMore = res.next != null;
        offset = res.next ?? offset;
      }
      if (my !== token) return;
      const known = new Set(items.map(i => i.id));
      const fresh = res.items.filter(i => !known.has(i.id));
      items.push(...fresh);
      grid.append(...fresh.map(tile));
      renderInfo();
      status.textContent = items.length
        ? `${items.length} ${items.length === 1 ? 'Bild' : 'Bilder'} · antippen zum Auswählen`
        : (source === 'inat' && !taxon ? `Keine Pflanze „${q}“ bei iNaturalist gefunden. Schreibweise prüfen oder Wikimedia versuchen.` : 'Keine Bilder gefunden.');
      more.hidden = !hasMore;
    } catch (err) {
      if (my !== token) return;
      status.textContent = err?.message || String(err);
    }
  }

  const sourceSwitch = segmented([
    { value: 'inat', label: 'iNaturalist' },
    { value: 'commons', label: 'Wikimedia Commons' },
  ], source, v => { source = v; lastSource = v; load(); }, { id: 'img-source' });

  const search = () => { q = input.value.trim(); if (q) load(); };
  input.addEventListener('keydown', e => { if (e.key === 'Enter') { e.preventDefault(); search(); } });

  setTimeout(() => { updateCount(); load(); }, 0);

  const chosen = await dialog({
    title: 'Beispielbilder aus dem Internet',
    sheet: true,
    cls: 'web-sheet',
    body: h('div', { class: 'web-body' },
      h('div', { class: 'add-row' }, h('div', { class: 'search-wrap' }, icon('search', 20), input), btn('Suchen', search)),
      sourceSwitch,
      info,
      status,
      h('div', { class: 'web-scroll' }, grid, more),
      h('p', { class: 'field-hint' }, 'Freie Bilder (Creative Commons) von iNaturalist bzw. Wikimedia Commons. Urheber und Lizenz werden beim Foto gespeichert und im Vollbild angezeigt.')),
    actions: [
      { label: 'Abbrechen', value: null },
      { label: 'Hinzufügen', kind: 'primary', value: () => [...selected.values()] },
    ],
  });
  token++;
  if (!chosen?.length) return 0;

  const stop = toast(`${chosen.length} ${chosen.length === 1 ? 'Bild wird' : 'Bilder werden'} geladen …`, { duration: 120000 });
  let ok = 0;
  for (const it of chosen) {
    try {
      const blob = await downloadImage(it.full);
      await onAdd(blob, it);
      ok++;
    } catch (err) {
      toast(err?.message || String(err));
    }
  }
  stop();
  if (ok) toast(`${ok} ${ok === 1 ? 'Bild' : 'Bilder'} hinzugefügt. Speichern nicht vergessen.`);
  return ok;
}
