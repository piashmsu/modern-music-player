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
import com.gsmtrick.musicplayer.data.PreferencesRepository
import com.gsmtrick.musicplayer.effects.AudioEffectsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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
                effects.apply(p.effects)
                applyChannelMix(p.effects)
                globalSpeed = p.playbackSpeed.coerceIn(0.5f, 2f)
                pitchSemitones = p.effects.pitchSemitones
                perSongSpeed = p.perSongSpeed
                lockScreenPlayerEnabled = p.lockScreenPlayer
                applyPlaybackParameters(exoPlayer)
                fadeOutEnabled = p.effects.sleepFadeOut
                if (p.sleepMinutes != lastSleepMinutes) {
                    lastSleepMinutes = p.sleepMinutes
                    handleSleepTimer(p.sleepMinutes)
                }
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                applyPlaybackParameters(exoPlayer)
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

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private fun applyChannelMix(state: EffectsState) {
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
        // 2-input -> 2-output matrix.
        val matrix = when (state.monoMode) {
            "mono" -> floatArrayOf(
                0.5f, 0.5f,
                0.5f, 0.5f,
            )
            "reverse" -> floatArrayOf(
                0f, 1f,
                1f, 0f,
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
