// Einstieg: Navigation zwischen den Ansichten, Theme, Start.
import { h } from './ui.js';
import { icon } from './icons.js';
import { closeTopDialog, toast } from './ui.js';
import { closeZoom } from './cardview.js';
import { getSettings, getMeta, setMeta } from './db.js';
import { setDayStartHour } from './srs.js';
import { setNameMode, setPhotoNameMode } from './plants.js';
import { createSampleDeck } from './sample.js';
import { isNative, AppPlugin } from './native.js';
import { initCloudAuto } from './cloud.js';

import homeView from './views/home.js';
import deckView from './views/deck.js';
import reviewView from './views/review.js';
import editView from './views/edit.js';
import browseView from './views/browse.js';
import randomView from './views/random.js';
import quizView from './views/quiz.js';
import statsView from './views/stats.js';
import settingsView from './views/settings.js';
import quickView from './views/quick.js';

const VIEWS = {
  home: homeView, deck: deckView, review: reviewView, edit: editView, browse: browseView,
  random: randomView, quiz: quizView, stats: statsView, settings: settingsView, quick: quickView,
};

const NAV = [
  { name: 'home', label: 'Stapel', icon: 'layers' },
  { name: 'browse', label: 'Suchen', icon: 'search' },
  { name: 'random', label: 'Zufall', icon: 'shuffle' },
  { name: 'quiz', label: 'Training', icon: 'target' },
  { name: 'stats', label: 'Statistik', icon: 'chart' },
];

export const app = {
  settings: null,
  go,
  back,
  reload: () => render(current.name, current.params),
  applyTheme,
  pushLayer,
  popLayer,
  refreshSettings,
};

/** Einstellungen neu laden und anwenden (z. B. nach dem Einspielen einer Sicherung). */
async function refreshSettings() {
  app.settings = await getSettings();
  applyTheme(app.settings.theme);
  setDayStartHour(app.settings.dayStartHour);
  setNameMode(app.settings.nameMode);
  setPhotoNameMode(app.settings.photoNameMode);
  return app.settings;
}

let depth = 0;
let current = { name: 'home', params: {} };
let currentView = null;
let renderToken = 0;
const viewEl = document.getElementById('view');
const navEl = document.getElementById('nav');

function buildNav() {
  navEl.replaceChildren(...NAV.map(n => h('button', {
    type: 'button', class: 'nav-item', dataset: { name: n.name },
    onclick: () => { if (current.name !== n.name || Object.keys(current.params || {}).length) go(n.name, {}, { replace: depth > 0 && NAV.some(x => x.name === current.name) }); },
  }, icon(n.icon, 22), h('span', null, n.label))));
}

function go(name, params = {}, { replace = false } = {}) {
  const state = { name, params, depth: replace ? depth : depth + 1 };
  try {
    if (replace) history.replaceState(state, '');
    else history.pushState(state, '');
    depth = state.depth;
  } catch { /* in eingeschränkten Umgebungen ohne History: Zurück führt dann zur Übersicht */ }
  return render(name, params);
}

/** Unterebene innerhalb einer Ansicht (z. B. laufende Runde): Zurück-Taste schließt sie zuerst. */
function pushLayer() {
  try { history.pushState({ ...current, depth: depth + 1, layer: true }, ''); depth++; } catch { /* egal */ }
}
function popLayer() {
  if (history.state?.layer) { try { history.back(); return; } catch { /* egal */ } }
  currentView?.onPop?.();
}

let popSeen = false;
function back(fallback = 'home', params = {}) {
  if (depth > 0) {
    popSeen = false;
    try {
      history.back();
      // Falls der Verlauf nicht reagiert (eingebettete Vorschau), trotzdem weiterkommen.
      setTimeout(() => { if (!popSeen) { depth = 0; go(fallback, params, { replace: true }); } }, 600);
      return;
    } catch { /* weiter unten */ }
  }
  go(fallback, params, { replace: true });
}

window.addEventListener('popstate', e => {
  popSeen = true;
  // Offenes Vollbild oder offener Dialog: zuerst diesen schließen und auf der Seite bleiben.
  if (closeZoom() || closeTopDialog()) {
    try { history.pushState({ ...current, depth }, ''); } catch { /* egal */ }
    return;
  }
  const st = e.state || { name: 'home', params: {}, depth: 0 };
  // Eine Unterebene der aktuellen Ansicht wurde verlassen
  if (currentView?.onPop && st.name === current.name && currentView.onPop()) {
    depth = st.depth || 0;
    return;
  }
  depth = st.depth || 0;
  render(st.name, st.params || {});
});

async function render(name, params = {}) {
  const token = ++renderToken;
  const fn = VIEWS[name] || VIEWS.home;
  let result;
  try {
    result = await fn(params || {});
  } catch (err) {
    console.error(err);
    result = { el: h('div', { class: 'page' }, h('p', { class: 'error' }, 'Diese Ansicht konnte nicht geladen werden: ' + (err?.message || err))) };
  }
  if (token !== renderToken) { result?.destroy?.(); return; }
  try { currentView?.destroy?.(); } catch { /* egal */ }
  currentView = result;
  current = { name, params };
  viewEl.replaceChildren(result.el);
  const full = !!result.fullscreen;
  document.body.classList.toggle('fullscreen-view', full);
  navEl.hidden = full;
  const tab = result.tab || name;
  for (const b of navEl.children) b.classList.toggle('on', b.dataset.name === tab);
  window.scrollTo(0, 0);
  result.afterMount?.();
}

function applyTheme(theme) {
  const root = document.documentElement;
  if (theme === 'light' || theme === 'dark') root.dataset.theme = theme;
  else delete root.dataset.theme;
  const dark = theme === 'dark' || (theme !== 'light' && matchMedia('(prefers-color-scheme: dark)').matches);
  document.querySelector('meta[name="theme-color"]')?.setAttribute('content', dark ? '#141a16' : '#eef1ea');
}

async function boot() {
  buildNav();
  try {
    app.settings = await getSettings();
  } catch (err) {
    viewEl.replaceChildren(h('div', { class: 'page' }, h('p', { class: 'error' },
      'Der Speicher des Browsers ist nicht verfügbar (privates Fenster?). Ohne Speicher kann die App keine Karten sichern.')));
    console.error(err);
    return;
  }
  applyTheme(app.settings.theme);
  setDayStartHour(app.settings.dayStartHour);
  setNameMode(app.settings.nameMode);
  setPhotoNameMode(app.settings.photoNameMode);
  matchMedia('(prefers-color-scheme: dark)').addEventListener?.('change', () => applyTheme(app.settings.theme));

  if (!(await getMeta('seeded'))) {
    viewEl.replaceChildren(h('div', { class: 'boot' }, icon('leaf', 40), h('p', null, 'Beispielstapel wird angelegt …')));
    try {
      await createSampleDeck();
    } catch (err) {
      console.error(err);
    }
    await setMeta('seeded', true);
  }

  try { await navigator.storage?.persist?.(); } catch { /* optional */ }
  initCloudAuto();

  if (isNative && AppPlugin) {
    // Android-Zurück-Taste: erst Vollbild/Dialog schließen, dann zurück, auf der Startseite App beenden.
    AppPlugin.addListener('backButton', () => {
      if (closeZoom() || closeTopDialog()) return;
      if (depth > 0) history.back();
      else AppPlugin.exitApp();
    });
  }

  if ('serviceWorker' in navigator && location.protocol !== 'file:' && !isNative) {
    navigator.serviceWorker.register('sw.js').catch(() => { /* z. B. in eingebetteten Vorschauen nicht erlaubt */ });
  }

  const st = history.state;
  if (st && st.name && VIEWS[st.name]) {
    depth = st.depth || 0;
    render(st.name, st.params || {});
  } else {
    try { history.replaceState({ name: 'home', params: {}, depth: 0 }, ''); } catch { /* egal */ }
    render('home');
  }
}

window.addEventListener('unhandledrejection', e => {
  console.error(e.reason);
  toast('Fehler: ' + (e.reason?.message || e.reason));
});

boot();
