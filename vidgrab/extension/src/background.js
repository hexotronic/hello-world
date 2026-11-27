/*
 * VidGrab – Hintergrundskript.
 * Beobachtet die Netzwerkantworten aller Tabs, erkennt Video-Dateien und
 * Streaming-Manifeste und hält pro Tab eine Liste für das Popup bereit.
 */
'use strict';

const V = globalThis.VidGrab;

const MAX_ITEMS_PER_TAB = 300;
const MAX_ANALYSES_PER_TAB = 40;
const ANALYZE_TIMEOUT_MS = 8000;
const MAX_MANIFEST_CHARS = 2000000;

/** @type {Map<number, TabState>} */
const tabs = new Map();
let settings = Object.assign({}, V.DEFAULT_SETTINGS);
let nextId = 1;

function newTabState(pageUrl) {
  return {
    pageUrl: pageUrl || '',
    pageTitle: '',
    items: new Map(), // normalisierte URL -> Eintrag
    variantOf: new Map(), // Varianten-URL -> Master-URL
    analyses: 0,
    updateTimer: null,
  };
}

function getTab(tabId) {
  let t = tabs.get(tabId);
  if (!t) {
    t = newTabState('');
    tabs.set(tabId, t);
  }
  return t;
}

function resetTab(tabId, pageUrl) {
  const old = tabs.get(tabId);
  if (old && old.updateTimer) clearTimeout(old.updateTimer);
  tabs.set(tabId, newTabState(pageUrl));
  scheduleUpdate(tabId);
}

// ---------------------------------------------------------------- Einstellungen

browser.storage.local.get(V.DEFAULT_SETTINGS).then((s) => {
  settings = Object.assign({}, V.DEFAULT_SETTINGS, s);
  for (const id of tabs.keys()) updateBadge(id);
});

browser.storage.onChanged.addListener((changes, area) => {
  if (area !== 'local') return;
  for (const key of Object.keys(changes)) settings[key] = changes[key].newValue;
  for (const id of tabs.keys()) updateBadge(id);
});

// ------------------------------------------------------------------- Einträge

function addItem(tabId, data) {
  const url = V.normalizeUrl(data.url);
  if (!url || !data.kind) return;
  const t = getTab(tabId);
  let item = t.items.get(url);
  let changed = false;

  if (!item) {
    if (t.items.size >= MAX_ITEMS_PER_TAB) return;
    const masterUrl = t.variantOf.get(url) || null;
    item = {
      id: nextId++,
      url,
      kind: data.kind,
      ext: data.ext || '',
      mime: data.mime || '',
      size: data.size || null,
      sources: [data.source],
      referer: data.referer || t.pageUrl || '',
      title: V.filenameFromUrl(url),
      firstSeen: Date.now(),
      analysis: null,
      isVariant: !!masterUrl,
      masterUrl,
    };
    t.items.set(url, item);
    changed = true;
    // Bekannte Varianten nicht erneut abrufen – der Master liefert schon alles.
    if ((item.kind === 'hls' || item.kind === 'dash') && !item.isVariant && settings.analyzeManifests) {
      analyze(tabId, t, item);
    }
  } else {
    if (!item.sources.includes(data.source)) {
      item.sources.push(data.source);
      changed = true;
    }
    // Netzwerkdaten sind genauer als DOM-Vermutungen
    if (data.source === 'net' && data.mime && item.mime !== data.mime) {
      item.mime = data.mime;
      if (item.kind !== data.kind && data.kind) item.kind = data.kind;
      changed = true;
      if ((item.kind === 'hls' || item.kind === 'dash') && !item.analysis && settings.analyzeManifests) {
        analyze(tabId, t, item);
      }
    }
    if (data.size && (!item.size || data.size > item.size)) {
      item.size = data.size;
      changed = true;
    }
    if (!item.referer && data.referer) item.referer = data.referer;
  }
  if (changed) scheduleUpdate(tabId);
}

async function analyze(tabId, t, item) {
  if (t.analyses >= MAX_ANALYSES_PER_TAB) return;
  t.analyses++;
  item.analysis = { pending: true };
  const ctrl = new AbortController();
  const timer = setTimeout(() => ctrl.abort(), ANALYZE_TIMEOUT_MS);
  try {
    const resp = await fetch(item.url, { credentials: 'include', signal: ctrl.signal });
    if (!resp.ok) throw new Error('HTTP ' + resp.status);
    const text = (await resp.text()).slice(0, MAX_MANIFEST_CHARS);
    const result = item.kind === 'hls' ? V.parseHls(text, resp.url || item.url) : V.parseDash(text);
    item.analysis = result || { failed: true };
    if (result && result.isMaster) {
      const children = result.variants.map((v) => v.url).concat(result.renditions.map((r) => r.url));
      for (const child of children) {
        const key = V.normalizeUrl(child);
        if (!key || key === item.url) continue;
        t.variantOf.set(key, item.url);
        const existing = t.items.get(key);
        if (existing) {
          existing.isVariant = true;
          existing.masterUrl = item.url;
        }
      }
    }
  } catch (e) {
    item.analysis = { failed: true };
  } finally {
    clearTimeout(timer);
  }
  if (tabs.get(tabId) === t) scheduleUpdate(tabId);
}

function snapshot(tabId) {
  const t = tabs.get(tabId);
  if (!t) return { pageUrl: '', pageTitle: '', items: [] };
  return {
    pageUrl: t.pageUrl,
    pageTitle: t.pageTitle,
    items: [...t.items.values()],
  };
}

// ------------------------------------------------------------- Badge & Popup

function scheduleUpdate(tabId) {
  const t = tabs.get(tabId);
  if (!t || t.updateTimer) return;
  t.updateTimer = setTimeout(() => {
    t.updateTimer = null;
    updateBadge(tabId);
    browser.runtime.sendMessage({ type: 'media-updated', tabId }).catch(() => {});
  }, 150);
}

function updateBadge(tabId) {
  const t = tabs.get(tabId);
  let count = 0;
  if (t) {
    for (const item of t.items.values()) if (!V.hiddenReason(item, settings)) count++;
  }
  const action = browser.browserAction;
  if (!action) return;
  try {
    action.setBadgeText({ tabId, text: count ? String(count) : '' }).catch(() => {});
    if (action.setBadgeBackgroundColor) action.setBadgeBackgroundColor({ tabId, color: '#e65100' }).catch(() => {});
  } catch (e) { /* Tab existiert nicht mehr */ }
}

// --------------------------------------------------------------- Netzwerk

function headerMap(headers) {
  const map = {};
  for (const h of headers || []) map[h.name.toLowerCase()] = h.value || '';
  return map;
}

browser.webRequest.onBeforeRequest.addListener(
  (details) => {
    if (details.tabId >= 0) resetTab(details.tabId, details.url);
  },
  { urls: ['<all_urls>'], types: ['main_frame'] }
);

browser.webRequest.onHeadersReceived.addListener(
  (details) => {
    if (details.tabId < 0) return; // z. B. unsere eigenen Manifest-Abrufe
    if (details.statusCode < 200 || details.statusCode >= 300) return;
    const h = headerMap(details.responseHeaders);
    const c = V.classify(details.url, h['content-type']);
    if (!c) return;
    const isManifest = c.kind === 'hls' || c.kind === 'dash';
    addItem(details.tabId, {
      url: details.url,
      kind: c.kind,
      ext: c.ext,
      mime: c.mime,
      size: isManifest ? null : V.parseSize(h['content-range'], h['content-length'], details.statusCode),
      source: 'net',
      referer: details.type === 'main_frame' ? '' : (details.documentUrl || details.originUrl || ''),
    });
  },
  {
    urls: ['<all_urls>'],
    types: ['main_frame', 'sub_frame', 'media', 'object', 'object_subrequest', 'xmlhttprequest', 'other'],
  },
  ['responseHeaders']
);

// ------------------------------------------------------------------- Tabs

browser.tabs.onRemoved.addListener((tabId) => {
  const t = tabs.get(tabId);
  if (t && t.updateTimer) clearTimeout(t.updateTimer);
  tabs.delete(tabId);
});

browser.tabs.onUpdated.addListener((tabId, changeInfo) => {
  if (changeInfo.title) {
    const t = tabs.get(tabId);
    if (t) t.pageTitle = changeInfo.title;
  }
});

// -------------------------------------------------------------- Nachrichten

async function downloadM3u(msg) {
  const text = V.buildM3u(msg);
  if (!browser.downloads || !browser.downloads.download) return { ok: false, text };
  const blobUrl = URL.createObjectURL(new Blob([text], { type: 'audio/x-mpegurl' }));
  try {
    await browser.downloads.download({
      url: blobUrl,
      filename: V.safeFilename(msg.title) + '.m3u',
      conflictAction: 'uniquify',
    });
    return { ok: true };
  } catch (e) {
    return { ok: false, text };
  } finally {
    setTimeout(() => URL.revokeObjectURL(blobUrl), 60000);
  }
}

browser.runtime.onMessage.addListener((msg, sender) => {
  if (!msg || typeof msg.type !== 'string') return undefined;
  switch (msg.type) {
    case 'dom-media': {
      if (!sender.tab || sender.tab.id < 0 || !Array.isArray(msg.items)) return undefined;
      const tabId = sender.tab.id;
      if (msg.pageTitle && sender.frameId === 0) getTab(tabId).pageTitle = String(msg.pageTitle);
      for (const m of msg.items.slice(0, 100)) {
        const c = m && typeof m.url === 'string' ? V.classifyDom(m.url, m.tag) : null;
        if (!c) continue;
        addItem(tabId, { url: m.url, kind: c.kind, ext: c.ext, mime: c.mime, source: 'dom', referer: sender.url || '' });
      }
      return undefined;
    }
    case 'get-media':
      return Promise.resolve(snapshot(msg.tabId));
    case 'clear': {
      const old = tabs.get(msg.tabId) || {};
      resetTab(msg.tabId, old.pageUrl || '');
      getTab(msg.tabId).pageTitle = old.pageTitle || '';
      return Promise.resolve(true);
    }
    case 'download-m3u':
      return downloadM3u(msg);
    default:
      return undefined;
  }
});
