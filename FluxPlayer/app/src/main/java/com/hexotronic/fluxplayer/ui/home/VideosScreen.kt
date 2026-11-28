package com.hexotronic.fluxplayer.ui.home

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hexotronic.fluxplayer.PlayerActivity
import com.hexotronic.fluxplayer.data.HistoryEntry
import com.hexotronic.fluxplayer.data.HistoryStore
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.data.MediaRepository
import com.hexotronic.fluxplayer.data.Prefs
import com.hexotronic.fluxplayer.data.SortOrder
import com.hexotronic.fluxplayer.data.ThumbCache
import com.hexotronic.fluxplayer.data.VideoFolder
import com.hexotronic.fluxplayer.data.VideoItem
import com.hexotronic.fluxplayer.ui.theme.Accent
import com.hexotronic.fluxplayer.util.Format
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun mediaPermissions(): Array<String> = when {
    Build.VERSION.SDK_INT >= 34 -> arrayOf(
        Manifest.permission.READ_MEDIA_VIDEO,
        Manifest.permission.READ_MEDIA_AUDIO,
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    )
    Build.VERSION.SDK_INT >= 33 -> arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun hasMediaPermission(context: Context): Boolean {
    fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
    return when {
        Build.VERSION.SDK_INT >= 34 ->
            granted(Manifest.permission.READ_MEDIA_VIDEO) || granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
        Build.VERSION.SDK_INT >= 33 -> granted(Manifest.permission.READ_MEDIA_VIDEO)
        else -> granted(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
}

@Composable
fun VideosScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val historyStore = remember { HistoryStore(context) }
    var permission by remember { mutableStateOf(hasMediaPermission(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permission = hasMediaPermission(context)
        refresh++
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permission = hasMediaPermission(context)
        refresh++
    }

    if (!permission) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.VideoLibrary, null, tint = Accent, modifier = Modifier.size(72.dp))
            Spacer(Modifier.height(16.dp))
            Text("Zugriff auf deine Videos", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "FluxPlayer braucht die Berechtigung, um Videos auf deinem Gerät zu finden. " +
                    "Netzwerk-Streams und „Datei öffnen“ funktionieren auch ohne.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Button(onClick = { launcher.launch(mediaPermissions()) }) { Text("Zugriff erlauben") }
        }
        return
    }

    var loading by remember { mutableStateOf(true) }
    var videos by remember { mutableStateOf<List<VideoItem>>(emptyList()) }
    var history by remember { mutableStateOf<Map<String, HistoryEntry>>(emptyMap()) }
    LaunchedEffect(refresh) {
        val (v, h) = withContext(Dispatchers.IO) {
            MediaRepository.loadVideos(context) to historyStore.all().associateBy { it.uri }
        }
        videos = v
        history = h
        loading = false
    }

    var showFolders by rememberSaveable { mutableStateOf(true) }
    var openFolderId by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(prefs.sortOrder) }
    var desc by remember { mutableStateOf(prefs.sortDescending) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }

    val folders = remember(videos) { MediaRepository.folders(videos) }
    val openFolder = folders.firstOrNull { it.id == openFolderId }
    BackHandler(enabled = openFolder != null || searching) {
        if (searching) {
            searching = false
            query = ""
        } else openFolderId = null
    }

    Column(Modifier.fillMaxSize()) {
        // Kopfzeile
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (openFolder != null && !searching) {
                IconButton(onClick = { openFolderId = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Zurück") }
                Text(openFolder.name, style = MaterialTheme.typography.titleMedium, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            } else if (searching) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Videos durchsuchen") },
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                )
            } else {
                FilterChip(selected = showFolders, onClick = { showFolders = true }, label = { Text("Ordner") })
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = !showFolders, onClick = { showFolders = false }, label = { Text("Alle Videos") })
                Spacer(Modifier.weight(1f))
            }
            if (!searching) {
                IconButton(onClick = { searching = true }) { Icon(Icons.Rounded.Search, "Suchen") }
            }
            SortMenu(sort, desc) { s, d ->
                sort = s; desc = d
                prefs.sortOrder = s; prefs.sortDescending = d
            }
            IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, "Aktualisieren") }
        }

        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            videos.isEmpty() -> EmptyHint()
            searching -> {
                val list = remember(videos, query, sort, desc) {
                    MediaRepository.sort(videos.filter { it.name.contains(query.trim(), ignoreCase = true) }, sort, desc)
                }
                VideoList(list, history)
            }
            openFolder != null -> VideoList(MediaRepository.sort(openFolder.videos, sort, desc), history)
            showFolders -> FolderList(folders) { openFolderId = it.id }
            else -> VideoList(MediaRepository.sort(videos, sort, desc), history)
        }
    }
}

@Composable
private fun SortMenu(sort: SortOrder, desc: Boolean, onChange: (SortOrder, Boolean) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sortieren") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(s.label + if (s == sort) (if (desc) "  ↓" else "  ↑") else "") },
                    onClick = {
                        onChange(s, if (s == sort) !desc else desc)
                        open = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.Movie, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Keine Videos gefunden", style = MaterialTheme.typography.titleMedium)
        Text(
            "Öffne eine Datei über das Ordnersymbol oben oder spiele einen Stream im Tab „Netzwerk“ ab.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FolderList(folders: List<VideoFolder>, onOpen: (VideoFolder) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(folders, key = { it.id }) { f ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(f) }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Folder, null, tint = Accent, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${f.videos.size} Video${if (f.videos.size == 1) "" else "s"} · ${Format.size(f.totalSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun VideoList(list: List<VideoItem>, history: Map<String, HistoryEntry>) {
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        items(list.size, key = { list[it].id }) { index ->
            val v = list[index]
            VideoRow(v, history[v.uri.toString()]) {
                val entries = list.map { MediaEntry(it.uri.toString(), it.name) }
                PlayerActivity.playEntries(context, entries, index)
            }
        }
    }
}

@Composable
private fun VideoRow(v: VideoItem, h: HistoryEntry?, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState(initialValue = ThumbCache.peek(v.uri.toString()), v.uri) {
        if (value == null) value = withContext(Dispatchers.IO) { ThumbCache.load(context, v) }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Color(0xFF23262C))) {
            val bmp = thumb
            if (bmp != null) {
                Image(bmp.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Icon(Icons.Rounded.PlayArrow, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.align(Alignment.Center))
            }
            if (v.durationMs > 0) {
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp)
                ) {
                    Text(Format.time(v.durationMs), color = Color.White, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }
            if (h != null && h.progress > 0.01f) {
                LinearProgressIndicator(
                    progress = { h.progress },
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp),
                    color = Accent,
                    trackColor = Color.Black.copy(alpha = 0.4f)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(v.name, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            val meta = listOf(Format.resolution(v.width, v.height), Format.size(v.size)).filter { it.isNotEmpty() }
            Text(meta.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
