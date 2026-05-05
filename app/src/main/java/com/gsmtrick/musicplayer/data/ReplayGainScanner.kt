package com.gsmtrick.musicplayer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import kotlin.math.pow

/**
 * v3.4 — Reads `REPLAYGAIN_TRACK_GAIN` (and album fallback) from any
 * tag-bearing audio file, returning the multiplier we should apply to
 * the player volume. ReplayGain's reference loudness is -89 dB so we
 * convert the gain from dB to a 0..1 amplitude factor that the playback
 * layer multiplies into the master volume.
 */
object ReplayGainScanner {

    /** Returns a 0..1 multiplier or null when no RG tag present. */
    suspend fun multiplierFor(filePath: String?): Float? {
        if (filePath.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val f = File(filePath)
                if (!f.canRead()) return@runCatching null
                val af = AudioFileIO.read(f)
                val tag = af.tag ?: return@runCatching null
                val track = tag.getFirst("REPLAYGAIN_TRACK_GAIN")
                val album = tag.getFirst("REPLAYGAIN_ALBUM_GAIN")
                val raw = listOf(track, album).firstOrNull { !it.isNullOrBlank() }
                    ?: return@runCatching null
                val db = raw.replace("dB", "").trim().toDoubleOrNull()
                    ?: return@runCatching null
                // 10 ^ (gain / 20) gives the linear amplitude factor.
                val factor = 10.0.pow(db / 20.0).coerceIn(0.1, 1.0)
                factor.toFloat()
            }.getOrNull()
        }
    }
}
