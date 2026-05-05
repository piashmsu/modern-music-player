package com.gsmtrick.musicplayer.cast

import android.content.Context
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import com.gsmtrick.musicplayer.data.Song
import android.net.Uri
import com.google.android.gms.common.ConnectionResult

/**
 * v3.4 — Tiny façade over [CastContext]. All Cast features are gated
 * behind Google Play Services availability, so a Play-services-less
 * device gracefully degrades to local playback only.
 */
object CastManager {

    fun isAvailable(context: Context): Boolean {
        val avail = GoogleApiAvailability.getInstance()
            .isGooglePlayServicesAvailable(context)
        return avail == ConnectionResult.SUCCESS
    }

    fun castContext(context: Context): CastContext? {
        if (!isAvailable(context)) return null
        return runCatching { CastContext.getSharedInstance(context.applicationContext) }
            .getOrNull()
    }

    /** Returns the active session if any, or null when not casting. */
    fun currentSession(context: Context): CastSession? =
        castContext(context)?.sessionManager?.currentCastSession

    /**
     * Load a [Song] onto the active Cast session. The receiver expects
     * a publicly resolvable URL — local-only files don't cast. Returns
     * true on a best-effort attempt.
     */
    fun loadSong(context: Context, song: Song, mimeType: String? = null): Boolean {
        val session = currentSession(context) ?: return false
        val client = session.remoteMediaClient ?: return false
        val streamUri: Uri = song.uri
        val artUri = song.artworkUri
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MUSIC_TRACK).apply {
            putString(MediaMetadata.KEY_TITLE, song.title)
            putString(MediaMetadata.KEY_ARTIST, song.artist)
            putString(MediaMetadata.KEY_ALBUM_TITLE, song.album)
            artUri?.let { addImage(WebImage(it)) }
        }
        val info = MediaInfo.Builder(streamUri.toString())
            .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeType ?: song.mimeType ?: "audio/*")
            .setMetadata(meta)
            .build()
        val req = MediaLoadRequestData.Builder().setMediaInfo(info).build()
        return runCatching { client.load(req) }.isSuccess
    }
}
