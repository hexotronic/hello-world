package de.florakarten.app;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import androidx.activity.result.ActivityResult;
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

/**
 * Speichert die Sicherung an einem frei gewählten Ort (z. B. eine Datei im eigenen Google Drive).
 * Nutzt den Android-Dialog "Speichern unter" (Storage Access Framework) und merkt sich die Freigabe,
 * damit die App später automatisch in dieselbe Datei schreiben kann.
 */
@CapacitorPlugin(name = "CloudFile")
public class CloudFilePlugin extends Plugin {

    private static final int RW = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

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
