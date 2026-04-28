package com.gsmtrick.musicplayer.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class EffectsState(
    val equalizerEnabled: Boolean = false,
    val bands: List<Short> = List(5) { 0 },
    val preset: String = "Custom",
    val bassBoost: Int = 0, // 0..1000
    val virtualizer: Int = 0, // 0..1000
    val loudness: Int = 0, // mB
    val reverbPreset: Int = 0, // 0..6 (PresetReverb)
    val vocalBoost: Int = 0, // 0..1000 (custom mid-frequency boost via EQ)
)

data class AppPrefs(
    val themeMode: String = "system", // system | light | dark
    val dynamicColor: Boolean = true,
    val accent: String = "purple", // purple | blue | green | orange | pink
    val playbackSpeed: Float = 1.0f,
    val sleepMinutes: Int = 0,
    val effects: EffectsState = EffectsState(),
)

class PreferencesRepository(private val context: Context) {

    private object K {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic")
        val ACCENT = stringPreferencesKey("accent")
        val SPEED = floatPreferencesKey("speed")
        val SLEEP = intPreferencesKey("sleep")

        val EQ_ON = booleanPreferencesKey("eq_on")
        val EQ_PRESET = stringPreferencesKey("eq_preset")
        val EQ_B0 = intPreferencesKey("eq_b0")
        val EQ_B1 = intPreferencesKey("eq_b1")
        val EQ_B2 = intPreferencesKey("eq_b2")
        val EQ_B3 = intPreferencesKey("eq_b3")
        val EQ_B4 = intPreferencesKey("eq_b4")
        val BASS = intPreferencesKey("bass")
        val VIRT = intPreferencesKey("virt")
        val LOUD = intPreferencesKey("loud")
        val REVERB = intPreferencesKey("reverb")
        val VOCAL = intPreferencesKey("vocal")
    }

    val prefs: Flow<AppPrefs> = context.dataStore.data.map { p -> p.toAppPrefs() }

    private fun Preferences.toAppPrefs() = AppPrefs(
        themeMode = this[K.THEME] ?: "system",
        dynamicColor = this[K.DYNAMIC] ?: true,
        accent = this[K.ACCENT] ?: "purple",
        playbackSpeed = this[K.SPEED] ?: 1.0f,
        sleepMinutes = this[K.SLEEP] ?: 0,
        effects = EffectsState(
            equalizerEnabled = this[K.EQ_ON] ?: false,
            preset = this[K.EQ_PRESET] ?: "Custom",
            bands = listOf(
                (this[K.EQ_B0] ?: 0).toShort(),
                (this[K.EQ_B1] ?: 0).toShort(),
                (this[K.EQ_B2] ?: 0).toShort(),
                (this[K.EQ_B3] ?: 0).toShort(),
                (this[K.EQ_B4] ?: 0).toShort(),
            ),
            bassBoost = this[K.BASS] ?: 0,
            virtualizer = this[K.VIRT] ?: 0,
            loudness = this[K.LOUD] ?: 0,
            reverbPreset = this[K.REVERB] ?: 0,
            vocalBoost = this[K.VOCAL] ?: 0,
        )
    )

    suspend fun setTheme(mode: String) = context.dataStore.edit { it[K.THEME] = mode }
    suspend fun setDynamic(enabled: Boolean) = context.dataStore.edit { it[K.DYNAMIC] = enabled }
    suspend fun setAccent(accent: String) = context.dataStore.edit { it[K.ACCENT] = accent }
    suspend fun setSpeed(speed: Float) = context.dataStore.edit { it[K.SPEED] = speed }
    suspend fun setSleep(minutes: Int) = context.dataStore.edit { it[K.SLEEP] = minutes }

    suspend fun setEffects(state: EffectsState) = context.dataStore.edit { p ->
        p[K.EQ_ON] = state.equalizerEnabled
        p[K.EQ_PRESET] = state.preset
        state.bands.getOrNull(0)?.let { p[K.EQ_B0] = it.toInt() }
        state.bands.getOrNull(1)?.let { p[K.EQ_B1] = it.toInt() }
        state.bands.getOrNull(2)?.let { p[K.EQ_B2] = it.toInt() }
        state.bands.getOrNull(3)?.let { p[K.EQ_B3] = it.toInt() }
        state.bands.getOrNull(4)?.let { p[K.EQ_B4] = it.toInt() }
        p[K.BASS] = state.bassBoost
        p[K.VIRT] = state.virtualizer
        p[K.LOUD] = state.loudness
        p[K.REVERB] = state.reverbPreset
        p[K.VOCAL] = state.vocalBoost
    }
}
