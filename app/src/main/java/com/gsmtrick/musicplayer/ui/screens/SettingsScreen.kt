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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsmtrick.musicplayer.ui.PlayerViewModel

@Composable
fun SettingsScreen(viewModel: PlayerViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

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
            Text(
                "Modern Music Player • v1.0",
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
