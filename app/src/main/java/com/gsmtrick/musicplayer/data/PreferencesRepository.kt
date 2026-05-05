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
    // v3.3 — Tactile Bass: software low-shelf on top of hardware BassBoost
    // (0..1000). Adds extra +6..+12 dB of headroom in the 30-200 Hz band
    // by lifting the lowest two equalizer bands.
    val subBassBoost: Int = 0,
    // v3.3 — Bass Punch: amplifies transient bass kicks via the loudness
    // enhancer (0..1000). Stacks on top of [loudness].
    val bassPunch: Int = 0,
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
    // v3.1 mega batch
    val audioOnlyMode: Boolean = true, // YouTube: never download/play video stream
    val preCacheEnabled: Boolean = false, // cache likely-next on Wi-Fi
    val autoSkipSilence: Boolean = false,
    val captionsToLyrics: Boolean = true,
    val ytMusicBackend: Boolean = false, // music.youtube.com
    val npAnimations: Boolean = true, // Now Playing animations
    val swipeGestures: Boolean = true,
    val density: String = "comfortable", // compact | comfortable | spacious
    val iconVariant: String = "classic", // classic | neon | minimal | vinyl | dark | sunset | ocean | gold
    val stickyLyricsNotif: Boolean = false,
    val dailyStatsNotif: Boolean = false,
    val workoutMode: Boolean = false,
    val sleepMusicMode: Boolean = false,
    val drivingMode: Boolean = false,
    val autoEqByEnvironment: Boolean = false,
    val autoResumeOnHeadphone: Boolean = false,
    val backupPasswordHash: String = "", // sha256(password) hex; empty = unencrypted
    val currentProfile: String = "default",
    val profiles: List<String> = listOf("default"),
    val hiddenSongs: Set<String> = emptySet(),
    val trashedSongs: Set<String> = emptySet(),
    val headphonePreset: String = "flat", // flat | sony_wh | airpods | boat | realme | beats
    val bassEnhancerPro: Boolean = false,
    val cinemaMode: Boolean = false,
    val loudnessFix: Boolean = false,
    val splashEnabled: Boolean = true,
    val notifButtonStyle: String = "5", // "3" or "5"
    // v3.2 mega batch
    val banglaNumerals: Boolean = false,
    val radioStations: List<RadioStation> = emptyList(), // user-added; defaults shown separately
    val lastRadioId: String = "",
    val tabOrder: List<String> = emptyList(), // empty -> use default tab order
    val autoTagFromFilename: Boolean = false,
    // v3.3 — Beat Light & Bass
    val edgeLightingBeatReactive: Boolean = true,
    val edgeLightingSystemWide: Boolean = false, // requires SYSTEM_ALERT_WINDOW
    val edgeLightingThicknessDp: Int = 12, // 4..32
    val edgeLightingIntensity: Float = 0.8f, // 0f..1f
    val edgeLightingColorMode: String = "rainbow", // rainbow | album | single
    val flashOnBeat: Boolean = false, // strobe phone torch on bass kicks
    val vibrateOnBeat: Boolean = false, // haptic pulse on bass kicks
    // v3.4 — Big ship
    /** Per-song effects override (JSON: songId -> serialized EffectsState). */
    val perSongEffects: Map<String, EffectsState> = emptyMap(),
    /** Synced lyrics tap-to-seek + karaoke highlight. */
    val syncedLyricsEnabled: Boolean = true,
    /** Auto-translate lyrics to this BCP-47 code (en/bn/hi/ar/...). Empty = off. */
    val lyricsTranslateTo: String = "",
    /** Auto crossfade adapts to track genre/tempo (1s for fast, 5s for slow). */
    val autoCrossfadeByGenre: Boolean = false,
    /** Smart sleep: detect motion via accelerometer, auto-fade out when idle. */
    val smartSleepEnabled: Boolean = false,
    /** Smart sleep idle timeout in minutes before fade-out triggers. */
    val smartSleepIdleMin: Int = 5,
    /** Daily listening goal in minutes (0 = disabled). */
    val dailyGoalMinutes: Int = 30,
    /** Streak counter — consecutive days with at least dailyGoalMinutes of playback. */
    val streakDays: Int = 0,
    /** Last day (epoch / 86400) we counted toward the streak. */
    val streakLastDay: Long = 0L,
    /** Map of (epoch / 86400) -> minutes played that day for last 30 days. */
    val dailyMinutesMap: Map<String, Int> = emptyMap(),
    /** Sleep sounds mixer (rain, ocean, white, brown, pink, fire) -> volume 0..100. */
    val sleepSoundsVolumes: Map<String, Int> = emptyMap(),
    /** Visualizer mode: bars | spectrum | waveform | particles | radial. */
    val visualizerMode: String = "bars",
    /** Mirror playback to BT and on-device speaker simultaneously (best-effort). */
    val dualOutputMirror: Boolean = false,
    /** Cast device id when actively casting (empty = local). */
    val castDeviceId: String = "",
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
        // v3.1
        val AUDIO_ONLY = booleanPreferencesKey("audio_only")
        val PRECACHE = booleanPreferencesKey("precache")
        val AUTO_SKIP_SILENCE = booleanPreferencesKey("auto_skip_silence")
        val CAPTIONS_LYRICS = booleanPreferencesKey("captions_lyrics")
        val YT_MUSIC = booleanPreferencesKey("yt_music")
        val NP_ANIM = booleanPreferencesKey("np_anim")
        val SWIPE_GESTURES = booleanPreferencesKey("swipe_gestures")
        val DENSITY = stringPreferencesKey("density")
        val ICON_VARIANT = stringPreferencesKey("icon_variant")
        val STICKY_LYRICS = booleanPreferencesKey("sticky_lyrics")
        val DAILY_STATS = booleanPreferencesKey("daily_stats")
        val WORKOUT = booleanPreferencesKey("workout")
        val SLEEP_MUSIC = booleanPreferencesKey("sleep_music")
        val DRIVING = booleanPreferencesKey("driving")
        val AUTO_EQ_ENV = booleanPreferencesKey("auto_eq_env")
        val AUTO_RESUME_HP = booleanPreferencesKey("auto_resume_hp")
        val BACKUP_PWD = stringPreferencesKey("backup_pwd")
        val PROFILE = stringPreferencesKey("profile")
        val PROFILES = stringPreferencesKey("profiles") // CSV
        val HIDDEN = stringSetPreferencesKey("hidden")
        val TRASHED = stringSetPreferencesKey("trashed")
        val HP_PRESET = stringPreferencesKey("hp_preset")
        val BASS_PRO = booleanPreferencesKey("bass_pro")
        val CINEMA = booleanPreferencesKey("cinema")
        val LOUDNESS_FIX = booleanPreferencesKey("loudness_fix")
        val SPLASH = booleanPreferencesKey("splash")
        val NOTIF_STYLE = stringPreferencesKey("notif_style")
        // v3.2
        val BANGLA_NUMS = booleanPreferencesKey("bangla_nums")
        val RADIO_STATIONS = stringPreferencesKey("radio_stations") // JSON
        val LAST_RADIO = stringPreferencesKey("last_radio")
        val TAB_ORDER = stringPreferencesKey("tab_order") // CSV
        val AUTO_TAG = booleanPreferencesKey("auto_tag")
        // v3.3 — Beat Light & Bass
        val SUB_BASS = intPreferencesKey("sub_bass")
        val BASS_PUNCH = intPreferencesKey("bass_punch")
        val EDGE_BEAT = booleanPreferencesKey("edge_beat")
        val EDGE_SYS = booleanPreferencesKey("edge_sys")
        val EDGE_THICK = intPreferencesKey("edge_thick")
        val EDGE_INT = floatPreferencesKey("edge_intensity")
        val EDGE_COLOR = stringPreferencesKey("edge_color")
        val FLASH_BEAT = booleanPreferencesKey("flash_beat")
        val VIB_BEAT = booleanPreferencesKey("vib_beat")
        // v3.4 — Big ship
        val PER_SONG_FX = stringPreferencesKey("per_song_fx") // JSON
        val SYNC_LYRICS = booleanPreferencesKey("sync_lyrics")
        val LYR_TRANS = stringPreferencesKey("lyr_trans")
        val AUTO_CROSS_GENRE = booleanPreferencesKey("auto_cross_genre")
        val SMART_SLEEP = booleanPreferencesKey("smart_sleep")
        val SMART_SLEEP_IDLE = intPreferencesKey("smart_sleep_idle")
        val DAILY_GOAL = intPreferencesKey("daily_goal")
        val STREAK_DAYS = intPreferencesKey("streak_days")
        val STREAK_LAST = androidx.datastore.preferences.core.longPreferencesKey("streak_last")
        val DAILY_MINS = stringPreferencesKey("daily_mins") // JSON map day->mins
        val SLEEP_SOUNDS = stringPreferencesKey("sleep_sounds") // JSON map name->vol
        val VIS_MODE = stringPreferencesKey("vis_mode")
        val DUAL_OUT = booleanPreferencesKey("dual_out")
        val CAST_DEV = stringPreferencesKey("cast_dev")
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
                subBassBoost = this[K.SUB_BASS] ?: 0,
                bassPunch = this[K.BASS_PUNCH] ?: 0,
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
            audioOnlyMode = this[K.AUDIO_ONLY] ?: true,
            preCacheEnabled = this[K.PRECACHE] ?: false,
            autoSkipSilence = this[K.AUTO_SKIP_SILENCE] ?: false,
            captionsToLyrics = this[K.CAPTIONS_LYRICS] ?: true,
            ytMusicBackend = this[K.YT_MUSIC] ?: false,
            npAnimations = this[K.NP_ANIM] ?: true,
            swipeGestures = this[K.SWIPE_GESTURES] ?: true,
            density = this[K.DENSITY] ?: "comfortable",
            iconVariant = this[K.ICON_VARIANT] ?: "classic",
            stickyLyricsNotif = this[K.STICKY_LYRICS] ?: false,
            dailyStatsNotif = this[K.DAILY_STATS] ?: false,
            workoutMode = this[K.WORKOUT] ?: false,
            sleepMusicMode = this[K.SLEEP_MUSIC] ?: false,
            drivingMode = this[K.DRIVING] ?: false,
            autoEqByEnvironment = this[K.AUTO_EQ_ENV] ?: false,
            autoResumeOnHeadphone = this[K.AUTO_RESUME_HP] ?: false,
            backupPasswordHash = this[K.BACKUP_PWD] ?: "",
            currentProfile = this[K.PROFILE] ?: "default",
            profiles = (this[K.PROFILES] ?: "default").split(",").filter { it.isNotBlank() },
            hiddenSongs = this[K.HIDDEN] ?: emptySet(),
            trashedSongs = this[K.TRASHED] ?: emptySet(),
            headphonePreset = this[K.HP_PRESET] ?: "flat",
            bassEnhancerPro = this[K.BASS_PRO] ?: false,
            cinemaMode = this[K.CINEMA] ?: false,
            loudnessFix = this[K.LOUDNESS_FIX] ?: false,
            splashEnabled = this[K.SPLASH] ?: true,
            notifButtonStyle = this[K.NOTIF_STYLE] ?: "5",
            banglaNumerals = this[K.BANGLA_NUMS] ?: false,
            radioStations = decodeStations(this[K.RADIO_STATIONS]),
            lastRadioId = this[K.LAST_RADIO] ?: "",
            tabOrder = (this[K.TAB_ORDER] ?: "").split(",").filter { it.isNotBlank() },
            autoTagFromFilename = this[K.AUTO_TAG] ?: false,
            edgeLightingBeatReactive = this[K.EDGE_BEAT] ?: true,
            edgeLightingSystemWide = this[K.EDGE_SYS] ?: false,
            edgeLightingThicknessDp = this[K.EDGE_THICK] ?: 12,
            edgeLightingIntensity = this[K.EDGE_INT] ?: 0.8f,
            edgeLightingColorMode = this[K.EDGE_COLOR] ?: "rainbow",
            flashOnBeat = this[K.FLASH_BEAT] ?: false,
            vibrateOnBeat = this[K.VIB_BEAT] ?: false,
            perSongEffects = decodePerSongEffects(this[K.PER_SONG_FX]),
            syncedLyricsEnabled = this[K.SYNC_LYRICS] ?: true,
            lyricsTranslateTo = this[K.LYR_TRANS] ?: "",
            autoCrossfadeByGenre = this[K.AUTO_CROSS_GENRE] ?: false,
            smartSleepEnabled = this[K.SMART_SLEEP] ?: false,
            smartSleepIdleMin = this[K.SMART_SLEEP_IDLE] ?: 5,
            dailyGoalMinutes = this[K.DAILY_GOAL] ?: 30,
            streakDays = this[K.STREAK_DAYS] ?: 0,
            streakLastDay = this[K.STREAK_LAST] ?: 0L,
            dailyMinutesMap = decodeIntMap(this[K.DAILY_MINS]),
            sleepSoundsVolumes = decodeIntMap(this[K.SLEEP_SOUNDS]),
            visualizerMode = this[K.VIS_MODE] ?: "bars",
            dualOutputMirror = this[K.DUAL_OUT] ?: false,
            castDeviceId = this[K.CAST_DEV] ?: "",
        )
    }

    private fun decodePerSongEffects(json: String?): Map<String, EffectsState> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val o = JSONObject(json)
            val out = mutableMapOf<String, EffectsState>()
            o.keys().forEach { k ->
                val e = o.getJSONObject(k)
                val bandsArr = e.optJSONArray("bands")
                val bands: List<Short> = if (bandsArr != null) {
                    (0 until bandsArr.length()).map { bandsArr.getInt(it).toShort() }
                } else List(EQ_BANDS) { 0 }
                out[k] = EffectsState(
                    equalizerEnabled = e.optBoolean("eq_on", false),
                    bands = bands,
                    preset = e.optString("preset", "Custom"),
                    bassBoost = e.optInt("bass", 0),
                    virtualizer = e.optInt("virt", 0),
                    loudness = e.optInt("loud", 0),
                    reverbPreset = e.optInt("rev", 0),
                    vocalBoost = e.optInt("vocal", 0),
                    pitchSemitones = e.optDouble("pitch", 0.0).toFloat(),
                    balance = e.optDouble("balance", 0.0).toFloat(),
                    monoMode = e.optString("mono", "stereo"),
                    crossfadeSec = e.optInt("cross", 0),
                    replayGainEnabled = e.optBoolean("rg", false),
                    sleepFadeOut = e.optBoolean("sleep_fade", true),
                    subBassBoost = e.optInt("sub", 0),
                    bassPunch = e.optInt("punch", 0),
                )
            }
            out.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun encodePerSongEffects(m: Map<String, EffectsState>): String {
        val o = JSONObject()
        m.forEach { (id, e) ->
            val obj = JSONObject().apply {
                put("eq_on", e.equalizerEnabled)
                put("bands", JSONArray().also { a -> e.bands.forEach { a.put(it.toInt()) } })
                put("preset", e.preset)
                put("bass", e.bassBoost)
                put("virt", e.virtualizer)
                put("loud", e.loudness)
                put("rev", e.reverbPreset)
                put("vocal", e.vocalBoost)
                put("pitch", e.pitchSemitones.toDouble())
                put("balance", e.balance.toDouble())
                put("mono", e.monoMode)
                put("cross", e.crossfadeSec)
                put("rg", e.replayGainEnabled)
                put("sleep_fade", e.sleepFadeOut)
                put("sub", e.subBassBoost)
                put("punch", e.bassPunch)
            }
            o.put(id, obj)
        }
        return o.toString()
    }

    private fun decodeStations(json: String?): List<RadioStation> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                RadioStation(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    streamUrl = o.getString("url"),
                    country = o.optString("country", ""),
                    tags = o.optString("tags", ""),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun encodeStations(list: List<RadioStation>): String {
        val arr = JSONArray()
        list.forEach { s ->
            val o = JSONObject()
            o.put("id", s.id)
            o.put("name", s.name)
            o.put("url", s.streamUrl)
            o.put("country", s.country)
            o.put("tags", s.tags)
            arr.put(o)
        }
        return arr.toString()
    }

    // v3.2 setters
    suspend fun setBanglaNumerals(v: Boolean) =
        context.dataStore.edit { it[K.BANGLA_NUMS] = v }
    suspend fun saveRadioStation(station: RadioStation) = context.dataStore.edit { p ->
        val list = decodeStations(p[K.RADIO_STATIONS]).toMutableList()
        list.removeAll { it.id == station.id }
        list += station
        p[K.RADIO_STATIONS] = encodeStations(list)
    }
    suspend fun deleteRadioStation(id: String) = context.dataStore.edit { p ->
        val list = decodeStations(p[K.RADIO_STATIONS]).toMutableList()
        list.removeAll { it.id == id }
        p[K.RADIO_STATIONS] = encodeStations(list)
    }
    suspend fun setLastRadio(id: String) = context.dataStore.edit { it[K.LAST_RADIO] = id }
    suspend fun setTabOrder(order: List<String>) = context.dataStore.edit {
        it[K.TAB_ORDER] = order.joinToString(",")
    }
    suspend fun setAutoTagFromFilename(v: Boolean) =
        context.dataStore.edit { it[K.AUTO_TAG] = v }

    // v3.1 setters
    suspend fun setAudioOnly(v: Boolean) = context.dataStore.edit { it[K.AUDIO_ONLY] = v }
    suspend fun setPreCache(v: Boolean) = context.dataStore.edit { it[K.PRECACHE] = v }
    suspend fun setAutoSkipSilence(v: Boolean) = context.dataStore.edit { it[K.AUTO_SKIP_SILENCE] = v }
    suspend fun setCaptionsToLyrics(v: Boolean) = context.dataStore.edit { it[K.CAPTIONS_LYRICS] = v }
    suspend fun setYtMusicBackend(v: Boolean) = context.dataStore.edit { it[K.YT_MUSIC] = v }
    suspend fun setNpAnimations(v: Boolean) = context.dataStore.edit { it[K.NP_ANIM] = v }
    suspend fun setSwipeGestures(v: Boolean) = context.dataStore.edit { it[K.SWIPE_GESTURES] = v }
    suspend fun setDensity(v: String) = context.dataStore.edit { it[K.DENSITY] = v }
    suspend fun setIconVariant(v: String) = context.dataStore.edit { it[K.ICON_VARIANT] = v }
    suspend fun setStickyLyricsNotif(v: Boolean) = context.dataStore.edit { it[K.STICKY_LYRICS] = v }
    suspend fun setDailyStatsNotif(v: Boolean) = context.dataStore.edit { it[K.DAILY_STATS] = v }
    suspend fun setWorkoutMode(v: Boolean) = context.dataStore.edit { it[K.WORKOUT] = v }
    suspend fun setSleepMusicMode(v: Boolean) = context.dataStore.edit { it[K.SLEEP_MUSIC] = v }
    suspend fun setDrivingMode(v: Boolean) = context.dataStore.edit { it[K.DRIVING] = v }
    suspend fun setAutoEqByEnvironment(v: Boolean) = context.dataStore.edit { it[K.AUTO_EQ_ENV] = v }
    suspend fun setAutoResumeOnHeadphone(v: Boolean) = context.dataStore.edit { it[K.AUTO_RESUME_HP] = v }
    suspend fun setBackupPasswordHash(v: String) = context.dataStore.edit { it[K.BACKUP_PWD] = v }
    suspend fun setCurrentProfile(v: String) = context.dataStore.edit { it[K.PROFILE] = v }
    suspend fun setProfiles(v: List<String>) = context.dataStore.edit {
        it[K.PROFILES] = v.joinToString(",")
    }
    suspend fun toggleHidden(songId: String) = context.dataStore.edit { p ->
        val s = (p[K.HIDDEN] ?: emptySet()).toMutableSet()
        if (!s.add(songId)) s.remove(songId)
        p[K.HIDDEN] = s
    }
    suspend fun setHidden(songs: Set<String>) = context.dataStore.edit { it[K.HIDDEN] = songs }
    suspend fun trashSong(songId: String) = context.dataStore.edit { p ->
        val s = (p[K.TRASHED] ?: emptySet()).toMutableSet()
        s.add(songId)
        p[K.TRASHED] = s
    }
    suspend fun restoreSong(songId: String) = context.dataStore.edit { p ->
        val s = (p[K.TRASHED] ?: emptySet()).toMutableSet()
        s.remove(songId)
        p[K.TRASHED] = s
    }
    suspend fun emptyTrash() = context.dataStore.edit { it[K.TRASHED] = emptySet() }
    suspend fun setHeadphonePreset(v: String) = context.dataStore.edit { it[K.HP_PRESET] = v }
    suspend fun setBassEnhancerPro(v: Boolean) = context.dataStore.edit { it[K.BASS_PRO] = v }
    suspend fun setCinemaMode(v: Boolean) = context.dataStore.edit { it[K.CINEMA] = v }
    suspend fun setLoudnessFix(v: Boolean) = context.dataStore.edit { it[K.LOUDNESS_FIX] = v }
    suspend fun setSplashEnabled(v: Boolean) = context.dataStore.edit { it[K.SPLASH] = v }
    suspend fun setNotifButtonStyle(v: String) = context.dataStore.edit { it[K.NOTIF_STYLE] = v }

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
        p[K.SUB_BASS] = state.subBassBoost
        p[K.BASS_PUNCH] = state.bassPunch
    }

    // v3.3 setters
    suspend fun setEdgeLightingBeatReactive(v: Boolean) =
        context.dataStore.edit { it[K.EDGE_BEAT] = v }
    suspend fun setEdgeLightingSystemWide(v: Boolean) =
        context.dataStore.edit { it[K.EDGE_SYS] = v }
    suspend fun setEdgeLightingThickness(dp: Int) =
        context.dataStore.edit { it[K.EDGE_THICK] = dp.coerceIn(2, 64) }
    suspend fun setEdgeLightingIntensity(v: Float) =
        context.dataStore.edit { it[K.EDGE_INT] = v.coerceIn(0f, 1f) }
    suspend fun setEdgeLightingColorMode(v: String) =
        context.dataStore.edit { it[K.EDGE_COLOR] = v }
    suspend fun setFlashOnBeat(v: Boolean) =
        context.dataStore.edit { it[K.FLASH_BEAT] = v }
    suspend fun setVibrateOnBeat(v: Boolean) =
        context.dataStore.edit { it[K.VIB_BEAT] = v }

    suspend fun setFavorites(ids: Set<String>) = context.dataStore.edit { p ->
        p[K.FAVORITES] = ids
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

    // v3.4 — Big ship setters
    suspend fun setPerSongEffects(songId: String, fx: EffectsState?) =
        context.dataStore.edit { p ->
            val m = decodePerSongEffects(p[K.PER_SONG_FX]).toMutableMap()
            if (fx == null) m.remove(songId) else m[songId] = fx
            p[K.PER_SONG_FX] = encodePerSongEffects(m)
        }

    suspend fun setSyncedLyricsEnabled(v: Boolean) =
        context.dataStore.edit { it[K.SYNC_LYRICS] = v }

    suspend fun setLyricsTranslateTo(code: String) =
        context.dataStore.edit { it[K.LYR_TRANS] = code }

    suspend fun setAutoCrossfadeByGenre(v: Boolean) =
        context.dataStore.edit { it[K.AUTO_CROSS_GENRE] = v }

    suspend fun setSmartSleepEnabled(v: Boolean) =
        context.dataStore.edit { it[K.SMART_SLEEP] = v }

    suspend fun setSmartSleepIdleMin(v: Int) =
        context.dataStore.edit { it[K.SMART_SLEEP_IDLE] = v.coerceIn(1, 120) }

    suspend fun setDailyGoalMinutes(v: Int) =
        context.dataStore.edit { it[K.DAILY_GOAL] = v.coerceAtLeast(0) }

    /**
     * Record [minutesPlayed] additional minutes played today, advancing the
     * streak counter if today's total newly crosses the daily goal.
     */
    suspend fun recordDailyPlayMinutes(minutesPlayed: Int) = context.dataStore.edit { p ->
        if (minutesPlayed <= 0) return@edit
        val today = (System.currentTimeMillis() / 86_400_000L)
        val key = today.toString()
        val map = decodeIntMap(p[K.DAILY_MINS]).toMutableMap()
        val before = map[key] ?: 0
        val after = before + minutesPlayed
        map[key] = after
        // Keep only the last 60 days for size.
        val trimmed = map.entries.sortedByDescending { it.key.toLongOrNull() ?: 0L }.take(60)
            .associate { it.key to it.value }
        p[K.DAILY_MINS] = encodeIntMap(trimmed)
        val goal = p[K.DAILY_GOAL] ?: 30
        if (goal > 0 && before < goal && after >= goal) {
            val lastDay = p[K.STREAK_LAST] ?: 0L
            val days = p[K.STREAK_DAYS] ?: 0
            p[K.STREAK_DAYS] = when {
                lastDay == today -> days
                lastDay == today - 1L -> days + 1
                else -> 1 // streak resets to 1 on the first goal day
            }
            p[K.STREAK_LAST] = today
        }
    }

    suspend fun setSleepSoundVolume(name: String, volume: Int) =
        context.dataStore.edit { p ->
            val m = decodeIntMap(p[K.SLEEP_SOUNDS]).toMutableMap()
            if (volume <= 0) m.remove(name) else m[name] = volume.coerceIn(0, 100)
            p[K.SLEEP_SOUNDS] = encodeIntMap(m)
        }

    suspend fun setVisualizerMode(mode: String) =
        context.dataStore.edit { it[K.VIS_MODE] = mode }

    suspend fun setDualOutputMirror(v: Boolean) =
        context.dataStore.edit { it[K.DUAL_OUT] = v }

    suspend fun setCastDeviceId(id: String) =
        context.dataStore.edit { it[K.CAST_DEV] = id }
}
