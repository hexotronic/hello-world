// Darstellung einer Karte (Vorder-/Rückseite), Herbar-Etikett, Blühkalender, Foto-Zoom.
import { h, iconBtn } from './ui.js';
import { icon } from './icons.js';
import { photoURL } from './images.js';
import { getPhoto, displayName } from './db.js';
import { MONTHS_SHORT, MONTHS, formatMonths, speak } from './util.js';
import { toxicityLabel, hintText, mainName, mainIsLatin, otherName, photoName, safeFileName } from './plants.js';
import { shareFile } from './backup.js';

export function monthBar(months, { compact = false } = {}) {
  const set = new Set(months || []);
  const now = new Date().getMonth() + 1;
  return h('div', { class: 'monthbar' + (compact ? ' compact' : ''), role: 'img', 'aria-label': 'Blütezeit: ' + (formatMonths(months) || 'unbekannt') },
    MONTHS_SHORT.map((m, i) => h('span', {
      class: 'mb-cell' + (set.has(i + 1) ? ' on' : '') + (now === i + 1 ? ' now' : ''),
      title: MONTHS[i],
    }, m)));
}

function row(label, value, cls = '') {
  if (!value) return null;
  return h('div', { class: 'hl-row ' + cls }, h('dt', null, label), h('dd', null, value));
}

/** Rückseite einer Pflanzenkarte im Stil eines Herbar-Etiketts. */
export function herbariumLabel(card, { speechLang = 'de-DE' } = {}) {
  const p = card.plant || {};
  const tox = toxicityLabel(p.toxicity);
  const custom = (card.title || '').trim();
  const main = mainName(p) || custom;
  const mainLatin = mainIsLatin(p);
  const other = otherName(p);
  const say = text => iconBtn('volume', `„${text}“ vorlesen`, e => { e.stopPropagation(); speak(text, speechLang); }, { class: 'speak' });
  return h('section', { class: 'herb-label', 'aria-label': 'Pflanzenetikett' },
    h('div', { class: 'hl-head' },
      h('span', { class: 'hl-kicker' }, p.family || 'Familie unbekannt'),
      tox ? h('span', { class: 'tox tox-' + (p.toxicity || 'unknown') }, tox) : null),
    h('div', { class: 'hl-names' },
      h('div', { class: 'hl-de' + (mainLatin ? ' latin' : '') }, main || '—', main ? say(main) : null),
      other ? h('div', { class: mainLatin ? 'hl-sub' : 'hl-latin' }, other, say(other)) : null),
    h('dl', { class: 'hl-rows' },
      row('Lebensform', (p.lifeForm || []).join(' · ')),
      row('Merkmale', p.features, 'pre'),
      (p.bloom || []).length ? h('div', { class: 'hl-row' }, h('dt', null, 'Blütezeit'), h('dd', null, monthBar(p.bloom), h('span', { class: 'hl-months' }, formatMonths(p.bloom)))) : null,
      row('Standort', p.habitat, 'pre'),
      (p.usesList?.length || p.uses) ? h('div', { class: 'hl-row' }, h('dt', null, 'Verwendung'), h('dd', null,
        p.usesList?.length ? h('div', { class: 'hl-tags' }, p.usesList.map(u => h('span', { class: 'hl-tag' }, u))) : null,
        p.uses ? h('div', { class: 'pre' }, p.uses) : null)) : null,
      row('Verwechslung', p.confusion, 'pre warn'),
      row('Notizen', p.notes, 'pre'),
      card.back ? row('Rückseite', card.back, 'pre') : null,
      custom && custom !== main ? row('Kartenname', custom) : null));
}

/** Foto-Bereich mit Punkten zum Wechseln und Zoom per Antippen. */
export function photoStage(card, startIndex = 0, { onChange, single = false, named = false } = {}) {
  const all = card.photoIds || [];
  const start = Math.min(Math.max(0, startIndex), all.length - 1);
  // single: nur dieses eine Foto zeigen, kein Wechseln zu den anderen (auch nicht im Vollbild)
  const ids = single ? [all[start]] : all;
  let index = single ? 0 : start;
  const img = h('img', { class: 'stage-img', alt: 'Foto der Karte', draggable: 'false' });
  const caption = h('span', { class: 'stage-caption', hidden: true });
  const dots = h('div', { class: 'stage-dots' });
  const stage = h('figure', { class: 'stage', onclick: () => openZoom(ids, index, named ? { card } : {}) },
    img, caption, ids.length > 1 ? dots : null,
    h('span', { class: 'stage-zoom' }, icon('zoom', 18)));

  async function show(i) {
    index = (i + ids.length) % ids.length;
    stage.classList.add('loading');
    const [url, meta] = await Promise.all([photoURL(ids[index], 'full'), getPhoto(ids[index])]);
    img.src = url || '';
    stage.classList.remove('loading');
    caption.textContent = meta?.caption || '';
    caption.hidden = !meta?.caption;
    dots.replaceChildren(...ids.map((_, k) => h('button', {
      type: 'button', class: 'dot' + (k === index ? ' on' : ''), 'aria-label': `Foto ${k + 1} von ${ids.length}`,
      onclick: e => { e.stopPropagation(); show(k); },
    })));
    onChange?.(single ? start : index);
  }

  // Wischen zwischen Fotos
  let x0 = null;
  stage.addEventListener('touchstart', e => { x0 = e.touches[0].clientX; }, { passive: true });
  stage.addEventListener('touchend', e => {
    if (x0 == null || ids.length < 2) return;
    const dx = e.changedTouches[0].clientX - x0;
    x0 = null;
    if (Math.abs(dx) > 50) { e.preventDefault(); show(index + (dx < 0 ? 1 : -1)); }
  });

  if (ids.length) show(index);
  return { el: stage, show, get index() { return index; } };
}

/** Vollbildansicht mit Zoom (Doppeltippen/Klick) und Blättern. */
/** card: nur übergeben, wenn der Name gezeigt werden darf (z. B. nach dem Aufdecken). */
export function openZoom(ids, start = 0, { card } = {}) {
  if (!ids?.length) return;
  let i = start;
  let zoomed = false;
  const img = h('img', { class: 'zoom-img', alt: 'Foto groß', draggable: 'false' });
  const cap = h('div', { class: 'zoom-cap' });
  const scroller = h('div', { class: 'zoom-scroll' }, img);
  const close = () => { wrap.remove(); document.removeEventListener('keydown', onKey, true); };
  const go = async d => {
    i = (i + d + ids.length) % ids.length;
    zoomed = false;
    scroller.classList.remove('zoomed');
    img.src = (await photoURL(ids[i], 'full')) || '';
    const meta = await getPhoto(ids[i]);
    cap.textContent = [card ? photoName(card, ids[i]) : '', meta?.caption, ids.length > 1 ? `${i + 1} / ${ids.length}` : ''].filter(Boolean).join(' · ');
  };
  scroller.addEventListener('click', e => {
    const rect = img.getBoundingClientRect();
    const fx = (e.clientX - rect.left) / rect.width, fy = (e.clientY - rect.top) / rect.height;
    zoomed = !zoomed;
    scroller.classList.toggle('zoomed', zoomed);
    if (zoomed) requestAnimationFrame(() => {
      scroller.scrollLeft = fx * scroller.scrollWidth - scroller.clientWidth / 2;
      scroller.scrollTop = fy * scroller.scrollHeight - scroller.clientHeight / 2;
    });
  });
  const onKey = e => {
    if (e.key === 'Escape') { e.stopPropagation(); close(); }
    if (e.key === 'ArrowRight') go(1);
    if (e.key === 'ArrowLeft') go(-1);
  };
  const wrap = h('div', { class: 'zoom', role: 'dialog', 'aria-label': 'Foto-Vollbild' },
    scroller,
    h('div', { class: 'zoom-bar' },
      ids.length > 1 ? iconBtn('back', 'Vorheriges Foto', () => go(-1)) : h('span'),
      cap,
      h('div', { class: 'zoom-right' },
        card ? iconBtn('upload', 'Foto teilen oder speichern', async () => {
          const meta = await getPhoto(ids[i]);
          if (meta?.blob) shareFile(meta.blob, `${safeFileName(photoName(card, ids[i]))}.jpg`).catch(() => {});
        }) : null,
        ids.length > 1 ? iconBtn('chevron', 'Nächstes Foto', () => go(1)) : null,
        iconBtn('close', 'Schließen', close))));
  wrap._close = close;
  document.body.appendChild(wrap);
  document.addEventListener('keydown', onKey, true);
  go(0);
}

export function closeZoom() {
  const z = document.querySelector('.zoom');
  if (z) { z._close?.(); return true; }
  return false;
}

/**
 * Komplette Kartenansicht.
 * opts: revealed, photoIndex, hintLevel, typed {value, result, best}, speechLang
 */
export function cardFace(card, opts = {}) {
  const { revealed = false, photoIndex = 0, hintLevel = 0, typed = null, speechLang } = opts;
  const isPlant = card.type === 'plant';
  const ids = card.photoIds || [];
  const showPhotos = ids.length && (isPlant || card.photosOnFront || revealed);
  const parts = [];

  if (showPhotos) parts.push(photoStage(card, photoIndex, { onChange: opts.onPhotoChange, single: !!opts.singlePhoto && !revealed, named: revealed }).el);

  if (isPlant) {
    const q = (card.front || '').trim() || (ids.length ? 'Welche Pflanze ist das?' : 'Welche Pflanze hat diese Merkmale?');
    parts.push(h('p', { class: 'prompt' }, q));
    if (!ids.length && !card.front && card.plant?.features && !revealed) parts.push(h('p', { class: 'front-text pre' }, card.plant.features));
  } else {
    parts.push(h('div', { class: 'front-text pre' }, card.front || '(leere Vorderseite)'));
  }

  if (hintLevel > 0 && !revealed) {
    parts.push(h('div', { class: 'hints' }, hintText(card, hintLevel).map(t => h('div', { class: 'hint' }, icon('bulb', 16), h('span', null, t)))));
  }

  if (revealed && typed) {
    const cls = typed.result === 'correct' ? 'ok' : typed.result === 'close' ? 'close' : 'bad';
    const label = typed.result === 'correct' ? 'Richtig' : typed.result === 'close' ? 'Fast richtig' : 'Leider falsch';
    parts.push(h('div', { class: 'typed ' + cls },
      h('strong', null, label), h('span', null, ` · Deine Antwort: „${typed.value || '—'}“`)));
  }

  if (revealed) {
    parts.push(h('hr', { class: 'answer-rule', id: 'answer' }));
    if (isPlant) parts.push(herbariumLabel(card, { speechLang }));
    else {
      parts.push(h('div', { class: 'back-text pre' }, card.back || '(leere Rückseite)'));
      if (card.title) parts.push(h('p', { class: 'muted small' }, 'Kartenname: ' + card.title));
    }
  }
  return h('article', { class: 'card-face' + (revealed ? ' revealed' : '') }, parts);
}

export { displayName };
