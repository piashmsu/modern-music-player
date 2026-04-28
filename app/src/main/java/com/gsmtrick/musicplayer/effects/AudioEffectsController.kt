package com.gsmtrick.musicplayer.effects

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.util.Log
import com.gsmtrick.musicplayer.data.EffectsState

/**
 * Wraps Android's audio effect classes. Effects attach to the ExoPlayer
 * audio session id and can be updated live as the user changes settings.
 *
 * All effects are best-effort: some devices/ROMs disallow particular effects
 * (especially LoudnessEnhancer). Failures are logged and ignored.
 */
class AudioEffectsController {

    private var sessionId: Int = 0
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudness: LoudnessEnhancer? = null
    private var reverb: PresetReverb? = null
    /** Secondary equalizer used to inject vocal-frequency boost on top of user EQ. */
    private var vocalEq: Equalizer? = null

    val numberOfBands: Short
        get() = equalizer?.numberOfBands ?: 5
    val bandLevelRange: ShortArray?
        get() = equalizer?.bandLevelRange
    val centerFrequencies: IntArray?
        get() = equalizer?.let { eq ->
            IntArray(eq.numberOfBands.toInt()) { eq.getCenterFreq(it.toShort()) }
        }
    val presets: List<String>
        get() = equalizer?.let { eq ->
            (0 until eq.numberOfPresets).map { eq.getPresetName(it.toShort()) }
        } ?: emptyList()

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
                    val n = eq.numberOfBands.toInt().coerceAtMost(state.bands.size)
                    for (i in 0 until n) {
                        eq.setBandLevel(i.toShort(), state.bands[i])
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

    /**
     * Vocal boost works by lifting the mid-frequency bands (closest to ~1-3 kHz where
     * vocals sit) on a secondary equalizer instance. The user's main EQ is untouched.
     */
    private fun applyVocalBoost(strength: Int) {
        val eq = vocalEq ?: return
        runCatching {
            val s = strength.coerceIn(0, 1000)
            if (s == 0) {
                eq.enabled = false
                return@runCatching
            }
            val range = eq.bandLevelRange
            val maxGain = range[1].toInt() // mB
            val n = eq.numberOfBands.toInt()
            // Find indices of bands whose center frequency is in the vocal range.
            val vocalIdxs = (0 until n).filter { i ->
                val f = eq.getCenterFreq(i.toShort()) // microHz
                f in 800_000..3_500_000
            }.ifEmpty { listOf(n / 2) }
            // Scale gain: full strength -> roughly 60% of max boost so it doesn't clip.
            val gain = (maxGain * 0.6 * (s / 1000.0)).toInt().toShort()
            for (i in 0 until n) {
                val target = if (i in vocalIdxs) gain else 0
                eq.setBandLevel(i.toShort(), target.toShort())
            }
            eq.enabled = true
        }.onFailure { Log.w(TAG, "Vocal boost apply failed", it) }
    }

    fun applyPreset(presetIndex: Short): ShortArray? {
        val eq = equalizer ?: return null
        return runCatching {
            eq.usePreset(presetIndex)
            ShortArray(eq.numberOfBands.toInt()) { eq.getBandLevel(it.toShort()) }
        }.getOrNull()
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
