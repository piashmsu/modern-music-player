package com.gsmtrick.musicplayer.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsmtrick.musicplayer.data.DefaultRadioStations
import com.gsmtrick.musicplayer.data.RadioStation
import com.gsmtrick.musicplayer.ui.PlayerViewModel

@Composable
fun RadioScreen(viewModel: PlayerViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    var addOpen by remember { mutableStateOf(false) }

    val all = remember(prefs.radioStations) {
        DefaultRadioStations.all + prefs.radioStations
    }

    if (addOpen) AddStationDialog(onDismiss = { addOpen = false }, onSave = { s ->
        viewModel.saveRadioStation(s); addOpen = false
    })

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { addOpen = true },
                text = { Text("Add station") },
                icon = { Icon(Icons.Rounded.Radio, null) },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Internet Radio",
                    style = MaterialTheme.typography.displayLarge,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                )
            }
            item {
                Text(
                    "Tap a station to start streaming. Stations save to your library so you can come back to them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            items(all, key = { it.id }) { s ->
                StationRow(
                    station = s,
                    isUserAdded = prefs.radioStations.any { it.id == s.id },
                    onPlay = {
                        viewModel.setLastRadio(s.id)
                        viewModel.playStreamUrl(s.name, s.streamUrl)
                    },
                    onDelete = { viewModel.deleteRadioStation(s.id) },
                )
            }
        }
    }
}

@Composable
private fun StationRow(
    station: RadioStation,
    isUserAdded: Boolean,
    onPlay: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Radio, null, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(station.name, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(station.country, station.tags).filter { it.isNotEmpty() }.joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onPlay) { Icon(Icons.Rounded.PlayArrow, "Play") }
            if (isUserAdded) {
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete") }
            }
        }
    }
}

@Composable
private fun AddStationDialog(
    onDismiss: () -> Unit,
    onSave: (RadioStation) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add radio station") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(60) },
                    label = { Text("Name") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = url, onValueChange = { url = it.trim().take(500) },
                    label = { Text("Stream URL (.mp3 / .m3u8 / icecast)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = tags, onValueChange = { tags = it.take(60) },
                    label = { Text("Tags (Bangla, Pop, …)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && url.startsWith("http"),
                onClick = {
                    val id = "user-" + System.currentTimeMillis()
                    onSave(RadioStation(id = id, name = name.trim(), streamUrl = url.trim(), tags = tags.trim()))
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
