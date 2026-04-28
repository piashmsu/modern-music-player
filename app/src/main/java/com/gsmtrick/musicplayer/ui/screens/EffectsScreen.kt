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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gsmtrick.musicplayer.data.EffectsState
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
        item { EqualizerCard(effects, viewModel) }
        item { BassBoostCard(effects, viewModel) }
        item { VirtualizerCard(effects, viewModel) }
        item { ReverbCard(effects, viewModel) }
        item { VocalBoostCard(effects, viewModel) }
        item { LoudnessCard(effects, viewModel) }
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

private val EQ_PRESETS = mapOf(
    "Flat" to listOf<Short>(0, 0, 0, 0, 0),
    "Bass Booster" to listOf<Short>(700, 500, 200, 0, 0),
    "Pop" to listOf<Short>(-100, 200, 500, 200, -100),
    "Rock" to listOf<Short>(500, 300, -100, 300, 500),
    "Hip-Hop" to listOf<Short>(500, 400, 100, 200, 300),
    "Jazz" to listOf<Short>(400, 200, -200, 200, 400),
    "Classical" to listOf<Short>(500, 300, -100, 200, 400),
    "Vocal" to listOf<Short>(-200, 0, 500, 300, -100),
    "Treble Boost" to listOf<Short>(0, 0, 200, 500, 700),
)

@Composable
private fun EqualizerCard(state: EffectsState, vm: PlayerViewModel) {
    EffectCard(
        title = "Equalizer",
        subtitle = "5-band EQ with custom presets",
        enabled = state.equalizerEnabled,
        onToggle = { enabled ->
            vm.updateEffects { it.copy(equalizerEnabled = enabled) }
        },
    ) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(EQ_PRESETS.keys.toList()) { name ->
                FilterChip(
                    selected = state.preset == name,
                    onClick = {
                        val bands = EQ_PRESETS[name].orEmpty()
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
        val labels = listOf("60Hz", "230Hz", "910Hz", "3.6kHz", "14kHz")
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (i in 0 until 5) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        labels[i],
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(64.dp),
                    )
                    val value = state.bands.getOrNull(i) ?: 0
                    Slider(
                        value = value.toFloat(),
                        valueRange = -1500f..1500f,
                        onValueChange = { v ->
                            val newBands = state.bands.toMutableList().also { list ->
                                while (list.size < 5) list.add(0)
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
                        modifier = Modifier.width(48.dp),
                        textAlign = TextAlign.End,
                    )
                }
            }
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
