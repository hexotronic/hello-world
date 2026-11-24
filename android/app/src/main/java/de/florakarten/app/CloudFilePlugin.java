package de.florakarten.app;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

/**
 * Speichert die Sicherung an einem frei gewählten Ort (z. B. eine Datei im eigenen Google Drive).
 * Nutzt den Android-Dialog "Speichern unter" (Storage Access Framework) und merkt sich die Freigabe,
 * damit die App später automatisch in dieselbe Datei schreiben kann.
 */
@CapacitorPlugin(name = "CloudFile")
public class CloudFilePlugin extends Plugin {

    private static final int RW = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    // Umbenennen von Galerie-Fotos (Android 11+ fragt per System-Dialog um Erlaubnis)
    private ActivityResultLauncher<IntentSenderRequest> writeRequestLauncher;
    private PluginCall pendingRenameCall;
    private final List<Uri> pendingMediaUris = new ArrayList<>();
    private final List<String> pendingMediaNames = new ArrayList<>();
    private int pendingRenamed;
    private JSArray pendingFailed;

    @Override
    public void load() {
        writeRequestLauncher = getActivity().registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(),
            result -> onWriteRequestResult(result.getResultCode() == Activity.RESULT_OK)
        );
    }

    /** Fotos aus der Galerie wählen; liefert pro Foto { uri, name, path } (path = Kopie im Cache). */
    @PluginMethod
    public void pickImages(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, Boolean.TRUE.equals(call.getBoolean("multiple", true)));
        intent.addFlags(RW | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "onImagesPicked");
    }

    @ActivityCallback
    private void onImagesPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            call.reject("Keine Fotos gewählt", "CANCELLED");
            return;
        }
        final List<Uri> uris = new ArrayList<>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) uris.add(clip.getItemAt(i).getUri());
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        getBridge().execute(() -> {
            ContentResolver resolver = getContext().getContentResolver();
            File dir = new File(getContext().getCacheDir(), "picked");
            dir.mkdirs();
            JSArray items = new JSArray();
            int n = 0;
            for (Uri uri : uris) {
                try {
                    resolver.takePersistableUriPermission(uri, RW);
                } catch (Exception ignored) {}
                String name = describe(uri).getString("name", "foto.jpg");
                File out = new File(dir, (n++) + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
                try (InputStream in = resolver.openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                    if (in == null) continue;
                    copy(in, os);
                    JSObject item = new JSObject();
                    item.put("uri", uri.toString());
                    item.put("name", name);
                    item.put("path", Uri.fromFile(out).toString());
                    items.put(item);
                } catch (Exception ignored) {}
            }
            JSObject ret = new JSObject();
            ret.put("items", items);
            call.resolve(ret);
        });
    }

    /** Löscht die Cache-Kopien aus pickImages. */
    @PluginMethod
    public void clearPicked(PluginCall call) {
        File dir = new File(getContext().getCacheDir(), "picked");
        File[] list = dir.listFiles();
        if (list != null) for (File f : list) f.delete();
        call.resolve();
    }

    /**
     * Benennt Originalfotos um. items: [{ uri, name }] (name ohne Endung, die Endung bleibt erhalten).
     * Ergebnis: { renamed, failed: [{ name, reason }] }
     */
    @PluginMethod
    public void renameImages(PluginCall call) {
        final JSArray items = call.getArray("items");
        if (items == null) {
            call.reject("items fehlen");
            return;
        }
        if (pendingRenameCall != null) {
            call.reject("Umbenennen läuft bereits");
            return;
        }
        getBridge().execute(() -> {
            ContentResolver resolver = getContext().getContentResolver();
            int renamed = 0;
            JSArray failed = new JSArray();
            List<Uri> mediaUris = new ArrayList<>();
            List<String> mediaNames = new ArrayList<>();
            for (int i = 0; i < items.length(); i++) {
                String newName = "?";
                try {
                    JSONObject it = items.getJSONObject(i);
                    Uri uri = Uri.parse(it.getString("uri"));
                    newName = buildName(it.getString("name"), describe(uri).getString("name", ""));
                    if (tryRenameDocument(resolver, uri, newName)) {
                        renamed++;
                        continue;
                    }
                    Uri media = null;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            media = MediaStore.getMediaUri(getContext(), uri);
                        } catch (Exception ignored) {}
                    }
                    if (media == null && "media".equals(uri.getAuthority())) media = uri;
                    if (media != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        mediaUris.add(media);
                        mediaNames.add(newName);
                    } else {
                        failed.put(fail(newName, Build.VERSION.SDK_INT < Build.VERSION_CODES.R
                            ? "Umbenennen braucht Android 11 oder neuer"
                            : "Diese Quelle erlaubt kein Umbenennen (z. B. Cloud-Foto)"));
                    }
                } catch (Exception e) {
                    failed.put(fail(newName, String.valueOf(e.getMessage())));
                }
            }
            if (mediaUris.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                call.resolve(result(renamed, failed));
                return;
            }
            try {
                PendingIntent pi = MediaStore.createWriteRequest(resolver, mediaUris);
                pendingRenameCall = call;
                pendingMediaUris.clear();
                pendingMediaUris.addAll(mediaUris);
                pendingMediaNames.clear();
                pendingMediaNames.addAll(mediaNames);
                pendingRenamed = renamed;
                pendingFailed = failed;
                final IntentSenderRequest req = new IntentSenderRequest.Builder(pi.getIntentSender()).build();
                getBridge().executeOnMainThread(() -> writeRequestLauncher.launch(req));
            } catch (Exception e) {
                for (String n : mediaNames) failed.put(fail(n, "Keine Erlaubnis: " + e.getMessage()));
                call.resolve(result(renamed, failed));
            }
        });
    }

    private void onWriteRequestResult(final boolean granted) {
        final PluginCall call = pendingRenameCall;
        if (call == null) return;
        pendingRenameCall = null;
        final List<Uri> uris = new ArrayList<>(pendingMediaUris);
        final List<String> names = new ArrayList<>(pendingMediaNames);
        final JSArray failed = pendingFailed != null ? pendingFailed : new JSArray();
        final int base = pendingRenamed;
        getBridge().execute(() -> {
            int renamed = base;
            for (int i = 0; i < uris.size(); i++) {
                if (!granted) {
                    failed.put(fail(names.get(i), "Erlaubnis abgelehnt"));
                    continue;
                }
                try {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME, names.get(i));
                    int n = getContext().getContentResolver().update(uris.get(i), v, null, null);
                    if (n > 0) renamed++;
                    else failed.put(fail(names.get(i), "Nicht umbenannt"));
                } catch (Exception e) {
                    failed.put(fail(names.get(i), String.valueOf(e.getMessage())));
                }
            }
            call.resolve(result(renamed, failed));
        });
    }

    private boolean tryRenameDocument(ContentResolver r, Uri uri, String newName) {
        try {
            if (!DocumentsContract.isDocumentUri(getContext(), uri)) return false;
            int flags = 0;
            try (Cursor c = r.query(uri, new String[] { DocumentsContract.Document.COLUMN_FLAGS }, null, null, null)) {
                if (c != null && c.moveToFirst()) flags = c.getInt(0);
            }
            if ((flags & DocumentsContract.Document.FLAG_SUPPORTS_RENAME) == 0) return false;
            return DocumentsContract.renameDocument(r, uri, newName) != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static String buildName(String base, String currentName) {
        String ext = ".jpg";
        int dot = currentName == null ? -1 : currentName.lastIndexOf('.');
        if (dot > 0 && currentName.length() - dot <= 6) ext = currentName.substring(dot).toLowerCase();
        String clean = base.replaceAll("[\\\\/:*?\"<>|]", "-").trim();
        if (clean.isEmpty()) clean = "Foto";
        if (clean.length() > 100) clean = clean.substring(0, 100).trim();
        return clean + ext;
    }

    private static JSObject fail(String name, String reason) {
        JSObject o = new JSObject();
        o.put("name", name);
        o.put("reason", reason);
        return o;
    }

    private static JSObject result(int renamed, JSArray failed) {
        JSObject o = new JSObject();
        o.put("renamed", renamed);
        o.put("failed", failed);
        return o;
    }

    /** Öffnet "Speichern unter". Ergebnis: { uri, name, provider, persistable }. */
    @PluginMethod
    public void pickSaveLocation(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(call.getString("mimeType", "application/json"));
        intent.putExtra(Intent.EXTRA_TITLE, call.getString("fileName", "florakarten-sicherung.json"));
        startActivityForResult(call, intent, "onSaveLocationPicked");
    }

    /** Öffnet "Datei öffnen", um eine bestehende Sicherung als Ziel zu wählen. */
    @PluginMethod
    public void pickExistingFile(PluginCall call) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.addFlags(RW | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(call, intent, "onSaveLocationPicked");
    }

    @ActivityCallback
    private void onSaveLocationPicked(PluginCall call, ActivityResult result) {
        if (call == null) return;
        if (result.getResultCode() != Activity.RESULT_OK || result.getData() == null || result.getData().getData() == null) {
            call.reject("Kein Speicherort gewählt", "CANCELLED");
            return;
        }
        Uri uri = result.getData().getData();
        boolean persistable = true;
        try {
            getContext().getContentResolver().takePersistableUriPermission(uri, RW);
        } catch (Exception e) {
            persistable = false;
        }
        JSObject ret = describe(uri);
        ret.put("persistable", persistable);
        call.resolve(ret);
    }

    /** Kopiert eine lokale Datei (Pfad oder file://-URI) in das gewählte Ziel. */
    @PluginMethod
    public void writeFromFile(PluginCall call) {
        final String target = call.getString("uri");
        final String source = call.getString("path");
        if (target == null || source == null) {
            call.reject("uri und path sind nötig");
            return;
        }
        getBridge().execute(() -> {
            File file = toFile(source);
            Uri uri = Uri.parse(target);
            ContentResolver resolver = getContext().getContentResolver();
            OutputStream out = null;
            try (InputStream in = new FileInputStream(file)) {
                try {
                    out = resolver.openOutputStream(uri, "wt");
                } catch (Exception e) {
                    out = null;
                }
                if (out == null) out = resolver.openOutputStream(uri, "w");
                if (out == null) throw new Exception("Ziel lässt sich nicht beschreiben");
                long bytes = copy(in, out);
                out.flush();
                JSObject ret = describe(uri);
                ret.put("bytes", bytes);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("Kein Zugriff mehr auf den Speicherort. Bitte neu wählen.", "NO_ACCESS", e);
            } catch (Exception e) {
                call.reject("Schreiben fehlgeschlagen: " + e.getMessage(), "WRITE_FAILED", e);
            } finally {
                try {
                    if (out != null) out.close();
                } catch (Exception ignored) {}
            }
        });
    }

    /** Kopiert den Inhalt des gewählten Ziels in eine lokale Datei im Cache. Ergebnis: { path, bytes }. */
    @PluginMethod
    public void readToFile(PluginCall call) {
        final String target = call.getString("uri");
        if (target == null) {
            call.reject("uri ist nötig");
            return;
        }
        getBridge().execute(() -> {
            Uri uri = Uri.parse(target);
            File out = new File(getContext().getCacheDir(), "cloud-import.json");
            try (InputStream in = getContext().getContentResolver().openInputStream(uri); OutputStream os = new FileOutputStream(out)) {
                if (in == null) throw new Exception("Datei lässt sich nicht lesen");
                long bytes = copy(in, os);
                JSObject ret = new JSObject();
                ret.put("path", Uri.fromFile(out).toString());
                ret.put("bytes", bytes);
                call.resolve(ret);
            } catch (SecurityException e) {
                call.reject("Kein Zugriff mehr auf den Speicherort. Bitte neu wählen.", "NO_ACCESS", e);
            } catch (Exception e) {
                call.reject("Lesen fehlgeschlagen: " + e.getMessage(), "READ_FAILED", e);
            }
        });
    }

    /** Prüft, ob die App noch Zugriff auf das Ziel hat, und liefert Name/Größe. */
    @PluginMethod
    public void info(PluginCall call) {
        String target = call.getString("uri");
        if (target == null) {
            call.reject("uri ist nötig");
            return;
        }
        Uri uri = Uri.parse(target);
        JSObject ret = describe(uri);
        ret.put("granted", hasPermission(uri));
        call.resolve(ret);
    }

    /** Gibt die gespeicherte Freigabe wieder frei. */
    @PluginMethod
    public void forget(PluginCall call) {
        String target = call.getString("uri");
        if (target != null) {
            try {
                getContext().getContentResolver().releasePersistableUriPermission(Uri.parse(target), RW);
            } catch (Exception ignored) {}
        }
        call.resolve();
    }

    private boolean hasPermission(Uri uri) {
        for (UriPermission p : getContext().getContentResolver().getPersistedUriPermissions()) {
            if (p.getUri().equals(uri) && p.isWritePermission()) return true;
        }
        return false;
    }

    private JSObject describe(Uri uri) {
        JSObject ret = new JSObject();
        ret.put("uri", uri.toString());
        ret.put("provider", uri.getAuthority());
        try (Cursor c = getContext().getContentResolver().query(uri, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int n = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                int s = c.getColumnIndex(OpenableColumns.SIZE);
                if (n >= 0) ret.put("name", c.getString(n));
                if (s >= 0 && !c.isNull(s)) ret.put("size", c.getLong(s));
            }
        } catch (Exception ignored) {}
        return ret;
    }

    private static File toFile(String pathOrUri) {
        if (pathOrUri.startsWith("file://")) {
            String p = Uri.parse(pathOrUri).getPath();
            return new File(p != null ? p : pathOrUri.substring(7));
        }
        return new File(pathOrUri);
    }

    private static long copy(InputStream in, OutputStream out) throws java.io.IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int r;
        while ((r = in.read(buf)) != -1) {
            out.write(buf, 0, r);
            total += r;
        }
        return total;
    }
}
