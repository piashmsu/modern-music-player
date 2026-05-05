package com.gsmtrick.musicplayer.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import android.widget.RemoteViews
import com.gsmtrick.musicplayer.MainActivity
import com.gsmtrick.musicplayer.R
import com.gsmtrick.musicplayer.playback.MusicPlaybackService

/**
 * v3.4 — 4x1 home screen widget with working media controls.
 *
 * Tapping the album-art tile launches the full app, while tapping
 * prev / play-pause / next dispatches a media-button [KeyEvent] to the
 * running [MusicPlaybackService]. We rely on Media3's `MediaSession` to
 * handle these key events automatically.
 */
class PlayerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val views = remoteViews(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, views)
    }

    companion object {

        const val ACTION_PLAY_PAUSE = "com.gsmtrick.musicplayer.widget.PLAY_PAUSE"
        const val ACTION_PREV = "com.gsmtrick.musicplayer.widget.PREV"
        const val ACTION_NEXT = "com.gsmtrick.musicplayer.widget.NEXT"

        /** Push a fresh RemoteViews to every running widget. */
        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, PlayerWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = remoteViews(context)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun remoteViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_player)
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent(context))
            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                actionPendingIntent(context, ACTION_PLAY_PAUSE),
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                actionPendingIntent(context, ACTION_PREV),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                actionPendingIntent(context, ACTION_NEXT),
            )
            return views
        }

        private fun openAppPendingIntent(context: Context): PendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )

        private fun actionPendingIntent(context: Context, action: String): PendingIntent {
            val intent = Intent(context, WidgetActionReceiver::class.java).setAction(action)
            return PendingIntent.getBroadcast(
                context,
                action.hashCode(),
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        /**
         * Construct the key event ↔ action mapping. Used by [WidgetActionReceiver]
         * to translate the click action into a Media3 media-button intent
         * dispatched to the playback service.
         */
        fun keyCodeForAction(action: String?): Int = when (action) {
            ACTION_PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            ACTION_NEXT -> KeyEvent.KEYCODE_MEDIA_NEXT
            ACTION_PREV -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> -1
        }

        /**
         * Build the media-button [Intent] aimed at the playback service.
         * Runs on Android 8+ via `startForegroundService`.
         */
        fun mediaButtonIntent(context: Context, keyCode: Int): Intent =
            Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                component = ComponentName(context, MusicPlaybackService::class.java)
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            }

        fun startWithMediaButton(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
