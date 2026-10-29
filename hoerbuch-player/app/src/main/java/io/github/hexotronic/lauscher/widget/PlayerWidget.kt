package io.github.hexotronic.lauscher.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaButtonReceiver
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.MainActivity
import io.github.hexotronic.lauscher.R
import io.github.hexotronic.lauscher.playback.NowPlaying
import io.github.hexotronic.lauscher.playback.PlaybackHub
import kotlinx.coroutines.launch

/** Home screen widget: cover, title, chapter, progress and rewind/play/forward buttons. */
class PlayerWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val state = PlaybackHub.core.value?.state?.value
        if (state?.book != null) {
            WidgetUpdater.render(context, WidgetUpdater.Content.from(state))
            return
        }
        val pending = goAsync()
        val app = context.applicationContext as LauscherApp
        app.appScope.launch {
            try {
                val book = app.repository.lastPlayedBook()
                WidgetUpdater.render(
                    context,
                    WidgetUpdater.Content(
                        title = book?.title,
                        subtitle = book?.author,
                        progress = book?.progress ?: 0f,
                        playing = false,
                        coverPath = book?.coverPath,
                    ),
                )
            } finally {
                pending.finish()
            }
        }
    }
}

/** Receives rewind/forward taps from the widget. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val core = PlaybackHub.core.value ?: return
        when (intent.action) {
            ACTION_REWIND -> core.rewind()
            ACTION_FORWARD -> core.forward()
        }
    }

    companion object {
        const val ACTION_REWIND = "io.github.hexotronic.lauscher.widget.REWIND"
        const val ACTION_FORWARD = "io.github.hexotronic.lauscher.widget.FORWARD"
    }
}

object WidgetUpdater {
    data class Content(
        val title: String?,
        val subtitle: String?,
        val progress: Float,
        val playing: Boolean,
        val coverPath: String?,
    ) {
        companion object {
            fun from(s: NowPlaying) = Content(
                title = s.book?.title,
                subtitle = s.chapter?.title?.takeIf { s.chapters.size > 1 } ?: s.book?.author,
                progress = if (s.durationMs > 0) s.positionMs.toFloat() / s.durationMs else 0f,
                playing = s.playWhenReady,
                coverPath = s.book?.coverPath,
            )
        }
    }

    private var lastKey: String? = null
    private var lastRenderAt = 0L
    private var cachedCoverPath: String? = null
    private var cachedCover: Bitmap? = null

    /** Called on every player refresh; only touches the widget when something visible changed. */
    fun onState(context: Context, state: NowPlaying) {
        val content = Content.from(state)
        val key = "${content.title}|${content.subtitle}|${content.playing}|${content.coverPath}"
        val now = SystemClock.elapsedRealtime()
        if (key == lastKey && now - lastRenderAt < 15_000) return
        lastKey = key
        lastRenderAt = now
        render(context, content)
    }

    fun render(context: Context, content: Content) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, PlayerWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(context.packageName, R.layout.widget_player)
        views.setTextViewText(R.id.widget_title, content.title ?: context.getString(R.string.widget_empty))
        views.setTextViewText(R.id.widget_subtitle, content.subtitle.orEmpty())
        views.setProgressBar(R.id.widget_progress, 1000, (content.progress * 1000).toInt(), false)
        views.setImageViewResource(R.id.widget_play, if (content.playing) R.drawable.widget_ic_pause else R.drawable.widget_ic_play)
        val cover = cover(content.coverPath)
        if (cover != null) {
            views.setImageViewBitmap(R.id.widget_cover, cover)
        } else {
            views.setImageViewResource(R.id.widget_cover, R.drawable.widget_cover_placeholder)
        }

        val open = PendingIntent.getActivity(
            context, 10,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_PLAYER, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_cover, open)
        views.setOnClickPendingIntent(R.id.widget_title, open)
        views.setOnClickPendingIntent(R.id.widget_play, playPauseIntent(context))
        views.setOnClickPendingIntent(R.id.widget_rewind, actionIntent(context, WidgetActionReceiver.ACTION_REWIND, 11))
        views.setOnClickPendingIntent(R.id.widget_forward, actionIntent(context, WidgetActionReceiver.ACTION_FORWARD, 12))
        manager.updateAppWidget(ids, views)
    }

    /** Goes through Media3's MediaButtonReceiver so playback can even resume when the app is closed. */
    @OptIn(UnstableApi::class)
    private fun playPauseIntent(context: Context): PendingIntent {
        val intent = Intent(Intent.ACTION_MEDIA_BUTTON)
            .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        return PendingIntent.getBroadcast(context, 13, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun actionIntent(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode,
            Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun cover(path: String?): Bitmap? {
        if (path == null) return null
        if (path == cachedCoverPath) return cachedCover
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 240) sample *= 2
        cachedCover = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        cachedCoverPath = path
        return cachedCover
    }
}
