// Sprachmodell im Hintergrund-Thread: laden (einmaliger Download, danach aus dem Speicher), Text erzeugen.
// Nachrichten: { id, cmd: 'status'|'load'|'generate'|'abort'|'clear', ... } → { id, type: 'progress'|'token'|'done'|'error', ... }
//
// Transformers.js (Hugging Face) wird beim ersten Gebrauch von jsDelivr geladen und – wie das Modell und die
// WebAssembly-Laufzeit – im App-Speicher (Cache „transformers-cache“) abgelegt, damit es danach ohne Netz startet.
const TJS_URL = 'https://cdn.jsdelivr.net/npm/@huggingface/transformers@4.3.2/dist/transformers.min.js';
const CACHE_KEY = 'transformers-cache';
let T = null;   // geladene Bibliothek
let env, AutoTokenizer, AutoModelForCausalLM, TextStreamer, ModelRegistry, stopper;

async function lib() {
  if (T) return T;
  let code;
  try {
    const cache = await caches.open(CACHE_KEY);
    let res = await cache.match(TJS_URL);
    if (!res) {
      res = await fetch(TJS_URL, { credentials: 'omit' });
      if (!res.ok) throw new Error('HTTP ' + res.status);
      await cache.put(TJS_URL, res.clone());
    }
    code = await res.blob();
  } catch {
    throw new Error('Die KI-Laufzeit konnte nicht geladen werden – keine Internetverbindung?');
  }
  const url = URL.createObjectURL(new Blob([code], { type: 'text/javascript' }));
  try { T = await import(url); } finally { URL.revokeObjectURL(url); }
  ({ env, AutoTokenizer, AutoModelForCausalLM, TextStreamer, ModelRegistry } = T);
  stopper = new T.InterruptableStoppingCriteria();
  env.allowLocalModels = false;
  env.cacheKey = CACHE_KEY;
  env.backends.onnx.wasm.numThreads = self.crossOriginIsolated ? Math.max(1, Math.min(4, (navigator.hardwareConcurrency || 2) - 1)) : 1;
  return T;
}

let current = null;          // { key, tokenizer, model }
let loading = null;          // Promise beim Laden
let backend = null;          // { device, dtype }

/** Grafikkarte (WebGPU) wenn möglich – viel schneller –, sonst Prozessor (WebAssembly). */
async function pickBackend() {
  if (backend) return backend;
  await lib();
  try {
    const a = await navigator.gpu?.requestAdapter?.({ powerPreference: 'high-performance' });
    if (a) return (backend = { device: 'webgpu', dtype: a.features.has('shader-f16') ? 'q4f16' : 'q4' });
  } catch { /* kein WebGPU */ }
  // Nur Prozessor: die normale WebAssembly-Laufzeit (die Standard-Laufzeit mit WebGPU-Unterstützung
  // kennt nicht alle Rechenbausteine, die quantisierte Modelle auf dem Prozessor brauchen)
  const v = env.backends.onnx.versions?.web;
  if (v) {
    const cdn = `https://cdn.jsdelivr.net/npm/onnxruntime-web@${v}/dist/`;
    env.backends.onnx.wasm.wasmPaths = { mjs: cdn + 'ort-wasm-simd-threaded.mjs', wasm: cdn + 'ort-wasm-simd-threaded.wasm' };
  }
  return (backend = { device: 'wasm', dtype: 'q4' });
}

const keyOf = (m, b) => `${m.id}|${b.dtype}|${b.device}`;

async function load(m, reply) {
  const b = await pickBackend();
  if (current?.key === keyOf(m, b)) return current;
  if (loading) return loading;
  const files = new Map();
  const progress_callback = d => {
    if (d.status !== 'progress' || !d.total) return;
    files.set(d.file, { loaded: d.loaded, total: d.total });
    let loaded = 0, total = 0;
    for (const f of files.values()) { loaded += f.loaded; total += f.total; }
    reply({ type: 'progress', loaded, total, file: d.file });
  };
  loading = (async () => {
    current = null;
    const tokenizer = await AutoTokenizer.from_pretrained(m.id, { progress_callback });
    const model = await AutoModelForCausalLM.from_pretrained(m.id, { dtype: b.dtype, device: b.device, progress_callback });
    current = { key: keyOf(m, b), tokenizer, model };
    return current;
  })();
  try { return await loading; } finally { loading = null; }
}

async function generate({ model: m, messages, maxTokens = 300 }, reply) {
  const { tokenizer, model } = await load(m, reply);
  const inputs = tokenizer.apply_chat_template(messages, { add_generation_prompt: true, return_dict: true, enable_thinking: false });
  const inTokens = inputs.input_ids.dims[1];
  let n = 0;
  const streamer = new TextStreamer(tokenizer, {
    skip_prompt: true, skip_special_tokens: true,
    callback_function: () => {},
    token_callback_function: () => { n++; reply({ type: 'token', n, max: maxTokens }); },
  });
  stopper.reset();
  const t0 = performance.now();
  const out = await model.generate({ ...inputs, max_new_tokens: maxTokens, do_sample: false, repetition_penalty: 1.05, streamer, stopping_criteria: stopper });
  const gen = out.slice(null, [inTokens, null]);
  const text = tokenizer.batch_decode(gen, { skip_special_tokens: true })[0];
  return { text, inTokens, outTokens: gen.dims[1], ms: Math.round(performance.now() - t0) };
}

self.onmessage = async e => {
  const { id, cmd } = e.data;
  const reply = msg => self.postMessage({ id, ...msg });
  try {
    let result;
    if (cmd === 'status') {
      const b = await pickBackend();
      const r = await ModelRegistry.is_cached_files(e.data.model.id, { dtype: b.dtype, device: b.device, include_processor: false });
      result = { cached: r.allCached, files: r.files, loaded: current?.key === keyOf(e.data.model, b), ...b };
    } else if (cmd === 'load') {
      await load(e.data.model, reply);
      result = { loaded: true };
    } else if (cmd === 'generate') {
      result = await generate(e.data, reply);
    } else if (cmd === 'abort') {
      stopper?.interrupt();
      result = { aborted: true };
    } else if (cmd === 'clear') {
      current = null;
      result = { deleted: await caches.delete(CACHE_KEY) };
    }
    reply({ type: 'done', result });
  } catch (err) {
    reply({ type: 'error', message: err?.message || String(err) });
  }
};
