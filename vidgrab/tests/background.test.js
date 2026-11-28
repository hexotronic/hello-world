'use strict';
/*
 * Testet das Hintergrundskript mit einer nachgebildeten WebExtension-API.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

function event() {
  const listeners = [];
  return {
    listeners,
    addListener: (fn) => listeners.push(fn),
    fire: (...args) => listeners.map((fn) => fn(...args)),
  };
}

const badges = {};
const removedKeys = [];
const sentMessages = [];
const fetched = [];
const browserMock = {
  storage: {
    local: {
      get: async (defaults) => Object.assign({}, defaults),
      remove: async (key) => {
        removedKeys.push(key);
      },
    },
    onChanged: event(),
  },
  webRequest: { onBeforeRequest: event(), onHeadersReceived: event() },
  tabs: { onRemoved: event(), onUpdated: event() },
  runtime: {
    onInstalled: event(),
    onMessage: event(),
    sendMessage: async (msg) => {
      sentMessages.push(msg);
    },
  },
  browserAction: {
    setBadgeText: async ({ tabId, text }) => {
      badges[tabId] = text;
    },
    setBadgeBackgroundColor: async () => {},
  },
};

const MASTER = `#EXTM3U
#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080
hi/index.m3u8
#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=640x360
lo/index.m3u8
`;

globalThis.browser = browserMock;
globalThis.fetch = async (url) => {
  fetched.push(url);
  return { ok: true, status: 200, url, text: async () => MASTER };
};
require(path.join(__dirname, '../extension/src/common/media.js'));
require(path.join(__dirname, '../extension/src/background.js'));

const wr = browserMock.webRequest;
const tick = (ms = 200) => new Promise((r) => setTimeout(r, ms));

function response(tabId, url, headers, extra) {
  wr.onHeadersReceived.fire(Object.assign({
    tabId,
    url,
    type: 'media',
    statusCode: 200,
    documentUrl: 'https://seite.test/video/1',
    responseHeaders: Object.entries(headers).map(([name, value]) => ({ name, value })),
  }, extra));
}

async function getMedia(tabId) {
  const [res] = browserMock.runtime.onMessage.fire({ type: 'get-media', tabId }, {});
  return res;
}

test('erkennt MP4 mit Größe aus Content-Range und ignoriert Segmente', async () => {
  wr.onBeforeRequest.fire({ tabId: 1, url: 'https://seite.test/video/1', type: 'main_frame' });
  response(1, 'https://cdn.test/film.mp4', { 'Content-Type': 'video/mp4', 'Content-Range': 'bytes 0-99/734003200' }, { statusCode: 206 });
  response(1, 'https://cdn.test/seg-1.ts', { 'Content-Type': 'video/mp2t' });
  response(1, 'https://cdn.test/logo.png', { 'Content-Type': 'image/png' });
  response(-1, 'https://cdn.test/hintergrund.mp4', { 'Content-Type': 'video/mp4' });
  response(1, 'https://cdn.test/kaputt.mp4', { 'Content-Type': 'video/mp4' }, { statusCode: 404 });
  await tick();

  const data = await getMedia(1);
  assert.equal(data.items.length, 1);
  const [item] = data.items;
  assert.equal(item.url, 'https://cdn.test/film.mp4');
  assert.equal(item.kind, 'video');
  assert.equal(item.size, 734003200);
  assert.equal(item.referer, 'https://seite.test/video/1');
  assert.equal(badges[1], '1');
  assert.ok(sentMessages.some((m) => m.type === 'media-updated' && m.tabId === 1));
});

test('analysiert HLS-Master und blendet Varianten aus', async () => {
  wr.onBeforeRequest.fire({ tabId: 2, url: 'https://seite.test/live', type: 'main_frame' });
  response(2, 'https://cdn.test/stream/master.m3u8', { 'Content-Type': 'application/vnd.apple.mpegurl' }, { type: 'xmlhttprequest' });
  await tick();
  response(2, 'https://cdn.test/stream/hi/index.m3u8', { 'Content-Type': 'application/vnd.apple.mpegurl' }, { type: 'xmlhttprequest' });
  await tick();

  assert.deepEqual(fetched, ['https://cdn.test/stream/master.m3u8']);
  const data = await getMedia(2);
  const master = data.items.find((i) => i.url.endsWith('master.m3u8'));
  const variant = data.items.find((i) => i.url.endsWith('hi/index.m3u8'));
  assert.equal(master.analysis.isMaster, true);
  assert.deepEqual(master.analysis.variants.map((v) => v.label), ['1080p', '360p']);
  assert.equal(variant.isVariant, true);
  assert.equal(variant.masterUrl, master.url);
  // Variante wird ausgeblendet -> Badge zählt nur den Master
  assert.equal(badges[2], '1');
});

test('führt DOM-Funde mit Netzwerkdaten zusammen', async () => {
  wr.onBeforeRequest.fire({ tabId: 3, url: 'https://seite.test/a', type: 'main_frame' });
  browserMock.runtime.onMessage.fire(
    { type: 'dom-media', pageTitle: 'Mein Film', items: [{ url: 'https://cdn.test/v.webm#t=3', tag: 'video' }, { url: 'blob:https://x/1', tag: 'video' }] },
    { tab: { id: 3 }, frameId: 0, url: 'https://seite.test/a' }
  );
  response(3, 'https://cdn.test/v.webm', { 'Content-Type': 'video/webm', 'Content-Length': '5242880' });
  await tick();

  const data = await getMedia(3);
  assert.equal(data.pageTitle, 'Mein Film');
  assert.equal(data.items.length, 1);
  assert.deepEqual(data.items[0].sources.sort(), ['dom', 'net']);
  assert.equal(data.items[0].size, 5242880);
});

test('neue Seite leert die Liste, Tab schließen räumt auf', async () => {
  wr.onBeforeRequest.fire({ tabId: 1, url: 'https://seite.test/anders', type: 'main_frame' });
  await tick();
  assert.equal((await getMedia(1)).items.length, 0);
  assert.equal(badges[1], '');

  browserMock.tabs.onRemoved.fire(3);
  assert.equal((await getMedia(3)).items.length, 0);
});

test('Direktaufruf einer Videodatei als Hauptdokument', async () => {
  wr.onBeforeRequest.fire({ tabId: 4, url: 'https://cdn.test/direkt.mkv', type: 'main_frame' });
  response(4, 'https://cdn.test/direkt.mkv', { 'Content-Type': 'application/octet-stream', 'Content-Length': '99999999' }, { type: 'main_frame' });
  await tick();
  const [item] = (await getMedia(4)).items;
  assert.equal(item.kind, 'video');
  assert.equal(item.size, 99999999);
});

test('Knopf am Video: best-media und Start über den Haupt-Frame', async () => {
  const tabMessages = [];
  browserMock.tabs.sendMessage = async (tabId, msg, opts) => {
    tabMessages.push({ tabId, msg, opts });
    return true;
  };
  wr.onBeforeRequest.fire({ tabId: 5, url: 'https://seite.test/serie', type: 'main_frame' });
  browserMock.tabs.onUpdated.fire(5, { title: 'Folge 1' });
  response(5, 'https://ads.test/preroll.mp4', { 'Content-Type': 'video/mp4', 'Content-Length': '400000' });
  response(5, 'https://cdn.test/folge1.mp4', { 'Content-Type': 'video/mp4', 'Content-Length': '300000000' });
  await tick();

  const sender = { tab: { id: 5, title: 'Folge 1' }, frameId: 3, url: 'https://player.test/embed' };
  const [best] = browserMock.runtime.onMessage.fire({ type: 'best-media' }, sender);
  assert.equal((await best).item.url, 'https://cdn.test/folge1.mp4');

  const [opened] = browserMock.runtime.onMessage.fire({ type: 'open-in-top', url: 'https://cdn.test/folge1.mp4', kind: 'video' }, sender);
  assert.equal(await opened, true);
  assert.equal(tabMessages.length, 1);
  assert.equal(tabMessages[0].tabId, 5);
  assert.deepEqual(tabMessages[0].opts, { frameId: 0 });
  assert.equal(
    tabMessages[0].msg.url,
    'intent://cdn.test/folge1.mp4#Intent;scheme=https;action=android.intent.action.VIEW;type=video/*;package=org.videolan.vlc;S.title=Folge%201;end'
  );
});

test('Update von 1.2.x setzt die alte 1080p-Voreinstellung zurück', () => {
  browserMock.runtime.onInstalled.fire({ reason: 'update', previousVersion: '1.2.1' });
  assert.deepEqual(removedKeys, ['maxHeight']);
  browserMock.runtime.onInstalled.fire({ reason: 'update', previousVersion: '1.3.0' });
  browserMock.runtime.onInstalled.fire({ reason: 'install' });
  assert.deepEqual(removedKeys, ['maxHeight']);
});
