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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

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
    mode: String = "bars",
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
        when (mode) {
            "spectrum" -> drawSpectrum(magnitudes, color)
            "waveform" -> drawWaveform(magnitudes, color)
            "particles" -> drawParticles(magnitudes, color)
            "radial" -> drawRadial(magnitudes, color)
            else -> drawBars(magnitudes, color)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBars(
    magnitudes: FloatArray, color: Color,
) {
    val bars = magnitudes.size
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

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpectrum(
    magnitudes: FloatArray, color: Color,
) {
    val bars = magnitudes.size
    val barWidth = size.width / bars
    for (i in 0 until bars) {
        val mag = magnitudes[i]
        val h = mag * size.height
        val mid = size.height / 2f
        val brush = Brush.horizontalGradient(
            colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0.25f)),
            startX = 0f, endX = size.width,
        )
        drawRect(
            brush = brush,
            topLeft = Offset(i * barWidth, mid - h / 2f),
            size = Size(barWidth - 1f, h.coerceAtLeast(1f)),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawWaveform(
    magnitudes: FloatArray, color: Color,
) {
    val n = magnitudes.size
    if (n < 2) return
    val mid = size.height / 2f
    val step = size.width / (n - 1)
    val path = Path().apply {
        moveTo(0f, mid - magnitudes[0] * mid)
        for (i in 1 until n) {
            lineTo(i * step, mid - magnitudes[i] * mid * if (i % 2 == 0) 1f else -1f)
        }
    }
    drawPath(
        path,
        brush = Brush.horizontalGradient(
            listOf(color.copy(alpha = 0.6f), color, color.copy(alpha = 0.6f)),
        ),
        alpha = 0.9f,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawParticles(
    magnitudes: FloatArray, color: Color,
) {
    val n = magnitudes.size
    val cx = size.width / 2f
    val cy = size.height / 2f
    val maxR = min(size.width, size.height) / 2f * 0.95f
    for (i in 0 until n) {
        val angle = (i.toDouble() / n) * 2.0 * Math.PI
        val mag = magnitudes[i]
        val r = (0.2f + 0.8f * mag) * maxR
        val px = cx + (r * cos(angle)).toFloat()
        val py = cy + (r * sin(angle)).toFloat()
        drawCircle(
            color = color.copy(alpha = (0.3f + 0.7f * mag).coerceIn(0f, 1f)),
            radius = 2f + mag * 6f,
            center = Offset(px, py),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRadial(
    magnitudes: FloatArray, color: Color,
) {
    val n = magnitudes.size
    val cx = size.width / 2f
    val cy = size.height / 2f
    val baseR = min(size.width, size.height) / 2f * 0.4f
    val maxR = min(size.width, size.height) / 2f * 0.95f
    for (i in 0 until n) {
        val angle = (i.toDouble() / n) * 2.0 * Math.PI
        val mag = magnitudes[i]
        val r0 = baseR
        val r1 = baseR + (maxR - baseR) * mag
        val ax = cx + (r0 * cos(angle)).toFloat()
        val ay = cy + (r0 * sin(angle)).toFloat()
        val bx = cx + (r1 * cos(angle)).toFloat()
        val by = cy + (r1 * sin(angle)).toFloat()
        drawLine(
            color = color.copy(alpha = (0.3f + 0.7f * mag).coerceIn(0f, 1f)),
            start = Offset(ax, ay),
            end = Offset(bx, by),
            strokeWidth = 3f,
        )
    }
}
