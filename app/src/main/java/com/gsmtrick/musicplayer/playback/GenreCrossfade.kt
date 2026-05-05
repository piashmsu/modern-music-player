package com.gsmtrick.musicplayer.playback

import com.gsmtrick.musicplayer.data.Song

/**
 * v3.4 — Heuristic crossfade duration calculator that chooses an
 * appropriate fade window based on the *vibe* of the current track.
 *
 * Result is in seconds, clamped to the user's max preference. We go
 * shorter for fast / energetic genres (so the next track punches in
 * cleanly) and longer for slow / chill genres (so the bleed is smooth).
 */
object GenreCrossfadeAdvisor {

    private val FAST_HINTS = listOf(
        "edm", "house", "techno", "trap", "trance", "dnb", "dubstep",
        "drum and bass", "electro", "club", "dance", "remix", "rock", "metal",
        "punk", "hardcore", "speed",
    )
    private val SLOW_HINTS = listOf(
        "ambient", "lo-fi", "lofi", "classical", "instrumental", "ballad",
        "acoustic", "folk", "blues", "jazz", "soft", "sleep", "rabindra",
        "lullaby", "ghazal", "qawwali", "piano", "meditation",
    )

    /**
     * @param userMaxSec the slider value in Settings (0-12). Treated as
     * an upper bound — auto mode never exceeds it.
     */
    fun crossfadeForSong(song: Song?, userMaxSec: Int): Int {
        if (song == null || userMaxSec <= 0) return userMaxSec.coerceAtLeast(0)
        val haystack = (song.title + " " + song.artist + " " + song.album +
            " " + (song.filePath ?: "")).lowercase()
        val isFast = FAST_HINTS.any { haystack.contains(it) }
        val isSlow = SLOW_HINTS.any { haystack.contains(it) }
        val target = when {
            isFast && !isSlow -> 1
            isSlow && !isFast -> 5
            else -> 3
        }
        return target.coerceAtMost(userMaxSec)
    }
}
