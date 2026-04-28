package com.gsmtrick.musicplayer.ui.screens

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow

/**
 * Lightweight audio spectrum visualizer that latches onto the currently
 * playing audio session via the system [Visualizer] effect.
 */
@Composable
fun AudioSpectrum(
    audioSessionId: Int?,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 96.dp,
    bars: Int = 48,
) {
    val magnitudes = remember { FloatArray(bars) }
    var version by remember { mutableStateOf(0) }

    DisposableEffect(audioSessionId) {
        val session = audioSessionId
        if (session == null || session == 0) {
            return@DisposableEffect onDispose { }
        }
        val viz = runCatching {
            Visualizer(session).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            v: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int,
                        ) {}

                        override fun onFftDataCapture(
                            v: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int,
                        ) {
                            val data = fft ?: return
                            val n = data.size / 2
                            val perBar = (n / bars).coerceAtLeast(1)
                            for (i in 0 until bars) {
                                var sum = 0.0
                                val start = i * perBar
                                val end = ((i + 1) * perBar).coerceAtMost(n)
                                for (k in start until end) {
                                    val real = data.getOrElse(2 * k) { 0 }.toInt()
                                    val imag = data.getOrElse(2 * k + 1) { 0 }.toInt()
                                    sum += hypot(real.toDouble(), imag.toDouble())
                                }
                                val avg = (sum / (end - start).coerceAtLeast(1)) / 128.0
                                val target = avg.pow(0.4).toFloat().coerceIn(0f, 1f)
                                magnitudes[i] = magnitudes[i] * 0.7f + target * 0.3f
                            }
                            version = (version + 1) and 0xFFFF
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    /* waveform = */ false,
                    /* fft = */ true,
                )
                enabled = true
            }
        }.getOrNull()
        onDispose {
            runCatching { viz?.enabled = false }
            runCatching { viz?.release() }
        }
    }

    // Smoothly redraw even when no FFT events arrive.
    LaunchedEffect(audioSessionId) {
        while (true) {
            delay(50)
            for (i in magnitudes.indices) {
                magnitudes[i] *= 0.92f
            }
            version = (version + 1) and 0xFFFF
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
    ) {
        @Suppress("UNUSED_VARIABLE")
        val tick = version
        val barWidth = size.width / bars
        val gap = barWidth * 0.25f
        val brush = Brush.verticalGradient(
            colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.4f)),
        )
        for (i in 0 until bars) {
            val mag = magnitudes[i]
            val h = mag * size.height
            val x = i * barWidth + gap / 2
            val y = size.height - h
            drawRect(
                brush = brush,
                topLeft = Offset(x, y),
                size = Size(barWidth - gap, min(h, size.height)),
            )
        }
    }
}
