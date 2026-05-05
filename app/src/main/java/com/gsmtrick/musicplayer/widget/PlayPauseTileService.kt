package com.gsmtrick.musicplayer.widget

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.KeyEvent
import androidx.annotation.RequiresApi

/**
 * v3.4 — Quick Settings tile that toggles play/pause from the
 * notification-shade. Tap dispatches a media-button [KeyEvent] to the
 * playback service which is then handled by Media3's `MediaSession`.
 *
 * Visible on Android 7+ (API 24). The user must explicitly drag it from
 * the QS edit screen the first time.
 */
@RequiresApi(Build.VERSION_CODES.N)
class PlayPauseTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        tile.label = "Play / Pause"
        tile.state = Tile.STATE_ACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = PlayerWidgetProvider.mediaButtonIntent(
            applicationContext,
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        )
        runCatching {
            PlayerWidgetProvider.startWithMediaButton(applicationContext, intent)
        }
    }
}
