package com.gsmtrick.musicplayer.effects

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.gsmtrick.musicplayer.data.EQ_BANDS
import com.gsmtrick.musicplayer.data.EffectsState

/**
 * Wraps Android's audio effect classes. Effects attach to the ExoPlayer
 * audio session id and can be updated live as the user changes settings.
 *
 * Up to 10 logical bands are exposed to the UI. The hardware equalizer may
 * report fewer bands (typically 5); when that happens we down-sample the
 * UI band values to the available bands.
 */
class AudioEffectsController {

    private var sessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null
    private var vocalEq: Equalizer? = null

    val numberOfBands: Int
        get() = EQ_BANDS

    val centerFrequencies: IntArray
        get() = LOGICAL_FREQUENCIES

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        if (audioSessionId == sessionId && equalizer != null) return
        release()
        sessionId = audioSessionId
        runCatching {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = false }
        }.onFailure { Log.w(TAG, "Equalizer init failed", it) }
        runCatching {
            bassBoost = BassBoost(0, audioSessionId).apply {
                enabled = false
                setStrength(0)
            }
        }.onFailure { Log.w(TAG, "BassBoost init failed", it) }
        runCatching {
            virtualizer = Virtualizer(0, audioSessionId).apply {
                enabled = false
                setStrength(0)
            }
        }.onFailure { Log.w(TAG, "Virtualizer init failed", it) }
        runCatching {
            loudness = LoudnessEnhancer(audioSessionId).apply {
                enabled = false
                setTargetGain(0)
            }
        }.onFailure { Log.w(TAG, "LoudnessEnhancer init failed", it) }
        runCatching {
            reverb = PresetReverb(0, audioSessionId).apply {
                enabled = false
                preset = PresetReverb.PRESET_NONE
            }
        }.onFailure { Log.w(TAG, "PresetReverb init failed", it) }
        runCatching {
            vocalEq = Equalizer(1, audioSessionId).apply { enabled = false }
        }.onFailure { Log.w(TAG, "Vocal EQ init failed", it) }
    }

    fun apply(state: EffectsState) {
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = state.equalizerEnabled
                if (state.equalizerEnabled) {
                    val hw = eq.numberOfBands.toInt()
                    val hwLevels = downsampleBands(state.bands, hw)
                    val range = eq.bandLevelRange
                    val lo = range[0].toInt()
                    val hi = range[1].toInt()
                    for (i in 0 until hw) {
                        val lvl = hwLevels[i].toInt().coerceIn(lo, hi).toShort()
                        eq.setBandLevel(i.toShort(), lvl)
                    }
                }
            }.onFailure { Log.w(TAG, "Equalizer apply failed", it) }
        }
        bassBoost?.let { bb ->
            runCatching {
                bb.enabled = state.bassBoost > 0
                bb.setStrength(state.bassBoost.coerceIn(0, 1000).toShort())
            }.onFailure { Log.w(TAG, "BassBoost apply failed", it) }
        }
        virtualizer?.let { v ->
            runCatching {
                v.enabled = state.virtualizer > 0
                v.setStrength(state.virtualizer.coerceIn(0, 1000).toShort())
            }.onFailure { Log.w(TAG, "Virtualizer apply failed", it) }
        }
        loudness?.let { le ->
            runCatching {
                le.enabled = state.loudness > 0
                le.setTargetGain(state.loudness)
            }.onFailure { Log.w(TAG, "Loudness apply failed", it) }
        }
        reverb?.let { r ->
            runCatching {
                val preset = state.reverbPreset.coerceIn(0, 6).toShort()
                r.preset = preset
                r.enabled = preset != PresetReverb.PRESET_NONE
            }.onFailure { Log.w(TAG, "Reverb apply failed", it) }
        }
        applyVocalBoost(state.vocalBoost)
    }

    private fun applyVocalBoost(strength: Int) {
        val eq = vocalEq ?: return
        runCatching {
            val s = strength.coerceIn(0, 1000)
            if (s == 0) {
                eq.enabled = false
                return@runCatching
            }
            val range = eq.bandLevelRange
            val maxGain = range[1].toInt()
            val n = eq.numberOfBands.toInt()
            val vocalIdxs = (0 until n).filter { i ->
                val f = eq.getCenterFreq(i.toShort())
                f in 800_000..3_500_000
            }.ifEmpty { listOf(n / 2) }
            val gain = (maxGain * 0.6 * (s / 1000.0)).toInt().toShort()
            for (i in 0 until n) {
                val target = if (i in vocalIdxs) gain else 0
                eq.setBandLevel(i.toShort(), target.toShort())
            }
            eq.enabled = true
        }.onFailure { Log.w(TAG, "Vocal boost apply failed", it) }
    }

    /**
     * Map a 10-band UI level array onto the [hwBands] hardware bands by
     * averaging the UI bands that fall into each hardware bucket.
     */
    private fun downsampleBands(uiBands: List<Short>, hwBands: Int): ShortArray {
        if (hwBands <= 0) return ShortArray(0)
        if (uiBands.size <= hwBands) {
            return ShortArray(hwBands) { i -> uiBands.getOrNull(i) ?: 0 }
        }
        val result = ShortArray(hwBands)
        val step = uiBands.size.toFloat() / hwBands
        for (i in 0 until hwBands) {
            val start = (i * step).toInt()
            val end = ((i + 1) * step).toInt().coerceAtMost(uiBands.size)
            val slice = uiBands.subList(start, end.coerceAtLeast(start + 1))
            result[i] = slice.map { it.toInt() }.average().toInt().toShort()
        }
        return result
    }

    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { loudness?.release() }
        runCatching { reverb?.release() }
        runCatching { vocalEq?.release() }
        equalizer = null
        bassBoost = null
        virtualizer = null
        loudness = null
        reverb = null
        vocalEq = null
        sessionId = 0
    }

    companion object {
        private const val TAG = "AudioEffects"

        /** Logical center frequencies (Hz) shown to the user in the 10-band UI. */
        val LOGICAL_FREQUENCIES = intArrayOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

        val REVERB_PRESETS = listOf(
            "None" to PresetReverb.PRESET_NONE,
            "Small Room" to PresetReverb.PRESET_SMALLROOM,
            "Medium Room" to PresetReverb.PRESET_MEDIUMROOM,
            "Large Room" to PresetReverb.PRESET_LARGEROOM,
            "Medium Hall" to PresetReverb.PRESET_MEDIUMHALL,
            "Large Hall" to PresetReverb.PRESET_LARGEHALL,
            "Plate" to PresetReverb.PRESET_PLATE,
        )
    }
}
