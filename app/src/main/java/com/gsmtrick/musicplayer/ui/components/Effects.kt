package com.gsmtrick.musicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.gsmtrick.musicplayer.effects.BeatBus
import kotlin.math.cos
import kotlin.math.sin

/**
 * A static frosted-glass backdrop: layered radial gradients with a faint
 * blur-ish appearance via overlapping translucent circles.
 */
@Composable
fun GlassBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0x33FFFFFF),
                    Color(0x11FFFFFF),
                    Color(0x33A48BFF),
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h),
            ),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x33A48BFF), Color(0x00000000)),
                center = Offset(w * 0.2f, h * 0.15f),
                radius = w * 0.6f,
            ),
            radius = w * 0.6f,
            center = Offset(w * 0.2f, h * 0.15f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x3357E0FF), Color(0x00000000)),
                center = Offset(w * 0.85f, h * 0.85f),
                radius = w * 0.55f,
            ),
            radius = w * 0.55f,
            center = Offset(w * 0.85f, h * 0.85f),
        )
    }
}

/**
 * A subtle, slowly drifting aurora background. Speeds up while [playing].
 */
@Composable
fun AnimatedAuroraBackground(
    modifier: Modifier = Modifier,
    playing: Boolean,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val durationMs = if (playing) 8000 else 20000
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f + cos(phase * 6.28f) * w * 0.25f
        val cy = h / 2f + sin(phase * 6.28f) * h * 0.25f
        val brush = Brush.radialGradient(
            colors = listOf(
                Color(0x33A48BFF),
                Color(0x1A57E0FF),
                Color(0x00000000),
            ),
            center = Offset(cx, cy),
            radius = (w + h) * 0.75f,
        )
        drawRect(brush = brush, topLeft = Offset.Zero, size = Size(w, h))
    }
}

/**
 * A pulsing border around the screen edge that reacts to detected
 * beats. The bus-published bass intensity drives stroke + alpha; each
 * onset gives an extra flash that decays in ~250 ms. When [beatReactive]
 * is false (or when the detector isn't producing data) the overlay
 * falls back to the v3.2 ambient rainbow pulse so users without a
 * working Visualizer still see something.
 */
@Composable
fun EdgeLightingOverlay(
    modifier: Modifier = Modifier,
    active: Boolean,
    beatReactive: Boolean = true,
    thicknessDp: Int = 12,
    intensity: Float = 0.8f,
    colorMode: String = "rainbow",
    accent: Color = Color.Unspecified,
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "edge")
    val ambientPulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val ambientHue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hue",
    )

    var bass by remember { mutableFloatStateOf(0f) }
    var beatFlash by remember { mutableFloatStateOf(0f) }
    var lastBeat by remember { mutableIntStateOf(-1) }
    var spunHue by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(beatReactive) {
        if (!beatReactive) return@LaunchedEffect
        var prev = -1L
        while (true) {
            val now = withFrameNanos { it }
            val dt = if (prev > 0) (now - prev).coerceAtLeast(0L) / 1_000_000_000f else 0f
            prev = now
            val pulse = BeatBus.pulses.value
            bass = pulse.bass
            if (pulse.beat != lastBeat) {
                lastBeat = pulse.beat
                beatFlash = 1f
            }
            beatFlash = (beatFlash - dt * 4f).coerceAtLeast(0f)
            spunHue = (spunHue + (24f + bass * 240f) * dt) % 360f
        }
    }

    Canvas(modifier = modifier) {
        val stroke = thicknessDp.dp.toPx()
        val pulseStrength = if (beatReactive) {
            (0.35f + bass * 0.65f + beatFlash * 0.45f).coerceIn(0f, 1.4f)
        } else {
            ambientPulse
        }
        val hue = if (beatReactive) spunHue else ambientHue
        val baseAlpha = (intensity * (0.45f + 0.55f * pulseStrength)).coerceIn(0f, 1f)
        val palette = when (colorMode) {
            "single" -> {
                val a = if (accent != Color.Unspecified) accent else Color.hsv(280f, 0.85f, 1f)
                listOf(
                    a.copy(alpha = baseAlpha),
                    a.copy(alpha = baseAlpha * 0.7f),
                    a.copy(alpha = baseAlpha),
                )
            }
            "album" -> {
                val a = if (accent != Color.Unspecified) accent else Color.hsv(hue, 0.7f, 1f)
                listOf(
                    a.copy(alpha = baseAlpha),
                    Color.hsv((hue + 30f) % 360f, 0.7f, 1f, baseAlpha * 0.85f),
                    a.copy(alpha = baseAlpha),
                )
            }
            else -> listOf(
                Color.hsv(hue, 0.85f, 1f, baseAlpha),
                Color.hsv((hue + 120f) % 360f, 0.85f, 1f, baseAlpha),
                Color.hsv((hue + 240f) % 360f, 0.85f, 1f, baseAlpha),
            )
        }
        drawRoundRect(
            brush = Brush.linearGradient(colors = palette),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.cornerPathEffect(28.dp.toPx())),
        )
    }
}
