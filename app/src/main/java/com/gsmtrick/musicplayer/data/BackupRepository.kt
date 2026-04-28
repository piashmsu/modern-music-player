package com.gsmtrick.musicplayer.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lightweight, on-device backup of user data: settings, favourites,
 * play counts, recently played, custom EQ presets, bookmarks, search
 * history and locally-stored playlists. Written as a single JSON
 * document to a [Uri] supplied by the caller (e.g. via the Storage
 * Access Framework so the user picks where to save / restore from).
 */
class BackupRepository(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val playlistRepo: PlaylistRepository,
) {

    suspend fun export(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val prefs = prefsRepo.prefs.first()
            val playlists = playlistRepo.playlists.first()
            val json = encode(prefs, playlists)
            context.contentResolver.openOutputStream(uri, "wt")?.use { os ->
                os.write(json.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            true
        }.getOrElse { false }
    }

    suspend fun import(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                input.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext false
            val o = JSONObject(text)
            // Restore prefs
            val p = o.optJSONObject("prefs")
            if (p != null) restorePrefs(p)
            // Restore playlists
            val pls = o.optJSONArray("playlists")
            if (pls != null) restorePlaylists(pls)
            true
        }.getOrElse { false }
    }

    private fun encode(prefs: AppPrefs, playlists: List<Playlist>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("createdAt", System.currentTimeMillis())

        val p = JSONObject()
        p.put("themeMode", prefs.themeMode)
        p.put("dynamicColor", prefs.dynamicColor)
        p.put("accent", prefs.accent)
        p.put("font", prefs.font)
        p.put("language", prefs.language)
        p.put("nowPlayingLayout", prefs.nowPlayingLayout)
        p.put("audioQuality", prefs.audioQuality)
        p.put("autoLyrics", prefs.autoLyrics)
        p.put("autoRadio", prefs.autoRadio)
        p.put("incognito", prefs.incognito)
        p.put("glassTheme", prefs.glassTheme)
        p.put("edgeLighting", prefs.edgeLighting)
        p.put("animatedWallpaper", prefs.animatedWallpaper)
        p.put("karaokeMode", prefs.karaokeMode)
        p.put("shakeToSkip", prefs.shakeToSkip)
        p.put("spatialWide", prefs.spatialWide)
        p.put("favorites", JSONArray(prefs.favorites.toList()))
        p.put("recentlyPlayed", JSONArray(prefs.recentlyPlayed))
        p.put("playCounts", JSONObject(prefs.playCounts.mapValues { it.value }))
        p.put("ytSearchHistory", JSONArray(prefs.ytSearchHistory))
        p.put("perSongSpeed", JSONObject(prefs.perSongSpeed.mapValues { it.value.toDouble() }))
        p.put("genreEqMap", JSONObject(prefs.genreEqMap))
        p.put("customEqPresets", JSONArray(prefs.customEqPresets.map { ep ->
            JSONObject().apply {
                put("name", ep.name)
                put("bands", JSONArray(ep.bands.map { it.toInt() }))
            }
        }))
        root.put("prefs", p)

        val pls = JSONArray()
        for (pl in playlists) {
            pls.put(JSONObject().apply {
                put("id", pl.id)
                put("name", pl.name)
                put("songIds", JSONArray(pl.songIds))
            })
        }
        root.put("playlists", pls)

        return root.toString(2)
    }

    private suspend fun restorePrefs(p: JSONObject) {
        runCatching { prefsRepo.setTheme(p.getString("themeMode")) }
        runCatching { prefsRepo.setDynamic(p.getBoolean("dynamicColor")) }
        runCatching { prefsRepo.setAccent(p.getString("accent")) }
        runCatching { prefsRepo.setFont(p.getString("font")) }
        runCatching { prefsRepo.setLanguage(p.getString("language")) }
        runCatching { prefsRepo.setNowPlayingLayout(p.getString("nowPlayingLayout")) }
        runCatching { prefsRepo.setAudioQuality(p.getString("audioQuality")) }
        runCatching { prefsRepo.setAutoLyrics(p.getBoolean("autoLyrics")) }
        runCatching { prefsRepo.setAutoRadio(p.getBoolean("autoRadio")) }
        runCatching { prefsRepo.setIncognito(p.getBoolean("incognito")) }
        runCatching { prefsRepo.setGlassTheme(p.getBoolean("glassTheme")) }
        runCatching { prefsRepo.setEdgeLighting(p.getBoolean("edgeLighting")) }
        runCatching { prefsRepo.setAnimatedWallpaper(p.getBoolean("animatedWallpaper")) }
        runCatching { prefsRepo.setKaraokeMode(p.getBoolean("karaokeMode")) }
        runCatching { prefsRepo.setShakeToSkip(p.getBoolean("shakeToSkip")) }
        runCatching { prefsRepo.setSpatialWide(p.getBoolean("spatialWide")) }

        val favs = p.optJSONArray("favorites")
        if (favs != null) {
            val set = (0 until favs.length()).mapNotNullTo(mutableSetOf()) {
                runCatching { favs.getString(it) }.getOrNull()
            }
            runCatching { prefsRepo.setFavorites(set) }
        }
    }

    private suspend fun restorePlaylists(arr: JSONArray) {
        for (i in 0 until arr.length()) {
            runCatching {
                val o = arr.getJSONObject(i)
                val name = o.getString("name")
                val songIds = o.getJSONArray("songIds")
                val ids = (0 until songIds.length()).map { songIds.getLong(it) }
                playlistRepo.create(name, ids)
            }
        }
    }
}
