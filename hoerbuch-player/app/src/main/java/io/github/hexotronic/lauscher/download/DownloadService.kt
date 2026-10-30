package io.github.hexotronic.lauscher.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.MainActivity
import io.github.hexotronic.lauscher.R
import io.github.hexotronic.lauscher.download.core.TransferEstimate
import io.github.hexotronic.lauscher.ui.formatHuman
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps Lauscher running while downloads are in progress and shows how far they are, with
 * "Pausieren"; when everything is paused it stops and leaves a note with "Fortsetzen".
 */
class DownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watching = false
    private lateinit var manager: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Downloads", NotificationManager.IMPORTANCE_LOW))
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            running("Downloads", "Wird vorbereitet …", 0f),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloads = (application as LauscherApp).downloads
        when (intent?.action) {
            ACTION_PAUSE -> downloads.pauseAll()
            ACTION_RESUME -> downloads.resumeAll()
        }
        if (!watching) {
            watching = true
            scope.launch { downloads.jobs.collect(::show) }
        }
        return START_NOT_STICKY
    }

    private fun show(jobs: List<DownloadJob>) {
        val active = jobs.filter { it.active }
        if (active.isEmpty()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            val paused = jobs.count { it.state == JobState.PAUSED }
            if (paused > 0) manager.notify(PAUSED_ID, pausedNote(paused)) else manager.cancel(PAUSED_ID)
            stopSelf()
            return
        }
        manager.cancel(PAUSED_ID)
        val job = active.firstOrNull { it.state == JobState.RUNNING } ?: active.first()
        val text = buildString {
            if (job.total > 1) append("${minOf(job.done + 1, job.total)} von ${job.total}: ")
            append(job.current ?: "wartet")
        }
        val stats = listOfNotNull(
            job.bytesPerSecond.takeIf { it > 0 }?.let { TransferEstimate.size(it) + "/s" },
            job.remainingMs?.let { "noch ca. ${formatHuman(it)}" },
            (active.size - 1).takeIf { it > 0 }?.let { "+$it weitere" },
        ).joinToString(" · ")
        manager.notify(NOTIFICATION_ID, running(job.title, if (stats.isEmpty()) text else "$text\n$stats", job.overall))
    }

    private fun running(title: String, text: String, progress: Float): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(1000, (progress * 1000).toInt(), progress <= 0f)
            .setContentIntent(openApp())
            .addAction(
                R.drawable.ic_pause,
                "Pausieren",
                PendingIntent.getService(this, 1, Intent(this, DownloadService::class.java).setAction(ACTION_PAUSE), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()

    private fun pausedNote(count: Int): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (count == 1) "Download pausiert" else "$count Downloads pausiert")
            .setContentText("Antippen zum Ansehen, oder gleich fortsetzen")
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(
                R.drawable.ic_play,
                "Fortsetzen",
                PendingIntent.getForegroundService(this, 2, Intent(this, DownloadService::class.java).setAction(ACTION_RESUME), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "downloads"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "downloads"
        private const val NOTIFICATION_ID = 7
        private const val PAUSED_ID = 8
        private const val ACTION_PAUSE = "io.github.hexotronic.lauscher.download.PAUSE"
        private const val ACTION_RESUME = "io.github.hexotronic.lauscher.download.RESUME"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java)) }
        }
    }
}
