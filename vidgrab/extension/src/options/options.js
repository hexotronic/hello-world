/* VidGrab – Einstellungen (werden sofort gespeichert). */
'use strict';

(async function () {
  const V = window.VidGrab;
  const form = document.getElementById('form');
  const player = document.getElementById('player');
  const customWrap = document.getElementById('custom-wrap');
  const status = document.getElementById('status');

  for (const p of V.PLAYERS) player.append(new Option(p.name, p.id));
  player.append(new Option('Andere App (Paketname eingeben)', 'custom'));

  const s = Object.assign({}, V.DEFAULT_SETTINGS, await browser.storage.local.get(V.DEFAULT_SETTINGS));
  player.value = s.player;
  if (player.value !== s.player) player.value = V.DEFAULT_SETTINGS.player;
  form.customPackage.value = s.customPackage;
  for (const r of form.querySelectorAll('input[name="launchMethod"]')) r.checked = r.value === s.launchMethod;
  form.showOverlay.checked = s.showOverlay !== false;
  form.showAudio.checked = !!s.showAudio;
  form.showVariants.checked = !!s.showVariants;
  form.analyzeManifests.checked = !!s.analyzeManifests;
  form.minSizeMB.value = s.minSizeMB;
  customWrap.hidden = player.value !== 'custom';

  let statusTimer = null;
  async function save() {
    customWrap.hidden = player.value !== 'custom';
    const checked = form.querySelector('input[name="launchMethod"]:checked');
    const min = parseFloat(String(form.minSizeMB.value).replace(',', '.'));
    await browser.storage.local.set({
      player: player.value,
      customPackage: form.customPackage.value.trim(),
      launchMethod: checked ? checked.value : V.DEFAULT_SETTINGS.launchMethod,
      showOverlay: form.showOverlay.checked,
      showAudio: form.showAudio.checked,
      showVariants: form.showVariants.checked,
      analyzeManifests: form.analyzeManifests.checked,
      minSizeMB: Number.isFinite(min) && min >= 0 ? min : V.DEFAULT_SETTINGS.minSizeMB,
    });
    status.textContent = 'Gespeichert ✓';
    clearTimeout(statusTimer);
    statusTimer = setTimeout(() => (status.textContent = ''), 1800);
  }

  form.addEventListener('change', save);
  form.addEventListener('submit', (e) => {
    e.preventDefault();
    save();
  });
})();
