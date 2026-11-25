// Einstellungen: KI-Auswertung des Steckbriefs mit einem Sprachmodell direkt auf dem Gerät.
import { app } from '../app.js';
import { h, btn, toggle, confirmDialog, toast, segmented } from '../ui.js';
import { icon } from '../icons.js';
import { saveSettings } from '../db.js';
import { aiFacts } from '../aifacts.js';
import { FACT_LABELS } from '../plantfacts.js';
import { formatMonths } from '../util.js';
import { LLM_MODEL, LLM_MODELS, setLlmModel, llmSizeMB, llmSupported, llmStatus, llmDownload, llmDelete, llmDownloadState, onLlmDownload } from '../llm.js';

export function aiSection() {
  const box = h('div', { class: 'ai-box', id: 'ai' });
  let cached = false;
  let checking = true;
  let device = '', dtype = '';
  const sizeMB = () => llmSizeMB(dtype);
  const applyStatus = st => { cached = !!st.cached; device = st.device || device; dtype = st.dtype || dtype; checking = false; };

  const setAi = async v => { app.settings = await saveSettings({ aiFacts: v }); };

  // Probelauf mit einem kurzen Beispieltext: zeigt Dauer und Ergebnis auf diesem Gerät
  const SAMPLE = `Die Schafgarbe ist eine ausdauernde, krautige Pflanze, die 20 bis 80 Zentimeter hoch wird. Die Laubblätter sind fein gefiedert. Die kleinen weißen bis rosafarbenen Blütenkörbchen stehen in dichten, schirmartigen Rispen. Die Blütezeit reicht von Juni bis Oktober.
== Vorkommen ==
Sie wächst auf Wiesen, Weiden, an Wegrändern und auf Böschungen und bevorzugt sonnige Standorte.
== Verwendung ==
Die Schafgarbe wird als Heilpflanze für Tee genutzt und ist eine beliebte Pflanze für Naturgärten.
== Verwechslung ==
Verwechslungsgefahr besteht mit der Sumpf-Schafgarbe und mit giftigen Doldenblütlern wie dem Schierling.`;
  let probe = null;   // { running, seconds, result, error }
  async function runProbe() {
    probe = { running: true, seconds: 0 };
    const t0 = Date.now();
    const tick = setInterval(() => { probe.seconds = Math.round((Date.now() - t0) / 1000); render(); }, 1000);
    render();
    try {
      const r = await aiFacts({ wikiText: SAMPLE }, { name: 'Schafgarbe', lifeForms: app.settings.lifeForms || [] });
      probe = { seconds: Math.round((Date.now() - t0) / 1000), result: r };
    } catch (err) {
      probe = { error: err?.message || String(err) };
    } finally {
      clearInterval(tick);
      render();
    }
  }
  const probeView = () => {
    if (!probe) return null;
    if (probe.running) return h('p', { class: 'small' }, `Probelauf läuft … ${probe.seconds} s`);
    if (probe.error) return h('p', { class: 'small warn-text' }, 'Probelauf fehlgeschlagen: ' + probe.error);
    const rows = Object.entries(probe.result).map(([k, v]) => h('div', null, h('strong', null, FACT_LABELS[k] + ': '),
      k === 'bloom' ? formatMonths(v) : Array.isArray(v) ? v.join(', ') : v));
    return h('div', { class: 'ai-probe small' },
      h('p', null, h('strong', null, `Fertig in ${probe.seconds} s`), ` (${device === 'webgpu' ? 'Grafikkarte' : 'Prozessor'}). Gelesen aus einem kurzen Beispieltext über die Schafgarbe:`),
      rows.length ? rows : h('p', { class: 'muted' }, 'Die KI hat nichts Brauchbares geliefert.'));
  };

  async function download() {
    try {
      await llmDownload();
      applyStatus(await llmStatus());
      if (cached) {
        if (!app.settings.aiFacts) await setAi(true);
        toast('Sprachmodell ist bereit');
      } else {
        toast('Das Modell konnte nicht vollständig gespeichert werden – zu wenig freier Speicher?', { duration: 6000 });
      }
    } catch (err) {
      toast('Download fehlgeschlagen: ' + (err?.message || err), { duration: 6000 });
    }
    render();
  }

  function render() {
    const dl = llmDownloadState;
    const pct = Math.min(100, Math.round((dl.loaded / (sizeMB() * 1e6)) * 100));
    const status = checking ? 'Wird geprüft …'
      : dl.active ? `Wird geladen … ${pct} % (${Math.round(dl.loaded / 1e6)} von ca. ${sizeMB()} MB)`
        : cached ? `Auf dem Gerät gespeichert (ca. ${sizeMB()} MB)`
          : `Noch nicht heruntergeladen (ca. ${sizeMB()} MB)`;
    const speed = !device ? null : device === 'webgpu'
      ? h('p', { class: 'muted small' }, 'Rechnet mit der Grafikkarte (WebGPU) – je Pflanze meist unter einer Minute.')
      : h('p', { class: 'muted small warn-text' }, 'Dieses Gerät bietet der App keine Grafikbeschleunigung (WebGPU). Die KI rechnet dann nur mit dem Prozessor und braucht mehrere Minuten pro Pflanze.');
    box.replaceChildren(...[
      h('h3', { class: 'form-h' }, 'KI-Auswertung auf dem Handy'),
      h('p', { class: 'muted small' },
        'Ein kleines Sprachmodell liest den Wikipedia-Artikel und fasst Merkmale, Verwendung und Verwechslungen kurz zusammen. ',
        'Standort, Lebensform und Blütezeit ergänzt es nur, wenn die schnelle Texterkennung nichts gefunden hat; die Giftigkeit liest immer die Texterkennung. ',
        'Alles läuft auf deinem Gerät – ohne Konto und ohne Kosten.'),
      h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Modell'),
        segmented(LLM_MODELS.map(m => ({ value: m.key, label: `${m.label} (${llmSizeMB(dtype, m)} MB)` })), LLM_MODEL.key, async v => {
          if (dl.active) { toast('Bitte warten, bis der Download fertig ist.'); render(); return; }
          app.settings = await saveSettings({ aiModel: v });
          setLlmModel(v);
          probe = null;
          checking = true;
          render();
          applyStatus(await llmStatus());
          render();
        }, { id: 'set-aimodel' }),
        h('span', { class: 'field-hint' }, '„Genauer“ liefert bessere Zusammenfassungen, braucht aber mehr Speicher und Zeit. Probier „Klein & schnell“, wenn die App beim Auswerten abstürzt oder es zu lange dauert.')),
      h('div', { class: 'ai-status' + (cached ? ' ok' : '') }, icon('sparkle', 18),
        h('span', null, h('strong', null, LLM_MODEL.name + ': '), status)),
      speed,
      dl.active ? h('div', { class: 'rv-progress ai-progress', role: 'progressbar', 'aria-valuenow': String(pct), 'aria-valuemin': '0', 'aria-valuemax': '100' }, h('span', { style: `width:${pct}%` })) : null,
      !checking && !dl.active && !cached ? h('div', { class: 'btn-col' },
        btn(`Modell herunterladen (ca. ${sizeMB()} MB)`, download, { kind: 'primary', iconName: 'download' }),
        h('p', { class: 'muted small' }, 'Am besten im WLAN. Der Download läuft weiter, wenn du die Einstellungen verlässt, aber nicht, wenn du die App schließt.')) : null,
      cached ? toggle('Steckbrief mit KI auswerten', !!app.settings.aiFacts, v => setAi(v), {
        id: 'set-aifacts',
        hint: 'Nach der Internet-Suche werden die Felder sofort gefüllt und danach von der KI verfeinert – im Hintergrund, dein Getipptes wird nie überschrieben.',
      }) : null,
      cached ? h('div', { class: 'btn-col' }, btn('Probelauf (wie schnell ist es hier?)', () => { if (!probe?.running) runProbe(); }, { iconName: 'play', attrs: probe?.running ? { disabled: true } : {} })) : null,
      probeView(),
      cached ? h('div', { class: 'btn-col' }, btn('Modell löschen', async () => {
        if (!await confirmDialog('Sprachmodell löschen?', `Gibt ca. ${sizeMB()} MB frei. Die KI-Auswertung ist danach aus, bis du das Modell wieder herunterlädst.`, { ok: 'Löschen' })) return;
        await llmDelete();
        await setAi(false);
        cached = false;
        toast('Sprachmodell gelöscht');
        render();
      }, { kind: 'ghost', iconName: 'trash' })) : null,
      h('p', { class: 'muted small' }, `Modell: ${LLM_MODEL.id} von Liquid AI (${LLM_MODEL.license}), geladen über Hugging Face.`),
    ].filter(Boolean));
  }

  if (!llmSupported()) {
    box.replaceChildren(h('h3', { class: 'form-h' }, 'KI-Auswertung auf dem Handy'),
      h('p', { class: 'muted small' }, 'Auf diesem Gerät bzw. in diesem Browser nicht verfügbar.'));
    return { el: box, destroy() {} };
  }
  render();
  let last = 0;
  const off = onLlmDownload(st => {
    if (!st.active) {   // fertig oder abgebrochen: Speicher neu prüfen
      checking = true;
      render();
      llmStatus().then(r => { applyStatus(r); render(); });
      return;
    }
    const now = Date.now();
    if (llmDownloadState.active && now - last < 400) return;   // Anzeige nicht zu oft neu aufbauen
    last = now;
    render();
  });
  llmStatus().then(st => { applyStatus(st); render(); });
  return { el: box, destroy: off };
}
