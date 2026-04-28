package com.gsmtrick.musicplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
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
 * A pulsing border around the screen edge — feel like a beat-driven glow.
 */
@Composable
fun EdgeLightingOverlay(
    modifier: Modifier = Modifier,
    active: Boolean,
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "edge")
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val hue by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "hue",
    )
    Canvas(modifier = modifier) {
        val stroke = 6.dp.toPx()
        val color = Color.hsv(hue, 0.85f, 1f, alpha = 0.45f * pulse)
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    color,
                    Color.hsv((hue + 120f) % 360f, 0.85f, 1f, 0.45f * pulse),
                    Color.hsv((hue + 240f) % 360f, 0.85f, 1f, 0.45f * pulse),
                ),
            ),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(size.width - stroke, size.height - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(28.dp.toPx()),
            style = Stroke(width = stroke, pathEffect = PathEffect.cornerPathEffect(28.dp.toPx())),
        )
    }
}
