package com.gsmtrick.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsmtrick.musicplayer.ui.PlayerViewModel

@Composable
fun SettingsScreen(viewModel: PlayerViewModel, onOpenStats: () -> Unit = {}) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pinDialog by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = viewModel.exportBackup(uri)
                Toast.makeText(
                    context,
                    if (ok) "Backup exported" else "Export failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = viewModel.importBackup(uri)
                Toast.makeText(
                    context,
                    if (ok) "Backup restored" else "Import failed",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    if (pinDialog) {
        var pin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pinDialog = false },
            title = { Text("Set app lock PIN") },
            text = {
                Column {
                    Text("Enter 4-8 digits. Empty PIN clears the lock.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setAppLockPin(if (pin.length in 4..8) pin else "")
                    pinDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.setAppLockPin("")
                    pinDialog = false
                }) { Text("Clear") }
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Settings",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        item {
            SettingCard("Theme", "Choose dark / light / system") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system", "light", "dark").forEach { mode ->
                        FilterChip(
                            selected = prefs.themeMode == mode,
                            onClick = { viewModel.setTheme(mode) },
                            label = { Text(mode.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Dynamic color", "Use system Material You colors (Android 12+)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Accent color", "Used when dynamic color is off") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val accents = listOf(
                        "purple" to Color(0xFFB69CFF),
                        "blue" to Color(0xFF6FA8FF),
                        "green" to Color(0xFF7FE0A0),
                        "orange" to Color(0xFFFFB077),
                        "pink" to Color(0xFFFF8FB1),
                    )
                    accents.forEach { (name, color) ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (prefs.accent == name) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { viewModel.setAccent(name) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Playback speed", "${"%.2f".format(prefs.playbackSpeed)}x") {
                Slider(
                    value = prefs.playbackSpeed,
                    valueRange = 0.5f..2.0f,
                    onValueChange = { viewModel.setSpeed(it) },
                )
            }
        }
        item {
            SettingCard("Sleep timer", if (prefs.sleepMinutes > 0) "${prefs.sleepMinutes} minutes" else "Off") {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0, 15, 30, 45, 60, 90).forEach { mins ->
                            FilterChip(
                                selected = prefs.sleepMinutes == mins,
                                onClick = { viewModel.setSleepTimer(mins) },
                                label = { Text(if (mins == 0) "Off" else "${mins}m") },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Fade out", modifier = Modifier.weight(1f))
                        Switch(
                            checked = prefs.effects.sleepFadeOut,
                            onCheckedChange = { v ->
                                viewModel.updateEffects { it.copy(sleepFadeOut = v) }
                            },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Visualizer", "Show audio spectrum on Now Playing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.visualizerEnabled,
                        onCheckedChange = { viewModel.setVisualizer(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Blurred album art", "Use blurred art as background") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.blurredBackground,
                        onCheckedChange = { viewModel.setBlurredBackground(it) },
                    )
                }
            }
        }
        item {
            SettingCard(
                "Lock screen player",
                "Auto-show vinyl player + lyrics when device locks",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.lockScreenPlayer,
                        onCheckedChange = { viewModel.setLockScreenPlayer(it) },
                    )
                }
            }
        }
        item {
            SettingCard(
                "Album-art adaptive theme",
                "Tint UI to match the playing album art",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.artworkAdaptive,
                        onCheckedChange = { viewModel.setArtworkAdaptive(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Font", "Choose UI font family") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("default", "serif", "mono", "rounded").forEach { f ->
                        FilterChip(
                            selected = prefs.font == f,
                            onClick = { viewModel.setFont(f) },
                            label = { Text(f.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Auto-radio (YouTube)", "Queue 5 related songs after each YouTube song") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.autoRadio,
                        onCheckedChange = { viewModel.setAutoRadio(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Audio quality (YouTube)", "Bitrate for streaming and downloads") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("auto" to "Auto", "low" to "Low", "medium" to "Med", "high" to "High").forEach { (k, v) ->
                        FilterChip(
                            selected = prefs.audioQuality == k,
                            onClick = { viewModel.setAudioQuality(k) },
                            label = { Text(v) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Auto lyrics", "Fetch synced lyrics from LRCLib when missing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.autoLyrics,
                        onCheckedChange = { viewModel.setAutoLyrics(it) },
                    )
                }
            }
        }
        item {
            SettingCard(
                "Incognito mode",
                "Skip recording play counts, recents and search history",
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.incognito,
                        onCheckedChange = { viewModel.setIncognito(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Karaoke mode", "Reduce vocals (center channel) for sing-along") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.karaokeMode,
                        onCheckedChange = { viewModel.setKaraokeMode(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Glass theme", "Frosted-glass surfaces") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.glassTheme,
                        onCheckedChange = { viewModel.setGlassTheme(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Edge lighting", "Pulse screen edges to the beat") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.edgeLighting,
                        onCheckedChange = { viewModel.setEdgeLighting(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Animated wallpaper", "Animated gradient behind library") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.animatedWallpaper,
                        onCheckedChange = { viewModel.setAnimatedWallpaper(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Now Playing layout", "Vinyl, cassette, cards or minimal") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("vinyl", "cassette", "cards", "minimal").forEach { l ->
                        FilterChip(
                            selected = prefs.nowPlayingLayout == l,
                            onClick = { viewModel.setNowPlayingLayout(l) },
                            label = { Text(l.replaceFirstChar { it.uppercase() }) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Language", "App display language") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("system" to "System", "en" to "English", "bn" to "বাংলা").forEach { (k, v) ->
                        FilterChip(
                            selected = prefs.language == k,
                            onClick = { viewModel.setLanguage(k) },
                            label = { Text(v) },
                        )
                    }
                }
            }
        }
        item {
            SettingCard("Statistics", "Total listening time, top artists & songs") {
                Button(onClick = onOpenStats, modifier = Modifier.fillMaxWidth()) {
                    Text("Open statistics")
                }
            }
        }
        item {
            SettingCard("Backup & restore", "Export and import settings, favorites, playlists") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { exportLauncher.launch("modern-music-backup.json") },
                        modifier = Modifier.weight(1f),
                    ) { Text("Export") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Restore") }
                }
            }
        }
        item {
            SettingCard("App lock", if (prefs.appLockPin.isEmpty()) "No PIN set" else "PIN protected") {
                Button(onClick = { pinDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (prefs.appLockPin.isEmpty()) "Set PIN" else "Change / clear PIN")
                }
            }
        }
        item {
            SettingCard("Shake to skip", "Shake the phone to play next song") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.shakeToSkip,
                        onCheckedChange = { viewModel.setShakeToSkip(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Sleep at end of song", "Auto-pause once the current song finishes") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.sleepEndOfSong,
                        onCheckedChange = { viewModel.setSleepEndOfSong(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Spatial wide", "Stereo widening for surround feel") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.spatialWide,
                        onCheckedChange = { viewModel.setSpatialWide(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Pre-fetch next stream", "Resolves the next YouTube song in advance") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.prefetchEnabled,
                        onCheckedChange = { viewModel.setPrefetch(it) },
                    )
                }
            }
        }
        item {
            SettingCard("Voice search", "Mic icon in YouTube search bar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enable", modifier = Modifier.weight(1f))
                    Switch(
                        checked = prefs.voiceSearchEnabled,
                        onCheckedChange = { viewModel.setVoiceSearch(it) },
                    )
                }
            }
        }
        item {
            Text(
                "Modern Music Player • v3.0",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
