// Ein- und ausschaltbare Bereiche der App (Einstellungen → Bereiche ein- und ausschalten).
// Ausgeschaltete Bereiche werden nur ausgeblendet – gespeicherte Daten bleiben erhalten.

export const FEATURE_GROUPS = [
  {
    title: 'Hauptbereiche',
    items: [
      { key: 'random', label: 'Zufallsfotos', hint: 'Unterer Reiter „Zufall“ und Kachel im Stapel' },
      { key: 'quiz', label: 'Training', hint: 'Unterer Reiter „Training“ und Kachel im Stapel' },
      { key: 'stats', label: 'Statistik', hint: 'Unterer Reiter „Statistik“' },
      { key: 'quick', label: 'Fotos schnell erfassen', hint: 'Knopf auf der Startseite und Kachel im Stapel' },
    ],
  },
  {
    title: 'Funktionen',
    items: [
      { key: 'speechIn', label: 'Spracherkennung (Mikrofon)', hint: 'Mikrofon-Knöpfe und Übung „Foto → Name sprechen“' },
      { key: 'speechOut', label: 'Vorlesen', hint: 'Lautsprecher-Knöpfe bei Pflanzennamen' },
      { key: 'webImages', label: 'Bildsuche im Internet', hint: 'Knopf „Internet“ bei den Fotos im Editor' },
      { key: 'hints', label: 'Tipps beim Lernen', hint: 'Tipp-Knopf (gilt zusätzlich zur Stapel-Einstellung)' },
      { key: 'bloomBanner', label: 'Hinweis „Blüht im …“', hint: 'Startseite, Suche und Zufallsfotos' },
      { key: 'cloudHint', label: 'Cloud-Hinweis auf der Startseite', hint: 'Status bzw. Einladung zur Google-Drive-Sicherung' },
    ],
  },
  {
    title: 'Felder bei Pflanzenkarten',
    items: [
      { key: 'field:family', label: 'Familie' },
      { key: 'field:lifeForm', label: 'Lebensform' },
      { key: 'field:features', label: 'Merkmale' },
      { key: 'field:bloom', label: 'Blütezeit' },
      { key: 'field:habitat', label: 'Standort' },
      { key: 'field:toxicity', label: 'Giftigkeit' },
      { key: 'field:uses', label: 'Verwendung' },
      { key: 'field:confusion', label: 'Verwechslungsgefahr' },
      { key: 'field:notes', label: 'Notizen' },
      { key: 'field:question', label: 'Eigene Frage' },
      { key: 'field:tags', label: 'Schlagwörter' },
    ],
  },
];

let off = new Set();

/** Liste der ausgeschalteten Bereiche übernehmen (aus den Einstellungen). */
export function setFeaturesOff(list) {
  off = new Set(Array.isArray(list) ? list : []);
}

/** Ist ein Bereich eingeschaltet? */
export function isOn(key) {
  return !off.has(key);
}
