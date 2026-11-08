package io.github.hexotronic.lauscher.voice

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.github.hexotronic.lauscher.LauscherApp
import io.github.hexotronic.lauscher.MainActivity
import io.github.hexotronic.lauscher.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the microphone open for voice commands while the screen is off (phone in a pocket).
 * Android shows its microphone indicator meanwhile; the notification can switch it off.
 */
class VoiceControlService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val voice get() = (application as LauscherApp).voice

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Sprachsteuerung", NotificationManager.IMPORTANCE_LOW))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Every start (startForegroundService) has to be answered with startForeground.
        val started = runCatching {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification(),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0,
            )
        }
        if (started.isFailure) {
            Log.w(VoiceControl.TAG, "Sprachsteuerung kann das Mikrofon nicht öffnen", started.exceptionOrNull())
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_OFF) {
            voice.setEnabled(false)
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive != true) {
            job = scope.launch {
                voice.run()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val wake = voice.store.current.commands.let { if (it.wakeWordEnabled) "${it.wakeWord}, " else "" }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Sprachsteuerung hört zu")
            .setContentText("Zum Beispiel: „${wake}Pause“ oder „${wake}30 Sekunden zurück“")
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 4, Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, "voice"),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .addAction(
                R.drawable.ic_pause,
                "Ausschalten",
                PendingIntent.getService(this, 5, Intent(this, VoiceControlService::class.java).setAction(ACTION_OFF), PendingIntent.FLAG_IMMUTABLE),
            )
            .build()
    }

    override fun onDestroy() {
        job?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "voice"
        private const val NOTIFICATION_ID = 9
        private const val ACTION_OFF = "io.github.hexotronic.lauscher.voice.OFF"

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, VoiceControlService::class.java)) }
                .onFailure { Log.w(VoiceControl.TAG, "Sprachsteuerung konnte nicht starten", it) }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, VoiceControlService::class.java))
        }
    }
}
