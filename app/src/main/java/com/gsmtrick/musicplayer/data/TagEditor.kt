package com.gsmtrick.musicplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File

/**
 * v3.4 — Read/write basic ID3 / Vorbis / MP4 tags via JAudioTagger. Works
 * on any audio file the user has direct filesystem write access to (so
 * effectively only files in app-private dirs or external storage on
 * Android < 11 unless the user has granted MANAGE_EXTERNAL_STORAGE).
 *
 * On modern Android (Q+) most user music sits in MediaStore-mediated
 * locations and direct write fails — surfacing that as a friendly error
 * message is the caller's responsibility.
 */
data class TagInfo(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: String,
    val track: String,
    val comment: String,
)

object TagEditor {

    suspend fun read(path: String): TagInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.canRead()) return@runCatching null
            val af = AudioFileIO.read(file)
            val t = af.tag ?: return@runCatching null
            TagInfo(
                title = t.getFirst(FieldKey.TITLE).orEmpty(),
                artist = t.getFirst(FieldKey.ARTIST).orEmpty(),
                album = t.getFirst(FieldKey.ALBUM).orEmpty(),
                albumArtist = t.getFirst(FieldKey.ALBUM_ARTIST).orEmpty(),
                genre = t.getFirst(FieldKey.GENRE).orEmpty(),
                year = t.getFirst(FieldKey.YEAR).orEmpty(),
                track = t.getFirst(FieldKey.TRACK).orEmpty(),
                comment = t.getFirst(FieldKey.COMMENT).orEmpty(),
            )
        }.getOrNull()
    }

    /** Returns null on success, an error message on failure. */
    suspend fun write(path: String, info: TagInfo): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path)
            if (!file.canWrite()) return@runCatching "File is not writable"
            val af = AudioFileIO.read(file)
            val t = af.tagOrCreateAndSetDefault
            t.setField(FieldKey.TITLE, info.title)
            t.setField(FieldKey.ARTIST, info.artist)
            t.setField(FieldKey.ALBUM, info.album)
            t.setField(FieldKey.ALBUM_ARTIST, info.albumArtist)
            t.setField(FieldKey.GENRE, info.genre)
            t.setField(FieldKey.YEAR, info.year)
            t.setField(FieldKey.TRACK, info.track)
            t.setField(FieldKey.COMMENT, info.comment)
            af.commit()
            null
        }.getOrElse { it.message ?: it.javaClass.simpleName }
    }

    /** Best-effort: jaudiotagger talks the path API only. */
    @Suppress("UNUSED_PARAMETER")
    suspend fun isWritableForPath(context: Context, path: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching { File(path).canWrite() }.getOrDefault(false)
        }
}
