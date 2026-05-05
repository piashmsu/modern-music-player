package com.gsmtrick.musicplayer.effects

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Plays a short haptic pulse on every detected beat. Pulse strength
 * scales with the bass intensity at the time of the onset so you feel
 * a lighter tap on a soft kick and a firmer one on a loud kick.
 *
 * VIBRATE is a normal (non-runtime) permission so this works as soon
 * as the user toggles the feature on.
 */
class BeatHaptics(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private var lastBeat = -1
    private val vibrator: Vibrator? = run {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    fun start() {
        if (job != null) return
        if (vibrator == null || vibrator.hasVibrator() != true) return
        job = scope.launch {
            BeatBus.pulses.collect { p ->
                if (p.beat != lastBeat) {
                    lastBeat = p.beat
                    pulse(p.bass)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun pulse(bass: Float) {
        val v = vibrator ?: return
        val amplitude = (60 + bass * 195f).toInt().coerceIn(40, 255)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(40L, amplitude))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(40L)
            }
        }
    }
}
