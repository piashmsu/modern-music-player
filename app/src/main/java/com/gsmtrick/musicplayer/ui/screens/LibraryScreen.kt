package com.gsmtrick.musicplayer.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gsmtrick.musicplayer.data.Album
import com.gsmtrick.musicplayer.data.Artist
import com.gsmtrick.musicplayer.data.Folder
import com.gsmtrick.musicplayer.data.Genre
import com.gsmtrick.musicplayer.data.Playlist
import com.gsmtrick.musicplayer.data.Song
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import java.util.concurrent.TimeUnit

private enum class LibTab(val titleRes: Int, val icon: ImageVector) {
    ForYou(com.gsmtrick.musicplayer.R.string.tab_for_you, Icons.Rounded.AutoAwesome),
    Songs(com.gsmtrick.musicplayer.R.string.tab_songs, Icons.Rounded.MusicNote),
    Albums(com.gsmtrick.musicplayer.R.string.tab_albums, Icons.Rounded.Album),
    Artists(com.gsmtrick.musicplayer.R.string.tab_artists, Icons.Rounded.Person),
    Playlists(com.gsmtrick.musicplayer.R.string.tab_playlists, Icons.Rounded.PlaylistPlay),
    Favorites(com.gsmtrick.musicplayer.R.string.tab_favorites, Icons.Rounded.Favorite),
    Recent(com.gsmtrick.musicplayer.R.string.tab_recent, Icons.Rounded.History),
    MostPlayed(com.gsmtrick.musicplayer.R.string.tab_most_played, Icons.Rounded.Whatshot),
    Folders(com.gsmtrick.musicplayer.R.string.tab_folders, Icons.Rounded.Folder),
    Genres(com.gsmtrick.musicplayer.R.string.tab_genres, Icons.Rounded.QueueMusic),
    Decades(com.gsmtrick.musicplayer.R.string.tab_decades, Icons.Rounded.History),
    Hidden(com.gsmtrick.musicplayer.R.string.tab_hidden, Icons.Rounded.VisibilityOff),
    Trash(com.gsmtrick.musicplayer.R.string.tab_trash, Icons.Rounded.Delete),
}

@Composable
fun LibraryScreen(viewModel: PlayerViewModel) {
    val library by viewModel.library.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasPerm by viewModel.hasPermission.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(LibTab.Songs) }
    var openAlbum by remember { mutableStateOf<Album?>(null) }
    var openArtist by remember { mutableStateOf<Artist?>(null) }
    var openFolder by remember { mutableStateOf<Folder?>(null) }
    var openGenre by remember { mutableStateOf<Genre?>(null) }
    var openDecade by remember { mutableStateOf<Int?>(null) }
    var openPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var showCreatePlaylist by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        Text(
            text = "Your Library",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp, bottom = 4.dp),
        )
        Text(
            text = "${library.songs.size} songs · ${library.albums.size} albums · ${library.artists.size} artists",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            singleLine = true,
            placeholder = { Text("Search songs, artists, albums") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )

        if (!hasPerm) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Storage permission required to read your music library.",
                    modifier = Modifier.padding(32.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Column
        }

        ScrollableTabRow(
            selectedTabIndex = LibTab.values().indexOf(tab),
            edgePadding = 12.dp,
        ) {
            LibTab.values().forEachIndexed { idx, t ->
                Tab(
                    selected = tab == t,
                    onClick = { tab = t },
                    text = { Text(androidx.compose.ui.res.stringResource(t.titleRes)) },
                    icon = { Icon(t.icon, null, modifier = Modifier.size(20.dp)) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        val q = query.trim()
        when (tab) {
            LibTab.ForYou -> ForYouContent(
                viewModel = viewModel,
                state = state,
                favorites = prefs.favorites,
            )
            LibTab.Songs -> SongList(
                songs = filterSongs(viewModel.visibleSongs(), q),
                state = state,
                viewModel = viewModel,
                favorites = prefs.favorites,
            )
            LibTab.Hidden -> SongList(
                songs = filterSongs(viewModel.hiddenSongs(), q),
                state = state,
                viewModel = viewModel,
                favorites = prefs.favorites,
            )
            LibTab.Trash -> SongList(
                songs = filterSongs(viewModel.trashedSongs(), q),
                state = state,
                viewModel = viewModel,
                favorites = prefs.favorites,
            )
            LibTab.Albums -> AlbumGrid(
                albums = library.albums.filter { q.isBlank() || it.name.contains(q, true) },
                onClick = { openAlbum = it },
            )
            LibTab.Artists -> ArtistList(
                artists = library.artists.filter { q.isBlank() || it.name.contains(q, true) },
                onClick = { openArtist = it },
            )
            LibTab.Playlists -> PlaylistList(
                playlists = playlists.filter { q.isBlank() || it.name.contains(q, true) },
                onCreate = { showCreatePlaylist = true },
                onClick = { openPlaylist = it },
            )
            LibTab.Favorites -> SongList(
                songs = filterSongs(viewModel.favoriteSongs(), q),
                state = state,
                viewModel = viewModel,
                favorites = prefs.favorites,
            )
            LibTab.Recent -> SongList(
                songs = filterSongs(viewModel.recentlyPlayedSongs(), q),
                state = state,
                viewModel = viewModel,
                favorites = prefs.favorites,
            )
            LibTab.MostPlayed -> SongList(
                songs = filterSongs(viewModel.mostPlayedSongs(), q),
                state = state,
                viewModel = viewModel,
                favorites = prefs.favorites,
            )
            LibTab.Folders -> FolderList(
                folders = library.folders.filter { q.isBlank() || it.name.contains(q, true) },
                lockedFolders = prefs.lockedFolders,
                pin = prefs.folderLockPin,
                onClick = { openFolder = it },
                onToggleLock = { path -> viewModel.toggleLockedFolder(path) },
                onSetPin = { newPin -> viewModel.setFolderLockPin(newPin) },
            )
            LibTab.Genres -> GenreList(
                genres = library.genres.filter { q.isBlank() || it.name.contains(q, true) },
                onClick = { openGenre = it },
            )
            LibTab.Decades -> DecadeList(
                songs = library.songs,
                onClick = { openDecade = it },
            )
        }
    }

    val openSongs: List<Song>? = when {
        openAlbum != null -> library.songs.filter { it.albumId == openAlbum!!.id }
        openArtist != null -> library.songs.filter { it.artist.equals(openArtist!!.name, true) }
        openFolder != null -> library.songs.filter { s ->
            s.filePath?.let { java.io.File(it).parent == openFolder!!.path } == true
        }
        openGenre != null -> library.songs // The genre detail uses lazy lookup; show all & filter by genre id
        openDecade != null -> library.songs.filter { s ->
            val d = (s.year / 10) * 10
            d == openDecade
        }
        openPlaylist != null -> openPlaylist!!.songIds.mapNotNull { id ->
            library.songs.firstOrNull { it.id == id }
        }
        else -> null
    }

    if (openSongs != null) {
        SongDetailDialog(
            title = openAlbum?.name
                ?: openArtist?.name
                ?: openFolder?.name
                ?: openGenre?.name
                ?: openDecade?.let { "${it}s" }
                ?: openPlaylist?.name
                ?: "",
            songs = openSongs,
            state = state,
            favorites = prefs.favorites,
            viewModel = viewModel,
            onClose = {
                openAlbum = null
                openArtist = null
                openFolder = null
                openGenre = null
                openDecade = null
                openPlaylist = null
            },
            onDeletePlaylist = openPlaylist?.let { p -> { viewModel.deletePlaylist(p.id) } },
        )
    }

    if (showCreatePlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreatePlaylist = false },
            title = { Text("Create playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) viewModel.createPlaylist(name.trim())
                    showCreatePlaylist = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel") }
            },
        )
    }
}

private fun filterSongs(songs: List<Song>, q: String): List<Song> {
    if (q.isBlank()) return songs
    return songs.filter {
        it.title.contains(q, true) ||
            it.artist.contains(q, true) ||
            it.album.contains(q, true)
    }
}

@Composable
private fun ForYouContent(
    viewModel: PlayerViewModel,
    state: com.gsmtrick.musicplayer.ui.PlaybackUiState,
    favorites: Set<String>,
) {
    val sections = remember(viewModel.library.value, viewModel.prefs.value) {
        listOf(
            "Daily Mix" to viewModel.dailyMix(),
            "On Repeat" to viewModel.onRepeatSongs(),
            "Discovery" to viewModel.discoverySongs(),
            "Mood: Chill" to viewModel.moodSongs("chill"),
            "Mood: Upbeat" to viewModel.moodSongs("upbeat"),
            "Tempo: Fast" to viewModel.tempoSongs(true),
            "Tempo: Slow" to viewModel.tempoSongs(false),
            "Recently played" to viewModel.recentlyPlayedSongs(),
        ).filter { it.second.isNotEmpty() }
    }
    if (sections.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Play a few songs first to see suggestions.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)) {
        sections.forEach { (title, songs) ->
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        if (songs.isNotEmpty()) viewModel.playSong(songs.first(), songs)
                    }) { Text("Play") }
                }
            }
            items(songs.take(8), key = { "$title:${it.id}" }) { song ->
                SongRow(
                    song = song,
                    isCurrent = state.currentSong?.id == song.id,
                    isFavorite = song.id.toString() in favorites,
                    onClick = { viewModel.playSong(song, songs) },
                    onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                )
            }
        }
    }
}

@Composable
private fun SongList(
    songs: List<Song>,
    state: com.gsmtrick.musicplayer.ui.PlaybackUiState,
    viewModel: PlayerViewModel,
    favorites: Set<String>,
) {
    if (songs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing here yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    var actionsFor by remember { mutableStateOf<Song?>(null) }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = state.currentSong?.id == song.id,
                isFavorite = song.id.toString() in favorites,
                onClick = { viewModel.playSong(song, songs) },
                onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                onLongClick = { actionsFor = song },
            )
        }
    }
    actionsFor?.let { song ->
        SongActionsDialog(song = song, onDismiss = { actionsFor = null })
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onClick: (Album) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(12.dp),
    ) {
        items(albums, key = { it.id }) { album ->
            Column(
                modifier = Modifier
                    .padding(6.dp)
                    .clickable { onClick(album) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (album.artworkUri != null) {
                        AsyncImage(
                            model = album.artworkUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Icon(
                        Icons.Rounded.Album,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    album.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${album.artist} · ${album.songCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onClick: (Artist) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(artists, key = { it.name }) { a ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(a) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Person, null)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${a.albumCount} albums · ${a.songCount} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderList(
    folders: List<Folder>,
    lockedFolders: Set<String>,
    pin: String,
    onClick: (Folder) -> Unit,
    onToggleLock: (String) -> Unit,
    onSetPin: (String) -> Unit,
) {
    var pendingFolder by remember { mutableStateOf<Folder?>(null) }
    var menuFor by remember { mutableStateOf<Folder?>(null) }
    var setPinOpen by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(folders, key = { it.path }) { f ->
            val locked = f.path in lockedFolders
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            if (locked && pin.isNotEmpty()) pendingFolder = f else onClick(f)
                        },
                        onLongClick = { menuFor = f },
                    )
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (locked) Icons.Rounded.Lock else Icons.Rounded.Folder,
                    null,
                    tint = if (locked) MaterialTheme.colorScheme.primary
                        else androidx.compose.ui.graphics.Color.Unspecified,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (locked) "Locked" else f.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    "${f.songCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    menuFor?.let { f ->
        AlertDialog(
            onDismissRequest = { menuFor = null },
            title = { Text(f.name) },
            text = {
                Column {
                    TextButton(onClick = {
                        if (pin.isEmpty()) setPinOpen = true
                        else { onToggleLock(f.path); menuFor = null }
                    }) {
                        Text(if (f.path in lockedFolders) "Unlock folder" else "Lock folder")
                    }
                    TextButton(onClick = { setPinOpen = true; menuFor = null }) {
                        Text(if (pin.isEmpty()) "Set PIN" else "Change PIN")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { menuFor = null }) { Text("Close") }
            },
        )
    }

    pendingFolder?.let { f ->
        PinDialog(
            title = "Enter PIN to open ${f.name}",
            onDismiss = { pendingFolder = null },
            onSubmit = { entered ->
                if (entered == pin) {
                    onClick(f)
                    pendingFolder = null
                }
            },
        )
    }

    if (setPinOpen) {
        PinDialog(
            title = if (pin.isEmpty()) "Set 4-digit PIN" else "Change PIN",
            onDismiss = { setPinOpen = false },
            onSubmit = { entered ->
                if (entered.length in 4..8) {
                    onSetPin(entered)
                    setPinOpen = false
                }
            },
        )
    }
}

@Composable
private fun DecadeList(songs: List<Song>, onClick: (Int) -> Unit) {
    val decades = remember(songs) {
        songs.asSequence()
            .filter { it.year > 0 }
            .groupBy { (it.year / 10) * 10 }
            .map { (decade, list) -> decade to list.size }
            .sortedByDescending { it.first }
    }
    if (decades.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No year metadata found in your songs.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(32.dp),
            )
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(decades, key = { it.first }) { (decade, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(decade) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.History, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "${decade}s",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "$count",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PinDialog(
    title: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter { c -> c.isDigit() }.take(8) },
                label = { Text("PIN") },
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun GenreList(genres: List<Genre>, onClick: (Genre) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        items(genres, key = { it.id }) { g ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(g) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.QueueMusic, null)
                Spacer(Modifier.width(12.dp))
                Text(g.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "${g.songCount}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlaylistList(
    playlists: List<Playlist>,
    onCreate: () -> Unit,
    onClick: (Playlist) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 96.dp)) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onCreate)
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Create playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        items(playlists, key = { it.id }) { p ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onClick(p) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlaylistPlay, null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "${p.songIds.size} songs",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SongDetailDialog(
    title: String,
    songs: List<Song>,
    state: com.gsmtrick.musicplayer.ui.PlaybackUiState,
    favorites: Set<String>,
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onDeletePlaylist: (() -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.ArrowBack, null) }
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
        },
        text = {
            Column {
                LazyColumn(modifier = Modifier.height(420.dp)) {
                    items(songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            isCurrent = state.currentSong?.id == song.id,
                            isFavorite = song.id.toString() in favorites,
                            onClick = {
                                viewModel.playSong(song, songs)
                                onClose()
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(song.id) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (songs.isNotEmpty()) {
                    viewModel.playSong(songs.first(), songs)
                    onClose()
                }
            }) { Text("Play all") }
        },
        dismissButton = {
            if (onDeletePlaylist != null) {
                TextButton(onClick = {
                    onDeletePlaylist()
                    onClose()
                }) { Text("Delete") }
            } else {
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
    )
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (song.artworkUri != null) {
                AsyncImage(
                    model = song.artworkUri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Unspecified,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                null,
                tint = if (isFavorite) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

internal fun formatDuration(ms: Long): String {
    if (ms <= 0) return "--:--"
    val total = TimeUnit.MILLISECONDS.toSeconds(ms)
    val m = total / 60
    val s = total % 60
    val raw = "%d:%02d".format(m, s)
    return com.gsmtrick.musicplayer.util.banglaNumeralsIf(
        com.gsmtrick.musicplayer.util.banglaNumeralsGlobal, raw
    )
}
