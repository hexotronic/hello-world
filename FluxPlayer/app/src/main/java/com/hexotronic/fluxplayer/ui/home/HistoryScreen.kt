package com.hexotronic.fluxplayer.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.hexotronic.fluxplayer.PlayerActivity
import com.hexotronic.fluxplayer.data.HistoryStore
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.ui.theme.Accent
import com.hexotronic.fluxplayer.util.Format

@Composable
fun HistoryScreen() {
    val context = LocalContext.current
    val store = remember { HistoryStore(context) }
    var entries by remember { mutableStateOf(store.all()) }
    var confirmClear by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { entries = store.all() }

    if (entries.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Rounded.History, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("Noch nichts abgespielt", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${entries.size} Einträge", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { confirmClear = true }) { Text("Verlauf löschen") }
            }
        }
        items(entries, key = { it.uri }) { e ->
            val network = e.uri.startsWith("http") || e.uri.startsWith("rt")
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (network) PlayerActivity.playUrl(context, e.uri)
                    else PlayerActivity.playEntries(context, listOf(MediaEntry(e.uri, e.title)), 0)
                }.padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(if (network) Icons.Rounded.Language else Icons.Rounded.Movie, null, tint = Accent)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val detail = buildList {
                        add(Format.ago(e.lastPlayed))
                        if (e.duration > 0) add("${Format.time(e.position)} / ${Format.time(e.duration)}")
                    }.joinToString(" · ")
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.progress > 0.01f) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(progress = { e.progress }, modifier = Modifier.fillMaxWidth(), color = Accent)
                    }
                }
                IconButton(onClick = {
                    store.remove(e.uri)
                    entries = store.all()
                }) { Icon(Icons.Rounded.Delete, "Entfernen") }
            }
            HorizontalDivider(Modifier.padding(start = 54.dp))
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Verlauf löschen?") },
            text = { Text("Alle gespeicherten Wiedergabepositionen werden entfernt.") },
            confirmButton = {
                TextButton(onClick = {
                    store.clear()
                    entries = emptyList()
                    confirmClear = false
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Abbrechen") } }
        )
    }
}
