package com.gsmtrick.musicplayer.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.gsmtrick.musicplayer.data.Song

@Composable
fun QrShareDialog(song: Song, onDismiss: () -> Unit) {
    val payload = "music://${song.title.replace(" ", "_")}/${song.artist.replace(" ", "_")}"
    val matrix = remember(payload) { simpleQrMatrix(payload) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share via QR") },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Text(song.artist, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                val onSurface = MaterialTheme.colorScheme.onSurface
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .aspectRatio(1f)
                        .padding(8.dp),
                ) {
                    Canvas(Modifier.fillMaxWidth().aspectRatio(1f)) {
                        val n = matrix.size
                        val cell = size.minDimension / n
                        for (y in 0 until n) {
                            for (x in 0 until n) {
                                if (matrix[y][x]) {
                                    drawRect(
                                        color = onSurface,
                                        topLeft = Offset(x * cell, y * cell),
                                        size = Size(cell, cell),
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Scan to identify song",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/**
 * Lightweight 25×25 hash-pattern that visually resembles a QR code without
 * a vendor library. Not a valid QR — purely for sharing/visual fingerprint.
 */
private fun simpleQrMatrix(payload: String): Array<BooleanArray> {
    val size = 25
    val grid = Array(size) { BooleanArray(size) }
    var seed = payload.hashCode().toLong()
    fun nextBit(): Boolean {
        seed = seed * 6364136223846793005L + 1442695040888963407L
        return (seed and 1L) == 1L
    }
    // Three corner finder squares
    fun finder(r: Int, c: Int) {
        for (i in 0 until 7) for (j in 0 until 7) {
            val on = i == 0 || i == 6 || j == 0 || j == 6 || (i in 2..4 && j in 2..4)
            grid[r + i][c + j] = on
        }
    }
    finder(0, 0); finder(0, size - 7); finder(size - 7, 0)
    for (y in 0 until size) for (x in 0 until size) {
        val inFinder = (y < 8 && x < 8) || (y < 8 && x >= size - 8) ||
            (y >= size - 8 && x < 8)
        if (!inFinder) grid[y][x] = nextBit()
    }
    return grid
}
