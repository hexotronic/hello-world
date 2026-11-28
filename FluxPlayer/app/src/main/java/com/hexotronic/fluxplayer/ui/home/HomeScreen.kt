package com.hexotronic.fluxplayer.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import com.hexotronic.fluxplayer.ui.theme.Accent

private data class Tab(val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Videos", Icons.Rounded.VideoLibrary),
    Tab("Netzwerk", Icons.Rounded.Language),
    Tab("Verlauf", Icons.Rounded.History),
    Tab("Einstellungen", Icons.Rounded.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onOpenFile: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected == 0) "FluxPlayer" else tabs[selected].title,
                        fontWeight = FontWeight.Bold,
                        color = if (selected == 0) Accent else MaterialTheme.colorScheme.onSurface
                    )
                },
                actions = {
                    IconButton(onClick = onOpenFile) {
                        Icon(Icons.Rounded.FolderOpen, "Datei öffnen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = selected == i,
                        onClick = { selected = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (selected) {
                0 -> VideosScreen()
                1 -> NetworkScreen()
                2 -> HistoryScreen()
                else -> SettingsScreen()
            }
        }
    }
}
