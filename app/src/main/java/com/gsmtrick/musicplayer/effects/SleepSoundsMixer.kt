package com.gsmtrick.musicplayer.effects

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * v3.4 — Pure-software sleep-sound generator. Produces five different
 * noise / nature loops in PCM and streams them through [AudioTrack]:
 *
 *  - white  : flat-spectrum white noise
 *  - pink   : 1/f-pink (gentle hiss)
 *  - brown  : low-frequency brownian rumble
 *  - ocean  : amplitude-modulated brown noise (waves rolling)
 *  - rain   : white noise + occasional spike clusters (drops)
 *  - fire   : pink noise + low-rate amplitude flicker (crackling)
 *
 * Each sound has its own track so they can play in parallel with
 * independent volumes — a true mixer.
 */
class SleepSoundsMixer {

    enum class Sound(val displayName: String) {
        WHITE("White noise"),
        PINK("Pink noise"),
        BROWN("Brown noise"),
        OCEAN("Ocean waves"),
        RAIN("Rain"),
        FIRE("Fire crackles"),
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tracks = HashMap<Sound, AudioTrack>()
    private val jobs = HashMap<Sound, Job>()

    /** Set [sound]'s output volume (0..100). 0 stops it entirely. */
    fun setVolume(sound: Sound, volume: Int) {
        val v = (volume.coerceIn(0, 100) / 100f)
        if (v <= 0f) {
            stop(sound)
            return
        }
        val existing = tracks[sound]
        if (existing != null) {
            existing.setVolume(v)
            return
        }
        start(sound, v)
    }

    fun stopAll() {
        tracks.keys.toList().forEach { stop(it) }
        scope.cancel()
    }

    private fun start(sound: Sound, volume: Float) {
        val sampleRate = 44_100
        val bufSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        @Suppress("DEPRECATION")
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
            AudioFormat.Builder()
                .setSampleRate(sampleRate)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufSize,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE,
        )
        track.setVolume(volume)
        track.play()
        tracks[sound] = track
        val gen = generatorFor(sound)
        jobs[sound] = scope.launch {
            val buf = ShortArray(2048)
            while (isActive) {
                gen.fill(buf)
                track.write(buf, 0, buf.size)
            }
        }
    }

    private fun stop(sound: Sound) {
        jobs.remove(sound)?.cancel()
        tracks.remove(sound)?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
    }

    private fun generatorFor(sound: Sound): NoiseGen = when (sound) {
        Sound.WHITE -> WhiteNoise()
        Sound.PINK -> PinkNoise()
        Sound.BROWN -> BrownNoise()
        Sound.OCEAN -> OceanWaves()
        Sound.RAIN -> Rain()
        Sound.FIRE -> Fire()
    }

    private interface NoiseGen {
        fun fill(buf: ShortArray)
    }

    private class WhiteNoise : NoiseGen {
        private val rng = Random.Default
        override fun fill(buf: ShortArray) {
            for (i in buf.indices) {
                buf[i] = (rng.nextInt(-32_768, 32_768) / 4).toShort()
            }
        }
    }

    private class PinkNoise : NoiseGen {
        private val rng = Random.Default
        // Voss-McCartney style pink: average several octave-rate whites.
        private val white = FloatArray(7)
        private var phase = 0
        override fun fill(buf: ShortArray) {
            for (i in buf.indices) {
                phase++
                for (j in white.indices) {
                    if (phase % (1 shl j) == 0) {
                        white[j] = rng.nextFloat() * 2f - 1f
                    }
                }
                val sum = white.sum() / white.size
                buf[i] = (sum * 8000).toInt().toShort()
            }
        }
    }

    private class BrownNoise : NoiseGen {
        private val rng = Random.Default
        private var prev = 0f
        override fun fill(buf: ShortArray) {
            for (i in buf.indices) {
                val w = rng.nextFloat() * 2f - 1f
                prev = (prev + w * 0.02f).coerceIn(-1f, 1f)
                buf[i] = (prev * 18000).toInt().toShort()
            }
        }
    }

    private class OceanWaves : NoiseGen {
        private val brown = BrownNoise()
        private var phase = 0.0
        override fun fill(buf: ShortArray) {
            brown.fill(buf)
            // Slow LFO ~ every 6 seconds for wave envelope.
            for (i in buf.indices) {
                phase += 2.0 * Math.PI / (44_100.0 * 6.0)
                val env = (0.4 + 0.6 * (0.5 + 0.5 * sin(phase))).toFloat()
                buf[i] = (buf[i] * env).toInt().toShort()
            }
        }
    }

    private class Rain : NoiseGen {
        private val white = WhiteNoise()
        private val rng = Random.Default
        override fun fill(buf: ShortArray) {
            white.fill(buf)
            for (i in buf.indices) {
                if (rng.nextFloat() < 0.0008f) {
                    val drop = (rng.nextInt(-30_000, 30_000)).toShort()
                    buf[i] = drop
                }
            }
        }
    }

    private class Fire : NoiseGen {
        private val pink = PinkNoise()
        private val rng = Random.Default
        private var lfoPhase = 0.0
        override fun fill(buf: ShortArray) {
            pink.fill(buf)
            for (i in buf.indices) {
                lfoPhase += 2.0 * Math.PI / 4400.0
                val flick = (0.6 + 0.4 * cos(lfoPhase)).toFloat()
                var v = buf[i] * flick
                if (rng.nextFloat() < 0.0015f) {
                    v += rng.nextInt(-12_000, 12_000)
                }
                buf[i] = v.toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
        }
    }
}
