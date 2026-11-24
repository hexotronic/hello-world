// Einstellungen, Sicherung, Import, Speicher.
import { app } from '../app.js';
import { h, topbar, btn, segmented, field, dialog, confirmDialog, toast, pickFiles } from '../ui.js';
import { saveSettings, getMeta, setMeta, listDecks, wipeAll, raw } from '../db.js';
import { setDayStartHour } from '../srs.js';
import { exportBackup, importBackup, parseTextImport, importTextCards } from '../backup.js';
import { createSampleDeck } from '../sample.js';
import { forgetAll } from '../images.js';
import { fmtDate, fmtBytes } from '../util.js';
import { isNative } from '../native.js';

const VERSION = '1.0.0';

export default async function settingsView() {
  const s = app.settings;
  const [lastBackup, decks, cardCount] = await Promise.all([getMeta('lastBackup'), listDecks(), raw.count('cards')]);
  const root = h('div', { class: 'page settings' });

  const update = async patch => {
    app.settings = await saveSettings(patch);
    if ('theme' in patch) app.applyTheme(app.settings.theme);
    if ('dayStartHour' in patch) setDayStartHour(app.settings.dayStartHour);
  };

  // Speicherinfo
  const storageInfo = h('p', { class: 'muted small' }, 'Speicher wird ermittelt …');
  (async () => {
    try {
      const est = await navigator.storage?.estimate?.();
      const persisted = await navigator.storage?.persisted?.();
      const photos = await raw.count('photos');
      storageInfo.textContent = `${cardCount} Karten, ${photos} Fotos` +
        (est ? ` · belegt ${fmtBytes(est.usage)} von ca. ${fmtBytes(est.quota)}` : '') +
        (persisted === true ? ' · dauerhaft gespeichert' : persisted === false ? ' · Browser darf Daten bei Platzmangel löschen' : '');
    } catch {
      storageInfo.textContent = `${cardCount} Karten`;
    }
  })();

  async function doImport() {
    const [file] = await pickFiles({ accept: isNative ? '' : '.json,application/json', multiple: false });
    if (!file) return;
    const mode = await dialog({
      title: 'Sicherung einspielen',
      body: h('div', null,
        h('p', null, `Datei: ${file.name} (${fmtBytes(file.size)})`),
        h('p', { class: 'muted' }, 'Zusammenführen behält deine Karten und ergänzt bzw. aktualisiert sie. Ersetzen löscht vorher alles.')),
      actions: [
        { label: 'Abbrechen', value: null },
        { label: 'Ersetzen', value: 'replace', kind: 'danger' },
        { label: 'Zusammenführen', value: 'merge', kind: 'primary' },
      ],
    });
    if (!mode) return;
    if (mode === 'replace' && !(await confirmDialog('Wirklich alles ersetzen?', 'Alle aktuellen Stapel, Karten und Fotos werden gelöscht und durch die Sicherung ersetzt.', { ok: 'Ersetzen' }))) return;
    const stop = toast('Sicherung wird eingespielt …', { duration: 120000 });
    try {
      const r = await importBackup(file, mode);
      stop();
      toast(`Eingespielt: ${r.decks} Stapel, ${r.cards} Karten, ${r.photos} Fotos`);
      app.go('home', {}, { replace: true });
    } catch (err) {
      stop();
      toast('Import fehlgeschlagen: ' + (err?.message || err), { duration: 6000 });
    }
  }

  async function textImport() {
    if (!decks.length) { toast('Lege zuerst einen Stapel an.'); return; }
    let type = 'plant';
    let deckId = decks[0].id;
    const area = h('textarea', { class: 'input mono', rows: 7, id: 'imp-text', placeholder: 'Spitzwegerich;Plantago lanceolata;Plantaginaceae (Wegerichgewächse);Schmale Blätter mit Längsnerven\nBrennnessel;Urtica dioica;Urticaceae (Brennnesselgewächse);Brennhaare, gegenständige Blätter' });
    const preview = h('p', { class: 'muted small' });
    const refresh = () => {
      const rows = parseTextImport(area.value, type);
      preview.textContent = rows.length ? `${rows.length} Karten erkannt. Erste: ${type === 'plant' ? rows[0].deName + (rows[0].latinName ? ' / ' + rows[0].latinName : '') : rows[0].front}` : 'Noch keine Zeilen erkannt.';
    };
    area.addEventListener('input', refresh);
    const formatHint = h('p', { class: 'field-hint' });
    const setHint = () => {
      formatHint.textContent = type === 'plant'
        ? 'Pro Zeile: Deutscher Name ; Botanischer Name ; Familie ; Merkmale ; Schlagwörter'
        : 'Pro Zeile: Vorderseite ; Rückseite ; Schlagwörter';
      area.placeholder = type === 'plant' ? area.placeholder : 'Was heißt „folium“?;Blatt\nWas ist ein Rhizom?;Unterirdischer Spross';
    };
    setHint();
    refresh();
    const ok = await dialog({
      title: 'Karten aus Text importieren',
      cls: 'wide',
      body: h('div', { class: 'form' },
        field('Stapel', h('select', { class: 'input', id: 'imp-deck', value: deckId, onchange: e => { deckId = e.target.value; } }, decks.map(d => h('option', { value: d.id }, d.name)))),
        h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Kartentyp'), segmented([
          { value: 'plant', label: 'Pflanze' }, { value: 'basic', label: 'Standard' },
        ], type, v => { type = v; setHint(); refresh(); })),
        formatHint,
        area,
        h('div', { class: 'row-gap' }, btn('Textdatei wählen', async () => {
          const [f] = await pickFiles({ accept: '.txt,.csv,.tsv,text/*', multiple: false });
          if (f) { area.value = await f.text(); refresh(); }
        }, { iconName: 'upload' })),
        preview),
      actions: [{ label: 'Abbrechen', value: false }, { label: 'Importieren', value: true, kind: 'primary' }],
    });
    if (!ok) return;
    const rows = parseTextImport(area.value, type);
    if (!rows.length) { toast('Keine Zeilen zum Importieren.'); return; }
    const n = await importTextCards(rows, deckId, type);
    toast(`${n} Karten importiert`);
  }

  async function wipe() {
    if (!(await confirmDialog('Alle Daten löschen?', 'Alle Stapel, Karten, Fotos und der Lernverlauf werden unwiderruflich gelöscht. Erstelle vorher eine Sicherung.', { ok: 'Alles löschen' }))) return;
    await wipeAll();
    forgetAll();
    await setMeta('seeded', true);
    app.settings = await saveSettings({});
    toast('Alle Daten gelöscht');
    app.go('home', {}, { replace: true });
  }

  const native = isNative;

  root.append(
    topbar({ title: 'Einstellungen', back: () => app.back() }),
    h('div', { class: 'settings-body' },
      h('section', { class: 'set-sec' },
        h('h2', { class: 'section-h' }, 'Darstellung'),
        h('div', { class: 'field' }, h('span', { class: 'field-label' }, 'Farbschema'), segmented([
          { value: 'system', label: 'System' }, { value: 'light', label: 'Hell' }, { value: 'dark', label: 'Dunkel' },
        ], s.theme, v => update({ theme: v }), { id: 'set-theme' }))),

      h('section', { class: 'set-sec' },
        h('h2', { class: 'section-h' }, 'Lernen'),
        field('Neuer Lerntag beginnt um', h('select', { class: 'input', id: 'set-daystart', value: String(s.dayStartHour), onchange: e => update({ dayStartHour: +e.target.value }) },
          [0, 1, 2, 3, 4, 5, 6].map(x => h('option', { value: String(x) }, `${x}:00 Uhr`))), 'Wer abends spät lernt, zählt so noch zum selben Tag.'),
        field('Lernkarten vorziehen (Minuten)', h('select', { class: 'input', id: 'set-ahead', value: String(s.learnAheadMin), onchange: e => update({ learnAheadMin: +e.target.value }) },
          [0, 5, 10, 20, 30, 60].map(x => h('option', { value: String(x) }, x ? `bis zu ${x} Min.` : 'nicht vorziehen'))), 'Wenn sonst nichts mehr offen ist, kommen Lernkarten etwas früher dran.'),
        field('Vorlesestimme', h('select', { class: 'input', id: 'set-voice', value: s.speechLang, onchange: e => update({ speechLang: e.target.value }) },
          h('option', { value: 'de-DE' }, 'Deutsch'), h('option', { value: 'it-IT' }, 'Italienisch (nah an Kirchenlatein)'), h('option', { value: 'en-GB' }, 'Englisch')),
          'Für botanische Namen gibt es meist keine lateinische Stimme. Italienisch klingt oft passender.'),
        h('p', { class: 'muted small' }, 'Tageslimits, Lernschritte und „Antwort eintippen“ stellst du pro Stapel ein (Stapel öffnen → Optionen).')),

      h('section', { class: 'set-sec' },
        h('h2', { class: 'section-h' }, 'Sichern & Übertragen'),
        h('p', { class: 'muted' }, 'Deine Karten und Fotos liegen nur auf diesem Gerät. Sichere sie regelmäßig, z. B. in deine Cloud oder per Mail an dich selbst.'),
        h('p', { class: 'small' }, lastBackup ? `Letzte Sicherung: ${fmtDate(lastBackup)}` : 'Noch keine Sicherung erstellt.'),
        h('div', { class: 'btn-col' },
          btn(native ? 'Sicherung erstellen & teilen' : 'Sicherung herunterladen', () => exportBackup(), { kind: 'primary', iconName: 'download' }),
          btn('Sicherung einspielen', doImport, { iconName: 'upload' }),
          btn('Karten aus Text importieren', textImport, { iconName: 'stack' })),
        storageInfo,
        h('div', { class: 'btn-col' },
          btn('Speicher dauerhaft machen', async () => {
            const ok = await navigator.storage?.persist?.();
            toast(ok ? 'Der Browser behält die Daten jetzt dauerhaft.' : 'Der Browser hat abgelehnt. Tipp: App zum Startbildschirm hinzufügen.');
          }, { kind: 'ghost' }))),

      h('section', { class: 'set-sec' },
        h('h2', { class: 'section-h' }, 'Sonstiges'),
        h('div', { class: 'btn-col' },
          btn('Beispielstapel „Wiesenpflanzen“ laden', async () => { await createSampleDeck(); toast('Beispielstapel angelegt'); }, { iconName: 'leaf' }),
          btn('Alle Daten löschen', wipe, { kind: 'danger', iconName: 'trash' }))),

      h('section', { class: 'set-sec about' },
        h('h2', { class: 'section-h' }, 'Über Florakarten'),
        h('p', null, `Version ${VERSION}. Offline-Karteikarten mit Wiederholung in wachsenden Abständen, Zufallsfotos und Pflanzen-Extras. Für den privaten Gebrauch.`),
        h('p', { class: 'muted small' }, 'Tastatur beim Lernen: Leertaste = aufdecken / Gut, 1–4 = Bewertung, H = Tipp, M = markieren, E = bearbeiten, Z = rückgängig.'))));

  return { el: root, fullscreen: false, tab: 'home' };
}
