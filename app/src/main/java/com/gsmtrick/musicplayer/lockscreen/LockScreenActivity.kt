package com.gsmtrick.musicplayer.lockscreen

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import com.gsmtrick.musicplayer.ui.screens.AudioSpectrum
import com.gsmtrick.musicplayer.ui.theme.ModernMusicTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

class LockScreenActivity : ComponentActivity() {

    private val viewModel: PlayerViewModel by viewModels { PlayerViewModel.Factory }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            ModernMusicTheme(
                themeMode = "dark",
                dynamicColor = prefs.dynamicColor,
                accent = prefs.accent,
            ) {
                LockScreenContent(viewModel) { finish() }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.connectController(this)
    }

    override fun onStop() {
        super.onStop()
        viewModel.releaseController()
    }
}

@Composable
private fun LockScreenContent(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sessionId by viewModel.audioSessionId.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val song = state.currentSong

    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(1000)
        }
    }
    val timeFmt = remember { SimpleDateFormat("hh:mm", Locale.getDefault()) }
    val ampmFmt = remember { SimpleDateFormat("a", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEE, d MMM", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {},
                ) { _, dy -> if (dy > 16) onDismiss() }
            },
        color = Color.Black,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Layer 1: Blurred album art bg.
            if (song?.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 80.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f)),
            )

            // Layer 2: Animated aurora ring + vinyl art + content.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.8f))
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    timeFmt.format(now),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Light,
                    fontSize = 64.sp,
                )
                Text(
                    "${ampmFmt.format(now)} · ${dateFmt.format(now)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )

                Spacer(Modifier.height(28.dp))

                AnimatedVinyl(
                    artworkUri = song?.artworkUri,
                    isPlaying = state.isPlaying,
                )

                Spacer(Modifier.height(24.dp))

                Text(
                    song?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    song?.artist ?: "—",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))

                AudioSpectrum(
                    audioSessionId = sessionId.takeIf { it != 0 },
                    color = MaterialTheme.colorScheme.primary,
                    height = 56.dp,
                )

                Spacer(Modifier.height(8.dp))

                if (lyrics != null && lyrics!!.isSynced) {
                    val idx = lyrics!!.lineForPosition(state.positionMs)
                    val current = if (idx in lyrics!!.lines.indices) lyrics!!.lines[idx].text else ""
                    val next = if (idx + 1 in lyrics!!.lines.indices) lyrics!!.lines[idx + 1].text else ""
                    Text(
                        current,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    if (next.isNotBlank()) {
                        Text(
                            next,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.55f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { viewModel.previous() },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SkipPrevious,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                    FilledIconButton(
                        onClick = { viewModel.togglePlay() },
                        modifier = Modifier.size(96.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.next() },
                        modifier = Modifier.size(72.dp),
                    ) {
                        Icon(
                            Icons.Rounded.SkipNext,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedVinyl(artworkUri: android.net.Uri?, isPlaying: Boolean) {
    val infinite = rememberInfiniteTransition(label = "vinyl")
    val targetRotation = if (isPlaying) 360f else 0f
    val spin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = targetRotation,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 14000 else 1, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val auroraSpin by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 9000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora",
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 1100 else 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier = Modifier.size(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Outer glow.
        Box(
            modifier = Modifier
                .size(280.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.55f),
                            primary.copy(alpha = 0.0f),
                        ),
                    ),
                ),
        )
        // Aurora sweep ring.
        Box(
            modifier = Modifier
                .size(248.dp)
                .rotate(auroraSpin)
                .clip(CircleShape)
                .background(
                    Brush.sweepGradient(
                        colors = listOf(primary, secondary, tertiary, primary, secondary),
                    ),
                ),
        )
        // Inner dark vinyl.
        Box(
            modifier = Modifier
                .size(228.dp)
                .clip(CircleShape)
                .background(Color(0xFF0A0A0A)),
        )
        // Album art (vinyl center, rotates with music).
        Box(
            modifier = Modifier
                .size(200.dp)
                .rotate(spin)
                .clip(CircleShape)
                .background(Color(0xFF1A1A1A)),
            contentAlignment = Alignment.Center,
        ) {
            if (artworkUri != null) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Rounded.MusicNote,
                    null,
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(80.dp),
                )
            }
        }
        // Center "label" of the vinyl.
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
            )
        }
    }
}
