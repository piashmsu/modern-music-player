package com.gsmtrick.musicplayer.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer

/**
 * v3.4 — Pure-Java audio cutter that copies a startMs..endMs window of
 * a source audio file to a new `.m4a` (AAC in MP4 container) file by
 * doing a sample-by-sample re-mux. No transcoding, so quality is bit-
 * perfect and the operation is fast even on low-end phones.
 *
 * Limitation: only AAC sources can be saved as `.m4a` losslessly. For
 * MP3/Opus/Vorbis we fall back to including the file extension as-is and
 * the caller is responsible for choosing the matching muxer format.
 */
object AudioCutter {

    sealed class Result {
        data class Saved(val uri: Uri, val displayName: String) : Result()
        data class Failed(val reason: String) : Result()
    }

    /**
     * Copy [sourcePath] between [startMs] and [endMs] into a new audio
     * file in the Music/Modern Player Cuts/ directory and register it
     * with MediaStore so it shows up in the user's library and ringtone
     * picker. Returns the resulting [Uri] on success.
     */
    suspend fun cut(
        context: Context,
        sourcePath: String,
        startMs: Long,
        endMs: Long,
        outputName: String,
    ): Result = withContext(Dispatchers.IO) {
        if (endMs <= startMs) return@withContext Result.Failed("End must be after start")
        val src = File(sourcePath)
        if (!src.canRead()) return@withContext Result.Failed("Cannot read source")

        val tmp = File.createTempFile("mp_cut_", ".m4a", context.cacheDir)
        try {
            val ok = remuxSliceToFile(sourcePath, tmp.absolutePath, startMs, endMs)
            if (!ok) return@withContext Result.Failed("Codec rejected the slice")
            val savedUri = saveToMediaStore(context, tmp, outputName)
                ?: return@withContext Result.Failed("Could not save into MediaStore")
            Result.Saved(savedUri, outputName)
        } catch (e: Throwable) {
            Result.Failed(e.message ?: e.javaClass.simpleName)
        } finally {
            tmp.delete()
        }
    }

    /**
     * Remux a slice using MediaExtractor + MediaMuxer. Returns true on
     * success.
     */
    private fun remuxSliceToFile(
        sourcePath: String,
        destPath: String,
        startMs: Long,
        endMs: Long,
    ): Boolean {
        val ex = MediaExtractor()
        ex.setDataSource(sourcePath)
        // Pick the first audio track.
        var trackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                trackIndex = i
                format = f
                break
            }
        }
        if (trackIndex < 0 || format == null) {
            ex.release()
            return false
        }
        ex.selectTrack(trackIndex)
        val muxer = MediaMuxer(destPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val outIndex = muxer.addTrack(format)
        muxer.start()
        ex.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buf = ByteBuffer.allocate(1024 * 256)
        val info = MediaCodec.BufferInfo()
        var firstSampleTime = -1L
        try {
            while (true) {
                info.offset = 0
                info.size = ex.readSampleData(buf, 0)
                if (info.size < 0) break
                val sampleTimeUs = ex.sampleTime
                if (sampleTimeUs > endMs * 1000L) break
                if (sampleTimeUs >= startMs * 1000L) {
                    if (firstSampleTime < 0) firstSampleTime = sampleTimeUs
                    info.presentationTimeUs = sampleTimeUs - firstSampleTime
                    info.flags = ex.sampleFlags
                    muxer.writeSampleData(outIndex, buf, info)
                }
                if (!ex.advance()) break
            }
        } finally {
            try {
                muxer.stop()
            } catch (_: Throwable) {
            }
            muxer.release()
            ex.release()
        }
        return File(destPath).length() > 0
    }

    private fun saveToMediaStore(context: Context, src: File, displayName: String): Uri? {
        val resolver = context.contentResolver
        val safeName = if (displayName.endsWith(".m4a", true)) displayName else "$displayName.m4a"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, safeName)
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(
                    MediaStore.Audio.Media.RELATIVE_PATH,
                    Environment.DIRECTORY_MUSIC + "/Modern Player Cuts",
                )
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            } else {
                @Suppress("DEPRECATION")
                val out = File(
                    Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_MUSIC
                    ),
                    "Modern Player Cuts/$safeName",
                )
                out.parentFile?.mkdirs()
                src.copyTo(out, overwrite = true)
                @Suppress("DEPRECATION")
                put(MediaStore.Audio.Media.DATA, out.absolutePath)
            }
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.openOutputStream(uri)?.use { os ->
                src.inputStream().use { it.copyTo(os) }
            }
            val v = ContentValues().apply {
                put(MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(uri, v, null, null)
        }
        return uri
    }

    /**
     * Set [uri] as the system ringtone (or notification sound). Requires
     * `WRITE_SETTINGS` to be granted; the caller is responsible for
     * checking [Settings.System.canWrite] and routing to
     * [Settings.ACTION_MANAGE_WRITE_SETTINGS] if false.
     */
    fun applyAsRingtone(context: Context, uri: Uri, type: Int = RingtoneManager.TYPE_RINGTONE): Boolean {
        if (!Settings.System.canWrite(context)) return false
        return runCatching {
            RingtoneManager.setActualDefaultRingtoneUri(context, type, uri)
        }.isSuccess
    }
}
