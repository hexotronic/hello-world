/* VidGrab – kleine DOM-Helfer für Popup, Einstellungen und Startseite. */
(function (root) {
  'use strict';

  /** Erzeugt ein Element. Texte werden immer als Text (nie als HTML) eingefügt. */
  function h(tag, attrs, ...children) {
    const el = document.createElement(tag);
    for (const [k, v] of Object.entries(attrs || {})) {
      if (v == null || v === false) continue;
      if (k === 'class') el.className = v;
      else if (k.startsWith('on') && typeof v === 'function') el.addEventListener(k.slice(2), v);
      else if (k === 'hidden') el.hidden = !!v;
      else el.setAttribute(k, v === true ? '' : String(v));
    }
    for (const c of children.flat()) {
      if (c == null || c === false) continue;
      el.append(c instanceof Node ? c : document.createTextNode(String(c)));
    }
    return el;
  }

  let toastTimer = null;
  function toast(text) {
    const el = document.getElementById('toast');
    if (!el) return;
    el.textContent = text;
    el.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => el.classList.remove('show'), 2400);
  }

  async function copyText(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (e) {
      const ta = h('textarea', { style: 'position:fixed;top:-1000px;opacity:0' });
      ta.value = text;
      document.body.append(ta);
      ta.select();
      let ok = false;
      try {
        ok = document.execCommand('copy');
      } catch (err) { /* nichts zu tun */ }
      ta.remove();
      return ok;
    }
  }

  async function isAndroid() {
    try {
      const info = await browser.runtime.getPlatformInfo();
      return info.os === 'android';
    } catch (e) {
      return /Android/i.test(navigator.userAgent);
    }
  }

  function saveTextFile(text, filename) {
    const url = URL.createObjectURL(new Blob([text], { type: 'audio/x-mpegurl' }));
    const a = h('a', { href: url, download: filename });
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 30000);
  }

  root.VidGrabUI = { h, toast, copyText, isAndroid, saveTextFile };
})(window);
