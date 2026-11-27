/* VidGrab – Startseite: öffnet den Intent über einen echten Fingertipp im Tab. */
'use strict';

(function () {
  const { toast, copyText } = window.VidGrabUI;
  const params = new URLSearchParams(location.search);
  const intent = params.get('u') || '';
  const media = params.get('m') || '';
  const title = params.get('t') || '';
  const player = params.get('p') || 'App';

  const open = document.getElementById('open');
  if (intent.startsWith('intent://')) {
    open.href = intent;
    open.textContent = player === 'App' ? 'Mit App öffnen' : 'In ' + player + ' öffnen';
  } else {
    open.hidden = true;
  }
  if (title) document.getElementById('title').textContent = title;
  document.getElementById('url').textContent = media;

  document.getElementById('copy').addEventListener('click', async () => {
    toast((await copyText(media)) ? 'Link kopiert' : 'Kopieren fehlgeschlagen');
  });
  document.getElementById('close').addEventListener('click', async () => {
    try {
      const tab = await browser.tabs.getCurrent();
      await browser.tabs.remove(tab.id);
    } catch (e) {
      window.close();
    }
  });
})();
