package de.florakarten.app;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import androidx.activity.result.ActivityResult;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.ActivityCallback;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.util.ArrayList;

/**
 * Spracherkennung über den Android-Dialog „Jetzt sprechen“ (meist Google).
 * Liefert mehrere Vorschläge, damit auch botanische Namen tolerant erkannt werden können.
 */
@CapacitorPlugin(name = "Speech")
public class SpeechPlugin extends Plugin {

    @PluginMethod
    public void available(PluginCall call) {
        boolean ok;
        try {
            ok = SpeechRecognizer.isRecognitionAvailable(getContext());
        } catch (Exception e) {
            ok = false;
        }
        JSObject r = new JSObject();
        r.put("available", ok);
        call.resolve(r);
    }

    @PluginMethod
    public void listen(PluginCall call) {
        String lang = call.getString("language", "de-DE");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, lang);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, call.getString("prompt", "Jetzt sprechen …"));
        try {
            startActivityForResult(call, intent, "onSpeechResult");
        } catch (ActivityNotFoundException e) {
            call.reject("Auf diesem Gerät ist keine Spracherkennung eingerichtet.", "UNAVAILABLE");
        }
    }

    @ActivityCallback
    private void onSpeechResult(PluginCall call, ActivityResult result) {
        if (call == null) return;
        Intent data = result.getData();
        if (result.getResultCode() != Activity.RESULT_OK || data == null) {
            call.reject("Nichts erkannt", "CANCELLED");
            return;
        }
        ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        JSArray arr = new JSArray();
        if (matches != null) for (String m : matches) arr.put(m);
        JSObject r = new JSObject();
        r.put("matches", arr);
        call.resolve(r);
    }
}
