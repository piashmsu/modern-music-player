package com.gsmtrick.musicplayer.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * v3.4 — Translates a widget-button click [Intent] into a media-button
 * [Intent] aimed at the running [com.gsmtrick.musicplayer.playback.MusicPlaybackService].
 */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val keyCode = PlayerWidgetProvider.keyCodeForAction(intent.action)
        if (keyCode <= 0) return
        val mediaIntent = PlayerWidgetProvider.mediaButtonIntent(context, keyCode)
        runCatching { PlayerWidgetProvider.startWithMediaButton(context, mediaIntent) }
    }
}
