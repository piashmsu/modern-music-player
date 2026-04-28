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
import com.gsmtrick.musicplayer.data.Album
import com.gsmtrick.musicplayer.data.AppPrefs
import com.gsmtrick.musicplayer.data.Artist
import com.gsmtrick.musicplayer.data.EffectsState
import com.gsmtrick.musicplayer.data.EqPreset
import com.gsmtrick.musicplayer.data.Folder
import com.gsmtrick.musicplayer.data.Genre
import com.gsmtrick.musicplayer.data.Lyrics
import com.gsmtrick.musicplayer.data.LyricsRepository
import com.gsmtrick.musicplayer.data.MusicRepository
import com.gsmtrick.musicplayer.data.Playlist
import com.gsmtrick.musicplayer.data.PlaylistRepository
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

data class LibraryState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val genres: List<Genre> = emptyList(),
)

class PlayerViewModel(app: android.app.Application) : AndroidViewModel(app) {

    private val musicRepo = MusicRepository(app)
    private val prefsRepo = PreferencesRepository(app)
    private val playlistRepo = PlaylistRepository(app)
    private val lyricsRepo = LyricsRepository(app)

    private val _library = MutableStateFlow(LibraryState())
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    private val _prefs = MutableStateFlow(AppPrefs())
    val prefs: StateFlow<AppPrefs> = _prefs.asStateFlow()

    private val _hasPermission = MutableStateFlow(false)
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _lyrics = MutableStateFlow<Lyrics?>(null)
    val lyrics: StateFlow<Lyrics?> = _lyrics.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private var controller: MediaController? = null
    private var positionJob: Job? = null
    private var sleepJob: Job? = null

    init {
        viewModelScope.launch { prefsRepo.prefs.collect { p -> _prefs.value = p } }
        viewModelScope.launch {
            playlistRepo.load()
            playlistRepo.playlists.collect { _playlists.value = it }
        }
        viewModelScope.launch {
            // Poll the playback service's published audio session id.
            val sp = app.getSharedPreferences("playback_state", android.content.Context.MODE_PRIVATE)
            while (true) {
                _audioSessionId.value = sp.getInt("audio_session_id", 0)
                delay(750)
            }
        }
    }

    fun setPermissionGranted(granted: Boolean) {
        _hasPermission.value = granted
        if (granted && _library.value.songs.isEmpty()) {
            loadLibrary()
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val songs = musicRepo.loadSongs()
            val albums = runCatching { musicRepo.loadAlbums() }.getOrDefault(emptyList())
            val artists = runCatching { musicRepo.loadArtists() }.getOrDefault(emptyList())
            val genres = runCatching { musicRepo.loadGenres() }.getOrDefault(emptyList())
            val folders = musicRepo.loadFolders(songs)
            _library.value = LibraryState(songs, albums, artists, folders, genres)
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

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                _state.value.currentSong?.let {
                    viewModelScope.launch { prefsRepo.recordPlay(it.id.toString()) }
                }
                // syncFromController (triggered via onEvents) loads lyrics
                // after the state has been updated to the new song.
            }
        })
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            var tickCount = 0L
            while (true) {
                controller?.let { ctl ->
                    val pos = ctl.currentPosition.coerceAtLeast(0)
                    _state.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = ctl.duration.coerceAtLeast(0),
                        )
                    }
                    // A-B loop enforcement.
                    _abLoop.value?.let { loop ->
                        if (pos >= loop.endMs && loop.startMs in 0..loop.endMs) {
                            ctl.seekTo(loop.startMs)
                        }
                    }
                    // Persist last position roughly every 5 s.
                    tickCount++
                    if (tickCount % 10 == 0L) {
                        _state.value.currentSong?.let { s ->
                            prefsRepo.saveLastPosition(s.id.toString(), pos)
                        }
                    }
                }
                delay(500)
            }
        }
    }

    private var lyricsJob: Job? = null

    private fun loadLyricsForCurrent() {
        val s = _state.value.currentSong
        lyricsJob?.cancel()
        if (s == null) {
            _lyrics.value = null
            return
        }
        val targetId = s.id
        val online = _prefs.value.autoLyrics
        lyricsJob = viewModelScope.launch {
            val loaded = lyricsRepo.loadLyrics(s, allowOnline = online)
            // Discard if the user/player has moved on to a different song.
            if (_state.value.currentSong?.id == targetId) {
                _lyrics.value = loaded
            }
        }
    }

    private fun syncFromController(c: MediaController) {
        val current = c.currentMediaItem
        val song = current?.let { item ->
            _library.value.songs.firstOrNull { it.id.toString() == item.mediaId }
        }
        val previous = _state.value.currentSong
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
        if (song != null && song.id != previous?.id) {
            loadLyricsForCurrent()
            // Per-song speed override is applied by MusicPlaybackService, which
            // observes both the prefs flow and currentMediaItem changes so that
            // global-speed re-applications don't clobber the override.
        }
    }

    fun playSong(song: Song, queue: List<Song> = _library.value.songs) {
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

    /**
     * Play a remote audio stream (e.g. a YouTube extracted audio URL).
     * The mediaId encodes the source page URL so it can be re-resolved later.
     */
    fun playRemoteAudio(
        streamUrl: String,
        title: String,
        artist: String,
        artworkUrl: String?,
        durationMs: Long,
        sourceUrl: String,
    ) {
        val c = controller ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .apply {
                if (artworkUrl != null) setArtworkUri(android.net.Uri.parse(artworkUrl))
            }
            .build()
        val item = MediaItem.Builder()
            .setMediaId("yt:$sourceUrl")
            .setUri(streamUrl)
            .setMediaMetadata(metadata)
            .build()

        // Render a synthetic Song so the rest of the UI (Now Playing, lock
        // screen, mini player) can show metadata.
        val syntheticSong = Song(
            id = -(sourceUrl.hashCode().toLong()),
            title = title,
            artist = artist,
            album = "YouTube",
            albumId = -1L,
            durationMs = durationMs,
            uri = android.net.Uri.parse(streamUrl),
            artworkUri = artworkUrl?.let { android.net.Uri.parse(it) },
        )
        _state.update { it.copy(currentSong = syntheticSong, queue = listOf(syntheticSong)) }

        c.setMediaItem(item)
        c.prepare()
        c.play()
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

    fun saveCustomPreset(preset: EqPreset) {
        viewModelScope.launch { prefsRepo.saveCustomPreset(preset) }
    }

    fun deleteCustomPreset(name: String) {
        viewModelScope.launch { prefsRepo.deleteCustomPreset(name) }
    }

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { prefsRepo.toggleFavorite(songId.toString()) }
    }

    fun isFavorite(songId: Long): Boolean =
        _prefs.value.favorites.contains(songId.toString())

    fun favoriteSongs(): List<Song> {
        val ids = _prefs.value.favorites
        return _library.value.songs.filter { it.id.toString() in ids }
    }

    fun recentlyPlayedSongs(limit: Int = 30): List<Song> {
        val recent = _prefs.value.recentlyPlayed
        val byId = _library.value.songs.associateBy { it.id.toString() }
        return recent.mapNotNull { byId[it] }.take(limit)
    }

    fun mostPlayedSongs(limit: Int = 30): List<Song> {
        val counts = _prefs.value.playCounts
        val byId = _library.value.songs.associateBy { it.id.toString() }
        return counts.entries.sortedByDescending { it.value }
            .mapNotNull { byId[it.key] }
            .take(limit)
    }

    fun createPlaylist(name: String, ids: List<Long> = emptyList()) {
        viewModelScope.launch { playlistRepo.create(name, ids) }
    }

    fun renamePlaylist(id: Long, name: String) {
        viewModelScope.launch { playlistRepo.rename(id, name) }
    }

    fun deletePlaylist(id: Long) {
        viewModelScope.launch { playlistRepo.delete(id) }
    }

    fun addToPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepo.addSong(playlistId, songId) }
    }

    fun removeFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch { playlistRepo.removeSong(playlistId, songId) }
    }

    fun reorderPlaylist(playlistId: Long, fromIndex: Int, toIndex: Int) {
        viewModelScope.launch { playlistRepo.reorder(playlistId, fromIndex, toIndex) }
    }

    fun setPerSongSpeed(songId: Long, speed: Float?) {
        viewModelScope.launch { prefsRepo.setPerSongSpeed(songId.toString(), speed) }
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

    fun setArtworkAdaptive(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setArtworkAdaptive(enabled) }
    }

    fun setFont(font: String) {
        viewModelScope.launch { prefsRepo.setFont(font) }
    }

    fun setVisualizer(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setVisualizer(enabled) }
    }

    fun setBlurredBackground(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setBlurredBackground(enabled) }
    }

    fun setLockScreenPlayer(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setLockScreenPlayer(enabled) }
    }

    fun setAudioQuality(q: String) {
        viewModelScope.launch { prefsRepo.setAudioQuality(q) }
    }

    fun setIncognito(v: Boolean) {
        viewModelScope.launch { prefsRepo.setIncognito(v) }
    }

    fun setAutoLyrics(v: Boolean) {
        viewModelScope.launch { prefsRepo.setAutoLyrics(v) }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { prefsRepo.setLanguage(lang) }
    }

    fun setNowPlayingLayout(layout: String) {
        viewModelScope.launch { prefsRepo.setNowPlayingLayout(layout) }
    }

    fun setGlassTheme(v: Boolean) {
        viewModelScope.launch { prefsRepo.setGlassTheme(v) }
    }

    fun setEdgeLighting(v: Boolean) {
        viewModelScope.launch { prefsRepo.setEdgeLighting(v) }
    }

    fun setAnimatedWallpaper(v: Boolean) {
        viewModelScope.launch { prefsRepo.setAnimatedWallpaper(v) }
    }

    fun setKaraokeMode(v: Boolean) {
        viewModelScope.launch { prefsRepo.setKaraokeMode(v) }
    }

    fun setFolderLockPin(pin: String) {
        viewModelScope.launch { prefsRepo.setFolderLockPin(pin) }
    }

    fun toggleLockedFolder(folder: String) {
        viewModelScope.launch { prefsRepo.toggleLockedFolder(folder) }
    }

    fun pushSearchHistory(query: String) {
        viewModelScope.launch { prefsRepo.pushSearchHistory(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { prefsRepo.clearSearchHistory() }
    }

    fun addBookmark(songId: String, label: String) {
        val pos = controller?.currentPosition ?: 0L
        viewModelScope.launch {
            prefsRepo.addBookmark(songId, com.gsmtrick.musicplayer.data.Bookmark(pos, label))
        }
    }

    fun removeBookmark(songId: String, positionMs: Long) {
        viewModelScope.launch { prefsRepo.removeBookmark(songId, positionMs) }
    }

    fun seekToBookmark(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun saveLastPosition() {
        val c = controller ?: return
        val id = _state.value.currentSong?.id?.toString() ?: return
        viewModelScope.launch { prefsRepo.saveLastPosition(id, c.currentPosition) }
    }

    /** A-B loop is purely playback-session ephemeral state. */
    data class AbLoop(val startMs: Long, val endMs: Long)

    private val _abLoop = MutableStateFlow<AbLoop?>(null)
    val abLoop: StateFlow<AbLoop?> = _abLoop.asStateFlow()

    fun setAbLoopStart() {
        val pos = controller?.currentPosition ?: return
        _abLoop.value = AbLoop(pos, _abLoop.value?.endMs ?: Long.MAX_VALUE)
    }

    fun setAbLoopEnd() {
        val pos = controller?.currentPosition ?: return
        val cur = _abLoop.value
        if (cur == null) {
            _abLoop.value = AbLoop(0L, pos)
        } else {
            _abLoop.value = cur.copy(endMs = pos)
        }
    }

    fun clearAbLoop() {
        _abLoop.value = null
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
        // Service handles fade-out. This is a fallback in case the service isn't running.
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
