package com.gsmtrick.musicplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
    val lines: List<LyricLine>,
    val isSynced: Boolean,
) {
    fun lineForPosition(positionMs: Long): Int {
        if (!isSynced) return -1
        var idx = -1
        for ((i, l) in lines.withIndex()) {
            if (l.timeMs <= positionMs) idx = i else break
        }
        return idx
    }
}

class LyricsRepository(private val context: Context) {

    /**
     * Looks for a sidecar `.lrc` next to the file, then falls back to plain text in
     * tags. Returns null if nothing is found.
     */
    suspend fun loadLyrics(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        val path = song.filePath ?: return@withContext null
        val file = File(path)
        val parent = file.parentFile ?: return@withContext null
        val baseName = file.nameWithoutExtension
        val candidates = listOf(
            File(parent, "$baseName.lrc"),
            File(parent, "$baseName.LRC"),
            File(parent, "${file.name}.lrc"),
        )
        candidates.firstOrNull { it.exists() && it.canRead() }
            ?.let { return@withContext parseLrc(it.readText()) }
        // Plain .txt fallback.
        val txt = File(parent, "$baseName.txt")
        if (txt.exists() && txt.canRead()) {
            val raw = txt.readText().trim()
            if (raw.isNotEmpty()) {
                return@withContext Lyrics(
                    raw.lineSequence().map { LyricLine(0, it) }.toList(),
                    isSynced = false,
                )
            }
        }
        null
    }

    private fun parseLrc(text: String): Lyrics {
        val tagRegex = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?\]""")
        val lines = mutableListOf<LyricLine>()
        var hasTimes = false
        for (raw in text.lineSequence()) {
            val matches = tagRegex.findAll(raw).toList()
            val content = raw.replace(tagRegex, "").trim()
            if (matches.isEmpty()) {
                if (content.isNotEmpty()) lines += LyricLine(0, content)
                continue
            }
            hasTimes = true
            for (m in matches) {
                val min = m.groupValues[1].toLongOrNull() ?: 0
                val sec = m.groupValues[2].toLongOrNull() ?: 0
                val frac = m.groupValues.getOrNull(3)?.toLongOrNull() ?: 0L
                val ms = min * 60_000 + sec * 1000 +
                    when (m.groupValues[3].length) {
                        2 -> frac * 10
                        3 -> frac
                        else -> 0L
                    }
                lines += LyricLine(ms, content)
            }
        }
        lines.sortBy { it.timeMs }
        return Lyrics(lines, isSynced = hasTimes)
    }
}
