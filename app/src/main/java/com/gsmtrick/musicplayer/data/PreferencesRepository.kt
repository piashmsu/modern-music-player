package com.gsmtrick.musicplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore by preferencesDataStore(name = "settings")

const val EQ_BANDS = 10

data class EffectsState(
    val equalizerEnabled: Boolean = false,
    val bands: List<Short> = List(EQ_BANDS) { 0 },
    val preset: String = "Custom",
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudness: Int = 0,
    val reverbPreset: Int = 0,
    val vocalBoost: Int = 0,
    val pitchSemitones: Float = 0f, // -12..+12 (chromatic)
    val balance: Float = 0f, // -1f (full L) .. 1f (full R)
    val monoMode: String = "stereo", // stereo | mono | reverse
    val crossfadeSec: Int = 0, // 0..12
    val replayGainEnabled: Boolean = false,
    val sleepFadeOut: Boolean = true, // fade out at end of sleep timer
)

data class AppPrefs(
    val themeMode: String = "system",
    val dynamicColor: Boolean = true,
    val accent: String = "purple",
    val artworkAdaptive: Boolean = true, // tint UI from album art
    val font: String = "default", // default | serif | mono | rounded
    val playbackSpeed: Float = 1.0f,
    val sleepMinutes: Int = 0,
    val visualizerEnabled: Boolean = true,
    val blurredBackground: Boolean = true,
    val lockScreenPlayer: Boolean = true, // auto-show full lock screen player when device is locked
    val effects: EffectsState = EffectsState(),
    val favorites: Set<String> = emptySet(), // song ids
    val customEqPresets: List<EqPreset> = emptyList(),
    val perSongSpeed: Map<String, Float> = emptyMap(),
    val playCounts: Map<String, Int> = emptyMap(),
    val recentlyPlayed: List<String> = emptyList(), // song ids, most recent first
    // Wave 2 features
    val audioQuality: String = "auto", // auto | low | medium | high
    val incognito: Boolean = false,
    val autoLyrics: Boolean = true, // fetch from LRCLib when local lrc not present
    val ytSearchHistory: List<String> = emptyList(),
    val language: String = "system", // system | en | bn
    val audioBookmarks: Map<String, List<Bookmark>> = emptyMap(),
    val lastPositions: Map<String, Long> = emptyMap(), // songId -> ms (for sleep auto-resume)
    val nowPlayingLayout: String = "vinyl", // vinyl | cassette | cards | minimal
    val glassTheme: Boolean = false,
    val edgeLighting: Boolean = false,
    val animatedWallpaper: Boolean = false,
    val folderLockPin: String = "", // empty = no lock
    val lockedFolders: Set<String> = emptySet(),
    val karaokeMode: Boolean = false,
    val genreEqMap: Map<String, String> = emptyMap(), // genre -> preset name
    val autoRadio: Boolean = false, // queue related YouTube songs after current
    // Final batch
    val appLockPin: String = "", // empty = no app-launch PIN
    val shakeToSkip: Boolean = false,
    val sleepEndOfSong: Boolean = false, // pause after current song ends
    val spatialWide: Boolean = false, // wide stereo / surround feel
    val prefetchEnabled: Boolean = true, // pre-resolve next YT stream
    val voiceSearchEnabled: Boolean = true,
)

data class Bookmark(val positionMs: Long, val label: String)

data class EqPreset(val name: String, val bands: List<Short>)

class PreferencesRepository(private val context: Context) {

    private object K {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val ACCENT = stringPreferencesKey("accent")
        val ART_ADAPTIVE = booleanPreferencesKey("art_adaptive")
        val FONT = stringPreferencesKey("font")
        val SPEED = floatPreferencesKey("speed")
        val SLEEP = intPreferencesKey("sleep")
        val VIS_ON = booleanPreferencesKey("vis_on")
        val BLUR_BG = booleanPreferencesKey("blur_bg")
        val LOCK_PLAYER = booleanPreferencesKey("lock_player")

        val EQ_ON = booleanPreferencesKey("eq_on")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_BANDS = stringPreferencesKey("eq_bands_v2") // CSV of shorts
        val BASS = intPreferencesKey("bass")
        val VIRT = intPreferencesKey("virt")
        val LOUD = intPreferencesKey("loud")
        val REVERB = intPreferencesKey("reverb")
        val VOCAL = intPreferencesKey("vocal")
        val PITCH = floatPreferencesKey("pitch")
        val BALANCE = floatPreferencesKey("balance")
        val MONO = stringPreferencesKey("mono")
        val CROSSFADE = intPreferencesKey("crossfade")
        val REPLAY_GAIN = booleanPreferencesKey("replay_gain")
        val SLEEP_FADE = booleanPreferencesKey("sleep_fade")

        val FAVORITES = stringSetPreferencesKey("favorites")
        val CUSTOM_PRESETS = stringPreferencesKey("custom_presets") // JSON
        val PER_SONG_SPEED = stringPreferencesKey("per_song_speed") // JSON
        val PLAY_COUNTS = stringPreferencesKey("play_counts") // JSON
        val RECENT = stringPreferencesKey("recent") // CSV

        val AUDIO_QUALITY = stringPreferencesKey("audio_quality")
        val INCOGNITO = booleanPreferencesKey("incognito")
        val AUTO_LYRICS = booleanPreferencesKey("auto_lyrics")
        val YT_HISTORY = stringPreferencesKey("yt_history") // JSON array
        val LANGUAGE = stringPreferencesKey("language")
        val BOOKMARKS = stringPreferencesKey("bookmarks") // JSON map
        val LAST_POS = stringPreferencesKey("last_pos") // JSON map
        val NP_LAYOUT = stringPreferencesKey("np_layout")
        val GLASS = booleanPreferencesKey("glass")
        val EDGE_LIGHT = booleanPreferencesKey("edge_light")
        val ANIM_WALL = booleanPreferencesKey("anim_wall")
        val LOCK_PIN = stringPreferencesKey("lock_pin")
        val LOCKED_FOLDERS = stringSetPreferencesKey("locked_folders")
        val KARAOKE = booleanPreferencesKey("karaoke")
        val GENRE_EQ = stringPreferencesKey("genre_eq") // JSON map
        val AUTO_RADIO = booleanPreferencesKey("auto_radio")
        val APP_LOCK_PIN = stringPreferencesKey("app_lock_pin")
        val SHAKE_SKIP = booleanPreferencesKey("shake_skip")
        val SLEEP_EOS = booleanPreferencesKey("sleep_eos")
        val SPATIAL_WIDE = booleanPreferencesKey("spatial_wide")
        val PREFETCH = booleanPreferencesKey("prefetch")
        val VOICE_SEARCH = booleanPreferencesKey("voice_search")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { it.toAppPrefs() }

    private fun Preferences.toAppPrefs(): AppPrefs {
        val bandsCsv = this[K.EQ_BANDS]
        val bands: List<Short> = if (bandsCsv != null && bandsCsv.isNotBlank()) {
            bandsCsv.split(",").mapNotNull { it.trim().toShortOrNull() }
                .let { if (it.size >= EQ_BANDS) it.take(EQ_BANDS) else it + List(EQ_BANDS - it.size) { 0.toShort() } }
        } else {
            List(EQ_BANDS) { 0 }
        }
        return AppPrefs(
            themeMode = this[K.THEME] ?: "system",
            dynamicColor = this[K.DYNAMIC] ?: true,
            accent = this[K.ACCENT] ?: "purple",
            artworkAdaptive = this[K.ART_ADAPTIVE] ?: true,
            font = this[K.FONT] ?: "default",
            playbackSpeed = this[K.SPEED] ?: 1.0f,
            sleepMinutes = this[K.SLEEP] ?: 0,
            visualizerEnabled = this[K.VIS_ON] ?: true,
            blurredBackground = this[K.BLUR_BG] ?: true,
            lockScreenPlayer = this[K.LOCK_PLAYER] ?: true,
            effects = EffectsState(
                equalizerEnabled = this[K.EQ_ON] ?: false,
                preset = this[K.EQ_PRESET] ?: "Custom",
                bands = bands,
                bassBoost = this[K.BASS] ?: 0,
                virtualizer = this[K.VIRT] ?: 0,
                loudness = this[K.LOUD] ?: 0,
                reverbPreset = this[K.REVERB] ?: 0,
                vocalBoost = this[K.VOCAL] ?: 0,
                pitchSemitones = this[K.PITCH] ?: 0f,
                balance = this[K.BALANCE] ?: 0f,
                monoMode = this[K.MONO] ?: "stereo",
                crossfadeSec = this[K.CROSSFADE] ?: 0,
                replayGainEnabled = this[K.REPLAY_GAIN] ?: false,
                sleepFadeOut = this[K.SLEEP_FADE] ?: true,
            ),
            favorites = this[K.FAVORITES] ?: emptySet(),
            customEqPresets = decodePresets(this[K.CUSTOM_PRESETS]),
            perSongSpeed = decodeFloatMap(this[K.PER_SONG_SPEED]),
            playCounts = decodeIntMap(this[K.PLAY_COUNTS]),
            recentlyPlayed = (this[K.RECENT] ?: "").split(",").filter { it.isNotBlank() },
            audioQuality = this[K.AUDIO_QUALITY] ?: "auto",
            incognito = this[K.INCOGNITO] ?: false,
            autoLyrics = this[K.AUTO_LYRICS] ?: true,
            ytSearchHistory = decodeStringList(this[K.YT_HISTORY]),
            language = this[K.LANGUAGE] ?: "system",
            audioBookmarks = decodeBookmarks(this[K.BOOKMARKS]),
            lastPositions = decodeLongMap(this[K.LAST_POS]),
            nowPlayingLayout = this[K.NP_LAYOUT] ?: "vinyl",
            glassTheme = this[K.GLASS] ?: false,
            edgeLighting = this[K.EDGE_LIGHT] ?: false,
            animatedWallpaper = this[K.ANIM_WALL] ?: false,
            folderLockPin = this[K.LOCK_PIN] ?: "",
            lockedFolders = this[K.LOCKED_FOLDERS] ?: emptySet(),
            karaokeMode = this[K.KARAOKE] ?: false,
            genreEqMap = decodeStringMap(this[K.GENRE_EQ]),
            autoRadio = this[K.AUTO_RADIO] ?: false,
            appLockPin = this[K.APP_LOCK_PIN] ?: "",
            shakeToSkip = this[K.SHAKE_SKIP] ?: false,
            sleepEndOfSong = this[K.SLEEP_EOS] ?: false,
            spatialWide = this[K.SPATIAL_WIDE] ?: false,
            prefetchEnabled = this[K.PREFETCH] ?: true,
            voiceSearchEnabled = this[K.VOICE_SEARCH] ?: true,
        )
    }

    suspend fun setAppLockPin(pin: String) = context.dataStore.edit { it[K.APP_LOCK_PIN] = pin }
    suspend fun setShakeToSkip(v: Boolean) = context.dataStore.edit { it[K.SHAKE_SKIP] = v }
    suspend fun setSleepEndOfSong(v: Boolean) = context.dataStore.edit { it[K.SLEEP_EOS] = v }
    suspend fun setSpatialWide(v: Boolean) = context.dataStore.edit { it[K.SPATIAL_WIDE] = v }
    suspend fun setPrefetch(v: Boolean) = context.dataStore.edit { it[K.PREFETCH] = v }
    suspend fun setVoiceSearch(v: Boolean) = context.dataStore.edit { it[K.VOICE_SEARCH] = v }

    suspend fun setAutoRadio(v: Boolean) = context.dataStore.edit { it[K.AUTO_RADIO] = v }

    suspend fun setTheme(mode: String) = context.dataStore.edit { it[K.THEME] = mode }
    suspend fun setDynamic(enabled: Boolean) = context.dataStore.edit { it[K.DYNAMIC] = enabled }
    suspend fun setAccent(accent: String) = context.dataStore.edit { it[K.ACCENT] = accent }
    suspend fun setArtworkAdaptive(enabled: Boolean) =
        context.dataStore.edit { it[K.ART_ADAPTIVE] = enabled }
    suspend fun setFont(font: String) = context.dataStore.edit { it[K.FONT] = font }
    suspend fun setSpeed(speed: Float) = context.dataStore.edit { it[K.SPEED] = speed }
    suspend fun setSleep(minutes: Int) = context.dataStore.edit { it[K.SLEEP] = minutes }
    suspend fun setVisualizer(enabled: Boolean) =
        context.dataStore.edit { it[K.VIS_ON] = enabled }
    suspend fun setBlurredBackground(enabled: Boolean) =
        context.dataStore.edit { it[K.BLUR_BG] = enabled }
    suspend fun setLockScreenPlayer(enabled: Boolean) =
        context.dataStore.edit { it[K.LOCK_PLAYER] = enabled }

    suspend fun setEffects(state: EffectsState) = context.dataStore.edit { p ->
        p[K.EQ_ON] = state.equalizerEnabled
        p[K.EQ_PRESET] = state.preset
        p[K.EQ_BANDS] = state.bands.joinToString(",") { it.toString() }
        p[K.BASS] = state.bassBoost
        p[K.VIRT] = state.virtualizer
        p[K.LOUD] = state.loudness
        p[K.REVERB] = state.reverbPreset
        p[K.VOCAL] = state.vocalBoost
        p[K.PITCH] = state.pitchSemitones
        p[K.BALANCE] = state.balance
        p[K.MONO] = state.monoMode
        p[K.CROSSFADE] = state.crossfadeSec
        p[K.REPLAY_GAIN] = state.replayGainEnabled
        p[K.SLEEP_FADE] = state.sleepFadeOut
    }

    suspend fun toggleFavorite(songId: String) = context.dataStore.edit { p ->
        val cur = p[K.FAVORITES].orEmpty().toMutableSet()
        if (!cur.add(songId)) cur.remove(songId)
        p[K.FAVORITES] = cur
    }

    suspend fun saveCustomPreset(preset: EqPreset) = context.dataStore.edit { p ->
        val list = decodePresets(p[K.CUSTOM_PRESETS]).toMutableList()
        list.removeAll { it.name == preset.name }
        list += preset
        p[K.CUSTOM_PRESETS] = encodePresets(list)
    }

    suspend fun deleteCustomPreset(name: String) = context.dataStore.edit { p ->
        val list = decodePresets(p[K.CUSTOM_PRESETS]).toMutableList()
        list.removeAll { it.name == name }
        p[K.CUSTOM_PRESETS] = encodePresets(list)
    }

    suspend fun setPerSongSpeed(songId: String, speed: Float?) =
        context.dataStore.edit { p ->
            val map = decodeFloatMap(p[K.PER_SONG_SPEED]).toMutableMap()
            if (speed == null) map.remove(songId) else map[songId] = speed
            p[K.PER_SONG_SPEED] = encodeFloatMap(map)
        }

    suspend fun recordPlay(songId: String) = context.dataStore.edit { p ->
        if (p[K.INCOGNITO] == true) return@edit
        val counts = decodeIntMap(p[K.PLAY_COUNTS]).toMutableMap()
        counts[songId] = (counts[songId] ?: 0) + 1
        p[K.PLAY_COUNTS] = encodeIntMap(counts)
        val recent = (p[K.RECENT] ?: "").split(",").filter { it.isNotBlank() }
            .toMutableList()
        recent.remove(songId)
        recent.add(0, songId)
        p[K.RECENT] = recent.take(50).joinToString(",")
    }

    private fun encodePresets(list: List<EqPreset>): String {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("name", p.name)
            o.put("bands", JSONArray(p.bands.map { it.toInt() }))
            arr.put(o)
        }
        return arr.toString()
    }

    private fun decodePresets(json: String?): List<EqPreset> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ba = o.getJSONArray("bands")
                EqPreset(
                    name = o.getString("name"),
                    bands = (0 until ba.length()).map { ba.getInt(it).toShort() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeFloatMap(map: Map<String, Float>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v.toDouble())
        return o.toString()
    }

    private fun decodeFloatMap(json: String?): Map<String, Float> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getDouble(it).toFloat() }
        }.getOrDefault(emptyMap())
    }

    private fun encodeIntMap(map: Map<String, Int>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    private fun decodeIntMap(json: String?): Map<String, Int> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getInt(it) }
        }.getOrDefault(emptyMap())
    }

    private fun decodeLongMap(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getLong(it) }
        }.getOrDefault(emptyMap())
    }

    private fun encodeLongMap(map: Map<String, Long>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    private fun decodeStringList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun encodeStringList(list: List<String>): String =
        JSONArray(list).toString()

    private fun decodeStringMap(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { o.getString(it) }
        }.getOrDefault(emptyMap())
    }

    private fun encodeStringMap(map: Map<String, String>): String {
        val o = JSONObject()
        for ((k, v) in map) o.put(k, v)
        return o.toString()
    }

    private fun decodeBookmarks(json: String?): Map<String, List<Bookmark>> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            o.keys().asSequence().associateWith { key ->
                val arr = o.getJSONArray(key)
                (0 until arr.length()).map { idx ->
                    val b = arr.getJSONObject(idx)
                    Bookmark(b.getLong("p"), b.optString("l", ""))
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun encodeBookmarks(map: Map<String, List<Bookmark>>): String {
        val o = JSONObject()
        for ((k, list) in map) {
            val arr = JSONArray()
            for (bm in list) {
                val b = JSONObject()
                b.put("p", bm.positionMs)
                b.put("l", bm.label)
                arr.put(b)
            }
            o.put(k, arr)
        }
        return o.toString()
    }

    suspend fun setAudioQuality(q: String) = context.dataStore.edit { it[K.AUDIO_QUALITY] = q }
    suspend fun setIncognito(v: Boolean) = context.dataStore.edit { it[K.INCOGNITO] = v }
    suspend fun setAutoLyrics(v: Boolean) = context.dataStore.edit { it[K.AUTO_LYRICS] = v }
    suspend fun setLanguage(lang: String) = context.dataStore.edit { it[K.LANGUAGE] = lang }
    suspend fun setNowPlayingLayout(layout: String) =
        context.dataStore.edit { it[K.NP_LAYOUT] = layout }
    suspend fun setGlassTheme(v: Boolean) = context.dataStore.edit { it[K.GLASS] = v }
    suspend fun setEdgeLighting(v: Boolean) = context.dataStore.edit { it[K.EDGE_LIGHT] = v }
    suspend fun setAnimatedWallpaper(v: Boolean) =
        context.dataStore.edit { it[K.ANIM_WALL] = v }
    suspend fun setKaraokeMode(v: Boolean) = context.dataStore.edit { it[K.KARAOKE] = v }

    suspend fun setFolderLockPin(pin: String) =
        context.dataStore.edit { it[K.LOCK_PIN] = pin }
    suspend fun setLockedFolders(folders: Set<String>) =
        context.dataStore.edit { it[K.LOCKED_FOLDERS] = folders }
    suspend fun toggleLockedFolder(folder: String) = context.dataStore.edit { p ->
        val cur = p[K.LOCKED_FOLDERS].orEmpty().toMutableSet()
        if (!cur.add(folder)) cur.remove(folder)
        p[K.LOCKED_FOLDERS] = cur
    }

    suspend fun pushSearchHistory(query: String) = context.dataStore.edit { p ->
        if (p[K.INCOGNITO] == true) return@edit
        val list = decodeStringList(p[K.YT_HISTORY]).toMutableList()
        list.remove(query)
        list.add(0, query)
        p[K.YT_HISTORY] = encodeStringList(list.take(30))
    }

    suspend fun clearSearchHistory() = context.dataStore.edit { p ->
        p[K.YT_HISTORY] = encodeStringList(emptyList())
    }

    suspend fun addBookmark(songId: String, bm: Bookmark) = context.dataStore.edit { p ->
        val map = decodeBookmarks(p[K.BOOKMARKS]).toMutableMap()
        val list = (map[songId] ?: emptyList()).toMutableList()
        list += bm
        map[songId] = list.sortedBy { it.positionMs }
        p[K.BOOKMARKS] = encodeBookmarks(map)
    }

    suspend fun removeBookmark(songId: String, positionMs: Long) =
        context.dataStore.edit { p ->
            val map = decodeBookmarks(p[K.BOOKMARKS]).toMutableMap()
            val list = (map[songId] ?: emptyList()).filterNot { it.positionMs == positionMs }
            if (list.isEmpty()) map.remove(songId) else map[songId] = list
            p[K.BOOKMARKS] = encodeBookmarks(map)
        }

    suspend fun saveLastPosition(songId: String, ms: Long) = context.dataStore.edit { p ->
        val m = decodeLongMap(p[K.LAST_POS]).toMutableMap()
        if (ms <= 1000) m.remove(songId) else m[songId] = ms
        // Keep only last 100 to bound size
        val trimmed = m.entries.sortedByDescending { it.value }.take(100)
            .associate { it.key to it.value }
        p[K.LAST_POS] = encodeLongMap(trimmed)
    }

    suspend fun setGenreEqPreset(genre: String, presetName: String?) =
        context.dataStore.edit { p ->
            val map = decodeStringMap(p[K.GENRE_EQ]).toMutableMap()
            if (presetName.isNullOrBlank()) map.remove(genre) else map[genre] = presetName
            p[K.GENRE_EQ] = encodeStringMap(map)
        }
}
