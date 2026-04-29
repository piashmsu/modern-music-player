package com.gsmtrick.musicplayer.data

/**
 * Declarative rules for building "Smart Playlists" — collections that
 * recompute themselves whenever the library or play-count map changes.
 *
 * Rules are intentionally simple and explicit so they can be persisted
 * as JSON and edited via a wizard-style UI.
 */
data class SmartPlaylistRules(
    val name: String = "My Smart Mix",
    val minPlayCount: Int = 0,
    val maxPlayCount: Int = Int.MAX_VALUE,
    val artistContains: String = "",
    val titleContains: String = "",
    val albumContains: String = "",
    val minYear: Int = 0,
    val maxYear: Int = Int.MAX_VALUE,
    val minDurationSec: Int = 0,
    val maxDurationSec: Int = Int.MAX_VALUE,
    val limit: Int = 100,
    val sort: Sort = Sort.MOST_PLAYED,
) {
    enum class Sort { MOST_PLAYED, RECENT, RANDOM, TITLE, ARTIST, YEAR_DESC }

    fun apply(songs: List<Song>, playCounts: Map<String, Int>): List<Song> {
        val ac = artistContains.trim().lowercase()
        val tc = titleContains.trim().lowercase()
        val bc = albumContains.trim().lowercase()
        val filtered = songs.filter { s ->
            val plays = playCounts[s.id.toString()] ?: 0
            if (plays < minPlayCount || plays > maxPlayCount) return@filter false
            if (ac.isNotEmpty() && !s.artist.lowercase().contains(ac)) return@filter false
            if (tc.isNotEmpty() && !s.title.lowercase().contains(tc)) return@filter false
            if (bc.isNotEmpty() && !s.album.lowercase().contains(bc)) return@filter false
            if (s.year != 0 && (s.year < minYear || s.year > maxYear)) return@filter false
            val secs = (s.durationMs / 1000).toInt()
            if (secs < minDurationSec || secs > maxDurationSec) return@filter false
            true
        }
        val sorted = when (sort) {
            Sort.MOST_PLAYED -> filtered.sortedByDescending { playCounts[it.id.toString()] ?: 0 }
            Sort.RECENT -> filtered.sortedByDescending { it.id }
            Sort.RANDOM -> filtered.shuffled()
            Sort.TITLE -> filtered.sortedBy { it.title.lowercase() }
            Sort.ARTIST -> filtered.sortedBy { it.artist.lowercase() }
            Sort.YEAR_DESC -> filtered.sortedByDescending { it.year }
        }
        return sorted.take(limit.coerceAtLeast(1))
    }
}
