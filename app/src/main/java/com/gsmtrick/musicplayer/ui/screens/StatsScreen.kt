package com.gsmtrick.musicplayer.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsmtrick.musicplayer.ui.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(viewModel: PlayerViewModel, onBack: () -> Unit) {
    // Re-collect prefs and library so the screen reacts to live changes.
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val library by viewModel.library.collectAsStateWithLifecycle()

    val totalMs = remember(prefs.playCounts, library.songs) { viewModel.totalListeningMs() }
    val topArtists = remember(prefs.playCounts, library.songs) { viewModel.topArtists(15) }
    val topSongs = remember(prefs.playCounts, library.songs) { viewModel.topSongs(15) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatCard(
                    icon = { Icon(Icons.Rounded.Schedule, null) },
                    title = "Total listening time",
                    value = formatHours(totalMs),
                )
            }
            item {
                StatCard(
                    icon = { Icon(Icons.Rounded.MusicNote, null) },
                    title = "Songs played",
                    value = prefs.playCounts.values.sum().toString(),
                )
            }
            item {
                StatCard(
                    icon = { Icon(Icons.Rounded.Person, null) },
                    title = "Unique artists",
                    value = topArtists.size.toString(),
                )
            }

            item {
                Text(
                    "Top artists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            if (topArtists.isEmpty()) {
                item {
                    Text(
                        "Play a few songs to see stats here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(topArtists, key = { it.first }) { (artist, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Person, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(artist, modifier = Modifier.weight(1f))
                        Text(
                            "$count plays",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Text(
                    "Top songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
            }
            if (topSongs.isEmpty()) {
                item {
                    Text(
                        "No tracked plays yet.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(topSongs, key = { it.first.id }) { (song, count) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                maxLines = 1,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "$count×",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatCard(
    icon: @Composable () -> Unit,
    title: String,
    value: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatHours(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "—"
    }
}
