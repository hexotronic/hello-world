@file:OptIn(ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.AnalysisStatus
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.analysis.core.SceneMerger
import io.github.hexotronic.lauscher.analysis.core.TranscriptWindow
import io.github.hexotronic.lauscher.data.Book
import io.github.hexotronic.lauscher.music.MusicSettings
import io.github.hexotronic.lauscher.music.MusicSource
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.LabeledSlider
import io.github.hexotronic.lauscher.ui.components.MoodMap
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.RetroProgress
import io.github.hexotronic.lauscher.ui.components.SectionHeader
import io.github.hexotronic.lauscher.ui.components.SwitchRow
import io.github.hexotronic.lauscher.ui.components.block
import io.github.hexotronic.lauscher.ui.components.moodColor
import io.github.hexotronic.lauscher.ui.components.onMoodColor
import io.github.hexotronic.lauscher.ui.formatClock
import io.github.hexotronic.lauscher.ui.formatHuman
import kotlin.math.roundToInt

/** Everything the "KI-Analyse" tab shows about one book. */
internal class AnalysisTabData(
    val scenes: List<Scene>,
    val story: String,
    val texts: List<TranscriptWindow>,
    val positionMs: Long,
    val durationMs: Long,
    /** The book is the one in the player (position and music are live). */
    val isCurrent: Boolean,
    val trackId: String?,
    val status: AnalysisStatus,
    val settings: MusicSettings,
)

@Composable
internal fun rememberAnalysisTabData(vm: MainViewModel, book: Book): AnalysisTabData {
    val scenesFlow = remember(book.id) { vm.repository.observeScenes(book.id) }
    val scenes by scenesFlow.collectAsStateWithLifecycle(emptyList())
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val status by vm.analysisStatus.collectAsStateWithLifecycle()
    val settings by vm.musicSettings.collectAsStateWithLifecycle()
    // Summary and texts grow with the analysis, so they are read again when the scenes change.
    val story by produceState("", book.id, scenes) { value = vm.repository.story(book.id) }
    val texts by produceState(emptyList<TranscriptWindow>(), book.id, scenes) { value = vm.repository.transcripts(book.id) }
    val current = np.book?.id == book.id
    return AnalysisTabData(
        scenes = scenes,
        story = story,
        texts = texts,
        positionMs = if (current) np.positionMs else book.positionMs,
        durationMs = book.durationMs,
        isCurrent = current,
        trackId = if (current && np.musicPlaying) np.musicTrackId else null,
        status = status,
        settings = settings,
    )
}

/**
 * The "KI-Analyse" tab: how far the AI has read ahead, what it found (places, feelings, tension,
 * the music) as bullet points per scene, and the controls of the analysis.
 */
internal fun LazyListScope.analysisTab(d: AnalysisTabData, vm: MainViewModel, bookId: Long, onEdit: (Scene) -> Unit) {
    item(key = "analysis-head") { AnalysisHeader(d, vm, bookId, onEdit) }
    if (d.scenes.isEmpty()) {
        item(key = "analysis-empty") {
            Text(
                "Noch keine Szenen. Spiel das Buch ab – die KI liest dann automatisch voraus – oder starte die " +
                    "Analyse oben. Ohne Spracherkennungsmodell geht es mit dem Buchtext (Reiter „Buchtext“).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    items(d.scenes, key = { "scene-${it.startMs}" }) { scene ->
        SceneNotes(scene, d, onJump = { vm.playBook(bookId, startAtMs = scene.startMs) }, onEdit = { onEdit(scene) })
    }
}

@Composable
private fun AnalysisHeader(d: AnalysisTabData, vm: MainViewModel, bookId: Long, onEdit: (Scene) -> Unit) {
    val s = d.settings
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Beim Hören liest die KI voraus: Für jeden Abschnitt notiert sie Ort, Gefühl, Spannung und einen kurzen " +
                "Titel. So steht die Hintergrundmusik fest, bevor du an der Stelle bist. Neue Szenen erscheinen hier, " +
                "sobald sie fertig sind – alles bleibt auf dem Gerät.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (d.durationMs > 0) {
            Spacer(Modifier.height(12.dp))
            MoodMap(d.scenes, d.durationMs, d.positionMs, onSceneClick = onEdit)
        }
        val covered = d.scenes.sumOf { it.endMs - it.startMs }
        val percent = if (d.durationMs > 0) (covered * 100 / d.durationMs).toInt() else 0
        val withMusic = d.scenes.count { it.mood != null }
        Text(
            "$percent % des Buchs analysiert · $withMusic ${if (withMusic == 1) "Szene" else "Szenen"} mit Musik",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        val until = SceneMerger.coveredUntil(d.scenes, d.positionMs, d.durationMs)
        Text(
            when {
                until != null && until >= d.durationMs -> "Bis zum Ende des Buchs analysiert"
                until != null && d.isCurrent -> "Vorausgelesen bis ${formatClock(until)} – ${formatHuman(until - d.positionMs)} vor dir"
                until != null -> "Ab deiner Stelle (${formatClock(d.positionMs)}) lückenlos analysiert bis ${formatClock(until)}"
                s.analyzeAhead -> "Deine Stelle (${formatClock(d.positionMs)}) ist noch nicht analysiert – beim Abspielen liest die KI sie zuerst"
                else -> "Deine Stelle (${formatClock(d.positionMs)}) ist noch nicht analysiert"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 2.dp),
        )

        val status = d.status
        if (status is AnalysisStatus.Running && status.bookId == bookId) {
            Spacer(Modifier.height(10.dp))
            Text(status.phase + if (status.automatic) " (liest voraus)" else "", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            RetroProgress(if (status.total > 0) status.done.toFloat() / status.total else 0f)
            Text(
                "${status.done} von ${status.total} Abschnitten" + (status.remainingSeconds?.let { " · noch ca. ${formatHuman(it * 1000L)}" } ?: ""),
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(6.dp))
            RetroButton(onClick = { vm.stopAnalysis() }, style = RetroButtonStyle.PLAIN) { Text("Stopp") }
        } else {
            if (status is AnalysisStatus.Failed) {
                Text(status.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                RetroButton(onClick = { vm.analyzeBookFrom(bookId, d.positionMs) }) { Text("Ab meiner Stelle analysieren") }
                RetroButton(onClick = { vm.analyzeBookFrom(bookId, 0) }, style = RetroButtonStyle.SECONDARY) { Text("Ganzes Buch") }
            }
        }
    }
    SwitchRow(
        "Beim Hören vorauslesen",
        "Die KI bereitet im Hintergrund die nächsten Minuten vor",
        s.analyzeAhead,
    ) { v -> vm.updateMusic { it.copy(analyzeAhead = v) } }
    LabeledSlider(
        "Wie weit voraus",
        s.aheadMinutes.toFloat(),
        "${s.aheadMinutes} Min.",
        range = 5f..60f,
        steps = 10,
        subtitle = "Mehr Vorlauf: die Musik steht früher fest, das Handy rechnet dafür am Stück länger",
        enabled = s.analyzeAhead,
    ) { v -> vm.updateMusic { it.copy(aheadMinutes = (v / 5f).roundToInt() * 5) } }

    if (d.story.isNotBlank()) {
        SectionHeader("Was bisher geschah")
        Text(
            d.story,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
    val places = SceneMerger.shares(d.scenes) { it.setting }
    val moods = SceneMerger.shares(d.scenes) { it.mood }
    if (places.isNotEmpty() || moods.isNotEmpty()) {
        SectionHeader("Überblick")
        Column(Modifier.padding(horizontal = 16.dp)) {
            if (places.isNotEmpty()) {
                Text("Orte", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    places.take(10).forEach { (place, share) ->
                        PixelBadge(
                            "${place.label} ${percentOf(share)}",
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (moods.isNotEmpty()) {
                Text("Gefühle", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    moods.forEach { (mood, share) -> PixelBadge("${mood.label} ${percentOf(share)}", moodColor(mood), onMoodColor(mood)) }
                }
            }
        }
    }
    if (d.scenes.isNotEmpty()) SectionHeader("Szenen als Stichpunkte")
}

/** One scene as bullet points; tapping jumps there, the pencil corrects it. */
@Composable
private fun SceneNotes(scene: Scene, d: AnalysisTabData, onJump: () -> Unit, onEdit: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val now = d.isCurrent && d.positionMs >= scene.startMs && d.positionMs < scene.endMs
    val past = scene.endMs <= d.positionMs
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 20.dp, top = 5.dp, bottom = 5.dp)
            .alpha(if (past && !now) 0.65f else 1f)
            .block(if (now) colors.secondaryContainer else colors.surfaceContainerLow)
            .clickable(onClick = onJump)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${formatClock(scene.startMs)} – ${formatClock(scene.endMs)}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            when {
                now -> PixelBadge("Jetzt", colors.primary, colors.onPrimary)
                d.isCurrent && scene.startMs > d.positionMs -> PixelBadge(
                    "in ${formatHuman(scene.startMs - d.positionMs)}",
                    colors.surfaceContainerHighest,
                    colors.onSurface,
                )
                else -> Unit
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.Edit, contentDescription = "Szene korrigieren", modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.padding(end = 12.dp)) {
            Text(
                scene.title.ifBlank { scene.mood?.hint ?: "Ohne Musik" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Bullet("Ort", scene.setting?.let { "${it.label} (${it.hint})" } ?: "unklar")
            Bullet("Gefühl", scene.mood?.let { "${it.label} – ${it.hint}" } ?: "keins – hier bewusst ohne Musik")
            if (scene.mood != null) Bullet("Spannung", "●".repeat(scene.level.coerceIn(1, 3)) + "○".repeat(3 - scene.level.coerceIn(1, 3)))
            musicLine(scene, d, now)?.let { Bullet("Musik", it) }
            if (scene.manual) Bullet("Quelle", "von dir korrigiert oder bestätigt")
            excerptOf(scene, d.texts)?.let {
                Text(
                    "„$it“",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Bullet(label: String, text: String) {
    Row {
        Text("▪ $label: ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

private fun musicLine(scene: Scene, d: AnalysisTabData, now: Boolean): String? {
    if (!d.settings.enabled) return null
    val mood = scene.mood ?: return "aus"
    if (now && d.trackId != null) MusicCatalog.byId(d.trackId)?.let { return "läuft gerade: „${it.title}“ (${it.composer})" }
    val where = scene.setting?.let { " am Ort „${it.label}“" } ?: ""
    return when (d.settings.source) {
        MusicSource.LIBRARY -> "ein Stück für „${mood.label}“$where, Spannung ${scene.level} von 3"
        MusicSource.SYNTH -> "erzeugte Klänge „${mood.label}“" + if (d.settings.placeSounds && scene.setting?.atmosphere != null) " mit Ortsgeräuschen" else ""
    }
}

/** The beginning of what was heard (or read) in this scene. */
private fun excerptOf(scene: Scene, texts: List<TranscriptWindow>): String? {
    val text = texts.firstOrNull { it.endMs > scene.startMs && it.startMs < scene.endMs }?.text
        ?.replace(Regex("\\s+"), " ")?.trim()
        ?.takeIf { it.length > 10 } ?: return null
    return if (text.length > 180) text.take(180).substringBeforeLast(' ') + " …" else text
}

private fun percentOf(share: Float): String = "${(share * 100).roundToInt().coerceAtLeast(1)} %"
