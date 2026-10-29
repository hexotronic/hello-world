@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.audio.AudioEnhancerHub
import io.github.hexotronic.lauscher.audio.dsp.EnhancerPreset
import io.github.hexotronic.lauscher.audio.dsp.EnhancerSettings
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.LabeledSlider
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.ui.formatDb
import io.github.hexotronic.lauscher.ui.formatHz
import io.github.hexotronic.lauscher.ui.formatPercent
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val EQ_PRESETS: List<Pair<String, List<Float>>> = listOf(
    "Linear" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Sprache" to listOf(-6f, -4f, -2f, 0f, 1f, 2f, 4f, 4f, 2f, 0f),
    "Wärmer" to listOf(2f, 3f, 3f, 2f, 0f, 0f, -1f, -2f, -3f, -4f),
    "Heller" to listOf(-2f, -1f, 0f, 0f, 0f, 1f, 2f, 4f, 5f, 5f),
    "Weniger Bass" to listOf(-10f, -8f, -5f, -2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Handy-Lautsprecher" to listOf(-12f, -10f, -6f, -1f, 2f, 3f, 3f, 2f, 0f, -2f),
    "Kopfhörer" to listOf(1f, 1f, 0f, -1f, 0f, 1f, 2f, 1f, 2f, 1f),
)

@Composable
fun SoundStudioScreen(vm: MainViewModel) {
    val sound by vm.sound.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val s = sound.settings
    val update = vm::updateSound

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück") }
            },
            title = { Text("Klangstudio") },
            actions = {
                Switch(checked = s.enabled, onCheckedChange = { on -> update { it.copy(enabled = on) } })
                Spacer(Modifier.width(12.dp))
            },
        )
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding()) {
            if (sound.bookId != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SegmentedButton(
                        selected = !sound.perBook,
                        onClick = { vm.setSoundPerBook(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Alle Hörbücher") }
                    SegmentedButton(
                        selected = sound.perBook,
                        onClick = { vm.setSoundPerBook(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Nur dieses Hörbuch") }
                }
                if (sound.perBook) {
                    Text(
                        "Gilt nur für „${np.book?.title.orEmpty()}“",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }

            SpectrumCard(vm, active = s.enabled, playing = np.isPlaying)

            SectionHeader("Voreinstellungen")
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            ) {
                items(EnhancerPreset.entries) { preset ->
                    FilterChip(
                        selected = s.copy(enabled = true) == preset.settings.copy(enabled = true),
                        onClick = { vm.applyPreset(preset) },
                        label = { Text(preset.label) },
                    )
                }
            }
            EnhancerPreset.entries.firstOrNull { it.settings.copy(enabled = true) == s.copy(enabled = true) }?.let {
                Text(
                    it.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            val on = s.enabled
            SectionHeader("Störgeräusche entfernen")
            LabeledSlider(
                "Rauschunterdrückung",
                s.noiseReduction,
                if (s.noiseReduction == 0f) "Aus" else formatPercent(s.noiseReduction),
                subtitle = "Rauschen, Brummen, Lüfter und Raumhall werden automatisch erkannt",
                enabled = on,
            ) { v -> update { it.copy(noiseReduction = v.snap()) } }
            SwitchRow(
                "Rauschprofil einfrieren",
                "Nützlich, wenn das Rauschen gleich bleibt und Sprache fast pausenlos ist",
                s.freezeNoiseProfile,
                enabled = on,
            ) { v -> update { it.copy(freezeNoiseProfile = v) } }
            OptionChips("Rumpelfilter (tiefe Töne)", EnhancerSettings.RUMBLE_OPTIONS, s.rumbleFilterHz, on) { v ->
                update { it.copy(rumbleFilterHz = v) }
            }
            OptionChips("Rauschfilter (Höhen)", EnhancerSettings.HISS_OPTIONS, s.hissFilterHz, on) { v ->
                update { it.copy(hissFilterHz = v) }
            }
            LabeledSlider(
                "Knackser glätten",
                s.declick,
                if (s.declick == 0f) "Aus" else formatPercent(s.declick),
                subtitle = "Für digitalisierte Kassetten und Schallplatten",
                enabled = on,
            ) { v -> update { it.copy(declick = v.snap()) } }
            LabeledSlider(
                "Pausen säubern",
                s.noiseGate,
                if (s.noiseGate == 0f) "Aus" else formatPercent(s.noiseGate),
                subtitle = "Macht Atempausen und Sprechpausen leiser",
                enabled = on,
            ) { v -> update { it.copy(noiseGate = v.snap()) } }
            LabeledSlider(
                "Zischlaute dämpfen",
                s.deEsser,
                if (s.deEsser == 0f) "Aus" else formatPercent(s.deEsser),
                subtitle = "Weniger scharfe S- und Sch-Laute",
                enabled = on,
            ) { v -> update { it.copy(deEsser = v.snap()) } }

            SectionHeader("Sprache & Lautstärke")
            LabeledSlider(
                "Sprachverständlichkeit",
                s.voiceClarity,
                if (s.voiceClarity == 0f) "Aus" else formatPercent(s.voiceClarity),
                subtitle = "Mehr Präsenz, weniger Dröhnen",
                enabled = on,
            ) { v -> update { it.copy(voiceClarity = v.snap()) } }
            LabeledSlider(
                "Dynamik ausgleichen (Nachtmodus)",
                s.compressor,
                if (s.compressor == 0f) "Aus" else formatPercent(s.compressor),
                subtitle = "Leise Stellen lauter, laute Stellen leiser",
                enabled = on,
            ) { v -> update { it.copy(compressor = v.snap()) } }
            SwitchRow(
                "Lautstärke angleichen",
                "Gleicht unterschiedlich laute Dateien und Kapitel langsam an",
                s.autoLevel,
                enabled = on,
            ) { v -> update { it.copy(autoLevel = v) } }
            LabeledSlider(
                "Lautstärke-Boost",
                s.boostDb,
                if (s.boostDb == 0f) "Aus" else formatDb(s.boostDb),
                range = 0f..EnhancerSettings.MAX_BOOST_DB,
                subtitle = "Mit Begrenzer gegen Übersteuern",
                enabled = on,
            ) { v -> update { it.copy(boostDb = (v * 2).roundToInt() / 2f) } }

            SectionHeader("Equalizer")
            SwitchRow("Equalizer verwenden", null, s.equalizerEnabled, enabled = on) { v ->
                update { it.copy(equalizerEnabled = v) }
            }
            EqualizerEditor(
                gains = s.equalizerGains,
                enabled = on && s.equalizerEnabled,
                onChange = { gains -> update { it.copy(equalizerGains = gains) } },
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                EQ_PRESETS.forEach { (name, gains) ->
                    FilterChip(
                        selected = s.equalizerGains == gains,
                        enabled = on,
                        onClick = { update { it.copy(equalizerEnabled = true, equalizerGains = gains) } },
                        label = { Text(name) },
                    )
                }
            }

            SectionHeader("Kanäle")
            SwitchRow("Mono", "Beide Kanäle auf beiden Ohren – ideal mit nur einem Ohrhörer", s.mono, enabled = on) { v ->
                update { it.copy(mono = v) }
            }
            LabeledSlider(
                "Balance",
                s.balance,
                when {
                    abs(s.balance) < 0.01f -> "Mitte"
                    s.balance < 0 -> "Links ${formatPercent(-s.balance)}"
                    else -> "Rechts ${formatPercent(s.balance)}"
                },
                range = -1f..1f,
                enabled = on,
            ) { v -> update { it.copy(balance = if (abs(v) < 0.04f) 0f else v.snap()) } }

            SectionHeader("Wiedergabe")
            SwitchRow("Stille überspringen", "Lange Pausen werden ausgelassen", settings.skipSilence) { v ->
                vm.updateSettings { it.copy(skipSilence = v) }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { update { EnhancerSettings() } }) { Text("Alles zurücksetzen") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun Float.snap(): Float = (this * 100).roundToInt() / 100f

@Composable
private fun OptionChips(title: String, options: IntArray, selected: Int, enabled: Boolean, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { hz ->
                FilterChip(
                    selected = hz == selected,
                    enabled = enabled,
                    onClick = { onSelect(hz) },
                    label = { Text(formatHz(hz)) },
                )
            }
        }
    }
}

/** Live spectrum: original level, processed level and the estimated noise floor. */
@Composable
private fun SpectrumCard(vm: MainViewModel, active: Boolean, playing: Boolean) {
    val bands = 48
    val input = remember { FloatArray(bands) { -100f } }
    val noise = remember { FloatArray(bands) { -100f } }
    val output = remember { FloatArray(bands) { -100f } }
    val scratchIn = remember { FloatArray(bands) }
    val scratchNoise = remember { FloatArray(bands) }
    val scratchOut = remember { FloatArray(bands) }
    var frame by remember { mutableLongStateOf(0L) }
    var hasEngine by remember { mutableStateOf(AudioEnhancerHub.engine != null) }

    LaunchedEffect(playing) {
        while (true) {
            val engine = AudioEnhancerHub.engine
            hasEngine = engine != null
            if (engine != null) {
                engine.snapshotSpectrum(bands, scratchIn, scratchNoise, scratchOut)
                for (i in 0 until bands) {
                    // Fast attack, slow release reads calmer than raw frames.
                    input[i] = if (scratchIn[i] > input[i]) scratchIn[i] else input[i] * 0.85f + scratchIn[i] * 0.15f
                    output[i] = if (scratchOut[i] > output[i]) scratchOut[i] else output[i] * 0.85f + scratchOut[i] * 0.15f
                    noise[i] = noise[i] * 0.8f + scratchNoise[i] * 0.2f
                }
                frame++
            }
            if (!playing) {
                delay(250)
                if (frame > 0) break
            }
            delay(40)
        }
    }

    val original = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
    val processed = MaterialTheme.colorScheme.primary
    val floor = MaterialTheme.colorScheme.secondary
    val grid = MaterialTheme.colorScheme.outlineVariant

    ElevatedCard(Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Live-Spektrum", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                ABButton(vm)
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(150.dp)) {
                Canvas(Modifier.fillMaxSize()) {
                    // Reading the frame counter here redraws the canvas for every new snapshot.
                    if (frame < 0) return@Canvas
                    val minDb = -100f
                    val maxDb = -10f
                    fun y(db: Float) = size.height * (1f - ((db - minDb) / (maxDb - minDb)).coerceIn(0f, 1f))
                    for (db in listOf(-90f, -60f, -30f)) {
                        drawLine(grid, Offset(0f, y(db)), Offset(size.width, y(db)), strokeWidth = 1f)
                    }
                    val slot = size.width / bands
                    val barWidth = (slot - 2.dp.toPx()).coerceIn(1f, 24.dp.toPx())
                    for (i in 0 until bands) {
                        val x = i * slot + (slot - barWidth) / 2
                        val top = y(input[i])
                        drawRect(original, Offset(x, top), Size(barWidth, size.height - top))
                        if (active) {
                            val outTop = y(output[i])
                            drawRect(processed, Offset(x, outTop), Size(barWidth, size.height - outTop))
                        }
                    }
                    val path = Path()
                    for (i in 0 until bands) {
                        val x = i * slot + slot / 2
                        if (i == 0) path.moveTo(x, y(noise[i])) else path.lineTo(x, y(noise[i]))
                    }
                    drawPath(path, floor, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }
                if (!hasEngine) {
                    Text(
                        "Das Spektrum erscheint, sobald ein Hörbuch läuft.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                LegendSwatch(original, "Original")
                Spacer(Modifier.width(14.dp))
                LegendSwatch(processed, "Bearbeitet")
                Spacer(Modifier.width(14.dp))
                LegendSwatch(floor, "Rauschboden")
            }
            Row(Modifier.fillMaxWidth()) {
                Text("50 Hz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("1 kHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text("16 kHz", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(color))
    Spacer(Modifier.width(6.dp))
    Text(label, style = MaterialTheme.typography.labelSmall)
}

/** Hold to hear the unprocessed original. */
@Composable
private fun ABButton(vm: MainViewModel) {
    var pressed by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(50),
        color = if (pressed) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                pressed = true
                vm.setBypass(true)
                try {
                    tryAwaitRelease()
                } finally {
                    vm.setBypass(false)
                    pressed = false
                }
            })
        },
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Hearing, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (pressed) "Original" else "Halten: A/B", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/** Ten-band graphic equalizer: drag the points up and down. */
@Composable
private fun EqualizerEditor(gains: List<Float>, enabled: Boolean, onChange: (List<Float>) -> Unit) {
    val currentGains by rememberUpdatedState(gains)
    val currentOnChange by rememberUpdatedState(onChange)
    val currentEnabled by rememberUpdatedState(enabled)
    val measurer = rememberTextMeasurer()
    val line = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.35f)
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.1f else 0.04f)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val zero = MaterialTheme.colorScheme.outline
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surface = MaterialTheme.colorScheme.surface
    val range = EnhancerSettings.EQ_RANGE_DB
    val freqs = EnhancerSettings.EQ_FREQUENCIES
    var activeBand by remember { mutableIntStateOf(-1) }

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                val labelSpace = 18.dp.toPx()
                fun bandAt(x: Float): Int = ((x / size.width) * freqs.size).toInt().coerceIn(0, freqs.size - 1)
                fun gainAt(y: Float): Float {
                    val h = size.height - labelSpace
                    val g = (0.5f - y / h) * 2 * range
                    return ((g * 2).roundToInt() / 2f).coerceIn(-range, range)
                }
                detectDragGestures(
                    onDragStart = { pos ->
                        if (currentEnabled) {
                            activeBand = bandAt(pos.x)
                            currentOnChange(currentGains.toMutableList().also { it[activeBand] = gainAt(pos.y) })
                        }
                    },
                    onDragEnd = { activeBand = -1 },
                    onDragCancel = { activeBand = -1 },
                    onDrag = { change, _ ->
                        if (currentEnabled && activeBand >= 0) {
                            currentOnChange(currentGains.toMutableList().also { it[activeBand] = gainAt(change.position.y) })
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { pos ->
                    if (currentEnabled) {
                        val band = ((pos.x / size.width) * freqs.size).toInt().coerceIn(0, freqs.size - 1)
                        currentOnChange(currentGains.toMutableList().also { it[band] = 0f })
                    }
                })
            },
    ) {
        val labelSpace = 18.dp.toPx()
        val h = size.height - labelSpace
        val slot = size.width / freqs.size
        fun y(g: Float) = h * (0.5f - g / (2 * range))
        for (g in listOf(-range, -range / 2, range / 2, range)) {
            drawLine(grid, Offset(0f, y(g)), Offset(size.width, y(g)), strokeWidth = 1f)
        }
        drawLine(zero, Offset(0f, y(0f)), Offset(size.width, y(0f)), strokeWidth = 1.5f)

        val points = gains.mapIndexed { i, g -> Offset(i * slot + slot / 2, y(g)) }
        val curve = Path()
        points.forEachIndexed { i, p ->
            if (i == 0) {
                curve.moveTo(p.x, p.y)
            } else {
                val prev = points[i - 1]
                val midX = (prev.x + p.x) / 2
                curve.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
            }
        }
        val area = Path().apply {
            addPath(curve)
            lineTo(points.last().x, y(0f))
            lineTo(points.first().x, y(0f))
            close()
        }
        drawPath(area, fill)
        drawPath(curve, line, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        points.forEachIndexed { i, p ->
            val r = if (i == activeBand) 8.dp.toPx() else 5.dp.toPx()
            drawCircle(surface, radius = r + 2.dp.toPx(), center = p)
            drawCircle(line, radius = r, center = p)
            val label = freqs[i].let { if (it >= 1000) "${it / 1000}k" else "$it" }
            val layout = measurer.measure(label, TextStyle(fontSize = 10.sp, color = labelColor))
            drawText(layout, topLeft = Offset(p.x - layout.size.width / 2f, size.height - layout.size.height))
            if (i == activeBand) {
                val value = measurer.measure(formatDb(gains[i]), TextStyle(fontSize = 11.sp, color = labelColor))
                val ty = (p.y - 26.dp.toPx()).coerceAtLeast(0f)
                drawText(value, topLeft = Offset((p.x - value.size.width / 2f).coerceIn(0f, size.width - value.size.width), ty))
            }
        }
    }
    Text(
        "Punkte ziehen zum Anpassen, Doppeltippen setzt ein Band auf 0 dB.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}
