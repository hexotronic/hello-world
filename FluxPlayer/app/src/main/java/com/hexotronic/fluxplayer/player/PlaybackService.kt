package com.hexotronic.fluxplayer.player

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hexotronic.fluxplayer.PlayerActivity

/** Media-Session + Benachrichtigung, ermöglicht Hintergrundwiedergabe und Sperrbildschirm-Steuerung. */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = PlayerHolder.get(this)
        val intent = Intent(this, PlayerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(pending)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            p?.pause()
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
