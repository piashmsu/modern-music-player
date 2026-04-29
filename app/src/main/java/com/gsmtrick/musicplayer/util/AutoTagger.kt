package com.gsmtrick.musicplayer.util

/**
 * Result of inferring metadata from a media filename. All fields fall back
 * to the original media-store value when nothing useful can be parsed.
 */
data class GuessedTags(
    val title: String,
    val artist: String,
    val track: Int? = null,
)

/**
 * Best-effort parse of common filename patterns into title / artist:
 *   - "Artist - Title.mp3"
 *   - "01 - Artist - Title.mp3"
 *   - "01. Title.mp3"      (track # only)
 *   - "Title (Official Audio).mp3" (strip noise suffixes)
 */
object AutoTagger {

    private val noiseRegex = Regex(
        "\\s*[\\(\\[](?:official|audio|video|lyrics?|hd|hq|4k|live)[^\\)\\]]*[\\)\\]]",
        RegexOption.IGNORE_CASE,
    )

    fun guess(filename: String, fallbackTitle: String, fallbackArtist: String): GuessedTags {
        val base = filename.substringBeforeLast('.', filename).trim()
        if (base.isEmpty()) {
            return GuessedTags(fallbackTitle, fallbackArtist)
        }
        val cleaned = base.replace(noiseRegex, "").trim()
        val parts = cleaned.split(Regex("\\s+-\\s+")).map { it.trim() }.filter { it.isNotEmpty() }
        return when (parts.size) {
            1 -> {
                val track = "^(\\d{1,2})\\.?\\s+(.+)$".toRegex().matchEntire(parts[0])
                if (track != null) {
                    GuessedTags(track.groupValues[2], fallbackArtist, track.groupValues[1].toIntOrNull())
                } else {
                    GuessedTags(parts[0], fallbackArtist)
                }
            }
            2 -> GuessedTags(title = parts[1], artist = parts[0])
            else -> {
                // "01 - Artist - Title" → strip leading number if present.
                val lead = parts[0].trim()
                if (lead.matches(Regex("^\\d{1,3}$"))) {
                    GuessedTags(
                        title = parts.drop(2).joinToString(" - "),
                        artist = parts[1],
                        track = lead.toIntOrNull(),
                    )
                } else {
                    GuessedTags(title = parts.last(), artist = parts.first())
                }
            }
        }
    }
}
