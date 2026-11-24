// IndexedDB-Speicher: Stapel, Karten, Fotos (als Blobs), Lernprotokoll, Einstellungen.
import { uid } from './util.js';
import { newSrs } from './srs.js';
import { mainName, DEFAULT_LIFE_FORMS, DEFAULT_USES, LEGACY_LIFE_FORMS, LEGACY_USES } from './plants.js';

const DB_NAME = 'florakarten';
const DB_VERSION = 1;
let dbPromise = null;

function openDb() {
  if (dbPromise) return dbPromise;
  dbPromise = new Promise((resolve, reject) => {
    const r = indexedDB.open(DB_NAME, DB_VERSION);
    r.onupgradeneeded = () => {
      const db = r.result;
      if (!db.objectStoreNames.contains('decks')) db.createObjectStore('decks', { keyPath: 'id' });
      if (!db.objectStoreNames.contains('cards')) {
        const s = db.createObjectStore('cards', { keyPath: 'id' });
        s.createIndex('deckId', 'deckId');
      }
      if (!db.objectStoreNames.contains('photos')) {
        const s = db.createObjectStore('photos', { keyPath: 'id' });
        s.createIndex('cardId', 'cardId');
      }
      if (!db.objectStoreNames.contains('revlog')) {
        const s = db.createObjectStore('revlog', { keyPath: 'id', autoIncrement: true });
        s.createIndex('time', 'time');
        s.createIndex('cardId', 'cardId');
      }
      if (!db.objectStoreNames.contains('meta')) db.createObjectStore('meta', { keyPath: 'key' });
    };
    r.onsuccess = () => resolve(r.result);
    r.onerror = () => reject(r.error);
    r.onblocked = () => reject(new Error('Datenbank ist blockiert. Bitte andere Tabs der App schließen.'));
  });
  return dbPromise;
}

function wrap(req) {
  return new Promise((resolve, reject) => {
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

let changeListener = null;
/** Wird nach jeder Änderung an Stapeln, Karten, Fotos oder Lernverlauf aufgerufen (z. B. für die Cloud-Sicherung). */
export function setChangeListener(fn) {
  changeListener = fn;
}

/** Führt fn innerhalb einer Transaktion aus. fn darf nur IDB-Requests nutzen (kein fremdes await). */
async function tx(stores, mode, fn) {
  const db = await openDb();
  const notify = mode === 'readwrite' && stores.some(s => s !== 'meta');
  return new Promise((resolve, reject) => {
    const t = db.transaction(stores, mode);
    let out;
    try {
      out = fn(t);
    } catch (e) {
      try { t.abort(); } catch { /* schon beendet */ }
      reject(e);
      return;
    }
    t.oncomplete = () => {
      resolve(out);
      if (notify && changeListener) { try { changeListener(); } catch { /* egal */ } }
    };
    t.onerror = () => reject(t.error);
    t.onabort = () => reject(t.error || new Error('Transaktion abgebrochen'));
  });
}

export const raw = {
  async get(store, key) {
    const db = await openDb();
    return wrap(db.transaction(store).objectStore(store).get(key));
  },
  async getAll(store) {
    const db = await openDb();
    return wrap(db.transaction(store).objectStore(store).getAll());
  },
  async getAllByIndex(store, index, value) {
    const db = await openDb();
    return wrap(db.transaction(store).objectStore(store).index(index).getAll(value));
  },
  async getRange(store, index, lower, upper) {
    const db = await openDb();
    const range = upper == null ? IDBKeyRange.lowerBound(lower) : IDBKeyRange.bound(lower, upper);
    return wrap(db.transaction(store).objectStore(store).index(index).getAll(range));
  },
  async count(store) {
    const db = await openDb();
    return wrap(db.transaction(store).objectStore(store).count());
  },
  put(store, obj) {
    return tx([store], 'readwrite', t => { t.objectStore(store).put(obj); }).then(() => obj);
  },
  putMany(store, list) {
    return tx([store], 'readwrite', t => { const s = t.objectStore(store); for (const o of list) s.put(o); });
  },
  delete(store, key) {
    return tx([store], 'readwrite', t => { t.objectStore(store).delete(key); });
  },
  clear(store) {
    return tx([store], 'readwrite', t => { t.objectStore(store).clear(); });
  },
};

// ---------- Einstellungen ----------
export const SETTINGS_DEFAULTS = {
  theme: 'system',
  dayStartHour: 4,
  learnAheadMin: 20,
  speechLang: 'de-DE',
  nameMode: 'de',           // Hauptname für Pflanzen: 'de' oder 'la'
  lifeForms: DEFAULT_LIFE_FORMS,  // Auswahlliste Lebensformen
  usesList: DEFAULT_USES,         // Auswahlliste Verwendungszwecke
  photoNameMode: '',              // Fotonamen: '' = wie Hauptname, 'de' oder 'la'
  renameOriginals: true,          // Android: Originalfotos in der Galerie umbenennen
};

export async function getMeta(key, fallback = null) {
  const r = await raw.get('meta', key);
  return r ? r.value : fallback;
}
export function setMeta(key, value) {
  return raw.put('meta', { key, value });
}
const sameList = (a, b) => Array.isArray(a) && a.length === b.length && a.every((x, i) => x === b[i]);

export async function getSettings() {
  const s = { ...SETTINGS_DEFAULTS, ...(await getMeta('settings', {})) };
  // Unveränderte alte Standardlisten auf die neuen Standardlisten umstellen
  if (sameList(s.lifeForms, LEGACY_LIFE_FORMS)) s.lifeForms = DEFAULT_LIFE_FORMS;
  if (sameList(s.usesList, LEGACY_USES)) s.usesList = DEFAULT_USES;
  return s;
}
export async function saveSettings(patch) {
  const s = { ...(await getSettings()), ...patch };
  await setMeta('settings', s);
  // Einstellungen und Auswahllisten gehören mit in die Cloud-Sicherung
  if (changeListener) { try { changeListener(); } catch { /* egal */ } }
  return s;
}

// ---------- Stapel ----------
export async function listDecks() {
  const decks = await raw.getAll('decks');
  return decks.sort((a, b) => a.name.localeCompare(b.name, 'de'));
}
export function getDeck(id) {
  return raw.get('decks', id);
}
export async function createDeck(name, extra = {}) {
  const deck = { id: uid(), name: name.trim() || 'Neuer Stapel', description: '', created: Date.now(), config: {}, ...extra };
  await raw.put('decks', deck);
  return deck;
}
export function saveDeck(deck) {
  return raw.put('decks', deck);
}
export async function deleteDeck(id) {
  const cards = await raw.getAllByIndex('cards', 'deckId', id);
  const photoIds = [];
  for (const c of cards) photoIds.push(...(c.photoIds || []));
  await tx(['decks', 'cards', 'photos'], 'readwrite', t => {
    t.objectStore('decks').delete(id);
    const cs = t.objectStore('cards');
    for (const c of cards) cs.delete(c.id);
    const ps = t.objectStore('photos');
    for (const p of photoIds) ps.delete(p);
  });
  return cards.length;
}

// ---------- Karten ----------
export function emptyCard(deckId, type = 'plant') {
  return {
    id: uid(),
    deckId,
    type,
    title: '',
    front: '',
    back: '',
    photosOnFront: true,
    plant: {
      deName: '', latinName: '', family: '', features: '', bloom: [], lifeForm: [],
      habitat: '', toxicity: '', usesList: [], uses: '', confusion: '', notes: '',
    },
    photoIds: [],
    tags: [],
    marked: false,
    suspended: false,
    buriedUntil: 0,
    created: Date.now(),
    modified: Date.now(),
    srs: newSrs(),
  };
}

export function getCard(id) {
  return raw.get('cards', id);
}
export function allCards() {
  return raw.getAll('cards');
}
export function cardsInDeck(deckId) {
  return raw.getAllByIndex('cards', 'deckId', deckId);
}
export function saveCard(card, { touch = true } = {}) {
  if (touch) card.modified = Date.now();
  return raw.put('cards', card);
}
export function saveCards(cards) {
  return raw.putMany('cards', cards);
}
export async function deleteCards(ids) {
  const cards = (await Promise.all(ids.map(id => raw.get('cards', id)))).filter(Boolean);
  await tx(['cards', 'photos'], 'readwrite', t => {
    const cs = t.objectStore('cards');
    const ps = t.objectStore('photos');
    for (const c of cards) {
      cs.delete(c.id);
      for (const p of c.photoIds || []) ps.delete(p);
    }
  });
}

/** Name, unter dem eine Karte in Listen erscheint: eigener Kartenname, sonst Pflanzenname, sonst Vorderseite. */
export function displayName(card) {
  return (card.title || '').trim()
    || (card.type === 'plant' ? mainName(card.plant) : '')
    || (card.front || '').split('\n')[0].slice(0, 80)
    || 'Unbenannte Karte';
}

/** Durchsuchbarer Text einer Karte. */
export function searchText(card) {
  const p = card.plant || {};
  return [card.title, card.front, card.back, p.deName, p.latinName, p.family, p.features, p.habitat,
    p.uses, p.confusion, p.notes, ...(p.lifeForm || []), ...(p.usesList || []), ...(card.tags || [])].filter(Boolean).join(' \n ');
}

// ---------- Fotos ----------
export async function addPhoto({ blob, thumb, w, h, caption = '', source = null }, cardId) {
  // source: Herkunft bei Bildern aus dem Internet { author, license, source, page }
  const p = { id: uid(), cardId, blob, thumb, w, h, caption, source, created: Date.now() };
  await raw.put('photos', p);
  return p;
}
export function getPhoto(id) {
  return raw.get('photos', id);
}
export function savePhoto(p) {
  return raw.put('photos', p);
}
export function deletePhoto(id) {
  return raw.delete('photos', id);
}
export function allPhotos() {
  return raw.getAll('photos');
}

// ---------- Lernprotokoll ----------
export function addLog(entry) {
  return tx(['revlog'], 'readwrite', t => { t.objectStore('revlog').add({ time: Date.now(), ...entry }); });
}
export function logsSince(ts) {
  return raw.getRange('revlog', 'time', ts);
}
export function allLogs() {
  return raw.getAll('revlog');
}
export async function deleteLastLog(cardId) {
  const logs = await raw.getAllByIndex('revlog', 'cardId', cardId);
  if (!logs.length) return;
  logs.sort((a, b) => b.time - a.time);
  await raw.delete('revlog', logs[0].id);
}

export async function wipeAll() {
  await tx(['decks', 'cards', 'photos', 'revlog', 'meta'], 'readwrite', t => {
    for (const s of ['decks', 'cards', 'photos', 'revlog', 'meta']) t.objectStore(s).clear();
  });
}

export { tx };
