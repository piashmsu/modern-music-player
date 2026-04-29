package com.gsmtrick.musicplayer.data.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
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
    val uploaderUrl: String? = null,
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
                        uploaderUrl = it.uploaderUrl,
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

    /** Imports a YouTube playlist by URL and returns its songs as search results. */
    suspend fun importPlaylist(url: String, limit: Int = 200): List<YoutubeSearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit()
            val info: PlaylistInfo = PlaylistInfo.getInfo(url)
            info.relatedItems
                .asSequence()
                .take(limit)
                .map {
                    YoutubeSearchResult(
                        url = it.url,
                        title = it.name,
                        uploader = it.uploaderName,
                        uploaderUrl = it.uploaderUrl,
                        durationSec = it.duration,
                        thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    )
                }
                .toList()
        }

    /**
     * After playing a stream, fetch the related list (auto-radio).
     */
    suspend fun relatedTo(url: String, limit: Int = 30): List<YoutubeSearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit()
            val info = StreamInfo.getInfo(url)
            info.relatedItems
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map {
                    YoutubeSearchResult(
                        url = it.url,
                        title = it.name,
                        uploader = it.uploaderName,
                        uploaderUrl = it.uploaderUrl,
                        durationSec = it.duration,
                        thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    )
                }
                .toList()
        }

    private fun mimeFor(s: AudioStream): String {
        return s.format?.mimeType ?: "audio/mp4"
    }

    /** Fetches the trending kiosk feed (default music kiosk if available). */
    suspend fun trending(limit: Int = 50): List<YoutubeSearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit()
            val service = ServiceList.YouTube
            val kioskList = service.kioskList
            val kiosk = runCatching { kioskList.getDefaultKioskExtractor() }.getOrNull()
                ?: return@withContext emptyList()
            kiosk.fetchPage()
            kiosk.initialPage.items
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map {
                    YoutubeSearchResult(
                        url = it.url,
                        title = it.name,
                        uploader = it.uploaderName,
                        uploaderUrl = it.uploaderUrl,
                        durationSec = it.duration,
                        thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    )
                }
                .toList()
        }

    /** Fetches uploads from a YouTube channel by channel URL. */
    suspend fun channelUploads(channelUrl: String, limit: Int = 50): List<YoutubeSearchResult> =
        withContext(Dispatchers.IO) {
            ensureInit()
            val info = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(channelUrl)
            // Try the first tab (usually "Videos")
            val tab = info.tabs.firstOrNull() ?: return@withContext emptyList()
            val tabInfo = org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo.getInfo(
                ServiceList.YouTube,
                tab,
            )
            tabInfo.relatedItems
                .asSequence()
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map {
                    YoutubeSearchResult(
                        url = it.url,
                        title = it.name,
                        uploader = it.uploaderName,
                        uploaderUrl = it.uploaderUrl,
                        durationSec = it.duration,
                        thumbnailUrl = it.thumbnails.firstOrNull()?.url,
                    )
                }
                .toList()
        }
}
