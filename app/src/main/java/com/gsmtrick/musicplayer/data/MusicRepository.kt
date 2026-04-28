package com.gsmtrick.musicplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val artworkUri: Uri?,
    val songCount: Int,
)

data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
)

data class Genre(
    val id: Long,
    val name: String,
    val songCount: Int,
)

data class Folder(
    val path: String,
    val name: String,
    val songCount: Int,
)

class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.IS_MUSIC,
            MediaStore.Audio.Media.DATA,
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "${MediaStore.Audio.Media.DURATION} >= 15000"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
            val yearCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
            val mimeCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                val uri = ContentUris.withAppendedId(collection, id)
                val artworkUri = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"),
                    albumId
                )
                val data = c.getString(dataCol)
                songs += Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = c.getString(artistCol) ?: "Unknown Artist",
                    album = c.getString(albumCol) ?: "",
                    albumId = albumId,
                    durationMs = c.getLong(durationCol),
                    uri = uri,
                    artworkUri = artworkUri,
                    track = c.getInt(trackCol),
                    year = c.getInt(yearCol),
                    mimeType = c.getString(mimeCol),
                    filePath = data,
                )
            }
        }
        songs
    }

    suspend fun loadAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Albums._ID,
            MediaStore.Audio.Albums.ALBUM,
            MediaStore.Audio.Albums.ARTIST,
            MediaStore.Audio.Albums.NUMBER_OF_SONGS,
        )
        val albums = mutableListOf<Album>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Audio.Albums.ALBUM} COLLATE NOCASE ASC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST)
            val countCol = c.getColumnIndexOrThrow(MediaStore.Audio.Albums.NUMBER_OF_SONGS)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val art = ContentUris.withAppendedId(
                    Uri.parse("content://media/external/audio/albumart"), id
                )
                albums += Album(
                    id = id,
                    name = c.getString(nameCol) ?: "Unknown",
                    artist = c.getString(artistCol) ?: "Unknown",
                    artworkUri = art,
                    songCount = c.getInt(countCol),
                )
            }
        }
        albums
    }

    suspend fun loadArtists(): List<Artist> = withContext(Dispatchers.IO) {
        val collection = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_ALBUMS,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS,
        )
        val list = mutableListOf<Artist>()
        context.contentResolver.query(
            collection, projection, null, null,
            "${MediaStore.Audio.Artists.ARTIST} COLLATE NOCASE ASC"
        )?.use { c ->
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_ALBUMS)
            val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Artists.NUMBER_OF_TRACKS)
            while (c.moveToNext()) {
                list += Artist(
                    name = c.getString(nameCol) ?: "Unknown",
                    albumCount = c.getInt(albumCol),
                    songCount = c.getInt(trackCol),
                )
            }
        }
        list
    }

    suspend fun loadGenres(): List<Genre> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Genre>()
        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME),
                null, null,
                "${MediaStore.Audio.Genres.NAME} COLLATE NOCASE ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Genres._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Genres.NAME)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val name = c.getString(nameCol) ?: continue
                    val count = countSongsInGenre(id)
                    if (count > 0) list += Genre(id, name, count)
                }
            }
        }
        list
    }

    private fun countSongsInGenre(genreId: Long): Int {
        return runCatching {
            val uri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
            context.contentResolver.query(uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)
                ?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    suspend fun loadFolders(songs: List<Song>): List<Folder> = withContext(Dispatchers.Default) {
        songs.mapNotNull { s -> s.filePath?.let { File(it).parent } }
            .groupingBy { it }
            .eachCount()
            .map { (path, count) -> Folder(path = path, name = File(path).name, songCount = count) }
            .sortedBy { it.name.lowercase() }
    }

    suspend fun songsForAlbum(albumId: Long, all: List<Song>): List<Song> =
        all.filter { it.albumId == albumId }
            .sortedWith(compareBy({ it.track }, { it.title.lowercase() }))

    suspend fun songsForArtist(name: String, all: List<Song>): List<Song> =
        all.filter { it.artist.equals(name, ignoreCase = true) }
            .sortedBy { it.title.lowercase() }

    suspend fun songsForFolder(path: String, all: List<Song>): List<Song> =
        all.filter { s -> s.filePath?.let { File(it).parent == path } == true }
            .sortedBy { it.title.lowercase() }

    suspend fun songsForGenre(genreId: Long, all: List<Song>): List<Song> =
        withContext(Dispatchers.IO) {
            val ids = mutableSetOf<Long>()
            runCatching {
                val uri = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                context.contentResolver.query(
                    uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null
                )?.use { c ->
                    val col = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                    while (c.moveToNext()) ids += c.getLong(col)
                }
            }
            all.filter { it.id in ids }.sortedBy { it.title.lowercase() }
        }
}
