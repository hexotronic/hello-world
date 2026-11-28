/*
 * VidGrab – Content-Script.
 * - Sucht im DOM nach <video>/<audio>-Quellen, direkten Video-Links und
 *   og:video-Metadaten und meldet neue Funde an das Hintergrundskript.
 * - Blendet auf Android einen Knopf „▶ VLC“ direkt über jedem Video ein.
 * - Öffnet auf Wunsch des Popups einen intent://-Link im Tab.
 */
(() => {
  'use strict';
  if (window.__vidgrabLoaded) return;
  window.__vidgrabLoaded = true;

  const V = globalThis.VidGrab;
  const isTop = window === window.top;
  const isAndroid = /Android/i.test(navigator.userAgent);
  const reported = new Set();
  let settings = Object.assign({}, V.DEFAULT_SETTINGS);
  let timer = null;

  try {
    browser.storage.local.get(V.DEFAULT_SETTINGS).then((s) => {
      settings = Object.assign({}, V.DEFAULT_SETTINGS, s);
      Overlay.refresh();
    }).catch(() => {});
    browser.storage.onChanged.addListener((changes, area) => {
      if (area !== 'local') return;
      for (const k of Object.keys(changes)) settings[k] = changes[k].newValue;
      Overlay.refresh();
    });
  } catch (e) { /* ohne Speicher: Standardeinstellungen */ }

  function absolute(raw) {
    try {
      return new URL(raw, document.baseURI).href;
    } catch (e) {
      return null;
    }
  }

  // ------------------------------------------------------------ DOM-Suche

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
      if (tag === 'video') Overlay.track(el);
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

  // ------------------------------------------------------- Knopf am Video

  const Overlay = (() => {
    const MIN_W = 160;
    const MIN_H = 90;
    const entries = new Map(); // video -> { host, wrap, mainLabel, msg, msgTimer }
    let rafPending = false;
    let interval = null;

    const enabled = () => isAndroid && settings.showOverlay !== false;

    function svgPlay() {
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      svg.setAttribute('viewBox', '0 0 24 24');
      svg.setAttribute('width', '14');
      svg.setAttribute('height', '14');
      svg.setAttribute('aria-hidden', 'true');
      const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
      p.setAttribute('d', 'M7 4v16l13-8z');
      p.setAttribute('fill', '#fff');
      svg.append(p);
      return svg;
    }

    // Nur CSSOM-Stile (element.style): funktioniert auch auf Seiten mit strenger CSP.
    function styleButton(btn, accent) {
      Object.assign(btn.style, {
        all: 'initial',
        boxSizing: 'border-box',
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '6px',
        height: '36px',
        minWidth: '36px',
        padding: '0 12px',
        borderRadius: '18px',
        background: accent ? 'rgba(216, 79, 0, 0.94)' : 'rgba(20, 20, 24, 0.78)',
        color: '#fff',
        font: '600 14px/1 system-ui, -apple-system, Roboto, sans-serif',
        cursor: 'pointer',
        boxShadow: '0 1px 6px rgba(0, 0, 0, 0.45)',
        userSelect: 'none',
        webkitTapHighlightColor: 'transparent',
      });
    }

    function create(video) {
      const host = document.createElement('vidgrab-button');
      Object.assign(host.style, {
        all: 'initial',
        position: 'fixed',
        top: '0px',
        left: '0px',
        zIndex: '2147483647',
        display: 'none',
      });
      const root = host.attachShadow({ mode: 'closed' });
      const wrap = document.createElement('div');
      Object.assign(wrap.style, { display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: '6px' });
      const row = document.createElement('div');
      Object.assign(row.style, { display: 'flex', gap: '6px' });

      const main = document.createElement('button');
      styleButton(main, true);
      main.title = 'Video in externer App öffnen';
      const mainLabel = document.createElement('span');
      main.append(svgPlay(), mainLabel);

      const more = document.createElement('button');
      styleButton(more, false);
      more.title = 'Alle gefundenen Videos anzeigen';
      more.textContent = '⋯';
      more.style.padding = '0 10px';
      more.style.fontSize = '18px';
      more.style.border = '1px solid rgba(255, 255, 255, 0.45)';

      const msg = document.createElement('div');
      Object.assign(msg.style, {
        display: 'none',
        maxWidth: '240px',
        padding: '8px 10px',
        borderRadius: '8px',
        background: 'rgba(20, 20, 24, 0.9)',
        color: '#fff',
        font: '13px/1.35 system-ui, -apple-system, Roboto, sans-serif',
        textAlign: 'right',
      });

      row.append(main, more);
      wrap.append(row, msg);
      root.append(wrap);

      // Die Seite (bzw. ihr Player) soll von unseren Tipps nichts mitbekommen.
      for (const type of ['pointerdown', 'pointerup', 'mousedown', 'mouseup', 'touchstart', 'touchend', 'click', 'dblclick']) {
        wrap.addEventListener(type, (e) => e.stopPropagation());
      }
      const entry = { video, host, mainLabel, msg, msgTimer: null };
      main.addEventListener('click', (e) => {
        e.preventDefault();
        openVideo(entry);
      });
      more.addEventListener('click', (e) => {
        e.preventDefault();
        browser.runtime.sendMessage({ type: 'open-list' }).catch(() => {});
      });
      return entry;
    }

    function flash(entry, text) {
      entry.msg.textContent = text;
      entry.msg.style.display = 'block';
      clearTimeout(entry.msgTimer);
      entry.msgTimer = setTimeout(() => (entry.msg.style.display = 'none'), 3500);
    }

    async function openVideo(entry) {
      const pkg = V.effectivePlayer(settings);
      let url = V.normalizeUrl(entry.video.currentSrc || entry.video.getAttribute('src') || '');
      let c = url ? V.classifyDom(url, 'video') : null;
      let title = isTop ? document.title : '';

      if (!c) {
        // Video läuft über blob:/MediaSource – dann den erkannten Stream des Tabs nehmen
        const res = await browser.runtime.sendMessage({ type: 'best-media' }).catch(() => null);
        if (!res || !res.item) {
          flash(entry, 'Noch kein Stream erkannt. Video kurz abspielen und nochmal tippen.');
          return;
        }
        url = V.chooseStream(res.item, settings).url; // z. B. 1080p statt 4K
        c = { kind: res.item.kind };
        title = title || res.pageTitle || '';
      }

      if (isTop) {
        const intent = V.buildIntentUrl(url, { pkg, mime: V.intentMime(c.kind), title });
        if (intent) window.location.href = intent;
      } else {
        // In iframes übernimmt der Haupt-Frame das Öffnen.
        const ok = await browser.runtime.sendMessage({ type: 'open-in-top', url, kind: c.kind }).catch(() => false);
        if (!ok) flash(entry, 'Konnte die App nicht starten.');
      }
      try {
        entry.video.pause();
      } catch (e) { /* egal */ }
    }

    function position(entry) {
      const { video, host } = entry;
      if (!enabled() || !video.isConnected) {
        host.style.display = 'none';
        return;
      }
      const fs = document.fullscreenElement;
      // Im Vollbild muss der Knopf im Vollbild-Element hängen, sonst ist er unsichtbar.
      const parent = fs && fs !== video && fs.contains(video) ? fs : document.documentElement;
      if (host.parentNode !== parent) parent.append(host);

      const r = video.getBoundingClientRect();
      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const visible = r.width >= MIN_W && r.height >= MIN_H && r.bottom > 40 && r.right > 40 && r.top < vh - 40 && r.left < vw - 40
        && (!fs || fs.contains(video));
      if (!visible) {
        host.style.display = 'none';
        return;
      }
      entry.mainLabel.textContent = pkg2label();
      host.style.display = 'block';
      const w = host.getBoundingClientRect().width || 120;
      const right = Math.min(r.right, vw);
      const top = Math.max(r.top, 0) + 8;
      host.style.left = Math.max(Math.min(right - w - 8, vw - w - 4), 4) + 'px';
      host.style.top = top + 'px';
    }

    function pkg2label() {
      const pkg = V.effectivePlayer(settings);
      return pkg ? V.playerName(pkg) : 'Öffnen';
    }

    function update() {
      rafPending = false;
      for (const [video, entry] of entries) {
        if (!video.isConnected) {
          entry.host.remove();
          entries.delete(video);
          continue;
        }
        position(entry);
      }
      if (!entries.size && interval) {
        clearInterval(interval);
        interval = null;
      }
    }

    function requestUpdate() {
      if (rafPending) return;
      rafPending = true;
      requestAnimationFrame(update);
    }

    function track(video) {
      if (!isAndroid || entries.has(video)) return;
      entries.set(video, create(video));
      if (!interval) interval = setInterval(requestUpdate, 700); // fängt Layout-Änderungen ab
      requestUpdate();
    }

    if (isAndroid) {
      window.addEventListener('scroll', requestUpdate, { capture: true, passive: true });
      window.addEventListener('resize', requestUpdate, { passive: true });
      document.addEventListener('fullscreenchange', requestUpdate);
    }

    return { track, refresh: requestUpdate };
  })();

  // --------------------------------------------------------------- Start

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
