package com.hexotronic.fluxplayer

import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.hexotronic.fluxplayer.player.PlayerHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit
import android.app.Application
import android.content.ComponentName
import com.hexotronic.fluxplayer.player.PlaybackService
import org.junit.Before

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PlayerActivityTest {

    @Before
    fun noServiceBinding() {
        // Robolectric liefert beim Binden keinen echten MediaSessionService-Binder.
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).declareComponentUnbindable(ComponentName(app, PlaybackService::class.java))
    }

    private fun idle(ms: Long = 300) {
        val end = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < end) {
            shadowOf(Looper.getMainLooper()).idleFor(50, TimeUnit.MILLISECONDS)
            Thread.sleep(10)
        }
    }

    @Test
    fun lifecycleWithMissingFileDoesNotCrash() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(ctx, PlayerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("file:///sdcard/gibtsnicht.mp4"), "video/mp4")
        val controller = Robolectric.buildActivity(PlayerActivity::class.java, intent).setup()
        idle(1500)
        val player = PlayerHolder.get(ctx)
        assertEquals(1, player.mediaItemCount)
        assertEquals("file:///sdcard/gibtsnicht.mp4", player.currentMediaItem?.mediaId)
        controller.pause().stop()
        idle()
        controller.destroy()
        idle()
        assertEquals(0, player.mediaItemCount)
    }

    @Test
    fun extremeSlowMotionMutesAudioAndRestoresIt() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(ctx, PlayerActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("file:///sdcard/zeitlupe.mp4"), "video/mp4")
        val controller = Robolectric.buildActivity(PlayerActivity::class.java, intent).setup()
        idle(800)
        val activity = controller.get()
        val player = PlayerHolder.get(ctx)

        activity.setSpeed(0.05f)
        idle(500)
        assertEquals(0.05f, player.playbackParameters.speed, 0.0001f)
        assertTrue(player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_AUDIO))

        activity.setSpeed(0.25f)
        idle(500)
        assertEquals(0.25f, player.playbackParameters.speed, 0.0001f)
        assertTrue(!player.trackSelectionParameters.disabledTrackTypes.contains(androidx.media3.common.C.TRACK_TYPE_AUDIO))

        activity.setSpeed(0.01f) // unter Minimum -> 0,05
        idle(300)
        assertEquals(0.05f, player.playbackParameters.speed, 0.0001f)
        activity.setSpeed(1f)
        controller.pause().stop().destroy()
        idle()
    }

    @Test
    fun sharedTextStartsResolver() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(ctx, PlayerActivity::class.java)
            .setAction(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "Schau mal: rtsp://192.0.2.1:554/stream bitte")
        val controller = Robolectric.buildActivity(PlayerActivity::class.java, intent).setup()
        idle(1500)
        val player = PlayerHolder.get(ctx)
        assertTrue(player.mediaItemCount == 1)
        assertEquals("rtsp://192.0.2.1:554/stream", player.currentMediaItem?.mediaId)
        controller.pause().stop().destroy()
        idle()
    }
}
