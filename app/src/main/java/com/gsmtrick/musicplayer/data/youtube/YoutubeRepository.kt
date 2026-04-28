package com.gsmtrick.musicplayer.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamExtractor
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicBoolean

data class YoutubeSearchResult(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
)

data class YoutubeStream(
    val url: String,
    val title: String,
    val uploader: String?,
    val durationSec: Long,
    val thumbnailUrl: String?,
    val audioStreamUrl: String,
    val audioMimeType: String,
)

object YoutubeRepository {

    private val initialized = AtomicBoolean(false)

    private fun ensureInit() {
        if (initialized.compareAndSet(false, true)) {
            NewPipe.init(YoutubeDownloader.get())
        }
    }

    suspend fun search(query: String, limit: Int = 30): List<YoutubeSearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit()
            val service = ServiceList.YouTube
            val info: SearchInfo = SearchInfo.getInfo(
                service,
                service.searchQHFactory.fromQuery(
                    query,
                    listOf("music_songs"),
                    "",
                ),
            )
            info.relatedItems
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map {
                    YoutubeSearchResult(
                        url = it.url,
                        title = it.name,
                        uploader = it.uploaderName,
                        durationSec = it.duration,
                        thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    )
                }
                .toList()
        }

    /** quality: "auto" | "low" | "medium" | "high" */
    suspend fun resolveStream(url: String, quality: String = "auto"): YoutubeStream =
        withContext(Dispatchers.IO) {
            ensureInit()
            val info: StreamInfo = StreamInfo.getInfo(url)
            val progressive = info.audioStreams
                .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP }
            val candidates = if (progressive.isNotEmpty()) progressive else info.audioStreams
            val audio = pickByQuality(candidates, quality)
                ?: throw IllegalStateException("No audio stream available")
            YoutubeStream(
                url = url,
                title = info.name,
                uploader = info.uploaderName,
                durationSec = info.duration,
                thumbnailUrl = info.thumbnails.firstOrNull()?.url,
                audioStreamUrl = audio.content,
                audioMimeType = mimeFor(audio),
            )
        }

    private fun pickByQuality(streams: List<AudioStream>, quality: String): AudioStream? {
        if (streams.isEmpty()) return null
        val sorted = streams.sortedBy { it.averageBitrate.takeIf { b -> b > 0 } ?: it.bitrate }
        return when (quality) {
            "low" -> sorted.first()
            "medium" -> sorted[sorted.size / 2]
            "high", "auto" -> sorted.last()
            else -> sorted.last()
        }
    }

    private fun mimeFor(s: AudioStream): String {
        return s.format?.mimeType ?: "audio/mp4"
    }
}
