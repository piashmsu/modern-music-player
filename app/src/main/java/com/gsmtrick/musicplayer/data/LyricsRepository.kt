package com.gsmtrick.musicplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

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

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Looks for a sidecar `.lrc` next to the file, then a plain `.txt`,
     * then a cached LRCLib hit, then (if [allowOnline]) the LRCLib API.
     */
    suspend fun loadLyrics(song: Song, allowOnline: Boolean = true): Lyrics? =
        withContext(Dispatchers.IO) {
            val path = song.filePath
            if (path != null) {
                val file = File(path)
                val parent = file.parentFile
                val baseName = file.nameWithoutExtension
                if (parent != null) {
                    val candidates = listOf(
                        File(parent, "$baseName.lrc"),
                        File(parent, "$baseName.LRC"),
                        File(parent, "${file.name}.lrc"),
                    )
                    candidates.firstOrNull { it.exists() && it.canRead() }
                        ?.let { return@withContext parseLrc(it.readText()) }
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
                }
            }

            // Cache lookup.
            val cacheFile = lyricsCacheFile(song)
            if (cacheFile.exists() && cacheFile.canRead()) {
                val cached = cacheFile.readText()
                if (cached.isNotBlank()) return@withContext parseLrc(cached)
            }

            // LRCLib fallback.
            if (allowOnline) {
                val online = fetchFromLrclib(song)
                if (online != null) {
                    runCatching { cacheFile.writeText(online) }
                    return@withContext parseLrc(online)
                }
            }
            null
        }

    private fun lyricsCacheFile(song: Song): File {
        val dir = File(context.filesDir, "lyrics-cache").apply { mkdirs() }
        val key = (song.title + "|" + song.artist).hashCode().toString()
        return File(dir, "$key.lrc")
    }

    private fun fetchFromLrclib(song: Song): String? {
        return runCatching {
            val q = listOf(
                "track_name" to song.title,
                "artist_name" to song.artist,
                "album_name" to song.album,
                "duration" to (song.durationMs / 1000).toString(),
            )
                .filter { (_, v) -> v.isNotBlank() && v != "0" }
                .joinToString("&") { (k, v) ->
                    "$k=" + URLEncoder.encode(v, "UTF-8")
                }
            val req = Request.Builder()
                .url("https://lrclib.net/api/get?$q")
                .header(
                    "User-Agent",
                    "ModernMusicPlayer/2.0 (https://github.com/piashmsu/modern-music-player)",
                )
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                val body = resp.body?.string() ?: return@runCatching null
                val o = JSONObject(body)
                val synced = o.optString("syncedLyrics", "")
                val plain = o.optString("plainLyrics", "")
                when {
                    synced.isNotBlank() -> synced
                    plain.isNotBlank() -> plain
                    else -> null
                }
            }
        }.getOrNull()
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
