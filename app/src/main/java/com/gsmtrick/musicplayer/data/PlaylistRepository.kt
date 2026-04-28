package com.gsmtrick.musicplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Playlist(
    val id: Long,
    val name: String,
    val songIds: List<Long>,
)

class PlaylistRepository(private val context: Context) {

    private val file: File by lazy { File(context.filesDir, "playlists.json") }
    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: Flow<List<Playlist>> = _playlists.asStateFlow()

    suspend fun load() = withContext(Dispatchers.IO) {
        if (!file.exists()) {
            _playlists.value = emptyList()
            return@withContext
        }
        runCatching {
            val arr = JSONArray(file.readText())
            val list = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ids = o.getJSONArray("songIds")
                Playlist(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    songIds = (0 until ids.length()).map { ids.getLong(it) },
                )
            }
            _playlists.value = list
        }
    }

    private suspend fun save(list: List<Playlist>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("songIds", JSONArray(p.songIds))
            arr.put(o)
        }
        file.writeText(arr.toString())
        _playlists.value = list
    }

    suspend fun create(name: String, initialSongs: List<Long> = emptyList()): Playlist {
        val pl = Playlist(System.currentTimeMillis(), name, initialSongs)
        save(_playlists.value + pl)
        return pl
    }

    suspend fun rename(id: Long, name: String) {
        save(_playlists.value.map { if (it.id == id) it.copy(name = name) else it })
    }

    suspend fun delete(id: Long) {
        save(_playlists.value.filterNot { it.id == id })
    }

    suspend fun addSong(id: Long, songId: Long) {
        save(_playlists.value.map {
            if (it.id == id) it.copy(songIds = (it.songIds + songId).distinct()) else it
        })
    }

    suspend fun removeSong(id: Long, songId: Long) {
        save(_playlists.value.map {
            if (it.id == id) it.copy(songIds = it.songIds - songId) else it
        })
    }

    suspend fun reorder(id: Long, fromIndex: Int, toIndex: Int) {
        save(_playlists.value.map {
            if (it.id != id) it else {
                val ids = it.songIds.toMutableList()
                if (fromIndex in ids.indices && toIndex in 0..ids.size) {
                    val item = ids.removeAt(fromIndex)
                    ids.add(toIndex.coerceAtMost(ids.size), item)
                }
                it.copy(songIds = ids)
            }
        })
    }

    suspend fun importM3u(name: String, content: String, songsByPath: Map<String, Long>): Playlist? {
        val ids = content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                songsByPath[line] ?: songsByPath.entries.firstOrNull {
                    it.key.endsWith(line, ignoreCase = true)
                }?.value
            }
            .toList()
        return if (ids.isEmpty()) null else create(name, ids)
    }

    suspend fun exportM3u(playlist: Playlist, songsById: Map<Long, Song>): String {
        val sb = StringBuilder("#EXTM3U\n")
        for (sid in playlist.songIds) {
            val s = songsById[sid] ?: continue
            sb.append("#EXTINF:${s.durationMs / 1000},${s.artist} - ${s.title}\n")
            sb.append(s.filePath ?: s.uri.toString()).append('\n')
        }
        return sb.toString()
    }
}
