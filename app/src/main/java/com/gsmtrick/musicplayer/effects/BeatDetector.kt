package com.gsmtrick.musicplayer.effects

import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

/**
 * Drives [BeatBus] from the system [Visualizer] effect attached to a
 * particular audio session id.
 *
 * We use the FFT capture (not the waveform) and split the spectrum into
 * three bands — bass (low ~20-200Hz portion of the buffer), mid and
 * high — each smoothed with a one-pole IIR. A simple onset detector
 * fires whenever the bass band rises sharply above an adaptive
 * baseline, producing the beat counter that downstream effects
 * (edge lighting, flashlight, vibration) react to.
 *
 * One detector instance per audio session is enough; the playback
 * service owns it and re-attaches whenever ExoPlayer reports a new
 * session id. Failures (denied permission, unsupported SoC) are
 * swallowed — beat-driven UI just stays at zero in that case.
 */
class BeatDetector {

    private var visualizer: Visualizer? = null
    private var attached: Int = 0

    private var bass = 0f
    private var mid = 0f
    private var high = 0f
    private var bassBaseline = 0f
    private var beatCount = 0
    private var ticksSinceBeat = 0

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (audioSessionId == attached && visualizer != null) return
        release()
        attached = audioSessionId
        runCatching {
            val captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer = Visualizer(audioSessionId).apply {
                this.captureSize = captureSize
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {
                        }

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            onFft(fft ?: return)
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    /* waveform = */ false,
                    /* fft = */ true,
                )
                enabled = true
            }
        }.onFailure {
            Log.w(TAG, "BeatDetector attach failed", it)
            visualizer = null
        }
    }

    fun release() {
        runCatching { visualizer?.enabled = false }
        runCatching { visualizer?.release() }
        visualizer = null
        attached = 0
        bass = 0f
        mid = 0f
        high = 0f
        bassBaseline = 0f
        BeatBus.clear()
    }

    private fun onFft(data: ByteArray) {
        val n = data.size / 2
        if (n <= 0) return
        // Three buckets: bass = first ~12% of bins, mid = next ~38%, high = the rest.
        val bassEnd = max(2, (n * 0.12f).toInt())
        val midEnd = max(bassEnd + 1, (n * 0.5f).toInt())

        val bassRaw = bandMagnitude(data, 1, bassEnd)
        val midRaw = bandMagnitude(data, bassEnd, midEnd)
        val highRaw = bandMagnitude(data, midEnd, n)

        // One-pole low-pass smoothing for display values.
        bass = bass * SMOOTH + bassRaw * (1f - SMOOTH)
        mid = mid * SMOOTH + midRaw * (1f - SMOOTH)
        high = high * SMOOTH + highRaw * (1f - SMOOTH)

        // Adaptive baseline (slow EMA) for onset detection.
        bassBaseline = bassBaseline * BASELINE + bassRaw * (1f - BASELINE)

        ticksSinceBeat++
        val threshold = bassBaseline * BEAT_GAIN + BEAT_FLOOR
        if (bassRaw > threshold && ticksSinceBeat >= MIN_BEAT_GAP_TICKS) {
            beatCount = (beatCount + 1) and 0x7FFFFFFF
            ticksSinceBeat = 0
        }

        BeatBus.publish(
            BeatPulse(
                bass = bass.coerceIn(0f, 1f),
                mid = mid.coerceIn(0f, 1f),
                high = high.coerceIn(0f, 1f),
                intensity = bass.coerceIn(0f, 1f),
                beat = beatCount,
                active = true,
            ),
        )
    }

    private fun bandMagnitude(data: ByteArray, fromBin: Int, toBin: Int): Float {
        val end = toBin.coerceAtMost(data.size / 2)
        if (end <= fromBin) return 0f
        var sum = 0.0
        for (k in fromBin until end) {
            val real = data.getOrElse(2 * k) { 0 }.toInt()
            val imag = data.getOrElse(2 * k + 1) { 0 }.toInt()
            sum += hypot(real.toDouble(), imag.toDouble())
        }
        val avg = (sum / (end - fromBin).coerceAtLeast(1)) / 128.0
        return avg.pow(0.5).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        private const val TAG = "BeatDetector"

        // Display smoothing: 0..1, higher = smoother / slower fall.
        private const val SMOOTH = 0.55f

        // Baseline EMA — much slower so it tracks "average loudness".
        private const val BASELINE = 0.92f

        // Beat triggers when current bass > baseline*GAIN + FLOOR.
        private const val BEAT_GAIN = 1.45f
        private const val BEAT_FLOOR = 0.05f

        // ~50ms per FFT tick at the rates we ask for; 6 ticks ~= 300ms
        // refractory window, which prevents double-counting a single
        // kick as multiple beats but still allows ~200 BPM.
        private const val MIN_BEAT_GAP_TICKS = 6
    }
}
