package com.gsmtrick.musicplayer.data.youtube

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as NPRequest
import org.schabi.newpipe.extractor.downloader.Response as NPResponse
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.util.concurrent.TimeUnit

/** OkHttp-backed Downloader required by NewPipeExtractor. */
class YoutubeDownloader private constructor() : Downloader() {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override fun execute(request: NPRequest): NPResponse {
        val url = request.url()
        val builder = Request.Builder().url(url)

        request.headers().forEach { (name, values) ->
            values.forEach { v -> builder.addHeader(name, v) }
        }
        if (request.headers()["User-Agent"] == null) {
            builder.addHeader("User-Agent", USER_AGENT)
        }

        val httpMethod = request.httpMethod()
        val data = request.dataToSend()
        val body = if (data != null) {
            RequestBody.create(null, data)
        } else null
        builder.method(httpMethod, body)

        val response = client.newCall(builder.build()).execute()
        if (response.code == 429) {
            response.close()
            throw ReCaptchaException("reCaptcha required", url)
        }
        val responseBody = response.body?.string() ?: ""
        val latestUrl = response.request.url.toString()
        val responseHeaders = response.headers.toMultimap()
        return NPResponse(
            response.code,
            response.message,
            responseHeaders,
            responseBody,
            latestUrl,
        )
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Mobile Safari/537.36"

        @Volatile
        private var instance: YoutubeDownloader? = null

        fun get(): YoutubeDownloader = instance ?: synchronized(this) {
            instance ?: YoutubeDownloader().also { instance = it }
        }
    }
}
