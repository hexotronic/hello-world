// Einstellungs-Abschnitt „Cloud-Speicher“: eigenes Google Drive (oder anderen Speicher) wählen.
import { app } from '../app.js';
import { h, btn, toggle, dialog, confirmDialog, toast } from '../ui.js';
import { icon } from '../icons.js';
import { listDecks, allLogs, deleteDeck } from '../db.js';
import {
  cloudSupported, getCloud, chooseCloudTarget, cloudBackupNow, restoreFromCloud, cloudHasBackup,
  setCloudAuto, forgetCloud, providerLabel, onCloudChange, ago,
} from '../cloud.js';

const cancelled = err => err?.code === 'CANCELLED' || /Kein Speicherort gewählt/.test(err?.message || '');

export function cloudSection() {
  const sec = h('section', { class: 'set-sec cloud-sec', id: 'cloud' });
  const head = () => h('h2', { class: 'section-h' }, 'Cloud-Speicher (Google Drive)');

  async function backup(label) {
    const stop = toast('Wird in die Cloud gesichert …', { duration: 120000 });
    try {
      const c = await cloudBackupNow();
      stop();
      toast(`${label ? label + ' ' : ''}${c.cards} Karten und ${c.photos} Fotos in ${providerLabel(c.provider)} gesichert`);
    } catch (err) {
      stop();
      toast('Sicherung fehlgeschlagen: ' + (err?.message || err), { duration: 6000 });
    }
  }

  async function restore(mode) {
    const stop = toast('Sicherung wird aus der Cloud geladen …', { duration: 120000 });
    try {
      const r = await restoreFromCloud(mode);
      await app.refreshSettings();
      stop();
      toast(`Wiederhergestellt: ${r.decks} Stapel, ${r.cards} Karten, ${r.photos} Fotos`);
      return true;
    } catch (err) {
      stop();
      toast('Wiederherstellen fehlgeschlagen: ' + (err?.message || err), { duration: 6000 });
      return false;
    }
  }

  async function choose(existing) {
    let c;
    try {
      c = await chooseCloudTarget({ existing });
    } catch (err) {
      if (!cancelled(err)) toast('Speicherort konnte nicht gewählt werden: ' + (err?.message || err), { duration: 6000 });
      return;
    }
    if (existing && await cloudHasBackup()) {
      const choice = await dialog({
        title: 'In dieser Datei liegt schon eine Sicherung',
        body: h('p', { class: 'muted' }, 'Zusammenführen holt die Karten aus der Cloud dazu und sichert danach alles gemeinsam. Überschreiben ersetzt die Datei durch die Karten auf diesem Handy.'),
        actions: [
          { label: 'Abbrechen', value: null },
          { label: 'Überschreiben', value: 'overwrite', kind: 'danger' },
          { label: 'Zusammenführen', value: 'merge', kind: 'primary' },
        ],
      });
      if (!choice) { await forgetCloud(); return; }
      if (choice === 'merge') {
        // Frisch installiert? Dann den unbenutzten Beispielstapel weglassen, sonst gäbe es ihn doppelt.
        const [decks, logs] = await Promise.all([listDecks(), allLogs()]);
        if (decks.length && decks.every(d => d.sample) && !logs.length) for (const d of decks) await deleteDeck(d.id);
        if (await restore('merge')) await backup('Zusammengeführt.');
        app.go('home', {}, { replace: true });
        return;
      }
    }
    await backup('Verbunden.');
  }

  async function restoreFlow() {
    const mode = await dialog({
      title: 'Aus der Cloud wiederherstellen',
      body: h('p', { class: 'muted' }, 'Zusammenführen behält die Karten auf diesem Handy und ergänzt sie. Ersetzen löscht vorher alles hier.'),
      actions: [
        { label: 'Abbrechen', value: null },
        { label: 'Ersetzen', value: 'replace', kind: 'danger' },
        { label: 'Zusammenführen', value: 'merge', kind: 'primary' },
      ],
    });
    if (!mode) return;
    if (mode === 'replace' && !(await confirmDialog('Wirklich alles ersetzen?', 'Alle Karten auf diesem Handy werden durch die Cloud-Sicherung ersetzt.', { ok: 'Ersetzen' }))) return;
    if (await restore(mode)) app.go('home', {}, { replace: true });
  }

  async function render() {
    const c = await getCloud();
    if (!cloudSupported) {
      sec.replaceChildren(head(),
        h('p', null, 'In der Android-App wählst du hier eine Datei in deinem eigenen Google Drive. Die App hält sie dann automatisch aktuell, und du kannst damit jederzeit auf einem anderen Handy weitermachen.'),
        h('p', { class: 'muted small' }, 'Im Browser geht es von Hand: Unten „Sicherung herunterladen“ (auf dem Handy: teilen → „Drive“) und zum Zurückholen „Sicherung einspielen“ → Datei aus Drive wählen.'));
      return;
    }
    if (!c.uri) {
      sec.replaceChildren(head(),
        h('p', null, 'Sichere Karten, Fotos, Lernstand, Einstellungen und deine Auswahllisten in deinem eigenen Google Drive. Danach sichert die App automatisch, und nach einem Update, einer Neuinstallation oder auf einem neuen Handy holst du alles zurück.'),
        h('ol', { class: 'steps' },
          h('li', null, '„Neue Sicherung anlegen“ antippen.'),
          h('li', null, 'Im Dialog oben links ', h('strong', null, '☰'), ' öffnen und ', h('strong', null, 'Drive'), ' mit deinem Google-Konto wählen.'),
          h('li', null, 'Ordner aussuchen und ', h('strong', null, 'Speichern'), ' antippen.')),
        h('div', { class: 'btn-col' },
          btn('Neue Sicherung in Google Drive anlegen', () => choose(false), { kind: 'primary', iconName: 'cloudUp' }),
          btn('Bestehende Sicherung verbinden', () => choose(true), { iconName: 'cloudDown' })),
        h('p', { class: 'field-hint' }, '„Bestehende Sicherung verbinden“ ist für ein neues Handy oder nach einer Neuinstallation gedacht.'));
      return;
    }
    const status = c.lastError
      ? h('span', { class: 'cloud-err' }, 'Letzter Versuch fehlgeschlagen: ' + c.lastError)
      : h('span', { class: 'small' }, `Zuletzt gesichert: ${ago(c.lastSync)}` + (c.lastSync && c.cards != null ? ` · ${c.cards} Karten, ${c.photos} Fotos` : ''));
    sec.replaceChildren(head(),
      h('div', { class: 'cloud-card' + (c.lastError ? ' err' : '') },
        h('span', { class: 'cloud-ico' }, icon('cloud', 24)),
        h('div', { class: 'cloud-main' },
          h('strong', null, providerLabel(c.provider)),
          h('span', { class: 'muted small cloud-file' }, c.name || 'Sicherungsdatei'),
          status)),
      toggle('Automatisch sichern', c.auto, v => setCloudAuto(v), { id: 'cloud-auto', hint: 'Etwa eine Minute nach Änderungen (auch an Einstellungen und Auswahllisten), nach jeder Lernrunde und beim Verlassen der App.' }),
      h('p', { class: 'field-hint' }, 'Gesichert werden Karten, Fotos, Lernstand, Einstellungen und Auswahllisten. Vor einem Update oder Neuinstallieren einmal „Jetzt sichern“, danach „Bestehende Sicherung verbinden“ → Zusammenführen.'),
      h('div', { class: 'btn-col' },
        btn('Jetzt sichern', () => backup(), { kind: 'primary', iconName: 'cloudUp' }),
        btn('Aus der Cloud wiederherstellen', restoreFlow, { iconName: 'cloudDown' }),
        btn('Anderen Speicherort wählen', () => choose(false), { iconName: 'move' }),
        btn('Verbindung trennen', async () => {
          if (await confirmDialog('Verbindung trennen?', 'Die Datei in der Cloud bleibt erhalten, die App sichert nur nicht mehr automatisch dorthin.', { ok: 'Trennen' })) {
            await forgetCloud();
            toast('Verbindung getrennt');
          }
        }, { kind: 'ghost' })));
  }

  render();
  const unsub = onCloudChange(() => render());
  return { el: sec, destroy: unsub };
}
