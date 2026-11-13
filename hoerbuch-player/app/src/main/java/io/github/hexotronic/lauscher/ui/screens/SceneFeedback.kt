@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.hexotronic.lauscher.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.hexotronic.lauscher.analysis.core.Scene
import io.github.hexotronic.lauscher.music.library.core.MusicCatalog
import io.github.hexotronic.lauscher.playback.NowPlaying
import io.github.hexotronic.lauscher.ui.MainViewModel
import io.github.hexotronic.lauscher.ui.components.PixelBadge
import io.github.hexotronic.lauscher.ui.components.RetroButton
import io.github.hexotronic.lauscher.ui.components.RetroButtonStyle
import io.github.hexotronic.lauscher.ui.components.moodColor
import io.github.hexotronic.lauscher.ui.components.onMoodColor
import io.github.hexotronic.lauscher.ui.formatClock

internal val LevelLabels = mapOf(1 to "Dezent", 2 to "Deutlich", 3 to "Stark")

/**
 * "Passt die Musik?": feedback on the current scene and piece. Every answer changes something
 * right away and teaches the analysis the listener's taste for the rest of the book.
 */
@Composable
fun SceneFeedbackSheet(vm: MainViewModel, np: NowPlaying, onDismiss: () -> Unit) {
    val settings by vm.musicSettings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val scene = np.scene
    val piece = np.musicTrackId?.takeIf { np.musicPlaying }?.let { MusicCatalog.byId(it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text("Passt die Musik?", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            if (scene == null) {
                Text(
                    "Diese Stelle ist noch nicht analysiert.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                RetroButton(onClick = { vm.analyzeBook(fromCurrentPosition = true); onDismiss() }) { Text("Ab hier analysieren") }
                return@Column
            }
            SceneSummary(scene)
            piece?.let {
                Spacer(Modifier.height(6.dp))
                Text("♪ ${it.title} – ${it.composer}", style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))
            Text("Die Szene", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FeedbackButton(Icons.Filled.ThumbUp, "Passt", RetroButtonStyle.PRIMARY) {
                    vm.confirmScene(scene)
                    onDismiss()
                }
                FeedbackButton(Icons.Filled.Edit, "Korrigieren …") { editing = true }
                if (scene.mood != null) {
                    FeedbackButton(Icons.Filled.MusicOff, "Hier keine Musik") {
                        vm.overrideScene(scene, null, scene.level, scene.title, scene.setting)
                        onDismiss()
                    }
                }
                FeedbackButton(Icons.Filled.Refresh, "Neu analysieren") {
                    vm.deleteScene(scene, reanalyze = true)
                    onDismiss()
                }
                FeedbackButton(Icons.Filled.Delete, "Analyse löschen") {
                    vm.deleteScene(scene, reanalyze = false)
                    onDismiss()
                }
                FeedbackButton(Icons.AutoMirrored.Filled.List, "Alle Szenen (KI-Analyse)") {
                    onDismiss()
                    vm.openScreen("analysis")
                }
            }

            if (piece != null) {
                Spacer(Modifier.height(14.dp))
                Text("Das Stück", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeedbackButton(Icons.Filled.SkipNext, "Anderes Stück") { vm.skipMusicTrack() }
                    val liked = piece.id in settings.likedTracks
                    FeedbackButton(if (liked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, if (liked) "Gemerkt" else "Gefällt mir") {
                        vm.toggleLikedTrack(piece.id)
                    }
                    FeedbackButton(Icons.Filled.Block, "Nie mehr spielen") {
                        vm.toggleHiddenTrack(piece.id)
                        onDismiss()
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(
                if (settings.learnFromFeedback) {
                    "Korrigierte und bestätigte Szenen zeigt die App der KI als Beispiele, wenn sie den Rest des Buchs analysiert."
                } else {
                    "Lernen aus Korrekturen ist in den Einstellungen ausgeschaltet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (editing && scene != null) {
        SceneEditDialog(scene, onDismiss = { editing = false }) { mood, level, title, setting ->
            vm.overrideScene(scene, mood, level, title, setting)
            editing = false
            onDismiss()
        }
    }
}

@Composable
private fun SceneSummary(scene: Scene) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (scene.mood != null) {
            PixelBadge(scene.mood.label, moodColor(scene.mood), onMoodColor(scene.mood))
        } else {
            PixelBadge("Keine Musik", MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
        }
        scene.setting?.let {
            Spacer(Modifier.width(6.dp))
            PixelBadge(it.label, MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.width(6.dp))
        PixelBadge(LevelLabels[scene.level] ?: "", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurface)
    }
    Spacer(Modifier.height(4.dp))
    Text(
        (scene.title.ifBlank { "Szene" }) + " · " + formatClock(scene.startMs) + " – " + formatClock(scene.endMs) +
            if (scene.manual) " · von dir festgelegt" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun FeedbackButton(icon: ImageVector, label: String, style: RetroButtonStyle = RetroButtonStyle.PLAIN, onClick: () -> Unit) {
    RetroButton(onClick = onClick, style = style) {
        Icon(icon, contentDescription = null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label)
    }
}
