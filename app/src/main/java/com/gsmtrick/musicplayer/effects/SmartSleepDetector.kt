package com.gsmtrick.musicplayer.effects

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * v3.4 — Listens to the accelerometer and notifies its callback when the
 * device has been still for at least [idleTimeoutMs]. Used by the
 * playback service to fade-out playback when the user has fallen asleep.
 *
 * The detection is deliberately tolerant: we only count motion above a
 * conservative magnitude threshold so that ambient vibrations (a phone
 * laying on a desk near a speaker) don't reset the timer.
 */
class SmartSleepDetector(
    private val context: Context,
    private val idleTimeoutMs: Long,
    private val onIdle: () -> Unit,
) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    @Volatile
    private var lastMotionAt = SystemClock.elapsedRealtime()

    @Volatile
    private var registered = false

    @Volatile
    private var fired = false

    /** Start watching for motion. No-op if already running. */
    fun start() {
        if (registered || accelerometer == null || sensorManager == null) return
        lastMotionAt = SystemClock.elapsedRealtime()
        fired = false
        registered = sensorManager.registerListener(
            this,
            accelerometer,
            SensorManager.SENSOR_DELAY_NORMAL,
        )
    }

    fun stop() {
        if (!registered) return
        sensorManager?.unregisterListener(this)
        registered = false
    }

    /** Caller should poll this every minute or so from a coroutine. */
    fun checkIdle(now: Long = SystemClock.elapsedRealtime()) {
        if (!registered || fired) return
        if (now - lastMotionAt >= idleTimeoutMs) {
            fired = true
            try {
                onIdle()
            } finally {
                stop()
            }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
        val ax = event.values[0]
        val ay = event.values[1]
        val az = event.values[2]
        // Subtract gravity (~9.81) and threshold to detect motion.
        val mag = sqrt(ax * ax + ay * ay + az * az)
        val delta = abs(mag - 9.81f)
        if (delta > 0.4f) {
            lastMotionAt = SystemClock.elapsedRealtime()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
