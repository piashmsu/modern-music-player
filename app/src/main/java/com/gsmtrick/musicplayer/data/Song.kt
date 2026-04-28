package com.gsmtrick.musicplayer.data

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val uri: Uri,
    val artworkUri: Uri?,
    val track: Int = 0,
    val year: Int = 0,
    val mimeType: String? = null,
)
