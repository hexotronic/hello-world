package io.github.hexotronic.lauscher.playback

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.sqrt

/** Detects a firm shake of the phone (used to extend the sleep timer in the dark). */
class ShakeDetector(private val context: Context, private val onShake: () -> Unit) : SensorEventListener {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager?
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var registered = false
    private var lastShake = 0L
    private var hits = 0
    private var firstHit = 0L

    fun setActive(active: Boolean) {
        if (active == registered || accelerometer == null) return
        if (active) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager?.unregisterListener(this)
        }
        registered = active
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0] / SensorManager.GRAVITY_EARTH
        val y = event.values[1] / SensorManager.GRAVITY_EARTH
        val z = event.values[2] / SensorManager.GRAVITY_EARTH
        val g = sqrt(x * x + y * y + z * z)
        if (g < THRESHOLD_G) return
        val now = SystemClock.elapsedRealtime()
        if (now - firstHit > WINDOW_MS) {
            firstHit = now
            hits = 0
        }
        hits++
        if (hits >= REQUIRED_HITS && now - lastShake > COOLDOWN_MS) {
            lastShake = now
            hits = 0
            vibrate()
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    @Suppress("DEPRECATION")
    private fun vibrate() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator? ?: return
        runCatching { vibrator.vibrate(VibrationEffect.createOneShot(90, VibrationEffect.DEFAULT_AMPLITUDE)) }
    }

    private companion object {
        const val THRESHOLD_G = 2.3f
        const val WINDOW_MS = 700L
        const val REQUIRED_HITS = 3
        const val COOLDOWN_MS = 2000L
    }
}
