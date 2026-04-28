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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsmtrick.musicplayer.data.EQ_BANDS
import com.gsmtrick.musicplayer.data.EffectsState
import com.gsmtrick.musicplayer.data.EqPreset
import com.gsmtrick.musicplayer.effects.AudioEffectsController
import com.gsmtrick.musicplayer.ui.PlayerViewModel

@Composable
fun EffectsScreen(viewModel: PlayerViewModel) {
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()
    val effects = prefs.effects

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                "Audio Effects",
                style = MaterialTheme.typography.displayLarge,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            )
        }
        item { EqualizerCard(effects, prefs.customEqPresets, viewModel) }
        item { BassBoostCard(effects, viewModel) }
        item { VirtualizerCard(effects, viewModel) }
        item { ReverbCard(effects, viewModel) }
        item { VocalBoostCard(effects, viewModel) }
        item { LoudnessCard(effects, viewModel) }
        item { PitchCard(effects, viewModel) }
        item { BalanceCard(effects, viewModel) }
        item { ChannelModeCard(effects, viewModel) }
        item { CrossfadeCard(effects, viewModel) }
        item { ReplayGainCard(effects, viewModel) }
    }
}

@Composable
private fun EffectCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onToggle: ((Boolean) -> Unit)?,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                onToggle?.let {
                    Switch(checked = enabled, onCheckedChange = it)
                }
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

private fun shorts(vararg v: Int): List<Short> = v.map { it.toShort() }

private val EQ_PRESETS: Map<String, List<Short>> = linkedMapOf(
    "Flat" to shorts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
    "Bass Booster" to shorts(800, 700, 600, 400, 200, 0, 0, 0, 0, 0),
    "Bass Reducer" to shorts(-700, -500, -300, -200, 0, 0, 0, 0, 0, 0),
    "Pop" to shorts(-100, 0, 200, 400, 500, 400, 200, 0, -100, -200),
    "Rock" to shorts(500, 400, 300, 100, -100, -100, 100, 300, 500, 600),
    "Hip-Hop" to shorts(500, 500, 400, 200, 100, 100, 200, 300, 400, 500),
    "Electronic" to shorts(500, 400, 200, 0, -100, 100, 200, 400, 500, 600),
    "Jazz" to shorts(400, 300, 200, 200, -100, -100, 0, 200, 300, 400),
    "Classical" to shorts(500, 400, 300, 200, 0, 0, -100, 200, 400, 500),
    "Vocal" to shorts(-300, -200, -100, 100, 400, 500, 400, 200, 0, -100),
    "Acoustic" to shorts(400, 400, 300, 100, 200, 300, 400, 500, 400, 300),
    "Treble Boost" to shorts(0, 0, 0, 0, 0, 100, 300, 500, 600, 700),
    "Treble Reducer" to shorts(0, 0, 0, 0, 0, -100, -300, -500, -600, -700),
    "Loudness" to shorts(600, 400, 0, 0, -200, -200, 0, 0, 400, 600),
)

@Composable
private fun EqualizerCard(
    state: EffectsState,
    customPresets: List<EqPreset>,
    vm: PlayerViewModel,
) {
    EffectCard(
        title = "Equalizer",
        subtitle = "10-band EQ with built-in & custom presets",
        enabled = state.equalizerEnabled,
        onToggle = { enabled ->
            vm.updateEffects { it.copy(equalizerEnabled = enabled) }
        },
    ) {
        val allPresets = remember(customPresets) {
            EQ_PRESETS.entries.map { it.key to it.value } +
                customPresets.map { it.name to it.bands }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(allPresets) { (name, bands) ->
                FilterChip(
                    selected = state.preset == name,
                    onClick = {
                        vm.updateEffects {
                            it.copy(
                                equalizerEnabled = true,
                                preset = name,
                                bands = bands,
                            )
                        }
                    },
                    label = { Text(name) },
                    colors = FilterChipDefaults.filterChipColors(),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val labels = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 0 until EQ_BANDS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${labels[i]}Hz",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(56.dp),
                    )
                    val value = state.bands.getOrNull(i) ?: 0
                    Slider(
                        value = value.toFloat(),
                        valueRange = -1500f..1500f,
                        onValueChange = { v ->
                            val newBands = state.bands.toMutableList().also { list ->
                                while (list.size < EQ_BANDS) list.add(0)
                                list[i] = v.toInt().toShort()
                            }
                            vm.updateEffects {
                                it.copy(
                                    equalizerEnabled = true,
                                    preset = "Custom",
                                    bands = newBands,
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${value / 100}dB",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(44.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        var showSaveDialog by remember { androidx.compose.runtime.mutableStateOf(false) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.OutlinedButton(onClick = { showSaveDialog = true }) {
                Text("Save preset")
            }
            if (state.preset.isNotBlank() && state.preset !in EQ_PRESETS.keys) {
                androidx.compose.material3.TextButton(onClick = { vm.deleteCustomPreset(state.preset) }) {
                    Text("Delete \"${state.preset}\"")
                }
            }
        }
        if (showSaveDialog) {
            var name by remember { androidx.compose.runtime.mutableStateOf("") }
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save preset") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Preset name") },
                        singleLine = true,
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        if (name.isNotBlank()) {
                            vm.saveCustomPreset(EqPreset(name.trim(), state.bands))
                            vm.updateEffects { it.copy(preset = name.trim()) }
                        }
                        showSaveDialog = false
                    }) { Text("Save") }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(onClick = { showSaveDialog = false }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}

@Composable
private fun BassBoostCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Bass Boost",
        subtitle = "Boost low frequencies",
        enabled = state.bassBoost > 0,
        onToggle = null,
    ) {
        Slider(
            value = state.bassBoost.toFloat(),
            valueRange = 0f..1000f,
            onValueChange = { v -> vm.updateEffects { it.copy(bassBoost = v.toInt()) } },
        )
        Text("${state.bassBoost / 10}%", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VirtualizerCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Virtualizer (3D)",
        subtitle = "Wider stereo imaging on headphones",
        enabled = state.virtualizer > 0,
        onToggle = null,
    ) {
        Slider(
            value = state.virtualizer.toFloat(),
            valueRange = 0f..1000f,
            onValueChange = { v -> vm.updateEffects { it.copy(virtualizer = v.toInt()) } },
        )
        Text("${state.virtualizer / 10}%", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReverbCard(state: EffectsState, vm: PlayerViewModel) {
    val presets = remember { AudioEffectsController.REVERB_PRESETS }
    EffectCard(
        title = "Reverb / Echo",
        subtitle = "Add room ambience to playback",
        enabled = state.reverbPreset != 0,
        onToggle = null,
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { (name, idx) ->
                FilterChip(
                    selected = state.reverbPreset == idx.toInt(),
                    onClick = { vm.updateEffects { it.copy(reverbPreset = idx.toInt()) } },
                    label = { Text(name) },
                )
            }
        }
    }
}

@Composable
private fun VocalBoostCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Vocal Boost",
        subtitle = "Lifts mid frequencies (1-3 kHz) for clearer vocals",
        enabled = state.vocalBoost > 0,
        onToggle = null,
    ) {
        Slider(
            value = state.vocalBoost.toFloat(),
            valueRange = 0f..1000f,
            onValueChange = { v -> vm.updateEffects { it.copy(vocalBoost = v.toInt()) } },
        )
        Text("${state.vocalBoost / 10}%", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun LoudnessCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Loudness",
        subtitle = "Volume booster (use carefully)",
        enabled = state.loudness > 0,
        onToggle = null,
    ) {
        Slider(
            value = state.loudness.toFloat(),
            valueRange = 0f..1500f,
            onValueChange = { v -> vm.updateEffects { it.copy(loudness = v.toInt()) } },
        )
        Text("+${state.loudness / 100} dB", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PitchCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Pitch shift",
        subtitle = "Change key without changing speed",
        enabled = state.pitchSemitones != 0f,
        onToggle = null,
    ) {
        Slider(
            value = state.pitchSemitones,
            valueRange = -12f..12f,
            steps = 23,
            onValueChange = { v -> vm.updateEffects { it.copy(pitchSemitones = v) } },
        )
        Text(
            "${if (state.pitchSemitones >= 0) "+" else ""}${"%.0f".format(state.pitchSemitones)} semitones",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun BalanceCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Stereo balance",
        subtitle = "Shift the audio left or right",
        enabled = state.balance != 0f,
        onToggle = null,
    ) {
        Slider(
            value = state.balance,
            valueRange = -1f..1f,
            onValueChange = { v -> vm.updateEffects { it.copy(balance = v) } },
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("L", style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.weight(1f))
            Text(
                when {
                    state.balance == 0f -> "Center"
                    state.balance < 0 -> "L ${(-state.balance * 100).toInt()}%"
                    else -> "R ${(state.balance * 100).toInt()}%"
                },
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.weight(1f))
            Text("R", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChannelModeCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Channel mode",
        subtitle = "Stereo / Mono / Reverse stereo",
        enabled = state.monoMode != "stereo",
        onToggle = null,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("stereo" to "Stereo", "mono" to "Mono", "reverse" to "Reverse").forEach { (key, label) ->
                FilterChip(
                    selected = state.monoMode == key,
                    onClick = { vm.updateEffects { it.copy(monoMode = key) } },
                    label = { Text(label) },
                )
            }
        }
    }
}

@Composable
private fun CrossfadeCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Crossfade",
        subtitle = "Smoothly fade between songs (0-12 sec)",
        enabled = state.crossfadeSec > 0,
        onToggle = null,
    ) {
        Slider(
            value = state.crossfadeSec.toFloat(),
            valueRange = 0f..12f,
            steps = 11,
            onValueChange = { v -> vm.updateEffects { it.copy(crossfadeSec = v.toInt()) } },
        )
        Text("${state.crossfadeSec} sec", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ReplayGainCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Replay gain",
        subtitle = "Auto-level loud and quiet tracks (uses tag info if present)",
        enabled = state.replayGainEnabled,
        onToggle = { v -> vm.updateEffects { it.copy(replayGainEnabled = v) } },
    ) {}
}
