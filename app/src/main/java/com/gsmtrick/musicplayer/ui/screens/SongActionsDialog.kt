package com.gsmtrick.musicplayer.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.lifecycle.viewmodel.compose.viewModel
import com.gsmtrick.musicplayer.ui.PlayerViewModel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gsmtrick.musicplayer.data.Song

@Composable
fun SongActionsDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val viewModel: PlayerViewModel = viewModel()
    var infoOpen by remember { mutableStateOf(false) }
    var tagOpen by remember { mutableStateOf(false) }
    var qrOpen by remember { mutableStateOf(false) }

    // Pending tag-edit state — captured so we can re-run the update once
    // the user has granted write permission via the system dialog.
    var pendingTagUpdate by remember { mutableStateOf<ContentValues?>(null) }
    val writeRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            pendingTagUpdate?.let { values ->
                runCatching {
                    context.contentResolver.update(song.uri, values, null, null)
                    Toast.makeText(context, "Tags updated", Toast.LENGTH_SHORT).show()
                }
            }
        }
        pendingTagUpdate = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(song.title, maxLines = 2) },
        text = {
            Column {
                ActionRow(Icons.Rounded.Share, "Share") {
                    shareSong(context, song)
                    onDismiss()
                }
                ActionRow(Icons.Rounded.Notifications, "Set as ringtone") {
                    setAsRingtone(context, song)
                    onDismiss()
                }
                ActionRow(Icons.Rounded.Info, "Audio info") {
                    infoOpen = true
                }
                ActionRow(Icons.Rounded.Edit, "Edit tags") {
                    tagOpen = true
                }
                ActionRow(Icons.Rounded.QrCode, "Share via QR") {
                    qrOpen = true
                }
                ActionRow(Icons.Rounded.VisibilityOff, "Hide / unhide") {
                    viewModel.toggleHidden(song.id.toString())
                    Toast.makeText(context, "Visibility toggled", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
                ActionRow(Icons.Rounded.Delete, "Move to trash") {
                    viewModel.trashSong(song.id.toString())
                    Toast.makeText(context, "Moved to trash", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        },
    )

    if (infoOpen) {
        AudioInfoDialog(song = song, onDismiss = { infoOpen = false; onDismiss() })
    }
    if (qrOpen) {
        QrShareDialog(song = song, onDismiss = { qrOpen = false; onDismiss() })
    }
    if (tagOpen) {
        TagEditorDialog(
            song = song,
            onDismiss = { tagOpen = false },
            onSave = { newTitle, newArtist, newAlbum ->
                tagOpen = false
                val values = ContentValues().apply {
                    if (newTitle != song.title) put(MediaStore.Audio.Media.TITLE, newTitle)
                    if (newArtist != song.artist) put(MediaStore.Audio.Media.ARTIST, newArtist)
                    if (newAlbum != song.album) put(MediaStore.Audio.Media.ALBUM, newAlbum)
                }
                if (values.size() == 0) {
                    onDismiss()
                    return@TagEditorDialog
                }
                applyTagUpdate(
                    context = context,
                    song = song,
                    values = values,
                    onPending = { sender ->
                        pendingTagUpdate = values
                        writeRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    },
                    onDone = { ok ->
                        Toast.makeText(
                            context,
                            if (ok) "Tags updated" else "Tag update failed",
                            Toast.LENGTH_SHORT,
                        ).show()
                        onDismiss()
                    },
                )
            },
        )
    }
}

@Composable
private fun ActionRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClick) { Text("Open") }
    }
}

@Composable
private fun AudioInfoDialog(song: Song, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val info = remember(song.uri) {
        runCatching {
            val mmr = MediaMetadataRetriever()
            mmr.setDataSource(context, song.uri)
            val bitrate = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                ?.toLongOrNull()?.let { "${it / 1000} kbps" } ?: "—"
            val mime = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                ?: song.mimeType ?: "—"
            val sample = if (Build.VERSION.SDK_INT >= 31) {
                mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)
                    ?.let { "$it Hz" } ?: "—"
            } else "—"
            mmr.release()
            Triple(bitrate, mime, sample)
        }.getOrNull() ?: Triple("—", song.mimeType ?: "—", "—")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio info") },
        text = {
            Column {
                InfoLine("Title", song.title)
                InfoLine("Artist", song.artist)
                InfoLine("Album", song.album)
                InfoLine("Duration", "${song.durationMs / 1000} s")
                InfoLine("Bitrate", info.first)
                InfoLine("MIME", info.second)
                InfoLine("Sample rate", info.third)
                InfoLine("Year", if (song.year > 0) song.year.toString() else "—")
                InfoLine("Path", song.filePath ?: song.uri.toString())
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 4)
    }
}

@Composable
private fun TagEditorDialog(
    song: Song,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit,
) {
    var title by remember { mutableStateOf(song.title) }
    var artist by remember { mutableStateOf(song.artist) }
    var album by remember { mutableStateOf(song.album) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags") },
        text = {
            Column {
                OutlinedTextField(
                    value = title, onValueChange = { title = it },
                    label = { Text("Title") }, singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist, onValueChange = { artist = it },
                    label = { Text("Artist") }, singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = album, onValueChange = { album = it },
                    label = { Text("Album") }, singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, artist, album) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private fun shareSong(context: android.content.Context, song: Song) {
    val text = buildString {
        append("🎵 ")
        append(song.title)
        if (song.artist.isNotEmpty()) append(" — ${song.artist}")
        append("\n")
        append("Shared via Modern Music Player")
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, song.title)
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}

private fun setAsRingtone(context: android.content.Context, song: Song) {
    if (!Settings.System.canWrite(context)) {
        // Send the user to the system grant page. They will need to come
        // back and try again after granting the permission.
        val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        Toast.makeText(
            context,
            "Allow modify-system-settings, then try again.",
            Toast.LENGTH_LONG,
        ).show()
        return
    }
    runCatching {
        // Mark the file as a ringtone in MediaStore. This is best-effort —
        // some devices reject the column update for files owned by other
        // packages, but the setActualDefaultRingtoneUri call below works
        // either way.
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.IS_RINGTONE, true)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, false)
            put(MediaStore.Audio.Media.IS_ALARM, false)
            put(MediaStore.Audio.Media.IS_MUSIC, true)
        }
        runCatching { context.contentResolver.update(song.uri, values, null, null) }
        RingtoneManager.setActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_RINGTONE,
            song.uri,
        )
        Toast.makeText(context, "Ringtone set: ${song.title}", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "Could not set ringtone: ${it.message}", Toast.LENGTH_LONG).show()
    }
}

private fun applyTagUpdate(
    context: android.content.Context,
    song: Song,
    values: ContentValues,
    onPending: (android.content.IntentSender) -> Unit,
    onDone: (Boolean) -> Unit,
) {
    runCatching {
        val rows = context.contentResolver.update(song.uri, values, null, null)
        onDone(rows > 0)
    }.onFailure { e ->
        if (Build.VERSION.SDK_INT >= 29 && e is android.app.RecoverableSecurityException) {
            // The user owns the file but we don't have permission yet —
            // launch the system dialog to ask. The retry happens in the
            // launcher callback in SongActionsDialog.
            onPending(e.userAction.actionIntent.intentSender)
        } else {
            onDone(false)
        }
    }
}
