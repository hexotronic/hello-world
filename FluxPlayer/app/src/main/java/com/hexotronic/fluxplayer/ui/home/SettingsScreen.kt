package com.hexotronic.fluxplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hexotronic.fluxplayer.BuildConfig
import com.hexotronic.fluxplayer.data.AspectMode
import com.hexotronic.fluxplayer.data.DecoderPreference
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.data.FrameGenQuality
import com.hexotronic.fluxplayer.data.OrientationMode
import com.hexotronic.fluxplayer.data.Prefs
import com.hexotronic.fluxplayer.data.UpscalerMode
import com.hexotronic.fluxplayer.ui.theme.Accent
import kotlin.math.roundToInt

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val p = remember { Prefs(context) }

    var fgMode by remember { mutableStateOf(p.frameGenMode) }
    var fgQuality by remember { mutableStateOf(p.frameGenQuality) }
    var highRefresh by remember { mutableStateOf(p.highRefreshRate) }
    var stats by remember { mutableStateOf(p.showStats) }
    var slowBoost by remember { mutableStateOf(p.slowMoBoost) }
    var upscaler by remember { mutableStateOf(p.upscaler) }
    var sharp by remember { mutableStateOf(p.sharpness) }
    var resume by remember { mutableStateOf(p.resumePlayback) }
    var background by remember { mutableStateOf(p.backgroundPlayback) }
    var autoPip by remember { mutableStateOf(p.autoPip) }
    var closeAtEnd by remember { mutableStateOf(p.closeAtEnd) }
    var seekStep by remember { mutableStateOf(p.seekStepSeconds) }
    var aspect by remember { mutableStateOf(p.defaultAspect) }
    var decoder by remember { mutableStateOf(p.decoder) }
    var orientation by remember { mutableStateOf(p.orientation) }
    var gSeek by remember { mutableStateOf(p.gestureSeek) }
    var gBright by remember { mutableStateOf(p.gestureBrightness) }
    var gVol by remember { mutableStateOf(p.gestureVolume) }
    var gDouble by remember { mutableStateOf(p.gestureDoubleTap) }
    var gZoom by remember { mutableStateOf(p.gestureZoom) }
    var gLong by remember { mutableStateOf(p.longPressSpeed) }
    var boost by remember { mutableStateOf(p.volumeBoost) }
    var subScale by remember { mutableStateOf(p.subtitleScale) }
    var subBg by remember { mutableStateOf(p.subtitleBackground) }
    var bigBuffer by remember { mutableStateOf(p.largeBuffer) }
    var ua by remember { mutableStateOf(p.userAgent) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Section("Frame-Generierung")
        ChoiceSetting("Modus", fgMode, FrameGenMode.entries, { it.label }, { it.description }) {
            fgMode = it; p.frameGenMode = it
        }
        ChoiceSetting("Qualität", fgQuality, FrameGenQuality.entries, { it.label }, { it.description }) {
            fgQuality = it; p.frameGenQuality = it
        }
        SwitchSetting("Zeitlupen-Boost", "In Zeitlupe automatisch stärkere Bewegungsschätzung (bis „Ultra“)", slowBoost) {
            slowBoost = it; p.slowMoBoost = it
        }
        SwitchSetting("Hohe Bildwiederholrate", "Display während der Wiedergabe auf maximale Hz schalten", highRefresh) {
            highRefresh = it; p.highRefreshRate = it
        }
        SwitchSetting("Statistik einblenden", "fps-Anzeige und Anteil generierter Bilder", stats) {
            stats = it; p.showStats = it
        }

        Section("Upscaler")
        ChoiceSetting("Verfahren", upscaler, UpscalerMode.entries, { it.label }, { it.description }) {
            upscaler = it; p.upscaler = it
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Schärfe: ${(sharp * 100).roundToInt()} %")
            Slider(value = sharp, onValueChange = {
                sharp = (it * 20).roundToInt() / 20f
                p.sharpness = sharp
            }, valueRange = 0f..1f)
        }

        Section("Wiedergabe")
        SwitchSetting("Wiedergabe fortsetzen", "An der zuletzt gesehenen Stelle weitermachen", resume) {
            resume = it; p.resumePlayback = it
        }
        SwitchSetting("Hintergrundwiedergabe", "Ton läuft weiter, wenn die App verlassen wird", background) {
            background = it; p.backgroundPlayback = it
        }
        SwitchSetting("Automatisch Bild-in-Bild", "Beim Verlassen der App als kleines Fenster weiterspielen", autoPip) {
            autoPip = it; p.autoPip = it
        }
        SwitchSetting("Player am Ende schließen", null, closeAtEnd) {
            closeAtEnd = it; p.closeAtEnd = it
        }
        ChoiceSetting("Sprungweite", seekStep, listOf(5, 10, 15, 30, 60), { "$it Sekunden" }, { null }) {
            seekStep = it; p.seekStepSeconds = it
        }
        ChoiceSetting("Standard-Seitenverhältnis", aspect, AspectMode.entries, { it.label }, { null }) {
            aspect = it; p.defaultAspect = it
        }
        ChoiceSetting("Ausrichtung", orientation, OrientationMode.entries, { it.label }, { null }) {
            orientation = it; p.orientation = it
        }
        ChoiceSetting("Decoder", decoder, DecoderPreference.entries, { it.label }, { it.description }) {
            decoder = it; p.decoder = it
        }

        Section("Gesten")
        SwitchSetting("Wischen zum Spulen", "Horizontal wischen", gSeek) { gSeek = it; p.gestureSeek = it }
        SwitchSetting("Helligkeit", "Links vertikal wischen", gBright) { gBright = it; p.gestureBrightness = it }
        SwitchSetting("Lautstärke", "Rechts vertikal wischen", gVol) { gVol = it; p.gestureVolume = it }
        SwitchSetting("Lautstärke-Boost bis 200 %", "Über das Maximum hinaus wischen", boost) { boost = it; p.volumeBoost = it }
        SwitchSetting("Doppeltippen zum Springen", "Links/rechts springen, Mitte Pause", gDouble) { gDouble = it; p.gestureDoubleTap = it }
        SwitchSetting("Pinch-Zoom", "Mit zwei Fingern zoomen und verschieben", gZoom) { gZoom = it; p.gestureZoom = it }
        SwitchSetting("Lang drücken = 2x", "Vorübergehend doppelte Geschwindigkeit", gLong) { gLong = it; p.longPressSpeed = it }

        Section("Untertitel")
        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
            Text("Schriftgröße: ${(subScale * 100).roundToInt()} %")
            Slider(value = subScale, onValueChange = {
                subScale = (it * 20).roundToInt() / 20f
                p.subtitleScale = subScale
            }, valueRange = 0.5f..2.5f)
        }
        SwitchSetting("Hintergrund hinter Untertiteln", null, subBg) { subBg = it; p.subtitleBackground = it }

        Section("Netzwerk")
        SwitchSetting("Großer Puffer", "Mehr vorladen bei instabilen Verbindungen (wirkt nach App-Neustart)", bigBuffer) {
            bigBuffer = it; p.largeBuffer = it
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            OutlinedTextField(
                value = ua,
                onValueChange = { ua = it; p.userAgent = it },
                label = { Text("User-Agent (wirkt nach App-Neustart)") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3
            )
            TextButton(onClick = { ua = Prefs.DEFAULT_USER_AGENT; p.userAgent = "" }) { Text("Standard wiederherstellen") }
        }

        Section("Über")
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("FluxPlayer ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Wiedergabe-Engine: AndroidX Media3 (ExoPlayer) mit FFmpeg-Erweiterung. " +
                    "Frame-Generierung: eigener OpenGL-ES-3-Renderer mit GPU-Bewegungsschätzung. " +
                    "Nur für den privaten Gebrauch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = Accent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchSetting(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceSetting(
    title: String,
    value: T,
    options: List<T>,
    label: (T) -> String,
    description: (T) -> String?,
    onSelect: (T) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable { open = true }.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title)
        Text(label(value), style = MaterialTheme.typography.bodySmall, color = Accent)
    }
    if (open) {
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    options.forEach { o ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onSelect(o); open = false }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = o == value, onClick = { onSelect(o); open = false })
                            Column {
                                Text(label(o))
                                description(o)?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { open = false }) { Text("Schließen") } }
        )
    }
}
