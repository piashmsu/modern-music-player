package com.gsmtrick.musicplayer.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import kotlin.math.abs

@Composable
fun NowPlayingSheet(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val song = state.currentSong ?: return
    var expanded by remember { mutableStateOf(false) }
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val isFav = song.id.toString() in prefs.favorites

    Box(Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(8.dp)
                .clickable { expanded = true }
                .pointerInput(song.id) {
                    var totalDx = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDx > 80) viewModel.previous()
                            else if (totalDx < -80) viewModel.next()
                            totalDx = 0f
                        },
                    ) { _, dragAmount -> totalDx += dragAmount }
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (song.artworkUri != null) {
                        AsyncImage(
                            model = song.artworkUri,
                            null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Icon(
                        Icons.Rounded.MusicNote,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { viewModel.previous() }) {
                    Icon(Icons.Rounded.SkipPrevious, null)
                }
                FilledIconButton(
                    onClick = { viewModel.togglePlay() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                    )
                }
                IconButton(onClick = { viewModel.next() }) {
                    Icon(Icons.Rounded.SkipNext, null)
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            FullPlayer(
                viewModel = viewModel,
                isFavorite = isFav,
                onCollapse = { expanded = false },
            )
        }
    }
}

@Composable
private fun FullPlayer(
    viewModel: PlayerViewModel,
    isFavorite: Boolean,
    onCollapse: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val sessionId by viewModel.audioSessionId.collectAsStateWithLifecycle()
    val song = state.currentSong ?: return
    var lyricsOpen by remember { mutableStateOf(false) }
    var speedOpen by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = { },
                ) { _, dragAmount ->
                    if (dragAmount > 8) onCollapse()
                }
            },
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(Modifier.fillMaxSize()) {
            // Blurred album art background.
            if (prefs.blurredBackground && song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(radius = 60.dp),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f)),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = 24.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCollapse) {
                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Now Playing", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { viewModel.toggleFavorite(song.id) }) {
                        Icon(
                            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null,
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { speedOpen = true }) {
                        Icon(Icons.Rounded.Speed, null)
                    }
                    IconButton(onClick = { lyricsOpen = !lyricsOpen }) {
                        Icon(
                            Icons.Rounded.Lyrics,
                            null,
                            tint = if (lyricsOpen) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                if (!lyricsOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .pointerInput(song.id) {
                                var totalDx = 0f
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        if (abs(totalDx) > 100) {
                                            if (totalDx > 0) viewModel.previous()
                                            else viewModel.next()
                                        }
                                        totalDx = 0f
                                    },
                                ) { _, dragAmount -> totalDx += dragAmount }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (song.artworkUri != null) {
                            AsyncImage(
                                model = song.artworkUri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Icon(
                            Icons.Rounded.MusicNote,
                            null,
                            modifier = Modifier.size(96.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surface),
                    ) {
                        LyricsPanel(
                            lyrics = lyrics,
                            currentMs = state.positionMs,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                if (prefs.visualizerEnabled) {
                    AudioSpectrum(
                        audioSessionId = sessionId.takeIf { it != 0 },
                        color = MaterialTheme.colorScheme.primary,
                        height = 60.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Text(
                    song.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${song.artist} · ${song.album}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(16.dp))

                var seekPos by remember(song.id) { mutableStateOf<Float?>(null) }
                val total = state.durationMs.coerceAtLeast(1L).toFloat()
                val current = (seekPos ?: state.positionMs.toFloat()).coerceIn(0f, total)
                Slider(
                    value = current,
                    valueRange = 0f..total,
                    onValueChange = { seekPos = it },
                    onValueChangeFinished = {
                        seekPos?.let { viewModel.seekTo(it.toLong()) }
                        seekPos = null
                    },
                    colors = SliderDefaults.colors(),
                )
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        formatDuration(current.toLong()),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        formatDuration(state.durationMs),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Rounded.Shuffle,
                            null,
                            tint = if (state.shuffle) MaterialTheme.colorScheme.primary
                                else Color.Unspecified,
                        )
                    }
                    IconButton(onClick = { viewModel.previous() }) {
                        Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(40.dp))
                    }
                    FilledIconButton(
                        onClick = { viewModel.togglePlay() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                    ) {
                        Icon(
                            if (state.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            null,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    IconButton(onClick = { viewModel.next() }) {
                        Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(40.dp))
                    }
                    IconButton(onClick = { viewModel.cycleRepeat() }) {
                        val tint = if (state.repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary else Color.Unspecified
                        Icon(
                            if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne
                            else Icons.Rounded.Repeat,
                            null,
                            tint = tint,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Headphones,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    val perSong = prefs.perSongSpeed[song.id.toString()]
                    val effectiveSpeed = perSong ?: prefs.playbackSpeed
                    Text(
                        "${"%.2f".format(effectiveSpeed)}x" +
                            (if (perSong != null) " (per song)" else ""),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (speedOpen) {
                SpeedDialog(
                    currentSpeed = prefs.perSongSpeed[song.id.toString()] ?: prefs.playbackSpeed,
                    isPerSong = song.id.toString() in prefs.perSongSpeed,
                    onSetGlobal = { v ->
                        viewModel.setSpeed(v)
                        speedOpen = false
                    },
                    onSetPerSong = { v ->
                        viewModel.setPerSongSpeed(song.id, v)
                        speedOpen = false
                    },
                    onClearPerSong = {
                        viewModel.setPerSongSpeed(song.id, null)
                        speedOpen = false
                    },
                    onDismiss = { speedOpen = false },
                )
            }
        }
    }
}

@Composable
private fun LyricsPanel(
    lyrics: com.gsmtrick.musicplayer.data.Lyrics?,
    currentMs: Long,
) {
    if (lyrics == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No lyrics found.\nPlace a .lrc file next to the audio file to enable lyrics.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    val activeIndex = lyrics.lineForPosition(currentMs)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        itemsIndexed(lyrics.lines) { i, line ->
            val isActive = i == activeIndex
            Text(
                line.text,
                style = MaterialTheme.typography.titleMedium,
                color = if (isActive) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(vertical = 4.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun SpeedDialog(
    currentSpeed: Float,
    isPerSong: Boolean,
    onSetGlobal: (Float) -> Unit,
    onSetPerSong: (Float) -> Unit,
    onClearPerSong: () -> Unit,
    onDismiss: () -> Unit,
) {
    var speed by remember { mutableStateOf(currentSpeed) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playback speed") },
        text = {
            Column {
                Text("${"%.2f".format(speed)}x")
                Slider(
                    value = speed,
                    valueRange = 0.5f..2f,
                    steps = 14,
                    onValueChange = { speed = it },
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { v ->
                        AssistChip(
                            onClick = { speed = v },
                            label = { Text("${v}x") },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSetPerSong(speed) }) { Text("Save for this song") }
        },
        dismissButton = {
            Row {
                if (isPerSong) {
                    TextButton(onClick = onClearPerSong) { Text("Clear") }
                }
                TextButton(onClick = { onSetGlobal(speed) }) { Text("Set global") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        },
    )
}
