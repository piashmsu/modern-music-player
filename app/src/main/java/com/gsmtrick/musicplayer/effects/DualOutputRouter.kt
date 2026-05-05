package com.gsmtrick.musicplayer.effects

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.gsmtrick.musicplayer.data.Song

/**
 * v3.4 — Best-effort "dual output" mirror.
 *
 * Android's audio policy normally lets one app output to *one* device
 * at a time (the active media route). Two notable exceptions:
 *  - Multi-channel route (BT + speaker) is supported on Android 12+
 *    via [AudioManager.setCommunicationDevice], but only for voice
 *    calls — not for `STREAM_MUSIC`.
 *  - Many OEM ROMs (Samsung Dual Audio, OnePlus Audio Share) provide
 *    a system-level dual-A2DP route accessible only via OEM toggles.
 *
 * What we *can* do as an app: spin up a second [MediaPlayer] tied to
 * the requested device id (Android 9+). If the system honours the
 * preference both will play; if not, only the routed one will.
 */
class DualOutputRouter(private val context: Context) {

    private var secondary: MediaPlayer? = null

    /** Returns true on Android 9+ where preferred-device routing exists. */
    val supported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun start(song: Song) {
        if (!supported) return
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val builtIn = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        }
        if (builtIn == null) return
        runCatching {
            stop()
            val mp = MediaPlayer()
            mp.setDataSource(context, song.uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                mp.preferredDevice = builtIn
            }
            mp.setVolume(0.5f, 0.5f)
            mp.prepare()
            mp.start()
            secondary = mp
        }
    }

    fun stop() {
        secondary?.let {
            runCatching {
                it.stop()
                it.release()
            }
        }
        secondary = null
    }
}
