/* VidGrab – Popup: zeigt die gefundenen Videos des aktuellen Tabs. */
'use strict';

const V = window.VidGrab;
const { h, toast, copyText, isAndroid, saveTextFile } = window.VidGrabUI;
const $ = (id) => document.getElementById(id);

const state = {
  tab: null,
  data: { pageUrl: '', pageTitle: '', items: [] },
  settings: Object.assign({}, V.DEFAULT_SETTINGS),
  android: false,
  showHidden: false,
  canShare: typeof navigator.share === 'function',
};

const OWN_PAGE = browser.runtime.getURL('');
// Über den „⋯“-Knopf am Video wird diese Seite als normaler Tab geöffnet.
const OPENED_AS_TAB = new URLSearchParams(location.search).has('tabId');

/** Popup schließen – bzw. als Tab: zurück zur Seite und diesen Tab schließen. */
async function closeSelf() {
  if (OPENED_AS_TAB) {
    try {
      const me = await browser.tabs.getCurrent();
      if (state.tab) await browser.tabs.update(state.tab.id, { active: true });
      if (me) await browser.tabs.remove(me.id);
      return;
    } catch (e) { /* Fallback unten */ }
  }
  window.close();
}

const ICONS = {
  play: 'M8 5v14l11-7z',
  apps: 'M4 4h6v6H4zM14 4h6v6h-6zM4 14h6v6H4zM14 14h6v6h-6z',
  copy: 'M9 9h11v11H9zM5 15H4V4h11v1',
  share: 'M18 8a3 3 0 1 0-2.8-4M6 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM18 22a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM8.6 13.5l6.8 4M15.4 6.5l-6.8 4',
  file: 'M14 3H6v18h12V7zM14 3v4h4M9 13h6M9 17h6',
  open: 'M14 4h6v6M20 4l-9 9M18 14v6H4V6h6',
};

function icon(name) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  path.setAttribute('d', ICONS[name]);
  if (name === 'play') {
    path.setAttribute('fill', 'currentColor');
  } else {
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
  }
  svg.append(path);
  return svg;
}

// ------------------------------------------------------------------ Daten

async function findTargetTab() {
  const param = new URLSearchParams(location.search).get('tabId');
  if (param) return browser.tabs.get(Number(param));
  const usable = (t) => t && t.id >= 0 && !(t.url || '').startsWith(OWN_PAGE);
  let list = await browser.tabs.query({ active: true, currentWindow: true }).catch(() => []);
  list = list.filter(usable);
  if (!list.length) {
    list = (await browser.tabs.query({}).catch(() => []))
      .filter(usable)
      .sort((a, b) => (b.active - a.active) || ((b.lastAccessed || 0) - (a.lastAccessed || 0)));
  }
  return list[0] || null;
}

async function load() {
  if (!state.tab) return;
  try {
    state.data = await browser.runtime.sendMessage({ type: 'get-media', tabId: state.tab.id });
  } catch (e) {
    state.data = { pageUrl: '', pageTitle: '', items: [] };
  }
  render();
}

let reloadTimer = null;
function scheduleReload() {
  clearTimeout(reloadTimer);
  reloadTimer = setTimeout(load, 120);
}

function mediaTitle() {
  return state.data.pageTitle || (state.tab && state.tab.title) || '';
}

// ---------------------------------------------------------------- Aktionen

function launchUrl(intent, mediaUrl) {
  const params = new URLSearchParams({
    u: intent,
    m: mediaUrl,
    t: mediaTitle(),
    p: V.playerName(V.effectivePlayer(state.settings)),
  });
  return browser.runtime.getURL('src/launch/launch.html') + '?' + params.toString();
}

async function openLaunchPage(intent, mediaUrl) {
  await browser.tabs.create({ url: launchUrl(intent, mediaUrl) });
  closeSelf();
}

async function play(mediaUrl, kind, pkg) {
  const intent = V.buildIntentUrl(mediaUrl, { pkg, mime: V.intentMime(kind), title: mediaTitle() });
  if (!intent) return;
  const method = state.settings.launchMethod;

  if (method === 'popup') {
    // Direkte Navigation aus dem Klick heraus (echte Nutzergeste)
    window.location.href = intent;
    return;
  }
  if (method === 'tab') {
    try {
      if (OPENED_AS_TAB) await browser.tabs.update(state.tab.id, { active: true }).catch(() => {});
      await browser.tabs.sendMessage(state.tab.id, { type: 'vidgrab-open', url: intent }, { frameId: 0 });
      closeSelf();
      return;
    } catch (e) {
      // Seite ohne Content-Script (z. B. about:-Seiten) – Startseite als Ausweg
    }
  }
  await openLaunchPage(intent, mediaUrl);
}

async function copy(url) {
  toast((await copyText(url)) ? 'Link kopiert' : 'Kopieren fehlgeschlagen');
}

async function share(url) {
  try {
    await navigator.share({ title: mediaTitle() || V.filenameFromUrl(url), url });
  } catch (e) {
    if (e && e.name !== 'AbortError') toast('Teilen nicht möglich');
  }
}

async function saveM3u(item, url) {
  const title = mediaTitle() || item.title;
  const res = await browser.runtime
    .sendMessage({
      type: 'download-m3u',
      url,
      title,
      referer: item.referer || state.data.pageUrl,
      userAgent: navigator.userAgent,
    })
    .catch(() => null);
  if (res && res.ok) {
    toast('Playlist gespeichert – mit VLC öffnen');
  } else {
    saveTextFile(res && res.text ? res.text : V.buildM3u({ url, title }), V.safeFilename(title) + '.m3u');
  }
}

async function rescan() {
  if (!state.tab) return;
  toast('Seite wird durchsucht …');
  try {
    await browser.tabs.sendMessage(state.tab.id, { type: 'vidgrab-rescan' });
  } catch (e) {
    try {
      await browser.tabs.executeScript(state.tab.id, { file: '/src/common/media.js', allFrames: true });
      await browser.tabs.executeScript(state.tab.id, { file: '/src/content.js', allFrames: true });
    } catch (err) {
      toast('Diese Seite darf nicht durchsucht werden');
    }
  }
  setTimeout(load, 500);
}

async function clearList() {
  if (!state.tab) return;
  await browser.runtime.sendMessage({ type: 'clear', tabId: state.tab.id }).catch(() => {});
  load();
}

// ---------------------------------------------------------------- Anzeige

function metaLine(item) {
  const a = item.analysis || {};
  const parts = [];
  try {
    parts.push(new URL(item.url).hostname);
  } catch (e) { /* ignorieren */ }
  if (item.size) parts.push(V.formatBytes(item.size));
  if (a.duration) parts.push(V.formatDuration(a.duration));
  const q = V.maxQuality(item);
  if (q) parts.push('bis ' + q + 'p');
  if (a.isMaster && a.variants.length) parts.push(a.variants.length + (a.variants.length === 1 ? ' Qualität' : ' Qualitäten'));
  if (a.pending) parts.push('wird analysiert …');
  const chosen = V.chooseStream(item, state.settings);
  if (chosen.limited) parts.push('startet mit ' + chosen.label);
  return parts.join(' · ');
}

function actionButtons(item) {
  const pkg = V.effectivePlayer(state.settings);
  const url = item.url;
  // Zum Abspielen ggf. eine Qualitätsstufe unterhalb des Maximums (statt z. B. 4K)
  const playUrl = V.chooseStream(item, state.settings).url;
  if (state.android) {
    const label = pkg ? 'In ' + V.playerName(pkg) + ' öffnen' : 'Mit App öffnen';
    return h('div', { class: 'buttons' },
      h('button', { class: 'btn primary', type: 'button', onclick: () => play(playUrl, item.kind, pkg) }, icon('play'), label),
      h('div', { class: 'row' },
        pkg ? h('button', { class: 'btn small', type: 'button', title: 'Mit anderer App öffnen', onclick: () => play(playUrl, item.kind, '') }, icon('apps'), 'Andere App') : null,
        h('button', { class: 'btn small', type: 'button', onclick: () => copy(url) }, icon('copy'), 'Kopieren'),
        state.canShare ? h('button', { class: 'btn small', type: 'button', onclick: () => share(url) }, icon('share'), 'Teilen') : null,
        h('button', { class: 'btn small', type: 'button', title: 'Playlist mit Referer speichern (für geschützte Streams)', onclick: () => saveM3u(item, playUrl) }, icon('file'), 'M3U')
      )
    );
  }
  return h('div', { class: 'buttons' },
    h('button', { class: 'btn primary', type: 'button', onclick: () => copy(url) }, icon('copy'), 'Link kopieren'),
    h('div', { class: 'row' },
      h('button', { class: 'btn small', type: 'button', title: 'Playlist speichern und mit VLC öffnen', onclick: () => saveM3u(item, playUrl) }, icon('file'), 'M3U für VLC'),
      h('button', { class: 'btn small', type: 'button', onclick: () => browser.tabs.create({ url }).then(closeSelf) }, icon('open'), 'Im Tab öffnen')
    )
  );
}

function qualityChips(item) {
  const a = item.analysis;
  if (!a || !a.isMaster || a.variants.length < 2) return null;
  // Varianten mit getrennter Tonspur hätten allein keinen Ton – nicht anbieten.
  const usable = a.variants.filter((v) => !v.separateAudio);
  if (usable.length < 2) return null;
  const pkg = V.effectivePlayer(state.settings);
  return h('div', { class: 'qualities' },
    h('span', { class: 'label' }, state.android ? 'Direkt starten:' : 'Variante kopieren:'),
    usable.slice(0, 12).map((v) =>
      h('button', {
        class: 'quality',
        type: 'button',
        title: v.url,
        onclick: () => (state.android ? play(v.url, 'hls', pkg) : copy(v.url)),
      }, v.label))
  );
}

function card(item) {
  const a = item.analysis || {};
  const chips = [h('span', { class: 'chip type' }, V.typeLabel(item))];
  if (a.live) chips.push(h('span', { class: 'chip' }, 'LIVE'));
  if (a.drm) chips.push(h('span', { class: 'chip warn' }, 'DRM'));
  if (item.isVariant) chips.push(h('span', { class: 'chip' }, 'Variante'));

  return h('article', { class: 'card' },
    h('div', { class: 'card-head' }, chips, h('h2', { class: 'ellipsis', title: item.title }, item.title)),
    h('p', { class: 'meta' }, metaLine(item)),
    a.drm ? h('p', { class: 'warning' }, 'DRM-geschützt – externe Player können diesen Stream vermutlich nicht abspielen.') : null,
    qualityChips(item),
    actionButtons(item),
    h('details', { class: 'url' }, h('summary', null, 'Adresse anzeigen'), h('code', null, item.url))
  );
}

function render() {
  const items = V.sortItems(state.data.items || []);
  const visible = items.filter((i) => !V.hiddenReason(i, state.settings));
  const hiddenCount = items.length - visible.length;
  const shown = state.showHidden ? items : visible;

  const list = $('list');
  list.replaceChildren(...shown.map(card));
  $('empty').hidden = shown.length > 0;
  $('page').textContent = mediaTitle() || (state.tab ? state.tab.url : '');

  $('hidden-note').hidden = hiddenCount === 0;
  $('hidden-text').textContent = state.showHidden
    ? ''
    : hiddenCount + (hiddenCount === 1 ? ' Eintrag ausgeblendet' : ' Einträge ausgeblendet') + ' (Varianten, Audio, sehr kleine Dateien).';
  $('toggle-hidden').textContent = state.showHidden ? 'Nur wichtige anzeigen' : 'Alle anzeigen';
}

// -------------------------------------------------------------------- Start

async function init() {
  state.android = await isAndroid();
  document.documentElement.classList.add(state.android ? 'android' : 'desktop');

  state.settings = Object.assign({}, V.DEFAULT_SETTINGS, await browser.storage.local.get(V.DEFAULT_SETTINGS));
  browser.storage.onChanged.addListener((changes, area) => {
    if (area !== 'local') return;
    for (const k of Object.keys(changes)) state.settings[k] = changes[k].newValue;
    render();
  });

  $('rescan').addEventListener('click', rescan);
  $('rescan2').addEventListener('click', rescan);
  $('clear').addEventListener('click', clearList);
  $('settings').addEventListener('click', () => browser.runtime.openOptionsPage().then(closeSelf));
  $('toggle-hidden').addEventListener('click', () => {
    state.showHidden = !state.showHidden;
    render();
  });

  browser.runtime.onMessage.addListener((msg) => {
    if (msg && msg.type === 'media-updated' && state.tab && msg.tabId === state.tab.id) scheduleReload();
  });

  state.tab = await findTargetTab();
  if (!state.tab) {
    $('page').textContent = 'Kein Tab gefunden';
    render();
    return;
  }
  await load();
}

init();
