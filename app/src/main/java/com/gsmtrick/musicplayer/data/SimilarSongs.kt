package com.gsmtrick.musicplayer.data

import kotlin.math.absoluteValue

/**
 * v3.4 — Lightweight "similar songs" finder. Uses a duration / artist /
 * album / year heuristic to score every other song in the library
 * relative to a seed.
 */
object SimilarSongs {

    /**
     * Return the [limit] most similar songs to [seed] from [pool], not
     * including [seed] itself.
     */
    fun similarTo(seed: Song, pool: List<Song>, limit: Int = 25): List<Song> {
        return pool.asSequence()
            .filter { it.id != seed.id }
            .map { it to score(seed, it) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(limit)
            .toList()
    }

    private fun score(a: Song, b: Song): Int {
        var s = 0
        if (a.artist.isNotBlank() && a.artist.equals(b.artist, ignoreCase = true)) s += 5
        if (a.album.isNotBlank() && a.album.equals(b.album, ignoreCase = true)) s += 3
        if (a.year != 0 && b.year != 0 && (a.year - b.year).absoluteValue <= 2) s += 2
        // Duration similarity in 30-second buckets gives 1-2 points.
        val durDiff = (a.durationMs - b.durationMs).absoluteValue
        when {
            durDiff < 15_000 -> s += 2
            durDiff < 45_000 -> s += 1
        }
        // Title token overlap (small bonus): if any token > 4 chars matches.
        val aTokens = a.title.lowercase().split(Regex("\\W+")).filter { it.length > 4 }
        val bTokens = b.title.lowercase().split(Regex("\\W+")).filter { it.length > 4 }
        if (aTokens.any { bTokens.contains(it) }) s += 1
        return s
    }
}
