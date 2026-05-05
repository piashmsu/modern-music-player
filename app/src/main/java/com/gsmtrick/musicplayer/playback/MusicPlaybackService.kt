package com.gsmtrick.musicplayer.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.audio.ChannelMixingAudioProcessor
import androidx.media3.common.audio.ChannelMixingMatrix
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.gsmtrick.musicplayer.MainActivity
import com.gsmtrick.musicplayer.lockscreen.LockScreenActivity
import com.gsmtrick.musicplayer.data.EffectsState
import com.gsmtrick.musicplayer.data.MusicRepository
import com.gsmtrick.musicplayer.data.PreferencesRepository
import com.gsmtrick.musicplayer.effects.AudioEffectsController
import com.gsmtrick.musicplayer.effects.BeatDetector
import com.gsmtrick.musicplayer.effects.BeatHaptics
import com.gsmtrick.musicplayer.effects.BeatStrobe
import com.gsmtrick.musicplayer.effects.DualOutputRouter
import com.gsmtrick.musicplayer.effects.EdgeLightingService
import com.gsmtrick.musicplayer.effects.SmartSleepDetector
import com.gsmtrick.musicplayer.playback.GenreCrossfadeAdvisor
import com.gsmtrick.musicplayer.data.ReplayGainScanner
import com.gsmtrick.musicplayer.widget.PlayerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MusicPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val effects = AudioEffectsController()
    private val beatDetector = BeatDetector()
    private var beatStrobe: BeatStrobe? = null
    private var beatHaptics: BeatHaptics? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var prefs: PreferencesRepository

    private var sleepJob: Job? = null
    private var fadeOutEnabled: Boolean = true
    private var lastSleepMinutes: Int = 0
    private var perSongSpeed: Map<String, Float> = emptyMap()
    private var globalSpeed: Float = 1.0f
    private var pitchSemitones: Float = 0f
    private var lockScreenPlayerEnabled: Boolean = true

    // Crossfade state
    private var crossfadeSec: Int = 0
    private var crossfadeJob: Job? = null
    private var fadeInJob: Job? = null
    private var baseVolume: Float = 1f

    // v3.4 — Big ship state
    private var autoCrossfadeByGenre: Boolean = false
    private var perSongEffects: Map<String, EffectsState> = emptyMap()
    private var replayGainEnabled: Boolean = false
    private var smartSleepDetector: SmartSleepDetector? = null
    private var smartSleepEnabled: Boolean = false
    private var smartSleepIdleMin: Int = 5
    private var dailyMinutesJob: Job? = null
    private var dualOutputMirror: Boolean = false
    private val dualOutputRouter by lazy { DualOutputRouter(applicationContext) }

    @Suppress("unused")
    private val musicRepo by lazy { MusicRepository(applicationContext) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF &&
                lockScreenPlayerEnabled &&
                player?.isPlaying == true
            ) {
                val i = Intent(this@MusicPlaybackService, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { startActivity(i) }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private val channelMix = ChannelMixingAudioProcessor()

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        prefs = PreferencesRepository(applicationContext)

        val attrs = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        // Inject a ChannelMixingAudioProcessor so we can implement Mono / Reverse Stereo
        // / Balance entirely in software, on top of the hardware effects chain.
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(channelMix))
                    .build()
            }
        }
        renderers.setEnableAudioTrackPlaybackParams(true)

        val exoPlayer = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(attrs, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(pendingIntent)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effects.attach(audioSessionId)
                beatDetector.attach(audioSessionId)
                publishAudioSessionId(audioSessionId)
                scope.launch {
                    val state = prefs.prefs.map { it.effects }.distinctUntilChanged()
                    state.collectLatest { e -> effects.apply(e) }
                }
            }
        })
        effects.attach(exoPlayer.audioSessionId)
        beatDetector.attach(exoPlayer.audioSessionId)
        publishAudioSessionId(exoPlayer.audioSessionId)

        beatStrobe = BeatStrobe(applicationContext)
        beatHaptics = BeatHaptics(applicationContext)

        // Drive the system-wide edge-lighting overlay service, beat strobe
        // and beat haptics to mirror user preference + playback state.
        scope.launch {
            prefs.prefs.collectLatest { p ->
                val isPlaying = exoPlayer.isPlaying
                val wantOverlay = p.edgeLightingSystemWide && p.edgeLighting && isPlaying
                EdgeLightingService.setRunning(applicationContext, wantOverlay)
                if (p.flashOnBeat && isPlaying) beatStrobe?.start() else beatStrobe?.stop()
                if (p.vibrateOnBeat && isPlaying) beatHaptics?.start() else beatHaptics?.stop()
            }
        }
        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                scope.launch {
                    val p = prefs.prefs.first()
                    val wantOverlay = p.edgeLightingSystemWide && p.edgeLighting && isPlaying
                    EdgeLightingService.setRunning(applicationContext, wantOverlay)
                    if (p.flashOnBeat && isPlaying) beatStrobe?.start() else beatStrobe?.stop()
                    if (p.vibrateOnBeat && isPlaying) beatHaptics?.start() else beatHaptics?.stop()
                }
            }
        })

        scope.launch {
            prefs.prefs.collectLatest { p ->
                // Auto-amplify when v3.1 audio modes are enabled — gentle band/effect boosts
                // applied on top of the user's manual EQ state.
                val amplifiedEffects = applyV31AudioModes(p)
                effects.apply(amplifiedEffects)
                val wide = p.spatialWide || p.cinemaMode
                applyChannelMix(amplifiedEffects, karaoke = p.karaokeMode, spatialWide = wide)
                globalSpeed = p.playbackSpeed.coerceIn(0.5f, 2f)
                pitchSemitones = p.effects.pitchSemitones
                perSongSpeed = p.perSongSpeed
                lockScreenPlayerEnabled = p.lockScreenPlayer
                applyPlaybackParameters(exoPlayer)
                fadeOutEnabled = p.effects.sleepFadeOut
                crossfadeSec = p.effects.crossfadeSec.coerceIn(0, 12)
                exoPlayer.skipSilenceEnabled = p.autoSkipSilence
                if (p.sleepMinutes != lastSleepMinutes) {
                    lastSleepMinutes = p.sleepMinutes
                    handleSleepTimer(p.sleepMinutes)
                }
                // v3.4 — Big ship
                autoCrossfadeByGenre = p.autoCrossfadeByGenre
                perSongEffects = p.perSongEffects
                replayGainEnabled = p.effects.replayGainEnabled
                smartSleepEnabled = p.smartSleepEnabled
                smartSleepIdleMin = p.smartSleepIdleMin
                dualOutputMirror = p.dualOutputMirror
                if (smartSleepEnabled && exoPlayer.isPlaying) startSmartSleep()
                else stopSmartSleep()
                PlayerWidgetProvider.refreshAll(applicationContext)
            }
        }
        // v3.4 — Track minutes played per day for streaks. Increments
        // every minute the player is actively playing.
        dailyMinutesJob = scope.launch {
            while (true) {
                delay(60_000)
                if (exoPlayer.isPlaying) prefs.recordDailyPlayMinutes(1)
            }
        }

        // Drive crossfade volume schedule and Last.fm now-playing/scrobble timing.
        scope.launch { runCrossfadeAndScrobbleLoop(exoPlayer) }

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                applyPlaybackParameters(exoPlayer)
                // Reset crossfade volume to baseline at every track change so a
                // freshly-started song never inherits a half-faded volume.
                runCatching { exoPlayer.volume = baseVolume }
                fadeInJob?.cancel()
                if (crossfadeSec > 0) {
                    fadeInJob = scope.launch { fadeIn(exoPlayer, crossfadeSec) }
                }
                onTrackStarted(mediaItem?.mediaId)
            }
        })

        registerReceiver(screenReceiver, IntentFilter(Intent.ACTION_SCREEN_OFF))
    }

    private fun applyPlaybackParameters(p: ExoPlayer) {
        val mediaId = p.currentMediaItem?.mediaId
        val effectiveSpeed = mediaId
            ?.let { perSongSpeed[it] }
            ?.coerceIn(0.5f, 2f)
            ?: globalSpeed
        p.playbackParameters = PlaybackParameters(
            effectiveSpeed,
            pitchMultiplier(pitchSemitones),
        )
    }

    /**
     * Returns a copy of effects boosted by v3.1 modes:
     *  - cinemaMode: +reverb (large hall) and +virtualizer
     *  - bassEnhancerPro: +bass boost and lift two lowest EQ bands
     *  - loudnessFix: +loudness enhancer
     *  - autoEqByEnvironment: pick lows-vs-highs by speaker/headphone state
     *  - headphonePreset: shape EQ for the chosen earpiece flavour
     *  - workout/sleep music modes: subtle EQ tilt
     */
    private fun applyV31AudioModes(p: com.gsmtrick.musicplayer.data.AppPrefs): com.gsmtrick.musicplayer.data.EffectsState {
        var e = p.effects
        val bands = e.bands.toMutableList()
        if (p.cinemaMode) {
            e = e.copy(
                reverbPreset = e.reverbPreset.coerceAtLeast(5), // large hall
                virtualizer = e.virtualizer.coerceAtLeast(60),
            )
        }
        if (p.bassEnhancerPro) {
            e = e.copy(bassBoost = e.bassBoost.coerceAtLeast(70))
            for (i in 0..1) if (i < bands.size) {
                bands[i] = (bands[i] + 600).coerceIn(-1500, 1500).toShort()
            }
        }
        if (p.loudnessFix) {
            e = e.copy(loudness = e.loudness.coerceAtLeast(800))
        }
        // headphonePreset shapes
        when (p.headphonePreset) {
            "sony_wh" -> bands.applyShape(intArrayOf(400, 200, 0, -100, 0, 100, 200, 300, 400, 500))
            "airpods" -> bands.applyShape(intArrayOf(200, 100, 0, 0, -100, 0, 100, 200, 300, 400))
            "boat" -> bands.applyShape(intArrayOf(700, 500, 200, 0, -200, -100, 100, 200, 400, 600))
            "realme" -> bands.applyShape(intArrayOf(500, 300, 100, 0, 0, 100, 200, 300, 400, 500))
            "beats" -> bands.applyShape(intArrayOf(800, 600, 300, 0, -100, 0, 100, 200, 200, 100))
            "flat" -> { /* no shape */ }
        }
        if (p.workoutMode) bands.applyShape(intArrayOf(600, 400, 200, 0, 0, 100, 200, 300, 400, 500))
        if (p.sleepMusicMode) bands.applyShape(intArrayOf(0, -100, -200, -300, -300, -300, -400, -500, -600, -700))
        return e.copy(bands = bands.map { it.toShort() })
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyChannelMix(
        state: EffectsState,
        karaoke: Boolean = false,
        spatialWide: Boolean = false,
    ) {
        val balance = state.balance.coerceIn(-1f, 1f)
        val leftGain: Float
        val rightGain: Float
        when (state.monoMode) {
            "mono" -> {
                leftGain = 1f
                rightGain = 1f
            }
            "reverse" -> {
                leftGain = 1f
                rightGain = 1f
            }
            else -> {
                leftGain = if (balance > 0f) 1f - balance else 1f
                rightGain = if (balance < 0f) 1f + balance else 1f
            }
        }
        // Karaoke trick: mix left = L - R, right = R - L, which cancels
        // the centered (mono) vocal while leaving stereo instruments.
        val matrix = when {
            karaoke -> floatArrayOf(
                1f, -1f,
                -1f, 1f,
            )
            state.monoMode == "mono" -> floatArrayOf(
                0.5f, 0.5f,
                0.5f, 0.5f,
            )
            state.monoMode == "reverse" -> floatArrayOf(
                0f, 1f,
                1f, 0f,
            )
            // Spatial wide: emphasises stereo difference (L = 1.3·L − 0.3·R,
            // R = 1.3·R − 0.3·L), which broadens the stereo image.
            spatialWide -> floatArrayOf(
                1.3f * leftGain, -0.3f * leftGain,
                -0.3f * rightGain, 1.3f * rightGain,
            )
            else -> floatArrayOf(
                leftGain, 0f,
                0f, rightGain,
            )
        }
        channelMix.putChannelMixingMatrix(
            ChannelMixingMatrix(2, 2, matrix)
        )
    }

    private fun pitchMultiplier(semitones: Float): Float {
        val s = semitones.coerceIn(-12f, 12f)
        return Math.pow(2.0, s / 12.0).toFloat()
    }

    /**
     * Lightweight loop that polls the player position to drive end-of-track
     * crossfade volume fade-out. Last.fm scrobbling was removed in v3.4.
     */
    private suspend fun runCrossfadeAndScrobbleLoop(p: ExoPlayer) {
        var lastFadeApplied = false
        while (true) {
            val pos = p.currentPosition.coerceAtLeast(0)
            val dur = p.duration
            val playing = p.isPlaying
            val cf = crossfadeSec
            if (playing && cf > 0 && dur > 0 && dur != C.TIME_UNSET) {
                val remaining = dur - pos
                val fadeMs = (cf * 1000L).coerceAtMost(dur / 2)
                if (remaining in 1..fadeMs) {
                    val v = (remaining.toFloat() / fadeMs.toFloat()).coerceIn(0f, 1f)
                    p.volume = baseVolume * v
                    lastFadeApplied = true
                } else if (lastFadeApplied) {
                    p.volume = baseVolume
                    lastFadeApplied = false
                }
            } else if (lastFadeApplied) {
                p.volume = baseVolume
                lastFadeApplied = false
            }
            delay(500)
        }
    }

    private suspend fun fadeIn(p: ExoPlayer, seconds: Int) {
        if (seconds <= 0) return
        val totalMs = seconds * 1000L
        val steps = 30
        val stepDelay = totalMs / steps
        for (i in 0..steps) {
            val v = baseVolume * (i.toFloat() / steps).coerceIn(0f, 1f)
            runCatching { p.volume = v }
            delay(stepDelay)
        }
        runCatching { p.volume = baseVolume }
    }

    private fun onTrackStarted(mediaId: String?) {
        val id = mediaId ?: return
        val p = player ?: return
        // v3.4 — apply per-song effects override if any.
        perSongEffects[id]?.let { fx -> effects.apply(fx) }
        // v3.4 — adapt crossfade duration to track genre when enabled.
        if (autoCrossfadeByGenre) {
            scope.launch {
                val title = p.currentMediaItem?.mediaMetadata?.title?.toString().orEmpty()
                val artist = p.currentMediaItem?.mediaMetadata?.artist?.toString().orEmpty()
                val syntheticSong = com.gsmtrick.musicplayer.data.Song(
                    id = id.toLongOrNull() ?: 0L,
                    title = title,
                    artist = artist,
                    album = p.currentMediaItem?.mediaMetadata?.albumTitle?.toString().orEmpty(),
                    albumId = 0L,
                    durationMs = p.duration.coerceAtLeast(0),
                    uri = android.net.Uri.EMPTY,
                    artworkUri = null,
                )
                val userMax = prefs.prefs.first().effects.crossfadeSec.coerceIn(0, 12)
                crossfadeSec = GenreCrossfadeAdvisor.crossfadeForSong(syntheticSong, userMax)
            }
        }
        // v3.4 — Replay-gain volume normalization (best-effort, tag-based).
        if (replayGainEnabled) {
            scope.launch {
                val path = runCatching { p.currentMediaItem?.localConfiguration?.uri?.path }
                    .getOrNull()
                val mult = ReplayGainScanner.multiplierFor(path) ?: 1f
                baseVolume = mult.coerceIn(0.1f, 1f)
                runCatching { p.volume = baseVolume }
            }
        }
        // v3.4 — Dual output mirror (best-effort).
        if (dualOutputMirror) {
            scope.launch {
                val songs = musicRepo.loadSongs()
                val match = songs.firstOrNull { it.id.toString() == id }
                if (match != null) dualOutputRouter.start(match)
            }
        } else {
            dualOutputRouter.stop()
        }
    }

    private fun startSmartSleep() {
        if (smartSleepDetector != null) return
        val det = SmartSleepDetector(
            context = applicationContext,
            idleTimeoutMs = smartSleepIdleMin.coerceAtLeast(1) * 60_000L,
            onIdle = {
                scope.launch {
                    val p = player ?: return@launch
                    val totalMs = 30_000L
                    val steps = 30
                    val orig = p.volume
                    for (i in 0..steps) {
                        p.volume = orig * (1f - i.toFloat() / steps)
                        delay(totalMs / steps)
                    }
                    p.pause()
                    p.volume = orig
                }
            },
        )
        det.start()
        smartSleepDetector = det
        scope.launch {
            while (smartSleepDetector === det) {
                delay(30_000L)
                det.checkIdle()
            }
        }
    }

    private fun stopSmartSleep() {
        smartSleepDetector?.stop()
        smartSleepDetector = null
    }

    private fun handleSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) return
        val totalMs = minutes * 60_000L
        sleepJob = scope.launch {
            val p = player ?: return@launch
            if (fadeOutEnabled) {
                val fadeMs = 30_000L.coerceAtMost(totalMs / 2)
                delay(totalMs - fadeMs)
                val steps = 30
                val originalVolume = p.volume
                for (i in 0..steps) {
                    val v = originalVolume * (1f - i.toFloat() / steps)
                    p.volume = v.coerceAtLeast(0f)
                    delay(fadeMs / steps)
                }
                p.pause()
                p.volume = originalVolume
            } else {
                delay(totalMs)
                p.pause()
            }
        }
    }

    private fun publishAudioSessionId(id: Int) {
        getSharedPreferences("playback_state", MODE_PRIVATE)
            .edit()
            .putInt("audio_session_id", id)
            .apply()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0 ||
            p.playbackState == Player.STATE_ENDED
        ) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        scope.cancel()
        dailyMinutesJob?.cancel()
        stopSmartSleep()
        runCatching { dualOutputRouter.stop() }
        effects.release()
        beatDetector.release()
        beatStrobe?.stop()
        beatHaptics?.stop()
        EdgeLightingService.setRunning(applicationContext, false)
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}

private fun MutableList<Short>.applyShape(shape: IntArray) {
    val n = minOf(size, shape.size)
    for (i in 0 until n) {
        this[i] = (this[i] + shape[i]).coerceIn(-1500, 1500).toShort()
    }
}
