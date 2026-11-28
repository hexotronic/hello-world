package com.hexotronic.fluxplayer.ui.home

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hexotronic.fluxplayer.PlayerActivity
import com.hexotronic.fluxplayer.data.Prefs
import com.hexotronic.fluxplayer.ui.theme.Accent

@Composable
fun NetworkScreen() {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var url by rememberSaveable { mutableStateOf("") }
    var referer by rememberSaveable { mutableStateOf("") }
    var userAgent by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var recent by remember { mutableStateOf(prefs.urlHistory) }

    fun play(target: String) {
        val u = target.trim()
        if (u.isEmpty()) return
        prefs.addUrlToHistory(u)
        recent = prefs.urlHistory
        val headers = buildMap {
            if (referer.isNotBlank()) put("Referer", referer.trim())
            if (userAgent.isNotBlank()) put("User-Agent", userAgent.trim())
        }
        PlayerActivity.playUrl(context, u, headers)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Stream oder Video-Link öffnen", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Link, null) },
                        trailingIcon = {
                            if (url.isNotEmpty()) IconButton(onClick = { url = "" }) { Icon(Icons.Rounded.Close, "Leeren") }
                        },
                        placeholder = { Text("https://…/video.mp4, .m3u8, rtsp://…") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { play(url) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { play(url) }, enabled = url.isNotBlank()) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Abspielen")
                        }
                        OutlinedButton(onClick = {
                            val clip = (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
                            if (!clip.isNullOrBlank()) url = clip.trim()
                        }) {
                            Icon(Icons.Rounded.ContentPaste, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Einfügen")
                        }
                    }
                    TextButton(onClick = { advanced = !advanced }) {
                        Text("Erweitert (Header)")
                        Icon(if (advanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(advanced) {
                        Column {
                            OutlinedTextField(
                                value = referer, onValueChange = { referer = it }, singleLine = true,
                                label = { Text("Referer (optional)") }, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = userAgent, onValueChange = { userAgent = it }, singleLine = true,
                                label = { Text("User-Agent (optional)") }, modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Unterstützt: direkte Videodateien (MP4, MKV, WebM, AVI, MOV, TS …), HLS (.m3u8), DASH (.mpd), " +
                    "Smooth Streaming, RTSP, RTMP, UDP, IPTV-/M3U- und PLS-Playlists sowie Webseiten mit eingebettetem " +
                    "<video>-Element. Links können auch per „Teilen“ aus dem Browser an FluxPlayer geschickt werden.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (recent.isNotEmpty()) {
                Spacer(Modifier.height(18.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zuletzt geöffnet", style = MaterialTheme.typography.titleSmall, color = Accent, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        prefs.urlHistory = emptyList()
                        recent = emptyList()
                    }) { Text("Leeren") }
                }
            }
        }
        items(recent, key = { it }) { u ->
            Row(
                Modifier.fillMaxWidth().clickable { play(u) }.padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(12.dp))
                Text(u, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium)
                IconButton(onClick = {
                    prefs.urlHistory = prefs.urlHistory.filter { it != u }
                    recent = prefs.urlHistory
                }) { Icon(Icons.Rounded.Close, "Entfernen") }
            }
            HorizontalDivider()
        }
    }
}
