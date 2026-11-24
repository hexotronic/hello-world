// Cloud-Sicherung: eine selbst gewählte Datei (z. B. im eigenen Google Drive) wird automatisch aktuell gehalten.
// Funktioniert in der Android-App über den Android-Dialog „Speichern unter“; kein Google-Konto in der App nötig.
import { getMeta, setMeta, setChangeListener } from './db.js';
import { isNative, CloudFile, Filesystem, readNativeFile } from './native.js';
import { writeBackupToCache, importBackup } from './backup.js';

const KEY = 'cloud';
const FILE_NAME = 'florakarten-sicherung.json';
const AUTO_DELAY = 60 * 1000;

export const cloudSupported = isNative && !!CloudFile && !!Filesystem;

const PROVIDERS = [
  [/com\.google\.android\.apps\.docs/, 'Google Drive'],
  [/com\.microsoft\.skydrive/, 'OneDrive'],
  [/dropbox/i, 'Dropbox'],
  [/nextcloud/i, 'Nextcloud'],
  [/owncloud/i, 'ownCloud'],
  [/com\.android\.externalstorage/, 'Gerätespeicher'],
  [/com\.android\.providers\.downloads/, 'Downloads'],
  [/com\.android\.providers\.media/, 'Gerätespeicher'],
];

export function providerLabel(authority) {
  for (const [re, label] of PROVIDERS) if (re.test(authority || '')) return label;
  return authority ? 'Cloud-Speicher' : '';
}

export async function getCloud() {
  return { auto: true, uri: '', name: '', provider: '', lastSync: 0, lastError: '', ...(await getMeta(KEY, {})) };
}

const subs = new Set();
export function onCloudChange(fn) {
  subs.add(fn);
  return () => subs.delete(fn);
}

async function saveCloud(patch) {
  const c = { ...(await getCloud()), ...patch };
  await setMeta(KEY, c);
  subs.forEach(fn => { try { fn(c); } catch { /* egal */ } });
  return c;
}

/** Speicherort wählen. existing = bestehende Sicherungsdatei verbinden statt neue anzulegen. */
export async function chooseCloudTarget({ existing = false } = {}) {
  const old = await getCloud();
  const res = existing
    ? await CloudFile.pickExistingFile()
    : await CloudFile.pickSaveLocation({ fileName: FILE_NAME, mimeType: 'application/json' });
  if (old.uri && old.uri !== res.uri) CloudFile.forget({ uri: old.uri }).catch(() => {});
  return saveCloud({
    uri: res.uri,
    name: res.name || FILE_NAME,
    provider: res.provider || '',
    persistable: res.persistable !== false,
    lastSync: 0,
    lastError: '',
  });
}

let running = null;
let dirty = false;
let timer = null;

/** Sichert sofort in die gewählte Datei. */
export function cloudBackupNow() {
  if (running) return running;
  running = (async () => {
    const c = await getCloud();
    if (!c.uri) throw new Error('Noch kein Speicherort gewählt.');
    const wasDirty = dirty;
    dirty = false;
    try {
      const local = await writeBackupToCache('cloud-backup.json');
      const res = await CloudFile.writeFromFile({ uri: c.uri, path: local.path });
      await setMeta('cloudDirty', dirty);
      return saveCloud({ lastSync: Date.now(), lastError: '', size: res.bytes || 0, name: res.name || c.name, cards: local.cards, photos: local.photos });
    } catch (err) {
      dirty = dirty || wasDirty;
      await saveCloud({ lastError: err?.message || String(err) });
      throw err;
    }
  })().finally(() => { running = null; });
  return running;
}

/** Liest die Sicherung aus der gewählten Datei und spielt sie ein. */
export async function restoreFromCloud(mode = 'merge') {
  const c = await getCloud();
  if (!c.uri) throw new Error('Noch kein Speicherort gewählt.');
  const { path, bytes } = await CloudFile.readToFile({ uri: c.uri });
  if (!bytes) throw new Error('Die Datei in der Cloud ist leer.');
  const blob = await readNativeFile(path);
  return importBackup(blob, mode);
}

/** Prüft, ob in der Datei bereits eine Sicherung liegt. */
export async function cloudHasBackup() {
  const c = await getCloud();
  if (!c.uri) return false;
  try {
    const info = await CloudFile.info({ uri: c.uri });
    if (typeof info.size === 'number') return info.size > 20;
    const { bytes } = await CloudFile.readToFile({ uri: c.uri });
    return bytes > 20;
  } catch {
    return false;
  }
}

export async function setCloudAuto(auto) {
  return saveCloud({ auto });
}

export async function forgetCloud() {
  const c = await getCloud();
  if (c.uri) await CloudFile.forget({ uri: c.uri }).catch(() => {});
  return saveCloud({ uri: '', name: '', provider: '', lastSync: 0, lastError: '' });
}

/** Bald automatisch sichern (z. B. nach einer Lernrunde). */
export function cloudSoon(ms = 4000) {
  if (!cloudSupported || !dirty) return;
  clearTimeout(timer);
  timer = setTimeout(runAuto, ms);
}

async function runAuto() {
  clearTimeout(timer);
  if (!dirty) return;
  const c = await getCloud();
  if (!c.uri || !c.auto) return;
  try {
    await cloudBackupNow();
  } catch { /* Fehler steht in den Einstellungen und auf der Startseite */ }
}

/** Startet die automatische Sicherung nach Änderungen und beim Verlassen der App. */
export function initCloudAuto() {
  if (!cloudSupported) return;
  setChangeListener(() => {
    if (!dirty) { dirty = true; setMeta('cloudDirty', true).catch(() => {}); }
    clearTimeout(timer);
    timer = setTimeout(runAuto, AUTO_DELAY);
  });
  getMeta('cloudDirty').then(d => { if (d) { dirty = true; timer = setTimeout(runAuto, 5000); } });
  document.addEventListener('visibilitychange', () => {
    if (document.visibilityState === 'hidden' && dirty) runAuto();
  });
}

/** "vor 5 Min." usw. */
export function ago(ts) {
  if (!ts) return 'noch nie';
  const s = (Date.now() - ts) / 1000;
  if (s < 60) return 'gerade eben';
  if (s < 3600) return `vor ${Math.round(s / 60)} Min.`;
  if (s < 86400) return `vor ${Math.round(s / 3600)} Std.`;
  if (s < 172800) return 'gestern';
  return new Date(ts).toLocaleDateString('de-DE', { day: 'numeric', month: 'numeric', year: 'numeric' });
}
