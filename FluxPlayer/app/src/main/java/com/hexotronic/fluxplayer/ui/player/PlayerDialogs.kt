package com.hexotronic.fluxplayer.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import com.hexotronic.fluxplayer.data.AspectMode
import com.hexotronic.fluxplayer.data.DecoderPreference
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.data.FrameGenQuality
import com.hexotronic.fluxplayer.data.OrientationMode
import com.hexotronic.fluxplayer.ui.theme.Accent
import com.hexotronic.fluxplayer.util.Format
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun PlayerDialogs(ui: PlayerUiState, actions: PlayerActions) {
    val close = { actions.openDialog(null) }
    when (ui.dialog) {
        PlayerDialog.MENU -> MenuDialog(ui, actions, close)
        PlayerDialog.AUDIO -> TrackDialog("Audiospur", C.TRACK_TYPE_AUDIO, ui, actions, close, allowOff = false)
        PlayerDialog.SUBTITLES -> SubtitleDialog(ui, actions, close)
        PlayerDialog.VIDEO_QUALITY -> TrackDialog("Videoqualität / Videospur", C.TRACK_TYPE_VIDEO, ui, actions, close, allowOff = false)
        PlayerDialog.SPEED -> SpeedDialog(ui, actions, close)
        PlayerDialog.FRAME_GEN -> FrameGenDialog(ui, actions, close)
        PlayerDialog.ZOOM -> ZoomDialog(ui, actions, close)
        PlayerDialog.ADJUST -> AdjustDialog(ui, actions, close)
        PlayerDialog.EQUALIZER -> EqualizerDialog(ui, actions, close)
        PlayerDialog.SLEEP -> SleepDialog(actions, close)
        PlayerDialog.PLAYLIST -> PlaylistDialog(ui, actions, close)
        PlayerDialog.INFO -> InfoDialog(actions, close)
        PlayerDialog.DECODER -> ChoiceDialog(
            "Decoder", DecoderPreference.entries, ui.decoder, { it.label }, { it.description }, close
        ) { actions.setDecoder(it) }
        PlayerDialog.ORIENTATION -> ChoiceDialog(
            "Bildschirmausrichtung", OrientationMode.entries, ui.orientation, { it.label }, { null }, close
        ) { actions.setOrientation(it) }
        null -> Unit
    }
    ui.choices?.let { list ->
        DialogFrame(ui.choicesTitle ?: "Gefundene Videoquellen", { actions.dismissChoices() }, scroll = false) {
            Text("Auf der Seite wurden mehrere Quellen gefunden:", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.size(8.dp))
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                itemsIndexed(list) { _, e ->
                    Column(
                        Modifier.fillMaxWidth().clickable { actions.chooseCandidate(e) }.padding(vertical = 10.dp)
                    ) {
                        Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(e.uri, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------ Bausteine

@Composable
fun DialogFrame(title: String, onDismiss: () -> Unit, scroll: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth(0.94f).padding(vertical = 16.dp)
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onDismiss) { Text("Fertig") }
                }
                Spacer(Modifier.size(8.dp))
                val bounded = Modifier.heightIn(max = 560.dp)
                val inner = if (scroll) bounded.verticalScroll(rememberScrollState()) else bounded
                Column(inner) { content() }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = Accent,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(Modifier.padding(start = 6.dp)) {
            Text(title)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    description: (T) -> String?,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit
) {
    DialogFrame(title, onDismiss) {
        options.forEach { o ->
            RadioRow(label(o), description(o), o == selected) {
                onSelect(o)
                onDismiss()
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Accent)
        Spacer(Modifier.width(16.dp))
        Text(title, modifier = Modifier.weight(1f))
        if (value != null) {
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------------------------ Dialoge

@Composable
private fun MenuDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    DialogFrame("Optionen", close) {
        MenuItem(Icons.Rounded.AutoAwesome, "Frame-Generierung", ui.frameGenMode.label) { actions.openDialog(PlayerDialog.FRAME_GEN) }
        MenuItem(Icons.Rounded.AspectRatio, "Zoom & Seitenverhältnis", "${ui.aspect.label} · ${ui.zoomPercent} %") { actions.openDialog(PlayerDialog.ZOOM) }
        MenuItem(Icons.Rounded.Audiotrack, "Audiospur") { actions.openDialog(PlayerDialog.AUDIO) }
        MenuItem(Icons.Rounded.Subtitles, "Untertitel") { actions.openDialog(PlayerDialog.SUBTITLES) }
        MenuItem(Icons.Rounded.HighQuality, "Videoqualität") { actions.openDialog(PlayerDialog.VIDEO_QUALITY) }
        MenuItem(Icons.Rounded.Speed, "Wiedergabegeschwindigkeit", Format.speed(ui.speed)) { actions.openDialog(PlayerDialog.SPEED) }
        MenuItem(Icons.Rounded.Tune, "Bildeinstellungen") { actions.openDialog(PlayerDialog.ADJUST) }
        MenuItem(Icons.Rounded.Equalizer, "Equalizer & Lautstärke-Boost") { actions.openDialog(PlayerDialog.EQUALIZER) }
        val ab = when {
            ui.abStart == null -> "A setzen"
            ui.abEnd == null -> "B setzen"
            else -> "Aufheben"
        }
        MenuItem(Icons.Rounded.Loop, "A-B-Wiederholung", ab) { actions.abRepeat() }
        val repeat = when (ui.repeatMode) {
            Player.REPEAT_MODE_ONE -> "Titel"
            Player.REPEAT_MODE_ALL -> "Alle"
            else -> "Aus"
        }
        MenuItem(if (ui.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat, "Wiederholen", repeat) { actions.cycleRepeat() }
        MenuItem(Icons.Rounded.Shuffle, "Zufallswiedergabe", if (ui.shuffle) "An" else "Aus") { actions.toggleShuffle() }
        val sleep = when {
            ui.sleepAtEnd -> "Videoende"
            ui.sleepEndsAt != null -> Format.time((ui.sleepEndsAt!! - System.currentTimeMillis()).coerceAtLeast(0))
            else -> "Aus"
        }
        MenuItem(Icons.Rounded.Bedtime, "Sleep-Timer", sleep) { actions.openDialog(PlayerDialog.SLEEP) }
        MenuItem(Icons.Rounded.Headphones, "Hintergrundwiedergabe", if (ui.backgroundPlayback) "An" else "Aus") { actions.toggleBackground() }
        MenuItem(Icons.Rounded.Memory, "Decoder", ui.decoder.label) { actions.openDialog(PlayerDialog.DECODER) }
        MenuItem(Icons.Rounded.ScreenRotation, "Ausrichtung", ui.orientation.label) { actions.openDialog(PlayerDialog.ORIENTATION) }
        if (ui.playlist.size > 1) {
            MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlist", "${ui.currentIndex + 1}/${ui.playlist.size}") { actions.openDialog(PlayerDialog.PLAYLIST) }
        }
        MenuItem(Icons.Rounded.Info, "Medieninformationen") { actions.openDialog(PlayerDialog.INFO) }
    }
}

@Composable
private fun TrackDialog(title: String, type: Int, ui: PlayerUiState, actions: PlayerActions, close: () -> Unit, allowOff: Boolean) {
    val version = ui.tracksVersion
    val tracks = remember(version) { actions.tracks(type) }
    DialogFrame(title, close) {
        if (tracks.isEmpty()) {
            Text("Keine Spuren verfügbar.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            RadioRow("Automatisch", null, false) { actions.autoTrack(type); close() }
            if (allowOff) RadioRow("Aus", null, tracks.none { it.selected }) { actions.selectTrack(type, null); close() }
            tracks.forEach { t ->
                RadioRow(t.label, t.detail, t.selected) { actions.selectTrack(type, t); close() }
            }
        }
    }
}

@Composable
private fun SubtitleDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    val tracks = remember(ui.tracksVersion) { actions.tracks(C.TRACK_TYPE_TEXT) }
    DialogFrame("Untertitel", close) {
        RadioRow("Aus", null, tracks.none { it.selected }) { actions.selectTrack(C.TRACK_TYPE_TEXT, null) }
        tracks.forEach { t ->
            RadioRow(t.label, t.detail, t.selected) { actions.selectTrack(C.TRACK_TYPE_TEXT, t) }
        }
        Spacer(Modifier.size(4.dp))
        OutlinedButton(onClick = { actions.loadSubtitleFile() }, modifier = Modifier.fillMaxWidth()) {
            Text("Untertitel-Datei laden (SRT, VTT, ASS, TTML)…")
        }
        if (ui.subtitleDelaySupported) {
            SectionTitle("Verzögerung: ${ui.subtitleDelayMs} ms")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { actions.setSubtitleDelay(ui.subtitleDelayMs - 500) }) { Text("−500") }
                OutlinedButton(onClick = { actions.setSubtitleDelay(ui.subtitleDelayMs - 100) }) { Text("−100") }
                OutlinedButton(onClick = { actions.setSubtitleDelay(0) }) { Text("0") }
                OutlinedButton(onClick = { actions.setSubtitleDelay(ui.subtitleDelayMs + 100) }) { Text("+100") }
                OutlinedButton(onClick = { actions.setSubtitleDelay(ui.subtitleDelayMs + 500) }) { Text("+500") }
            }
        }
        SectionTitle("Schriftgröße: ${(ui.subtitleScale * 100).roundToInt()} %")
        Slider(
            value = ui.subtitleScale,
            onValueChange = { actions.setSubtitleScale((it * 20).roundToInt() / 20f) },
            valueRange = 0.5f..2.5f
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpeedDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    DialogFrame("Wiedergabegeschwindigkeit", close) {
        Text(Format.speed(ui.speed), style = MaterialTheme.typography.headlineMedium, color = Accent)
        Slider(
            value = ui.speed,
            onValueChange = { actions.setSpeed((it * 20).roundToInt() / 20f) },
            valueRange = 0.25f..4f
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 4f).forEach { s ->
                FilterChip(selected = ui.speed == s, onClick = { actions.setSpeed(s) }, label = { Text(Format.speed(s)) })
            }
        }
        Text(
            "Tipp: Lange auf das Video drücken = vorübergehend 2x.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun FrameGenDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    DialogFrame("Frame-Generierung", close) {
        Text(
            "Berechnet Zwischenbilder, damit Videos (z. B. 24/30 fps) flüssig mit der vollen Bildwiederholrate " +
                "deines Displays laufen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SectionTitle("Modus")
        FrameGenMode.entries.forEach { m ->
            RadioRow(m.label, m.description, ui.frameGenMode == m) { actions.setFrameGenMode(m) }
        }
        SectionTitle("Qualität der Bewegungsschätzung")
        FrameGenQuality.entries.forEach { q ->
            val detail = when (q) {
                FrameGenQuality.PERFORMANCE -> "Schnell, für ältere Geräte"
                FrameGenQuality.BALANCED -> "Empfohlen"
                FrameGenQuality.QUALITY -> "Feineres Bewegungsfeld, braucht eine starke GPU"
            }
            RadioRow(q.label, detail, ui.frameGenQuality == q) { actions.setFrameGenQuality(q) }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        SwitchRow(
            "Hohe Bildwiederholrate anfordern",
            "Display auf maximale Hz schalten (aktuell ${ui.displayHz.roundToInt()} Hz)",
            ui.highRefresh
        ) { actions.setHighRefresh(it) }
        SwitchRow("Statistik einblenden", "Quell-/Ausgabe-fps und Anteil generierter Bilder", ui.showStats) { actions.setShowStats(it) }
        ui.stats?.let { s ->
            if (s.sourceFps > 0) {
                Text(
                    String.format(
                        Locale.GERMANY, "Aktuell: %.1f fps Quelle → %.0f fps Ausgabe (%d %% generiert)",
                        s.sourceFps, s.outputFps, s.generatedPercent
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = Accent
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ZoomDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    DialogFrame("Zoom & Seitenverhältnis", close) {
        SectionTitle("Seitenverhältnis")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AspectMode.entries.forEach { m ->
                FilterChip(selected = ui.aspect == m, onClick = { actions.setAspect(m) }, label = { Text(m.label) })
            }
        }
        SectionTitle("Zoom: ${ui.zoomPercent} %")
        Slider(
            value = ui.zoomPercent.toFloat(),
            onValueChange = { actions.setZoomPercent(((it / 5f).roundToInt() * 5)) },
            valueRange = 25f..400f
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(50, 100, 125, 150, 200, 300).forEach { p ->
                FilterChip(selected = ui.zoomPercent == p, onClick = { actions.setZoomPercent(p) }, label = { Text("$p %") })
            }
        }
        Spacer(Modifier.size(8.dp))
        OutlinedButton(onClick = { actions.resetZoom() }) { Text("Zoom zurücksetzen") }
        Text(
            "Mit zwei Fingern zoomen (bis 1000 %) und verschieben. Gezoomt verschiebt auch ein Finger das Bild.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
private fun AdjustDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    val a = ui.adjustments
    fun set(i: Int, v: Float) {
        val n = a.copyOf()
        n[i] = v
        actions.setAdjustments(n)
    }
    DialogFrame("Bildeinstellungen", close) {
        SectionTitle(String.format(Locale.GERMANY, "Helligkeit: %+.2f", a[0]))
        Slider(value = a[0], onValueChange = { set(0, (it * 100).roundToInt() / 100f) }, valueRange = -0.5f..0.5f)
        SectionTitle(String.format(Locale.GERMANY, "Kontrast: %.2f", a[1]))
        Slider(value = a[1], onValueChange = { set(1, (it * 100).roundToInt() / 100f) }, valueRange = 0.5f..2f)
        SectionTitle(String.format(Locale.GERMANY, "Sättigung: %.2f", a[2]))
        Slider(value = a[2], onValueChange = { set(2, (it * 100).roundToInt() / 100f) }, valueRange = 0f..2.5f)
        SectionTitle(String.format(Locale.GERMANY, "Gamma: %.2f", a[3]))
        Slider(value = a[3], onValueChange = { set(3, (it * 100).roundToInt() / 100f) }, valueRange = 0.4f..2.5f)
        OutlinedButton(onClick = { actions.setAdjustments(floatArrayOf(0f, 1f, 1f, 1f)) }) { Text("Zurücksetzen") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EqualizerDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    val fx = actions.audioEffects()
    var enabled by remember { mutableStateOf(fx.enabled) }
    var preset by remember { mutableIntStateOf(fx.currentPreset()) }
    var levels by remember { mutableStateOf(fx.bandLevels()) }
    var boost by remember { mutableFloatStateOf(ui.boostPercent.toFloat()) }
    val presets = remember { fx.presets() }
    val (minL, maxL) = remember { fx.levelRange() }
    LaunchedEffect(preset) { levels = fx.bandLevels() }
    DialogFrame("Equalizer & Boost", close) {
        if (!fx.available) {
            Text("Der Equalizer ist auf diesem Gerät für diese Wiedergabe nicht verfügbar.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            SwitchRow("Equalizer aktiv", null, enabled) {
                enabled = it
                fx.enabled = it
            }
            if (presets.isNotEmpty()) {
                SectionTitle("Voreinstellungen")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEachIndexed { i, name ->
                        FilterChip(selected = preset == i, onClick = {
                            fx.usePreset(i)
                            preset = i
                            levels = fx.bandLevels()
                        }, label = { Text(name) })
                    }
                }
            }
            SectionTitle("Bänder")
            levels.forEachIndexed { band, level ->
                val hz = fx.centerFrequencyHz(band)
                val label = if (hz >= 1000) String.format(Locale.GERMANY, "%.1f kHz", hz / 1000f) else "$hz Hz"
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(76.dp), style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = level.toFloat(),
                        onValueChange = { v ->
                            fx.setBandLevel(band, v.roundToInt())
                            preset = -1
                            levels = levels.toMutableList().also { it[band] = v.roundToInt() }
                        },
                        valueRange = minL.toFloat()..maxL.toFloat(),
                        modifier = Modifier.weight(1f)
                    )
                    Text(String.format(Locale.GERMANY, "%+.1f dB", level / 100f), modifier = Modifier.width(64.dp),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        SectionTitle("Lautstärke-Boost: ${boost.roundToInt()} %")
        Slider(
            value = boost,
            onValueChange = {
                boost = ((it / 5f).roundToInt() * 5).toFloat()
                actions.setBoost(boost.roundToInt())
            },
            valueRange = 100f..200f
        )
        Text("Achtung: Hohe Verstärkung kann verzerren und Lautsprecher belasten.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepDialog(actions: PlayerActions, close: () -> Unit) {
    DialogFrame("Sleep-Timer", close) {
        val options = listOf(0 to "Aus", 5 to "5 Minuten", 15 to "15 Minuten", 30 to "30 Minuten", 45 to "45 Minuten",
            60 to "1 Stunde", 90 to "1,5 Stunden", 120 to "2 Stunden", -1 to "Am Ende des Videos")
        options.forEach { (min, label) ->
            Text(
                label,
                modifier = Modifier.fillMaxWidth().clickable { actions.setSleepTimer(min); close() }.padding(vertical = 12.dp)
            )
        }
    }
}

@Composable
private fun PlaylistDialog(ui: PlayerUiState, actions: PlayerActions, close: () -> Unit) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = (ui.currentIndex - 2).coerceAtLeast(0))
    DialogFrame("Playlist (${ui.playlist.size})", close, scroll = false) {
        LazyColumn(state = state, modifier = Modifier.heightIn(max = 480.dp)) {
            itemsIndexed(ui.playlist) { i, title ->
                val current = i == ui.currentIndex
                Row(
                    Modifier.fillMaxWidth().clickable { actions.playIndex(i); close() }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${i + 1}", modifier = Modifier.width(44.dp), color = if (current) Accent else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = if (current) Accent else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoDialog(actions: PlayerActions, close: () -> Unit) {
    val info = remember { actions.mediaInfo() }
    DialogFrame("Medieninformationen", close) {
        info.forEach { (k, v) ->
            if (v.isEmpty()) {
                SectionTitle(k)
            } else {
                Row(Modifier.padding(vertical = 3.dp)) {
                    Text(k, modifier = Modifier.width(130.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                    Text(v, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
