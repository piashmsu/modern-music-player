package com.gsmtrick.musicplayer.ui.screens

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.gsmtrick.musicplayer.data.youtube.YoutubeRepository
import com.gsmtrick.musicplayer.data.youtube.YoutubeSearchResult
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeScreen(viewModel: PlayerViewModel) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<YoutubeSearchResult>>(emptyList()) }
    var resolvingUrl by remember { mutableStateOf<String?>(null) }

    fun runSearch(q: String) {
        if (q.isBlank()) return
        loading = true
        error = null
        scope.launch {
            try {
                results = YoutubeRepository.search(q)
            } catch (t: Throwable) {
                error = t.message ?: "Search failed"
                results = emptyList()
            } finally {
                loading = false
            }
        }
    }

    fun playResult(item: YoutubeSearchResult) {
        resolvingUrl = item.url
        scope.launch {
            try {
                val stream = YoutubeRepository.resolveStream(item.url)
                viewModel.playRemoteAudio(
                    streamUrl = stream.audioStreamUrl,
                    title = stream.title,
                    artist = stream.uploader ?: "YouTube",
                    artworkUrl = stream.thumbnailUrl,
                    durationMs = stream.durationSec.coerceAtLeast(0) * 1000L,
                    sourceUrl = stream.url,
                )
            } catch (t: Throwable) {
                Toast.makeText(context, "Failed to play: ${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                resolvingUrl = null
            }
        }
    }

    fun downloadResult(item: YoutubeSearchResult) {
        resolvingUrl = item.url
        scope.launch {
            try {
                val stream = YoutubeRepository.resolveStream(item.url)
                enqueueDownload(context, stream.audioStreamUrl, stream.title, stream.audioMimeType)
                Toast.makeText(
                    context,
                    "Download started: ${stream.title}",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "Download failed: ${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                resolvingUrl = null
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "YouTube",
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp),
        )
        Text(
            "Search, stream and download — uses NewPipeExtractor",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, bottom = 12.dp),
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search a song or artist...") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Rounded.Close, null)
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                runSearch(query)
            }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(8.dp))

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                    }
                }
                results.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Type a song name above and press search.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(results, key = { it.url }) { item ->
                            YoutubeResultRow(
                                item = item,
                                resolving = resolvingUrl == item.url,
                                onPlay = { playResult(item) },
                                onDownload = { downloadResult(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YoutubeResultRow(
    item: YoutubeSearchResult,
    resolving: Boolean,
    onPlay: () -> Unit,
    onDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clickable(enabled = !resolving, onClick = onPlay),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 96.dp, height = 64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                if (item.thumbnailUrl != null) {
                    AsyncImage(
                        model = item.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (item.durationSec > 0) {
                    Text(
                        formatDur(item.durationSec),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.uploader ?: "—",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (resolving) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            } else {
                IconButton(onClick = onPlay) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                }
                IconButton(onClick = onDownload) {
                    Icon(Icons.Rounded.Download, contentDescription = "Download")
                }
            }
        }
    }
}

private fun formatDur(sec: Long): String {
    val s = sec % 60
    val m = (sec / 60) % 60
    val h = sec / 3600
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

private fun enqueueDownload(
    context: Context,
    url: String,
    title: String,
    mimeType: String,
) {
    val safeName = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
    val ext = when {
        "webm" in mimeType -> "webm"
        "mp4" in mimeType || "m4a" in mimeType -> "m4a"
        "mpeg" in mimeType -> "mp3"
        else -> "m4a"
    }
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setDescription("Modern Music Player download")
        .setMimeType(mimeType)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(
            Environment.DIRECTORY_MUSIC,
            "ModernMusic/${safeName}.${ext}",
        )
        .setAllowedOverMetered(true)
        .addRequestHeader(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36",
        )
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    dm.enqueue(request)
}
