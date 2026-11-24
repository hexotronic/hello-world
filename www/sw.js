// Offline-Cache: App-Dateien werden beim ersten Laden gespeichert.
const CACHE = 'florakarten-v6';
const FILES = [
  './',
  'css/app.css',
  'fonts/bricolage-latin.woff2',
  'fonts/newsreader-italic-latin.woff2',
  'icons/icon-maskable.svg',
  'icons/icon.svg',
  'index.html',
  'js/app.js',
  'js/backup.js',
  'js/cardview.js',
  'js/db.js',
  'js/icons.js',
  'js/images.js',
  'js/plants.js',
  'js/sample.js',
  'js/srs.js',
  'js/ui.js',
  'js/util.js',
  'js/views/browse.js',
  'js/views/deck.js',
  'js/views/edit.js',
  'js/views/home.js',
  'js/views/quick.js',
  'js/views/quiz.js',
  'js/views/random.js',
  'js/views/review.js',
  'js/views/settings.js',
  'js/views/stats.js',
  'js/native.js',
  'js/cloud.js',
  'js/cardpicker.js',
  'js/views/cloudsection.js',
  'vendor/capacitor.js',
  'manifest.webmanifest',
  'icons/icon-192.png',
  'icons/icon-512.png',
  'icons/icon-maskable-512.png',
];

self.addEventListener('install', e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll([...new Set(FILES)])).then(() => self.skipWaiting()));
});

self.addEventListener('activate', e => {
  e.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

// Erst Netz (damit Updates ankommen), sonst Cache.
self.addEventListener('fetch', e => {
  const req = e.request;
  if (req.method !== 'GET' || new URL(req.url).origin !== location.origin) return;
  e.respondWith(
    fetch(req)
      .then(res => {
        if (res.ok) {
          const copy = res.clone();
          caches.open(CACHE).then(c => c.put(req, copy));
        }
        return res;
      })
      .catch(() => caches.match(req, { ignoreSearch: true }).then(r => r || caches.match('./')))
  );
});
