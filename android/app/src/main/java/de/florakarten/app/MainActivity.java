package de.florakarten.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Eigenes Modul für die Cloud-Sicherung (Speicherort frei wählbar, z. B. Google Drive)
        registerPlugin(CloudFilePlugin.class);
        // Spracherkennung für das Training (Namen sprechen)
        registerPlugin(SpeechPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
