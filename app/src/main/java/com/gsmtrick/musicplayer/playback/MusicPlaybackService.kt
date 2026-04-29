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
import com.gsmtrick.musicplayer.data.LastFmRepository
import com.gsmtrick.musicplayer.data.MusicRepository
import com.gsmtrick.musicplayer.data.PreferencesRepository
import com.gsmtrick.musicplayer.data.Song
import com.gsmtrick.musicplayer.effects.AudioEffectsController
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

    // Last.fm scrobbling state
    private val lastfmRepo by lazy { LastFmRepository(applicationContext) }
    private val musicRepo by lazy { MusicRepository(applicationContext) }
    private var currentSongStartedAtSec: Long = 0L
    private var currentScrobbleSong: Song? = null
    private var scrobbleJob: Job? = null

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
                publishAudioSessionId(audioSessionId)
                scope.launch {
                    val state = prefs.prefs.map { it.effects }.distinctUntilChanged()
                    state.collectLatest { e -> effects.apply(e) }
                }
            }
        })
        effects.attach(exoPlayer.audioSessionId)
        publishAudioSessionId(exoPlayer.audioSessionId)

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
     * Lightweight loop that polls the player position to drive:
     *  1. End-of-track volume fade-out for crossfade transitions.
     *  2. Last.fm scrobble dispatch once the song has been played for the
     *     standard >=30 s and >= 50 % rule.
     */
    private suspend fun runCrossfadeAndScrobbleLoop(p: ExoPlayer) {
        var lastFadeApplied = false
        while (true) {
            val pos = p.currentPosition.coerceAtLeast(0)
            val dur = p.duration
            val playing = p.isPlaying
            val cf = crossfadeSec
            // Crossfade tail fade-out: smoothly drop volume during the last
            // [cf] seconds of the track. Skip when paused or when the user
            // disabled crossfade.
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

            // Last.fm scrobble dispatch: send once we cross the 50%/4-min mark.
            val song = currentScrobbleSong
            if (song != null && playing && scrobbleJob == null) {
                val played = pos
                val durMs = if (dur > 0 && dur != C.TIME_UNSET) dur else song.durationMs
                val threshold = minOf(durMs / 2, 4 * 60_000L)
                if (played >= threshold && played >= 30_000L) {
                    val s = song
                    val started = currentSongStartedAtSec
                    scrobbleJob = scope.launch(Dispatchers.IO) {
                        runCatching {
                            val pp = prefs.prefs.first()
                            if (pp.lastfmEnabled && pp.lastfmSessionKey.isNotEmpty()) {
                                lastfmRepo.scrobble(pp.lastfmSessionKey, s, started)
                            }
                        }
                    }
                }
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
        currentScrobbleSong = null
        scrobbleJob?.cancel()
        scrobbleJob = null
        if (mediaId == null) return
        currentSongStartedAtSec = System.currentTimeMillis() / 1000L
        scope.launch(Dispatchers.IO) {
            val pp = runCatching { prefs.prefs.first() }.getOrNull() ?: return@launch
            if (!pp.lastfmEnabled || pp.lastfmSessionKey.isEmpty()) return@launch
            // Resolve song metadata via MediaStore lookup. mediaId is the song
            // row id stringified; loadSongs() returns the entire library so we
            // pick from there for accurate duration/artist/album.
            val songs = runCatching { musicRepo.loadSongs() }.getOrNull() ?: return@launch
            val song = songs.firstOrNull { it.id.toString() == mediaId } ?: return@launch
            currentScrobbleSong = song
            runCatching { lastfmRepo.nowPlaying(pp.lastfmSessionKey, song) }
        }
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
        effects.release()
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
