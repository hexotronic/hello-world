package com.hexotronic.fluxplayer.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BrightnessMedium
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hexotronic.fluxplayer.data.FrameGenMode
import com.hexotronic.fluxplayer.ui.theme.Accent
import com.hexotronic.fluxplayer.util.Format
import java.util.Locale

@Composable
fun PlayerOverlay(ui: PlayerUiState, actions: PlayerActions) {
    Box(Modifier.fillMaxSize()) {
        if (ui.showStats && !ui.inPip) StatsPanel(ui, Modifier.align(Alignment.TopStart))

        if (ui.buffering && ui.error == null && !ui.inPip) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(56.dp), color = Accent, strokeWidth = 4.dp)
        }

        ui.feedback?.let { FeedbackPill(it, Modifier.align(Alignment.Center)) }

        if (!ui.inPip) {
            AnimatedVisibility(
                visible = ui.controlsVisible && !ui.locked,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Controls(ui, actions)
            }
            if (ui.locked && ui.controlsVisible) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(start = 24.dp)
                ) {
                    RoundIcon(Icons.Rounded.Lock, "Entsperren", 56.dp) { actions.toggleLock() }
                }
            }
        }

        ui.loadingMessage?.let { msg ->
            Surface(
                Modifier.align(Alignment.Center).widthIn(max = 420.dp).padding(24.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.75f)
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp), color = Accent, strokeWidth = 3.dp)
                    Spacer(Modifier.width(16.dp))
                    Text(msg, color = Color.White)
                }
            }
        }

        ui.error?.let { err -> ErrorCard(err, actions, Modifier.align(Alignment.Center)) }

        PlayerDialogs(ui, actions)
    }
}

@Composable
private fun Controls(ui: PlayerUiState, actions: PlayerActions) {
    Box(Modifier.fillMaxSize()) {
        // ---------------------------------------------------------- oben
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { actions.close() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück", tint = Color.White)
                }
                Text(
                    ui.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).basicMarquee()
                )
                IconButton(onClick = { actions.openDialog(PlayerDialog.AUDIO) }) {
                    Icon(Icons.Rounded.Audiotrack, "Audiospur", tint = Color.White)
                }
                IconButton(onClick = { actions.openDialog(PlayerDialog.SUBTITLES) }) {
                    Icon(Icons.Rounded.Subtitles, "Untertitel", tint = Color.White)
                }
                IconButton(onClick = { actions.openDialog(PlayerDialog.INFO) }) {
                    Icon(Icons.Rounded.Info, "Info", tint = Color.White)
                }
                IconButton(onClick = { actions.openDialog(PlayerDialog.MENU) }) {
                    Icon(Icons.Rounded.MoreVert, "Mehr", tint = Color.White)
                }
            }
            val badges = buildList {
                if (ui.frameGenMode != FrameGenMode.OFF) add("FG: " + ui.frameGenMode.label)
                if (ui.speed != 1f) add(Format.speed(ui.speed))
                if (ui.abStart != null) add(if (ui.abEnd != null) "A-B aktiv" else "A gesetzt")
                ui.sleepEndsAt?.let { add("Sleep " + Format.time((it - System.currentTimeMillis()).coerceAtLeast(0))) }
                if (ui.sleepAtEnd) add("Sleep: Videoende")
                if (ui.zoomPercent != 100) add("Zoom ${ui.zoomPercent} %")
            }
            if (badges.isNotEmpty()) {
                Row(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    badges.forEach { Badge(it) }
                }
            }
        }

        // ---------------------------------------------------------- Mitte
        Row(
            Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            if (ui.playlist.size > 1) RoundIcon(Icons.Rounded.SkipPrevious, "Vorheriger", 48.dp) { actions.previous() }
            if (ui.seekable) RoundIcon(Icons.Rounded.FastRewind, "Zurückspulen", 52.dp) { actions.seekBy(-ui.seekStepSeconds * 1000L) }
            val playIcon = when {
                ui.ended -> Icons.Rounded.Replay
                ui.isPlaying -> Icons.Rounded.Pause
                else -> Icons.Rounded.PlayArrow
            }
            RoundIcon(playIcon, "Wiedergabe", 76.dp, highlight = true) { actions.togglePlay() }
            if (ui.seekable) RoundIcon(Icons.Rounded.FastForward, "Vorspulen", 52.dp) { actions.seekBy(ui.seekStepSeconds * 1000L) }
            if (ui.playlist.size > 1) RoundIcon(Icons.Rounded.SkipNext, "Nächster", 48.dp) { actions.next() }
        }

        // ---------------------------------------------------------- unten
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            SeekRow(ui, actions)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { actions.toggleLock() }) {
                    Icon(Icons.Rounded.LockOpen, "Sperren", tint = Color.White)
                }
                IconButton(onClick = { actions.openDialog(PlayerDialog.ZOOM) }) {
                    Icon(Icons.Rounded.AspectRatio, "Zoom & Seitenverhältnis", tint = Color.White)
                }
                FrameGenChip(ui) { actions.openDialog(PlayerDialog.FRAME_GEN) }
                Spacer(Modifier.width(4.dp))
                Surface(
                    onClick = { actions.openDialog(PlayerDialog.SPEED) },
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.12f)
                ) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(Format.speed(ui.speed), color = Color.White, fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { actions.snapshot() }) {
                    Icon(Icons.Rounded.PhotoCamera, "Schnappschuss", tint = Color.White)
                }
                IconButton(onClick = { actions.openDialog(PlayerDialog.ORIENTATION) }) {
                    Icon(Icons.Rounded.ScreenRotation, "Ausrichtung", tint = Color.White)
                }
                IconButton(onClick = { actions.enterPip() }) {
                    Icon(Icons.Rounded.PictureInPictureAlt, "Bild-in-Bild", tint = Color.White)
                }
                if (ui.playlist.size > 1) {
                    IconButton(onClick = { actions.openDialog(PlayerDialog.PLAYLIST) }) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, "Playlist", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun SeekRow(ui: PlayerUiState, actions: PlayerActions) {
    var scrubbing by remember { mutableStateOf(false) }
    var scrubValue by remember { mutableFloatStateOf(0f) }
    val duration = ui.duration
    val shownPos = if (scrubbing) (scrubValue * duration).toLong() else ui.position
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            Format.time(shownPos),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            modifier = Modifier.widthIn(min = 56.dp)
        )
        if (ui.seekable && duration > 0) {
            val value = if (scrubbing) scrubValue else (ui.position.toFloat() / duration).coerceIn(0f, 1f)
            Slider(
                value = value,
                onValueChange = {
                    if (!scrubbing) {
                        scrubbing = true
                        actions.scrubStart()
                    }
                    scrubValue = it
                    actions.scrub((it * duration).toLong())
                },
                onValueChangeFinished = {
                    scrubbing = false
                    actions.scrubEnd((scrubValue * duration).toLong())
                },
                colors = SliderDefaults.colors(
                    thumbColor = Accent,
                    activeTrackColor = Accent,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                ),
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        } else {
            Box(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                if (ui.isLive) {
                    Surface(color = Color(0xFFE53935), shape = RoundedCornerShape(4.dp)) {
                        Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                } else {
                    LinearProgressIndicator(
                        progress = { 0f },
                        modifier = Modifier.fillMaxWidth(),
                        color = Accent,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
        }
        val right = when {
            duration <= 0 -> "--:--"
            ui.showRemaining -> "−" + Format.time((duration - shownPos).coerceAtLeast(0))
            else -> Format.time(duration)
        }
        Text(
            right,
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 56.dp).clickable { actions.toggleRemainingTime() }
        )
    }
}

@Composable
private fun FrameGenChip(ui: PlayerUiState, onClick: () -> Unit) {
    val active = ui.frameGenMode != FrameGenMode.OFF
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) Accent.copy(alpha = 0.9f) else Color.White.copy(alpha = 0.12f)
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = if (active) Color.Black else Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            val label = when (ui.frameGenMode) {
                FrameGenMode.OFF -> "FG aus"
                FrameGenMode.BLEND -> "FG Blend"
                FrameGenMode.MOTION -> "FG ${ui.displayHz.toInt()} Hz"
            }
            Text(label, color = if (active) Color.Black else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(color = Color.White.copy(alpha = 0.14f), shape = RoundedCornerShape(6.dp)) {
        Text(text, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

@Composable
fun RoundIcon(icon: ImageVector, description: String, size: androidx.compose.ui.unit.Dp, highlight: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (highlight) Accent.copy(alpha = 0.92f) else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, description, tint = if (highlight) Color.Black else Color.White, modifier = Modifier.size(size * 0.55f))
    }
}

@Composable
private fun FeedbackPill(fb: GestureFeedback, modifier: Modifier) {
    val icon = when (fb.kind) {
        FeedbackKind.SEEK_FORWARD -> Icons.Rounded.FastForward
        FeedbackKind.SEEK_BACK -> Icons.Rounded.FastRewind
        FeedbackKind.BRIGHTNESS -> Icons.Rounded.BrightnessMedium
        FeedbackKind.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
        FeedbackKind.ZOOM -> Icons.Rounded.ZoomIn
        FeedbackKind.SPEED -> Icons.Rounded.Speed
        FeedbackKind.INFO -> Icons.Rounded.Info
    }
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.Black.copy(alpha = 0.7f)) {
        Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Text(fb.text, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            fb.progress?.let { p ->
                Spacer(Modifier.size(10.dp))
                LinearProgressIndicator(
                    progress = { p.coerceIn(0f, 1f) },
                    modifier = Modifier.width(180.dp),
                    color = if (p > 1f) Color(0xFFFF5252) else Accent,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun StatsPanel(ui: PlayerUiState, modifier: Modifier) {
    val s = ui.stats
    Surface(
        modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start))
            .padding(top = if (ui.controlsVisible) 84.dp else 12.dp, start = 12.dp),
        shape = RoundedCornerShape(10.dp),
        color = Color.Black.copy(alpha = 0.6f)
    ) {
        Column(Modifier.padding(10.dp)) {
            val lines = buildList {
                add("Quelle: " + if (s != null && s.sourceFps > 0) String.format(Locale.GERMANY, "%.2f fps", s.sourceFps) else "–")
                add("Ausgabe: " + if (s != null) String.format(Locale.GERMANY, "%.1f fps", s.outputFps) else "–")
                add("Display: " + String.format(Locale.GERMANY, "%.0f Hz", ui.displayHz))
                add("Frame-Gen: " + (s?.activeMode ?: "–") + (s?.let { " · ${it.generatedPercent} % generiert" } ?: ""))
                if (s != null) add("Puffer: ${s.frameWidth}×${s.frameHeight} · Fluss ${s.flowWidth}×${s.flowHeight}")
                if (s != null) add("Timing: " + if (s.timestampMode) "Zeitstempel" else "Ankunft (+1 Bild Latenz)")
                add("Video: " + ui.videoSize)
                ui.decoderName?.let { add("Decoder: $it") }
            }
            lines.forEach { Text(it, color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }
    }
}

@Composable
private fun ErrorCard(message: String, actions: PlayerActions, modifier: Modifier) {
    Surface(modifier.widthIn(max = 480.dp).padding(24.dp), shape = RoundedCornerShape(20.dp), color = Color(0xEE1B1D22)) {
        Column(Modifier.padding(22.dp)) {
            Text("Wiedergabe nicht möglich", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.size(8.dp))
            Text(message, color = Color.White.copy(alpha = 0.85f), maxLines = 8, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.size(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { actions.retry() }) { Text("Erneut versuchen") }
                OutlinedButton(onClick = { actions.close() }) { Text("Schließen") }
            }
        }
    }
}
