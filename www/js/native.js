// Brücke zur Android-App (Capacitor). Im normalen Browser sind alle Plugin-Exporte null.
const Cap = window.Capacitor;
export const isNative = !!Cap?.isNativePlatform?.();
const reg = name => (isNative && typeof Cap.registerPlugin === 'function' ? Cap.registerPlugin(name) : null);
export const AppPlugin = reg('App');
export const Filesystem = reg('Filesystem');
export const Share = reg('Share');
// Eigenes Modul der App: Speicherort frei wählen (z. B. Google Drive) und dorthin schreiben
export const CloudFile = reg('CloudFile');
// Eigenes Modul der App: Spracherkennung über den Android-Dialog
export const SpeechNative = reg('Speech');

/** Liest eine lokale App-Datei (file://…) als Blob. */
export async function readNativeFile(fileUri) {
  const res = await fetch(Cap.convertFileSrc(fileUri));
  if (!res.ok) throw new Error('Datei konnte nicht gelesen werden');
  return res.blob();
}
