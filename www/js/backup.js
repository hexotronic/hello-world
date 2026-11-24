// Sicherung (Export/Import als JSON inkl. Fotos) und Dateispeichern – im Browser und in der Android-App.
import {
  listDecks, getDeck, allCards, cardsInDeck, getPhoto, allLogs, raw, tx, wipeAll, setMeta, getSettings,
  createDeck, emptyCard, saveCards,
} from './db.js';
import { toast } from './ui.js';
import { forgetAll } from './images.js';
import { isNative, Filesystem, Share } from './native.js';

function blobToDataURL(blob) {
  return new Promise((resolve, reject) => {
    const r = new FileReader();
    r.onload = () => resolve(r.result);
    r.onerror = () => reject(r.error);
    r.readAsDataURL(blob);
  });
}

function dataURLToBlob(url) {
  const [head, b64] = String(url).split(',');
  const mime = /data:([^;]+)/.exec(head)?.[1] || 'image/jpeg';
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new Blob([bytes], { type: mime });
}

/**
 * Erzeugt die Sicherung Stück für Stück (Kopf, dann Foto für Foto), damit auch große Sammlungen
 * nicht auf einmal im Speicher liegen. onChunk wird mit jedem Text-Stück aufgerufen.
 */
async function writeBackup(onChunk, { deckId } = {}) {
  const deck = deckId ? await getDeck(deckId) : null;
  const decks = deck ? [deck] : await listDecks();
  const cards = deck ? await cardsInDeck(deckId) : await allCards();
  const logs = (await allLogs()).filter(l => !deck || l.deckId === deckId).map(({ id, ...rest }) => rest);
  const ids = [...new Set(cards.flatMap(c => c.photoIds || []))];
  await onChunk('{"app":"florakarten","version":1,"exported":' + Date.now() +
    ',"decks":' + JSON.stringify(decks) +
    ',"cards":' + JSON.stringify(cards) +
    ',"revlog":' + JSON.stringify(logs) +
    ',"settings":' + JSON.stringify(deck ? null : await getSettings()) +
    ',"photos":[');
  let first = true;
  let photos = 0;
  for (const id of ids) {
    const p = await getPhoto(id);
    if (!p) continue;
    const { blob, thumb, ...meta } = p;
    const rec = { ...meta, data: await blobToDataURL(blob), thumbData: thumb ? await blobToDataURL(thumb) : null };
    await onChunk((first ? '' : ',') + JSON.stringify(rec));
    first = false;
    photos++;
  }
  await onChunk(']}');
  return { deck, cards: cards.length, photos };
}

/** Sicherung als Blob (Browser). */
export async function buildBackupBlob(opts) {
  const parts = [];
  const info = await writeBackup(chunk => { parts.push(new Blob([chunk])); }, opts);
  return { blob: new Blob(parts, { type: 'application/json' }), ...info };
}

/** Sicherung als Datei im App-Cache (Android). Liefert { path, ... }. */
export async function writeBackupToCache(filename, opts) {
  let firstChunk = true;
  const info = await writeBackup(async chunk => {
    if (firstChunk) await Filesystem.writeFile({ path: filename, data: chunk, directory: 'CACHE', encoding: 'utf8' });
    else await Filesystem.appendFile({ path: filename, data: chunk, directory: 'CACHE', encoding: 'utf8' });
    firstChunk = false;
  }, opts);
  const { uri } = await Filesystem.getUri({ path: filename, directory: 'CACHE' });
  return { path: uri, ...info };
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.rel = 'noopener';
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 60000);
}

function stamp() {
  const d = new Date();
  const p = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

function slug(s) {
  return String(s).toLowerCase().replace(/ä/g, 'ae').replace(/ö/g, 'oe').replace(/ü/g, 'ue').replace(/ß/g, 'ss')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) || 'stapel';
}

/**
 * Sicherung als Datei weitergeben: in der Android-App über den Teilen-Dialog
 * (dort z. B. „In Drive speichern“), auf Handys im Browser ebenfalls über Teilen, sonst als Download.
 */
export async function exportBackup({ deckId } = {}) {
  const stop = toast('Sicherung wird erstellt …', { duration: 120000 });
  try {
    const deck = deckId ? await getDeck(deckId) : null;
    const name = deck ? `florakarten-${slug(deck.name)}-${stamp()}.json` : `florakarten-sicherung-${stamp()}.json`;
    let info;
    if (isNative && Filesystem) {
      info = await writeBackupToCache(name, { deckId });
      stop();
      if (Share) await Share.share({ title: name, files: [info.path], dialogTitle: 'Sicherung speichern oder senden' });
    } else {
      const built = await buildBackupBlob({ deckId });
      info = built;
      stop();
      const file = new File([built.blob], name, { type: 'application/json' });
      let shared = false;
      if (/Android|iPhone|iPad/i.test(navigator.userAgent) && navigator.canShare?.({ files: [file] })) {
        try { await navigator.share({ files: [file], title: name }); shared = true; } catch (e) { if (e?.name === 'AbortError') shared = true; }
      }
      if (!shared) downloadBlob(built.blob, name);
    }
    if (!deck) await setMeta('lastBackup', Date.now());
    toast(`Sicherung erstellt: ${info.cards} Karten, ${info.photos} Fotos`);
  } catch (err) {
    stop();
    if (!/cancel/i.test(err?.message || '')) toast('Sicherung fehlgeschlagen: ' + (err?.message || err));
  }
}

/** Sicherung einspielen (Datei oder Blob). mode: 'merge' (zusammenführen) oder 'replace' (alles ersetzen). */
export async function importBackup(file, mode = 'merge') {
  const data = JSON.parse(await file.text());
  if (data?.app !== 'florakarten' || !Array.isArray(data.cards)) throw new Error('Diese Datei ist keine Florakarten-Sicherung.');
  if (mode === 'replace') {
    await wipeAll();
    forgetAll();
    await setMeta('seeded', true);
  }
  const existingCards = new Map((await allCards()).map(c => [c.id, c]));
  const cards = data.cards.filter(c => {
    const ex = existingCards.get(c.id);
    return !ex || (c.modified || 0) >= (ex.modified || 0);
  });
  const photos = (data.photos || []).map(({ data: d, thumbData, ...meta }) => ({
    ...meta, blob: dataURLToBlob(d), thumb: thumbData ? dataURLToBlob(thumbData) : null,
  }));
  let logs = data.revlog || [];
  if (mode === 'merge') {
    const seen = new Set((await allLogs()).map(l => `${l.cardId}:${l.time}`));
    logs = logs.filter(l => !seen.has(`${l.cardId}:${l.time}`));
  }
  await tx(['decks', 'cards', 'photos', 'revlog'], 'readwrite', t => {
    for (const d of data.decks || []) t.objectStore('decks').put(d);
    for (const c of cards) t.objectStore('cards').put(c);
    for (const p of photos) t.objectStore('photos').put(p);
    const rs = t.objectStore('revlog');
    for (const { id, ...l } of logs) rs.add(l);
  });
  if (mode === 'replace' && data.settings) await setMeta('settings', data.settings);
  return { decks: (data.decks || []).length, cards: cards.length, photos: photos.length };
}

/**
 * Text-Import: eine Karte pro Zeile, getrennt durch Tab, Semikolon oder Komma.
 * Standard: Vorderseite;Rückseite[;Schlagwörter]
 * Pflanze: Deutscher Name;Botanischer Name;Familie;Merkmale[;Schlagwörter]
 */
export function parseTextImport(text, type) {
  const lines = String(text).split(/\r?\n/).map(l => l.trim()).filter(l => l && !l.startsWith('#'));
  if (!lines.length) return [];
  const delim = lines[0].includes('\t') ? '\t' : lines[0].includes(';') ? ';' : ',';
  return lines.map(l => l.split(delim).map(s => s.trim().replace(/^"(.*)"$/, '$1')))
    .filter(cols => cols[0])
    .map(cols => {
      const tagsCol = type === 'plant' ? cols[4] : cols[2];
      const tags = tagsCol ? tagsCol.split(/[\s,]+/).filter(Boolean).map(t => t.toLowerCase()) : [];
      return type === 'plant'
        ? { deName: cols[0], latinName: cols[1] || '', family: cols[2] || '', features: cols[3] || '', tags }
        : { front: cols[0], back: cols[1] || '', tags };
    });
}

export async function importTextCards(rows, deckId, type) {
  let t = Date.now();
  const cards = rows.map(r => {
    const c = emptyCard(deckId, type);
    c.created = t++;
    c.tags = r.tags || [];
    if (type === 'plant') Object.assign(c.plant, { deName: r.deName, latinName: r.latinName, family: r.family, features: r.features });
    else { c.front = r.front; c.back = r.back; }
    return c;
  });
  await saveCards(cards);
  return cards.length;
}

export { createDeck };
