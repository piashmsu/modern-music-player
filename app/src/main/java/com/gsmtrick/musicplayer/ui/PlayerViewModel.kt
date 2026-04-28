package com.gsmtrick.musicplayer.ui

import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.gsmtrick.musicplayer.data.AppPrefs
import com.gsmtrick.musicplayer.data.EffectsState
import com.gsmtrick.musicplayer.data.MusicRepository
import com.gsmtrick.musicplayer.data.PreferencesRepository
import com.gsmtrick.musicplayer.data.Song
import com.gsmtrick.musicplayer.playback.MusicPlaybackService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PlaybackUiState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val queue: List<Song> = emptyList(),
)

class PlayerViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val musicRepo = MusicRepository(app)
    private val prefsRepo = PreferencesRepository(app)

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null

    init {
        viewModelScope.launch {
            prefsRepo.prefs.collect { p -> _prefs.value = p }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted && _songs.value.isEmpty()) {
            loadSongs()
        }
    }

    fun loadSongs() {
        viewModelScope.launch {
            _songs.value = musicRepo.loadSongs()
        }
    }

    fun connectController(context: Context) {
        if (controller != null) return
        val token = SessionToken(
            context.applicationContext,
            ComponentName(context.applicationContext, MusicPlaybackService::class.java),
        )
        val future = MediaController.Builder(context.applicationContext, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            attachListener(c)
            syncFromController(c)
        }, MoreExecutors.directExecutor())
    }

    fun releaseController() {
        positionJob?.cancel()
        controller?.release()
        controller = null
    }

    private fun attachListener(c: MediaController) {
        c.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                syncFromController(player as? MediaController ?: c)
            }
        })
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                controller?.let { ctl ->
                    _state.update {
                        it.copy(
                            positionMs = ctl.currentPosition.coerceAtLeast(0),
                            durationMs = ctl.duration.coerceAtLeast(0),
                        )
                    }
                }
                delay(500)
            }
        }
    }

    private fun syncFromController(c: MediaController) {
        val current = c.currentMediaItem
        val song = current?.let { item ->
            _songs.value.firstOrNull { it.id.toString() == item.mediaId }
        }
        _state.update {
            it.copy(
                currentSong = song,
                isPlaying = c.isPlaying,
                durationMs = c.duration.coerceAtLeast(0),
                positionMs = c.currentPosition.coerceAtLeast(0),
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
            )
        }
    }

    fun playSong(song: Song, queue: List<Song> = _songs.value) {
        val c = controller ?: return
        val items = queue.map { it.toMediaItem() }
        val index = queue.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
        c.setMediaItems(items, index, 0)
        c.prepare()
        c.play()
        _state.update { it.copy(queue = queue) }
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() = controller?.seekToNext()
    fun previous() = controller?.seekToPrevious()
    fun seekTo(positionMs: Long) = controller?.seekTo(positionMs)
    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }
    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun updateEffects(transform: (EffectsState) -> EffectsState) {
        viewModelScope.launch {
            prefsRepo.setEffects(transform(_prefs.value.effects))
        }
    }

    fun setTheme(mode: String) {
        viewModelScope.launch { prefsRepo.setTheme(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setDynamic(enabled) }
    }

    fun setAccent(accent: String) {
        viewModelScope.launch { prefsRepo.setAccent(accent) }
    }

    fun setSpeed(speed: Float) {
        viewModelScope.launch {
            prefsRepo.setSpeed(speed)
            controller?.setPlaybackSpeed(speed)
        }
    }

    fun setSleepTimer(minutes: Int) {
        viewModelScope.launch { prefsRepo.setSleep(minutes) }
        sleepJob?.cancel()
        if (minutes <= 0) return
        sleepJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            prefsRepo.setSleep(0)
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val md = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)
            .setArtworkUri(artworkUri)
            .build()
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(md)
            .build()
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as android.app.Application
                PlayerViewModel(app)
            }
        }
    }
}
