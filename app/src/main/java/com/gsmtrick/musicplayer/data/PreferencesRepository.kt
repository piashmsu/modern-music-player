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
)

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
        )
    }

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
}
