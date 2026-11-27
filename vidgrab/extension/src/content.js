/*
 * VidGrab – Content-Script.
 * Sucht im DOM nach <video>/<audio>-Quellen, direkten Video-Links und
 * og:video-Metadaten und meldet neue Funde an das Hintergrundskript.
 * Außerdem öffnet es auf Wunsch des Popups einen intent://-Link im Tab.
 */
(() => {
  'use strict';
  if (window.__vidgrabLoaded) return;
  window.__vidgrabLoaded = true;

  const V = globalThis.VidGrab;
  const isTop = window === window.top;
  const reported = new Set();
  let timer = null;

  function absolute(raw) {
    try {
      return new URL(raw, document.baseURI).href;
    } catch (e) {
      return null;
    }
  }

  function collect() {
    timer = null;
    const found = [];
    const add = (raw, tag) => {
      if (!raw || reported.size > 2000) return;
      const url = V.normalizeUrl(absolute(String(raw).trim()) || ''); // blob:, data: usw. fallen hier raus
      if (!url || reported.has(url) || !V.classifyDom(url, tag)) return;
      reported.add(url);
      found.push({ url, tag });
    };

    for (const el of document.querySelectorAll('video, audio')) {
      const tag = el.localName;
      add(el.currentSrc, tag);
      add(el.getAttribute('src'), tag);
      for (const s of el.querySelectorAll('source[src]')) add(s.getAttribute('src'), tag);
    }
    for (const a of document.querySelectorAll('a[href]')) add(a.getAttribute('href'), 'a');
    for (const m of document.querySelectorAll(
      'meta[property^="og:video"], meta[name^="twitter:player:stream"], meta[itemprop="contentUrl"], link[rel="video_src"]'
    )) {
      add(m.getAttribute('content') || m.getAttribute('href'), 'meta');
    }

    if (found.length) {
      browser.runtime
        .sendMessage({ type: 'dom-media', items: found, pageTitle: isTop ? document.title : '' })
        .catch(() => {});
    }
  }

  function schedule(delay) {
    if (!timer) timer = setTimeout(collect, delay || 800);
  }

  // Medien-Events blubbern nicht, lassen sich aber in der Capture-Phase abfangen.
  for (const ev of ['loadstart', 'loadedmetadata', 'play']) {
    document.addEventListener(ev, () => schedule(300), true);
  }

  const observer = new MutationObserver(() => schedule(1000));
  observer.observe(document.documentElement || document, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ['src', 'href'],
  });

  collect();
  setTimeout(collect, 2500);

  browser.runtime.onMessage.addListener((msg) => {
    if (!msg) return undefined;
    if (msg.type === 'vidgrab-rescan') {
      reported.clear();
      collect();
      return isTop ? Promise.resolve(true) : undefined;
    }
    if (msg.type === 'vidgrab-open' && isTop && typeof msg.url === 'string' && msg.url.startsWith('intent://')) {
      // Firefox für Android fängt intent://-Navigationen ab und startet die App.
      window.location.href = msg.url;
      return Promise.resolve(true);
    }
    return undefined;
  });
})();
