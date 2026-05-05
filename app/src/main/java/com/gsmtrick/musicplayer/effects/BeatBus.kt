package com.gsmtrick.musicplayer.effects

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of the current audio energy in three frequency buckets plus a
 * one-shot beat onset flag.
 *
 * - [bass]/[mid]/[high] are smoothed magnitudes in the range 0f..1f.
 * - [beat] is the rolling beat counter; consumers interested in onsets
 *   can compare the current value with the previous one — when it
 *   changes a new bass kick has been detected.
 * - [intensity] is a convenience field equal to [bass] (so UIs can
 *   subscribe to a single field for "how loud is the bass right now").
 */
data class BeatPulse(
    val bass: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
    val intensity: Float = 0f,
    val beat: Int = 0,
    val active: Boolean = false,
)

/**
 * Process-wide bus for beat events. The in-app Compose overlay, the
 * system-wide overlay window service, the flashlight strobe and the
 * haptic feedback all subscribe to the same bus so the audio is only
 * analyzed once per playback session.
 *
 * Producers (currently [BeatDetector] driven from the playback service)
 * call [publish]; consumers collect [pulses].
 */
object BeatBus {
    private val _pulses = MutableStateFlow(BeatPulse())
    val pulses: StateFlow<BeatPulse> = _pulses.asStateFlow()

    fun publish(p: BeatPulse) {
        _pulses.value = p
    }

    fun clear() {
        _pulses.value = BeatPulse()
    }
}
