package com.hexotronic.fluxplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.hexotronic.fluxplayer.data.MediaEntry
import com.hexotronic.fluxplayer.data.MediaRepository
import com.hexotronic.fluxplayer.net.StreamResolver
import com.hexotronic.fluxplayer.ui.home.HomeScreen
import com.hexotronic.fluxplayer.ui.theme.FluxTheme

class MainActivity : ComponentActivity() {

    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) openUri(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FluxTheme {
                HomeScreen(onOpenFile = { launchPicker() })
            }
        }
    }

    private fun launchPicker() {
        try {
            openFile.launch(
                arrayOf(
                    "video/*", "audio/*", "application/x-mpegurl", "audio/x-mpegurl",
                    "application/vnd.apple.mpegurl", "audio/x-scpls", "application/octet-stream"
                )
            )
        } catch (_: Exception) {
        }
    }

    private fun openUri(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
        }
        val name = MediaRepository.displayName(this, uri)
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext in StreamResolver.PLAYLIST_EXT) {
            PlayerActivity.playUrl(this, uri.toString())
        } else {
            PlayerActivity.playEntries(this, listOf(MediaEntry(uri.toString(), name)), 0)
        }
    }
}
