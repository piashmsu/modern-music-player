package com.gsmtrick.musicplayer.data

import kotlin.random.Random

/**
 * v3.4 — Heuristic, fully offline mood-based playlist generator.
 *
 * No ML models, no cloud — just text-pattern + duration + filename
 * heuristics applied to the local library. The same five mood "mixes"
 * always exist, so the UI can show them up-front and we lazily compute
 * track lists when the user taps in.
 */
enum class Mood(val displayName: String, val emoji: String) {
    Morning("Morning", "🌅"),
    Workout("Workout", "🏋️"),
    Focus("Focus", "🎧"),
    Sleep("Sleep", "🌙"),
    Drive("Drive", "🚗"),
}

object MoodMixer {

    private val WORKOUT_HINTS =
        listOf("dance", "club", "edm", "house", "techno", "pop", "remix", "rock", "metal", "trap")
    private val SLEEP_HINTS =
        listOf("sleep", "lullaby", "calm", "rain", "ambient", "piano", "lo-fi", "lofi", "acoustic", "soft")
    private val FOCUS_HINTS =
        listOf("instrumental", "study", "focus", "lo-fi", "lofi", "ambient", "classical", "minimal")
    private val MORNING_HINTS =
        listOf("morning", "sunrise", "happy", "feel good", "uplift", "indie", "folk", "rabindra")
    private val DRIVE_HINTS =
        listOf("drive", "road", "highway", "rock", "pop", "country", "synth", "retro")

    /**
     * Score each [songs] entry for [mood] and return the top [limit] with
     * deterministic ordering. Returns `emptyList()` when nothing scored
     * above the minimum threshold.
     */
    fun mix(songs: List<Song>, mood: Mood, limit: Int = 30): List<Song> {
        if (songs.isEmpty()) return emptyList()
        val scored = songs.asSequence().map { it to scoreFor(it, mood) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(limit * 2)
            .toList()
        if (scored.isEmpty()) {
            // Soft fall-through: shuffle a stable subset by song id to give
            // the user *something*, in case nothing matches the heuristics.
            val rng = Random(mood.ordinal * 31L + songs.size)
            return songs.shuffled(rng).take(limit)
        }
        return scored.map { it.first }.take(limit)
    }

    private fun scoreFor(song: Song, mood: Mood): Int {
        val haystack = (song.title + " " + song.artist + " " + song.album +
            " " + (song.filePath ?: "")).lowercase()
        val durMs = song.durationMs
        return when (mood) {
            Mood.Morning -> tagScore(haystack, MORNING_HINTS) + durBucket(durMs, 180_000, 360_000)
            Mood.Workout -> tagScore(haystack, WORKOUT_HINTS) + durBucket(durMs, 150_000, 300_000) +
                if (song.year in 2010..2030) 1 else 0
            Mood.Focus -> tagScore(haystack, FOCUS_HINTS) + durBucket(durMs, 180_000, 600_000)
            Mood.Sleep -> tagScore(haystack, SLEEP_HINTS) + durBucket(durMs, 240_000, 900_000)
            Mood.Drive -> tagScore(haystack, DRIVE_HINTS) + durBucket(durMs, 180_000, 360_000)
        }
    }

    private fun tagScore(haystack: String, hints: List<String>): Int =
        hints.count { haystack.contains(it) } * 2

    private fun durBucket(durMs: Long, minMs: Int, maxMs: Int): Int =
        if (durMs in minMs..maxMs) 1 else 0
}
