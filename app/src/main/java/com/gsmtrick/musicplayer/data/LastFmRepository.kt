package com.gsmtrick.musicplayer.data

import android.content.Context
import com.gsmtrick.musicplayer.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Minimal Last.fm scrobbling client.
 *
 * Authentication uses the "mobile session" flow (auth.getMobileSession): the
 * user provides their Last.fm username + password directly, the client signs
 * the request with the API secret, and the server returns a session key that
 * is stored in DataStore for subsequent scrobbles.
 *
 * Scrobbling rules (per Last.fm docs):
 *  - Send `track.updateNowPlaying` when a song starts.
 *  - Send `track.scrobble` once the song has been listened for >= 30 s and
 *    either >= 50 % of its duration OR >= 4 minutes — whichever first.
 */
class LastFmRepository(private val context: Context) {

    private val client = OkHttpClient()
    private val apiKey: String get() = BuildConfig.LASTFM_API_KEY
    private val apiSecret: String get() = BuildConfig.LASTFM_API_SECRET

    val isConfigured: Boolean get() = apiKey.isNotEmpty()

    suspend fun authenticate(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (!isConfigured) return@withContext Result.failure(
                IllegalStateException("Last.fm API key not configured at build time")
            )
            val params = sortedMapOf(
                "method" to "auth.getMobileSession",
                "username" to username,
                "password" to password,
                "api_key" to apiKey,
            )
            val signed = sign(params)
            val body = FormBody.Builder().apply {
                signed.forEach { (k, v) -> add(k, v) }
                add("format", "json")
            }.build()
            val req = Request.Builder()
                .url("https://ws.audioscrobbler.com/2.0/")
                .post(body)
                .build()
            runCatching {
                client.newCall(req).execute().use { resp ->
                    val raw = resp.body?.string().orEmpty()
                    val json = JSONObject(raw)
                    val key = json.optJSONObject("session")?.optString("key").orEmpty()
                    if (key.isNotEmpty()) Result.success(key)
                    else Result.failure(
                        Exception(json.optString("message", "Authentication failed"))
                    )
                }
            }.getOrElse { Result.failure(it) }
        }

    suspend fun nowPlaying(sessionKey: String, song: Song) = withContext(Dispatchers.IO) {
        if (!isConfigured || sessionKey.isEmpty()) return@withContext
        post(
            "track.updateNowPlaying",
            mapOf(
                "artist" to song.artist,
                "track" to song.title,
                "album" to song.album,
                "duration" to (song.durationMs / 1000).toString(),
                "sk" to sessionKey,
            ),
        )
    }

    suspend fun scrobble(sessionKey: String, song: Song, startedAtSec: Long) =
        withContext(Dispatchers.IO) {
            if (!isConfigured || sessionKey.isEmpty()) return@withContext
            post(
                "track.scrobble",
                mapOf(
                    "artist[0]" to song.artist,
                    "track[0]" to song.title,
                    "album[0]" to song.album,
                    "timestamp[0]" to startedAtSec.toString(),
                    "duration[0]" to (song.durationMs / 1000).toString(),
                    "sk" to sessionKey,
                ),
            )
        }

    private fun post(method: String, extras: Map<String, String>) {
        val toSign = sortedMapOf<String, String>().apply {
            put("method", method)
            put("api_key", apiKey)
            putAll(extras)
        }
        val signed = sign(toSign)
        val body = FormBody.Builder().apply {
            signed.forEach { (k, v) -> add(k, v) }
            add("format", "json")
        }.build()
        val req = Request.Builder()
            .url("https://ws.audioscrobbler.com/2.0/")
            .post(body)
            .build()
        runCatching { client.newCall(req).execute().close() }
    }

    private fun sign(params: Map<String, String>): Map<String, String> {
        val sb = StringBuilder()
        params.toSortedMap().forEach { (k, v) -> sb.append(k).append(v) }
        sb.append(apiSecret)
        val md = MessageDigest.getInstance("MD5")
        val sig = md.digest(sb.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return params + ("api_sig" to sig)
    }
}
