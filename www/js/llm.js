// Kleines Sprachmodell direkt auf dem Gerät (läuft in einem Web Worker, siehe llm-worker.js).
// Das Modell wird einmal von Hugging Face geladen und danach im App-Speicher behalten.

/** Wählbare Modelle. sizeMB: ungefährer Download je nach Gerät (mit/ohne 16-Bit-Grafik). */
export const LLM_MODELS = [
  { key: 'large', id: 'LiquidAI/LFM2.5-1.2B-Instruct-ONNX', name: 'LFM2.5 1.2B', label: 'Genauer', sizeMB: { q4f16: 760, q4: 850 }, license: 'LFM Open License' },
  { key: 'small', id: 'LiquidAI/LFM2.5-350M-ONNX', name: 'LFM2.5 350M', label: 'Klein & schnell', sizeMB: { q4f16: 255, q4: 295 }, license: 'LFM Open License' },
];

/** Aktuell verwendetes Modell (wird über setLlmModel umgestellt). */
export const LLM_MODEL = { ...LLM_MODELS[0] };

export function setLlmModel(key) {
  const m = LLM_MODELS.find(x => x.key === key) || LLM_MODELS[0];
  if (m.id === LLM_MODEL.id) return;
  Object.assign(LLM_MODEL, m);
  // anderes Modell: Hintergrund-Thread neu starten (gibt den Speicher des alten frei)
  if (worker && !pending.size) { worker.terminate(); worker = null; }
}

/** Ungefähre Downloadgröße in MB für die gewählte Variante. */
export function llmSizeMB(dtype, model = LLM_MODEL) {
  return model.sizeMB[dtype] || Math.max(...Object.values(model.sizeMB));
}

let worker = null;
let seq = 0;
const pending = new Map();
let idleTimer = null;
const IDLE_MS = 3 * 60 * 1000;   // danach Speicher wieder freigeben

export function llmSupported() {
  return typeof Worker !== 'undefined' && typeof WebAssembly === 'object' && typeof caches !== 'undefined';
}

function getWorker() {
  if (worker) return worker;
  worker = new Worker(new URL('./llm-worker.js', import.meta.url), { type: 'module' });
  worker.onmessage = e => {
    const { id, type, ...rest } = e.data;
    const p = pending.get(id);
    if (!p) return;
    if (type === 'done') { pending.delete(id); p.resolve(rest.result); scheduleIdle(); }
    else if (type === 'error') { pending.delete(id); p.reject(new Error(rest.message)); scheduleIdle(); }
    else p.onEvent?.({ type, ...rest });
  };
  worker.onerror = e => {
    const err = new Error(e.message || 'Das Sprachmodell ist abgestürzt (zu wenig Arbeitsspeicher?).');
    for (const p of pending.values()) p.reject(err);
    pending.clear();
    worker?.terminate();
    worker = null;
  };
  return worker;
}

function scheduleIdle() {
  clearTimeout(idleTimer);
  idleTimer = setTimeout(() => {
    if (pending.size || !worker) return;
    worker.terminate();
    worker = null;
  }, IDLE_MS);
}

function call(cmd, data = {}, onEvent) {
  clearTimeout(idleTimer);
  const id = ++seq;
  return new Promise((resolve, reject) => {
    pending.set(id, { resolve, reject, onEvent });
    getWorker().postMessage({ id, cmd, model: LLM_MODEL, ...data });
  });
}

/** { cached: Modell liegt vollständig im Speicher, loaded: gerade geladen, device: 'webgpu'|'wasm', dtype } */
export async function llmStatus() {
  if (!llmSupported()) return { cached: false, loaded: false, unsupported: true };
  try { return await call('status'); } catch { return { cached: false, loaded: false }; }
}

/** Laufender Download (für die Anzeige in den Einstellungen, auch nach Seitenwechsel). */
export const llmDownloadState = { active: false, loaded: 0, error: '' };
const dlListeners = new Set();
export function onLlmDownload(fn) { dlListeners.add(fn); return () => dlListeners.delete(fn); }
const dlNotify = () => dlListeners.forEach(fn => { try { fn(llmDownloadState); } catch { /* egal */ } });
let dlPromise = null;

/** Modell herunterladen (bzw. aus dem Speicher laden). onProgress({ loaded, total }) */
export async function llmDownload(onProgress) {
  if (dlPromise) return dlPromise;
  try { await navigator.storage?.persist?.(); } catch { /* egal */ }
  Object.assign(llmDownloadState, { active: true, loaded: 0, error: '' });
  dlNotify();
  dlPromise = call('load', {}, ev => {
    if (ev.type !== 'progress') return;
    llmDownloadState.loaded = ev.loaded;
    onProgress?.(ev);
    dlNotify();
  });
  try {
    return await dlPromise;
  } catch (err) {
    llmDownloadState.error = err.message;
    throw err;
  } finally {
    dlPromise = null;
    llmDownloadState.active = false;
    dlNotify();
  }
}

/** Text erzeugen. onToken({ n, max }) für eine Fortschrittsanzeige. */
export function llmGenerate(messages, { maxTokens = 300, onToken } = {}) {
  return call('generate', { messages, maxTokens }, ev => { if (ev.type === 'token') onToken?.(ev); });
}

export function llmAbort() {
  if (worker) worker.postMessage({ id: ++seq, cmd: 'abort' });
}

/** Modell aus dem Speicher löschen. */
export async function llmDelete() {
  const r = await call('clear');
  worker?.terminate();
  worker = null;
  return r;
}
